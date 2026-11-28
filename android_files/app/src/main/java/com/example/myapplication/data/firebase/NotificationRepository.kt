package com.example.myapplication.data.firebase

import com.example.myapplication.features.notification.AppNotification
import com.example.myapplication.features.notification.LotteryNotificationInbox
import com.example.myapplication.features.notification.buildResultNotifications
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

private const val TAG = "LotteryNotifications"
private const val RETRY_DELAY_MS = 5_000L
private const val RECIPIENTS_PER_TRANSACTION = 100

/** Converts committed lottery results into Firestore notifications and delivers local alerts. */
class NotificationRepository(
    private val db: FirebaseFirestore = Firebase.firestore
) {
    private val notifications = db.collection("notifications")
    private val lotteryDrawResults = db.collection("lotteryDrawResults")
    private val users = db.collection("users")
    private val currentInbox = MutableStateFlow(LotteryNotificationInbox())
    val inbox = currentInbox.asStateFlow()

    private val postedUnreadByUser = mutableMapOf<String, MutableSet<String>>()

    /** Call once in the foreground lifecycle. Cancelling the coroutine removes the listener. */
    suspend fun deliverLotteryResults() {
        observeAndDeliver(lotteryDrawResults, "Could not deliver lottery results") { snapshot ->
            deliverEach(snapshot.documents) { document ->
                do {
                    currentCoroutineContext().ensureActive()
                    val hasMore = deliverLotteryResultChunk(document.reference).await()
                } while (hasMore)
            }
        }
    }

    /**
     * Processes a bounded group from the current pending result. Firestore retries competing
     * clients against the latest pending document. Each committed group advances the result
     * together with its notification writes, so failures and restarts can resume safely.
     */
    internal fun deliverLotteryResultChunk(result: DocumentReference): Task<Boolean> =
        db.runTransaction { transaction ->
            val snapshot = transaction.get(result)
            if (!snapshot.exists()) return@runTransaction false

            val remaining = snapshot.recipientIds("remainingEntrantIds")
            val currentRecipients = remaining.take(RECIPIENTS_PER_TRANSACTION)
            val remainingAfterCommit = remaining.drop(currentRecipients.size)
            val winners = snapshot.recipientIds("winnerIds").toSet()
            if (currentRecipients.isNotEmpty()) {
                val campsiteName = checkNotNull(snapshot.getString("campsiteName"))
                val resultNotifications = buildResultNotifications(
                    result.id,
                    campsiteName,
                    currentRecipients,
                    currentRecipients.filter { it in winners }
                )
                val newNotifications = resultNotifications.mapNotNull { notification ->
                    val userExists = transaction.get(users.document(notification.userId)).exists()
                    val notificationRef = notifications.document(notification.id)
                    if (userExists && !transaction.get(notificationRef).exists()) {
                        notificationRef to notification
                    } else {
                        null
                    }
                }
                // Firestore requires every transaction read to precede the first write.
                newNotifications.forEach { (ref, notification) -> transaction.set(ref, notification) }
            }

            if (remainingAfterCommit.isEmpty()) {
                transaction.delete(result)
            } else {
                transaction.update(result, mapOf(
                    "remainingEntrantIds" to remainingAfterCommit,
                    "winnerIds" to remainingAfterCommit.filter { it in winners }
                ))
            }
            remainingAfterCommit.isNotEmpty()
        }

    /**
     * One lifecycle-bound subscription feeds Home, the inbox, and optional device alert delivery.
     * A disabled presenter leaves the persisted results available in the inbox.
     */
    internal suspend fun observeInbox(userId: String?, post: ((String, AppNotification) -> Boolean)?) {
        if (currentInbox.value.userId != userId) {
            currentInbox.value = LotteryNotificationInbox(userId = userId, loading = userId != null)
        }
        if (userId == null) return

        observeAndDeliver(
            notifications.whereEqualTo("userId", userId),
            "Could not refresh lottery notifications",
            showCachedSnapshots = true,
            onListenError = {
                currentInbox.update { inbox ->
                    if (inbox.userId == userId) inbox.copy(loading = false, loadFailed = true) else inbox
                }
            }
        ) { snapshot ->
            val results = snapshot.documents.mapNotNull { document ->
                val result = try {
                    document.toObject(AppNotification::class.java)
                } catch (_: RuntimeException) {
                    null
                }
                result?.takeIf { it.userId == userId }?.let { document to it.copy(id = document.id) }
            }
            currentInbox.value = LotteryNotificationInbox(
                userId = userId,
                notifications = results.map { it.second }.sortedByDescending { it.createdAt },
                loading = false,
                loadFailed = results.size != snapshot.documents.size,
                fromCache = snapshot.metadata.isFromCache
            )

            // Cached or locally changed records may be shown, but cannot trigger an OS post.
            if (!snapshot.isCommitted()) return@observeAndDeliver
            val unread = results.filterNot { it.second.read }.toMap()
            if (post == null) {
                postedUnreadByUser[userId]?.retainAll(unread.keys.map { it.id }.toSet())
            } else {
                postAndAcknowledgeUnread(userId, unread.keys.toList()) { document ->
                    post(document.id, unread.getValue(document))
                }
            }
        }
    }

    /** The stored read flag acknowledges an OS post. Opening the inbox does not change it. */
    internal suspend fun postAndAcknowledgeUnread(
        userId: String,
        documents: List<DocumentSnapshot>,
        post: (DocumentSnapshot) -> Boolean
    ) {
        val posted = postedUnreadByUser.getOrPut(userId) { mutableSetOf() }
        posted.retainAll(documents.map { it.id }.toSet())
        deliverEach(documents) { document ->
            if (document.id !in posted) {
                if (!post(document)) return@deliverEach
                posted.add(document.id)
            }
            document.reference.update("read", true).await()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeAndDeliver(
        query: Query,
        failureMessage: String,
        showCachedSnapshots: Boolean = false,
        onListenError: (Exception) -> Unit = {},
        deliver: suspend (QuerySnapshot) -> Unit
    ) {
        callbackFlow {
            val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onListenError(error)
                    close(error)
                } else if (snapshot != null && (showCachedSnapshots || snapshot.isCommitted())) {
                    trySend(snapshot)
                }
            }
            awaitClose { registration.remove() }
        }.conflate().let { snapshots ->
            // An inbox update should remain visible even when an earlier alert acknowledgment
            // waits for connectivity. A submitted Firestore write can finish after cancellation.
            if (showCachedSnapshots) snapshots.mapLatest(deliver) else snapshots.map(deliver)
        }.retryWhen { error, _ ->
            if (error !is Exception || error is CancellationException) return@retryWhen false
            Log.w(TAG, failureMessage, error)
            delay(RETRY_DELAY_MS)
            true
        }.collect {}
    }

    private fun QuerySnapshot.isCommitted(): Boolean =
        !metadata.isFromCache && !metadata.hasPendingWrites()

    /** Finish independent deliveries from this snapshot before asking the listener to retry. */
    private suspend fun deliverEach(
        documents: List<DocumentSnapshot>,
        deliver: suspend (DocumentSnapshot) -> Unit
    ) {
        var failure: Exception? = null
        for (document in documents) {
            currentCoroutineContext().ensureActive()
            try {
                deliver(document)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun DocumentSnapshot.recipientIds(field: String): List<String> {
        val ids = checkNotNull(get(field) as? List<*>) { "Missing or invalid $field in result $id" }
        return ids.filterIsInstance<String>().filter { it.isNotBlank() && '/' !in it }.distinct()
    }
}
