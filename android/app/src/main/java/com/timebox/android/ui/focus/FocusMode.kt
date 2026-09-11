package com.timebox.android.ui.focus

import kotlinx.coroutines.launch
import com.timebox.android.data.BattleTask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.flattenBattleTasks
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = state.wake, onCheckedChange = controller::setWake)
        Text("Keep display awake while Focus is visible")
    }
    Text("On this device. Battery policy applies; manual locking still works.")
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
        Column(Modifier.fillMaxSize().imePadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = controller::exit, modifier = Modifier.align(Alignment.End)) { Text("Exit Focus") }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(64.dp))
            Text("Focus", style = MaterialTheme.typography.labelLarge)
            ActivityTracking(taskTypes = emptyList(), onChanged = {}, focus = true)
            FocusTask(onTaskChanged)
            wakeMessage?.let { Text(it) }; state.error?.let { Text(it) }
            }
        }
    }
}

@Composable private fun FocusTask(onTaskChanged: () -> Unit) {
    val app = LocalContext.current.applicationContext as TimeboxApplication
    val activity by app.activityRepository.state.collectAsState()
    val taskId = activity.snapshot?.current?.taskId
    val notice by app.taskCompletion.notice.collectAsState()
    var task by remember(taskId) { mutableStateOf<BattleTask?>(null) }
    var error by remember(taskId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(taskId) { if (taskId != null) app.repository.listBattleTasks().onSuccess { task = it.items.flattenBattleTasks().find { it.id == taskId } } }
    task?.takeIf { it.id == taskId }?.let { current ->
        Text(current.title, style = MaterialTheme.typography.titleLarge)
        Text(current.description)
        current.subtasks.forEach { subtask -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = subtask.checked, enabled = !busy && current.status != TaskStatus.Completed, onCheckedChange = { checked ->
                busy = true; scope.launch {
                    (if (checked) app.repository.checkSubtask(subtask.id) else app.repository.uncheckSubtask(subtask.id)).onSuccess { next ->
                        if (task?.id == current.id) task = task?.copy(subtasks = task!!.subtasks.map { if (it.id == next.id) next else it })
                    }.onFailure { error = "Could not update Subtask." }; busy = false
                }
            }); Text(subtask.title)
        } }
        if (current.status != TaskStatus.Completed) Button(enabled = !busy, onClick = {
            busy = true; scope.launch {
                app.taskCompletion.transition(current.id, current.status, TaskStatus.Completed).onSuccess { if (task?.id == current.id) task = it; onTaskChanged() }.onFailure { error = "Could not complete Task." }; busy = false
            }
        }) { Text("Complete Task") }
        notice?.takeIf { it.canUndo }?.let { saved -> TextButton(enabled = !busy, onClick = {
            busy = true; scope.launch { app.taskCompletion.undo(saved.id).onSuccess { if (task?.id == it.id) task = it; onTaskChanged() }.onFailure { error = "Could not undo Task completion." }; busy = false }
        }) { Text("Undo Task completion") } }
        error?.let { Text(it) }
    }
}
