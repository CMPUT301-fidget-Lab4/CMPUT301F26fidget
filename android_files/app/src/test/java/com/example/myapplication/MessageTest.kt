package com.example.myapplication

import com.example.myapplication.backEnd.featureModels.Message
import com.example.myapplication.backEnd.featureModels.Reservation
import com.example.myapplication.backEnd.featureModels.ReservationStatus
import com.example.myapplication.backEnd.userModels.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [Message]: who can send, who receives, and the read flag
 * (US 02.12.01, US 01.14.01, US 03.12.01).
 *
 * Dates are far in the future or past so the tests don't depend on today's date.
 */
class MessageTest {

    private val ownerId = "owner1"
    private val entrantId = "entrant1"

    /** If User becomes abstract, change this to build the matching subclass. */
    private fun user(id: String, type: String = "entrant") =
        User(userType = type, deviceID = id)

    private val owner get() = user(ownerId, "campsite owner")
    private val entrant get() = user(entrantId, "entrant")

    /** An active reservation between owner1 and entrant1. */
    private fun reservation(
        status: ReservationStatus = ReservationStatus.RESERVED,
        bookingStart: String = "2035-07-01T00:00",
        bookingEnd: String = "2035-07-03T00:00"
    ) = Reservation(
        id = "res1",
        lotteryId = "lottery1",
        ownerId = ownerId,
        entrantId = entrantId,
        campsiteId = "CampA",
        bookingStart = bookingStart,
        bookingEnd = bookingEnd,
        status = status
    )


    // ---------------------------------------------------------------
    // Who can send, and to whom
    // ---------------------------------------------------------------

    @Test
    fun ownerMessageGoesToEntrant() {
        val message = Message.create(reservation(), owner, "Hello!")

        assertNotNull(message)
        message!!
        assertEquals(ownerId, message.senderId)
        assertEquals(entrantId, message.recipientId)
        assertTrue(message.senderIsOwner)
    }

    @Test
    fun entrantMessageGoesToOwner() {
        val message = Message.create(reservation(), entrant, "Hi there")

        assertNotNull(message)
        message!!
        assertEquals(entrantId, message.senderId)
        assertEquals(ownerId, message.recipientId)
        assertFalse(message.senderIsOwner)
    }

    @Test
    fun strangerCannotSend() {
        val stranger = user("someoneElse")

        assertNull(Message.create(reservation(), stranger, "Hi"))
    }

    @Test
    fun adminWhoIsNotOnReservationCannotSend() {
        val admin = user("admin1", "admin")

        assertNull(Message.create(reservation(), admin, "Hi"))
    }

    @Test
    fun messageCopiesReservationDetails() {
        val message = Message.create(reservation(), entrant, "Hi", now = 12345L)!!

        assertEquals("res1", message.reservationId)
        assertEquals("lottery1", message.lotteryId)
        assertEquals("CampA", message.campsiteId)
        assertEquals(12345L, message.sentAt)
        assertFalse(message.read)
    }


    // ---------------------------------------------------------------
    // Message text
    // ---------------------------------------------------------------

    @Test
    fun textIsTrimmed() {
        val message = Message.create(reservation(), entrant, "   Hi there   ")!!

        assertEquals("Hi there", message.text)
    }

    @Test
    fun blankTextIsRefused() {
        assertNull(Message.create(reservation(), entrant, ""))
        assertNull(Message.create(reservation(), entrant, "    "))
    }

    @Test
    fun textAtMaxLengthIsAllowed() {
        val text = "a".repeat(Message.MAX_LENGTH)

        assertNotNull(Message.create(reservation(), entrant, text))
    }

    @Test
    fun textOverMaxLengthIsRefused() {
        val text = "a".repeat(Message.MAX_LENGTH + 1)

        assertNull(Message.create(reservation(), entrant, text))
    }


    // ---------------------------------------------------------------
    // Which reservations can be messaged about
    // ---------------------------------------------------------------

    @Test
    fun invitedReservationCanBeMessaged() {
        val invited = reservation(
            status = ReservationStatus.INVITED,
            bookingStart = "",
            bookingEnd = ""
        )

        assertNotNull(Message.create(invited, owner, "Congrats on winning!"))
    }

    @Test
    fun pastReservationCannotBeMessaged() {
        val past = reservation(
            bookingStart = "2020-07-01T00:00",
            bookingEnd = "2020-07-03T00:00"
        )

        assertNull(Message.create(past, entrant, "Thanks!"))
    }

    @Test
    fun endedReservationsCannotBeMessaged() {
        for (status in listOf(
            ReservationStatus.DECLINED,
            ReservationStatus.CANCELLED,
            ReservationStatus.REJECTED
        )) {
            assertNull(
                "Should refuse a $status reservation",
                Message.create(reservation(status = status), entrant, "Hi")
            )
        }
    }

    @Test
    fun reservationMissingOwnerOrIdCannotBeMessaged() {
        assertNull(Message.create(reservation().copy(ownerId = ""), entrant, "Hi"))
        assertNull(Message.create(reservation().copy(entrantId = ""), owner, "Hi"))
        assertNull(Message.create(reservation().copy(id = ""), entrant, "Hi"))
    }


    // ---------------------------------------------------------------
    // Helpers on a message
    // ---------------------------------------------------------------

    @Test
    fun involvesOnlySenderAndRecipient() {
        val message = Message.create(reservation(), entrant, "Hi")!!

        assertTrue(message.involves(entrantId))
        assertTrue(message.involves(ownerId))
        assertFalse(message.involves("someoneElse"))
        assertFalse(message.involves(""))
    }

    @Test
    fun isFromOnlyTheSender() {
        val message = Message.create(reservation(), entrant, "Hi")!!

        assertTrue(message.isFrom(entrantId))
        assertFalse(message.isFrom(ownerId))
        assertFalse(message.isFrom(""))
    }

    @Test
    fun otherParticipantFromEachSide() {
        val message = Message.create(reservation(), entrant, "Hi")!!

        assertEquals(ownerId, message.otherParticipant(entrantId))
        assertEquals(entrantId, message.otherParticipant(ownerId))
        assertNull(message.otherParticipant("someoneElse"))
    }


    // ---------------------------------------------------------------
    // Read flag
    // ---------------------------------------------------------------

    @Test
    fun recipientCanMarkRead() {
        val message = Message.create(reservation(), entrant, "Hi")!!

        assertTrue(message.markRead(ownerId))
        assertTrue(message.read)
    }

    @Test
    fun senderCannotMarkOwnMessageRead() {
        val message = Message.create(reservation(), entrant, "Hi")!!

        assertFalse(message.markRead(entrantId))
        assertFalse(message.read)
    }

    @Test
    fun markingReadTwiceReturnsFalse() {
        val message = Message.create(reservation(), entrant, "Hi")!!

        assertTrue(message.markRead(ownerId))
        assertFalse(message.markRead(ownerId))
        assertTrue(message.read)
    }
}