package com.example.myapplication.RepoTests

import com.example.myapplication.backEnd.featureModels.BookingSchedule
import com.example.myapplication.backEnd.featureModels.Lottery
import com.example.myapplication.backEnd.featureModels.Reservation
import com.example.myapplication.backEnd.featureModels.ReservationStatus
import org.junit.Assert
import org.junit.Test
import java.time.LocalDateTime

class ReservationRebookingTest {
    private val now = LocalDateTime.of(2035, 1, 1, 0, 0)
    private fun schedule() = BookingSchedule(
        Lottery(id = "lottery", campsiteIds = listOf("camp")),
        now.plusDays(1), now.plusDays(10)
    )
    private fun reservation() = Reservation(lotteryId = "lottery", entrantId = "entrant")

    @Test
    fun unrejectRestoresInvitationWithoutReclaimingOccupiedDates() {
        val schedule = schedule()
        val reservation = reservation()
        Assert.assertTrue(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(1),
                now.plusDays(2),
                1,
                now
            )
        )
        Assert.assertTrue(reservation.reject(schedule))
        Assert.assertTrue(
            schedule.addBooking(
                BookingSchedule.Booking(
                    "other",
                    "camp",
                    now.plusDays(1),
                    now.plusDays(2)
                ), 1
            )
        )
        Assert.assertTrue(reservation.unreject())
        Assert.assertEquals(ReservationStatus.INVITED, reservation.status)
        Assert.assertEquals("", reservation.campsiteId)
        Assert.assertNull(reservation.bookingStartDate)
        Assert.assertNull(reservation.bookingEndDate)
        Assert.assertEquals("other", schedule.getBookings().single().entrantId)
        Assert.assertFalse(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(1),
                now.plusDays(2),
                1,
                now
            )
        )
        Assert.assertTrue(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(3),
                now.plusDays(4),
                1,
                now
            )
        )
    }

    @Test
    fun unrejectOnlyChangesRejectedReservations() {
        ReservationStatus.entries.filter { it != ReservationStatus.REJECTED }.forEach { status ->
            val reservation = reservation().apply { this.status = status }
            val before = reservation.copy()
            Assert.assertFalse(reservation.unreject())
            Assert.assertEquals(before, reservation)
        }
    }

    @Test
    fun cancellationFreesOldDatesAndAllowsOneNewBooking() {
        val schedule = schedule()
        val reservation = reservation()
        Assert.assertTrue(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(1),
                now.plusDays(2),
                1,
                now
            )
        )
        Assert.assertTrue(reservation.cancel(schedule, now))
        Assert.assertTrue(schedule.getBookings().isEmpty())
        Assert.assertTrue(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(3),
                now.plusDays(4),
                1,
                now
            )
        )
        Assert.assertEquals(ReservationStatus.RESERVED, reservation.status)
        Assert.assertEquals(now.plusDays(3), schedule.getBookings().single().startDate)
        Assert.assertFalse(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(5),
                now.plusDays(6),
                1,
                now
            )
        )
    }

    @Test
    fun rebookingStillEnforcesWindowCapacityAndCampsite() {
        val schedule = schedule()
        val reservation = reservation().apply { status = ReservationStatus.CANCELLED }
        schedule.addBooking(BookingSchedule.Booking("other", "camp", now.plusDays(3), now.plusDays(4)), 1)
        Assert.assertFalse(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(3),
                now.plusDays(4),
                1,
                now
            )
        )
        Assert.assertFalse(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(9),
                now.plusDays(11),
                1,
                now
            )
        )
        Assert.assertFalse(
            reservation.reserve(
                schedule,
                "unrelated",
                now.plusDays(5),
                now.plusDays(6),
                1,
                now
            )
        )
        Assert.assertFalse(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(1),
                now.plusDays(2),
                1,
                now.plusDays(2)
            )
        )
        Assert.assertEquals(ReservationStatus.CANCELLED, reservation.status)
        Assert.assertEquals(1, schedule.getBookings().size)
    }

    @Test
    fun managementCancellationFreesDatesButDoesNotAllowRebooking() {
        val schedule = schedule()
        val reservation = reservation()
        Assert.assertTrue(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(1),
                now.plusDays(2),
                1,
                now
            )
        )
        Assert.assertTrue(reservation.reject(schedule))
        Assert.assertTrue(schedule.getBookings().isEmpty())
        Assert.assertEquals(ReservationStatus.REJECTED, reservation.status)
        Assert.assertFalse(
            reservation.reserve(
                schedule,
                "camp",
                now.plusDays(3),
                now.plusDays(4),
                1,
                now
            )
        )
    }
}