package com.timebox.android.ui.day

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.Lane
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.primaryIdentity
import com.timebox.android.data.secondaryIdentity
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.theme.TimeboxTheme

@Composable
fun WorkModeScreen(
    state: WorkModeUiState,
    onToggleSubtask: (Subtask) -> Unit,
    onExit: () -> Unit,
    contentInsets: WindowInsets = WindowInsets.statusBars,
) {
    BackHandler(enabled = !state.saving, onBack = onExit)
    val colors = TimeboxTheme.colors
    val current = state.currentBlock
    val next = state.nextBlock

    Column(
        Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(contentInsets).navigationBarsPadding()
            .testTag("work-mode"),
    ) {
        when {
            current != null -> CurrentWork(current, state, onToggleSubtask, onExit)
            next != null -> UpNextWork(next, state, onExit)
            else -> EmptyWork(state, onExit)
        }
    }
}

@Composable
private fun CurrentWork(
    block: TimeBlock,
    state: WorkModeUiState,
    onToggleSubtask: (Subtask) -> Unit,
    onExit: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val task = state.task
    val openEnded = block.lane == Lane.Actual && state.activePlannedEndAt == null
    val elapsedSeconds = elapsedSeconds(block, state)
    val durationSeconds = (block.endMinute - block.startMinute).coerceAtLeast(1) * 60
    val remainingSeconds = (durationSeconds - elapsedSeconds).coerceAtLeast(0)

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.57f)
                .background(colors.actualSurface)
                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 28.dp),
        ) {
            WorkModeHeader(state)
            Spacer(Modifier.weight(1f))
            Text("CURRENT", style = TimeboxTheme.type.kicker, color = colors.actual)
            Spacer(Modifier.height(10.dp))
            Text(
                block.primaryIdentity(task?.title),
                style = TimeboxTheme.type.display.copy(fontSize = 52.sp, lineHeight = 50.sp),
                color = colors.on,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.43f)
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (openEnded) "Started ${minuteLabel(block.startMinute)}"
                        else "${minuteLabel(block.startMinute)}–${minuteLabel(block.endMinute)}",
                        style = TimeboxTheme.type.mono,
                        color = colors.onVariant,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(workModeStatus(state), style = TimeboxTheme.type.body, color = colors.on)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatDurationSeconds(if (openEnded) elapsedSeconds else remainingSeconds),
                        style = TimeboxTheme.type.display.copy(fontSize = 44.sp),
                        color = colors.on,
                    )
                    Text(
                        if (openEnded) "MIN:SEC IN" else "MIN:SEC LEFT",
                        style = TimeboxTheme.type.kicker,
                        color = colors.actual,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            SessionProgress(
                progress = if (openEnded) 1f else elapsedSeconds.toFloat() / durationSeconds,
                leftLabel = "${formatDurationSeconds(elapsedSeconds)} elapsed",
                rightLabel = if (openEnded) "Open-ended" else "${formatDurationSeconds(durationSeconds)} planned",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                block.secondaryIdentity(task?.title)?.let {
                    Text(it, style = TimeboxTheme.type.sectionTitle, color = colors.onVariant)
                }
                val detail = task?.description?.takeIf(String::isNotBlank) ?: block.note?.takeIf(String::isNotBlank)
                detail?.let { Text(it, style = TimeboxTheme.type.body, color = colors.onVariant) }
                if (task != null && task.subtasks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Subtasks ${task.subtasks.count { it.checked }}/${task.subtasks.size}",
                            style = TimeboxTheme.type.sectionTitle,
                            color = colors.on,
                        )
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
                state.error?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error) }
            }
            ExitWorkModeButton(state, onExit)
        }
    }
}

