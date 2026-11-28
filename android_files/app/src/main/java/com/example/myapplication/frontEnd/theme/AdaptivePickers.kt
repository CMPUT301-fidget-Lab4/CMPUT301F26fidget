package com.example.myapplication.frontEnd.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Adapts Material's UTC picker value to the calendar date used by campsite and lottery forms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveDatePickerDialog(
    selectedDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val useInput = pickerNeedsInputMode()
    val initialMillis = selectedDate?.takeIf { it.year in DatePickerDefaults.YearRange }
        ?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        initialDisplayMode = if (useInput) DisplayMode.Input else DisplayMode.Picker
    )
    LaunchedEffect(useInput) {
        if (useInput && state.displayMode != DisplayMode.Input) state.displayMode = DisplayMode.Input
    }

    AdaptivePickerDialog(
        onDismiss = onDismiss,
        confirmEnabled = state.selectedDateMillis != null,
        onConfirm = {
            state.selectedDateMillis?.let { millis ->
                onConfirm(Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate())
                onDismiss()
            }
        }
    ) {
        DatePicker(state = state, showModeToggle = !useInput)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveTimePickerDialog(
    selectedTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val useInput = pickerNeedsInputMode()
    val state = rememberTimePickerState(selectedTime.hour, selectedTime.minute, is24Hour = false)
    AdaptivePickerDialog(
        onDismiss = onDismiss,
        confirmEnabled = true,
        onConfirm = {
            onConfirm(LocalTime.of(state.hour, state.minute))
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppLayout.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppLayout.Medium)
        ) {
            Text("Select time", style = MaterialTheme.typography.titleLarge)
            if (useInput) TimeInput(state) else TimePicker(state, layoutType = TimePickerLayoutType.Vertical)
        }
    }
}

@Composable
private fun pickerNeedsInputMode(): Boolean {
    val configuration = LocalConfiguration.current
    // The seven-column Material calendar needs more room than numeric date entry.
    return configuration.screenWidthDp < 392 || configuration.screenHeightDp < 560 ||
        LocalDensity.current.fontScale >= 1.3f
}

@Composable
private fun AdaptivePickerDialog(
    onDismiss: () -> Unit,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().safeDrawingPadding().imePadding()
                .padding(AppLayout.Medium),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = AppLayout.FormWidth).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = AppLayout.Small
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    content()
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(AppLayout.Small),
                        horizontalArrangement = Arrangement.spacedBy(AppLayout.Small, Alignment.End)
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text("OK") }
                    }
                }
            }
        }
    }
}
