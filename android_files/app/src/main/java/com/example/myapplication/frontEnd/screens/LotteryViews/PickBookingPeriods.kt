package com.example.myapplication.frontEnd.screens.LotteryViews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Final creation step. The controller validates and saves when [onCreate] is invoked. */
@Composable
fun PickBookingPeriodsScreen(
    campsiteName: String,
    additionalCampsiteIds: List<String>,
    numWinners: String,
    registrationEnd: String,
    bookingStart: String,
    bookingEnd: String,
    errorMessage: String?,
    isCreating: Boolean,
    onBookingStartChange: (String) -> Unit,
    onBookingEndChange: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit
) {
    // A pending save must finish before leaving; pressing back never submits a second request.
    BackHandler { if (!isCreating) onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("pickBookingPeriodsScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(
            onClick = onBack,
            enabled = !isCreating,
            modifier = Modifier
                .sizeIn(minWidth = 72.dp, minHeight = 48.dp)
                .testTag("pickBookingPeriodsBackButton")
        ) { Text("Back", style = MaterialTheme.typography.labelLarge) }
        Text(
            "Pick Booking Periods",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("pickBookingPeriodsTitle")
        )
        Text("Step 2 of 2: Choose the period when lottery winners can stay.")
        Text("All dates and times use Alberta time (UTC-06:00).")
        HorizontalDivider()

        Text("Lottery summary", style = MaterialTheme.typography.titleMedium)
        Text("Campsites: ${(listOf(campsiteName) + additionalCampsiteIds).joinToString()}")
        Text("Winners: $numWinners")
        Text("Registration closes: ${formatDateTime(registrationEnd)}")
        Text("This booking period applies to every selected campsite.")
        HorizontalDivider()

        LotteryDateTimeFields(
            label = "Booking Starts",
            value = bookingStart,
            onValueChange = onBookingStartChange,
            testTagPrefix = "pickBookingStart",
            enabled = !isCreating
        )
        LotteryDateTimeFields(
            label = "Booking Ends",
            value = bookingEnd,
            onValueChange = onBookingEndChange,
            testTagPrefix = "pickBookingEnd",
            enabled = !isCreating
        )
        Text(
            "Winners currently select whole nights. Midnight boundaries let them book all nights in this period.",
            style = MaterialTheme.typography.bodySmall
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .testTag("pickBookingPeriodsError")
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        Button(
            onClick = onCreate,
            enabled = !isCreating,
            modifier = Modifier.fillMaxWidth().testTag("pickBookingPeriodsCreateButton")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (isCreating) "Creating lottery..." else "Create Lottery")
            }
        }
    }
}

private fun formatDateTime(value: String): String = runCatching {
    LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.CANADA))
}.getOrDefault("Choose registration dates on the previous page")
