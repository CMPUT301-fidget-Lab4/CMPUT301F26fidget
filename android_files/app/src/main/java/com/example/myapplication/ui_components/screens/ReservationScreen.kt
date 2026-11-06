package com.example.myapplication.ui_components.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.features.BookingSchedule
import com.example.myapplication.features.BookingScheduleRepository
import com.example.myapplication.features.Campsite
import com.example.myapplication.features.Lottery
import com.example.myapplication.features.Reservation
import com.example.myapplication.features.ReservationRepository
import com.example.myapplication.features.ReservationStatus
import com.example.myapplication.features.User
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Shows reservations grouped by status, with the actions this user can take.
 *
 * Entrant: reserve or decline invitations, cancel active reservations.
 * Owner: see invited, active, past and cancelled reservations, and reject them.
 *
 * @param navController Used for the back button.
 * @param user The logged-in user.
 * @param campsites All loaded campsites, used to look up occupancy.
 * @param reservationRepository Loads and changes reservations.
 * @param scheduleRepository Loads booking schedules for picking dates.
 */
@Composable
fun ReservationScreen(
    navController: NavController,
    user: User?,
    campsites: List<Campsite>,
    reservationRepository: ReservationRepository,
    scheduleRepository: BookingScheduleRepository
) {
    if (user == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You need to be logged in to see reservations.")
        }
        return
    }

    // Change "owner" to whatever value your User class uses for campsite owners.
    val isOwner = user.userType == "campsite owner"

    var reservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reserving by remember { mutableStateOf<Reservation?>(null) }   // open dialog

    // Listen while the screen is open
    DisposableEffect(user.deviceID, isOwner) {
        val registration = if (isOwner) {
            reservationRepository.listenForOwner(user.deviceID) { reservations = it }
        } else {
            reservationRepository.listenForEntrant(user.deviceID) { reservations = it }
        }
        onDispose { registration.remove() }
    }

    val onResult: (Boolean) -> Unit = { saved ->
        errorMessage = if (saved) null
        else "That didn't work. The reservation may have changed, or those dates are full."
    }

    val now = LocalDateTime.now()
    val ended = setOf(
        ReservationStatus.CANCELLED,
        ReservationStatus.REJECTED,
        ReservationStatus.DECLINED
    )

    val sections = listOf(
        (if (isOwner) "Waiting for an answer" else "Invitations") to
                reservations.filter { it.status == ReservationStatus.INVITED },
        "Active" to
                reservations.filter { it.status == ReservationStatus.RESERVED && it.isActive(now) },
        "Past" to
                reservations.filter { it.isPast(now) },
        (if (isOwner) "Cancelled" else "Cancelled or declined") to
                reservations.filter { it.status in ended }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (isOwner) "Reservations for my campsites" else "My reservations",
            fontSize = 22.sp
        )

        Spacer(Modifier.height(12.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (reservations.isEmpty()) {
            Text("No reservations yet.", modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("reservationList"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((title, list) in sections) {
                    if (list.isEmpty()) continue

                    item(key = title) {
                        Text(
                            title,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(list, key = { it.id }) { reservation ->
                        ReservationCard(
                            reservation = reservation,
                            isOwner = isOwner,
                            now = now,
                            onReserve = { reserving = reservation },
                            onDecline = { reservationRepository.decline(reservation, onResult) },
                            onCancel = { reservationRepository.cancel(reservation, onResult) },
                            onReject = { reservationRepository.reject(reservation, onResult) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = { navController.popBackStack() }) {
            Text("Back")
        }
    }

    // Date picker dialog for accepting an invitation
    reserving?.let { invitation ->
        ReserveDialog(
            reservation = invitation,
            campsites = campsites,
            scheduleRepository = scheduleRepository,
            onConfirm = { campsiteId, start, end, occupancy ->
                reservationRepository.reserve(invitation, campsiteId, start, end, occupancy, onResult)
                reserving = null
            },
            onDismiss = { reserving = null }
        )
    }
}

/**
 * One reservation, with the buttons allowed for this user and status.
 */
@Composable
private fun ReservationCard(
    reservation: Reservation,
    isOwner: Boolean,
    now: LocalDateTime,
    onReserve: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onReject: () -> Unit
) {
    val start = reservation.bookingStartDate
    val end = reservation.bookingEndDate

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {

            Text(
                text = reservation.campsiteId.ifBlank { "Lottery invitation" },
                fontSize = 18.sp
            )

            if (start != null && end != null) {
                Text("${start.format(dateFormat)} – ${end.format(dateFormat)}")
            }

            if (isOwner) {
                Text("Entrant: ${reservation.entrantId}")
            }

            Text("Status: ${statusLabel(reservation.status)}")

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canAct = reservation.status == ReservationStatus.INVITED ||
                        (reservation.status == ReservationStatus.RESERVED && reservation.isActive(now))

                if (isOwner) {
                    if (canAct) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.testTag("rejectButton")
                        ) { Text("Reject") }
                    }
                } else {
                    when (reservation.status) {
                        ReservationStatus.INVITED -> {
                            Button(
                                onClick = onReserve,
                                modifier = Modifier.testTag("reserveButton")
                            ) { Text("Reserve") }

                            OutlinedButton(
                                onClick = onDecline,
                                modifier = Modifier.testTag("declineButton")
                            ) { Text("Decline") }
                        }

                        ReservationStatus.RESERVED -> {
                            // Only before the stay starts
                            if (start != null && now.isBefore(start)) {
                                OutlinedButton(
                                    onClick = onCancel,
                                    modifier = Modifier.testTag("cancelButton")
                                ) { Text("Cancel") }
                            }
                        }

                        else -> {}   // nothing left to do
                    }
                }
            }
        }
    }
}

/**
 * Lets a winner pick a campsite and a range of available dates.
 *
 * Tap the first night, then the last night. Tapping again starts over.
 */
@Composable
private fun ReserveDialog(
    reservation: Reservation,
    campsites: List<Campsite>,
    scheduleRepository: BookingScheduleRepository,
    onConfirm: (campsiteId: String, start: LocalDateTime, end: LocalDateTime, occupancy: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var schedule by remember { mutableStateOf<BookingSchedule<Lottery>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var campsiteId by remember { mutableStateOf<String?>(null) }
    var firstNight by remember { mutableStateOf<LocalDate?>(null) }
    var lastNight by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(reservation.lotteryId) {
        scheduleRepository.getSchedule(reservation.lotteryId) {
            schedule = it
            loading = false
        }
    }

    val loaded = schedule
    val chosenCampsite = campsiteId
    val occupancy = campsites.find { it.name == chosenCampsite }?.occupancy ?: 0
    val available = if (loaded != null && chosenCampsite != null) {
        loaded.availableDates(chosenCampsite, occupancy)
    } else {
        emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reserve a campsite") },
        text = {
            Column {
                when {
                    loading -> CircularProgressIndicator()

                    loaded == null -> Text("Couldn't load available dates.")

                    else -> {
                        Text("Campsite")
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            loaded.registration.campsiteIds.forEach { id ->
                                FilterChip(
                                    selected = chosenCampsite == id,
                                    onClick = {
                                        campsiteId = id
                                        firstNight = null
                                        lastNight = null
                                    },
                                    label = { Text(id) }
                                )
                            }
                        }

                        if (chosenCampsite != null) {
                            Spacer(Modifier.height(8.dp))

                            if (available.isEmpty()) {
                                Text("No dates left at this campsite.")
                            } else {
                                Text("Tap your first night, then your last night.")

                                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                                    items(available) { day ->
                                        val first = firstNight
                                        val selected = first != null &&
                                                !day.isBefore(first) &&
                                                !day.isAfter(lastNight ?: first)

                                        Text(
                                            text = (if (selected) "✓ " else "") + day.format(dateFormat),
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    when {
                                                        // start a new range
                                                        first == null || lastNight != null -> {
                                                            firstNight = day
                                                            lastNight = null
                                                        }
                                                        day.isBefore(first) -> firstNight = day
                                                        else -> lastNight = day
                                                    }
                                                }
                                                .padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val first = firstNight
            Button(
                enabled = chosenCampsite != null && first != null,
                onClick = {
                    if (chosenCampsite != null && first != null) {
                        val last = lastNight ?: first
                        onConfirm(
                            chosenCampsite,
                            first.atStartOfDay(),
                            last.plusDays(1).atStartOfDay(),   // checkout the morning after the last night
                            occupancy
                        )
                    }
                }
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun statusLabel(status: ReservationStatus): String =
    status.name.lowercase().replaceFirstChar { it.uppercase() }