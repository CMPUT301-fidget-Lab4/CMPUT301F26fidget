package com.example.myapplication.features.messaging

import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.features.profile.User

/**
 * One message between a campsite owner and an entrant about a reservation.
 *
 * Every message belongs to a reservation, so a conversation is all the
 * messages with the same reservationId. The owner and entrant are taken
 * from that reservation, which means only the two people involved can
 * message each other (US 02.12.01, US 01.14.01).
 *
 * senderIsOwner is stored so admins can list every message sent from
 * owners to entrants (US 03.12.01) with a single Firestore query.
 *
 * Every field has a default so Firestore's toObject() works.
 *
 * @param id Firestore document id.
 * @param reservationId The reservation this conversation is about.
 * @param lotteryId The lottery the reservation came from.
 * @param campsiteId The reserved campsite, blank if not reserved yet.
 * @param senderId Device ID of the sender.
 * @param recipientId Device ID of the recipient.
 * @param senderIsOwner true if the campsite owner sent it, false if the entrant did.
 * @param text The message itself.
 * @param sentAt When it was sent, in milliseconds.
 * @param read true once the recipient has opened it.
 */
data class Message(
    var id: String = "",
    var reservationId: String = "",
    var lotteryId: String = "",
    var campsiteId: String = "",
    var senderId: String = "",
    var recipientId: String = "",
    var senderIsOwner: Boolean = false,
    var text: String = "",
    var sentAt: Long = System.currentTimeMillis(),
    var read: Boolean = false
) {

    /** true if this user sent or received the message. */
    fun involves(userId: String): Boolean =
        userId.isNotBlank() && (senderId == userId || recipientId == userId)

    /** true if this user sent the message. */
    fun isFrom(userId: String): Boolean =
        userId.isNotBlank() && senderId == userId

    /**
     * The other person in the conversation, from this user's point of view.
     *
     * @return The other person's ID, or null if this user isn't part of it.
     */
    fun otherParticipant(userId: String): String? = when (userId) {
        senderId -> recipientId
        recipientId -> senderId
        else -> null
    }

    /**
     * Marks the message as read, but only by its recipient.
     *
     * @return true if it was unread and is now read.
     */
    fun markRead(userId: String): Boolean {
        if (read || userId != recipientId) {
            return false
        }
        read = true
        return true
    }

    companion object {

        /** Longest message allowed, in characters. */
        const val MAX_LENGTH = 1000

        /**
         * Creates a message about a reservation, checking that it's allowed.
         *
         * Only the reservation's owner or entrant can send one, and only while
         * the reservation is still active (invited, or reserved and not ended).
         * The recipient is always the other person on the reservation.
         *
         * This only builds the Message, it does not save it.
         *
         * @param reservation The reservation the message is about.
         * @param sender The user sending it.
         * @param text What they wrote.
         * @param now The current time in milliseconds.
         * @return The new message, or null if it isn't allowed.
         */
        fun create(
            reservation: Reservation,
            sender: User,
            text: String,
            now: Long = System.currentTimeMillis()
        ): Message? {

            val body = text.trim()

            // Must say something, but not too much
            if (body.isEmpty() || body.length > MAX_LENGTH) {
                return null
            }

            // Reservation must have both people
            if (reservation.id.isBlank() ||
                reservation.ownerId.isBlank() ||
                reservation.entrantId.isBlank()
            ) {
                return null
            }

            // Only active reservations can be messaged about
            if (!reservation.isActive()) {
                return null
            }

            // Sender must be the owner or the entrant
            val senderIsOwner = when (sender.deviceID) {
                reservation.ownerId -> true
                reservation.entrantId -> false
                else -> return null
            }

            return Message(
                reservationId = reservation.id,
                lotteryId = reservation.lotteryId,
                campsiteId = reservation.campsiteId,
                senderId = sender.deviceID,
                recipientId = if (senderIsOwner) reservation.entrantId else reservation.ownerId,
                senderIsOwner = senderIsOwner,
                text = body,
                sentAt = now
            )
        }
    }
}