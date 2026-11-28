package com.example.myapplication.frontEnd.screens.LotteryViews

import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.lottery.Registration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout

/**
 * Displays the current listing and collects draft text only.
 * @param canEdit Permission computed by LotteryRepository in the view controller.
 * @param onSave Sends raw values to the controller; the repository parses, validates,
 * and persists them. This screen displays the result without changing domain models.
 * @param scheduleAvailable Whether the controller has loaded the required schedule.
 */
@Composable
fun EditLotteryScreen(
    lottery: Lottery?,
    canEdit: Boolean,
    onSave: (String, String, String, String, (Result<Unit>) -> Unit) -> Unit,
    onBack: () -> Unit,
    scheduleAvailable: Boolean,
    scheduleLoading: Boolean = false
) {
    if (lottery == null || !canEdit) {
        AdaptiveContent(maxWidth = AppLayout.FormWidth) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppLayout.Medium),
                verticalArrangement = Arrangement.spacedBy(AppLayout.Medium)
            ) {
                Text(
                    "This lottery is unavailable, already drawn, or you do not have permission to edit it.",
                    modifier = Modifier.testTag("editLotteryDenied")
                )

                Button(
                    onClick = onBack,
                    modifier = Modifier.testTag("editLotteryBack")
                ) {
                    Text("Back")
                }
            }
        }
        return
    }

    var start by rememberSaveable(lottery.id) { mutableStateOf(lottery.period.registrationStart.toString()) }
    var end by rememberSaveable(lottery.id) { mutableStateOf(lottery.period.registrationEnd.toString()) }
    var winners by rememberSaveable(lottery.id) { mutableStateOf(lottery.numWinners.toString()) }
    var capacity by rememberSaveable(lottery.id) { mutableStateOf(lottery.waitlistCapacity.toString()) }
    var error by rememberSaveable(lottery.id) { mutableStateOf<String?>(null) }
    var saving by remember(lottery.id) { mutableStateOf(false) }

    AdaptiveContent(maxWidth = AppLayout.FormWidth) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Medium)
        ) {
        Text(
            "Edit lottery listing",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("editLotteryTitle")
        )
        Text(
            lottery.campsiteIds.joinToString(),
            modifier = Modifier.testTag("editLotteryCampsites")
        )
        Text(waitlistSummary(lottery))
        Text("Changes apply to every campsite in this lottery. A capacity of 0 means no limit.")

        OutlinedTextField(
            winners,
            {
                winners = it
                error = null
            },
            label = { Text("Number of winners") },
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("editLotteryWinners")
        )
        OutlinedTextField(
            capacity,
            {
                capacity = it
                error = null
            },
            label = { Text("Waitlist capacity") },
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("editLotteryCapacity")
        )

        LotteryDateTimeFields(
            "Registration opens",
            start,
            {
                start = it
                error = null
            },
            "editLotteryStart",
            !saving
        )
        LotteryDateTimeFields(
            "Registration ends",
            end,
            {
                end = it
                error = null
            },
            "editLotteryEnd",
            !saving
        )

        if (scheduleLoading) {
            Text("Loading the booking schedule…")
        } else if (!scheduleAvailable) {
            Text(
                "Could not load the booking schedule. Reopen this listing to try again.",
                color = MaterialTheme.colorScheme.error
            )
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("editLotteryError")
            )
        }

        Button(
            enabled = !saving && !scheduleLoading && scheduleAvailable,
            modifier = Modifier.testTag("editLotterySave"),
            onClick = {
                if (saving || !scheduleAvailable) return@Button

                saving = true
                onSave(start, end, winners, capacity) { result ->
                    saving = false
                    result.fold(
                        onSuccess = { onBack() },
                        onFailure = {
                            error = it.message ?: "Could not save changes. Please try again."
                        }
                    )
                }
            }
        ) {
            Text(if (saving) "Saving..." else "Save changes")
        }

        OutlinedButton(
            onClick = onBack,
            enabled = !saving,
            modifier = Modifier.testTag("editLotteryBack")
        ) {
            Text("Cancel")
        }
        }
    }
}
