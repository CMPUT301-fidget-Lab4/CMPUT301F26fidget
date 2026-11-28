package com.example.myapplication.profile

import com.example.myapplication.features.profile.AuthState
import com.example.myapplication.data.firebase.UserRepository
import com.example.myapplication.features.profile.User
import com.google.android.gms.tasks.*
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

/** Tests discovery and limits in deleteUser; Task aggregation/scheduling is the external fake. */
class ProfileDeletionOrchestrationTest {
    @Test fun exactly450DistinctReferencesAreAllowedAndRereadBeforeDeletion() = withFixture(450) { f ->
        val result = f.repo.deleteUser(f.user)
        assertSame(f.committed, result)
        assertSame(f.user, f.repo.currentUser)
        f.refs.forEach { verify(f.tx).get(it) }
        f.refs.forEach { verify(f.tx).delete(it) }
        verify(f.tx, never()).update(any(DocumentReference::class.java), anyMap<String, Any>())
        f.complete()
        assertNull(f.repo.currentUser)
        assertEquals(AuthState.NotRegistered, f.repo.authState.value)
    }

    @Test fun moreThan450DistinctReferencesFailBeforeTransactionWithoutLoggingOut() = withFixture(451) { f ->
        val result = f.repo.deleteUser(f.user)
        assertTrue(result.isComplete)
        assertFalse(result.isSuccessful)
        assertTrue(result.exception is IllegalStateException)
        assertTrue(result.exception!!.message!!.contains("transaction limit"))
        verify(f.db, never()).runTransaction<Void>(any())
        assertSame(f.user, f.repo.currentUser)
        assertTrue(f.repo.authState.value is AuthState.LoggedIn)
        verifyNoInteractions(f.tx)
    }

    @Test fun duplicateDiscoveryReferencesDoNotConsumeLimitTwice() = withFixture(450, duplicates = true) { f ->
        val result = f.repo.deleteUser(f.user)
        assertSame(f.committed, result)
        f.refs.forEach { verify(f.tx, times(1)).get(it) }
        verify(f.tx, times(1)).delete(f.refs.first())
    }

    @Test fun unrelatedProfilesDoNotConsumeTheTransactionalLimit() = withFixture(1, unrelatedCount = 451) { f ->
        assertSame(f.committed, f.repo.deleteUser(f.user))
        verify(f.tx).get(f.refs.single())
        verify(f.tx).delete(f.refs.single())
        verifyNoMoreInteractions(f.tx)
    }

    @Test fun aJoinCommittedAfterDiscoveryRequiresRediscoveryBeforeDeletion() = withFixture(1) { f ->
        val updated = mock(DocumentSnapshot::class.java)
        val ref = f.refs.single()
        `when`(updated.exists()).thenReturn(true)
        `when`(updated.reference).thenReturn(ref)
        `when`(updated.data).thenReturn(mapOf(
            "name" to "current",
            "joinedLotteries" to listOf(mapOf("campsiteName" to "pine", "lotteryId" to "new"))
        ))
        `when`(f.tx.get(ref)).thenReturn(updated)

        val result = f.repo.deleteUser(f.user)

        assertFalse(result.isSuccessful)
        assertTrue(result.exception?.message.orEmpty().contains("relationships changed"))
        verify(f.tx, never()).delete(ref)
        assertSame(f.user, f.repo.currentUser)
    }

    @Test fun serverReadFailureNeverStartsCleanupOrClearsCurrentUser() = withFixture(2, readFailure = true) { f ->
        val result = f.repo.deleteUser(f.user)
        assertTrue(result.isComplete)
        assertFalse(result.isSuccessful)
        assertSame(f.readError, result.exception)
        verify(f.db, never()).runTransaction<Void>(any())
        verifyNoInteractions(f.tx)
        assertSame(f.user, f.repo.currentUser)
        assertSame(f.user, f.repo.findUser("current"))
        assertTrue(f.repo.authState.value is AuthState.LoggedIn)
        f.queries.forEach { verify(it).get(Source.SERVER) }
    }

