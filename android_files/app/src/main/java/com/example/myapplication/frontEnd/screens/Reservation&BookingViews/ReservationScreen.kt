package com.example.myapplication.frontEnd.screens.ReservationBookingViews

import android.util.Log
import com.example.myapplication.backEnd.time.AlbertaTime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.navigation.NavController
import com.example.myapplication.backEnd.featureModels.BookingSchedule
import com.example.myapplication.backEnd.firebaseRepositories.BookingScheduleRepository
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.featureModels.Lottery
import com.example.myapplication.backEnd.featureModels.Reservation
import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.backEnd.firebaseRepositories.ReservationRepository
import com.example.myapplication.backEnd.featureModels.ReservationStatus
import com.example.myapplication.backEnd.userModels.User
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Shows reservations grouped by status, with the actions this user can take.
 *
 * Entrant: reserve or decline invitations, cancel and rebook future stays.
 * Owner: manage their lotteries' reservations. Admin: manage all reservations.
 *
 * @param navController Used for the back button.
 * @param user The logged-in user.
 * @param campsites All loaded campsites, used to look up occupancy.
 * @param reservationRepository Loads and changes reservations.
 * @param scheduleRepository Loads booking schedules for picking dates.
 * @param users Loaded profiles used to show entrant names to management.
 */
