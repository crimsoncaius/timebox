package com.timebox.android.ui.battleplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.RecurrencePreview
import com.timebox.android.data.RecurrenceWindow
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.SectionCard
import com.timebox.android.ui.components.SectionHeader
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.components.WheelDatePicker
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Issues 189 and 191: local-state hierarchy experiment. Debug route only. Does not save.

private val prototypeTypes = listOf(
    TaskType(1, "Planning", 3),
    TaskType(2, "Writing", 1),
    TaskType(3, "Practice", 4),
)

private val prototypePreview = RecurrencePreview(
    upcoming = listOf(
        RecurrenceWindow("w1", LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-21")),
        RecurrenceWindow("w2", LocalDate.parse("2026-09-28"), LocalDate.parse("2026-09-28")),
        RecurrenceWindow("w3", LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-05")),
    ),
    pastCycles = 2,
    pastTasks = 2,
)

private fun RecurringEditorUiState.hasOptionalDetails(): Boolean =
    description.isNotBlank() || checklistText.isNotBlank() || taskTypeId != null || urgency != null || importance != null

private fun RecurringEditorUiState.hasPreplanning(): Boolean = preplanningSlots.isNotEmpty()

private fun prototypeSample(flow: String, scenario: String): RecurringEditorUiState {
    val quota = scenario == "quota"
    val filled = flow != "create"
    val today = LocalDate.parse("2026-09-15")
    return RecurringEditorUiState(
        templateId = if (filled) 191 else null,
        title = when {
            !filled -> ""
            quota -> "Practice sessions"
            else -> "Weekly review"
        },
        description = when {
            !filled -> ""
            quota -> "Three focused practice sessions in the period."
            else -> "Look at last week's Day Review and pick one follow-up."
        },
        taskTypeId = if (filled) if (quota) 3 else 1 else null,
        urgency = if (filled && !quota) PriorityLevel.Medium else null,
        importance = if (filled) PriorityLevel.High else null,
        mode = if (quota) RecurrenceMode.Quota else RecurrenceMode.Scheduled,
        frequency = RecurrenceFrequency.Weekly,
        interval = "1",
        weekdays = if (quota) emptySet() else setOf(0),
        quotaCount = "3",
        startDate = if (filled) "2026-08-04" else today.toString(),
        checklistText = when {
            !filled -> ""
            quota -> "Warm up\nPlay the hard passage\nNote what to keep"
            else -> "Scan the week\nPick one follow-up\nPark the rest"
        },
        keepUnfinishedOverdue = false,
        preplanningSlots = if (!quota && filled) {
            listOf(RecurringPreplanningSlotDraft(weekday = 0, start = "09:00", end = "10:00"))
        } else emptyList(),
        taskTypes = prototypeTypes,
        preview = prototypePreview,
        dirty = false,
    )
}

@Composable
fun RecurringDetailsHierarchyPrototype(
    initialFlow: String = "details",
    initialLayout: String = "core",
    initialScenario: String = "scheduled",
) {
    val colors = TimeboxTheme.colors
    var flow by rememberSaveable {
        mutableStateOf(
            when (initialFlow) {
                "create" -> "create"
                "edit" -> "edit"
                else -> "details"
            },
        )
    }
    var layout by rememberSaveable {
        mutableStateOf(
            when (initialLayout) {
                "extras" -> "extras"
                "own" -> "own"
                else -> "core"
            },
        )
    }
    var scenario by rememberSaveable { mutableStateOf(if (initialScenario == "quota") "quota" else "scheduled") }
    var extrasOverride by rememberSaveable { mutableStateOf("default") }
    var preplanningOverride by rememberSaveable { mutableStateOf("default") }
    var savedNotice by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf(prototypeSample(flow, scenario)) }
    fun resetOverrides() {
        extrasOverride = "default"
        preplanningOverride = "default"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().background(colors.bg)) {
        Column(
            Modifier.fillMaxWidth().background(colors.raised).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "RECURRING DETAILS · ROUND 2",
                    fontSize = 10.sp,
                    color = colors.onVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton({
                    resetOverrides()
                    state = prototypeSample(flow, scenario)
                }) { Text("Reset") }
            }
            PrototypeChipRow(
                options = listOf("create" to "Create", "edit" to "Edit", "details" to "Details"),
                selected = flow,
            ) {
                flow = it
                resetOverrides()
                state = when {
                    it == "create" -> prototypeSample("create", scenario)
                    state.templateId != null -> state
                    else -> prototypeSample("edit", scenario)
                }
            }
            PrototypeChipRow(
                options = listOf(
                    "core" to "With rhythm",
                    "extras" to "With extras",
                    "own" to "Own toggle",
                ),
                selected = layout,
            ) {
                layout = it
                resetOverrides()
            }
            PrototypeChipRow(
                options = listOf("scheduled" to "Scheduled", "quota" to "Quota"),
                selected = scenario,
            ) {
                scenario = it
                resetOverrides()
                state = prototypeSample(flow, it)
            }
        }
        if (flow == "details") {
            RecurringDetailsHierarchyView(
                state = state,
                layout = layout,
                extrasOverride = extrasOverride,
                onExtrasOverride = { extrasOverride = it },
                preplanningOverride = preplanningOverride,
                onPreplanningOverride = { preplanningOverride = it },
                onEdit = {
                    extrasOverride = "default"
                    preplanningOverride = "default"
                    flow = "edit"
                },
                onStub = { savedNotice = true },
                modifier = Modifier.weight(1f),
            )
        } else {
            RecurringDetailsHierarchyForm(
                state = state,
                layout = layout,
                extrasOverride = extrasOverride,
                onExtrasOverride = { extrasOverride = it },
                preplanningOverride = preplanningOverride,
                onPreplanningOverride = { preplanningOverride = it },
                onState = { state = it },
                onSave = { savedNotice = true },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (savedNotice) {
        AlertDialog(
            onDismissRequest = { savedNotice = false },
            title = { Text("Prototype only") },
            text = { Text("This hierarchy experiment does not save a Recurring Task Series.") },
            confirmButton = { TextButton({ savedNotice = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun PrototypeChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            TimeboxChip(label, selected == value, { onSelect(value) })
        }
    }
}

@Composable
private fun RecurringDetailsHierarchyForm(
    state: RecurringEditorUiState,
    layout: String,
    extrasOverride: String,
    onExtrasOverride: (String) -> Unit,
    preplanningOverride: String,
    onPreplanningOverride: (String) -> Unit,
    onState: (RecurringEditorUiState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val editing = state.templateId != null
    val quota = state.mode == RecurrenceMode.Quota
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val start = runCatching { LocalDate.parse(state.startDate) }.getOrDefault(LocalDate.parse("2026-09-15"))
    val end = runCatching { LocalDate.parse(state.endDate) }.getOrNull()
    var dateTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val extrasFilled = state.hasOptionalDetails() || (layout == "extras" && state.hasPreplanning())
    val showExtras = when (extrasOverride) {
        "open" -> true
        "closed" -> false
        else -> extrasFilled
    }
    val showPreplanning = when {
        quota -> false
        layout == "core" -> true
        layout == "extras" -> showExtras
        else -> when (preplanningOverride) {
            "open" -> true
            "closed" -> false
            else -> state.hasPreplanning()
        }
    }
    fun edit(transform: RecurringEditorUiState.() -> RecurringEditorUiState) {
        onState(state.transform().copy(dirty = true))
    }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (editing) "Edit this Recurring Task Series." else "A little more regular.", style = TimeboxTheme.type.screenTitle)
            Text(
                if (editing) "Change the series and its rhythm. Optional details stay in the same place as when you added it."
                else "Set the series and its rhythm. Leave the rest for later.",
                style = TimeboxTheme.type.body,
                color = colors.onVariant,
            )
            OutlinedTextField(
                state.title,
                { edit { copy(title = it) } },
                Modifier.fillMaxWidth(),
                label = { Text("Task name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Text("How it repeats", style = TimeboxTheme.type.sectionTitle)
            CreationMode("Flexible quota", "A number of times in a period", quota, !editing) {
                edit { copy(mode = RecurrenceMode.Quota, weekdays = emptySet(), preplanningSlots = emptyList(), keepUnfinishedOverdue = false) }
            }
            CreationMode("On a schedule", "Repeat on particular dates", !quota, !editing) {
                edit { copy(mode = RecurrenceMode.Scheduled) }
            }
            if (editing) Text("Mode cannot be changed after creation.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceFrequency.entries.forEach { frequency ->
                    TimeboxChip(frequency.label, state.frequency == frequency, { edit { copy(frequency = frequency) } })
                }
            }
            if (quota) {
                CreationCount("Times per period", state.quotaCount.toIntOrNull() ?: 1, 100) { edit { copy(quotaCount = it) } }
            } else {
                CreationCount("Repeat every (periods)", state.interval.toIntOrNull() ?: 1, 365) { edit { copy(interval = it) } }
                if (state.frequency == RecurrenceFrequency.Weekly) WeekdayPicker(state.weekdays) { day ->
                    edit { copy(weekdays = if (day in weekdays) weekdays - day else weekdays + day) }
                }
                if (state.frequency == RecurrenceFrequency.Monthly) {
                    CreationCount("Day of month", state.monthDay.toIntOrNull() ?: 1, 31) { edit { copy(monthDay = it) } }
                }
            }
            Text("When it runs", style = TimeboxTheme.type.sectionTitle)
            CreationDateRow("Starts", start.format(formatter), true) { dateTarget = "start" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set an end date", Modifier.weight(1f), style = TimeboxTheme.type.body)
                androidx.compose.material3.Switch(
                    state.endMode == RecurrenceEndMode.EndDate,
                    {
                        if (it) dateTarget = "end" else edit { copy(endMode = RecurrenceEndMode.Never) }
                    },
                    modifier = Modifier.semantics { contentDescription = "Set an end date" },
                )
            }
            if (state.endMode == RecurrenceEndMode.EndDate) {
                CreationDateRow("Ends", end?.format(formatter) ?: "Choose date", true) { dateTarget = "end" }
            } else {
                Text("Keeps repeating until you end it.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            if (!quota) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep unfinished occurrences overdue", style = TimeboxTheme.type.body)
                        Text("Otherwise they leave current work after their scheduled date.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    }
                    androidx.compose.material3.Switch(
                        state.keepUnfinishedOverdue,
                        { edit { copy(keepUnfinishedOverdue = it) } },
                        modifier = Modifier.semantics { contentDescription = "Keep unfinished occurrences overdue" },
                    )
                }
            }
            if (layout == "own" && !quota) {
                val preplanningLabel = when {
                    showPreplanning -> "−  Hide pre-planning"
                    state.hasPreplanning() -> "+  Pre-plan Task Occurrences · already set"
                    else -> "+  Pre-plan Task Occurrences"
                }
                TextButton({ onPreplanningOverride(if (showPreplanning) "closed" else "open") }) { Text(preplanningLabel) }
            }
            if (showPreplanning && layout != "extras") {
                PrototypePreplanning(state, ::edit)
            }
            val extrasLabel = when {
                showExtras -> "−  Less detail"
                extrasFilled -> "+  Notes, subtasks & more · already set"
                else -> "+  Notes, subtasks & more"
            }
            TextButton({ onExtrasOverride(if (showExtras) "closed" else "open") }) { Text(extrasLabel) }
            if (showExtras) {
                if (layout == "extras" && !quota) PrototypePreplanning(state, ::edit)
                OptionalDetailsFields(state, ::edit)
            }
            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (quota) "YOUR QUOTA" else "YOUR SERIES", style = TimeboxTheme.type.kicker, color = colors.onPrimaryContainer)
                    Text(state.title.ifBlank { "Your recurring task" }, style = TimeboxTheme.type.sectionTitle, color = colors.onPrimaryContainer)
                    Text(
                        if (quota) "${state.quotaCount} ${if (state.quotaCount == "1") "time" else "times"} per week"
                        else "Every ${state.interval} week${if (state.interval == "1") "" else "s"}",
                        style = TimeboxTheme.type.body,
                        color = colors.onPrimaryContainer,
                    )
                }
            }
            Text(
                if (quota) "A Quota Tracker counts completed Session Tasks. You choose when to do them."
                else "A Recurring Task Series creates separate Task Occurrences, each with its own completion.",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
            )
            SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Text("Local preview", style = TimeboxTheme.type.sectionTitle)
                Text(
                    "Past cycles: ${state.preview?.pastCycles ?: 0} · Tasks if backfilled: ${state.preview?.pastTasks ?: 0}",
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                )
                state.preview?.upcoming?.forEach { window ->
                    Text(window.start.toString(), Modifier.padding(vertical = 3.dp), style = TimeboxTheme.type.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Surface(color = colors.bg, shadowElevation = 8.dp) {
            PrimaryButton(
                if (editing) "Save changes" else if (quota) "Create recurring quota" else "Create recurring series",
                onSave,
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                enabled = state.title.isNotBlank(),
            )
        }
    }
    dateTarget?.let { target ->
        val isEnd = target == "end"
        WheelDatePicker(
            title = if (isEnd) "End date" else "Start date",
            initialDate = if (isEnd) end ?: start.plusMonths(1) else start,
            minimumDate = if (isEnd) start else null,
            onDismiss = { dateTarget = null },
            onConfirm = {
                if (isEnd) edit { copy(endDate = it.toString(), endMode = RecurrenceEndMode.EndDate) }
                else edit { copy(startDate = it.toString()) }
                dateTarget = null
            },
        )
    }
}

@Composable
private fun RecurringDetailsHierarchyView(
    state: RecurringEditorUiState,
    layout: String,
    extrasOverride: String,
    onExtrasOverride: (String) -> Unit,
    preplanningOverride: String,
    onPreplanningOverride: (String) -> Unit,
    onEdit: () -> Unit,
    onStub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val quota = state.mode == RecurrenceMode.Quota
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val start = runCatching { LocalDate.parse(state.startDate) }.getOrDefault(LocalDate.parse("2026-09-15"))
    val extrasFilled = state.hasOptionalDetails() || (layout == "extras" && state.hasPreplanning())
    val showExtras = when (extrasOverride) {
        "open" -> true
        "closed" -> false
        else -> extrasFilled
    }
    val showPreplanning = when {
        quota -> false
        layout == "core" -> true
        layout == "extras" -> showExtras
        else -> when (preplanningOverride) {
            "open" -> true
            "closed" -> false
            else -> state.hasPreplanning()
        }
    }
    val period = when (state.frequency) {
        RecurrenceFrequency.Daily -> if (state.interval == "1") "day" else "days"
        RecurrenceFrequency.Weekly -> if (state.interval == "1") "week" else "weeks"
        RecurrenceFrequency.Monthly -> if (state.interval == "1") "month" else "months"
    }
    val cadence = if (quota) {
        "${state.quotaCount} ${if (state.quotaCount == "1") "time" else "times"} per ${
            when (state.frequency) {
                RecurrenceFrequency.Daily -> "day"
                RecurrenceFrequency.Weekly -> "week"
                RecurrenceFrequency.Monthly -> "month"
            }
        }"
    } else if (state.interval == "1") {
        "Every $period"
    } else {
        "Every ${state.interval} $period"
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("ACTIVE", style = TimeboxTheme.type.kicker, color = colors.onVariant)
        Text(state.title.ifBlank { "Your recurring task" }, style = TimeboxTheme.type.screenTitle)
        Text("$cadence · from ${start.format(formatter)}", style = TimeboxTheme.type.body, color = colors.onVariant)
        SectionCard {
            SectionHeader(
                if (quota) "This period's Session Tasks" else "Current Task Occurrence",
                if (quota) "Independently completable work that counts toward this Quota Tracker."
                else "This Recurring Task Series's current work.",
            )
            if (quota) {
                Text("0 of ${state.quotaCount} Session Tasks completed", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                listOf("Practice session 1", "Practice session 2", "Practice session 3").forEach { title ->
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onStub).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, Modifier.weight(1f), color = colors.on)
                        Text("Open", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onStub).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Weekly review", color = colors.on)
                        Text("15 Sep 2026", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    }
                    Text("Open", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
            }
        }
        SectionCard {
            SectionHeader("Upcoming")
            state.preview?.upcoming.orEmpty().forEach { window ->
                Text(window.start.format(formatter), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), style = TimeboxTheme.type.bodySmall)
            }
        }
        if (layout == "own" && !quota) {
            val preplanningLabel = when {
                showPreplanning -> "−  Hide pre-planning"
                state.hasPreplanning() -> "+  Pre-plan Task Occurrences · already set"
                else -> "+  Pre-plan Task Occurrences"
            }
            TextButton({ onPreplanningOverride(if (showPreplanning) "closed" else "open") }) { Text(preplanningLabel) }
        }
        if (showPreplanning && layout != "extras") {
            PrototypePreplanningSummary(state)
        }
        val extrasLabel = when {
            showExtras -> "−  Less detail"
            extrasFilled -> "+  Notes, subtasks & more · already set"
            else -> "+  Notes, subtasks & more"
        }
        TextButton({ onExtrasOverride(if (showExtras) "closed" else "open") }) { Text(extrasLabel) }
        if (showExtras) {
            if (layout == "extras" && !quota) PrototypePreplanningSummary(state)
            PrototypeExtrasSummary(state)
        }
        PrimaryButton("Edit series", onEdit, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onStub) { Text("Pause") }
            TextButton(onStub) { Text("End") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PrototypePreplanningSummary(state: RecurringEditorUiState) {
    val colors = TimeboxTheme.colors
    val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    SectionCard(Modifier.semantics { contentDescription = "Recurring Pre-planning Schedule" }) {
        SectionHeader("Recurring Pre-planning Schedule")
        if (state.preplanningSlots.isEmpty()) {
            Text("No Planned Block slots", Modifier.padding(16.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        } else {
            state.preplanningSlots.forEach { slot ->
                val weekday = slot.weekday?.let { names.getOrNull(it) }
                Text(
                    listOfNotNull(weekday, "${slot.start}–${slot.end}").joinToString(" · "),
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = TimeboxTheme.type.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PrototypeExtrasSummary(state: RecurringEditorUiState) {
    val colors = TimeboxTheme.colors
    val type = state.taskTypes.firstOrNull { it.id == state.taskTypeId }?.name ?: "No task type"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.description.isNotBlank()) Text(state.description, style = TimeboxTheme.type.body, color = colors.onVariant)
        state.checklistText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { item ->
            Text("• $item", style = TimeboxTheme.type.body, color = colors.on)
        }
        Text(
            listOfNotNull(
                type.takeIf { state.taskTypeId != null },
                state.urgency?.label?.let { "$it urgency" },
                state.importance?.label?.let { "$it importance" },
            ).joinToString(" · ").ifBlank { "No Task Type or priorities" },
            style = TimeboxTheme.type.bodySmall,
            color = colors.onVariant,
        )
    }
}

@Composable
private fun PrototypePreplanning(
    state: RecurringEditorUiState,
    edit: (RecurringEditorUiState.() -> RecurringEditorUiState) -> Unit,
) {
    RecurringPreplanningScheduleEditor(
        state,
        { enabled ->
            edit {
                copy(preplanningSlots = if (enabled) preplanningSlots.ifEmpty { listOf(RecurringPreplanningSlotDraft(weekday = weekdays.minOrNull())) } else emptyList())
            }
        },
        { edit { copy(preplanningSlots = preplanningSlots + RecurringPreplanningSlotDraft(weekday = weekdays.minOrNull())) } },
        { index -> edit { copy(preplanningSlots = preplanningSlots.filterIndexed { i, _ -> i != index }) } },
        { index, value -> edit { copy(preplanningSlots = preplanningSlots.mapIndexed { i, slot -> if (i == index) slot.copy(start = value) else slot }) } },
        { index, value -> edit { copy(preplanningSlots = preplanningSlots.mapIndexed { i, slot -> if (i == index) slot.copy(end = value) else slot }) } },
        { index, weekday -> edit { copy(preplanningSlots = preplanningSlots.mapIndexed { i, slot -> if (i == index) slot.copy(weekday = weekday) else slot }) } },
    )
}

@Composable
private fun OptionalDetailsFields(
    state: RecurringEditorUiState,
    edit: (RecurringEditorUiState.() -> RecurringEditorUiState) -> Unit,
) {
    OutlinedTextField(state.description, { edit { copy(description = it) } }, Modifier.fillMaxWidth(), label = { Text("Notes · optional") }, minLines = 2)
    OutlinedTextField(state.checklistText, { edit { copy(checklistText = it) } }, Modifier.fillMaxWidth(), label = { Text("Subtasks · one per line") }, minLines = 2)
    RecurrenceMenu(
        "Task type",
        state.taskTypes.firstOrNull { it.id == state.taskTypeId }?.name ?: "No task type",
        listOf("No task type" to null) + state.taskTypes.map { it.name to it.id },
    ) { edit { copy(taskTypeId = it) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            RecurrenceMenu(
                "Urgency",
                state.urgency?.label ?: "No urgency",
                listOf("No urgency" to null) + PriorityLevel.entries.map { it.label to it },
            ) { edit { copy(urgency = it) } }
        }
        Box(Modifier.weight(1f)) {
            RecurrenceMenu(
                "Importance",
                state.importance?.label ?: "No importance",
                listOf("No importance" to null) + PriorityLevel.entries.map { it.label to it },
            ) { edit { copy(importance = it) } }
        }
    }
}
