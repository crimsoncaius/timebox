package com.timebox.android.ui.day

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.theme.TimeboxTheme

@Composable
fun WorkModeScreen(
    state: WorkModeUiState,
    onToggleSubtask: (Subtask) -> Unit,
    onLeave: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(enabled = !state.saving, onBack = onLeave)
    val colors = TimeboxTheme.colors
    val current = state.currentBlock
    val next = state.nextBlock
    val task = state.task

    Column(
        Modifier.fillMaxSize().background(colors.bg).navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag("work-mode"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("WORK MODE", style = TimeboxTheme.type.kicker, color = colors.actual)
                Text(
                    when {
                        state.isRecording -> "Actual recording live"
                        state.confirmingPlannedBlockId != null -> "Confirming current work…"
                        else -> "Following today’s plan"
                    },
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLeave, enabled = !state.saving) { Text("Back to app") }
        }

        when {
            current != null -> CurrentWork(current, state, onToggleSubtask)
            next != null -> {
                Text("UP NEXT", style = TimeboxTheme.type.kicker, color = colors.planned)
                Text(blockTitle(next), style = TimeboxTheme.type.display, color = colors.on)
                Text(
                    "${minuteLabel(next.startMinute)} · in ${countdownMinutes(next, state)} minutes",
                    style = TimeboxTheme.type.body,
                    color = colors.onVariant,
                )
            }
            else -> {
                Spacer(Modifier.weight(1f))
                Text("No more planned work today", style = TimeboxTheme.type.display, color = colors.on)
                Text("Work Mode stays open until you exit.", style = TimeboxTheme.type.body, color = colors.onVariant)
                Spacer(Modifier.weight(1f))
            }
        }

        state.error?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error) }
        PrimaryButton(
            text = "Exit Work Mode",
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
        )
    }
}

@Composable
private fun CurrentWork(block: TimeBlock, state: WorkModeUiState, onToggleSubtask: (Subtask) -> Unit) {
    val colors = TimeboxTheme.colors
    val task = state.task
    Text("CURRENT · ${minuteLabel(block.startMinute)}–${minuteLabel(block.endMinute)}", style = TimeboxTheme.type.kicker, color = colors.actual)
    Text(blockTitle(block, task?.title), style = TimeboxTheme.type.display, color = colors.on)
    if (task != null) {
        Text(block.taskTypeName, style = TimeboxTheme.type.sectionTitle, color = colors.onVariant)
    }
    val detail = task?.description?.takeIf(String::isNotBlank) ?: block.note?.takeIf(String::isNotBlank)
    detail?.let { Text(it, style = TimeboxTheme.type.body, color = colors.onVariant) }
    if (task != null && task.subtasks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Subtasks ${task.subtasks.count { it.checked }}/${task.subtasks.size}", style = TimeboxTheme.type.sectionTitle, color = colors.on)
            task.subtasks.forEach { subtask ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = subtask.checked,
                        enabled = !state.saving && task.status != TaskStatus.Completed,
                        onCheckedChange = { onToggleSubtask(subtask) },
                        modifier = Modifier.testTag("work-mode-subtask-${subtask.id}"),
                    )
                    Text(subtask.title, style = TimeboxTheme.type.body, color = colors.on)
                }
            }
        }
    }
}

@Composable
fun WorkModeEntryDialog(onPlanFirst: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("No immediate planned work") },
        text = { Text("There is no planned work for the immediate future. Plan something at the current time or continue anyway.") },
        dismissButton = { TextButton(onClick = onPlanFirst) { Text("Plan something first") } },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
    )
}

@Composable
fun WorkModeRestoreDialog(onDecline: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Were you still working?") },
        text = { Text("The application was away for more than ten minutes. Confirm before Work Mode records that interval.") },
        dismissButton = { TextButton(onClick = onDecline) { Text("No, stop at last confirmed time") } },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Yes, I continued") } },
    )
}

private fun blockTitle(block: TimeBlock, taskTitle: String? = block.task?.title): String = taskTitle ?: block.taskTypeName
private fun minuteLabel(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
private fun countdownMinutes(block: TimeBlock, state: WorkModeUiState): Long {
    val zone = java.time.ZoneId.of(state.timezone)
    val minute = state.lastObservedAt.atZone(zone).hour * 60 + state.lastObservedAt.atZone(zone).minute
    return (block.startMinute - minute).coerceAtLeast(0).toLong()
}
