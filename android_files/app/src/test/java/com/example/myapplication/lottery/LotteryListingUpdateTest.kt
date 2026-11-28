package com.example.myapplication.lottery

import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.lottery.Registration
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.features.profile.User
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/** Tests repository-owned validation with a fixed clock and no Firebase connection. */
class LotteryListingUpdateTest {
    private val now = LocalDateTime.of(2035, 1, 1, 0, 0)
    private val owner = User(userType = "campsite owner", deviceID = "owner")
    private val lottery = Lottery(id = "lot", ownerId = "owner", campsiteIds = listOf("a", "b"), waitlist = listOf("one", "two"))
    private val campsites = mapOf("a" to Campsite(ownerID = "owner", occupancy = 4), "b" to Campsite(ownerID = "owner", occupancy = 3))
    private val defaultStart = now.plusDays(3)
    private val schedule = BookingSchedule(lottery, defaultStart, defaultStart.plusDays(3))

    private fun validate(start: String = now.minusDays(1).toString(), end: String = now.plusDays(2).toString(),
        winners: String = "2", capacity: String = "5", selected: Lottery = lottery,
        sites: Map<String, Campsite> = campsites, booking: BookingSchedule<Lottery>? = schedule): String? =
        LotteryRepository.validateLotteryListingInputs(owner, selected, sites, start, end, winners, capacity, booking, now)

    @Test fun validChangesAndUnlimitedCapacityAreAccepted() {
        assertNull(validate())
        assertNull(validate(capacity = "0"))
        assertNull(validate(end = defaultStart.toString()))
    }

    @Test fun missingAndMalformedInputIsRejected() {
        assertNotNull(validate(start = ""))
        assertNotNull(validate(end = "bad date"))
        assertNotNull(validate(winners = ""))
        assertNotNull(validate(winners = "1.5"))
        assertNotNull(validate(capacity = ""))
        assertNotNull(validate(capacity = "999999999999"))
    }

    @Test fun invalidNumberRangesAndWaitlistReductionAreRejected() {
        assertNotNull(validate(winners = "0"))
        assertNotNull(validate(winners = "4"))
        assertNotNull(validate(capacity = "-1"))
        assertNotNull(validate(capacity = "1"))
    }

    @Test fun invalidDatesCannotCloseAfterStayStartsOrInThePast() {
        assertNotNull(validate(end = now.toString()))
        assertNotNull(validate(end = now.minusDays(2).toString()))
        assertNotNull(validate(start = now.plusDays(3).toString()))
        assertNotNull(validate(end = now.plusDays(5).toString()))
    }

    @Test fun registrationMustCloseByTheEarliestDefaultOrCampsiteBookingStart() {
        val customStart = defaultStart.minusDays(1)
        val customWindow = BookingSchedule.Booking(campsiteId = "b", startDate = customStart, endDate = customStart.plusDays(2))
        val customSchedule = BookingSchedule(lottery, defaultStart, defaultStart.plusDays(3), mapOf("b" to customWindow))
        assertNull(validate(end = customStart.toString(), booking = customSchedule))
        assertEquals("Registration must close by the time booking starts.",
            validate(end = customStart.plusMinutes(1).toString(), booking = customSchedule))
    }

    @Test fun drawnOrMissingCampsitesAreRejected() {
        assertNotNull(validate(selected = lottery.copy(drawn = true)))
        assertNotNull(validate(sites = campsites.filterKeys { it == "a" }))
        assertNotNull(validate(booking = null))
    }

    @Test fun sharedLotteryRequiresEveryCampsiteUnlessAdmin() {
        val shared = campsites + ("b" to Campsite(ownerID = "other"))
        assertFalse(LotteryRepository.canEditLotteryListing(owner, lottery, shared))
        assertTrue(LotteryRepository.canEditLotteryListing(User(userType = "admin", deviceID = "admin"), lottery, shared))
        assertFalse(LotteryRepository.canEditLotteryListing(owner.copy(userType = "entrant"), lottery, campsites))
        assertFalse(LotteryRepository.canEditLotteryListing(null, lottery, campsites))
        assertFalse(LotteryRepository.canEditLotteryListing(owner, lottery.copy(drawn = true), campsites))
    }
}