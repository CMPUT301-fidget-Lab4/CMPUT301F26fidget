package com.example.myapplication.frontEnd.screens.LotteryViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.myapplication.core.datetime.AlbertaTime
import com.example.myapplication.frontEnd.theme.AdaptiveDatePickerDialog
import com.example.myapplication.frontEnd.theme.AdaptiveTimePickerDialog
import com.example.myapplication.frontEnd.theme.AppLayout
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.CANADA)
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.CANADA)

/** Date and time pickers that edit a single ISO date-time value without database access. */
@Composable
internal fun LotteryDateTimeFields(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTagPrefix: String,
    enabled: Boolean = true
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val selectedDateTime = remember(value) {
        runCatching { LocalDateTime.parse(value) }.getOrNull()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.Small),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
        ) {
            OutlinedButton(
                enabled = enabled,
                onClick = { showDatePicker = true },
                modifier = Modifier.testTag("${testTagPrefix}DateButton")
            ) {
                Text(selectedDateTime?.format(dateFormat) ?: "Choose date")
            }
            OutlinedButton(
                enabled = enabled,
                onClick = { showTimePicker = true },
                modifier = Modifier.testTag("${testTagPrefix}TimeButton")
            ) {
                Text(selectedDateTime?.format(timeFormat) ?: "Choose time")
            }
        }
    }
    if (showDatePicker) {
        val current = selectedDateTime ?: remember { defaultPickerTime() }
        AdaptiveDatePickerDialog(
            selectedDate = current.toLocalDate(),
            onConfirm = { onValueChange(LocalDateTime.of(it, current.toLocalTime()).toString()) },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        val current = selectedDateTime ?: remember { defaultPickerTime() }
        AdaptiveTimePickerDialog(
            selectedTime = current.toLocalTime(),
            onConfirm = { onValueChange(LocalDateTime.of(current.toLocalDate(), it).toString()) },
            onDismiss = { showTimePicker = false }
        )
    }
}

private fun defaultPickerTime(): LocalDateTime =
    LocalDateTime.now(AlbertaTime.zone).withSecond(0).withNano(0)
