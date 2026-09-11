package com.timebox.android.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.TaskType
import com.timebox.android.data.remote.ActivityKind
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTracking(
    taskTypes: List<TaskType>, onChanged: () -> Unit,
    repository: ActivityRepository = (LocalContext.current.applicationContext as TimeboxApplication).activityRepository,
) {
    val state by repository.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChanged)
    var switching by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<TaskType?>(null) }
    var name by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(repository, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                while (true) { withContext(Dispatchers.IO) { repository.refresh() }; delay(5000) }
            }
            launch { while (true) { now = repository.now(); delay(1000) } }
        }
    }
    LaunchedEffect(state.snapshot?.cursor) { if (state.snapshot != null) changed() }
    LaunchedEffect(state.feedback) {
        if (state.feedback != null) { delay(6000); repository.dismissFeedback() }
    }
    val current = state.snapshot?.current
    val plan = repository.currentPlan()
    val enabled = !state.busy && state.snapshot != null
    val availableTypes = state.snapshot?.taskTypes?.takeIf { it.isNotEmpty() }?.map { TaskType(it.id, it.name, 0) } ?: taskTypes
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (current == null) {
                TextButton(enabled = enabled, onClick = { scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Start) } }) { Text("Start tracking") }
            } else {
                Text(current.name ?: current.taskType.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.on)
                Text("${Duration.between(Instant.parse(current.startAt), now).toMinutes().coerceAtLeast(0)}m", color = colors.onVariant)
                TextButton(enabled = enabled, onClick = { switching = true }) { Text("Switch") }
                TextButton(enabled = enabled, onClick = { scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Stop) } }) { Text("Stop") }
            }
        }
        if (current != null && plan != null && current.plannedBlockId != plan.id) {
            TextButton(enabled = enabled, onClick = { scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Switch, plan = plan) } }) {
                Text("Planned now: ${plan.name ?: availableTypes.find { it.id == plan.taskTypeId }?.name} · Switch")
            }
        }
        current?.plannedBlockId?.let { id ->
            val linked = state.snapshot!!.records.filter { it.plannedBlockId == id }
            val minutes = linked.sumOf { Duration.between(Instant.parse(it.startAt), it.endAt?.let(Instant::parse) ?: now).seconds }.coerceAtLeast(0) / 60
            Text("${linked.size} linked Actual Blocks · ${minutes}m recorded", color = colors.onVariant)
        }
        if (state.busy) Text("Saving…", color = colors.onVariant)
        state.feedback?.let { Text(it, color = colors.onVariant) }
        Text(if (state.offline) "Offline" + (if (state.pending) " · Unsynced" else "") else if (state.pending) "Unsynced" else if (state.snapshot != null) "Synced" else "Connection required", color = colors.onVariant)
        if (state.error != null || state.pending) {
            Text(state.error ?: "Change not confirmed.", color = colors.onVariant)
            TextButton(enabled = !state.busy, onClick = { scope.launch(Dispatchers.IO) { if (state.pending) repository.retry() else repository.refresh() } }) { Text("Retry") }
        }
    }
    if (switching) ModalBottomSheet(onDismissRequest = { switching = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Switch activity", style = MaterialTheme.typography.titleLarge)
            Text("Task Type", style = MaterialTheme.typography.labelLarge)
            availableTypes.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedType?.id == type.id, onClick = { selectedType = type })
                    TextButton(onClick = { selectedType = type }) { Text(type.name) }
                }
            }
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 500) name = it }, label = { Text("Block Name (optional)") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = enabled && selectedType != null, onClick = {
                scope.launch {
                    if (withContext(Dispatchers.IO) { repository.command(ActivityKind.Switch, selectedType!!.id, name) }) {
                        switching = false; selectedType = null; name = ""
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Switch activity") }
        }
    }
}
