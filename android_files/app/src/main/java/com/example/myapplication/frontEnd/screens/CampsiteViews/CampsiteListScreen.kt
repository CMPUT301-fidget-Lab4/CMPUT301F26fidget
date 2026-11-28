package com.example.myapplication.frontEnd.screens.campsiteViews

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.data.firebase.BookingScheduleRepository
import com.example.myapplication.data.firebase.LotteryRepository.Companion.currentCampsiteLottery
import com.example.myapplication.core.datetime.AlbertaTime
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.screens.LotteryViews.lotteryInfoDateFormat
import com.example.myapplication.frontEnd.screens.LotteryViews.lotteryRegistrationStatus
import com.example.myapplication.frontEnd.screens.LotteryViews.waitlistSummary
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AdaptiveDatePickerDialog
import com.example.myapplication.frontEnd.theme.AppLayout
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * CampsiteListScreen displays a list of campsites.
 *
 * Entrants use it to view all campsites, whereas Campsite owners use it to view their own
 * campsites. Campsite owners also have a Register new campsite button, which opens the
 * register screen.
 *
 * @param navController
 * @param userProfile: the current user's [User] object.
 * @param campsites: All campsites from [CampsiteRepository].
 * @param ownedOnly: If true, only campsites owned by the current user are shown.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampsiteListScreen(
    navController: NavController,
    userProfile: User?,
    campsites: Map<String, Campsite>,
    ownedOnly: Boolean,
    bookingSchedules: BookingScheduleRepository.Catalog = BookingScheduleRepository.Catalog(),
    onRetrySchedules: () -> Unit = {}
) {
    var filter by rememberSaveable(stateSaver = CampsiteFilterSaver) { mutableStateOf(CampsiteFilter()) }
    var draft by rememberSaveable(stateSaver = CampsiteFilterSaver) { mutableStateOf(CampsiteFilter()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val shownCampsites = campsites.entries.filter { !ownedOnly || it.value.ownerID == userProfile?.deviceID }
        .filter { (id, campsite) ->
            matchesLocation(campsite, filter.location) &&
                    matchesCapacity(campsite, filter.capacity) &&
                    matchesDate(id, campsite, filter.date, bookingSchedules.byCampsite)
        }
        .map { it.value }
    AdaptiveContent {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("campsiteListScroll"),
            contentPadding = PaddingValues(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item("availability") {
                val availability = if (filter.date != null) bookingSchedules.availabilityMessage() else null
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(availability ?: "Displaying ${shownCampsites.size} campsites")
                    if (filter.date != null && bookingSchedules.error != null) {
                        TextButton(onClick = onRetrySchedules) { Text("Retry date availability") }
                    }
                }
            }
            item("filters") {
                FilterBar(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onChooseDate = {
                        focusManager.clearFocus()
                        showDatePicker = true
                    },
                    onApply = {
                        filter = draft
                        focusManager.clearFocus()
                    },
                    onReset = {
                        val emptyFilter = CampsiteFilter()
                        draft = emptyFilter
                        filter = emptyFilter
                        focusManager.clearFocus()
                    }
                )
            }
            item("actions") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppLayout.Small, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
                ) {
                    if (userProfile?.userType == "campsite owner" || userProfile?.userType == "admin") {
                        Button(
                            onClick = { navController.navigate("RegisterCampsite") },
                            modifier = Modifier.testTag("registerNewCampsite")
                        ) { Text("Register a new campsite") }
                    }
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("returnFromCampsiteList")
                    ) { Text("Back To Main Screen") }
                }
            }
            items(shownCampsites) { campsite ->
                Column(modifier = Modifier.fillMaxWidth().clickable {
                    navController.navigate("CampsiteDetails/${Uri.encode(campsite.name)}") }
                    .testTag("campsiteRow_${campsite.name}") // for testing
                ) {

                    Text(campsite.name)
                    Text("Campsite capacity: ${campsite.occupancy} people", modifier = Modifier.testTag("campsiteCapacity_${campsite.name}"))
                    val lottery = currentCampsiteLottery(campsite)
                    when {
                        campsite.lotteryCollection == null -> Text("Loading waitlist information...")
                        lottery == null -> Text("No active lottery")
                        else -> {
                            Text(waitlistSummary(lottery), modifier = Modifier.testTag("campsiteWaitlist_${campsite.name}"))
                            if (lottery.campsiteIds.size > 1) Text("Shared across ${lottery.campsiteIds.size} campsites")
                            Text(lotteryRegistrationStatus(lottery, LocalDateTime.now(AlbertaTime.zone)))
                            Text("Registration ends: ${lottery.period.registrationEnd.format(lotteryInfoDateFormat)} (Alberta time)")
                        }
                    }
                }
            }
        }
    }
    if (showDatePicker) {
        AdaptiveDatePickerDialog(
            selectedDate = draft.date,
            onConfirm = {
                draft = draft.copy(date = it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

internal fun BookingScheduleRepository.Catalog.availabilityMessage(): String? = when {
    error != null && schedules == null -> "Couldn't verify date availability. Showing location and capacity matches."
    error != null -> "Couldn't refresh date availability. Showing previously loaded matches."
    checking && schedules == null -> "Checking date availability. Showing location and capacity matches."
    checking -> "Checking date availability. Showing previously loaded matches."
    fromCache -> "Showing saved date availability. Connect to verify these matches."
    else -> null
}

data class CampsiteFilter(
    val location: String = "",
    val date: LocalDate? = null,
    val capacity: Int? = null
)

private val CampsiteFilterSaver = listSaver<CampsiteFilter, String>(
    save = { listOf(it.location, it.date?.toString().orEmpty(), it.capacity?.toString().orEmpty()) },
    restore = {
        CampsiteFilter(
            location = it[0],
            date = it[1].takeIf(String::isNotEmpty)?.let(LocalDate::parse),
            capacity = it[2].toIntOrNull()
        )
    }
)

/** Every word typed must appear somewhere in the campsite's address. */
internal fun matchesLocation(campsite: Campsite, query: String): Boolean {
    val words = query.lowercase().split(Regex("[\\s,]+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val address = campsite.location?.address?.lowercase() ?: return false
    return words.all { it in address }
}

/** Campsite holds at least the requested number of people. */
internal fun matchesCapacity(campsite: Campsite, minimum: Int?): Boolean =
    minimum == null || campsite.occupancy >= minimum

/** Some stay window for this campsite covers the day and still has room. */
internal fun matchesDate(
    id: String,
    campsite: Campsite,
    date: LocalDate?,
    schedules: Map<String, List<BookingSchedule<Lottery>>>?
): Boolean {
    if (date == null || schedules == null) return true // no date chosen, or schedules not loaded yet
    return schedules[id].orEmpty().any { it.availableDates(id, campsite.occupancy).contains(date) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    draft: CampsiteFilter,
    onDraftChange: (CampsiteFilter) -> Unit,
    onChooseDate: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
    ) {
        OutlinedTextField(
            value = draft.location,
            onValueChange = { onDraftChange(draft.copy(location = it)) },
            label = { Text("Location") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("filterLocation")
        )
        CampsiteFieldPair(
            first = { field ->
                OutlinedButton(onClick = onChooseDate, modifier = field.testTag("filterDate")) {
                    Text(draft.date?.let { "Date: $it" } ?: "Choose date")
                }
            },
            second = { field ->
                OutlinedTextField(
                    value = draft.capacity?.toString() ?: "",
                    onValueChange = { text ->
                        onDraftChange(draft.copy(capacity = text.filter(Char::isDigit).toIntOrNull()))
                    },
                    label = { Text("Minimum Capacity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = field.testTag("filterCapacity")
                )
            }
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.Small, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
        ) {
            Button(onClick = onApply, modifier = Modifier.testTag("filterApply")) { Text("Filter") }
            TextButton(onClick = onReset, modifier = Modifier.testTag("filterReset")) { Text("Reset") }
        }
    }
}
