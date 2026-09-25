package com.timebox.android.ui.day

import com.timebox.android.data.primaryIdentity
import com.timebox.android.data.secondaryIdentity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.primaryIdentity
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.toModel
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.ActivityKind
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.elapsedDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The running Actual's compact summary; corrections retain its open end. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunningActualSheet(actual: ActualBlockDto, repository: ActivityRepository,
                                onDismiss: () -> Unit, onOpenLinkedTask: (Int) -> Unit) {
    val state by repository.state.collectAsState()
    LaunchedEffect(state.snapshot?.current?.id, actual.endAt) {
        if (state.snapshot?.current?.id != actual.id || actual.endAt != null) onDismiss()
    }
    val context = LocalContext.current
    val loadPlanTitles: suspend (java.time.LocalDate) -> Map<Int, String> = { date ->
        (context.applicationContext as TimeboxApplication).repository.getDayPreview(date).getOrNull()
            ?.blocks?.filter { it.lane == com.timebox.android.data.Lane.Planned }
            ?.associate { it.id to it.primaryIdentity() }.orEmpty()
    }
    val colors = TimeboxTheme.colors
    val zone = ZoneId.of(state.snapshot?.reportingTimezone ?: "UTC")
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(repository.now()) }
    LaunchedEffect(repository, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = repository.now(); delay(1000) }
        }
    }
    var editing by remember(actual.id) { mutableStateOf(false) }
    var name by remember(actual.id) { mutableStateOf(actual.name.orEmpty()) }
    var note by remember(actual.id) { mutableStateOf(actual.note.orEmpty()) }
    var typeId by remember(actual.id) { mutableStateOf(actual.taskTypeId) }
    var typeQuery by remember(actual.id) { mutableStateOf(actual.taskType.name.takeUnless { it == "unspecified" }.orEmpty()) }
    var start by remember(actual.id) { mutableStateOf(ActivityTimeValue.from(parseActivityInstant(actual.startAt), zone)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<String?>(null) }
    var timing by remember { mutableStateOf<ActivityTimeValue?>(null) }
    var nextName by remember { mutableStateOf("") }
    var nextTypeId by remember { mutableStateOf<Int?>(null) }
    var createdTypes by remember { mutableStateOf(emptyList<com.timebox.android.data.TaskType>()) }
    val typeRepository = (context.applicationContext as TimeboxApplication).repository
    val types = (state.snapshot?.taskTypes.orEmpty().map { it.toModel() } + createdTypes).distinctBy { it.id }
    val enabled = !busy && !state.busy
    val title = actual.primaryIdentity()
    val createType: (String) -> Unit = { path ->
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    val created = typeRepository.createTaskType(path).getOrThrow()
                    createdTypes = createdTypes + created
                    typeId = created.id
                    typeQuery = created.name
                    nextTypeId = created.id
                    repository.refresh()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (cause: Exception) {
                    error = cause.message ?: "Could not create Task Type."
                } finally {
                    busy = false
                }
            }
        }
    }
    fun edit() {
        name = actual.name.orEmpty(); note = actual.note.orEmpty(); typeId = actual.taskTypeId
        typeQuery = actual.taskType.name.takeUnless { it == "unspecified" }.orEmpty()
        start = ActivityTimeValue.from(parseActivityInstant(actual.startAt), zone)
        error = null; editing = true
    }
    fun submit(save: suspend () -> Boolean, done: () -> Unit) {
        if (!enabled) return
        busy = true; error = null
        scope.launch {
            try {
                if (withContext(Dispatchers.IO) { save() }) done()
                else error = repository.state.value.error ?: "Could not save the change."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (cause: Exception) { error = cause.message ?: "Could not save the change."
            } finally { busy = false }
        }
    }
    if (action == "switch") {
        SwitchActivitySheet(title, actual.id, parseActivityInstant(actual.startAt),
            state.snapshot?.records.orEmpty(), state.snapshot?.plans.orEmpty(), types, types.find { it.id == nextTypeId }, { nextTypeId = it.id },
            nextName, { nextName = it }, timing, { timing = it; error = null }, now, zone,
            enabled, busy || state.busy, error, { if (!busy) { action = null; error = null } }, {
                nextTypeId?.let { id -> submit({ repository.command(ActivityKind.Switch, id, nextName,
                    effectiveAt = timing?.resolve(zone), observedTargetId = actual.id) }, onDismiss) }
            }, onCreateType = createType, loadPlanTitles = loadPlanTitles, allowHistory = state.snapshot?.switchHistoryReady == true)
        return
    }
    if (action == "stop") {
        StopTrackingSheet(actual.id, state.snapshot?.records.orEmpty(), state.snapshot?.plans.orEmpty(), types, title, parseActivityInstant(actual.startAt), timing, { timing = it; error = null },
            now, zone, enabled, busy || state.busy, error, { if (!busy) { action = null; error = null } }, {
                submit({ repository.command(ActivityKind.Stop, effectiveAt = timing?.resolve(zone), observedTargetId = actual.id) }, onDismiss)
            }, loadPlanTitles = loadPlanTitles)
        return
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg, contentColor = colors.on, shape = TimeboxShapes.sheet) {
        Column(Modifier.fillMaxWidth().imePadding().heightIn(max = 760.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = colors.actualSurface, shape = TimeboxShapes.chip) {
                        Text("●  ACTUAL BLOCK", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = colors.actual, style = TimeboxTheme.type.laneLabel)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Recording", color = colors.actual, style = TimeboxTheme.type.bodySmall)
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Close", color = colors.onVariant) }
                }
                val started = parseActivityInstant(actual.startAt).atZone(zone)
                val clock = DateTimeFormatter.ofPattern(if (started.toLocalDate() == now.atZone(zone).toLocalDate()) "HH:mm" else "d MMM, HH:mm")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${clock.format(started)} – Now", Modifier.weight(1f), style = TimeboxTheme.type.display)
                    Text(elapsedDuration(Duration.between(started.toInstant(), now).toMinutes().coerceAtLeast(0)),
                        color = colors.actual, style = TimeboxTheme.type.label)
                }
                if (!editing) {
                    Text(title, style = TimeboxTheme.type.label)
                    actual.secondaryIdentity()?.let {
                        Text(it, color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
                    }
                    actual.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.onVariant, style = TimeboxTheme.type.body) }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { ActivityTimeField("Start", start, zone, compact = true, enabled = enabled) { start = it } }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("End", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                            Text("Now", style = TimeboxTheme.type.screenTitle, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            Text("Recording continues", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                        }
                    }
                    Text("Reporting Time Zone: $zone", color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
                    OutlinedTextField(name, { name = it.take(500) }, enabled = enabled, singleLine = true,
                        label = { Text("Block Name (optional)") }, modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field, textStyle = TimeboxTheme.type.body)
                    Column {
                        Text("TASK TYPE", style = TimeboxTheme.type.kicker, color = colors.onVariant)
                        Spacer(Modifier.height(8.dp))
                        TaskTypePicker(
                        recommendationName = name, recommendationLinkedTaskName = actual.task?.title, recommendationEnabled = enabled && actual.plannedBlockId == null,
                            taskTypes = types,
                            query = typeQuery,
                            onQueryChange = { if (enabled) typeQuery = it },
                            selectedTypeId = typeId,
                            onChoose = { if (enabled) { typeId = it.id; typeQuery = it.name } },
                            onCreate = createType,
                        )
                    }
                    OutlinedTextField(note, { note = it }, enabled = enabled, label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, shape = TimeboxShapes.field, textStyle = TimeboxTheme.type.body)
                }
                actual.task?.let { task ->
                    Surface(shape = TimeboxShapes.group, color = colors.low, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("LINKED BATTLE PLAN TASK", style = TimeboxTheme.type.kicker, color = colors.onVariant)
                            TextButton(enabled = enabled, onClick = { onOpenLinkedTask(task.id) }, contentPadding = PaddingValues(0.dp)) {
                                Text("↗  ${task.title}" + if (task.status == "completed") "  ✓" else "", style = TimeboxTheme.type.label, color = colors.on)
                            }
                            Text(if (task.status == "completed") "Completed · Recording time does not change Task Completion."
                                else "Recording time does not change Task Completion.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                        }
                    }
                }
                if (!editing) TextButton(enabled = enabled, onClick = ::edit) { Text("Edit details") }
                error?.let { Text(it, color = colors.error, style = TimeboxTheme.type.bodySmall) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.hairline)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editing) {
                    OutlinedButton(enabled = enabled, onClick = { editing = false; error = null }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(enabled = enabled, onClick = {
                        submit({ repository.correct(ActivityKind.Edit, actual.id, start.resolve(zone).toString(),
                            taskTypeId = typeId, name = name.trim(), note = note, clearName = name.isBlank(), clearNote = note.isBlank(), runningOnly = true) }, { editing = false })
                    }, modifier = Modifier.weight(1f)) { Text(if (busy) "Saving…" else "Save changes") }
                } else {
                    OutlinedButton(enabled = enabled, onClick = { action = "switch"; timing = null; error = null }, modifier = Modifier.weight(1f)) { Text("Switch activity") }
                    Button(enabled = enabled, onClick = { action = "stop"; timing = null; error = null }, modifier = Modifier.weight(1f)) { Text("Stop tracking") }
                }
            }
        }
    }
}
