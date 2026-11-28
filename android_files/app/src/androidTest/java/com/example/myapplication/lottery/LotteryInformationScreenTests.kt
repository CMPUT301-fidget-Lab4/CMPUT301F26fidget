package com.example.myapplication.lottery

import com.example.myapplication.data.firebase.BookingScheduleRepository
import com.example.myapplication.data.firebase.ReservationRepository
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.features.lottery.Registration
import com.example.myapplication.data.firebase.MessageRepository
import com.example.myapplication.features.notification.AppNotification
import com.example.myapplication.features.notification.LotteryNotificationInbox
import com.example.myapplication.data.firebase.NotificationRepository
import com.example.myapplication.features.profile.AuthState
import com.example.myapplication.data.firebase.UserRepository
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.junit.Assert.assertEquals
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.myapplication.frontEnd.viewcontroller.CampsiteApp
import io.mockk.*
import com.google.firebase.firestore.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.profile.User
import com.example.myapplication.features.profile.JoinedLottery
import com.example.myapplication.frontEnd.screens.LotteryViews.*
import com.example.myapplication.frontEnd.screens.campsiteViews.CampsiteListScreen
import com.example.myapplication.frontEnd.screens.mainViews.MainScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Tag-based UI tests for lottery information, owner/admin waitlists, and search counts.
 * Fixtures use in-memory state, so the tests require no Firebase data or account.
 * Unmerged semantics expose tagged text inside clickable campsite rows and cards.
 */
@RunWith(AndroidJUnit4::class)
class LotteryInformationScreenTests {
    @get:Rule val compose = createComposeRule()
    private val entrant = User(name = "Alex Entrant", email = "alex@example.com", phoneNumber = "555-1234", deviceID = "entrant", userType = "entrant", joinedLotteries = listOf(JoinedLottery("lot", "Pine")))
    private val owner = User(userType = "campsite owner", deviceID = "owner")
    private val camp = Campsite(name = "Pine", ownerID = "owner", occupancy = 4)
    private val campsites = mapOf("Pine" to camp)
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun lottery() = Lottery(id = "lot", ownerId = "owner", campsites = mapOf("Pine" to camp),
        waitlist = mapOf("entrant" to entrant), waitlistCapacity = 5,
        period = Registration(LocalDateTime.of(2035, 1, 1, 0, 0), LocalDateTime.of(2035, 1, 3, 0, 0)))

    @Test fun ownerCanReadEntrantNameAndContactInformation() {
        compose.setContent { LotteryWaitlistScreen(owner, lottery(), campsites, {}) }
        node("waitlistEntrantName_entrant").assertTextEquals("Alex Entrant")
        node("waitlistEntrantEmail_entrant").assertTextEquals("Email: alex@example.com")
        node("waitlistEntrantPhone_entrant").assertTextEquals("Phone: 555-1234")
        compose.onNodeWithTag("waitlistCount").assertTextEquals("Waitlist: 1 / 5 joined")
    }

    @Test fun entrantCannotOpenOwnerWaitlistRoute() {
        compose.setContent { LotteryWaitlistScreen(entrant, lottery(), campsites, {}) }
        node("waitlistAccessMessage").assertExists()
        node("waitlistEntrantName_entrant").assertDoesNotExist()
        node("waitlistEntrantEmail_entrant").assertDoesNotExist()
    }

    @Test fun unrelatedOwnerCannotReadWaitlist() {
        compose.setContent { LotteryWaitlistScreen(owner.copy(deviceID = "other"), lottery(), campsites, {}) }
        compose.onNodeWithTag("waitlistEntrant_entrant").assertDoesNotExist()
    }

    @Test fun reusableWaitlistDefaultsToNamesWithoutContactDetails() {
        compose.setContent { WaitlistScreen(lottery(), {}) }
        node("waitlistEntrantName_entrant").assertTextEquals("Alex Entrant")
        node("waitlistEntrantEmail_entrant").assertDoesNotExist()
        node("waitlistEntrantPhone_entrant").assertDoesNotExist()
    }

