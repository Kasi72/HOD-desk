package com.hoddesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.Squircles
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** iOS-style alert card hosting a single text field. */
@Composable
fun TextPromptDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, message: String? = null, numeric: Boolean = false, multiline: Boolean = false, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(Squircles.card).background(Hod.colors.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Hod.type.headline, color = Hod.colors.label)
            message?.let { Text(it, style = Hod.type.footnote, color = Hod.colors.secondaryLabel) }
            OutlinedTextField(
                value = value, onValueChange = { value = if (numeric) it.filter(Char::isDigit).take(4) else it },
                label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 3 else 1,
                shape = Squircles.small, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Hod.colors.tint, focusedLabelColor = Hod.colors.tint, cursorColor = Hod.colors.tint),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HodButton("Cancel", onDismiss, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton(confirm, { onConfirm(value) }, height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, destructive: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(Squircles.card).background(Hod.colors.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Hod.type.headline, color = Hod.colors.label)
            Text(message, style = Hod.type.subheadline, color = Hod.colors.secondaryLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HodButton("Cancel", onDismiss, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton(confirm, onConfirm, style = if (destructive) HodButtonStyle.Destructive else HodButtonStyle.Filled, height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Two-step date then time picker. Returns epoch millis in the device zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(initial: Long?, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = initial?.let { Instant.ofEpochMilli(it).atZone(zone) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val c = Hod.colors
    if (pickedDate == null) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = (start?.toLocalDate() ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { dateState.selectedDateMillis?.let { pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } }) { Text("Next", color = c.tint) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.tint) } },
            colors = DatePickerDefaults.colors(containerColor = c.surface),
        ) { DatePicker(state = dateState, colors = DatePickerDefaults.colors(selectedDayContainerColor = c.tint, todayDateBorderColor = c.tint, todayContentColor = c.tint)) }
    } else {
        TimeDialog(title = "Time", initialMinute = start?.toLocalTime()?.let { it.hour * 60 + it.minute } ?: (10 * 60), onDismiss = onDismiss) { minute ->
            onPicked(pickedDate!!.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant().toEpochMilli())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, initialMinute: Int, onDismiss: () -> Unit, onPicked: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = false)
    val c = Hod.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Hod.type.headline, color = c.label)
            TimeInput(state = state, colors = TimePickerDefaults.colors(timeSelectorSelectedContainerColor = c.tintSoft, timeSelectorSelectedContentColor = c.tint, periodSelectorSelectedContainerColor = c.tintSoft, periodSelectorSelectedContentColor = c.tint))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HodButton("Cancel", onDismiss, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton("Done", { onPicked(state.hour * 60 + state.minute) }, height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}
