package com.timebox.android.ui.battleplan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.data.*
import com.timebox.android.ui.components.*
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun RecurringCreationContent(
    state: RecurringEditorUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onTaskType: (Int?) -> Unit,
    onCreateTaskType: (String) -> Unit = {},
    onUrgency: (PriorityLevel?) -> Unit,
    onImportance: (PriorityLevel?) -> Unit,
    onMode: (RecurrenceMode) -> Unit,
    onFrequency: (RecurrenceFrequency) -> Unit,
    onInterval: (String) -> Unit,
    onToggleWeekday: (Int) -> Unit,
    onMonthDay: (String) -> Unit,
    onQuotaCount: (String) -> Unit,
    onStartDate: (String) -> Unit,
    onEndMode: (RecurrenceEndMode) -> Unit,
    onEndDate: (String) -> Unit,
    onCycleLimit: (String) -> Unit,
    onChecklist: (String) -> Unit,
    onKeepUnfinishedOverdue: (Boolean) -> Unit = {},
    onPreplanningEnabled: (Boolean) -> Unit = {},
    onAddPreplanningSlot: () -> Unit = {},
    onRemovePreplanningSlot: (Int) -> Unit = {},
    onPreplanningStart: (Int, String) -> Unit = { _, _ -> },
    onPreplanningEnd: (Int, String) -> Unit = { _, _ -> },
    onPreplanningWeekday: (Int, Int) -> Unit = { _, _ -> },
    onRefreshPreview: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val editing = state.templateId != null
    var extrasOverride by rememberSaveable(state.templateId) { mutableStateOf("default") }
    var preplanningOverride by rememberSaveable(state.templateId) { mutableStateOf("default") }
    var dateTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val start = runCatching { LocalDate.parse(state.startDate) }.getOrDefault(LocalDate.now())
    val end = runCatching { LocalDate.parse(state.endDate) }.getOrNull()
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val quota = state.mode == RecurrenceMode.Quota
    val period = when (state.frequency) {
        RecurrenceFrequency.Daily -> "day"
        RecurrenceFrequency.Weekly -> "week"
        RecurrenceFrequency.Monthly -> "month"
    }
    val extrasFilled = state.description.isNotBlank() || state.taskTypeId != null ||
        state.urgency != null || state.importance != null || state.endMode == RecurrenceEndMode.CycleLimit
    val showExtras = when (extrasOverride) {
        "open" -> true
        "closed" -> false
        else -> extrasFilled
    }
    val showPreplanning = when {
        quota -> false
        else -> when (preplanningOverride) {
            "open" -> true
            "closed" -> false
            else -> state.preplanningSlots.isNotEmpty()
        }
    }
    val validation = validateRecurrenceDraft(state, requireTitle = false)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack, enabled = !state.saving) { Text("‹ Recurring") }
            Spacer(Modifier.weight(1f))
            Text(if (editing) "EDIT RECURRING" else "NEW RECURRING", style = TimeboxTheme.type.kicker, color = colors.onVariant)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (editing) "Edit this Recurring Task Series." else "A little more regular.", style = TimeboxTheme.type.screenTitle)
            Text(
                if (editing) "Change the series and its rhythm. Notes and the rest stay behind Less detail."
                else "Set the series and its rhythm. Leave notes and the rest for later.",
                style = TimeboxTheme.type.body,
                color = colors.onVariant,
            )
            OutlinedTextField(state.title, onTitle, Modifier.fillMaxWidth(), label = { Text("Task name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            Text("How it repeats", style = TimeboxTheme.type.sectionTitle)
            CreationMode("Flexible quota", "A number of times in a period", quota, !editing && !state.saving) { onMode(RecurrenceMode.Quota) }
            CreationMode("On a schedule", "Repeat on particular dates", !quota, !editing && !state.saving) { onMode(RecurrenceMode.Scheduled) }
            if (editing) Text("Mode cannot be changed after creation.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceFrequency.entries.forEach { frequency -> TimeboxChip(frequency.label, state.frequency == frequency, { onFrequency(frequency) }) }
            }
            if (quota) {
                CreationCount("Times per period", state.quotaCount.toIntOrNull() ?: 1, 100, onQuotaCount)
            } else {
                CreationCount("Repeat every (periods)", state.interval.toIntOrNull() ?: 1, 365, onInterval)
                if (state.frequency == RecurrenceFrequency.Weekly) WeekdayPicker(state.weekdays, onToggleWeekday)
                if (state.frequency == RecurrenceFrequency.Monthly) CreationCount("Day of month", state.monthDay.toIntOrNull() ?: 1, 31, onMonthDay)
            }
            Text("When it runs", style = TimeboxTheme.type.sectionTitle)
            CreationDateRow("Starts", start.format(formatter), !state.saving) { dateTarget = "start" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set an end date", Modifier.weight(1f), style = TimeboxTheme.type.body)
                Switch(state.endMode == RecurrenceEndMode.EndDate, {
                    if (it) dateTarget = "end" else onEndMode(RecurrenceEndMode.Never)
                }, enabled = !state.saving, modifier = Modifier.semantics { contentDescription = "Set an end date" })
            }
            if (state.endMode == RecurrenceEndMode.EndDate) CreationDateRow("Ends", end?.format(formatter) ?: "Choose date", !state.saving) { dateTarget = "end" }
            else if (state.endMode == RecurrenceEndMode.CycleLimit) Text("Ends after ${state.cycleLimit} cycles.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            else Text("Keeps repeating until you end it.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            if (!quota) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep unfinished occurrences overdue", style = TimeboxTheme.type.body)
                    Text("Otherwise they leave current work after their scheduled date.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
                Switch(state.keepUnfinishedOverdue, onKeepUnfinishedOverdue, enabled = !state.saving, modifier = Modifier.semantics { contentDescription = "Keep unfinished occurrences overdue" })
            }
            RecurringSubtaskEditor(state.checklistText, !state.saving, state.saving, onChecklist)
            if (!quota) {
                val preplanningLabel = when {
                    showPreplanning -> "−  Hide pre-planning"
                    state.preplanningSlots.isNotEmpty() -> "+  Pre-plan Task Occurrences · already set"
                    else -> "+  Pre-plan Task Occurrences"
                }
                TextButton({ preplanningOverride = if (showPreplanning) "closed" else "open" }, contentPadding = PaddingValues(0.dp)) { Text(preplanningLabel) }
            }
            if (showPreplanning) {
                RecurringPreplanningScheduleEditor(
                    state,
                    onPreplanningEnabled,
                    onAddPreplanningSlot,
                    onRemovePreplanningSlot,
                    onPreplanningStart,
                    onPreplanningEnd,
                    onPreplanningWeekday,
                )
            }
            val extrasLabel = when {
                showExtras -> "−  Less detail"
                extrasFilled -> "+  Notes & more · already set"
                else -> "+  Notes & more"
            }
            TextButton({ extrasOverride = if (showExtras) "closed" else "open" }, contentPadding = PaddingValues(0.dp)) { Text(extrasLabel) }
            if (showExtras) {
                OutlinedTextField(state.description, onDescription, Modifier.fillMaxWidth(), label = { Text("Notes · optional") }, minLines = 2)
                OutlinedTextField(state.checklistText, onChecklist, Modifier.fillMaxWidth(), label = { Text("Subtasks · one per line") }, minLines = 2)
                var typeQuery by rememberSaveable { mutableStateOf("") }
                Text("Task type", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                com.timebox.android.ui.day.TaskTypePicker(
                    recommendationName = state.title, recommendationEnabled = !state.saving,
                    taskTypes = state.taskTypes,
                    query = typeQuery,
                    onQueryChange = { typeQuery = it },
                    selectedTypeId = state.taskTypeId.takeUnless { id -> state.taskTypes.find { it.id == id }?.name == "unspecified" },
                    onChoose = { onTaskType(it.id); typeQuery = it.name },
                    onCreate = onCreateTaskType,
                    allowUnset = true,
                    onUnset = { onTaskType(null); typeQuery = "" },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { RecurrenceMenu("Urgency", state.urgency?.label ?: "No urgency", listOf("No urgency" to null) + PriorityLevel.entries.map { it.label to it }, onUrgency) }
                    Box(Modifier.weight(1f)) { RecurrenceMenu("Importance", state.importance?.label ?: "No importance", listOf("No importance" to null) + PriorityLevel.entries.map { it.label to it }, onImportance) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("End after a number of cycles", Modifier.weight(1f), style = TimeboxTheme.type.body)
                    Switch(state.endMode == RecurrenceEndMode.CycleLimit, {
                        if (it) onEndMode(RecurrenceEndMode.CycleLimit) else if (state.endMode == RecurrenceEndMode.CycleLimit) onEndMode(RecurrenceEndMode.Never)
                    }, enabled = !state.saving, modifier = Modifier.semantics { contentDescription = "End after a number of cycles" })
                }
                if (state.endMode == RecurrenceEndMode.CycleLimit) OutlinedTextField(state.cycleLimit, onCycleLimit, Modifier.fillMaxWidth(), label = { Text("Number of cycles") }, singleLine = true)
            }
            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (quota) "YOUR QUOTA" else "YOUR SERIES", style = TimeboxTheme.type.kicker, color = colors.onPrimaryContainer)
                    Text(state.title.ifBlank { "Your recurring task" }, style = TimeboxTheme.type.sectionTitle, color = colors.onPrimaryContainer)
                    Text(if (quota) "${state.quotaCount} ${if (state.quotaCount == "1") "time" else "times"} per $period" else "Every ${state.interval} $period${if (state.interval == "1") "" else "s"}", style = TimeboxTheme.type.body, color = colors.onPrimaryContainer)
                    Text("From ${start.format(formatter)}" + when (state.endMode) {
                        RecurrenceEndMode.Never -> " · no end date"
                        RecurrenceEndMode.EndDate -> " through ${end?.format(formatter) ?: "…"}"
                        RecurrenceEndMode.CycleLimit -> " · ${state.cycleLimit} cycles"
                    }, style = TimeboxTheme.type.bodySmall, color = colors.onPrimaryContainer)
                }
            }
            Text(if (quota) "A Quota Tracker counts completed Session Tasks. You choose when to do them." else "A Recurring Task Series creates separate Task Occurrences, each with its own completion.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            PreviewCard(state, onRefreshPreview)
            if (validation != null) Text(validation, color = colors.error, style = TimeboxTheme.type.bodySmall)
            Spacer(Modifier.height(12.dp))
        }
        Surface(color = colors.bg, shadowElevation = 8.dp) {
            PrimaryButton(
                when {
                    state.saving && editing -> "Saving…"
                    state.saving -> "Creating…"
                    editing -> "Save changes"
                    quota -> "Create recurring quota"
                    else -> "Create recurring series"
                },
                onSave,
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                enabled = !state.saving && state.title.isNotBlank() && validation == null,
            )
        }
    }
    dateTarget?.let { target ->
        val isEnd = target == "end"
        WheelDatePicker(
            title = if (isEnd) "End date" else "Start date",
            initialDate = if (isEnd) end ?: start.plusMonths(1).coerceAtMost(LocalDate.of(9999, 12, 31)) else start,
            minimumDate = if (isEnd) start else null,
            onDismiss = { dateTarget = null },
            onConfirm = {
                if (isEnd) { onEndDate(it.toString()); onEndMode(RecurrenceEndMode.EndDate) } else onStartDate(it.toString())
                dateTarget = null
            },
        )
    }
}

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

@Composable
internal fun RecurringSubtaskEditor(text: String, enabled: Boolean, saving: Boolean, onChange: (String) -> Unit) {
    val items = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TaskSubtasks(
            subtasks = emptyList(),
            enabled = enabled,
            saving = saving,
            error = null,
            onToggle = {},
            onTrash = {},
            onAdd = { title -> onChange((items + title).joinToString("\n")) },
            draftNames = items,
            onRemoveDraft = { index -> onChange(items.filterIndexed { i, _ -> i != index }.joinToString("\n")) },
        )
    }
}
