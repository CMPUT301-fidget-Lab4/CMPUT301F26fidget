package com.example.myapplication

import com.example.myapplication.features.BookingSchedule
import com.example.myapplication.features.Lottery
import com.example.myapplication.features.Reservation
import com.example.myapplication.features.ReservationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReservationTest {

    private fun july(day: Int): LocalDateTime = LocalDate.of(2035, 7, day).atStartOfDay()

    /** Booking period July 1–8 for CampA and CampB. */
    private fun schedule(): BookingSchedule<Lottery> {
        val lottery = Lottery(
            id = "L1",
            ownerId = "owner1",
            campsiteIds = listOf("CampA", "CampB")
        )
        return BookingSchedule(lottery, july(1), july(8))
    }

    private fun invitation(entrantId: String = "e1") = Reservation(
        lotteryId = "L1",
        ownerId = "owner1",
        entrantId = entrantId
    )

    /** A reservation already moved to RESERVED for CampA, July 3–5. */
    private fun reserved(schedule: BookingSchedule<Lottery>): Reservation {
        val reservation = invitation()
        assertTrue(reservation.reserve(schedule, "CampA", july(3), july(5), occupancy = 2))
        return reservation
    }


    // ---------------------------------------------------------------
    // Starting state
    // ---------------------------------------------------------------

    @Test
    fun newReservationIsInvitedAndActive() {
        val reservation = invitation()

        assertEquals(ReservationStatus.INVITED, reservation.status)
        assertTrue(reservation.isActive(july(1)))
        assertFalse(reservation.isPast(july(1)))
    }


    // ---------------------------------------------------------------
    // Reserving
    // ---------------------------------------------------------------

    @Test
    fun reserveSetsDatesAndAddsBooking() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        assertEquals(ReservationStatus.RESERVED, reservation.status)
        assertEquals("CampA", reservation.campsiteId)
        assertEquals(july(3), reservation.bookingStartDate)
        assertEquals(july(5), reservation.bookingEndDate)

        val booking = schedule.getBookings().single()
        assertEquals("e1", booking.entrantId)
        assertEquals("CampA", booking.campsiteId)
    }

    @Test
    fun cannotReserveWithAnotherLotterysSchedule() {
        val other = BookingSchedule(Lottery(id = "L2", campsiteIds = listOf("CampA")), july(1), july(8))
        val reservation = invitation()

        assertFalse(reservation.reserve(other, "CampA", july(3), july(5), occupancy = 2))
        assertEquals(ReservationStatus.INVITED, reservation.status)
    }

    @Test
    fun cannotReserveCampsiteNotInLottery() {
        val reservation = invitation()

        assertFalse(reservation.reserve(schedule(), "CampZ", july(3), july(5), occupancy = 2))
        assertEquals(ReservationStatus.INVITED, reservation.status)
    }

    @Test
    fun cannotReserveBackwardsDates() {
        val reservation = invitation()

        assertFalse(reservation.reserve(schedule(), "CampA", july(5), july(3), occupancy = 2))
    }

    @Test
    fun cannotReserveFullDates() {
        val schedule = schedule()
        assertTrue(invitation("e1").reserve(schedule, "CampA", july(3), july(5), occupancy = 1))

        val second = invitation("e2")
        assertFalse(second.reserve(schedule, "CampA", july(4), july(6), occupancy = 1))

        // Still invited, so they can try other dates or decline
        assertEquals(ReservationStatus.INVITED, second.status)
        assertTrue(second.campsiteId.isBlank())
    }

    @Test
    fun cannotReserveTwice() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        assertFalse(reservation.reserve(schedule, "CampB", july(1), july(2), occupancy = 2))
        assertEquals(1, schedule.getBookings().size)
    }


    // ---------------------------------------------------------------
    // Declining
    // ---------------------------------------------------------------

    @Test
    fun invitedEntrantCanDecline() {
        val reservation = invitation()

        assertTrue(reservation.decline())
        assertEquals(ReservationStatus.DECLINED, reservation.status)
        assertFalse(reservation.isActive(july(1)))
    }

    @Test
    fun cannotDeclineAfterReserving() {
        val reservation = reserved(schedule())

        assertFalse(reservation.decline())
        assertEquals(ReservationStatus.RESERVED, reservation.status)
    }


    // ---------------------------------------------------------------
    // Cancelling
    // ---------------------------------------------------------------

    @Test
    fun cancelBeforeStayFreesDates() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        assertTrue(reservation.cancel(schedule, now = july(1)))
        assertEquals(ReservationStatus.CANCELLED, reservation.status)
        assertTrue(schedule.getBookings().isEmpty())
    }

    @Test
    fun cannotCancelOnceStayStarted() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        assertFalse(reservation.cancel(schedule, now = july(3).plusHours(1)))
        assertEquals(ReservationStatus.RESERVED, reservation.status)
        assertEquals(1, schedule.getBookings().size)
    }

    @Test
    fun cannotCancelAnInvitation() {
        val reservation = invitation()

        assertFalse(reservation.cancel(schedule(), now = july(1)))
        assertEquals(ReservationStatus.INVITED, reservation.status)
    }


    // ---------------------------------------------------------------
    // Owner rejecting
    // ---------------------------------------------------------------

    @Test
    fun ownerCanRejectReservationAndFreeDates() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        assertTrue(reservation.reject(schedule))
        assertEquals(ReservationStatus.REJECTED, reservation.status)
        assertTrue(schedule.getBookings().isEmpty())
    }

    @Test
    fun ownerCanRejectInvitation() {
        val reservation = invitation()

        assertTrue(reservation.reject(schedule()))
        assertEquals(ReservationStatus.REJECTED, reservation.status)
    }

    @Test
    fun cannotRejectDeclinedReservation() {
        val reservation = invitation()
        reservation.decline()

        assertFalse(reservation.reject(schedule()))
        assertEquals(ReservationStatus.DECLINED, reservation.status)
    }


    // ---------------------------------------------------------------
    // Active vs past
    // ---------------------------------------------------------------

    @Test
    fun reservedStayIsActiveUntilItEnds() {
        val reservation = reserved(schedule())

        assertTrue(reservation.isActive(july(4)))
        assertFalse(reservation.isPast(july(4)))

        assertFalse(reservation.isActive(july(5)))
        assertTrue(reservation.isPast(july(5)))
    }

    @Test
    fun cancelledReservationIsNeitherActiveNorPast() {
        val schedule = schedule()
        val reservation = reserved(schedule)
        reservation.cancel(schedule, now = july(1))

        assertFalse(reservation.isActive(july(6)))
        assertFalse(reservation.isPast(july(6)))
    }
}