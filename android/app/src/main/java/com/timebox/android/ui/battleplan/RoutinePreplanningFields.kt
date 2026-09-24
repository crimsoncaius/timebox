package com.timebox.android.ui.battleplan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxTheme

import androidx.compose.ui.semantics.contentDescription

internal enum class PreplanningDestination(val title: String, val shortTitle: String, val detail: String, val icon: ImageVector) {
    None("No pre-planning", "None", "Decide what to do with each occurrence yourself.", Icons.Outlined.RemoveCircleOutline),
    Queue("Ready to Plan", "Queue", "Add it to the queue on its date. Choose a time later.", Icons.AutoMirrored.Outlined.PlaylistAdd),
    Time("Planned time", "Time", "Reserve a time for each occurrence.", Icons.Outlined.Schedule),
}


/** A: a single, always-visible list. Each row explains the destination. */
@Composable
internal fun PreplanningDestinationChoices(choice: PreplanningDestination, onChoice: (PreplanningDestination) -> Unit) {
    val colors = TimeboxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreplanningDestination.entries.forEach { option ->
            val active = choice == option
            Surface(onClick = { onChoice(option) }, shape = RoundedCornerShape(16.dp),
                color = if (active) colors.selected else colors.sheet,
                border = BorderStroke(1.dp, if (active) colors.primary else colors.hairline),
                modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton; selected = active }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(option.icon, null, tint = colors.onVariant, modifier = Modifier.size(23.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(option.title, style = TimeboxTheme.type.body, fontWeight = FontWeight.Medium)
                        Text(option.detail, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    }
                    RadioButton(active, null, Modifier.size(24.dp))
                }
            }
        }
    }
}


@Composable
internal fun RoutinePreplanningFields(state: RecurringEditorUiState, onChange: (RecurringEditorUiState) -> Unit) {
    val colors = TimeboxTheme.colors
    val choice = when (state.preplanningDestination()) {
        "ready_to_plan" -> PreplanningDestination.Queue
        "planned_time" -> PreplanningDestination.Time
        else -> PreplanningDestination.None
    }
    val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    fun updateSlot(index: Int, change: (RecurringPreplanningSlotDraft) -> RecurringPreplanningSlotDraft) {
        onChange(state.copy(preplanningSlots = state.preplanningSlots.mapIndexed { i, slot -> if (i == index) change(slot) else slot }))
    }
    PreplanningDestinationChoices(choice) { destination ->
        onChange(state.copy(queuePreplanning = destination == PreplanningDestination.Queue,
            preplanningSlots = if (destination == PreplanningDestination.Time) state.preplanningSlots.ifEmpty {
                listOf(RecurringPreplanningSlotDraft(weekday = state.weekdays.minOrNull()))
            } else emptyList()))
    }
    if (choice != PreplanningDestination.Time) return
    state.preplanningSlots.forEachIndexed { index, slot ->
        Surface(color = colors.selected.copy(alpha = 0.45f), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = colors.onVariant)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        if (state.frequency == com.timebox.android.data.RecurrenceFrequency.Weekly && state.weekdays.size > 1) {
                            RecurrenceMenu("Weekday", weekdays.getOrNull(slot.weekday ?: -1) ?: "Choose weekday",
                                state.weekdays.sorted().map { weekdays[it] to it }) { day -> updateSlot(index) { it.copy(weekday = day) } }
                        } else {
                            Text(if (state.frequency == com.timebox.android.data.RecurrenceFrequency.Weekly)
                                "Every ${weekdays.getOrNull(slot.weekday ?: state.weekdays.minOrNull() ?: -1) ?: "occurrence"}"
                                else "Each occurrence", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                        }
                    }
                    if (state.preplanningSlots.size > 1) IconButton({
                        onChange(state.copy(preplanningSlots = state.preplanningSlots.filterIndexed { i, _ -> i != index }))
                    }) { Icon(Icons.Outlined.Close, "Remove pre-planning slot ${index + 1}") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PreplanningTimeInput("Start", slot.start, "Planned Block start ${index + 1}", Modifier.weight(1f)) { value -> updateSlot(index) { it.copy(start = value) } }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp), tint = colors.onVariant.copy(alpha = 0.6f))
                    PreplanningTimeInput("End", slot.end, "Planned Block end ${index + 1}", Modifier.weight(1f)) { value -> updateSlot(index) { it.copy(end = value) } }
                }
            }
        }
    }
    Text("Schedules when this time is free.", Modifier.padding(horizontal = 4.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
    TextButton({ onChange(state.copy(preplanningSlots = state.preplanningSlots + RecurringPreplanningSlotDraft(weekday = state.weekdays.minOrNull()))) }) { Text("Add another time") }
}

@Composable
private fun PreplanningTimeInput(label: String, value: String, description: String, modifier: Modifier, onChange: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        BasicTextField(value, onChange,
            Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = description },
            textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium, color = TimeboxTheme.colors.on),
            singleLine = true)
    }
}
