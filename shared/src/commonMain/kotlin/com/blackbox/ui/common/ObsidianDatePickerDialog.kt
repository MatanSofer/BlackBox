package com.blackbox.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.blackbox.ui.theme.BlackBoxColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Obsidian-themed date picker dialog shared by the Timeline and Map screens.
 *
 * - Future dates are always disabled.
 * - When [availableDates] is non-empty only those dates are selectable; passing
 *   an empty set disables the availability filter (all past dates selectable).
 * - The calendar is initialised to [selectedDate] and returns the chosen date
 *   as a "yyyy-MM-dd" string via [onDateSelected].
 *
 * @param selectedDate   Currently displayed date ("yyyy-MM-dd").
 * @param availableDates Set of "yyyy-MM-dd" strings that have recorded data.
 *                       Empty means "no filter — allow all past dates".
 * @param onDateSelected Called with the new date string when the user confirms.
 * @param onDismiss      Called when the dialog is dismissed without a selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObsidianDatePickerDialog(
    selectedDate: String,
    availableDates: Set<String>,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMs = System.currentTimeMillis()
    val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = localDateToPickerMs(selectedDate),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // Disable future dates
                if (utcTimeMillis > todayMs) return false
                // If no filter, allow all past dates
                if (availableDates.isEmpty()) return true
                // Check against the available dates set using local timezone
                val dateStr = localFormat.format(Date(utcTimeMillis))
                return dateStr in availableDates
            }

            override fun isSelectableYear(year: Int): Boolean {
                val thisYear = Calendar.getInstance().get(Calendar.YEAR)
                return year <= thisYear
            }
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val ms = datePickerState.selectedDateMillis ?: return@TextButton
                    onDateSelected(localFormat.format(Date(ms)))
                    onDismiss()
                },
            ) {
                Text("Select", color = BlackBoxColors.Indigo)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BlackBoxColors.TextSecondary)
            }
        },
        colors = DatePickerDefaults.colors(
            containerColor = BlackBoxColors.Surface,
            titleContentColor = BlackBoxColors.TextSecondary,
            headlineContentColor = BlackBoxColors.TextPrimary,
            weekdayContentColor = BlackBoxColors.TextTertiary,
            subheadContentColor = BlackBoxColors.TextSecondary,
            yearContentColor = BlackBoxColors.TextPrimary,
            currentYearContentColor = BlackBoxColors.Indigo,
            selectedYearContainerColor = BlackBoxColors.Indigo,
            selectedYearContentColor = BlackBoxColors.OnAccent,
            dayContentColor = BlackBoxColors.TextPrimary,
            disabledDayContentColor = BlackBoxColors.TextTertiary,
            selectedDayContainerColor = BlackBoxColors.Indigo,
            selectedDayContentColor = BlackBoxColors.OnAccent,
            todayContentColor = BlackBoxColors.IndigoLight,
            todayDateBorderColor = BlackBoxColors.Indigo,
            dayInSelectionRangeContainerColor = BlackBoxColors.IndigoDim,
            dayInSelectionRangeContentColor = BlackBoxColors.IndigoLight,
            navigationContentColor = BlackBoxColors.TextSecondary,
        ),
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * Converts a "yyyy-MM-dd" local date string to the UTC millisecond value that
 * the Material 3 [DatePicker] expects as its initial selection.
 *
 * The picker operates in UTC, so we set the calendar fields explicitly and
 * read the UTC millisecond timestamp, ensuring the correct day is highlighted
 * regardless of the device's local timezone offset.
 */
private fun localDateToPickerMs(date: String): Long? = try {
    val parts = date.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toInt()
    val month = parts[1].toInt() - 1 // Calendar months are 0-based
    val day = parts[2].toInt()
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(year, month, day, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
} catch (_: Exception) { null }
