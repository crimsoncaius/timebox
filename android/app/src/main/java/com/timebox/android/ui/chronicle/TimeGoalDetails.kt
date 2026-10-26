package com.timebox.android.ui.chronicle

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
import com.timebox.android.data.TimeGoal
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeGoalDetails(
    goal: TimeGoal, today: LocalDate, timezone: ZoneId, state: TimeGoalsUiState, viewModel: TimeGoalsViewModel,
    onDismiss: () -> Unit, onEdit: (() -> Unit)? = null,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var deleting by rememberSaveable(goal.id) { mutableStateOf(false) }
    val canManage = !state.offline && !state.goalSaving && !state.loading
    ModalBottomSheet(onDismissRequest = { if (!state.goalSaving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet.copy(alpha = 1f), shape = TimeboxShapes.sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(goal.taskType, style = type.sectionTitle, color = colors.on)
            Text("${goalRange(goal.period.start, goal.period.end)} · ${goalCadence(goal.unit, goal.interval)}",
                style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
            Text("${goalDuration((goal.period.durationSeconds / 60).toInt())} / ${goalDuration(goal.period.targetMinutes)}",
                style = type.display, color = colors.on, modifier = Modifier.padding(vertical = 12.dp))
            Text(goal.outcomeLabel(), style = type.label, color = colors.on)
            goal.endDate?.let {
                Text("Ended ${it.format(goalDateFormat)} · history retained", style = type.bodySmall,
                    color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
            }
            Text("Includes this Task Type and its descendants.\nReporting time zone · ${timezone.id}",
                style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
            if (goal.endDate == null) goal.nextTargetMinutes?.let {
                Text("Target changes to ${goalDuration(it)} on ${goal.nextTargetDate.format(goalDateFormat)}.",
                    style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(top = 12.dp))
            }
            Text("Actual Blocks", style = type.sectionTitle, color = colors.on, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            if (goal.period.blocks.isEmpty()) Text("No recorded time in this period.", style = type.bodySmall, color = colors.onVariant)
            goal.period.blocks.asReversed().forEach { block ->
                val start = block.startAt.atZone(timezone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
                val end = block.endAt?.atZone(timezone)?.format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) ?: "Running"
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(block.name.ifBlank { block.taskType }, style = type.body, color = colors.on)
                        Text("$start – $end\n${block.taskType}", style = type.bodySmall, color = colors.onVariant)
                    }
                    Text(goalDuration((block.creditedSeconds / 60).toInt()), style = type.label, color = colors.on)
                }
                HorizontalDivider(color = colors.hairline)
            }
            Text("Durations show time credited within this period.", style = type.bodySmall, color = colors.onVariant,
                modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (goal.endDate == null) {
                    if (onEdit != null) TextButton(onEdit, enabled = canManage) { Text("Edit goal") }
                }
                TextButton({ deleting = true }, enabled = canManage) { Text("Delete goal", color = colors.error) }
            }
            if (goal.startDate > today) Text("This goal has not started. You can delete it before its start date.", style = type.bodySmall, color = colors.onVariant)
            state.goalError?.let { Text(it, color = colors.error, style = type.bodySmall) }
            if (state.offline) Text("Connect to manage this goal.", style = type.bodySmall, color = colors.onVariant)
            TextButton(onDismiss, Modifier.align(Alignment.End), enabled = !state.goalSaving) { Text("Done") }
        }
    }
    if (deleting) {
        TimeGoalDeleteDialog(goal.id, state, viewModel, onCancel = { deleting = false },
            onSaved = { deleting = false; onDismiss() })
    }
}

@Composable
internal fun TimeGoalDeleteDialog(id: Int, state: TimeGoalsUiState, viewModel: TimeGoalsViewModel,
                                 onCancel: () -> Unit, onSaved: () -> Unit) {
    val colors = TimeboxTheme.colors
    AlertDialog(onDismissRequest = { if (!state.goalSaving) onCancel() },
        title = { Text("Delete this goal?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Permanently remove this goal and all its targets and results. Actual Blocks stay unchanged.")
            state.goalError?.let { Text(it, color = colors.error) }
        } },
        confirmButton = { TextButton({
            viewModel.deleteGoal(id, onSaved)
        }, enabled = !state.goalSaving && !state.offline) {
            Text(if (state.goalSaving) "Saving…" else "Delete goal")
        } },
        dismissButton = { TextButton(onCancel, enabled = !state.goalSaving) { Text("Cancel") } })
}
