package com.example.myapplication.frontEnd.screens.ReservationBookingViews

import com.example.myapplication.backEnd.featureModels.BookingSchedule.Booking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.myapplication.backEnd.featureModels.*
import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.backEnd.userModels.User
import com.example.myapplication.frontEnd.screens.LotteryViews.BookingPeriodFields
import java.time.LocalDateTime

/** Uses the campsite collections kept current by CampsiteRepository. */
@Composable
internal fun ManageBookingPeriodsDialog(
    user: User,
    campsites: Map<String, Campsite>,
    repository: LotteryRepository,
    onDismiss: () -> Unit
) {
    // Guard the dialog itself as well as the button that opens it.
    val role = user.userType.trim().lowercase()
    if (role !in setOf("admin", "campsite owner")) return
    val admin = role == "admin"
    val lotteries = campsites.values.flatMap { it.lotteryCollection.orEmpty() }.distinctBy { it.id }
        .filter { lottery -> lottery.campsiteIds.all { id -> admin || campsites[id]?.ownerID == user.deviceID } }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val lottery = lotteries.firstOrNull { it.id == selectedId }
    val schedule = campsites.values.flatMap { it.bookingSchedules.orEmpty() }
        .firstOrNull { (it.registration as? Lottery)?.id == selectedId }
    var start by remember(selectedId, schedule != null) { mutableStateOf(schedule?.bookingStart?.toString().orEmpty()) }
    var end by remember(selectedId, schedule != null) { mutableStateOf(schedule?.bookingEnd?.toString().orEmpty()) }
    var custom by remember(selectedId, schedule != null) { mutableStateOf(schedule?.campsitePeriods?.isNotEmpty() == true) }
    var starts by remember(selectedId, schedule != null) { mutableStateOf(schedule?.campsitePeriods.orEmpty().mapValues { it.value.startDate.toString() }) }
    var ends by remember(selectedId, schedule != null) { mutableStateOf(schedule?.campsitePeriods.orEmpty().mapValues { it.value.endDate.toString() }) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Manage booking periods") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (lottery == null) {
                    Text("Choose a lottery by its campsites.")
                    if (lotteries.isEmpty()) Text("No manageable lotteries loaded. Check your connection and try again.")
                    lotteries.forEach { item ->
                        TextButton(onClick = { selectedId = item.id; error = null },
                            modifier = Modifier.testTag("manageLottery_${item.id}")) {
                            Column {
                                Text(item.campsiteIds.joinToString())
                                Text("${if (item.drawn) "Drawn" else "Registration"}: ${item.period.registrationStart.toLocalDate()} to ${item.period.registrationEnd.toLocalDate()}")
                            }
                        }
                    }
                } else if (schedule == null) {
                    Text("The booking schedule has not loaded. Check your connection and try again.")
                } else {
                    Text(lottery.campsiteIds.joinToString())
                    Text("Existing reservations must remain inside their campsite's booking period.")
                    BookingPeriodFields(lottery.campsiteIds, start, end, custom, starts, ends, !saving,
                        { start = it; error = null }, { end = it; error = null }, { custom = it; error = null },
                        { id, value -> starts = starts + (id to value); error = null },
                        { id, value -> ends = ends + (id to value); error = null })
                }
                error?.let { Text(it, modifier = Modifier.testTag("bookingPeriodEditError")) }
            }
        },
        confirmButton = {
            if (lottery != null && schedule != null) Button(
                enabled = !saving,
                modifier = Modifier.testTag("saveBookingPeriods"),
                onClick = {
                    val parsed = runCatching {
                        val default = Booking(startDate = LocalDateTime.parse(start), endDate = LocalDateTime.parse(end))
                        val overrides = if (custom) lottery.campsiteIds.associateWith { id ->
                            Booking(campsiteId = id, startDate = LocalDateTime.parse(starts[id] ?: start),
                                endDate = LocalDateTime.parse(ends[id] ?: end))
                        } else emptyMap()
                        require(default.validationError(lottery.period.registrationEnd) == null) { "Select a valid default period after registration with at least one full night." }
                        overrides.forEach { (id, period) -> require(period.validationError(lottery.period.registrationEnd) == null) { "$id: Select a valid booking period." } }
                        default to overrides
                    }
                    parsed.onFailure { error = it.message ?: "Select valid dates." }.onSuccess { (default, overrides) ->
                        saving = true
                        repository.updateBookingPeriods(user, lottery.campsiteIds.first(), default.startDate, default.endDate, overrides,
                            lotteryId = lottery.id)
                            .addOnSuccessListener {
                                // CampsiteRepository's Firestore listener loads the saved schedule.
                                // TODO(notifications): Notify affected owners and entrants after booking periods change.
                                saving = false
                                onDismiss()
                            }.addOnFailureListener { saving = false; error = it.message ?: "Could not save booking periods." }
                    }
                }
            ) { Text(if (saving) "Saving..." else "Save booking periods") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Close") } }
    )
}
