package com.example.wardrobeapp.ui.outfit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wardrobeapp.domain.model.floorToUtcMidnight
import com.example.wardrobeapp.domain.model.normalizeToUtcDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Shared pieces for planning an outfit to a day: date helpers, the "wear today / pick a date"
 * chooser, the future-only date picker, and the one-outfit-per-day replace confirmation.
 * Used by the generator, the manual builder, My Outfits, and the calendar's outfit picker.
 */

/** A schedule request paused because [date] already has an outfit; confirmed by the user. */
data class PendingSchedule(val date: Long, val existingOutfitName: String)

/**
 * "today" for the current day, otherwise e.g. "August 4". [date] is always an already-resolved
 * day-key by the time it reaches here (from a date picker or a stored schedule), so this floors
 * it rather than re-running local-time-zone interpretation -- see [floorToUtcMidnight]'s doc for
 * why doing the latter here was a real, reported bug (a picked "August 8" displaying as "August 7").
 */
fun planDateLabel(date: Long): String {
    val day = floorToUtcMidnight(date)
    if (day == normalizeToUtcDay(System.currentTimeMillis())) return "today"
    val sdf = SimpleDateFormat("MMMM d", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(day))
}

/** Chooser between wearing the outfit today or planning it for a future date. */
@Composable
fun WearOptionsDialog(
    outfitName: String,
    onWearToday: () -> Unit,
    onPickDate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule \"$outfitName\"") },
        text = {
            Column {
                TextButton(onClick = onWearToday, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Today, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Wear now (plan for today)")
                }
                TextButton(onClick = onPickDate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Event, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Wear later (pick a date)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Date picker restricted to today and future days, for planning ahead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDatePickerDialog(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val today = normalizeToUtcDay(System.currentTimeMillis())
    val state = rememberDatePickerState(
        initialSelectedDateMillis = today,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                // selectedDateMillis is already UTC midnight of the visually-picked date (Compose's
                // own convention) -- floor, don't re-run local-time-zone interpretation on it, or
                // it shifts a day backward for EST/EDT (see floorToUtcMidnight's doc).
                onClick = { state.selectedDateMillis?.let { onPick(floorToUtcMidnight(it)) } },
                enabled = state.selectedDateMillis != null
            ) { Text("Plan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/** Confirms replacing the single outfit already planned for a day. */
@Composable
fun ReplacePlanDialog(
    pending: PendingSchedule,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace planned outfit?") },
        text = {
            Text(
                "\"${pending.existingOutfitName}\" is already planned for " +
                    "${planDateLabel(pending.date)}. Only one outfit can be planned per day."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Replace") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
