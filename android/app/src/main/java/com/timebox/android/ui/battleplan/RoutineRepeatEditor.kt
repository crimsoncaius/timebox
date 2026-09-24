package com.timebox.android.ui.battleplan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.components.WheelDatePicker
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun routineRuleSample(quota: Boolean) = RecurringEditorUiState(
    mode = if (quota) RecurrenceMode.Quota else RecurrenceMode.Scheduled,
    frequency = RecurrenceFrequency.Weekly, weekdays = setOf(0), quotaCount = "3",
    startDate = "2026-09-21", monthDay = "21", cycleLimit = "12",
)

internal fun routineRuleSummary(state: RecurringEditorUiState): String {
    val unit = when (state.frequency) {
        RecurrenceFrequency.Daily -> "day"
        RecurrenceFrequency.Weekly -> "week"
        RecurrenceFrequency.Monthly -> "month"
    }
    if (state.mode == RecurrenceMode.Quota) return "${state.quotaCount} ${if (state.quotaCount == "1") "time" else "times"} per $unit"
    val interval = state.interval.toIntOrNull() ?: 1
    val rhythm = if (interval == 1) "Every $unit" else "Every $interval ${unit}s"
    return rhythm + when (state.frequency) {
        RecurrenceFrequency.Weekly -> " · " + state.weekdays.sorted().joinToString(", ") { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[it] }
        RecurrenceFrequency.Monthly -> " · day ${state.monthDay}"
        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineRepeatEditor(saved: RecurringEditorUiState, creating: Boolean, onDismiss: () -> Unit, onSave: (RecurringEditorUiState) -> Unit) {
    var state by rememberSaveable(stateSaver = routineDraftSaver(saved)) { mutableStateOf(saved) }
    var dateTarget by remember { mutableStateOf<String?>(null) }
    val colors = TimeboxTheme.colors
    val quota = state.mode == RecurrenceMode.Quota
    val unit = when (state.frequency) { RecurrenceFrequency.Daily -> "day"; RecurrenceFrequency.Weekly -> "week"; RecurrenceFrequency.Monthly -> "month" }
    var discard by remember { mutableStateOf(false) }
    fun dismiss() { if (state != saved) discard = true else onDismiss() }
    val validation = validateRecurrenceDraft(state, requireTitle = false)
    val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
    ModalBottomSheet(onDismissRequest = ::dismiss, properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { value -> if (value == SheetValue.Hidden && state != saved) { discard = true; false } else true }), containerColor = colors.sheet.copy(alpha = 1f)) {
        TaskSheetBackHandler(false, ::dismiss)
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Repeat", Modifier.weight(1f), style = TimeboxTheme.type.sectionTitle)
                TextButton(::dismiss) { Text("Cancel") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (creating) {
                    CreationMode("On a schedule", "Create tasks on particular dates", !quota, true) { state = state.copy(mode = RecurrenceMode.Scheduled) }
                    CreationMode("Flexible quota", "Complete a number of sessions each period", quota, true) { state = state.copy(mode = RecurrenceMode.Quota, interval = "1", keepUnfinishedOverdue = false, queuePreplanning = false, preplanningSlots = emptyList()) }
                } else {
                    Text(if (quota) "Flexible quota" else "On a schedule", style = TimeboxTheme.type.label)
                    Text("Chosen when this routine was created.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
                HorizontalDivider(color = colors.hairline)
                Text(if (quota) "Count sessions per" else "Frequency", style = TimeboxTheme.type.label)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurrenceFrequency.entries.forEach { frequency -> TimeboxChip(frequency.name, state.frequency == frequency, { state = state.copy(frequency = frequency) }) }
                }
                if (quota) {
                    CreationCount("Times per $unit", state.quotaCount.toIntOrNull() ?: 1, 100) { state = state.copy(quotaCount = it) }
                    Text("Choose when to do each session within the $unit.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                } else {
                    CreationCount("Repeat every (${unit}s)", state.interval.toIntOrNull() ?: 1, 365) { state = state.copy(interval = it) }
                    when (state.frequency) {
                        RecurrenceFrequency.Weekly -> WeekdayPicker(state.weekdays) { day -> state = state.copy(weekdays = if (day in state.weekdays) state.weekdays - day else state.weekdays + day) }
                        RecurrenceFrequency.Monthly -> {
                            CreationCount("Day of month", state.monthDay.toIntOrNull() ?: 1, 31) { state = state.copy(monthDay = it) }
                            Text("For shorter months, use the last day available.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                        }
                        else -> Unit
                    }
                }
                HorizontalDivider(color = colors.hairline)
                CreationDateRow("Starts", LocalDate.parse(state.startDate).format(dateFormat), true) { dateTarget = "start" }
                Text("Ends", style = TimeboxTheme.type.label)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RecurrenceEndMode.Never to "Never", RecurrenceEndMode.EndDate to "On date", RecurrenceEndMode.CycleLimit to "After cycles").forEach { (mode, label) ->
                        TimeboxChip(label, state.endMode == mode, { state = state.copy(endMode = mode, cycleLimit = state.cycleLimit.ifEmpty { "12" }, endDate = state.endDate.ifEmpty { LocalDate.parse(state.startDate).plusMonths(3).toString() }) })
                    }
                }
                if (state.endMode == RecurrenceEndMode.EndDate) CreationDateRow("End date", state.endDate, true) { dateTarget = "end" }
                if (state.endMode == RecurrenceEndMode.CycleLimit) {
                    CreationCount("Cycles", state.cycleLimit.toIntOrNull() ?: 12, 10000) { state = state.copy(cycleLimit = it) }
                    Text("A cycle is one recurrence period, not one completed task.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
                if (!quota) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep unfinished occurrences overdue", style = TimeboxTheme.type.body)
                        Text("Otherwise they leave current work after their recurrence period.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    }
                    Switch(state.keepUnfinishedOverdue, { state = state.copy(keepUnfinishedOverdue = it) })
                }
                if (!quota && state.preplanningSlots.isNotEmpty()) {
                    Text("Pre-planning", style = TimeboxTheme.type.label)
                    Text("Check the planned slots when changing the schedule.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    RoutinePreplanningFields(state) { state = it }
                }
                HorizontalDivider(color = colors.hairline)
                Text(routineRuleSummary(state), style = TimeboxTheme.type.label)
                Text("From ${LocalDate.parse(state.startDate).format(dateFormat)}" + when (state.endMode) {
                    RecurrenceEndMode.Never -> " · no end date"
                    RecurrenceEndMode.EndDate -> " · through ${state.endDate}"
                    RecurrenceEndMode.CycleLimit -> " · ${state.cycleLimit} cycles"
                }, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                if (validation != null) Text(validation, color = colors.error)
                Spacer(Modifier.height(12.dp))
            }
            Button({ onSave(state) }, enabled = validation == null, modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text(if (creating) "Set repeat" else "Save repeat") }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard repeat changes?") }, confirmButton = { TextButton(onDismiss) { Text("Discard") } }, dismissButton = { TextButton({ discard = false }) { Text("Keep editing") } })
    dateTarget?.let { target ->
        val start = LocalDate.parse(state.startDate)
        WheelDatePicker(title = if (target == "start") "Start date" else "End date", initialDate = if (target == "start") start else LocalDate.parse(state.endDate), minimumDate = if (target == "end") start else null,
            onDismiss = { dateTarget = null }, onConfirm = { date -> state = if (target == "start") state.copy(startDate = date.toString()) else state.copy(endDate = date.toString()); dateTarget = null })
    }
}
