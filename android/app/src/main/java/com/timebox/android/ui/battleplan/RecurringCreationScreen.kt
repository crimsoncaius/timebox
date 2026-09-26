package com.timebox.android.ui.battleplan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.data.*
import com.timebox.android.ui.components.*
import com.timebox.android.ui.theme.TimeboxTheme

@Composable
internal fun RecurringPreplanningScheduleEditor(
    state: RecurringEditorUiState,
    onEnabled: (Boolean) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onStart: (Int, String) -> Unit,
    onEnd: (Int, String) -> Unit,
    onWeekday: (Int, Int) -> Unit,
    showEnabledSwitch: Boolean = true,
) {
    val slots = state.preplanningSlots
    if (showEnabledSwitch) Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Pre-plan each Task Occurrence", style = TimeboxTheme.type.body)
            Text("Create attached Planned Blocks in the existing seven-day horizon.", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        }
        Switch(
            slots.isNotEmpty(),
            onEnabled,
            enabled = !state.saving,
            modifier = Modifier.semantics { contentDescription = "Pre-plan each Task Occurrence" },
        )
    }
    slots.forEachIndexed { index, slot ->
        val suffix = if (index == 0) "" else " ${index + 1}"
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Planned Block slot ${index + 1}", style = TimeboxTheme.type.label)
            if (state.frequency == RecurrenceFrequency.Weekly) {
                RecurrenceMenu(
                    "Pre-planning weekday$suffix",
                    weekdayLabel(slot.weekday ?: state.weekdays.minOrNull()),
                    state.weekdays.sorted().map { weekdayLabel(it) to it },
                ) { onWeekday(index, it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    slot.start,
                    { onStart(index, it) },
                    Modifier.weight(1f),
                    label = { Text("Planned Block start$suffix") },
                    placeholder = { Text("HH:MM") },
                    singleLine = true,
                )
                OutlinedTextField(
                    slot.end,
                    { onEnd(index, it) },
                    Modifier.weight(1f),
                    label = { Text("Planned Block end$suffix") },
                    placeholder = { Text("HH:MM") },
                    singleLine = true,
                )
            }
            TextButton(
                { onRemoveSlot(index) },
                modifier = Modifier.semantics { contentDescription = "Remove pre-planning slot ${index + 1}" },
            ) { Text("Remove slot", color = TimeboxTheme.colors.error) }
        }
    }
    if (slots.isNotEmpty()) {
        TextButton(onAddSlot) { Text("Add Planned Block slot") }
    }
}

private fun weekdayLabel(weekday: Int?): String = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
).getOrNull(weekday ?: -1) ?: "Choose weekday"

@Composable
internal fun CreationMode(title: String, subtitle: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp), color = if (selected) colors.selected else colors.card, border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.primary else colors.hairline)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = TimeboxTheme.type.sectionTitle)
                Text(subtitle, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            RadioButton(selected, null)
        }
    }
}

@Composable
internal fun CreationCount(label: String, value: Int, maximum: Int, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = TimeboxTheme.type.label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ onChange((value - 1).toString()) }, Modifier.size(48.dp).semantics { contentDescription = "Decrease $label" }, enabled = value > 1, contentPadding = PaddingValues(0.dp)) { Text("−") }
            Text(value.toString(), style = TimeboxTheme.type.sectionTitle)
            OutlinedButton({ onChange((value + 1).toString()) }, Modifier.size(48.dp).semantics { contentDescription = "Increase $label" }, enabled = value < maximum, contentPadding = PaddingValues(0.dp)) { Text("+") }
        }
    }
}

@Composable
internal fun CreationDateRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = TimeboxTheme.colors.card, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = TimeboxTheme.type.label, color = TimeboxTheme.colors.onVariant)
                Text(value, style = TimeboxTheme.type.body)
            }
            Text("Change", style = TimeboxTheme.type.label, color = TimeboxTheme.colors.primary)
        }
    }
}
