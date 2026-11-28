package com.example.myapplication.frontEnd.screens.LotteryViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.example.myapplication.frontEnd.theme.AppLayout

/** Shared fields used when creating a lottery and managing its stay windows. */
@Composable
fun BookingPeriodFields(
    campsiteIds: List<String>,
    bookingStart: String,
    bookingEnd: String,
    customPeriods: Boolean,
    starts: Map<String, String>,
    ends: Map<String, String>,
    enabled: Boolean,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onCustomChange: (Boolean) -> Unit,
    onCampsiteStartChange: (String, String) -> Unit,
    onCampsiteEndChange: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
        Text("Default booking period")
        LotteryDateTimeFields("Booking Starts", bookingStart, onStartChange, "pickBookingStart", enabled)
        LotteryDateTimeFields("Booking Ends", bookingEnd, onEndChange, "pickBookingEnd", enabled)
        Row(
            modifier = Modifier.fillMaxWidth()
                .toggleable(customPeriods, enabled = enabled, role = Role.Checkbox, onValueChange = onCustomChange)
                .testTag("customBookingPeriods"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(customPeriods, onCheckedChange = null, enabled = enabled)
            Text("Choose separate periods for each campsite", modifier = Modifier.weight(1f))
        }
        if (customPeriods) campsiteIds.forEach { id ->
            Text(id)
            LotteryDateTimeFields("Booking Starts", starts[id] ?: bookingStart,
                { onCampsiteStartChange(id, it) }, "campsiteBookingStart_$id", enabled)
            LotteryDateTimeFields("Booking Ends", ends[id] ?: bookingEnd,
                { onCampsiteEndChange(id, it) }, "campsiteBookingEnd_$id", enabled)
        }
    }
}
