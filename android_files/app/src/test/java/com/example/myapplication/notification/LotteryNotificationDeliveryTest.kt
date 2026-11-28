package com.example.myapplication.notification

import android.app.Notification
import android.app.NotificationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.myapplication.features.notification.AndroidLotteryNotificationPresenter
import com.example.myapplication.app.LotteryNotificationViewModel
import com.example.myapplication.features.notification.AppNotification
import com.example.myapplication.data.firebase.NotificationRepository
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class LotteryNotificationDeliveryTest {
    @Test
    fun committedResultNotifiesWinnersAndLosersAndSkipsDeletedProfiles() {
        val fixture = Fixture()
        fixture.pending(listOf("winner", "loser", "deleted", "winner"), listOf("winner"))
        fixture.addProfiles("winner", "loser")

        fixture.startAttempt()
        assertFalse(fixture.finishAttempt())

        val winner = checkNotNull(fixture.notification("winner"))
        val loser = checkNotNull(fixture.notification("loser"))
        assertEquals("WIN", winner.type)
        assertEquals("LOSE", loser.type)
        assertEquals("draw_winner", winner.id)
        assertTrue(winner.message.contains("River Camp"))
        assertFalse(winner.read)
        assertNull(fixture.notification("deleted"))
        assertNull(fixture.remainingRecipients())

        // A client that saw the same result before completion has no writes left to make.
        fixture.startAttempt()
        assertFalse(fixture.finishAttempt())
        assertSame(winner, fixture.notification("winner"))
        assertSame(loser, fixture.notification("loser"))
    }

    @Test
    fun failedAttemptLeavesRecipientsPendingAndRetryPreservesAnExistingReadNotification() {
        val fixture = Fixture()
        fixture.pending(listOf("winner", "loser"), listOf("winner"))
        fixture.addProfiles("winner", "loser")
        fixture.startAttempt()
        assertFalse(fixture.finishAttempt(commit = false))
        assertEquals(listOf("winner", "loser"), fixture.remainingRecipients())
        assertNull(fixture.notification("winner"))
        assertNull(fixture.notification("loser"))

        val existing = AppNotification(id = "draw_winner", userId = "winner", lotteryId = "draw",
            type = "WIN", message = "Already delivered", read = true, createdAt = 123L)
        fixture.save(existing)
        assertFalse(fixture.finishAttempt())

        assertSame(existing, fixture.notification("winner"))
        assertEquals("LOSE", fixture.notification("loser")?.type)
        assertNull(fixture.remainingRecipients())
    }

    @Test
    fun largeResultsAdvanceInBoundedChunksAndLeaveOnlyUnprocessedWinners() {
        val fixture = Fixture()
        val recipients = (1..102).map { "entrant$it" }
        fixture.pending(recipients, listOf(recipients.first(), recipients.last()))
        fixture.addProfiles(*recipients.toTypedArray())

        fixture.startAttempt()
        assertTrue(fixture.finishAttempt())
        assertEquals(100, recipients.count { fixture.notification(it) != null })
        assertEquals(recipients.takeLast(2), fixture.remainingRecipients())
        assertEquals(listOf(recipients.last()), fixture.remainingWinners())

        fixture.startAttempt()
        assertFalse(fixture.finishAttempt())
        assertEquals(102, recipients.count { fixture.notification(it) != null })
        assertEquals("WIN", fixture.notification(recipients.first())?.type)
        assertEquals("WIN", fixture.notification(recipients.last())?.type)
        assertNull(fixture.remainingRecipients())
    }

    @Test
    fun failedReadAcknowledgmentRetriesWithoutPostingASecondAndroidAlert() = runBlocking {
        val fixture = Fixture()
        val failure = IllegalStateException("Acknowledgment could not commit")
        val ack = TaskCompletionSource<Void>()
        val unread = fixture.unreadDocument("winner", ack.task, Tasks.forResult(null))
        var posts = 0
        val post: (DocumentSnapshot) -> Boolean = {
            posts++
            true
        }

        val store = ViewModelStore()
        var viewModelCreations = 0
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                viewModelCreations++
                return checkNotNull(modelClass.cast(LotteryNotificationViewModel(fixture.createRepository())))
            }
        }
        fun repositoryFromActivity(): NotificationRepository {
            val owner = object : ViewModelStoreOwner {
                override val viewModelStore = store
            }
            return ViewModelProvider(owner, factory)[LotteryNotificationViewModel::class.java].repository
        }
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { repositoryFromActivity().postAndAcknowledgeUnread("winner", listOf(unread), post) }
        }
        assertFalse(result.isCompleted)
        assertEquals(1, fixture.acknowledgments)
        ack.setException(failure)
        val acknowledgmentFailure = withTimeout(5_000) { result.await() }.exceptionOrNull()
        assertTrue(acknowledgmentFailure is IllegalStateException)
        assertEquals(failure.message, acknowledgmentFailure?.message)
        repositoryFromActivity().postAndAcknowledgeUnread("winner", listOf(unread), post)

        assertEquals(1, posts)
        assertEquals(2, fixture.acknowledgments)
        assertEquals(1, viewModelCreations)
    }

    @Test
    fun committedInboxSnapshotPostsUsingAndroidPresenterBeforeAcknowledgment() = runBlocking {
        val fixture = InboxFixture()
        val manager = mock(NotificationManager::class.java)
        val alert = mock(Notification::class.java)
        val presenter = AndroidLotteryNotificationPresenter(manager, { true }) { notification ->
            assertEquals("You won!", notification.message)
            alert
        }
        val document = fixture.result("win", "WIN", "You won!")
        val acknowledged = CompletableDeferred<Unit>()
        `when`(document.reference.update("read", true)).thenAnswer {
            acknowledged.complete(Unit)
            Tasks.forResult(null)
        }
        val monitoring = launch { fixture.repository.observeInbox("entrant", presenter::post) }
        try {
            val listener = withTimeout(5_000) { fixture.listener.await() }
            listener.onEvent(fixture.snapshot(listOf(document), fromCache = true), null)
            withTimeout(5_000) { fixture.repository.inbox.first { it.fromCache } }
            verifyNoInteractions(manager)
            verify(document.reference, never()).update("read", true)

            listener.onEvent(fixture.snapshot(listOf(document)), null)
            withTimeout(5_000) { acknowledged.await() }

            val ordered = inOrder(manager, document.reference)
            ordered.verify(manager).notify("win", 0, alert)
            ordered.verify(document.reference).update("read", true)
            assertEquals(listOf("win"), fixture.repository.inbox.value.notifications.map { it.id })
        } finally {
            monitoring.cancelAndJoin()
        }
        fixture.repository.observeInbox(null, post = null)
        assertNull(fixture.repository.inbox.value.userId)
        assertTrue(fixture.repository.inbox.value.notifications.isEmpty())
        verify(fixture.registration).remove()
    }

    @Test
    fun optedOutProfileStillReceivesWinnerAndLoserRecordsWithoutAcknowledgingAlerts() = runBlocking {
        val fixture = InboxFixture()
        val win = fixture.result("win", "WIN", "You won!", createdAt = 2L)
        val loss = fixture.result("loss", "LOSE", "You were not selected.", createdAt = 1L)
        val monitoring = launch { fixture.repository.observeInbox("entrant", post = null) }
        try {
            val listener = withTimeout(5_000) { fixture.listener.await() }
            listener.onEvent(fixture.snapshot(listOf(loss, win)), null)

            val inbox = withTimeout(5_000) { fixture.repository.inbox.first { !it.loading } }
            assertEquals(listOf("WIN", "LOSE"), inbox.notifications.map { it.type })
            assertFalse(inbox.loadFailed)
            verify(win.reference, never()).update("read", true)
            verify(loss.reference, never()).update("read", true)
        } finally {
            monitoring.cancelAndJoin()
        }
        verify(fixture.registration).remove()
    }

    @Test
    fun disabledAndFailedAndroidPostsLeaveAlertsUnreadUntilTheyCanBeShown() = runBlocking {
        val fixture = Fixture()
        val unread = fixture.unreadDocument("winner", Tasks.forResult(null))
        val documents = listOf(unread)
        fixture.repository.postAndAcknowledgeUnread("winner", documents) { false }
        assertEquals(0, fixture.acknowledgments)

        val postingFailure = SecurityException("Notification permission changed")
        val result = runCatching {
            fixture.repository.postAndAcknowledgeUnread("winner", documents) { throw postingFailure }
        }
        assertSame(postingFailure, result.exceptionOrNull())
        assertEquals(0, fixture.acknowledgments)

        fixture.repository.postAndAcknowledgeUnread("winner", documents) { true }
        assertEquals(1, fixture.acknowledgments)
    }

    @Test
    fun stoppingTheForegroundScopeRemovesThePendingResultListener() = runBlocking {
        val db = mock(FirebaseFirestore::class.java)
        val pendingResults = mock(CollectionReference::class.java)
        val notifications = mock(CollectionReference::class.java)
        val users = mock(CollectionReference::class.java)
        val registration = mock(ListenerRegistration::class.java)
        val started = CompletableDeferred<Unit>()
        `when`(db.collection("notifications")).thenReturn(notifications)
        `when`(db.collection("lotteryDrawResults")).thenReturn(pendingResults)
        `when`(db.collection("users")).thenReturn(users)
        `when`(pendingResults.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>()))
            .thenAnswer {
                started.complete(Unit)
                registration
            }

        val monitoring = launch { NotificationRepository(db).deliverLotteryResults() }
        withTimeout(5_000) { started.await() }
        monitoring.cancelAndJoin()

        verify(registration).remove()
    }

    private class InboxFixture {
        val listener = CompletableDeferred<EventListener<QuerySnapshot>>()
        val registration = mock(ListenerRegistration::class.java)
        val repository: NotificationRepository

        init {
            val db = mock(FirebaseFirestore::class.java)
            val notifications = mock(CollectionReference::class.java)
            val query = mock(Query::class.java)
            `when`(db.collection("notifications")).thenReturn(notifications)
            val pendingResults = mock(CollectionReference::class.java)
            val users = mock(CollectionReference::class.java)
            `when`(db.collection("lotteryDrawResults")).thenReturn(pendingResults)
            `when`(db.collection("users")).thenReturn(users)
            `when`(notifications.whereEqualTo("userId", "entrant")).thenReturn(query)
            `when`(query.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>()))
                .thenAnswer {
                    listener.complete(it.getArgument(1))
                    registration
                }
            repository = NotificationRepository(db)
        }

        fun result(id: String, type: String, message: String, createdAt: Long = 1L): DocumentSnapshot {
            val document = mock(DocumentSnapshot::class.java)
            val reference = mock(DocumentReference::class.java)
            `when`(document.id).thenReturn(id)
            `when`(document.reference).thenReturn(reference)
            `when`(document.toObject(AppNotification::class.java)).thenReturn(
                AppNotification(id, "entrant", "lottery", type, message, createdAt = createdAt)
            )
            return document
        }

        fun snapshot(documents: List<DocumentSnapshot>, fromCache: Boolean = false): QuerySnapshot {
            val metadata = mock(SnapshotMetadata::class.java)
            `when`(metadata.isFromCache).thenReturn(fromCache)
            val snapshot = mock(QuerySnapshot::class.java)
            `when`(snapshot.metadata).thenReturn(metadata)
            `when`(snapshot.documents).thenReturn(documents)
            return snapshot
        }
    }

    private class Fixture {
        private val drawPath = "lotteryDrawResults/draw"
        private val records = mutableMapOf<String, Any>()
        private val refs = mutableMapOf<String, DocumentReference>()
        private val db = mock(FirebaseFirestore::class.java)
        private lateinit var body: Transaction.Function<Boolean>
        private lateinit var transactionResult: TaskCompletionSource<Boolean>
        val repository: NotificationRepository
        var acknowledgments = 0
            private set

        init {
            for (name in listOf("notifications", "lotteryDrawResults", "users")) {
                val collection = mock(CollectionReference::class.java)
                `when`(db.collection(name)).thenReturn(collection)
                `when`(collection.document(anyString())).thenAnswer { call -> reference("$name/${call.getArgument<String>(0)}") }
            }
            `when`(db.runTransaction(any<Transaction.Function<Boolean>>())).thenAnswer { call ->
                body = call.getArgument(0)
                transactionResult = TaskCompletionSource()
                transactionResult.task
            }
            repository = NotificationRepository(db)
        }

        fun pending(recipients: List<String>, winners: List<String>) {
            records[drawPath] = mapOf("campsiteName" to "River Camp", "remainingEntrantIds" to recipients, "winnerIds" to winners)
        }

        fun createRepository(): NotificationRepository = NotificationRepository(db)

        fun addProfiles(vararg ids: String) {
            ids.forEach { records["users/$it"] = Unit }
        }

        fun save(notification: AppNotification) {
            records["notifications/${notification.id}"] = notification
        }

        fun notification(userId: String): AppNotification? = records["notifications/draw_$userId"] as? AppNotification

        fun remainingRecipients(): List<String>? = outcomeList("remainingEntrantIds")

        fun remainingWinners(): List<String>? = outcomeList("winnerIds")

        @Suppress("UNCHECKED_CAST")
        private fun outcomeList(field: String): List<String>? = (records[drawPath] as? Map<*, *>)?.get(field) as? List<String>

        fun startAttempt() {
            repository.deliverLotteryResultChunk(reference(drawPath))
        }

        fun finishAttempt(commit: Boolean = true): Boolean {
            val transaction = mock(Transaction::class.java)
            val writes = mutableListOf<() -> Unit>()
            `when`(transaction.get(any(DocumentReference::class.java))).thenAnswer { call ->
                check(writes.isEmpty()) { "Transaction reads must precede writes" }
                snapshot(call.getArgument(0))
            }
            `when`(transaction.set(any(DocumentReference::class.java), any(AppNotification::class.java))).thenAnswer { call ->
                val reference = call.getArgument<DocumentReference>(0)
                val notice = call.getArgument<AppNotification>(1)
                writes.add { records[reference.path] = notice }
                transaction
            }
            `when`(transaction.update(any(DocumentReference::class.java), anyMap<String, Any>())).thenAnswer { call ->
                val reference = call.getArgument<DocumentReference>(0)
                val fields = call.getArgument<Map<String, Any>>(1)
                writes.add { records[reference.path] = (records[reference.path] as Map<*, *>) + fields }
                transaction
            }
            `when`(transaction.delete(any(DocumentReference::class.java))).thenAnswer { call ->
                val reference = call.getArgument<DocumentReference>(0)
                writes.add { records.remove(reference.path) }
                transaction
            }
            val hasMore = checkNotNull(body.apply(transaction))
            if (commit) {
                writes.forEach { it() }
                transactionResult.setResult(hasMore)
            }
            return hasMore
        }

        fun unreadDocument(userId: String, vararg tasks: Task<Void>): DocumentSnapshot {
            val ref = reference("notifications/draw_$userId")
            val attempts = tasks.iterator()
            `when`(ref.update("read", true)).thenAnswer {
                acknowledgments++
                attempts.next()
            }
            return snapshot(ref)
        }

        private fun reference(path: String): DocumentReference = refs.getOrPut(path) {
            mock(DocumentReference::class.java).also { ref ->
                `when`(ref.path).thenReturn(path)
                `when`(ref.id).thenReturn(path.substringAfterLast('/'))
            }
        }

        private fun snapshot(reference: DocumentReference): DocumentSnapshot {
            val data = records[reference.path]
            val id = reference.id
            return mock(DocumentSnapshot::class.java).also { snapshot ->
                `when`(snapshot.id).thenReturn(id)
                `when`(snapshot.reference).thenReturn(reference)
                `when`(snapshot.exists()).thenReturn(data != null)
                `when`(snapshot.get(anyString())).thenAnswer { call -> (data as? Map<*, *>)?.get(call.getArgument<String>(0)) }
                `when`(snapshot.getString(anyString())).thenAnswer { call -> (data as? Map<*, *>)?.get(call.getArgument<String>(0)) as? String }
            }
        }
    }
}