@Composable
private fun UpNextWork(block: TimeBlock, state: WorkModeUiState, onExit: () -> Unit) {
    val colors = TimeboxTheme.colors
    val countdown = formatDurationSeconds(countdownSeconds(block, state))
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.57f)
                .background(colors.plannedSurface)
                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 28.dp),
        ) {
            WorkModeHeader(state, accent = colors.planned, indicator = "WAITING")
            Spacer(Modifier.weight(1f))
            Text("UP NEXT", style = TimeboxTheme.type.kicker, color = colors.planned)
            Spacer(Modifier.height(10.dp))
            Text(
                blockTitle(block),
                style = TimeboxTheme.type.display.copy(fontSize = 52.sp, lineHeight = 50.sp),
                color = colors.on,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.43f)
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(minuteLabel(block.startMinute), style = TimeboxTheme.type.display.copy(fontSize = 40.sp), color = colors.on)
                    Text("START TIME", style = TimeboxTheme.type.kicker, color = colors.planned)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(countdown, style = TimeboxTheme.type.display.copy(fontSize = 44.sp), color = colors.on)
                    Text("MIN:SEC TO START", style = TimeboxTheme.type.kicker, color = colors.planned)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "${minuteLabel(block.startMinute)} · in $countdown",
                style = TimeboxTheme.type.body,
                color = colors.onVariant,
            )
            block.secondaryIdentity()?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = TimeboxTheme.type.sectionTitle, color = colors.onVariant)
            }
            Spacer(Modifier.weight(1f))
            state.error?.let {
                Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error)
                Spacer(Modifier.height(12.dp))
            }
            ExitWorkModeButton(state, onExit)
        }
    }
}

@Composable
private fun EmptyWork(state: WorkModeUiState, onExit: () -> Unit) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.57f)
                .background(colors.low)
                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 28.dp),
        ) {
            WorkModeHeader(state, indicator = "OPEN")
            Spacer(Modifier.weight(1f))
            Text("TODAY", style = TimeboxTheme.type.kicker, color = colors.onVariant)
            Spacer(Modifier.height(10.dp))
            Text(
                "No more planned work today",
                style = TimeboxTheme.type.display.copy(fontSize = 46.sp, lineHeight = 46.sp),
                color = colors.on,
                maxLines = 3,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.43f)
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text("Work Mode stays open until you exit.", style = TimeboxTheme.type.body, color = colors.onVariant)
            Spacer(Modifier.weight(1f))
            state.error?.let {
                Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error)
                Spacer(Modifier.height(12.dp))
            }
            ExitWorkModeButton(state, onExit)
        }
    }
}

@Composable
private fun WorkModeHeader(
    state: WorkModeUiState,
    accent: androidx.compose.ui.graphics.Color = TimeboxTheme.colors.actual,
    indicator: String = when {
        state.isRecording -> "LIVE"
        state.confirmingPlannedBlockId != null -> "STARTING"
        else -> "ACTIVE"
    },
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("WORK MODE", style = TimeboxTheme.type.kicker, color = accent)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(7.dp))
        Text(indicator, style = TimeboxTheme.type.navLabel, color = accent)
    }
}

@Composable
private fun SessionProgress(progress: Float, leftLabel: String, rightLabel: String) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.actualBorder.copy(alpha = 0.55f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(colors.actual),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(leftLabel, style = TimeboxTheme.type.monoSmall, color = colors.onVariant)
            Spacer(Modifier.weight(1f))
            Text(rightLabel, style = TimeboxTheme.type.monoSmall, color = colors.onVariant)
        }
    }
}

@Composable
private fun ExitWorkModeButton(state: WorkModeUiState, onExit: () -> Unit) {
    PrimaryButton(
        text = "Exit Work Mode",
        onClick = onExit,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.saving,
    )
}

private fun workModeStatus(state: WorkModeUiState): String = when {
    state.isRecording -> "Actual recording live"
    state.confirmingPlannedBlockId != null -> "Confirming current work…"
    else -> "Following today’s plan"
}

private fun elapsedSeconds(block: TimeBlock, state: WorkModeUiState): Int =
    (observedSecond(state) - block.startMinute * 60).coerceAtLeast(0)

private fun observedSecond(state: WorkModeUiState): Int {
    val local = state.lastObservedAt.atZone(java.time.ZoneId.of(state.timezone))
    return local.hour * 60 * 60 + local.minute * 60 + local.second
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

private fun blockTitle(block: TimeBlock): String = block.primaryIdentity()
private fun minuteLabel(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
internal fun formatDurationSeconds(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds.coerceAtLeast(0) / 60, totalSeconds.coerceAtLeast(0) % 60)

private fun countdownSeconds(block: TimeBlock, state: WorkModeUiState): Int =
    (block.startMinute * 60 - observedSecond(state)).coerceAtLeast(0)
