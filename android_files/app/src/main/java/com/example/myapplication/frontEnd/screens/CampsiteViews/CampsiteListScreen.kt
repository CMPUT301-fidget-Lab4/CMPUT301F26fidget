package com.example.myapplication.frontEnd.screens.campsiteViews

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.userModels.User
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.example.myapplication.backEnd.featureModels.BookingSchedule
import com.example.myapplication.backEnd.featureModels.Lottery
import com.example.myapplication.backEnd.firebaseRepositories.BookingScheduleRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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

@Composable
fun CampsiteListScreen(
    navController: NavController,
    userProfile: User?,
    campsites: Map<String, Campsite>,
    ownedOnly: Boolean,
    scheduleRepository: BookingScheduleRepository? = null
) {
    var filter by remember { mutableStateOf(CampsiteFilter()) }
    var schedules by remember {
        mutableStateOf<Map<String, List<BookingSchedule<Lottery>>>?>(null)
    }

    DisposableEffect(scheduleRepository) {
        val registration = scheduleRepository?.listenToAllSchedules { loaded ->
            schedules = loaded
                .flatMap { s -> s.registration.campsiteIds.map { id -> id to s } }
                .groupBy({ it.first }, { it.second })
        }
        onDispose { registration?.remove() }
    }
    val shownCampsites = campsites.entries.filter { !ownedOnly || it.value.ownerID == userProfile?.deviceID }
        .filter { (id, campsite) ->
            matchesLocation(campsite, filter.location) &&
                    matchesCapacity(campsite, filter.capacity) &&
                    matchesDate(id, campsite, filter.date, schedules)
        }
        .map { it.value }
        if (ownedOnly) {
            campsites.values.filter { it.ownerID == userProfile?.deviceID }
        } else {
            campsites.values.toList()
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Displaying ${shownCampsites.size} campsites")

        FilterBar(
            onApply = { filter = it },
            onReset = { filter = CampsiteFilter() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shownCampsites) { campsite ->
                Column(modifier = Modifier.fillMaxWidth().clickable {
                    navController.navigate("CampsiteDetails/${Uri.encode(campsite.name)}") }
                    .testTag("campsiteRow_${campsite.name}") // for testing
                ) {

                    Text(campsite.name)
                    Text("Capacity ${campsite.occupancy}")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (userProfile?.userType == "campsite owner" || userProfile?.userType == "admin") {
            Button(
                onClick = { navController.navigate("RegisterCampsite") },
                modifier = Modifier.testTag("registerNewCampsite") // for testing
            ) {
                Text("Register a new campsite")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("returnFromCampsiteList") // for testing
        ) {
            Text("Back To Main Screen")
        }
    }
}

data class CampsiteFilter(
    val location: String = "",
    val date: LocalDate? = null,
    val capacity: Int? = null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    onApply: (CampsiteFilter) -> Unit,
    onReset: () -> Unit
) {
    // What's typed in the boxes right now; not applied until the button is pressed
    var draft by remember { mutableStateOf(CampsiteFilter()) }
    var showDatePicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = draft.location,
        onValueChange = { draft = draft.copy(location = it) },
        label = { Text("Location") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("filterLocation")
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = draft.date?.toString() ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth().testTag("filterDate")
            )
            Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
        }
        OutlinedTextField(
            value = draft.capacity?.toString() ?: "",
            onValueChange = { text ->
                draft = draft.copy(capacity = text.filter(Char::isDigit).toIntOrNull())
            },
            label = { Text("Minimum Capacity") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("filterCapacity")
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { onApply(draft) },
            modifier = Modifier.testTag("filterApply")
        ) { Text("Filter") }
        TextButton(
            onClick = {
                draft = CampsiteFilter()   // clear the boxes
                onReset()                  // and show every campsite again
            },
            modifier = Modifier.testTag("filterReset")
        ) { Text("Reset") }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        draft = draft.copy(date = picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}
