package com.example.myapplication.frontEnd.screens.LotteryViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout

/**
 * First creation step: collects campsite, lottery, and registration options.
 * [onNext] asks the controller to validate the values before opening booking periods.
 * Each input reports its value through a callback.
 */
@Composable
fun CreateLotteryScreen(
    campsiteName: String,
    additionalCampsiteIds: List<String>,
    numWinners: String,
    waitlistCapacity: String,
    registrationStart: String,
    registrationEnd: String,
    campsiteNames: List<String>,
    errorMessage: String?,
    onCampsiteChange: (String) -> Unit,
    onAdditionalCampsitesChange: (List<String>) -> Unit,
    onNumWinnersChange: (String) -> Unit,
    onWaitlistCapacityChange: (String) -> Unit,
    onRegistrationStartChange: (String) -> Unit,
    onRegistrationEndChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val sortedCampsiteNames = campsiteNames.distinct().sorted()

    AdaptiveContent(maxWidth = AppLayout.FormWidth) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(AppLayout.Medium).testTag("createLotteryScreen"),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Medium)
        ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .sizeIn(minWidth = 72.dp, minHeight = 48.dp)
                .testTag("createLotteryBackButton")
        ) { Text("Back", style = MaterialTheme.typography.labelLarge) }
        Text(
            "Create Lottery",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("createLotteryTitle")
        )
        Text("Step 1 of 2: Choose campsites and registration options.")
        HorizontalDivider()

        Text("Select Campsite", style = MaterialTheme.typography.titleMedium)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { dropdownExpanded = true },
                enabled = sortedCampsiteNames.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("createLotteryCampsiteDropdown")
            ) {
                Text(campsiteName.ifBlank { "Choose a campsite" })
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                sortedCampsiteNames.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onCampsiteChange(name)
                            dropdownExpanded = false
                        },
                        modifier = Modifier.testTag("createLotteryPrimaryOption_$name")
                    )
                }
            }
        }

        if (sortedCampsiteNames.isEmpty()) {
            Text(
                "No eligible campsites available. Register a campsite or choose one without an active lottery.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("createLotteryNoCampsites")
            )
        }

        if (sortedCampsiteNames.size > 1 && campsiteName.isNotBlank()) {
            Text("Additional Campsites", style = MaterialTheme.typography.titleMedium)
            Text("Optional: Include other campsites in the same lottery.")
            sortedCampsiteNames.filterNot { it == campsiteName }.forEach { name ->
                val selected = name in additionalCampsiteIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = selected,
                            role = Role.Checkbox,
                            onValueChange = { checked ->
                                val updatedCampsites = if (checked) {
                                    (additionalCampsiteIds + name).distinct()
                                } else {
                                    additionalCampsiteIds.filterNot { it == name }
                                }
                                onAdditionalCampsitesChange(updatedCampsites)
                            }
                        )
                        .testTag("createLotteryAdditionalOption_$name"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The row owns the toggle so one tap produces one update.
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        modifier = Modifier.testTag("createLotteryAdditionalCheckbox_$name")
                    )
                    Text(name, modifier = Modifier.weight(1f))
                }
            }
        }

        HorizontalDivider()
        Text("Lottery Settings", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = numWinners,
            onValueChange = onNumWinnersChange,
            label = { Text("Number of Winners") },
            supportingText = { Text("How many entrants should win?") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("createLotteryNumWinnersField")
        )
        OutlinedTextField(
            value = waitlistCapacity,
            onValueChange = onWaitlistCapacityChange,
            label = { Text("Waitlist Capacity") },
            supportingText = { Text("Maximum entrants on the waitlist. Use 0 for no limit.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("createLotteryWaitlistCapacityField")
        )

        HorizontalDivider()
        Text("Registration Period", style = MaterialTheme.typography.titleMedium)
        Text("Choose when visitors can register. All dates and times use Alberta time (UTC-06:00).")
        LotteryDateTimeFields(
            label = "Registration Starts",
            value = registrationStart,
            onValueChange = onRegistrationStartChange,
            testTagPrefix = "createLotteryStart"
        )
        LotteryDateTimeFields(
            label = "Registration Ends",
            value = registrationEnd,
            onValueChange = onRegistrationEndChange,
            testTagPrefix = "createLotteryEnd"
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .testTag("createLotteryResult")
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Button(
            onClick = onNext,
            enabled = sortedCampsiteNames.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("createLotteryNextButton")
        ) { Text("Next: Pick Booking Periods") }
        }
    }
}
