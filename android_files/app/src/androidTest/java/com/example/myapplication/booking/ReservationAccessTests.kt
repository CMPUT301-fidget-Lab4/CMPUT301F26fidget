package com.example.myapplication.booking

import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.data.firebase.BookingScheduleRepository
import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.data.firebase.ReservationRepository
import com.example.myapplication.features.booking.ReservationStatus
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.data.firebase.LotteryRepository
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.screens.ReservationBookingViews.*
import com.google.firebase.firestore.ListenerRegistration
import io.mockk.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** UI regression tests use in-memory fixtures and mocked repositories; no Firestore emulator. */
@RunWith(AndroidJUnit4::class)
class ReservationAccessTests {
    @get:Rule val compose = createComposeRule()
    private val reservations = mockk<ReservationRepository>()
    private val schedules = mockk<BookingScheduleRepository>()
    private val lotteries = mockk<LotteryRepository>()
    private val listener = mockk<ListenerRegistration>(relaxed = true)
    private lateinit var emit: (List<Reservation>) -> Unit
    private val lottery = Lottery(id = "lottery", campsiteIds = listOf("camp"))
    private val schedule = BookingSchedule(lottery, LocalDateTime.of(2035, 1, 1, 0, 0), LocalDateTime.of(2035, 1, 10, 0, 0))
    private fun reservation(status: ReservationStatus, reason: String = "") = Reservation(
        id = status.name, lotteryId = "lottery", ownerId = "owner", entrantId = "entrant",
        campsiteId = "camp", status = status, cancellationReason = reason
    )

    private fun show(role: String, items: List<Reservation>, category: ReservationCategory? = null, liveCampsite: Boolean = true) {
        every { reservations.listenForEntrant(any(), any(), any(), any()) } answers {
            arg<(Boolean) -> Unit>(2)(false)
            emit = arg(3); emit(items); listener
        }
        every { reservations.listenForOwner(any(), any(), any(), any()) } answers {
            arg<(Boolean) -> Unit>(2)(false)
            emit = arg(3); emit(items); listener
        }
        every { reservations.listenForAdmin(any(), any(), any()) } answers {
            arg<(Boolean) -> Unit>(1)(false)
            emit = arg(2); emit(items); listener
        }
        every { schedules.getSchedule(any(), any()) } answers {
            secondArg<(BookingSchedule<Lottery>?) -> Unit>()(schedule)
        }
        val campsites = if (liveCampsite) mapOf("camp" to Campsite(name = "camp", ownerID = "owner", occupancy = 2).apply {
            lotteryCollection = listOf(lottery)
        }) else emptyMap()
        compose.setContent {
            ReservationScreen(rememberNavController(), User(userType = role, deviceID = if (role == "entrant") "entrant" else "owner"),
                campsites, reservations, schedules, lotteryRepository = lotteries, category = category)
        }
    }

    @Test fun deletedProfileCancellationHasNoRebookOrManagementActions() {
        show("entrant", listOf(reservation(ReservationStatus.CANCELLED, "PROFILE_DELETED")), ReservationCategory.CANCELLED)
        compose.onNodeWithTag("rebookButton").assertDoesNotExist()
        compose.onNodeWithTag("manageBookingPeriods").assertDoesNotExist()
        compose.onNodeWithTag("rejectButton").assertDoesNotExist()
        verify(exactly = 1) { reservations.listenForEntrant("entrant", any(), any(), any()) }
        verify(exactly = 0) { reservations.listenForAdmin(any(), any(), any()) }
        verify(exactly = 0) { reservations.listenForOwner(any(), any(), any(), any()) }
    }

    @Test fun ordinaryEntrantCancellationStillAllowsRebooking() {
        show("entrant", listOf(reservation(ReservationStatus.CANCELLED)))
        compose.onNodeWithTag("rebookButton").assertExists()
    }

    @Test fun historicalScheduleWithoutLiveCampsitesHasNoRebookButton() {
        show("entrant", listOf(reservation(ReservationStatus.CANCELLED)), liveCampsite = false)
        compose.onNodeWithTag("rebookButton").assertDoesNotExist()
    }

    @Test fun entrantCategoriesFilterTheirReservations() {
        show("entrant", listOf(reservation(ReservationStatus.INVITED), reservation(ReservationStatus.CANCELLED)), ReservationCategory.CURRENT)
        compose.onNodeWithText("Current reservations").assertExists()
        compose.onNodeWithTag("reserveButton").assertExists()
        compose.onNodeWithTag("rebookButton").assertDoesNotExist()
        compose.onNodeWithTag("manageBookingPeriods").assertDoesNotExist()
    }

    @Test fun ownersKeepManagementControls() {
        show("campsite owner", listOf(reservation(ReservationStatus.INVITED)))
        compose.onNodeWithTag("manageBookingPeriods").assertExists()
        compose.onNodeWithTag("rejectButton").assertExists()
        compose.onNodeWithTag("reserveButton").assertDoesNotExist()
        verify(exactly = 1) { reservations.listenForOwner("owner", any(), any(), any()) }
    }

    @Test fun adminsKeepManagementControls() {
        show("admin", listOf(reservation(ReservationStatus.INVITED)))
        compose.onNodeWithTag("manageBookingPeriods").assertExists()
        compose.onNodeWithTag("rejectButton").assertExists()
        verify(exactly = 1) { reservations.listenForAdmin(any(), any(), any()) }
    }

    @Test fun profileDeletionClosesAnAlreadyOpenDatePicker() {
        val invitation = reservation(ReservationStatus.INVITED)
        show("entrant", listOf(invitation))
        compose.onNodeWithTag("reserveButton").performClick()
        compose.onNodeWithText("Reserve a campsite").assertExists()
        compose.runOnIdle { emit(listOf(invitation.copy(status = ReservationStatus.CANCELLED, cancellationReason = "PROFILE_DELETED"))) }
        compose.onNodeWithText("Reserve a campsite").assertDoesNotExist()
        compose.onNodeWithTag("rebookButton").assertDoesNotExist()
    }
}