package com.example.myapplication.lottery

import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.lottery.Registration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.example.myapplication.data.firebase.LotteryRepository
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.profile.User
import com.example.myapplication.features.profile.JoinedLottery
import com.example.myapplication.frontEnd.screens.LotteryViews.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** Tag-based listing edit tests use callback fixtures and do not connect to Firebase. */
@RunWith(AndroidJUnit4::class)
class EditLotteryScreenTests {
    @get:Rule val compose = createComposeRule()
    private val owner = User(userType = "campsite owner", deviceID = "owner")
    private val campsites = mapOf("camp" to Campsite(name = "camp", ownerID = "owner", occupancy = 4))
    private val start = LocalDateTime.of(2035, 1, 1, 0, 0)
    private val lottery = Lottery(id = "lot", ownerId = "owner", campsiteIds = listOf("camp"),
        numWinners = 2, waitlistCapacity = 5, period = Registration(start, start.plusDays(2)), waitlist = listOf("entrant", "other"))
    private val schedule = BookingSchedule(lottery, start.plusDays(3), start.plusDays(5))
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun save() = node("editLotterySave").performScrollTo().performClick()

    @Test fun compactLayoutWithLargeTextCanReachSave() {
        var submitted = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.8f)) {
                Box(Modifier.size(width = 280.dp, height = 220.dp)) {
                    render(owner, lottery, campsites, { _, done ->
                        submitted = true
                        done(Result.success(Unit))
                    }, {}, schedule)
                }
            }
        }
        node("editLotteryStartDateButton").performScrollTo().assertIsDisplayed()
        node("editLotteryStartTimeButton").performScrollTo().assertIsDisplayed()
        save()
        compose.runOnIdle { assertTrue(submitted) }
    }

    @Test fun formPrefillsCurrentDetailsAndValidChangesAreSubmitted() {
        var submitted: Map<String, String>? = null
        var back = 0
        compose.setContent { render(owner, lottery, campsites, { value, done -> submitted = value; done(Result.success(Unit)) }, { back++ }, schedule) }
        node("editLotteryCampsites").assertTextEquals("camp")
        node("editLotteryWinners").assertTextContains("2")
        node("editLotteryCapacity").assertTextContains("5")
        node("editLotteryStartDateButton").assertExists()
        node("editLotteryEndDateButton").assertExists()
        node("editLotteryWinners").performTextReplacement("3")
        node("editLotteryCapacity").performTextReplacement("8")
        save()
        compose.runOnIdle {
            assertEquals(3, submitted?.get("numWinners")?.toInt())
            assertEquals(8, submitted?.get("waitlistCapacity")?.toInt())
            assertEquals(start, submitted?.get("registrationStart")?.let(LocalDateTime::parse))
            assertEquals(start.plusDays(2), submitted?.get("registrationEnd")?.let(LocalDateTime::parse))
            assertEquals(1, back)
        }
    }

    @Test fun blankOrInvalidWinnersShowErrorWithoutSaving() {
        var calls = 0
        compose.setContent { render(owner, lottery, campsites, { _, _ -> calls++ }, {}, schedule) }
        node("editLotteryWinners").performTextReplacement("")
        save()
        node("editLotteryError").assertExists()
        node("editLotteryWinners").performScrollTo().performTextReplacement("0")
        save()
        node("editLotteryError").assertTextEquals("Number of winners must be at least 1.")
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun capacityBelowCurrentMembershipDoesNotSave() {
        var calls = 0
        compose.setContent { render(owner, lottery, campsites, { _, _ -> calls++ }, {}, schedule) }
        node("editLotteryCapacity").performTextReplacement("1")
        save()
        node("editLotteryError").assertTextEquals("Capacity cannot be less than the current waitlist.")
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun reversedRegistrationDatesDoNotSave() {
        var calls = 0
        val invalid = lottery.copy(period = Registration(start.plusDays(3), start))
        compose.setContent { render(owner, invalid, campsites, { _, _ -> calls++ }, {}, schedule) }
        save()
        node("editLotteryError").assertTextEquals("Registration must end after it starts.")
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun registrationPastBookingStartShowsErrorBeforeSaving() {
        val earlySchedule = BookingSchedule(lottery, start.plusDays(1), start.plusDays(5))
        compose.setContent {
            render(owner, lottery, campsites, { _, _ -> fail("Invalid schedule submitted") }, {}, earlySchedule)
        }
        save()
        node("editLotteryError").assertTextEquals("Registration must close by the time booking starts.")
    }

    @Test fun entrantCannotOpenEditorEvenWithMatchingOwnerId() {
        compose.setContent { render(owner.copy(userType = "entrant"), lottery, campsites, { _, _ -> fail("Unauthorized save") }, {}, schedule) }
        node("editLotteryDenied").assertIsDisplayed()
        node("editLotterySave").assertDoesNotExist()
    }

    @Test fun unrelatedOwnerCannotOpenEditor() {
        compose.setContent { render(owner.copy(deviceID = "otherOwner"), lottery, campsites, { _, _ -> fail("Unauthorized save") }, {}, schedule) }
        node("editLotteryDenied").assertIsDisplayed()
        node("editLotteryWinners").assertDoesNotExist()
    }

    @Test fun administratorCanEditAnotherOwnersListing() {
        var calls = 0
        compose.setContent { render(owner.copy(userType = "admin", deviceID = "admin"), lottery, campsites, { _, done -> calls++; done(Result.success(Unit)) }, {}, schedule) }
        node("editLotteryTitle").assertExists()
        save()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun failedSaveKeepsEditsAndDisplaysErrorForRetry() {
        var back = 0
        compose.setContent { render(owner, lottery, campsites, { _, done -> done(Result.failure(IllegalStateException("Permission changed. Reopen this listing."))) }, { back++ }, schedule) }
        node("editLotteryCapacity").performTextReplacement("8")
        save()
        node("editLotteryError").assertTextEquals("Permission changed. Reopen this listing.")
        node("editLotteryCapacity").performScrollTo().assertTextContains("8")
        node("editLotterySave").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertEquals(0, back) }
    }

    @Test fun pendingSaveDisablesRepeatedSubmissionsAndCancellation() {
        var complete: ((Result<Unit>) -> Unit)? = null
        var calls = 0
        compose.setContent { render(owner, lottery, campsites, { _, done -> calls++; complete = done }, {}, schedule) }
        save()
        node("editLotterySave").assertIsNotEnabled()
        node("editLotteryBack").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls); complete!!(Result.failure(IllegalStateException("Try again"))) }
        node("editLotterySave").assertIsEnabled()
    }

    @Test fun ownerInformationScreenOpensSelectedListing() {
        var selected: String? = null
        compose.setContent { LotteryInformationScreen(owner, listOf(lottery), campsites, {}, {}, onEditLottery = { selected = it }) }
        node("editLottery_lot").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("lot", selected) }
    }

    @Test fun entrantInformationScreenNeverOffersEditing() {
        val entrant = User(userType = "entrant", deviceID = "entrant", joinedLotteries = listOf(JoinedLottery("lot", "camp")))
        compose.setContent { LotteryInformationScreen(entrant, listOf(lottery), campsites, {}, {}, onEditLottery = { fail("Entrant edit") }) }
        node("lotteryInfo_lot").assertExists()
        node("editLottery_lot").assertDoesNotExist()
    }

    @Test fun drawnLotteryHasNoEditAction() {
        compose.setContent { LotteryInformationScreen(owner, listOf(lottery.copy(drawn = true)), campsites, {}, {}, onEditLottery = { fail("Drawn edit") }) }
        node("editLottery_lot").assertDoesNotExist()
    }

    /** Mirrors the existing navigation controller: validation is delegated to the repository. */
    @Composable
    private fun render(user: User?, selected: Lottery?, sites: Map<String, Campsite>,
        onPersist: (Map<String, String>, (Result<Unit>) -> Unit) -> Unit, onBack: () -> Unit,
        bookingSchedule: BookingSchedule<Lottery>?) {
        EditLotteryScreen(selected,
            canEdit = selected?.let { LotteryRepository.canEditLotteryListing(user, it, sites) } == true,
            onSave = { start, end, winners, capacity, complete ->
                val error = LotteryRepository.validateLotteryListingInputs(user, selected, sites, start, end, winners, capacity, bookingSchedule)
                if (error != null) complete(Result.failure(IllegalArgumentException(error)))
                else onPersist(mapOf("registrationStart" to start, "registrationEnd" to end,
                    "numWinners" to winners, "waitlistCapacity" to capacity), complete)
            }, onBack = onBack, scheduleAvailable = bookingSchedule != null)
    }

    @Test fun screenSubmitsRawInputAndDisplaysRepositoryResult() {
        var submittedWinners: String? = null
        compose.setContent {
            EditLotteryScreen(lottery, canEdit = true,
                onSave = { _, _, winners, _, complete ->
                    submittedWinners = winners
                    complete(Result.failure(IllegalArgumentException("Repository rejected the input.")))
                }, onBack = {}, scheduleAvailable = true)
        }
        node("editLotteryWinners").performTextReplacement("invalid")
        save()
        compose.runOnIdle { assertEquals("invalid", submittedWinners) }
        node("editLotteryError").assertTextEquals("Repository rejected the input.")
    }
}