    @Test fun entrantInformationShowsOnlyJoinedLotteriesAndNoRosterButton() {
        // Deliberately disagree with waitlist maps: personal selection must use User.joinedLotteries.
        val joined = lottery().copy(waitlist = emptyMap())
        val other = lottery().copy(id = "other")
        compose.setContent { app(entrant, listOf(joined, other)) }
        node("viewLotteryInformation").performClick()
        node("lotteryInfo_lot").assertExists()
        node("lotteryInfo_other").assertDoesNotExist()
        node("viewWaitlist_lot").assertDoesNotExist()
        node("lotteryEntrantStatus_lot").assertExists()
    }

    @Test fun waitlistUpdatesWhenAnEntrantLeaves() {
        val live = mutableStateOf(lottery())
        compose.setContent { WaitlistScreen(live.value, {}) }
        node("waitlistEntrantName_entrant").assertTextEquals("Alex Entrant")
        compose.runOnIdle { live.value = live.value.copy(waitlist = emptyMap()) }
        node("waitlistEntrantName_entrant").assertDoesNotExist()
        compose.onNodeWithTag("waitlistEmpty").assertExists()
        compose.onNodeWithTag("waitlistCount").assertTextEquals("Waitlist: 0 / 5 joined")
    }

    @Test fun searchRowSeparatesCampsiteOccupancyFromLiveWaitlistCapacity() {
        camp.lotteryCollection = listOf(lottery())
        compose.setContent { CampsiteListScreen(rememberNavController(), entrant, campsites, false) }
        compose.onNodeWithTag("campsiteWaitlist_Pine", useUnmergedTree = true).assertTextEquals("Waitlist: 1 / 5 joined")
        node("campsiteCapacity_Pine").assertExists()
        compose.runOnIdle { camp.lotteryCollection = listOf(lottery().copy(waitlistCapacity = 1)) }
        compose.onNodeWithTag("campsiteWaitlist_Pine", useUnmergedTree = true).assertTextEquals("Waitlist: 1 / 1 joined (full)")
    }

