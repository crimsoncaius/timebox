package com.timebox.android.ui.focus

import kotlinx.coroutines.launch
import com.timebox.android.data.BattleTask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.flattenBattleTasks
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.timebox.android.TimeboxApplication
import com.timebox.android.ui.day.ActivityTracking
import com.timebox.android.ui.theme.TimeboxTheme

@Composable fun FocusWakeSettings() {
    val controller = (LocalContext.current.applicationContext as TimeboxApplication).focusController
    val state by controller.state.collectAsState()
    com.timebox.android.ui.components.SectionCard {
        com.timebox.android.ui.components.SectionHeader(title = "Focus Mode")
        Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
            com.timebox.android.ui.components.SettingRow(
                title = "Keep display awake",
                description = "While Focus is visible on this device. Battery policy applies; manual locking still works.",
            ) {
                com.timebox.android.ui.components.TimeboxSwitch(checked = state.wake, onCheckedChange = controller::setWake)
            }
        }
    }
}
@Composable fun FocusMode(onTaskChanged: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as TimeboxApplication
    val controller = app.focusController
    val state by controller.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    var wakeMessage by remember { mutableStateOf<String?>(null) }
    DisposableEffect(view, owner, state.wake) {
        fun update() {
            try { view.keepScreenOn = state.wake && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); wakeMessage = null }
            catch (_: Exception) { wakeMessage = "The display may sleep. Focus and recording continue." }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer); update()
        onDispose { owner.lifecycle.removeObserver(observer); view.keepScreenOn = false }
    }
    BackHandler { controller.exit() }
    Surface(Modifier.fillMaxSize(), color = TimeboxTheme.colors.bg) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("FOCUS", style = TimeboxTheme.type.kicker, color = TimeboxTheme.colors.actual)
                OutlinedButton(onClick = controller::exit, border = BorderStroke(1.dp, TimeboxTheme.colors.hairline), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Exit Focus", style = TimeboxTheme.type.button, color = TimeboxTheme.colors.on)
                }
            }
            wakeMessage?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant) }
            state.error?.let { Text(it, color = TimeboxTheme.colors.error) }
            ActivityTracking(taskTypes = emptyList(), onChanged = {}, focus = true, focusTask = { elapsed -> FocusTask(onTaskChanged, elapsed) })
        }
    }
}

@Composable private fun FocusTask(onTaskChanged: () -> Unit, elapsed: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as TimeboxApplication
    val activity by app.activityRepository.state.collectAsState()
    val taskId = activity.snapshot?.current?.taskId
    val notice by app.taskCompletion.notice.collectAsState()
    var task by remember(taskId) { mutableStateOf<BattleTask?>(null) }
    var error by remember(taskId) { mutableStateOf<String?>(null) }
    var busy by remember(taskId) { mutableStateOf(false) }
    var ownCompletion by remember(taskId) { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(taskId) { if (taskId != null) app.repository.listBattleTasks().onSuccess { task = it.items.flattenBattleTasks().find { it.id == taskId } } }
    if (task == null || task?.id != taskId) elapsed()
    task?.takeIf { it.id == taskId }?.let { current ->
        val colors = TimeboxTheme.colors
        val activityTitle = activity.snapshot?.current?.let { it.name?.takeIf(String::isNotBlank) ?: it.taskType.name }
        if (current.title != activityTitle) Text(current.title, style = TimeboxTheme.type.sectionTitle, color = colors.onVariant)
        if (current.description.isNotBlank()) Text(current.description, style = TimeboxTheme.type.body, color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
        elapsed()
        Spacer(Modifier.height(24.dp))
        if (current.subtasks.isNotEmpty()) Text("SUBTASKS · ${current.subtasks.count { it.checked }} / ${current.subtasks.size}", style = TimeboxTheme.type.kicker, color = colors.actual)
        current.subtasks.forEach { subtask -> Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = subtask.checked, enabled = !busy && current.status != TaskStatus.Completed, onCheckedChange = { checked ->
                busy = true; scope.launch {
                    (if (checked) app.repository.checkSubtask(subtask.id) else app.repository.uncheckSubtask(subtask.id)).onSuccess { next ->
                        if (task?.id == current.id) task = task?.copy(subtasks = task!!.subtasks.map { if (it.id == next.id) next else it })
                    }.onFailure { error = "Could not update Subtask." }; busy = false
                }
            }, colors = CheckboxDefaults.colors(checkedColor = colors.actual))
            Text(subtask.title, style = TimeboxTheme.type.body, color = if (subtask.checked) colors.onVariant else colors.on,
                textDecoration = if (subtask.checked) TextDecoration.LineThrough else TextDecoration.None)
        }
            HorizontalDivider(color = colors.hairline)
        }
        if (current.status != TaskStatus.Completed) OutlinedButton(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp), border = BorderStroke(1.dp, colors.hairline), enabled = !busy, onClick = {
            busy = true; scope.launch {
                app.taskCompletion.transition(current.id, current.status, TaskStatus.Completed).onSuccess { if (task?.id == current.id) { task = it; ownCompletion = app.taskCompletion.notice.value?.id }; onTaskChanged() }.onFailure { error = "Could not complete Task." }; busy = false
            }
        }) { Text("Complete Task") }
        notice?.takeIf { it.canUndo && it.id == ownCompletion }?.let { saved -> TextButton(enabled = !busy, onClick = {
            busy = true; scope.launch { app.taskCompletion.undo(saved.id).onSuccess { if (task?.id == it.id) task = it; onTaskChanged() }.onFailure { error = "Could not undo Task completion." }; busy = false }
        }) { Text("Undo Task completion") } }
        error?.let { Text(it) }
    }
}
