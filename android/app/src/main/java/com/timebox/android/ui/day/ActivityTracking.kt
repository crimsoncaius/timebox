package com.timebox.android.ui.day

import com.timebox.android.data.parseActivityInstant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    focus: Boolean = false, focusTask: @Composable (@Composable () -> Unit) -> Unit = { elapsed -> elapsed() }, planning: Boolean = false, onEnterFocus: () -> Unit = {},
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
    val unknown = current != null && current.name.isNullOrBlank() && current.taskType.name == "unspecified"
    var describing by remember(current?.id) { mutableStateOf(false) }
    var describeFromNow by remember(current?.id) { mutableStateOf(false) }
    var focusOptions by remember { mutableStateOf(false) }
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
    var expanded by remember(current?.id) { mutableStateOf(false) }
    BackHandler(!focus && expanded && !switching && !stopping && !checkInOpen) { expanded = false }
    val colors = TimeboxTheme.colors
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = if (focus) 0.dp else 20.dp)) {
            if (focus && current != null) {
                Spacer(Modifier.height(36.dp))
                if (current.taskType.name != "unspecified") Text(current.taskType.name.uppercase(), style = TimeboxTheme.type.kicker, color = colors.actual)
                Spacer(Modifier.height(16.dp))
                Text(current.name?.takeIf { it.isNotBlank() } ?: current.taskType.name.takeUnless { it == "unspecified" } ?: "What are you doing?",
                    style = TimeboxTheme.type.display, color = colors.on)
                if (unknown) Text("Choose an activity whenever you’re ready.", style = TimeboxTheme.type.body, color = colors.onVariant, modifier = Modifier.padding(top = 12.dp))
                val elapsed: @Composable () -> Unit = {
                    Spacer(Modifier.height(28.dp))
                    HorizontalDivider(color = colors.hairline)
                    Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val seconds = Duration.between(parseActivityInstant(current.startAt), now).seconds.coerceAtLeast(0)
                        Text("%d:%02d".format(seconds / 60, seconds % 60), style = TimeboxTheme.type.display, color = colors.on)
                        Text("elapsed", style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
                focusTask(elapsed)
            }
            if (!focus) {
                CurrentActivityControl(
                    activity = current?.name?.takeIf { it.isNotBlank() } ?: current?.taskType?.name.orEmpty(),
                    elapsed = current?.let { "${Duration.between(parseActivityInstant(it.startAt), now).toMinutes().coerceAtLeast(0)}m" }.orEmpty(),
                    running = current != null, expanded = expanded, enabled = enabled, focusEnabled = enabled && !planning,
                    onToggle = { expanded = !expanded },
                    onStart = { scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Start) } },
                    onSwitch = { expanded = false; current?.let { targetId = it.id; timing = null; timingError = null; switching = true } },
                    onStop = { expanded = false; current?.let { targetId = it.id; timing = null; timingError = null; stopping = true } },
                    onFocus = { expanded = false; onEnterFocus() },
                )
            }
            if ((focus || expanded) && question != null && !checkInOpen) TextButton(onClick = { checkInOpen = true }) { Text("Check-in waiting") }
            if (!focus && expanded && planning) Text("Finish or cancel planning to enter Focus.")
            if (focus && unknown) {
                OutlinedButton(enabled = enabled, onClick = { describing = true }, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
                    Text("Choose activity", style = TimeboxTheme.type.button, color = colors.on)
                }
            }
            if ((focus || expanded) && current != null && plan != null && current.plannedBlockId != plan.id) {
                TextButton(enabled = enabled, onClick = { scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Switch, plan = plan) } }) {
                    Text("Planned now: ${plan.name ?: availableTypes.find { it.id == plan.taskTypeId }?.name} · Switch")
                }
            }
            if (focus || expanded) current?.plannedBlockId?.let { id ->
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
            if (state.offline || state.pending || state.snapshot == null) Text(if (state.offline) "Offline" + (if (state.pending) " · Unsynced" else "") else if (state.pending) "Unsynced" else "Connection required", color = colors.onVariant)
            if (state.error != null || state.pending) {
                Text(state.error ?: "Change not confirmed.", color = colors.onVariant)
                TextButton(enabled = !state.busy, onClick = { scope.launch(Dispatchers.IO) { if (state.pending) repository.retry() else repository.refresh() } }) { Text("Retry") }
            }
        }
    }
    if (focus) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
            FilledTonalButton(
                enabled = enabled && current != null,
                onClick = { current?.let { targetId = it.id; timing = null; timingError = null; switching = true } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.low, contentColor = colors.on),
            ) { Text("Switch activity", style = TimeboxTheme.type.button) }
            TextButton(onClick = { focusOptions = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Focus options", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
        }
    } else content()
    if (describing && unknown) ModalBottomSheet(onDismissRequest = { describing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Describe this activity", style = TimeboxTheme.type.screenTitle, color = colors.on)
            Text("What have you been doing?", style = TimeboxTheme.type.body, color = colors.onVariant)
            availableTypes.filter { it.name != "unspecified" }.forEach { type ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedType?.id == type.id, onClick = { selectedType = type }, enabled = enabled)
                    TextButton(onClick = { selectedType = type }, enabled = enabled) { Text(type.name, style = TimeboxTheme.type.body, color = colors.on) }
                }
            }
            if (availableTypes.none { it.name != "unspecified" }) Text("Add a Task Type in Types to describe this activity.", style = TimeboxTheme.type.body, color = colors.onVariant)
            HorizontalDivider(color = colors.hairline)
            Text("Apply to", style = TimeboxTheme.type.sectionTitle, color = colors.on)
            val started = java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(java.time.ZoneId.of(state.snapshot?.reportingTimezone ?: "UTC")).format(parseActivityInstant(current.startAt))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !describeFromNow, onClick = { describeFromNow = false }, enabled = enabled)
                Column { TextButton(onClick = { describeFromNow = false }, enabled = enabled) { Text("From the start", color = colors.on) }; Text("All this time, since $started", style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = describeFromNow, onClick = { describeFromNow = true }, enabled = enabled)
                Column { TextButton(onClick = { describeFromNow = true }, enabled = enabled) { Text("From now", color = colors.on) }; Text("Keep earlier time unspecified", style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
            }
            state.error?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error) }
            Button(enabled = enabled && selectedType != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                val id = current.id
                val typeId = selectedType!!.id
                val kind = if (describeFromNow) ActivityKind.Switch else ActivityKind.Describe
                scope.launch { if (withContext(Dispatchers.IO) { repository.command(kind, typeId, observedTargetId = id) }) describing = false }
            }) { Text("Apply activity") }
            TextButton(onClick = { describing = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
    if (focusOptions) ModalBottomSheet(onDismissRequest = { focusOptions = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Focus options", style = TimeboxTheme.type.screenTitle, color = colors.on)
            com.timebox.android.ui.focus.FocusWakeSettings()
            if (state.checkInPreferences.enabled && detectionAccess == com.timebox.android.checkin.DetectionAccess.Denied) {
                HorizontalDivider(color = colors.hairline)
                Text("Screen-off detection", style = TimeboxTheme.type.sectionTitle, color = colors.on)
                Text("Allow usage access for optional inactivity check-ins. Recording works without it.", style = TimeboxTheme.type.body, color = colors.onVariant)
                OutlinedButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Enable screen-off detection") }
            }
            TextButton(onClick = { focusOptions = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Done") }
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
    if (switching && !stopping) SwitchActivitySheet(
        currentActivity = current?.name?.takeIf { it.isNotBlank() } ?: current?.taskType?.name.orEmpty(),
        taskTypes = availableTypes, selectedType = selectedType, onTypeChange = { selectedType = it },
        name = name, onNameChange = { name = it }, timing = timing,
        onTimingChange = { timing = it; timingError = null }, now = now,
        zone = java.time.ZoneId.of(state.snapshot?.reportingTimezone ?: "UTC"),
        enabled = enabled, busy = state.busy, error = timingError,
        onDismiss = { switching = false },
        onConfirm = {
            scope.launch {
                try {
                    val at = timing?.resolve(java.time.ZoneId.of(state.snapshot?.reportingTimezone ?: "UTC"))
                    if (withContext(Dispatchers.IO) { repository.command(ActivityKind.Switch, selectedType!!.id, name, effectiveAt = at, observedTargetId = targetId) }) {
                        switching = false; selectedType = null; name = ""
                    } else timingError = repository.state.value.error
                } catch (error: Exception) { timingError = error.message }
            }
        },
    )
    if (stopping) ModalBottomSheet(onDismissRequest = { switching = false; stopping = false }) {
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