    @Test fun cleanupCatchesDrawAndDeliveryWritesAfterDiscovery() = withFixture(1, racingDraw = true) { f ->
        f.repo.deleteUser(f.user)

        val pending = f.drawRefs.getValue("lotteryDrawResults/active")
        val notification = f.drawRefs.getValue("notifications/active_current")
        val invitation = f.drawRefs.getValue("reservations/active_current")
        val settledNotification = f.drawRefs.getValue("notifications/settled_current")
        val order = inOrder(f.tx)
        order.verify(f.tx).get(pending)
        order.verify(f.tx).get(notification)
        order.verify(f.tx).get(invitation)
        order.verify(f.tx).get(settledNotification)
        verify(f.tx).update(pending, mapOf("remainingEntrantIds" to listOf("other"), "winnerIds" to emptyList<String>()))
        verify(f.tx).update(eq(invitation), argThat<Map<String, Any>> { fields ->
            fields["cancellationReason"] == "PROFILE_DELETED" &&
                fields["status"] == "CANCELLED" && fields["entrantId"] == ""
        })
        verify(f.tx, never()).delete(notification)
        verify(f.tx).delete(settledNotification)
        verify(f.tx).delete(f.refs.first())
    }

    private fun withFixture(
        count: Int,
        duplicates: Boolean = false,
        readFailure: Boolean = false,
        racingDraw: Boolean = false,
        unrelatedCount: Int = 0,
        test: (Fixture) -> Unit
    ) {
        val f = Fixture(count, duplicates, readFailure, racingDraw, unrelatedCount)
        mockStatic(Tasks::class.java).use { tasks ->
            tasks.`when`<Task<List<QuerySnapshot>>> { Tasks.whenAllSuccess<QuerySnapshot>(f.reads) }.thenReturn(f.aggregate)
            test(f)
        }
        f.repo.close()
    }

    private class Fixture(count: Int, duplicates: Boolean, readFailure: Boolean, racingDraw: Boolean, unrelatedCount: Int) {
        val db = mock(FirebaseFirestore::class.java)
        val tx = mock(Transaction::class.java)
        val user = User(deviceID = "current", userType = "admin")
        val readError = IllegalStateException("simulated server read failure")
        val refs = mutableListOf<DocumentReference>()
        val drawRefs = mutableMapOf<String, DocumentReference>()
        val reads = mutableListOf<Task<QuerySnapshot>>()
        val queries = mutableListOf<Query>()
        @Suppress("UNCHECKED_CAST") val aggregate = mock(Task::class.java) as Task<List<QuerySnapshot>>
        @Suppress("UNCHECKED_CAST") val committed = mock(Task::class.java) as Task<Void>
        private lateinit var onCommitted: OnSuccessListener<Void>
        val repo: UserRepository

