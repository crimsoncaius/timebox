package com.timebox.android.ui.day

import com.timebox.android.data.primaryIdentity
import com.timebox.android.data.secondaryIdentity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.primaryIdentity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.Lane
import com.timebox.android.ui.components.LinkedTaskRow
import com.timebox.android.ui.components.SheetField
import com.timebox.android.ui.components.SheetHelp
import com.timebox.android.ui.components.SheetPrimary
import com.timebox.android.ui.components.SheetRowLink
import com.timebox.android.ui.components.SheetSecondary
import com.timebox.android.ui.components.SheetTextField
import com.timebox.android.ui.components.TimeboxSheet
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
    if (action == "notes") {
        CurrentActivityNotesSheet(actual, repository, onDismiss = { action = null })
        return
    }
    val started = parseActivityInstant(actual.startAt).atZone(zone)
    val clock = DateTimeFormatter.ofPattern(if (started.toLocalDate() == now.atZone(zone).toLocalDate()) "HH:mm" else "d MMM, HH:mm")
    val changed = name.trim() != actual.name.orEmpty() || note != actual.note.orEmpty() || typeId != actual.taskTypeId ||
        start.resolve(zone) != parseActivityInstant(actual.startAt)
    TimeboxSheet(
        lane = Lane.Actual,
        kicker = "Actual block · Recording",
        live = true,
        title = "${clock.format(started)} – Now",
        meta = elapsedDuration(Duration.between(started.toInstant(), now).toMinutes().coerceAtLeast(0)),
        onDismiss = onDismiss,
        onCancel = { editing = false; error = null },
        busy = busy,
        secondary = if (editing) SheetSecondary.Cancel
            else SheetSecondary.Action("Switch activity", { action = "switch"; timing = null; error = null }, enabled),
        primary = when {
            !editing -> if (enabled) SheetPrimary.Action("Stop tracking") { action = "stop"; timing = null; error = null }
                else SheetPrimary.Disabled("Saving…")
            busy -> SheetPrimary.Disabled("Saving…")
            !changed -> SheetPrimary.Disabled("No changes")
            else -> SheetPrimary.Action("Save changes") {
                submit({ repository.correct(ActivityKind.Edit, actual.id, start.resolve(zone).toString(),
                    taskTypeId = typeId, name = name.trim(), note = note, clearName = name.isBlank(), clearNote = note.isBlank(), runningOnly = true) }, { editing = false })
            }
        },
    ) {
        if (!editing) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = TimeboxTheme.type.sectionTitle.copy(fontSize = 20.sp), color = colors.on)
                actual.secondaryIdentity()?.let { Text(it, color = colors.onVariant, style = TimeboxTheme.type.bodySmall) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetRowLink("Edit details", ::edit, enabled = enabled)
                SheetRowLink(
                    "Notes", { action = "notes" }, enabled = enabled,
                    detail = if (actual.taskId != null) "Task Description"
                        else actual.note?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Add a note",
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { ActivityTimeField("Start", start, zone, compact = true, enabled = enabled) { start = it } }
                Column(
                    Modifier.weight(1f).clip(TimeboxShapes.field).border(1.dp, colors.outlineVariant, TimeboxShapes.field)
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) {
                    Text("End · recording continues", style = TimeboxTheme.type.bodySmall, color = colors.outline, maxLines = 1)
                    Text("Now", style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp), color = colors.onVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            SheetHelp("Reporting Time Zone: $zone")
            SheetTextField(name, { name = it.take(500) }, "Name", optional = true, placeholder = "Add a name", enabled = enabled, fieldModifier = Modifier.testTag("actual-name-input"))
            SheetField("Task type") {
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
            SheetTextField(note, { note = it }, "Note", optional = true, placeholder = "Add a note", enabled = enabled, singleLine = false, minLines = 2)
        }
        actual.task?.let { task ->
            SheetField("Battle Plan task") {
                LinkedTaskRow(task.title + if (task.status == "completed") "  ✓" else "",
                    if (task.status == "completed") "Completed · Recording time does not change Task Completion."
                    else "Recording time does not change Task Completion.",
                    onOpen = { onOpenLinkedTask(task.id) }, lane = Lane.Actual, enabled = enabled)
            }
        }
        error?.let { Text(it, color = colors.error, style = TimeboxTheme.type.bodySmall) }
    }
}