@Composable
fun ReservationScreen(
    navController: NavController,
    user: User?,
    campsites: Map<String, Campsite>,
    reservationRepository: ReservationRepository,
    scheduleRepository: BookingScheduleRepository,
    users: List<User> = emptyList(),
    lotteryRepository: LotteryRepository? = null,
    category: ReservationCategory? = null
) {
    if (user == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You need to be logged in to see reservations.")
        }
        return
    }

    val isAdmin = user.userType.trim().equals("admin", ignoreCase = true)
    val isOwner = user.userType.trim().equals("campsite owner", ignoreCase = true)
    val canManage = isOwner || isAdmin
    var managingPeriods by remember(user.deviceID) { mutableStateOf(false) }
    if (managingPeriods && lotteryRepository != null) {
        ManageBookingPeriodsDialog(user, campsites, lotteryRepository) { managingPeriods = false }
    }

    var reservations by remember(user.deviceID, user.userType) { mutableStateOf<List<Reservation>>(emptyList()) }
    var errorMessage by remember(user.deviceID, user.userType) { mutableStateOf<String?>(null) }
    var loadError by remember(user.deviceID, user.userType) { mutableStateOf<String?>(null) }
    var loading by remember(user.deviceID, user.userType) { mutableStateOf(true) }
    var fromCache by remember(user.deviceID, user.userType) { mutableStateOf(true) }
    var retryCount by remember(user.deviceID, user.userType) { mutableStateOf(0) }
    var reserving by remember(user.deviceID, user.userType) { mutableStateOf<Reservation?>(null) }

    // Listen while the screen is open
    DisposableEffect(user.deviceID, user.userType, reservationRepository, retryCount) {
        loading = true
        loadError = null
        var listening = true
        val onReadError: (Exception) -> Unit = { error ->
            if (listening) {
                Log.e("Reservations", "Could not load reservations", error)
                loading = false
                loadError = when ((error as? FirebaseFirestoreException)?.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Your account cannot read these reservations."
                    FirebaseFirestoreException.Code.UNAVAILABLE -> "Unable to connect. Check your internet connection and try again."
                    else -> "Unable to load reservations. Please try again."
                }
            }
        }
        val onCacheStatus: (Boolean) -> Unit = { cached -> if (listening) fromCache = cached }
        val onReservations: (List<Reservation>) -> Unit = { loaded ->
            if (listening) {
                reservations = loaded
                loading = false
                loadError = null
            }
        }
        val registration = if (isAdmin) {
            reservationRepository.listenForAdmin(onReadError, onCacheStatus, onReservations)
        } else if (isOwner) {
            reservationRepository.listenForOwner(user.deviceID, onReadError, onCacheStatus, onReservations)
        } else {
            reservationRepository.listenForEntrant(user.deviceID, onReadError, onCacheStatus, onReservations)
        }
        onDispose {
            listening = false
            registration.remove()
        }
    }

    val onResult: (Boolean) -> Unit = { saved ->
        errorMessage = if (saved) null
        else "That didn't work. The reservation may have changed, or those dates are full."
    }

    val now = LocalDateTime.now(AlbertaTime.zone)
    val ended = setOf(
        ReservationStatus.CANCELLED,
        ReservationStatus.REJECTED,
        ReservationStatus.DECLINED
    )

    val selectedCategory = category.takeIf { canManage }
    val displayedReservations = selectedCategory?.let { selected ->
        reservations.filter { selected.includes(it, now) }
    } ?: reservations
    val sections = listOf(
        (if (canManage) "Waiting for an answer" else "Invitations") to
                displayedReservations.filter { it.status == ReservationStatus.INVITED },
        "Active" to
                displayedReservations.filter { it.status == ReservationStatus.RESERVED && it.isActive(now) },
        "Past" to
                displayedReservations.filter { it.isPast(now) },
        (if (canManage) "Cancelled" else "Cancelled or declined") to
                displayedReservations.filter { it.status in ended }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Text(
            text = selectedCategory?.title ?: if (isAdmin) "All reservations" else if (isOwner) "Reservations for my campsites" else "My reservations",
            fontSize = 22.sp
        )

        if (canManage && lotteryRepository != null) {
            TextButton(onClick = { managingPeriods = true }, modifier = Modifier.testTag("manageBookingPeriods")) {
                Text("Manage booking periods")
            }
        }
        Spacer(Modifier.height(12.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        loadError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("reservationLoadError"))
            TextButton(onClick = { retryCount++ }, modifier = Modifier.testTag("reservationRetryButton")) {
                Text("Retry")
            }
        }

        if (displayedReservations.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    loadError != null -> Unit
                    loading || fromCache -> {
                        CircularProgressIndicator(modifier = Modifier.testTag("reservationsLoading"))
                        Text("Checking for reservations...")
                        if (!loading && fromCache) {
                            Text("No saved reservations on this device. Check your internet connection to load new invitations.")
                            TextButton(onClick = { retryCount++ }) { Text("Retry") }
                        }
                    }
                    else -> Text(selectedCategory?.let { "No ${it.title.lowercase()} yet." } ?: "No reservations yet.", modifier = Modifier.testTag("reservationsEmpty"))
                }
            }
        } else {
            if (fromCache && loadError == null) {
                Text("Showing saved reservations. New invitations will appear when connected.")
            }
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
                            lotteryCampsites = campsites.values.flatMap { campsite ->
                                campsite.lotteryCollection.orEmpty().filter { it.id == reservation.lotteryId }.flatMap { it.campsiteIds } +
                                    campsite.bookingSchedules.orEmpty().mapNotNull { it.registration as? Lottery }
                                        .filter { it.id == reservation.lotteryId }.flatMap { it.campsiteIds }
                            }.filter { it.isNotBlank() }.distinct(),
                            scheduleRepository = scheduleRepository,
                            isOwner = canManage,
                            entrant = users.firstOrNull { it.deviceID == reservation.entrantId },
                            now = now,
                            onReserve = { reserving = reservation },
                            onDecline = { reservationRepository.decline(reservation, onResult) },
                            onCancel = { reservationRepository.cancel(reservation, onResult) },
                            onReject = { reservationRepository.reject(reservation, onResult) },
                            onUnreject = { reservationRepository.unreject(reservation, onResult) },
                            onMessage = if (user.deviceID == reservation.ownerId ||
                                user.deviceID == reservation.entrantId
                            ) {
                                { navController.navigate("Conversation/${reservation.id}") }
                            } else null
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
    lotteryCampsites: List<String>,
    scheduleRepository: BookingScheduleRepository,
    isOwner: Boolean,
    entrant: User?,
    now: LocalDateTime,
    onReserve: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onReject: () -> Unit,
    onUnreject: () -> Unit,
    onMessage: (() -> Unit)? = null
) {
    val start = reservation.bookingStartDate
    val end = reservation.bookingEndDate
    var fetchedCampsites by remember(reservation.lotteryId) { mutableStateOf<List<String>>(emptyList()) }
    // Old invitations remain identifiable through their schedule after the current lottery changes.
    LaunchedEffect(reservation.lotteryId, lotteryCampsites.isEmpty()) {
        if (lotteryCampsites.isEmpty() && reservation.campsiteId.isBlank()) {
            scheduleRepository.getSchedule(reservation.lotteryId) {
                fetchedCampsites = it?.registration?.campsiteIds.orEmpty().filter { id -> id.isNotBlank() }.distinct()
            }
        }
    }
    val names = lotteryCampsites.ifEmpty { fetchedCampsites }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {

            Text(
                text = if (!isOwner && reservation.status == ReservationStatus.INVITED) "You won a lottery!"
                    else reservation.campsiteId.ifBlank { "Lottery invitation" },
                fontSize = 18.sp
            )
            if (reservation.campsiteId.isBlank() && names.isNotEmpty()) {
                Text("Campsites: ${names.take(3).joinToString()}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (names.size > 3) Text("+${names.size - 3} more")
            }

            if (start != null && end != null) {
                Text("Check-in: ${start.format(dateFormat)}")
                Text("Check-out: ${end.format(dateFormat)}")
            }

            if (isOwner) {
                Text("Entrant: ${entrant?.name?.takeIf { it.isNotBlank() } ?: "Name unavailable"}")
            }

            Text("Status: ${statusLabel(reservation.status)}")

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canAct = reservation.status == ReservationStatus.INVITED ||
                        (reservation.status == ReservationStatus.RESERVED && reservation.isActive(now))

                if (isOwner) {
                    if (reservation.status == ReservationStatus.REJECTED) {
                        OutlinedButton(
                            onClick = onUnreject,
                            modifier = Modifier.testTag("unrejectButton")
                        ) { Text("Unreject") }
                    }
                    if (canAct) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.testTag("rejectButton")
                        ) { Text(if (reservation.status == ReservationStatus.RESERVED) "Cancel reservation" else "Reject invitation") }
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

                        ReservationStatus.CANCELLED -> {
                            Button(onClick = onReserve, modifier = Modifier.testTag("rebookButton")) {
                                Text("Book different dates")
                            }
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
            if (onMessage != null && reservation.isActive(now)) {
                TextButton(
                    onClick = onMessage,
                    modifier = Modifier.testTag("messageButton")
                ) { Text("Message") }
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
    campsites: Map<String, Campsite>,
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
    val selectedCampsite = chosenCampsite?.let { campsites[it] }
    val policies = selectedCampsite?.termsAndConditions.orEmpty()
    var acceptedPolicies by remember(chosenCampsite, policies) { mutableStateOf(false) }
    val occupancy = chosenCampsite?.let { campsites[it]?.occupancy } ?: 0
    val available = if (loaded != null && chosenCampsite != null) {
        loaded.availableDates(chosenCampsite, occupancy).filter {
            it.atStartOfDay().isAfter(LocalDateTime.now(AlbertaTime.zone))
        }
    } else {
        emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reserve a campsite") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
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
                            Text("Rules and policies", style = MaterialTheme.typography.titleMedium)
                            val policyText = if (selectedCampsite == null) "Campsite policies could not be loaded. Please try again."
                                else policies.ifBlank { "The owner has not provided any rules or policies for this campsite." }
                            val linkedPolicies = remember(policyText) {
                                buildAnnotatedString {
                                    append(policyText)
                                    Regex("https?://[^\\s]+").findAll(policyText).forEach { match ->
                                        addLink(LinkAnnotation.Url(match.value), match.range.first, match.range.last + 1)
                                    }
                                }
                            }
                            Text(linkedPolicies, modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState()).testTag("reservationPolicies"))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = acceptedPolicies, onCheckedChange = { acceptedPolicies = it },
                                    enabled = selectedCampsite != null, modifier = Modifier.testTag("acceptReservationPolicies"))
                                Text("I have read and accept this campsite's rules and policies.")
                            }
                            val period = loaded.periodFor(chosenCampsite)
                            Text("Booking period: ${period.startDate.format(dateFormat)} – ${period.endDate.format(dateFormat)}")
                            Spacer(Modifier.height(8.dp))

                            if (available.isEmpty()) {
                                Text("No dates left at this campsite.")
                            } else {
                                Text("Tap your first night, then your last night.")

                                LazyColumn(Modifier.heightIn(max = 180.dp)) {
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
                enabled = selectedCampsite != null && first != null && acceptedPolicies,
                onClick = {
                    if (chosenCampsite != null && selectedCampsite != null && first != null && acceptedPolicies) {
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