        init {
            val users = mock(CollectionReference::class.java)
            `when`(db.collection("users")).thenReturn(users)
            lateinit var listener: EventListener<QuerySnapshot>
            doAnswer { call -> listener = call.getArgument(1); mock(ListenerRegistration::class.java) }.`when`(users).addSnapshotListener(eq(MetadataChanges.INCLUDE), any())
            repo = UserRepository("current", db)
            val current = document("users/current", mapOf("name" to "current"))
            `when`(current.toObject(User::class.java)).thenReturn(user)
            val currentRef = current.reference
            `when`(users.document("current")).thenReturn(currentRef)
            refs += currentRef
            val unrelatedProfiles = (1..unrelatedCount).map { index ->
                document("users/unrelated-$index", mapOf("name" to "Unrelated $index"))
            }
            val relatedMessages = (1 until count).map { index ->
                document("messages/message-$index", mapOf(
                    "senderId" to user.deviceID,
                    "recipientId" to if (duplicates) user.deviceID else "other"
                )).also { refs += it.reference }
            }
            val initial = mock(QuerySnapshot::class.java)
            val metadata = mock(SnapshotMetadata::class.java)
            `when`(initial.metadata).thenReturn(metadata)
            `when`(initial.documents).thenReturn(listOf(current))
            listener.onEvent(initial, null)
            val activeLottery = if (racingDraw) document("campsites/a/lottery/current", mapOf(
                "id" to "active", "waitlist" to listOf("current", "other"), "winners" to listOf("current")
            )) else null
            val settledDraw = if (racingDraw) document("lotteryDrawResults/settled", null) else null
            if (racingDraw) {
                document("campsites/a", null)
                document("lotteryDrawResults/active", mapOf("remainingEntrantIds" to listOf("current", "other"),
                    "winnerIds" to listOf("current"), "campsiteName" to "a"))
                document("notifications/active_current", null)
                document("reservations/active_current", mapOf("lotteryId" to "active", "entrantId" to "current",
                    "status" to "INVITED"))
                document("notifications/settled_current", mapOf("userId" to "current"))
                document("reservations/settled_current", null)
            }
            val messages = collection("messages")
            val notifications = collection("notifications")
            val lottery = mock(Query::class.java)
            `when`(db.collectionGroup("lottery")).thenReturn(lottery)
            val pending = collection("lotteryDrawResults")
            val pendingForUser = mock(Query::class.java)
            `when`(pending.whereArrayContains("remainingEntrantIds", user.deviceID)).thenReturn(pendingForUser)
            val snapshots = listOf(
                users to (listOf(current) + unrelatedProfiles),
                collection("campsites") to emptyList(),
                collection("bookingSchedules") to emptyList(),
                collection("reservations") to emptyList(),
                lottery to listOfNotNull(activeLottery),
                filtered(notifications, "userId") to emptyList(),
                filtered(messages, "senderId") to relatedMessages,
                filtered(messages, "recipientId") to if (duplicates) relatedMessages else emptyList(),
                pendingForUser to listOfNotNull(settledDraw)
            ).map { (query, documents) ->
                queries += query
                @Suppress("UNCHECKED_CAST") val read = mock(Task::class.java) as Task<QuerySnapshot>
                reads += read
                `when`(query.get(Source.SERVER)).thenReturn(read)
                mock(QuerySnapshot::class.java).also {
                    `when`(it.documents).thenReturn(documents)
                }
            }
            doAnswer { call -> call.getArgument<Transaction.Function<Void>>(0).apply(tx); committed }.`when`(db).runTransaction<Void>(any())
            doAnswer { call -> onCommitted = call.getArgument(0); committed }.`when`(committed).addOnSuccessListener(any())
            doAnswer { call ->
                if(readFailure) failedTask(readError) else try {
                    call.getArgument<SuccessContinuation<List<QuerySnapshot>, Void>>(0).then(snapshots)
                } catch(e: Exception) { failedTask(e) }
            }.`when`(aggregate).onSuccessTask<Void>(any())
        }
        private fun collection(name: String): CollectionReference = mock(CollectionReference::class.java).also {
            `when`(db.collection(name)).thenReturn(it)
        }
        private fun filtered(collection: CollectionReference, field: String): Query = mock(Query::class.java).also {
            `when`(collection.whereEqualTo(field, user.deviceID)).thenReturn(it)
        }
        private fun document(path: String, fields: Map<String, Any>?): DocumentSnapshot {
            val reference = mock(DocumentReference::class.java)
            drawRefs[path] = reference
            `when`(reference.path).thenReturn(path)
            `when`(db.document(path)).thenReturn(reference)
            return mock(DocumentSnapshot::class.java).also { snapshot ->
                `when`(snapshot.reference).thenReturn(reference)
                `when`(snapshot.exists()).thenReturn(fields != null)
                `when`(snapshot.data).thenReturn(fields)
                `when`(tx.get(reference)).thenReturn(snapshot)
            }
        }
        private fun failedTask(error: Exception): Task<Void> {
            @Suppress("UNCHECKED_CAST") val task = mock(Task::class.java) as Task<Void>
            `when`(task.isComplete).thenReturn(true)
            `when`(task.isSuccessful).thenReturn(false)
            `when`(task.exception).thenReturn(error)
            `when`(task.addOnSuccessListener(any())).thenReturn(task)
            return task
        }
        fun complete() { onCommitted.onSuccess(null) }
    }
}
