package com.example.myapplication.frontEnd.screens.LotteryViews

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

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
    Column {
        Text("Default booking period")
        LotteryDateTimeFields("Booking Starts", bookingStart, onStartChange, "pickBookingStart", enabled)
        LotteryDateTimeFields("Booking Ends", bookingEnd, onEndChange, "pickBookingEnd", enabled)
        Row {
            Checkbox(customPeriods, onCustomChange, enabled = enabled, modifier = Modifier.testTag("customBookingPeriods"))
            Text("Choose separate periods for each campsite")
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
