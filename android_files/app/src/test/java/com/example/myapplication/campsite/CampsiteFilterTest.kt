package com.example.myapplication.campsite

import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.campsite.CampsiteLocation
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.frontEnd.screens.campsiteViews.matchesCapacity
import com.example.myapplication.frontEnd.screens.campsiteViews.matchesDate
import com.example.myapplication.frontEnd.screens.campsiteViews.matchesLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CampsiteFilterTest {

    private fun campsite(address: String? = "1 Main St, Edmonton, AB", occupancy: Int = 10) =
        Campsite(
            name = "Alpha",
            location = address?.let { CampsiteLocation(address = it) },
            occupancy = occupancy
        )

    // ---- location ----

    @Test fun location_blankQueryMatchesEverything() {
        assertTrue(matchesLocation(campsite(), ""))
        assertTrue(matchesLocation(campsite(address = null), "  "))
    }

    @Test fun location_matchesIgnoringCase() {
        assertTrue(matchesLocation(campsite(), "edmonton"))
    }

    @Test fun location_allWordsMustMatch() {
        assertTrue(matchesLocation(campsite(), "Edmonton AB"))
        assertFalse(matchesLocation(campsite(), "Edmonton Calgary"))
    }

    @Test fun location_noMatchAndNullLocation() {
        assertFalse(matchesLocation(campsite(), "Vancouver"))
        assertFalse(matchesLocation(campsite(address = null), "Edmonton"))
    }

    // ---- capacity ----

    @Test fun capacity_nullMatchesEverything() {
        assertTrue(matchesCapacity(campsite(occupancy = 4), null))
    }

    @Test fun capacity_isAtLeastN() {
        assertTrue(matchesCapacity(campsite(occupancy = 10), 4))
        assertTrue(matchesCapacity(campsite(occupancy = 4), 4))
        assertFalse(matchesCapacity(campsite(occupancy = 4), 5))
    }

    // ---- date ----

    private fun schedulesFor(id: String): Map<String, List<BookingSchedule<Lottery>>> {
        val lottery = Lottery(id = "l1", ownerId = "owner", campsiteIds = listOf(id))
        val schedule = BookingSchedule(
            registration = lottery,
            bookingStart = LocalDateTime.of(2026, 12, 10, 0, 0),
            bookingEnd = LocalDateTime.of(2026, 12, 20, 0, 0)
        )
        return mapOf(id to listOf(schedule))
    }

    @Test fun date_noDateOrSchedulesNotLoadedMatchesEverything() {
        assertTrue(matchesDate("Alpha", campsite(), null, schedulesFor("Alpha")))
        assertTrue(matchesDate("Alpha", campsite(), LocalDate.of(2026, 12, 15), null))
    }

    @Test fun date_insideWindowMatches() {
        assertTrue(matchesDate("Alpha", campsite(), LocalDate.of(2026, 12, 15), schedulesFor("Alpha")))
    }

    @Test fun date_outsideWindowDoesNotMatch() {
        assertFalse(matchesDate("Alpha", campsite(), LocalDate.of(2026, 12, 25), schedulesFor("Alpha")))
    }

    @Test fun date_campsiteWithNoScheduleDoesNotMatch() {
        assertFalse(matchesDate("Beta", campsite(), LocalDate.of(2026, 12, 15), schedulesFor("Alpha")))
    }

    @Test fun date_fullDayDoesNotMatch() {
        val schedules = schedulesFor("Alpha")
        schedules.getValue("Alpha").first().restoreBooking(
            BookingSchedule.Booking(
                entrantId = "entrant1",
                campsiteId = "Alpha",
                startDate = LocalDateTime.of(2026, 12, 15, 0, 0),
                endDate = LocalDateTime.of(2026, 12, 16, 0, 0)
            )
        )
        val full = campsite(occupancy = 1)
        assertFalse(matchesDate("Alpha", full, LocalDate.of(2026, 12, 15), schedules))
        assertTrue(matchesDate("Alpha", full, LocalDate.of(2026, 12, 16), schedules))
    }
}