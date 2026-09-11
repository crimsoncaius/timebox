package com.timebox.android.ui.day

import com.timebox.android.data.parseActivityInstant

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
    focus: Boolean = false, planning: Boolean = false, onEnterFocus: () -> Unit = {},
) {
    val state by repository.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChanged)
    var switching by remember { mutableStateOf(false) }
    var reviewingLegacy by remember { mutableStateOf(false) }
    var reviewingRejected by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var targetId by remember { mutableStateOf<Int?>(null) }
    var timing by remember { mutableStateOf<ActivityTimeValue?>(null) }
    var timingError by remember { mutableStateOf<String?>(null) }
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
    val question = state.snapshot?.checkIn?.question
    var checkInOpen by remember(question?.id) { mutableStateOf(question != null && !repository.checkInDismissed(question.id)) }
    val checkIns = (LocalContext.current.applicationContext as? TimeboxApplication)?.checkIns
    val detectionAccess = checkIns?.access?.collectAsState()?.value
    val context = LocalContext.current
    val requestedQuestion = checkIns?.openQuestion?.collectAsState()?.value
    LaunchedEffect(requestedQuestion, question?.id) {
        if (requestedQuestion != null && requestedQuestion == question?.id) { checkInOpen = true; checkIns?.consumeOpen() }
    }
    fun dismissCheckIn() { checkInOpen = false; question?.let { scope.launch { repository.dismissCheckIn(it.id) } } }

    LaunchedEffect(current?.id) { selectedType = null }
    val plan = repository.currentPlan()
    val enabled = !state.busy && state.snapshot != null
    val availableTypes = state.snapshot?.taskTypes?.takeIf { it.isNotEmpty() }?.map { TaskType(it.id, it.name, 0) } ?: taskTypes
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (focus && current != null) {
            Text(current.name ?: current.taskType.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.align(Alignment.CenterHorizontally), color = colors.on)
            Text("${Duration.between(parseActivityInstant(current.startAt), now).toMinutes().coerceAtLeast(0)}m", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally), color = colors.onVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (current == null) {
                TextButton(enabled = enabled, onClick = { scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Start) } }) { Text("Start tracking") }
            } else {
                if (!focus) Text(current.name ?: current.taskType.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.on)
                if (!focus) Text("${Duration.between(parseActivityInstant(current.startAt), now).toMinutes().coerceAtLeast(0)}m", color = colors.onVariant)
                TextButton(enabled = enabled, onClick = { targetId = current.id; timing = null; timingError = null; switching = true }) { Text("Switch") }
                if (!focus) TextButton(enabled = enabled, onClick = { targetId = current.id; timing = null; timingError = null; stopping = true }) { Text("Stop") }
            }
            if (!focus) TextButton(enabled = enabled && !planning, onClick = onEnterFocus) { Text("Focus") }
        }
        if (question != null && !checkInOpen) TextButton(onClick = { checkInOpen = true }) { Text("Check-in waiting") }
        if (current != null && state.checkInPreferences.enabled && detectionAccess == com.timebox.android.checkin.DetectionAccess.Denied) {
            Text("Optional screen-off detection needs usage access. Recording continues without it.")
            TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Enable screen-off detection") }
        }
        if (!focus && planning) Text("Finish or cancel planning to enter Focus.")
        if (focus && current != null && current.name.isNullOrBlank() && current.taskType.name == "unspecified") {
            Text("What are you doing right now?", style = MaterialTheme.typography.headlineSmall)
            Text("Recording continues while you decide.")
            availableTypes.forEach { type -> TextButton(onClick = { selectedType = type }) { Text((if (selectedType?.id == type.id) "Selected: " else "") + type.name) } }
            Text("Apply from the original start (${current.startAt}) describes all this activity. Start now keeps preceding unspecified time.")
            Button(enabled = selectedType != null, onClick = { val id = current.id; scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Describe, selectedType!!.id, observedTargetId = id) } }) { Text("Apply from original start") }
            TextButton(enabled = selectedType != null, onClick = { val id = current.id; scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Switch, selectedType!!.id, observedTargetId = id) } }) { Text("Start now") }
        }
        if (current != null && plan != null && current.plannedBlockId != plan.id) {
            TextButton(enabled = enabled, onClick = { scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Switch, plan = plan) } }) {
                Text("Planned now: ${plan.name ?: availableTypes.find { it.id == plan.taskTypeId }?.name} · Switch")
            }
        }
        current?.plannedBlockId?.let { id ->
            val linked = state.snapshot!!.records.filter { it.plannedBlockId == id }
            val minutes = linked.sumOf { Duration.between(parseActivityInstant(it.startAt), it.endAt?.let(::parseActivityInstant) ?: now).seconds }.coerceAtLeast(0) / 60
            Text("${linked.size} linked Actual Blocks · ${minutes}m recorded", color = colors.onVariant)
        }
        if (state.busy) Text("Saving…", color = colors.onVariant)
        state.feedback?.let { Text(it, color = colors.onVariant) }
        if (!focus && state.rejectedRecovery != null) {
            TextButton(onClick = { reviewingRejected = !reviewingRejected }) { Text("Review rejected changes") }
            if (reviewingRejected) {
                Text("These changes were not replayed. Use Day add/edit to correct the saved timeline.")
                Text(state.rejectedRecovery!!)
            }
        }
        if (!focus && state.legacyRecovery != null) {
            TextButton(onClick = { reviewingLegacy = !reviewingLegacy }) { Text("Review old Work Mode data") }
            if (reviewingLegacy) {
                Text("Saved device observations, not recorded time. Use Day add/edit for corrections.")
                Text(state.legacyRecovery!!)
            }
        }
        Text(if (state.offline) "Offline" + (if (state.pending) " · Unsynced" else "") else if (state.pending) "Unsynced" else if (state.snapshot != null) "Synced" else "Connection required", color = colors.onVariant)
        if (state.error != null || state.pending) {
            Text(state.error ?: "Change not confirmed.", color = colors.onVariant)
            TextButton(enabled = !state.busy, onClick = { scope.launch(Dispatchers.IO) { if (state.pending) repository.retry() else repository.refresh() } }) { Text("Retry") }
        }
    }
    if (question != null && current != null && checkInOpen) ModalBottomSheet(onDismissRequest = { dismissCheckIn() }) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Still doing this?", style = MaterialTheme.typography.headlineLarge)
            Text(current.name ?: current.taskType.name, style = MaterialTheme.typography.headlineSmall)
            Button(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { if (repository.checkIn(com.timebox.android.data.remote.CheckInEventDto("confirm", questionId = question.id))) checkInOpen = false } }) { Text("Yes, still doing this") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { dismissCheckIn(); targetId = current.id; timing = null; timingError = null; switching = true }) { Text("Switch activity") }
            Text("Recording continues while you decide.")
        }
    }
    if (switching || stopping) ModalBottomSheet(onDismissRequest = { switching = false; stopping = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (stopping) "Stop tracking" else "Switch activity", style = MaterialTheme.typography.titleLarge)
            if (!stopping) {
            Text("Task Type", style = MaterialTheme.typography.labelLarge)
            availableTypes.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedType?.id == type.id, onClick = { selectedType = type })
                    TextButton(onClick = { selectedType = type }) { Text(type.name) }
                }
            }
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 500) name = it }, label = { Text("Block Name (optional)") }, modifier = Modifier.fillMaxWidth())
            }
            val zone = java.time.ZoneId.of(state.snapshot?.reportingTimezone ?: "UTC")
            Text("When did this ${if (stopping) "stop" else "change"} happen?")
            Row {
                TextButton(onClick = { timing = null }) { Text("Now") }
                TextButton(onClick = { timing = ActivityTimeValue.from(now.minusSeconds(900), zone) }) { Text("15 min ago") }
                TextButton(onClick = { timing = ActivityTimeValue.from(now, zone) }) { Text("Choose time") }
            }
            timing?.let { ActivityTimeField("Change", it, zone) { next -> timing = next } } ?: Text("Now")
            Text("After this change", style = MaterialTheme.typography.titleMedium)
            Text("${current?.name ?: current?.taskType?.name} ends ${timing?.local ?: "now"}.")
            Text(if (stopping) "Time after this is unrecorded." else "${name.ifBlank { selectedType?.name ?: "Next activity" }} starts at the same time and continues.")
            timingError?.let { Text(it) }
            Button(enabled = enabled && (stopping || selectedType != null), onClick = {
                scope.launch {
                    try {
                        val at = timing?.resolve(zone)
                        if (withContext(Dispatchers.IO) { repository.command(if (stopping) ActivityKind.Stop else ActivityKind.Switch, if (stopping) null else selectedType!!.id, if (stopping) null else name, effectiveAt = at, observedTargetId = targetId) }) {
                            switching = false; stopping = false; selectedType = null; name = ""
                        } else timingError = repository.state.value.error
                    } catch (error: Exception) { timingError = error.message }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (stopping) "Stop tracking" else "Switch activity") }
            TextButton(onClick = { switching = false; stopping = false }) { Text("Cancel") }
        }
    }
}
