package com.timebox.android.ui.day

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskStatus
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.theme.TimeboxTheme

@Composable
fun WorkModeScreen(
    state: WorkModeUiState,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onSaveActual: () -> Unit,
    onToggleSubtask: (Subtask) -> Unit,
    onFinish: () -> Unit,
    onFinishAndComplete: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(enabled = !state.saving, onBack = onClose)
    val colors = TimeboxTheme.colors
    val task = state.task
    val title = task?.title ?: state.actualBlock.task?.title ?: state.actualBlock.taskTypeName

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("work-mode"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, enabled = !state.saving) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                Text("Day")
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (state.isRunning) "ACTUAL · LIVE" else "ACTUAL · RECORDED",
                style = TimeboxTheme.type.kicker,
                color = if (state.isRunning) colors.actual else colors.onVariant,
            )
        }

        Text(title, style = TimeboxTheme.type.display, color = colors.on)

        if (task != null && task.subtasks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Subtasks ${task.subtasks.count { it.checked }}/${task.subtasks.size}",
                    style = TimeboxTheme.type.sectionTitle,
                    color = colors.on,
                )
                task.subtasks.forEach { subtask ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = subtask.checked,
                            enabled = !state.saving && task.status != TaskStatus.Completed,
                            onCheckedChange = { onToggleSubtask(subtask) },
                            modifier = Modifier.testTag("work-mode-subtask-${subtask.id}"),
                        )
                        Text(
                            subtask.title,
                            style = TimeboxTheme.type.body,
                            color = if (subtask.effectivelyResolved) colors.onVariant else colors.on,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Actual time", style = TimeboxTheme.type.sectionTitle, color = colors.on)
            Text(
                "${state.timezone} · minute precision",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
            )
            OutlinedTextField(
                value = state.startInput,
                onValueChange = onStartChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Start (YYYY-MM-DD HH:MM)") },
                singleLine = true,
                enabled = !state.saving,
            )
            OutlinedTextField(
                value = state.endInput,
                onValueChange = onEndChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (state.isRunning) "End (optional until finished)" else "End (YYYY-MM-DD HH:MM)") },
                singleLine = true,
                enabled = !state.saving,
            )
            state.error?.let {
                Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error)
            }
            PrimaryButton(
                text = "Save Actual time",
                onClick = onSaveActual,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
            )
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = if (state.isRunning) "Finish session" else "Save and return to Day",
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
        )
        if (task != null && task.status != TaskStatus.Completed) {
            PrimaryButton(
                text = if (state.isRunning) "Finish session + complete Task" else "Complete Task",
                onClick = onFinishAndComplete,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
            )
        }
    }
}
