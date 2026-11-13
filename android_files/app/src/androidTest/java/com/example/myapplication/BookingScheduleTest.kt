package com.example.myapplication

import com.example.myapplication.features.BookingSchedule
import com.example.myapplication.features.Lottery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BookingScheduleTest {

    /** Midnight on a day in July 2035. */
    private fun july(day: Int): LocalDateTime = LocalDate.of(2035, 7, day).atStartOfDay()

    /** Booking period: July 1 to July 8, so nights July 1–7. */
    private fun schedule(): BookingSchedule<Lottery> {
        val lottery = Lottery(
            id = "L1",
            ownerId = "owner1",
            campsiteIds = listOf("CampA", "CampB")
        )
        return BookingSchedule(lottery, july(1), july(8))
    }

    @Test
    fun bookingInsidePeriodIsAdded() {
        val schedule = schedule()
        val booking = schedule.Booking("e1", "CampA", july(2), july(4))

        assertTrue(schedule.addBooking(booking, occupancy = 2))
        assertEquals(1, schedule.getBookings().size)
    }

    @Test
    fun bookingOutsidePeriodIsRefused() {
        val schedule = schedule()

        // Starts before the period
        assertFalse(schedule.addBooking(schedule.Booking("e1", "CampA", july(1).minusDays(1), july(3)), 2))

        // Ends after the period
        assertFalse(schedule.addBooking(schedule.Booking("e1", "CampA", july(6), july(9)), 2))

        assertTrue(schedule.getBookings().isEmpty())
    }

    @Test
    fun entrantCanOnlyBookOnce() {
        val schedule = schedule()

        assertTrue(schedule.addBooking(schedule.Booking("e1", "CampA", july(1), july(2)), 5))
        assertFalse(schedule.addBooking(schedule.Booking("e1", "CampB", july(4), july(5)), 5))
    }

    @Test
    fun fullCampsiteRefusesOverlappingBooking() {
        val schedule = schedule()

        assertTrue(schedule.addBooking(schedule.Booking("e1", "CampA", july(1), july(3)), occupancy = 1))

        // Overlaps the night of July 2
        assertFalse(schedule.addBooking(schedule.Booking("e2", "CampA", july(2), july(4)), occupancy = 1))
    }

    @Test
    fun checkoutDayCanBeBookedByNextPerson() {
        val schedule = schedule()

        // e1 stays nights of July 1 and 2, leaves the morning of July 3
        assertTrue(schedule.addBooking(schedule.Booking("e1", "CampA", july(1), july(3)), occupancy = 1))

        // e2 arrives July 3
        assertTrue(schedule.addBooking(schedule.Booking("e2", "CampA", july(3), july(5)), occupancy = 1))
    }

    @Test
    fun otherCampsitesDoNotCountTowardOccupancy() {
        val schedule = schedule()

        assertTrue(schedule.addBooking(schedule.Booking("e1", "CampA", july(1), july(3)), occupancy = 1))
        assertTrue(schedule.addBooking(schedule.Booking("e2", "CampB", july(1), july(3)), occupancy = 1))
    }

    @Test
    fun occupancyAllowsSeveralPeoplePerNight() {
        val schedule = schedule()

        assertTrue(schedule.addBooking(schedule.Booking("e1", "CampA", july(1), july(3)), occupancy = 2))
        assertTrue(schedule.addBooking(schedule.Booking("e2", "CampA", july(1), july(3)), occupancy = 2))
        assertFalse(schedule.addBooking(schedule.Booking("e3", "CampA", july(1), july(3)), occupancy = 2))
    }

    @Test
    fun availableDatesSkipFullNights() {
        val schedule = schedule()
        schedule.addBooking(schedule.Booking("e1", "CampA", july(1), july(3)), occupancy = 1)

        val available = schedule.availableDates("CampA", occupancy = 1)

        val expected = (3..7).map { LocalDate.of(2035, 7, it) }
        assertEquals(expected, available)

        // CampB is untouched
        assertEquals(7, schedule.availableDates("CampB", occupancy = 1).size)
    }

    @Test
    fun bookedDatesShowNightsWithBookings() {
        val schedule = schedule()
        schedule.addBooking(schedule.Booking("e1", "CampA", july(4), july(6)), occupancy = 3)

        assertEquals(
            listOf(LocalDate.of(2035, 7, 4), LocalDate.of(2035, 7, 5)),
            schedule.bookedDates("CampA")
        )
        assertTrue(schedule.bookedDates("CampB").isEmpty())
    }

    @Test
    fun removingBookingFreesTheDates() {
        val schedule = schedule()
        schedule.addBooking(schedule.Booking("e1", "CampA", july(1), july(3)), occupancy = 1)

        assertTrue(schedule.removeBooking("e1"))
        assertTrue(schedule.getBookings().isEmpty())
        assertTrue(schedule.addBooking(schedule.Booking("e2", "CampA", july(1), july(3)), occupancy = 1))
    }

    @Test
    fun getBookingsAtFiltersByCampsite() {
        val schedule = schedule()
        schedule.addBooking(schedule.Booking("e1", "CampA", july(1), july(3)), occupancy = 5)
        schedule.addBooking(schedule.Booking("e2", "CampB", july(1), july(3)), occupancy = 5)

        val noon = july(2).plusHours(12)

        assertEquals(2, schedule.getBookingsAt(noon).size)
        assertEquals(1, schedule.getBookingsAt(noon, "CampA").size)
    }
}