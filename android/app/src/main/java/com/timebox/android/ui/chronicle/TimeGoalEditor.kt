package com.timebox.android.ui.chronicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                            state: HabitsUiState, viewModel: HabitsViewModel, onDismiss: () -> Unit) {
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
    LaunchedEffect(Unit) { viewModel.loadGoalTypes() }
    val parsedHours = hours.toIntOrNull()
    val parsedMinutes = minutes.toIntOrNull()
    val target = (parsedHours ?: 0) * 60 + (parsedMinutes ?: 0)
    val replacing = original != null && (typeId != original.taskTypeId || unit != original.unit || every != original.interval)
    val start = if (replacing) today else LocalDate.parse(startText)
    val end = runCatching { firstGoalPeriodEnd(start, unit, every) }.getOrNull()
    val valid = typeId != null && parsedHours != null && parsedMinutes != null && parsedMinutes in 0..59 &&
        target in 1..525600 && end != null && end.year < 9999
    fun dismiss() { if (!state.goalSaving) { if (touched) discard = true else onDismiss() } }
    ModalBottomSheet(onDismissRequest = ::dismiss, properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
            if (it == SheetValue.Hidden && (touched || state.goalSaving)) { if (!state.goalSaving) discard = true; false } else true
        }), containerColor = colors.sheet.copy(alpha = 1f), shape = TimeboxShapes.sheet) {
        TaskSheetBackHandler(state.goalSaving) { if (chooseType) chooseType = false else dismiss() }
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
                    TaskTypePicker(taskTypes = state.taskTypes, query = query, onQueryChange = { query = it }, selectedTypeId = typeId,
                        onChoose = { if (!state.goalSaving) { typeId = it.id; path = it.name; touched = true; chooseType = false } },
                        onCreate = { name -> viewModel.createGoalType(name) {
                            typeId = it.id; path = it.name; touched = true; chooseType = false
                        } }, recommendationEnabled = false)
                } else {
                    CreationDateRow("Task Type", path.ifEmpty { "Choose a Task Type" }, !state.goalSaving) { query = ""; chooseType = true }
                    Text("Includes time in all descendant Task Types.", style = type.bodySmall, color = colors.onVariant)
                    Text("Target duration", style = type.label, color = colors.on)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(4); touched = true }, Modifier.weight(1f),
                            enabled = !state.goalSaving, label = { Text("Hours") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2); touched = true }, Modifier.weight(1f),
                            enabled = !state.goalSaving, label = { Text("Minutes") }, singleLine = true,
                            isError = parsedMinutes == null || parsedMinutes !in 0..59,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    if (target !in 1..525600 || parsedMinutes == null || parsedMinutes !in 0..59)
                        Text("Enter 1 minute–8,760 hours; minutes must be 0–59.", style = type.bodySmall, color = colors.error)
                    Text("Repeat", style = type.label, color = colors.on)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    if (original == null) CreationDateRow("Starts", start.format(goalDateFormat), !state.goalSaving) { chooseDate = true }
                    HorizontalDivider(color = colors.hairline)
                    when {
                        original == null -> {
                            Text("First period · ${end?.let { goalRange(start, it) } ?: "Choose an earlier date"}", style = type.label, color = colors.on)
                            Text("${goalDuration(target.coerceAtLeast(0))} ${goalCadence(unit, every).lowercase()}. Existing Actual Blocks in this period count. No surplus carries forward.",
                                style = type.bodySmall, color = colors.onVariant)
                            if ((unit == "week" && start.dayOfWeek.value != 1) || (unit == "month" && start.dayOfMonth != 1))
                                Text("This first period is shorter. Its full target still applies.", style = type.bodySmall, color = colors.onVariant)
                        }
                        replacing -> {
                            Text("Replace from ${today.format(goalDateFormat)}", style = type.label, color = colors.on)
                            Text("The existing goal ends today; its unfinished period is excused. A replacement starts today with its full target for ${end?.let { goalRange(start, it) }}. Earlier history stays.",
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
                Text(if (state.goalSaving) "Saving…" else if (original == null) "Create Time Goal" else if (replacing) "End & replace goal" else "Save target")
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
