package com.example.myapplication.featureModelsTests

import com.example.myapplication.backEnd.featureModels.BookingSchedule
import com.example.myapplication.backEnd.featureModels.Lottery
import com.example.myapplication.backEnd.featureModels.Reservation
import com.example.myapplication.backEnd.featureModels.ReservationStatus
import org.junit.Assert
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
        Assert.assertTrue(reservation.reserve(schedule, "CampA", july(3), july(5), occupancy = 2))
        return reservation
    }


    // ---------------------------------------------------------------
    // Starting state
    // ---------------------------------------------------------------

    @Test
    fun newReservationIsInvitedAndActive() {
        val reservation = invitation()

        Assert.assertEquals(ReservationStatus.INVITED, reservation.status)
        Assert.assertTrue(reservation.isActive(july(1)))
        Assert.assertFalse(reservation.isPast(july(1)))
    }


    // ---------------------------------------------------------------
    // Reserving
    // ---------------------------------------------------------------

    @Test
    fun reserveSetsDatesAndAddsBooking() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        Assert.assertEquals(ReservationStatus.RESERVED, reservation.status)
        Assert.assertEquals("CampA", reservation.campsiteId)
        Assert.assertEquals(july(3), reservation.bookingStartDate)
        Assert.assertEquals(july(5), reservation.bookingEndDate)

        val booking = schedule.getBookings().single()
        Assert.assertEquals("e1", booking.entrantId)
        Assert.assertEquals("CampA", booking.campsiteId)
    }

    @Test
    fun cannotReserveWithAnotherLotterysSchedule() {
        val other =
            BookingSchedule(Lottery(id = "L2", campsiteIds = listOf("CampA")), july(1), july(8))
        val reservation = invitation()

        Assert.assertFalse(reservation.reserve(other, "CampA", july(3), july(5), occupancy = 2))
        Assert.assertEquals(ReservationStatus.INVITED, reservation.status)
    }

    @Test
    fun cannotReserveCampsiteNotInLottery() {
        val reservation = invitation()

        Assert.assertFalse(
            reservation.reserve(
                schedule(),
                "CampZ",
                july(3),
                july(5),
                occupancy = 2
            )
        )
        Assert.assertEquals(ReservationStatus.INVITED, reservation.status)
    }

    @Test
    fun cannotReserveBackwardsDates() {
        val reservation = invitation()

        Assert.assertFalse(
            reservation.reserve(
                schedule(),
                "CampA",
                july(5),
                july(3),
                occupancy = 2
            )
        )
    }

    @Test
    fun cannotReserveFullDates() {
        val schedule = schedule()
        Assert.assertTrue(
            invitation("e1").reserve(
                schedule,
                "CampA",
                july(3),
                july(5),
                occupancy = 1
            )
        )

        val second = invitation("e2")
        Assert.assertFalse(second.reserve(schedule, "CampA", july(4), july(6), occupancy = 1))

        // Still invited, so they can try other dates or decline
        Assert.assertEquals(ReservationStatus.INVITED, second.status)
        Assert.assertTrue(second.campsiteId.isBlank())
    }

    @Test
    fun cannotReserveTwice() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        Assert.assertFalse(reservation.reserve(schedule, "CampB", july(1), july(2), occupancy = 2))
        Assert.assertEquals(1, schedule.getBookings().size)
    }


    // ---------------------------------------------------------------
    // Declining
    // ---------------------------------------------------------------

    @Test
    fun invitedEntrantCanDecline() {
        val reservation = invitation()

        Assert.assertTrue(reservation.decline())
        Assert.assertEquals(ReservationStatus.DECLINED, reservation.status)
        Assert.assertFalse(reservation.isActive(july(1)))
    }

    @Test
    fun cannotDeclineAfterReserving() {
        val reservation = reserved(schedule())

        Assert.assertFalse(reservation.decline())
        Assert.assertEquals(ReservationStatus.RESERVED, reservation.status)
    }


    // ---------------------------------------------------------------
    // Cancelling
    // ---------------------------------------------------------------

    @Test
    fun cancelBeforeStayFreesDates() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        Assert.assertTrue(reservation.cancel(schedule, now = july(1)))
        Assert.assertEquals(ReservationStatus.CANCELLED, reservation.status)
        Assert.assertTrue(schedule.getBookings().isEmpty())
    }

    @Test
    fun cannotCancelOnceStayStarted() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        Assert.assertFalse(reservation.cancel(schedule, now = july(3).plusHours(1)))
        Assert.assertEquals(ReservationStatus.RESERVED, reservation.status)
        Assert.assertEquals(1, schedule.getBookings().size)
    }

    @Test
    fun cannotCancelAnInvitation() {
        val reservation = invitation()

        Assert.assertFalse(reservation.cancel(schedule(), now = july(1)))
        Assert.assertEquals(ReservationStatus.INVITED, reservation.status)
    }


    // ---------------------------------------------------------------
    // Owner rejecting
    // ---------------------------------------------------------------

    @Test
    fun ownerCanRejectReservationAndFreeDates() {
        val schedule = schedule()
        val reservation = reserved(schedule)

        Assert.assertTrue(reservation.reject(schedule))
        Assert.assertEquals(ReservationStatus.REJECTED, reservation.status)
        Assert.assertTrue(schedule.getBookings().isEmpty())
    }

    @Test
    fun ownerCanRejectInvitation() {
        val reservation = invitation()

        Assert.assertTrue(reservation.reject(schedule()))
        Assert.assertEquals(ReservationStatus.REJECTED, reservation.status)
    }

    @Test
    fun cannotRejectDeclinedReservation() {
        val reservation = invitation()
        reservation.decline()

        Assert.assertFalse(reservation.reject(schedule()))
        Assert.assertEquals(ReservationStatus.DECLINED, reservation.status)
    }


    // ---------------------------------------------------------------
    // Active vs past
    // ---------------------------------------------------------------

    @Test
    fun reservedStayIsActiveUntilItEnds() {
        val reservation = reserved(schedule())

        Assert.assertTrue(reservation.isActive(july(4)))
        Assert.assertFalse(reservation.isPast(july(4)))

        Assert.assertFalse(reservation.isActive(july(5)))
        Assert.assertTrue(reservation.isPast(july(5)))
    }

    @Test
    fun cancelledReservationIsNeitherActiveNorPast() {
        val schedule = schedule()
        val reservation = reserved(schedule)
        reservation.cancel(schedule, now = july(1))

        Assert.assertFalse(reservation.isActive(july(6)))
        Assert.assertFalse(reservation.isPast(july(6)))
    }
}