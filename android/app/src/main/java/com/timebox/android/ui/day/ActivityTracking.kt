package com.timebox.android.ui.day

import com.timebox.android.ui.elapsedDuration
import com.timebox.android.ui.elapsedDurationSeconds

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
import com.timebox.android.data.primaryIdentity
import com.timebox.android.data.remote.ActivityKind
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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
    controlsVisible: Boolean = true,
    createTaskType: suspend (String) -> Result<TaskType> = (LocalContext.current.applicationContext as TimeboxApplication).repository::createTaskType,
) {
    val state by repository.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChanged)
    var switching by remember { mutableStateOf(false) }
    var reviewingLegacy by remember { mutableStateOf(false) }
    var reviewingRejected by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var startSaving by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    var stopSaving by remember { mutableStateOf(false) }
    var notesTargetId by remember { mutableStateOf<Int?>(null) }
    var targetId by remember { mutableStateOf<Int?>(null) }
    var timing by remember { mutableStateOf<ActivityTimeValue?>(null) }
    var typeError by remember { mutableStateOf<String?>(null) }
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
    var focusOptions by remember { mutableStateOf(false) }
    val question = state.snapshot?.checkIn?.question
    var checkInOpen by remember(question?.id) { mutableStateOf(question != null && !repository.checkInDismissed(question.id)) }
    val checkIns = (LocalContext.current.applicationContext as? TimeboxApplication)?.checkIns
    val detectionAccess = checkIns?.access?.collectAsState()?.value
    val context = LocalContext.current
    var createdTypes by remember { mutableStateOf(emptyList<TaskType>()) }
    var typeQuery by remember { mutableStateOf("") }
    val requestedQuestion = checkIns?.openQuestion?.collectAsState()?.value
    LaunchedEffect(requestedQuestion, question?.id) {
        if (requestedQuestion != null && requestedQuestion == question?.id) { checkInOpen = true; checkIns?.consumeOpen() }
    }
    fun dismissCheckIn() { checkInOpen = false; question?.let { scope.launch { repository.dismissCheckIn(it.id) } } }

    LaunchedEffect(current?.id) { selectedType = null; typeQuery = ""; typeError = null }
    LaunchedEffect(starting, switching) { typeError = null }
    val plan = repository.currentPlan()
    // Without a covering Planned Block, starting waits for an explicit Task Type.
    fun start() {
        if (plan != null) scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Start) }
        else { selectedType = null; name = ""; typeQuery = ""; startError = null; starting = true }
    }
    val enabled = !state.busy && state.snapshot != null
    val statusFlags = StatusFlags(
        offline = state.offline, pending = state.pending, busy = state.busy,
        hasSnapshot = state.snapshot != null, error = state.error,
    )
    val availableTypes = ((state.snapshot?.taskTypes?.takeIf { it.isNotEmpty() }?.map { TaskType(it.id, it.name, 0) } ?: taskTypes) + createdTypes).distinctBy { it.id }
    val createType: (String) -> Unit = { path ->
        typeError = null
        scope.launch {
            try {
                val created = createTaskType(path).getOrThrow()
                createdTypes = createdTypes + created
                selectedType = created
                typeQuery = created.name
                withContext(Dispatchers.IO) { repository.refresh() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (cause: Exception) {
                typeError = cause.message ?: "Could not create Task Type."
            }
        }
    }
    var expanded by remember(current?.id) { mutableStateOf(false) }
    BackHandler(!focus && expanded && !switching && !stopping && !checkInOpen) { expanded = false }
    val colors = TimeboxTheme.colors
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = if (focus) 0.dp else 20.dp)) {
            if (focus && current != null) {
                Spacer(Modifier.height(36.dp))
                if (current.taskType.name != "unspecified") Text(current.taskType.name.uppercase(), style = TimeboxTheme.type.kicker, color = colors.actual)
                Spacer(Modifier.height(16.dp))
                Text(current.name?.takeIf { it.isNotBlank() } ?: current.taskType.name,
                    style = TimeboxTheme.type.display, color = colors.on)
                val elapsed: @Composable () -> Unit = {
                    Spacer(Modifier.height(28.dp))
                    HorizontalDivider(color = colors.hairline)
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val seconds = Duration.between(parseActivityInstant(current.startAt), now).seconds.coerceAtLeast(0)
                        Text(elapsedDurationSeconds(seconds), style = TimeboxTheme.type.display, color = colors.on)
                        Text("elapsed", style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
                focusTask(elapsed)
            }
            if (!focus) {
                CurrentActivityControl(
                    activity = current?.name?.takeIf { it.isNotBlank() } ?: current?.taskType?.name.orEmpty(),
                    elapsed = current?.let { elapsedDuration(Duration.between(parseActivityInstant(it.startAt), now).toMinutes().coerceAtLeast(0)) }.orEmpty(),
                    running = current != null, expanded = expanded, enabled = enabled, focusEnabled = enabled && !planning,
                    onToggle = { expanded = !expanded },
                    onStart = ::start,
                    onNotes = { current?.let { notesTargetId = it.id } },
                    onSwitch = { expanded = false; current?.let { targetId = it.id; timing = null; timingError = null; switching = true } },
                    onStop = { expanded = false; current?.let { targetId = it.id; timing = null; timingError = null; stopping = true } },
                    onFocus = { expanded = false; onEnterFocus() },
                )
            }
            ActivityTrackingStatusChip(
                flags = statusFlags,
                retryDisabled = statusFlags.busy,
                onRetry = { scope.launch(Dispatchers.IO) { if (state.pending) repository.retry() else repository.refresh() } },
                modifier = Modifier.padding(top = if (focus) 12.dp else 4.dp, bottom = 4.dp),
            )
            if ((focus || expanded) && question != null && !checkInOpen) TextButton(onClick = { checkInOpen = true }) { Text("Check-in waiting") }
            if (!focus && expanded && planning) Text("Finish or cancel planning to enter Focus.")
            if ((focus || expanded) && current != null && plan != null && current.plannedBlockId != plan.id) {
                PlannedBlockSuggestion(
                    name = plan.name ?: availableTypes.find { it.id == plan.taskTypeId }?.name ?: "Planned Block",
                    timing = plannedSuggestionTiming(plan, now, runCatching { ZoneId.of(state.snapshot!!.reportingTimezone) }.getOrDefault(ZoneId.systemDefault())),
                    enabled = enabled, focus = focus,
                    onSwitch = { scope.launch(Dispatchers.IO) { repository.command(ActivityKind.Switch, plan = plan) } },
                )
            }
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
        }
    }
    if (focus) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
            OutlinedButton(
                enabled = enabled && current != null,
                onClick = { current?.let { notesTargetId = it.id } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
            ) { Text("Notes", style = TimeboxTheme.type.button, color = colors.on) }
            FilledTonalButton(
                enabled = enabled && current != null,
                onClick = { current?.let { targetId = it.id; timing = null; timingError = null; switching = true } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.low, contentColor = colors.on),
            ) { Text("Switch activity", style = TimeboxTheme.type.button) }
            TextButton(onClick = { focusOptions = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Focus options", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
        }
    } else if (controlsVisible) content()
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
    val notesTarget = state.snapshot?.records?.find { it.id == notesTargetId }
        ?: current?.takeIf { it.id == notesTargetId }
    if (notesTargetId != null && notesTarget != null) CurrentActivityNotesSheet(
        actual = notesTarget,
        activityRepository = repository,
        onDismiss = { notesTargetId = null },
    )
    if (starting && current == null) StartTrackingSheet(
        taskTypes = availableTypes, selectedType = selectedType, onTypeChange = { selectedType = it; typeQuery = it.name; typeError = null },
        name = name, onNameChange = { name = it },
        enabled = enabled, busy = startSaving || state.busy, error = startError,
        onDismiss = { if (!startSaving) starting = false },
        onConfirm = {
            if (!startSaving) {
                startSaving = true
                val typeId = selectedType!!.id
                scope.launch {
                    try {
                        if (withContext(Dispatchers.IO) { repository.command(ActivityKind.Start, typeId, name.trim().ifBlank { null }) }) {
                            starting = false; selectedType = null; name = ""; typeQuery = ""
                        } else startError = "Could not start tracking."
                    } finally { startSaving = false }
                }
            }
        },
        onCreateType = createType, typeError = typeError,
        onTypeQueryChange = { typeError = null },
    )
    val switchTarget = state.snapshot?.records?.find { it.id == targetId } ?: current?.takeIf { it.id == targetId }
    if (switching && !stopping) SwitchActivitySheet(
        currentActivity = switchTarget?.name?.takeIf { it.isNotBlank() } ?: switchTarget?.taskType?.name.orEmpty(),
        currentId = targetId ?: 0, start = switchTarget?.startAt?.let(::parseActivityInstant) ?: now,
        loadPlanTitles = { date ->
            (context.applicationContext as TimeboxApplication).repository.getDayPreview(date).getOrNull()
                ?.blocks?.filter { it.lane == com.timebox.android.data.Lane.Planned }
                ?.associate { it.id to it.primaryIdentity() }.orEmpty()
        },
        records = state.snapshot?.records.orEmpty(), plans = state.snapshot?.plans.orEmpty(),
        taskTypes = availableTypes, selectedType = selectedType, onTypeChange = { selectedType = it; typeQuery = it.name; typeError = null },
        name = name, onNameChange = { name = it }, timing = timing,
        onTimingChange = { timing = it; timingError = null }, now = now,
        zone = java.time.ZoneId.of(state.snapshot?.reportingTimezone ?: "UTC"),
        enabled = enabled && switchTarget != null && current?.id == targetId, busy = state.busy,
        error = if (current?.id != targetId) "The current activity changed. Close this sheet and review it before switching." else timingError,
        onDismiss = { switching = false },
        onConfirm = {
            scope.launch {
                try {
                    val at = timing?.resolve(java.time.ZoneId.of(state.snapshot?.reportingTimezone ?: "UTC"))
                    if (withContext(Dispatchers.IO) { repository.command(ActivityKind.Switch, selectedType!!.id, name, effectiveAt = at, observedTargetId = targetId) }) {
                        switching = false; selectedType = null; name = ""; typeQuery = ""
                    } else timingError = repository.state.value.error
                } catch (error: Exception) { timingError = error.message }
            }
        },
        onCreateType = createType, typeError = typeError,
        onTypeQueryChange = { typeError = null },
    )
    val stopTarget = state.snapshot?.records?.find { it.id == targetId } ?: current?.takeIf { it.id == targetId }
    if (stopping && stopTarget != null) StopTrackingSheet(
        currentId = stopTarget.id, records = state.snapshot?.records.orEmpty(),
        plans = state.snapshot?.plans.orEmpty(), taskTypes = availableTypes,
        loadPlanTitles = { date ->
            (context.applicationContext as TimeboxApplication).repository.getDayPreview(date).getOrNull()
                ?.blocks?.filter { it.lane == com.timebox.android.data.Lane.Planned }
                ?.associate { it.id to it.primaryIdentity() }.orEmpty()
        },
        activity = stopTarget.name?.takeIf { it.isNotBlank() } ?: stopTarget.taskType.name,
        start = parseActivityInstant(stopTarget.startAt), timing = timing,
        onTimingChange = { timing = it; timingError = null }, now = now,
        zone = java.time.ZoneId.of(state.snapshot?.reportingTimezone ?: "UTC"),
        enabled = enabled, busy = stopSaving || state.busy, error = timingError,
        onDismiss = { stopping = false },
        onConfirm = {
            if (!stopSaving) {
                stopSaving = true
                scope.launch {
                    try {
                        val at = timing?.resolve(java.time.ZoneId.of(state.snapshot?.reportingTimezone ?: "UTC"))
                        if (withContext(Dispatchers.IO) { repository.command(ActivityKind.Stop, effectiveAt = at, observedTargetId = targetId) }) {
                            stopping = false
                        } else timingError = repository.state.value.error
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        timingError = error.message
                    } finally {
                        stopSaving = false
                    }
                }
            }
        },
    )
}