    @Test fun mainScreenLotteryButtonNavigatesToInformation() {
        compose.setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "main") {
                composable("main") { MainScreen(nav, entrant) }
                composable("LotteryInformation") { Text("Information destination", modifier = Modifier.testTag("informationDestination")) }
            }
        }
        compose.onNodeWithTag("viewLotteryInformation").performClick()
        node("informationDestination").assertExists()
    }

    @Test fun ownerOpensWaitlistAndReturnsUsingTaggedButtons() {
        compose.setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "information") {
                composable("information") {
                    LotteryInformationScreen(owner, listOf(lottery()), campsites,
                        { nav.navigate("waitlist/$it") }, {})
                }
                composable("waitlist/{id}") { entry ->
                    val selected = lottery().takeIf { it.id == entry.arguments?.getString("id") }
                    LotteryWaitlistScreen(owner, selected, campsites, { nav.popBackStack() })
                }
            }
        }
        node("viewWaitlist_lot").performScrollTo().performClick()
        node("waitlistCampsites").assertTextEquals("Pine")
        node("waitlistEntrantEmail_entrant").assertTextEquals("Email: alex@example.com")
        node("waitlistBack").performClick()
        node("lotteryInformationTitle").assertIsDisplayed()
        node("viewWaitlist_lot").assertExists()
    }

    @Test fun adminCanInspectAnotherOwnersWaitlist() {
        val admin = User(userType = "admin", deviceID = "admin")
        compose.setContent { LotteryWaitlistScreen(admin, lottery(), campsites, {}) }
        node("waitlistEntrantName_entrant").assertTextEquals("Alex Entrant")
        node("waitlistEntrantEmail_entrant").assertTextEquals("Email: alex@example.com")
        node("waitlistEntrantPhone_entrant").assertTextEquals("Phone: 555-1234")
        node("waitlistAccessMessage").assertDoesNotExist()
    }

    @Test fun emptyWaitlistStillShowsLoadErrorAndZeroCount() {
        compose.setContent {
            WaitlistScreen(lottery().copy(waitlist = emptyMap()), {}, loadError = "Unable to refresh waitlist.")
        }
        node("waitlistLoadError").assertTextEquals("Unable to refresh waitlist.")
        node("waitlistEmpty").assertIsDisplayed()
        node("waitlistCount").assertTextEquals("Waitlist: 0 / 5 joined")
        node("waitlistEntrant_entrant").assertDoesNotExist()
    }

    @Test fun unavailableLotteryShowsMessageAndBackStillWorks() {
        var backClicks = 0
        compose.setContent { WaitlistScreen(null, { backClicks++ }) }
        node("waitlistUnavailable").assertIsDisplayed()
        node("waitlistCount").assertDoesNotExist()
        node("waitlistEntrant_entrant").assertDoesNotExist()
        node("waitlistBack").performClick()
        compose.runOnIdle { assertEquals(1, backClicks) }
    }

    @Test fun unresolvedEntrantRemainsCountedWithoutContactDetails() {
        val unresolved = lottery().copy(waitlist = mapOf("entrant" to null))
        compose.setContent { LotteryWaitlistScreen(owner, unresolved, campsites, {}) }
        node("waitlistCount").assertTextEquals("Waitlist: 1 / 5 joined")
        node("waitlistEntrantName_entrant").assertTextEquals("Entrant profile unavailable")
        node("waitlistEntrantEmail_entrant").assertDoesNotExist()
        node("waitlistEntrantPhone_entrant").assertDoesNotExist()
    }

    @Test fun contactDetailsDisappearWhenOwnershipIsRevoked() {
        val liveUser = mutableStateOf(owner)
        compose.setContent { LotteryWaitlistScreen(liveUser.value, lottery(), campsites, {}) }
        node("waitlistEntrantEmail_entrant").assertExists()
        compose.runOnIdle { liveUser.value = owner.copy(deviceID = "another-owner") }
        node("waitlistAccessMessage").assertIsDisplayed()
        node("waitlistEntrant_entrant").assertDoesNotExist()
        node("waitlistEntrantEmail_entrant").assertDoesNotExist()
        node("waitlistEntrantPhone_entrant").assertDoesNotExist()
    }

    @Test fun entrantWithNoMembershipSeesEmptyInformationScreen() {
        compose.setContent { app(entrant.copy(joinedLotteries = emptyList()), listOf(lottery())) }
        node("viewLotteryInformation").performClick()
        node("lotteryInformationTitle").assertTextEquals("Lotteries I've joined")
        node("lotteryInformationEmpty").assertIsDisplayed()
        node("lotteryInfo_lot").assertDoesNotExist()
        node("viewWaitlist_lot").assertDoesNotExist()
    }

    @Test fun adminPersonalScreenShowsOnlySavedJoinedLotteries() {
        val admin = User(userType = "admin", deviceID = "admin", joinedLotteries = listOf(JoinedLottery("lot", "Pine")))
        compose.setContent { app(admin, listOf(lottery(), lottery().copy(id = "unrelated"))) }
        node("viewLotteryInformation").performClick()
        node("lotteryInformationTitle").assertTextEquals("My joined lotteries")
        node("lotteryInfo_lot").assertExists()
        node("lotteryInfo_unrelated").assertDoesNotExist()
    }

    @Test fun adminAllActiveScreenIncludesUnjoinedAndExcludesDrawnLotteries() {
        val admin = User(userType = "admin", deviceID = "admin")
        compose.setContent { app(admin, listOf(lottery(), lottery().copy(id = "drawn", drawn = true))) }
        node("viewAllActiveLotteries").performClick()
        node("lotteryInformationTitle").assertTextEquals("All active lotteries")
        node("lotteryInfo_lot").assertExists()
        node("lotteryInfo_drawn").assertDoesNotExist()
    }

    @Test fun entrantsAndOwnersCannotOpenAllActiveScreen() {
        val profile = mutableStateOf(User(userType = "admin", deviceID = "admin"))
        compose.setContent { app(profile.value, listOf(lottery())) }
        node("viewAllActiveLotteries").performClick()
        node("lotteryInfo_lot").assertExists()
        compose.runOnIdle { profile.value = entrant }
        node("lotteryInformationDenied").assertIsDisplayed()
        node("lotteryInfo_lot").assertDoesNotExist()
        compose.runOnIdle { profile.value = owner }
        node("lotteryInformationDenied").assertIsDisplayed()
        node("lotteryInfo_lot").assertDoesNotExist()
    }

    @Test fun adminMainScreenHasSeparatePersonalAndAllActiveDestinations() {
        val admin = User(userType = "admin", deviceID = "admin")
        compose.setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "main") {
                composable("main") { MainScreen(nav, admin) }
                composable("AllActiveLotteries") { Text("All active destination", modifier = Modifier.testTag("allActiveDestination")) }
            }
        }
        compose.onNodeWithTag("viewLotteryInformation").assertTextEquals("My joined lotteries")
        node("viewAllActiveLotteries").performClick()
        node("allActiveDestination").assertExists()
    }

    @Test fun entrantMainScreenDoesNotHaveAdminAllActiveAction() {
        compose.setContent { MainScreen(rememberNavController(), entrant) }
        node("viewLotteryInformation").assertExists()
        node("viewAllActiveLotteries").assertDoesNotExist()
    }

    @Test fun personalListUpdatesWhenUserJoinedFieldChanges() {
        val profile = mutableStateOf(entrant)
        compose.setContent { app(profile.value, listOf(lottery())) }
        node("viewLotteryInformation").performClick()
        node("lotteryInfo_lot").assertExists()
        compose.runOnIdle { profile.value = entrant.copy(joinedLotteries = emptyList()) }
        node("lotteryInfo_lot").assertDoesNotExist()
        node("lotteryInformationEmpty").assertIsDisplayed()
    }

    @Test fun alertOptOutStillShowsResultsOnHomeAndInNotifications() {
        val profile = entrant.copy(lotteryAlertsEnabled = false)
        val win = AppNotification("win", profile.deviceID, "lot", "WIN", "You won the lottery for Pine!")
        val loss = AppNotification("loss", profile.deviceID, "other", "LOSE", "You weren't selected for River.")
        compose.setContent { app(profile, listOf(lottery()), listOf(win, loss)) }

        node("latestLotteryResult").assertTextEquals(win.message)
        node("viewNotifications").performClick()

        compose.onNodeWithText(win.message).assertIsDisplayed()
        compose.onNodeWithText(loss.message).assertIsDisplayed()
    }

    /** Uses the production navigation controller, with repository fixtures instead of Firebase. */
    @Composable
    private fun app(profile: User, records: List<Lottery>, results: List<AppNotification> = emptyList()) {
        val repository = remember { mockk<LotteryRepository>(relaxed = true) }
        every { repository.allLotteries } returns records.associateBy { it.id }
        every { repository.listenerError } returns null
        every { repository.getOwnedLotteries(any(), any()) } answers {
            val current = firstArg<User?>()
            if (current?.userType?.trim()?.equals("campsite owner", true) == true)
                records.filter { LotteryRepository.canViewLotteryWaitlist(current, it, campsites) }
            else emptyList()
        }
        val userRepository = remember(profile) {
            val db = mockk<FirebaseFirestore>()
            val collection = mockk<CollectionReference>()
            val snapshot = mockk<QuerySnapshot>()
            val metadata = mockk<SnapshotMetadata>()
            val document = mockk<DocumentSnapshot>()
            every { metadata.isFromCache } returns false
            every { snapshot.metadata } returns metadata
            every { document.toObject(User::class.java) } returns profile
            every { snapshot.documents } returns listOf(document)
            every { db.collection("users") } returns collection
            every { collection.addSnapshotListener(MetadataChanges.INCLUDE, any<EventListener<QuerySnapshot>>()) } answers {
                secondArg<EventListener<QuerySnapshot>>().onEvent(snapshot, null)
                mockk<ListenerRegistration>(relaxed = true)
            }
            UserRepository(profile.deviceID, db)
        }
        val auth = remember(profile) { MutableStateFlow<AuthState>(AuthState.LoggedIn(profile)) }
        val notifications = remember(profile.deviceID, results) {
            val inbox = MutableStateFlow(LotteryNotificationInbox(profile.deviceID, results, loading = false))
            mockk<NotificationRepository>().also { repository ->
                every { repository.inbox } returns inbox
            }
        }
        CampsiteApp(
            user = profile, users = listOf(profile), onAddUser = {},
            onDeleteUser = { error("Deleting profiles is not part of this fixture") },
            onUpdateUser = { _, _ -> error("Updating profiles is not part of this fixture") },
            findUser = { id -> profile.takeIf { it.deviceID == id } },
            getAndroidId = { profile.deviceID }, authState = auth,
            getJoinedLotteries = userRepository::getJoinedLotteries,
            getAllActiveLotteryWaitlists = userRepository::getAllActiveLotteryWaitlists,
            getLotteryWaitlist = userRepository::getLotteryWaitlist,
            campsites = campsites, findCampsite = { campsites[it] },
            reservationRepository = remember { mockk<ReservationRepository>(relaxed = true) },
            scheduleRepository = remember { mockk<BookingScheduleRepository>(relaxed = true) },
            lotteryRepository = repository,
            notificationRepository = notifications,
            messageRepository = remember { mockk<MessageRepository>(relaxed = true) }
        )
    }

    @Test fun controllerSkipsMissingJoinedRecordsAndDeduplicatesJoinedIds() {
        val profile = entrant.copy(joinedLotteries = listOf(
            JoinedLottery("lot", "Pine"), JoinedLottery("lot", "Pine"), JoinedLottery("missing", "Pine")))
        compose.setContent { app(profile, listOf(lottery())) }
        node("viewLotteryInformation").performClick()
        compose.onAllNodesWithTag("lotteryInfo_lot", useUnmergedTree = true).assertCountEquals(1)
        node("lotteryInfo_missing").assertDoesNotExist()
    }

    @Test fun ownerPersonalViewUsesJoinedMembershipInsteadOfOwnership() {
        val joined = lottery().copy(id = "joined", ownerId = "someoneElse", campsites = mapOf("Other camp" to null))
        val profile = owner.copy(joinedLotteries = listOf(JoinedLottery("joined", "Other camp")))
        compose.setContent { app(profile, listOf(lottery(), joined)) }
        node("viewLotteryInformation").performClick()
        node("lotteryInfo_joined").assertExists()
        node("lotteryInfo_lot").assertDoesNotExist()
        node("viewWaitlist_joined").assertDoesNotExist()
    }

    @Test fun ownerControllerSuppliesOnlyOwnedListingsToTheSharedScreen() {
        val unrelated = lottery().copy(id = "other", ownerId = "otherOwner", campsites = mapOf("Other camp" to null))
        compose.setContent { app(owner, listOf(lottery(), unrelated)) }
        node("viewOwnedLotteries").performClick()
        node("lotteryInfo_lot").assertExists()
        node("lotteryInfo_other").assertDoesNotExist()
        node("viewWaitlist_lot").assertExists()
        node("leaveWaitlist_lot").assertDoesNotExist()
    }
}
