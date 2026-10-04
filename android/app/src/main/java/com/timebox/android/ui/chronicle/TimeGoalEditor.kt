package com.timebox.android.ui.chronicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.timebox.android.data.TimeGoal
import com.timebox.android.data.remote.TimeGoalWriteDto
import com.timebox.android.ui.battleplan.CreationCount
import com.timebox.android.ui.battleplan.CreationDateRow
import com.timebox.android.ui.battleplan.TaskSheetBackHandler
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.components.WheelDatePicker
import com.timebox.android.ui.day.TaskTypePicker
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate

/** Only first-period preview is local; the server owns all assessments and effective targets. */
internal fun firstGoalPeriodEnd(start: LocalDate, unit: String, interval: Int): LocalDate = when (unit) {
    "month" -> start.withDayOfMonth(1).plusMonths(interval.toLong()).minusDays(1)
    "week" -> start.minusDays(start.dayOfWeek.value - 1L).plusWeeks(interval.toLong()).minusDays(1)
    else -> start.plusDays(interval - 1L)
}

private fun defaultGoalStart(today: LocalDate, unit: String) = when (unit) {
    "month" -> today.withDayOfMonth(1)
    "week" -> today.minusDays(today.dayOfWeek.value - 1L)
    else -> today
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeGoalEditor(original: TimeGoal?, today: LocalDate, timezone: String,
                            state: TimeGoalsUiState, viewModel: TimeGoalsViewModel, onDismiss: () -> Unit) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var typeId by rememberSaveable { mutableStateOf(original?.taskTypeId) }
    var path by rememberSaveable { mutableStateOf(original?.taskType ?: "") }
    val initialTarget = original?.nextTargetMinutes ?: original?.targetMinutes ?: 240
    var hours by rememberSaveable { mutableStateOf((initialTarget / 60).toString()) }
    var minutes by rememberSaveable { mutableStateOf((initialTarget % 60).toString()) }
    var unit by rememberSaveable { mutableStateOf(original?.unit ?: "week") }
    var every by rememberSaveable { mutableIntStateOf(original?.interval ?: 1) }
    var startText by rememberSaveable { mutableStateOf((original?.startDate ?: defaultGoalStart(today, unit)).toString()) }
    var datePicked by rememberSaveable { mutableStateOf(false) }
    var chooseType by rememberSaveable { mutableStateOf(false) }
    var chooseDate by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var entryField by rememberSaveable { mutableStateOf<String?>(null) }
    var showPeriod by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { viewModel.loadGoalTypes() }
    val parsedHours = hours.toIntOrNull()
    val parsedMinutes = minutes.toIntOrNull()
    val target = (parsedHours ?: 0) * 60 + (parsedMinutes ?: 0)
    val replacing = original != null && (typeId != original.taskTypeId || unit != original.unit || every != original.interval)
    val start = if (replacing) today else LocalDate.parse(startText)
    val end = runCatching { firstGoalPeriodEnd(start, unit, every) }.getOrNull()
    val valid = typeId != null && parsedHours != null && parsedMinutes != null && parsedMinutes in 0..59 &&
        target in 1..525600 && end != null && end.year < 9999
    fun closeField() { if (!state.goalSaving) { focus.clearFocus(); entryField = null } }
    val typePicker: @Composable () -> Unit = {
        TaskTypePicker(taskTypes = state.taskTypes, query = query, onQueryChange = { query = it }, selectedTypeId = typeId,
            onChoose = { if (!state.goalSaving) {
                typeId = it.id; path = it.name; touched = true; chooseType = false; closeField()
            } },
            onCreate = { name -> viewModel.createGoalType(name) {
                typeId = it.id; path = it.name; touched = true; chooseType = false; closeField()
            } }, recommendationEnabled = false)
    }
    val durationFields: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(4); touched = true }, Modifier.weight(1f).testTag("goal-hours"),
                enabled = !state.goalSaving, label = { Text("Hours") }, singleLine = true,
                textStyle = if (original == null) type.display else LocalTextStyle.current,
                shape = TimeboxShapes.field, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2); touched = true }, Modifier.weight(1f).testTag("goal-minutes"),
                enabled = !state.goalSaving, label = { Text("Minutes") }, singleLine = true,
                textStyle = if (original == null) type.display else LocalTextStyle.current,
                shape = TimeboxShapes.field, isError = parsedMinutes == null || parsedMinutes !in 0..59,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        if (original == null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 120, 240).forEach { value ->
                FilterChip(target == value, { hours = (value / 60).toString(); minutes = (value % 60).toString(); touched = true; focus.clearFocus() },
                    enabled = !state.goalSaving, label = { Text(goalDuration(value), style = type.label) }, shape = TimeboxShapes.chip)
            }
        }
        if (parsedHours == null || target !in 1..525600 || parsedMinutes == null || parsedMinutes !in 0..59)
            Text("Enter 1 minute–8,760 hours; minutes must be 0–59.", style = type.bodySmall, color = colors.error)
    }
    val periodFields: @Composable () -> Unit = {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("day", "week", "month").forEach { value ->
                TimeboxChip(value.replaceFirstChar { it.uppercase() }, unit == value, {
                    if (!state.goalSaving) {
                        unit = value; touched = true
                        if (original == null && !datePicked) startText = defaultGoalStart(today, value).toString()
                    }
                })
            }
        }
        CreationCount("Repeat every (${unit}s)", every, 100) { if (!state.goalSaving) { every = it.toInt(); touched = true } }
    }
    fun dismiss() { if (!state.goalSaving) { if (touched) discard = true else onDismiss() } }
    ModalBottomSheet(onDismissRequest = ::dismiss, properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
            if (it == SheetValue.Hidden && (touched || state.goalSaving)) { if (!state.goalSaving) discard = true; false } else true
        }), containerColor = colors.sheet.copy(alpha = 1f), shape = TimeboxShapes.sheet) {
        TaskSheetBackHandler(state.goalSaving) { if (entryField != null) closeField() else if (chooseType) chooseType = false else dismiss() }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (chooseType) "Task Type" else if (original == null) "New Time Goal" else "Edit Time Goal",
                    Modifier.weight(1f), style = type.sectionTitle, color = colors.on)
                TextButton({ if (chooseType) chooseType = false else dismiss() }, enabled = !state.goalSaving) {
                    Text(if (chooseType) "Back" else "Cancel")
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (chooseType) {
                    typePicker()
                } else if (original == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("I want to spend", style = type.body, color = colors.onVariant)
                    GoalSentenceValue(if (parsedHours != null && parsedMinutes in 0..59 && target in 1..525600) goalDuration(target) else "Set duration",
                        "goal-duration", "Edit duration", !state.goalSaving) { entryField = "duration" }
                    Text("on", style = type.body, color = colors.onVariant)
                    GoalSentenceValue(path.ifEmpty { "Choose a Task Type" }.replace("/", " / "),
                        "goal-task-type", "Edit Task Type", !state.goalSaving) { query = ""; entryField = "type" }
                    GoalSentenceValue(goalCadence(unit, every).lowercase(), "goal-period", "Edit period", !state.goalSaving) { entryField = "period" }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ chooseDate = true }, enabled = !state.goalSaving, modifier = Modifier.weight(1f)) {
                            Text("Starts ${start.format(goalDateFormat)}", style = type.body)
                        }
                        TextButton({ showPeriod = !showPeriod }) {
                            Text("First period", style = type.bodySmall)
                            Icon(if (showPeriod) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (showPeriod) "Hide details" else "Show details")
                        }
                    }
                    Text(end?.let { "${goalDuration(target.coerceAtLeast(0))} by ${it.format(goalDateFormat)}" } ?: "Choose an earlier date",
                        style = type.body, color = colors.onVariant)
                    if ((unit == "week" && start.dayOfWeek.value != 1) || (unit == "month" && start.dayOfMonth != 1))
                        Text("This first period is shorter. Its full target still applies.", style = type.bodySmall, color = colors.onVariant)
                    if (showPeriod) {
                        Text(end?.let { goalRange(start, it) } ?: "Choose an earlier date", style = type.label)
                        Text("Actual Blocks in this Task Type and its descendants count, including existing recorded time. Extra time does not carry forward.",
                            style = type.bodySmall, color = colors.onVariant)
                        Text("Reporting time zone · $timezone", style = type.bodySmall, color = colors.onVariant)
                    }
                } else {
                    CreationDateRow("Task Type", path.ifEmpty { "Choose a Task Type" }, !state.goalSaving) { query = ""; chooseType = true }
                    Text("Includes time in all descendant Task Types.", style = type.bodySmall, color = colors.onVariant)
                    Text("Target duration", style = type.label, color = colors.on)
                    durationFields()
                    Text("Repeat", style = type.label, color = colors.on)
                    periodFields()
                    HorizontalDivider(color = colors.hairline)
                    when {
                        replacing -> {
                            Text("Replace from ${today.format(goalDateFormat)}", style = type.label, color = colors.on)
                            Text("The existing goal is archived today; its final period is excused if its target is unmet. A replacement starts today with its full target for ${end?.let { goalRange(start, it) }}. Its targets and results stay in the archive.",
                                style = type.bodySmall, color = colors.onVariant)
                        }
                        else -> {
                            Text("Takes effect ${original.nextTargetDate.format(goalDateFormat)}", style = type.label, color = colors.on)
                            Text("This period keeps its ${goalDuration(original.targetMinutes)} target. Earlier targets stay unchanged.", style = type.bodySmall, color = colors.onVariant)
                        }
                    }
                    Text("Reporting time zone · $timezone", style = type.bodySmall, color = colors.onVariant)
                }
                state.goalError?.let { Text(it, color = colors.error, style = type.bodySmall) }
                if (state.offline) {
                    Text("Connect to save changes. Your draft is kept here.", style = type.bodySmall, color = colors.onVariant)
                    TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Retry connection") }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (!chooseType) Button({
                viewModel.saveGoal(original?.id, replacing,
                    TimeGoalWriteDto(typeId!!, unit, every, target, if (original == null) start.toString() else null)) {
                    touched = false; onDismiss()
                }
            }, enabled = valid && !state.goalSaving && !state.offline && (original == null || touched),
                modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
                Text(if (state.goalSaving) "Saving…" else if (original == null) "Create Time Goal" else if (replacing) "Archive & replace goal" else "Save target")
            }
        }
    }
    if (entryField != null) ModalBottomSheet(onDismissRequest = ::closeField,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !state.goalSaving }),
        containerColor = colors.sheet.copy(alpha = 1f), shape = TimeboxShapes.sheet) {
        TaskSheetBackHandler(state.goalSaving, ::closeField)
        Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(when (entryField) { "duration" -> "How much time?"; "type" -> "Choose a Task Type"; else -> "Over which period?" },
                style = type.screenTitle, color = colors.on)
            when (entryField) {
                "duration" -> durationFields()
                "type" -> {
                    typePicker()
                    Text("Time in this Task Type and its descendants counts.", style = type.bodySmall, color = colors.onVariant)
                }
                "period" -> { periodFields(); Text("Weeks start Monday. Months follow the calendar.", style = type.bodySmall, color = colors.onVariant) }
            }
            state.goalError?.let { Text(it, color = colors.error, style = type.bodySmall) }
            if (entryField == "type" && state.goalError != null && !state.goalSaving)
                TextButton(viewModel::loadGoalTypes) { Text("Retry Task Types") }
            Button(::closeField, Modifier.fillMaxWidth().padding(bottom = 20.dp), enabled = !state.goalSaving) {
                Text(if (state.goalSaving) "Saving…" else "Done")
            }
        }
    }
    if (chooseDate) WheelDatePicker("Goal start date", start,
        onConfirm = { startText = it.toString(); datePicked = true; touched = true; chooseDate = false },
        onDismiss = { chooseDate = false })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard changes?") },
        text = { Text("Your unsaved changes will be lost.") },
        confirmButton = { TextButton({ touched = false; onDismiss() }) { Text("Discard") } },
        dismissButton = { TextButton({ discard = false }) { Text("Keep editing") } })
}

@Composable
private fun GoalSentenceValue(value: String, tag: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().testTag(tag).clickable(enabled = enabled, role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, Modifier.weight(1f), style = TimeboxTheme.type.screenTitle, color = colors.on)
            Icon(Icons.Outlined.ExpandMore, action, tint = colors.onVariant)
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}
