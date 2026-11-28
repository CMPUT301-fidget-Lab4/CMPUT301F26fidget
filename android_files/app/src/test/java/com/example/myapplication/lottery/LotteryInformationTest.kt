package com.example.myapplication.lottery

import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.lottery.Registration
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.screens.LotteryViews.*
import org.junit.Assert.*
import com.example.myapplication.data.firebase.LotteryRepository.Companion.canViewLotteryWaitlist
import com.example.myapplication.data.firebase.LotteryRepository.Companion.currentCampsiteLottery
import org.junit.Test
import java.time.LocalDateTime

class LotteryInformationTest {
    private val start = LocalDateTime.of(2035, 1, 1, 0, 0)
    private val camp = Campsite(name = "camp", ownerID = "owner")
    private val campsites = mapOf("camp" to camp)
    private fun lottery(id: String = "lot", owner: String = "owner") = Lottery(
        id = id, ownerId = owner, campsiteIds = listOf("camp"),
        period = Registration(start, start.plusDays(2)), waitlist = listOf("entrant"), waitlistCapacity = 3
    )

    @Test fun ownersCannotReadAnotherOwnersWaitlist() {
        assertTrue(canViewLotteryWaitlist(User(userType = "campsite owner", deviceID = "owner"), lottery(), campsites))
        assertFalse(canViewLotteryWaitlist(User(userType = "campsite owner", deviceID = "stranger"), lottery(), campsites))
        assertFalse(canViewLotteryWaitlist(User(userType = "entrant", deviceID = "owner"), lottery(), campsites))
        assertFalse(canViewLotteryWaitlist(null, lottery(), campsites))
    }

    @Test fun ownerOfSecondaryCampsiteCanSeeSharedWaitlist() {
        val shared = lottery().copy(campsites = mapOf("primary" to null, "camp" to camp), ownerId = "primaryOwner")
        assertTrue(canViewLotteryWaitlist(User(userType = "campsite owner", deviceID = "owner"), shared, campsites))
    }



    @Test fun countIncludesUnresolvedProfilesAndZeroCapacityMeansUnlimited() {
        assertEquals("Waitlist: 1 / 3 joined", waitlistSummary(lottery()))
        assertEquals("Waitlist: 1 / 1 joined (full)", waitlistSummary(lottery().copy(waitlistCapacity = 1)))
        assertEquals("Waitlist: 1 joined (no limit)", waitlistSummary(lottery().copy(waitlistCapacity = 0)))
    }

    @Test fun campsiteSearchUsesMatchingUndrawnLotteryInsteadOfHistoryOrFlag() {
        val drawn = lottery("drawn").copy(drawn = true, period = Registration(start.plusDays(5), start.plusDays(6)))
        val unrelated = lottery("unrelated").copy(campsites = mapOf("elsewhere" to null))
        camp.inLottery = false
        camp.lotteryCollection = listOf(drawn, unrelated, lottery())
        assertEquals("lot", currentCampsiteLottery(camp)?.id)
        camp.lotteryCollection = listOf(drawn, unrelated)
        assertNull(currentCampsiteLottery(camp))
    }

    @Test fun registrationBoundariesAreAccurateEvenBeforeDraw() {
        val lottery = lottery()
        assertEquals("Registration has not opened", lotteryRegistrationStatus(lottery, start.minusSeconds(1)))
        assertEquals("Registration open", lotteryRegistrationStatus(lottery, start))
        assertEquals("Registration closed", lotteryRegistrationStatus(lottery, start.plusDays(2)))
        assertEquals("Lottery drawn", lotteryRegistrationStatus(lottery.copy(drawn = true), start))
    }




}
