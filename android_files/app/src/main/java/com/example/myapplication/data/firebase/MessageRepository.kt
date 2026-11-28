package com.example.myapplication.data.firebase

import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.features.profile.User
import com.example.myapplication.features.messaging.Message
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore

/**
 * Sends and reads messages between campsite owners and entrants.
 *
 * Each message is its own document in the "messages" collection.
 * A conversation is every message with the same reservationId.
 *
 * Lists are sorted in the app rather than with Firestore's orderBy(),
 * because combining orderBy() with a filter on a different field needs
 * a custom Firestore index.
 *
 * @property db The Firestore instance to use.
 */
class MessageRepository(
    private val db: FirebaseFirestore = Firebase.firestore
) {
    private val messagesCollection = db.collection("messages")
    private val reservationsCollection = db.collection("reservations")


    // ---------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------

    /**
     * Sends a message about a reservation (US 02.12.01, US 01.14.01).
     *
     * Reads the latest copy of the reservation first, so a message can't
     * be sent about a reservation that was just cancelled or rejected.
     *
     * @param reservationId The reservation the message is about.
     * @param sender The user sending it, the reservation's owner or entrant.
     * @param text What they wrote.
     * @param onResult true if the message was saved.
     */
    fun sendMessage(
        reservationId: String,
        sender: User,
        text: String,
        onResult: (Boolean) -> Unit
    ) {
        if (!isValidDocumentId(reservationId)) {
            onResult(false)
            return
        }

        val reservationDocument = reservationsCollection.document(reservationId)
        val messageDocument = messagesCollection.document()   // new id

        db.runTransaction { transaction ->
            val reservation = transaction.get(reservationDocument)
                .toObject(Reservation::class.java)
                ?.apply { id = reservationDocument.id }
                ?: return@runTransaction false

            // Checks sender, active reservation and text length
            val message = Message.create(reservation, sender, text)
                ?: return@runTransaction false

            message.id = messageDocument.id
            transaction.set(messageDocument, message)
            true
        }
            .addOnSuccessListener { sent -> onResult(sent) }
            .addOnFailureListener { onResult(false) }
    }


    // ---------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------

    /**
     * Listens to one conversation, oldest message first.
     *
     * @param reservationId The reservation the conversation is about.
     * @param onChange Receives the current messages.
     * @return Call remove() on this to stop listening.
     */
    fun listenToConversation(
        reservationId: String,
        onChange: (List<Message>) -> Unit
    ): ListenerRegistration =
        listen(messagesCollection.whereEqualTo("reservationId", reservationId)) { messages ->
            onChange(messages.sortedBy { it.sentAt })
        }

    /**
     * Listens to every message a user sent or received, newest first.
     * Use this for the list of conversations.
     *
     * Firestore can't match "sender OR recipient" in one simple query,
     * so this listens to both and combines them.
     *
     * @param userId The user's device ID.
     * @param onChange Receives the current messages.
     * @return Call remove() on this to stop both listeners.
     */
    fun listenToUserMessages(
        userId: String,
        onChange: (List<Message>) -> Unit
    ): ListenerRegistration {
        var sent: List<Message> = emptyList()
        var received: List<Message> = emptyList()

        fun publish() {
            onChange((sent + received).distinctBy { it.id }.sortedByDescending { it.sentAt })
        }

        val sentListener = listen(messagesCollection.whereEqualTo("senderId", userId)) {
            sent = it
            publish()
        }
        val receivedListener = listen(messagesCollection.whereEqualTo("recipientId", userId)) {
            received = it
            publish()
        }

        return ListenerRegistration {
            sentListener.remove()
            receivedListener.remove()
        }
    }

    /**
     * Listens to how many unread messages a user has.
     * Use this for an "Unread" badge on the messages button.
     *
     * @param userId The user's device ID.
     * @param onChange Receives the unread count.
     * @return Call remove() on this to stop listening.
     */
    fun listenToUnreadCount(
        userId: String,
        onChange: (Int) -> Unit
    ): ListenerRegistration =
        listen(
            messagesCollection
                .whereEqualTo("recipientId", userId)
                .whereEqualTo("read", false)
        ) { onChange(it.size) }

    /**
     * Listens to every message sent from an owner to an entrant,
     * newest first. Only admins can use this (US 03.12.01).
     *
     * @param admin The user asking. Must be an admin.
     * @param onChange Receives the messages.
     * @return Call remove() on this to stop listening, or null if not an admin.
     */
    fun listenToOwnerMessages(
        admin: User,
        onChange: (List<Message>) -> Unit
    ): ListenerRegistration? {
        if (!admin.userType.equals("admin", ignoreCase = true)) {
            return null
        }

        return listen(messagesCollection.whereEqualTo("senderIsOwner", true)) { messages ->
            onChange(messages.sortedByDescending { it.sentAt })
        }
    }


    // ---------------------------------------------------------------
    // Updating
    // ---------------------------------------------------------------

    /**
     * Marks every unread message to this user in a conversation as read.
     * Call this when the user opens the conversation.
     *
     * @param reservationId The conversation's reservation.
     * @param userId The user reading it.
     * @param onResult true if the update succeeded (including when nothing was unread).
     */
    fun markConversationRead(
        reservationId: String,
        userId: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (!isValidDocumentId(reservationId) || !isValidDocumentId(userId)) {
            onResult(false)
            return
        }

        messagesCollection
            .whereEqualTo("reservationId", reservationId)
            .whereEqualTo("recipientId", userId)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onResult(true)
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                for (document in snapshot.documents) {
                    batch.update(document.reference, "read", true)
                }
                batch.commit()
                    .addOnSuccessListener { onResult(true) }
                    .addOnFailureListener { onResult(false) }
            }
            .addOnFailureListener { onResult(false) }
    }


    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun listen(
        query: Query,
        onChange: (List<Message>) -> Unit
    ): ListenerRegistration =
        query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                return@addSnapshotListener
            }
            onChange(toMessages(snapshot))
        }

    private fun toMessages(snapshot: QuerySnapshot): List<Message> =
        snapshot.documents.mapNotNull { document ->
            document.toObject(Message::class.java)?.apply { id = document.id }
        }

    /** Document IDs must have a nonwhitespace character and no path separator. */
    private fun isValidDocumentId(value: String): Boolean =
        value.isNotBlank() && '/' !in value
}