package com.example.myapplication.lottery

import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.lottery.Registration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.features.lottery.JoinWaitlistResult
import com.example.myapplication.core.datetime.AlbertaTime
import com.example.myapplication.features.profile.User
import com.example.myapplication.features.profile.JoinedLottery
import com.example.myapplication.frontEnd.screens.LotteryViews.LotteryInformationScreen
import com.example.myapplication.frontEnd.screens.campsiteViews.CampsiteDetailsScreen
import io.mockk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** Tag-based tests for both leave entry points, using local fixtures instead of Firebase. */
@RunWith(AndroidJUnit4::class)
class LeaveWaitlistScreenTests {
    @get:Rule val compose = createComposeRule()
    private val user = User(userType = "entrant", deviceID = "entrant", name = "Alex", joinedLotteries = listOf(JoinedLottery("lot", "Pine")))
    private val camp = Campsite(name = "Pine", ownerID = "owner", occupancy = 4)
    private val campsites = mapOf("Pine" to camp)
    private fun lottery() = Lottery(id = "lot", ownerId = "owner", campsiteIds = listOf("Pine"),
        waitlist = listOf("entrant"), period = Registration(LocalDateTime.now(AlbertaTime.zone).minusDays(1), LocalDateTime.now(AlbertaTime.zone).plusDays(1)))
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun details(success: Boolean, drawn: Boolean = false, closed: Boolean = false): LotteryRepository {
        val repository = mockk<LotteryRepository>()
        var saved = lottery().copy(drawn = drawn)
        if (closed) saved = saved.copy(period = Registration(LocalDateTime.now(AlbertaTime.zone).minusDays(2), LocalDateTime.now(AlbertaTime.zone).minusDays(1)))
        camp.lotteryCollection = listOf(saved)
        every { repository.leaveLotteryWaitlist(any(), any(), any(), any()) } answers {
            assertEquals("lot", arg<String>(2))
            if (success) { saved = saved.copy(waitlist = emptyMap()); camp.lotteryCollection = listOf(saved) }
            arg<(Boolean) -> Unit>(3)(success)
        }
        every { repository.joinLotteryWaitlist(any(), any(), any(), any()) } answers {
            assertEquals("lot", arg<String>(2))
            saved = saved.copy(waitlist = mapOf("entrant" to user)); camp.lotteryCollection = listOf(saved)
            arg<(JoinWaitlistResult) -> Unit>(3)(JoinWaitlistResult.JOINED)
        }
        compose.setContent { CampsiteDetailsScreen(rememberNavController(), "Pine", user, { camp },
            onJoinWaitlist = repository::joinLotteryWaitlist,
            onLeaveWaitlist = { campsiteId, entrant, lotteryId, complete ->
                repository.leaveLotteryWaitlist(campsiteId, entrant, lotteryId, complete)
            }) }
        return repository
    }

    @Test fun detailsLeaveShowsSuccessAndAllowsJoiningAgain() {
        val repository = details(true)
        node("leaveWaitlistButton").performScrollTo().performClick()
        node("waitlistLeaveMessage").assertTextEquals("You have left this lottery's waitlist.")
        node("alreadyOnWaitlist").assertDoesNotExist()
        node("joinWaitlistButton").performScrollTo().performClick()
        node("alreadyOnWaitlist").assertExists()
        node("leaveWaitlistButton").assertExists()
        verify(exactly = 1) { repository.leaveLotteryWaitlist("Pine", user, "lot", any()) }
    }

    @Test fun detailsFailureKeepsMembershipAndAllowsRetry() {
        details(false)
        node("leaveWaitlistButton").performScrollTo().performClick()
        node("waitlistLeaveMessage").assertTextContains("Could not leave the waitlist.", substring = true)
        node("alreadyOnWaitlist").assertExists()
        node("leaveWaitlistButton").assertIsEnabled()
        node("joinWaitlistButton").assertDoesNotExist()
    }

    @Test fun drawnLotteryDoesNotOfferLeave() {
        details(true, drawn = true)
        node("leaveWaitlistButton").assertDoesNotExist()
    }

    @Test fun closedUndrawnLotteryCanStillBeLeft() {
        details(true, closed = true)
        node("leaveWaitlistButton").performScrollTo().performClick()
        node("waitlistLeaveMessage").assertTextEquals("You have left this lottery's waitlist.")
        node("joinWaitlistButton").assertDoesNotExist()
    }

    @Test fun lotteryPageRetainsSuccessMessageAfterJoinedCardDisappears() {
        val live = mutableStateOf(listOf(lottery()))
        val liveUser = mutableStateOf(user)
        compose.setContent {
            LotteryInformationScreen(liveUser.value, live.value, campsites, {}, {}, onLeaveLottery = { selected, done ->
                assertEquals("lot", selected.id)
                liveUser.value = user.copy(joinedLotteries = emptyList())
                live.value = emptyList(); done(true)
            })
        }
        node("leaveWaitlist_lot").performScrollTo().performClick()
        node("lotteryLeaveMessage").assertTextEquals("You have left the waitlist for Pine.")
        node("lotteryInfo_lot").assertDoesNotExist()
        node("lotteryInformationEmpty").assertExists()
    }

    @Test fun lotteryPagePreventsRepeatedClicksAndOffersRetryAfterFailure() {
        var result: ((Boolean) -> Unit)? = null
        var calls = 0
        compose.setContent { LotteryInformationScreen(user, listOf(lottery()), campsites, {}, {}, onLeaveLottery = { _, complete -> calls++; result = complete }) }
        node("leaveWaitlist_lot").performScrollTo().performClick()
        node("leaveWaitlist_lot").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls); result!!(false) }
        node("lotteryLeaveMessage").assertTextContains("Could not leave the waitlist.", substring = true)
        node("leaveWaitlist_lot").assertIsEnabled()
        node("lotteryInfo_lot").assertExists()
    }

    @Test fun lotteryPageDrawnMembershipHasNoLeaveAction() {
        compose.setContent { LotteryInformationScreen(user, listOf(lottery().copy(drawn = true)), campsites, {}, {}, onLeaveLottery = { _, _ -> fail("Drawn lottery") }) }
        node("lotteryInfo_lot").assertExists()
        node("leaveWaitlist_lot").assertDoesNotExist()
    }

    @Test fun campsiteOwnerInformationDoesNotOfferEntrantLeaveAction() {
        compose.setContent { LotteryInformationScreen(User(userType = "campsite owner", deviceID = "owner"), listOf(lottery()), campsites, {}, {}, onLeaveLottery = { _, _ -> fail("Owner leave") }) }
        node("lotteryInfo_lot").assertExists()
        node("leaveWaitlist_lot").assertDoesNotExist()
    }
}
