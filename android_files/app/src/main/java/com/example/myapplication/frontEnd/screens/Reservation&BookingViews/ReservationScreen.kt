package com.example.myapplication.frontEnd.screens.ReservationBookingViews

import android.util.Log
import com.example.myapplication.core.datetime.AlbertaTime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.navigation.NavController
import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.data.firebase.BookingScheduleRepository
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.data.firebase.ReservationRepository
import com.example.myapplication.features.booking.ReservationStatus
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout
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
        AdaptiveContent {
            Box(Modifier.fillMaxSize().padding(AppLayout.Medium), contentAlignment = Alignment.Center) {
                Text("You need to be logged in to see reservations.")
            }
        }
        return
    }

    val isAdmin = user.userType.trim().equals("admin", ignoreCase = true)
    val isOwner = user.userType.trim().equals("campsite owner", ignoreCase = true)
    val canManage = isOwner || isAdmin
    var managingPeriods by remember(user.deviceID) { mutableStateOf(false) }
    if (canManage && managingPeriods && lotteryRepository != null) {
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

    val selectedCategory = category
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

    val listState = rememberLazyListState()
    LaunchedEffect(errorMessage, loadError) {
        if (errorMessage != null || loadError != null) listState.scrollToItem(0)
    }
    AdaptiveContent {
        Column(
            modifier = Modifier.fillMaxSize().padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("reservationList"),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
            ) {
                item(key = "heading") {
                    val title = selectedCategory?.title ?: when {
                        isAdmin -> "All reservations"
                        isOwner -> "Reservations for my campsites"
                        else -> "My reservations"
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
                if (canManage && lotteryRepository != null) {
                    item(key = "manage") {
                        TextButton(onClick = { managingPeriods = true }, modifier = Modifier.testTag("manageBookingPeriods")) {
                            Text("Manage booking periods")
                        }
                    }
                }
                errorMessage?.let { message ->
                    item(key = "action-error") { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                loadError?.let { message ->
                    item(key = "load-error") {
                        Column {
                            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("reservationLoadError"))
                            TextButton(onClick = { retryCount++ }, modifier = Modifier.testTag("reservationRetryButton")) {
                                Text("Retry")
                            }
                        }
                    }
                }
                if (displayedReservations.isEmpty() && loadError == null) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = AppLayout.Medium),
                            verticalArrangement = Arrangement.spacedBy(AppLayout.Small),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (loading || fromCache) {
                                CircularProgressIndicator(modifier = Modifier.testTag("reservationsLoading"))
                                Text("Checking for reservations...")
                                if (!loading && fromCache) {
                                    Text("No saved reservations on this device. Check your internet connection to load new invitations.")
                                    TextButton(onClick = { retryCount++ }) { Text("Retry") }
                                }
                            } else {
                                Text(
                                    selectedCategory?.let { "No ${it.title.lowercase()} yet." } ?: "No reservations yet.",
                                    modifier = Modifier.testTag("reservationsEmpty")
                                )
                            }
                        }
                    }
                } else if (displayedReservations.isNotEmpty() && fromCache && loadError == null) {
                    item(key = "cached") { Text("Showing saved reservations. New invitations will appear when connected.") }
                }
                for ((title, list) in sections) {
                    if (list.isEmpty()) continue
                    item(key = "section-$title") {
                        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = AppLayout.Small))
                    }
                    items(list, key = { "reservation-${it.id}" }) { reservation ->
                        ReservationCard(
                            reservation = reservation,
                            lotteryCampsites = campsites.values.flatMap { campsite ->
                                campsite.lotteryCollection.orEmpty().filter { it.id == reservation.lotteryId }.flatMap { it.campsiteIds } +
                                    campsite.bookingSchedules.orEmpty().mapNotNull { it.registration as? Lottery }
                                        .filter { it.id == reservation.lotteryId }.flatMap { it.campsiteIds }
                            }.filter { it.isNotBlank() }.distinct(),
                            existingCampsiteIds = campsites.keys,
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

            Button(onClick = { navController.popBackStack() }) {
                Text("Back")
            }
        }
    }

    // Re-check the live reservation so deletion also closes an already-open date picker.
    val bookableReservation = reserving?.let { selected ->
        reservations.firstOrNull { it.id == selected.id }?.takeIf { !canManage && it.canChooseDates() }
    }
    LaunchedEffect(bookableReservation) {
        if (bookableReservation == null) reserving = null
    }
    bookableReservation?.let { invitation ->
        var saving by remember(invitation.id) { mutableStateOf(false) }
        var bookingError by remember(invitation.id) { mutableStateOf<String?>(null) }
        var scheduleRefresh by remember(invitation.id) { mutableStateOf(0) }
        ReserveDialog(
            reservation = invitation,
            campsites = campsites,
            scheduleRepository = scheduleRepository,
            saving = saving,
            bookingError = bookingError,
            scheduleRefresh = scheduleRefresh,
            onConfirm = book@{ campsiteId, start, end, occupancy ->
                if (saving) return@book
                saving = true
                bookingError = null
                reservationRepository.reserve(invitation, campsiteId, start, end, occupancy) { saved ->
                    saving = false
                    if (saved) {
                        if (reserving?.id == invitation.id) reserving = null
                    } else {
                        bookingError = "Couldn't save this stay. Check availability and try again."
                        scheduleRefresh++
                    }
                }
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
    existingCampsiteIds: Set<String>,
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
        if (lotteryCampsites.isEmpty()) {
            scheduleRepository.getSchedule(reservation.lotteryId) {
                fetchedCampsites = it?.registration?.campsiteIds.orEmpty().filter { id -> id.isNotBlank() }.distinct()
            }
        }
    }
    val names = lotteryCampsites.ifEmpty { fetchedCampsites }.filter { it in existingCampsiteIds }
    val canChooseDates = reservation.canChooseDates() && names.isNotEmpty()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppLayout.Medium)) {

            Text(
                text = if (!isOwner && reservation.status == ReservationStatus.INVITED) "You won a lottery!"
                    else reservation.campsiteId.ifBlank { "Lottery invitation" },
                style = MaterialTheme.typography.titleMedium
            )
            if (reservation.campsiteId.isBlank() && names.isNotEmpty()) {
                Text("Campsites: ${names.take(3).joinToString()}")
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
            if (reservation.cancellationReason == "PROFILE_DELETED") {
                Text("Cancelled because a related profile was deleted. This reservation cannot be booked again.")
            }

            Spacer(Modifier.height(AppLayout.Small))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppLayout.Small),
                verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
            ) {
                val canAct = reservation.status == ReservationStatus.INVITED ||
                        (reservation.status == ReservationStatus.RESERVED && reservation.isActive(now))

                if (isOwner) {
                    if (reservation.status == ReservationStatus.REJECTED && reservation.cancellationReason != "PROFILE_DELETED") {
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
                            if (canChooseDates) {
                                Button(
                                    onClick = onReserve,
                                    modifier = Modifier.testTag("reserveButton")
                                ) { Text("Reserve") }
                            }

                            OutlinedButton(
                                onClick = onDecline,
                                modifier = Modifier.testTag("declineButton")
                            ) { Text("Decline") }
                        }

                        ReservationStatus.CANCELLED -> {
                            if (canChooseDates) {
                                Button(onClick = onReserve, modifier = Modifier.testTag("rebookButton")) {
                                    Text("Book different dates")
                                }
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
    saving: Boolean,
    bookingError: String?,
    scheduleRefresh: Int,
    onConfirm: (campsiteId: String, start: LocalDateTime, end: LocalDateTime, occupancy: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var schedule by remember { mutableStateOf<BookingSchedule<Lottery>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var campsiteId by remember { mutableStateOf<String?>(null) }
    var firstNight by remember { mutableStateOf<LocalDate?>(null) }
    var lastNight by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(reservation.lotteryId, scheduleRefresh) {
        loading = true
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
    val now = LocalDateTime.now(AlbertaTime.zone)
    val start = firstNight?.atStartOfDay()
    val end = (lastNight ?: firstNight)?.plusDays(1)?.atStartOfDay()
    val stayAvailable = loaded != null && chosenCampsite != null && start != null && end != null &&
        start.isAfter(now) && loaded.isStayAvailable(chosenCampsite, start, end, occupancy)
    val available = if (loaded != null && chosenCampsite != null) {
        loaded.availableDates(chosenCampsite, occupancy).filter {
            it.atStartOfDay().isAfter(now)
        }
    } else {
        emptyList()
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Reserve a campsite") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
                bookingError?.let { message ->
                    item(key = "booking-error") { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                if (!loading && start != null && loaded != null && !stayAvailable) {
                    item(key = "availability-error") {
                        Text("Choose dates with availability on every night of your stay.", color = MaterialTheme.colorScheme.error)
                    }
                }
                when {
                    loading -> item(key = "loading") { CircularProgressIndicator() }
                    loaded == null -> item(key = "load-error") { Text("Couldn't load available dates.") }
                    loaded.registration.campsiteIds.none { it in campsites } -> item(key = "no-campsites") {
                        Text("This reservation no longer has any available campsites.")
                    }
                    else -> {
                        item(key = "campsites") {
                            Column {
                                Text("Campsite")
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppLayout.Small)
                                ) {
                                    loaded.registration.campsiteIds.filter { it in campsites }.forEach { id ->
                                        FilterChip(
                                            enabled = !saving,
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
                            }
                        }

                        if (chosenCampsite != null) {
                            item(key = "policy-heading") {
                                Text("Rules and policies", style = MaterialTheme.typography.titleMedium)
                            }
                            item(key = "policies") {
                                val policyText = if (selectedCampsite == null) {
                                    "Campsite policies could not be loaded. Please try again."
                                } else {
                                    policies.ifBlank { "The owner has not provided any rules or policies for this campsite." }
                                }
                                val linkedPolicies = remember(policyText) {
                                    buildAnnotatedString {
                                        append(policyText)
                                        Regex("https?://[^\\s]+").findAll(policyText).forEach { match ->
                                            addLink(LinkAnnotation.Url(match.value), match.range.first, match.range.last + 1)
                                        }
                                    }
                                }
                                Text(linkedPolicies, modifier = Modifier.fillMaxWidth().testTag("reservationPolicies"))
                            }
                            item(key = "accept-policies") {
                                val canAccept = !saving && selectedCampsite != null
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .toggleable(
                                            value = acceptedPolicies,
                                            enabled = canAccept,
                                            role = Role.Checkbox,
                                            onValueChange = { acceptedPolicies = it }
                                        )
                                        .testTag("acceptReservationPolicies"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = acceptedPolicies, onCheckedChange = null, enabled = canAccept)
                                    Text(
                                        "I have read and accept this campsite's rules and policies.",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item(key = "period") {
                                val period = loaded.periodFor(chosenCampsite)
                                Text("Booking period: ${period.startDate.format(dateFormat)} – ${period.endDate.format(dateFormat)}")
                            }

                            if (available.isEmpty()) {
                                item(key = "no-dates") { Text("No dates left at this campsite.") }
                            } else {
                                item(key = "date-instructions") { Text("Tap your first night, then your last night.") }
                                items(available, key = { "night-$it" }) { day ->
                                    val first = firstNight
                                    val dateSelected = first != null && !day.isBefore(first) && !day.isAfter(lastNight ?: first)
                                    TextButton(
                                        onClick = {
                                            when {
                                                first == null || lastNight != null -> {
                                                    firstNight = day
                                                    lastNight = null
                                                }
                                                day.isBefore(first) -> firstNight = day
                                                else -> lastNight = day
                                            }
                                        },
                                        enabled = !saving,
                                        modifier = Modifier.fillMaxWidth().semantics { selected = dateSelected }
                                    ) {
                                        Text(
                                            text = (if (dateSelected) "✓ " else "") + day.format(dateFormat),
                                            modifier = Modifier.fillMaxWidth(),
                                            color = when {
                                                saving -> Color.Unspecified
                                                dateSelected -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
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
            Button(
                enabled = !saving && !loading && selectedCampsite != null && stayAvailable && acceptedPolicies,
                onClick = {
                    if (chosenCampsite != null && start != null && end != null) {
                        onConfirm(chosenCampsite, start, end, occupancy)
                    }
                }
            ) { Text(if (saving) "Saving…" else "Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Close") }
        }
    )
}

private fun statusLabel(status: ReservationStatus): String =
    status.name.lowercase().replaceFirstChar { it.uppercase() }
