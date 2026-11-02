package com.example.myapplication


import com.example.myapplication.features.Campsite
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import kotlin.collections.emptyList

/**
 * US 02.04.03: Owner configures registration date/time.
 * Local JVM tests; does not require Firebase or an Android emulator.
 * Assumes Reservation and Campsite use LocalDateTime.
 */
class RegistrationPeriodTest {

    private val start = LocalDateTime.now().plusDays(10).withSecond(0).withNano(0)
    private val end = start.plusHours(8)

    private fun newCampsite() =
        Campsite(name = "TestCampsite", occupancy = 5)

    private fun newCampsiteWithLottery() =
        newCampsite().apply {
            assertTrue(createLottery(2, 0, emptyList()))
        }

    @Test
    fun cannotSetRegistrationUntilLotteryExists() {
        val campsite = newCampsite()
        assertFalse(campsite.setReservation(start, end))
        assertFalse(campsite.reservationSet)
    }

    @Test
    fun canSetValidRegistrationDateAndTime() {
        val campsite = newCampsiteWithLottery()
        assertTrue(campsite.setReservation(start, end))
        assertEquals(start, campsite.lottery.period.registrationStart)
        assertEquals(end, campsite.lottery.period.registrationEnd)
        assertTrue(campsite.reservationSet)
    }

    @Test
    fun closingTimeMustBeAfterOpeningTime() {
        val campsite = newCampsiteWithLottery()
        assertFalse(campsite.setReservation(start, start))
        assertFalse(campsite.setReservation(start, start.minusMinutes(1)))
        assertFalse(campsite.reservationSet)
    }

    @Test
    fun registrationCannotStartInThePast() {
        val campsite = newCampsiteWithLottery()
        val yesterday = LocalDateTime.now().minusDays(1)
        assertFalse(campsite.setReservation(yesterday, yesterday.plusDays(2)))
    }

    @Test
    fun missingAndInvalidFormValuesFailParsing() {
        val badInputs = listOf(
            "",
            " ",
            "not-a-date",
            "2035-02-30T09:00",
            "2035-06-10", // Date only; time is required
            "2035-06-10T25:00"
        )
        for (input in badInputs) {
            assertTrue(
                "Expected parse error for '$input'",
                runCatching { LocalDateTime.parse(input) }.isFailure
            )
        }
        assertEquals(
            LocalDateTime.of(2035, 6, 10, 9, 0),
            LocalDateTime.parse("2035-06-10T09:00")
        )
    }

    @Test
    fun bookingCannotBeginBeforeRegistrationCloses() {
        val campsite = newCampsiteWithLottery()
        assertTrue(campsite.setReservation(start, end))
        assertFalse(campsite.setBookingPeriod(end.minusMinutes(1), end.plusDays(1)))
        assertTrue(campsite.setBookingPeriod(end, end.plusDays(1)))
    }

    @Test
    fun changingRegistrationCannotOverlapExistingBooking() {
        val campsite = newCampsiteWithLottery()
        assertTrue(campsite.setReservation(start, end))
        assertTrue(campsite.setBookingPeriod(end.plusHours(1), end.plusDays(1)))
        assertFalse(campsite.setReservation(start, end.plusHours(2)))
        assertEquals(end, campsite.lottery.period.registrationEnd)
    }
}
