/*
 * API references: Android Developers
 *
 * DatePickerDialog: built-in dialog for selecting a date.
 * https://developer.android.com/reference/android/app/DatePickerDialog
 *
 * TimePickerDialog: built-in dialog for selecting a time.
 * https://developer.android.com/reference/android/app/TimePickerDialog
 *
 * OutlinedButton: Material 3 Compose buttons used to open the dialogs.
 * https://developer.android.com/reference/kotlin/androidx/compose/material3/OutlinedButton.composable
 *
 * Add pickers to your app: guide to Android date and time picker dialogs.
 * https://developer.android.com/develop/ui/views/components/pickers
 *
 * These references document the APIs used, not the source of this complete file.
 * LotteryDateTimeFields is custom Compose code that arranges the controls,
 * formats and combines date/time values, and reports changes via onValueChange.
 */
package com.example.myapplication.frontEnd.screens.lotteryViews

import com.example.myapplication.backEnd.time.AlbertaTime

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    val context = LocalContext.current
    val selectedDateTime = remember(value) {
        runCatching { LocalDateTime.parse(value) }.getOrNull()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                enabled = enabled,
                onClick = {
                    val current = selectedDateTime ?: defaultPickerTime()
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val date = LocalDate.of(year, month + 1, day)
                            onValueChange(LocalDateTime.of(date, current.toLocalTime()).toString())
                        },
                        current.year,
                        current.monthValue - 1,
                        current.dayOfMonth
                    ).show()
                },
                modifier = Modifier.weight(1f).testTag("${testTagPrefix}DateButton")
            ) {
                Text(selectedDateTime?.format(dateFormat) ?: "Choose date")
            }
            OutlinedButton(
                enabled = enabled,
                onClick = {
                    val current = selectedDateTime ?: defaultPickerTime()
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val time = LocalTime.of(hour, minute)
                            onValueChange(LocalDateTime.of(current.toLocalDate(), time).toString())
                        },
                        current.hour,
                        current.minute,
                        false
                    ).show()
                },
                modifier = Modifier.weight(1f).testTag("${testTagPrefix}TimeButton")
            ) {
                Text(selectedDateTime?.format(timeFormat) ?: "Choose time")
            }
        }
    }
}

private fun defaultPickerTime(): LocalDateTime =
    LocalDateTime.now(AlbertaTime.zone).withSecond(0).withNano(0)
