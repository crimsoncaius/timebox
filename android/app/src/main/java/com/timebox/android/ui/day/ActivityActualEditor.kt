package com.timebox.android.ui.day

import com.timebox.android.ui.components.HelperText
import com.timebox.android.ui.elapsedDuration

import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.TaskType
import com.timebox.android.data.toModel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.remote.ActivityKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.timebox.android.data.Lane
import com.timebox.android.ui.components.CardLink
import com.timebox.android.ui.components.LinkedTaskRow
import com.timebox.android.ui.components.RelatedCard
import com.timebox.android.ui.components.SheetDeleteRow
import com.timebox.android.ui.components.SheetField
import com.timebox.android.ui.components.SheetPrimary
import com.timebox.android.ui.components.SheetSecondary
import com.timebox.android.ui.components.SheetTextField
import com.timebox.android.ui.components.TimeboxDialog
import com.timebox.android.ui.components.TimeboxSheet
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/** Actual-specific fields in the existing Day block sheet. */
@Composable
fun ActivityActualEditor(state: DayUiState, onDismiss: () -> Unit,
                         repository: ActivityRepository = (LocalContext.current.applicationContext as TimeboxApplication).activityRepository,
                         onOpenLinkedTask: (Int) -> Unit = {},
                         onOpenPlanned: (Int) -> Unit = {}) {
    val snapshot by repository.state.collectAsState()
    val actual = snapshot.snapshot?.records?.find { it.id == state.selectedBlockId }
    val openedRunning = remember(state.selectedBlockId) { actual != null && actual.endAt == null }
    if (openedRunning) {
        if (actual != null) RunningActualSheet(actual, repository, onDismiss, onOpenLinkedTask)
        else LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val zone = ZoneId.of(snapshot.snapshot?.reportingTimezone ?: state.day?.timezone ?: "UTC")
    val initialStart = actual?.let { ActivityTimeValue.from(parseActivityInstant(it.startAt), zone) }
        ?: ActivityTimeValue(state.date.atStartOfDay().plusMinutes(state.sheetStart.toLong()).toString())
    val initialEnd = actual?.endAt?.let { ActivityTimeValue.from(parseActivityInstant(it), zone) }
        ?: ActivityTimeValue(state.date.atStartOfDay().plusMinutes(state.sheetEnd.toLong()).toString())
    var start by remember { mutableStateOf(initialStart) }; var end by remember { mutableStateOf(initialEnd) }
    var name by remember { mutableStateOf(actual?.name.orEmpty()) }; var note by remember { mutableStateOf(actual?.note.orEmpty()) }
    var type by remember { mutableStateOf(actual?.taskTypeId ?: state.draft?.taskTypeId ?: snapshot.snapshot?.taskTypes?.find { it.name == "unspecified" }?.id) }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val typeRepository = (LocalContext.current.applicationContext as TimeboxApplication).repository
    var createdTypes by remember { mutableStateOf(emptyList<TaskType>()) }
    var typeQuery by remember { mutableStateOf("") }
    val taskTypes = (snapshot.snapshot?.taskTypes.orEmpty().map { it.toModel() } + createdTypes).distinctBy { it.id }
    val createType: (String) -> Unit = { path ->
        if (!saving) scope.launch {
            saving = true
            error = null
            try {
                val created = typeRepository.createTaskType(path).getOrThrow()
                createdTypes = createdTypes + created
                type = created.id
                typeQuery = created.name
                repository.refresh()
            } catch (cause: kotlinx.coroutines.CancellationException) {
                throw cause
            } catch (cause: Exception) {
                error = cause.message ?: "Could not create Task Type."
            } finally {
                saving = false
            }
        }
    }
    val colors = TimeboxTheme.colors
    val save: () -> Unit = {
        scope.launch {
            saving = true
            error = null
            try {
                val a = start.resolve(zone)
                val b = end.resolve(zone)
                require(b > a) { "End must be after start." }
                val saved = withContext(Dispatchers.IO) {
                    repository.correct(if (actual == null) ActivityKind.Add else ActivityKind.Edit,
                        actual?.id, a.toString(), b.toString(), type, name.trim().ifBlank { null },
                        note.ifBlank { null }, taskId = actual?.taskId ?: state.draft?.taskId,
                        clearName = name.isBlank(), clearNote = note.isBlank())
                }
                if (saved) onDismiss() else error = repository.state.value.error
            } catch (cause: kotlinx.coroutines.CancellationException) {
                throw cause
            } catch (cause: Exception) {
                error = cause.message
            } finally {
                saving = false
            }
        }
    }
    if (actual == null) {
        LogTimeForm(
            start = start, end = end, zone = zone, taskTypes = taskTypes, selectedTypeId = type,
            query = typeQuery, name = name, note = note, saving = saving, error = error,
            onStart = { start = it }, onEnd = { end = it },
            onQuery = { if (!saving) typeQuery = it },
            onChooseType = { if (!saving) { type = it.id; typeQuery = it.name } },
            onCreateType = createType, onName = { name = it.take(500) }, onNote = { note = it },
            onSave = save, onDismiss = onDismiss,
        )
        return
    }
    if (actual.endAt == null) {
        TimeboxSheet(
            lane = Lane.Actual, kicker = "Actual block", title = "Current Activity",
            onDismiss = onDismiss, secondary = SheetSecondary.Cancel,
            primary = SheetPrimary.Disabled("Use Switch or Stop"),
        ) { HelperText("Use Switch or Stop above the Day timeline to correct the Current Activity.") }
        return
    }
    val startAt = parseActivityInstant(actual.startAt)
    val endAt = parseActivityInstant(actual.endAt)
    val localStart = startAt.atZone(zone)
    val localEnd = endAt.atZone(zone)
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val dateTime = DateTimeFormatter.ofPattern("d MMM HH:mm")
    val format = if (localStart.toLocalDate() == localEnd.toLocalDate()) clock else dateTime
    val range = "${format.format(localStart)} – ${format.format(localEnd)}"
    val changed = runCatching { start.resolve(zone) != startAt || end.resolve(zone) != endAt }.getOrDefault(true) ||
        name.trim() != actual.name.orEmpty() || note != actual.note.orEmpty() || type != actual.taskTypeId
    TimeboxSheet(
        lane = Lane.Actual,
        kicker = "Actual block",
        title = range,
        meta = elapsedDuration(Duration.between(startAt, endAt).toMinutes()),
        onDismiss = onDismiss,
        busy = saving,
        primary = when {
            saving -> SheetPrimary.Disabled("Saving…")
            !changed -> SheetPrimary.Disabled("No changes")
            else -> SheetPrimary.Action("Save changes", save)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { ActivityTimeField("Start", start, zone, compact = true, enabled = !saving, onChange = { start = it }) }
            Box(Modifier.weight(1f)) { ActivityTimeField("End", end, zone, compact = true, enabled = !saving, onChange = { end = it }) }
        }
        val plan = actual.plannedBlockId?.let { planId ->
            state.day?.blocks?.firstOrNull { it.lane == Lane.Planned && it.id == planId }
        }
        plan?.let {
            val startMinute = localStart.hour * 60 + localStart.minute
            val endMinute = localEnd.hour * 60 + localEnd.minute
            val timesMatch = startMinute == it.startMinute && endMinute == it.endMinute
            RelatedCard(
                lane = Lane.Planned,
                caption = if (timesMatch) "From this plan · already recorded" else "From this plan · times differ",
                trailing = elapsedDuration(it.durationMinutes.toLong()),
                title = "${hhmm(it.startMinute)} – ${hhmm(it.endMinute)}",
            ) { CardLink("Open planned block", Lane.Planned, { onOpenPlanned(it.id) }) }
        }
        SheetTextField(name, { name = it.take(500) }, "Name", optional = true, placeholder = "Add a name", enabled = !saving, fieldModifier = Modifier.testTag("actual-name-input"))
        SheetField("Task type") {
            TaskTypePicker(
                recommendationName = name, recommendationLinkedTaskName = actual.task?.title, recommendationEnabled = !saving && actual.plannedBlockId == null,
                taskTypes = taskTypes,
                query = typeQuery,
                onQueryChange = { if (!saving) typeQuery = it },
                selectedTypeId = type,
                onChoose = { if (!saving) { type = it.id; typeQuery = it.name } },
                onCreate = createType,
            )
        }
        SheetTextField(note, { note = it }, "Note", optional = true, placeholder = "Add a note", enabled = !saving, singleLine = false, minLines = 2)
        actual.task?.let { task ->
            SheetField("Battle Plan task") {
                LinkedTaskRow(task.title + if (task.status == "completed") "  ✓" else "",
                    if (task.status == "completed") "Completed · Recording time does not change Task Completion."
                    else "Recording time does not change Task Completion.",
                    onOpen = { onOpenLinkedTask(task.id) }, lane = Lane.Actual, enabled = !saving)
            }
        }
        error?.let { Text(it, color = colors.error, style = TimeboxTheme.type.bodySmall) }
        SheetDeleteRow(onClick = { deleting = true }, enabled = !saving)
    }

    if (deleting) TimeboxDialog(
        lane = Lane.Actual,
        kicker = "Actual block",
        title = "Delete actual block $range?",
        body = "This time will be unrecorded. Adjacent activities stay unchanged.",
        onDismiss = { deleting = false },
        destructive = true,
        busy = saving,
        primary = SheetPrimary.Action("Delete block") {
            scope.launch {
                saving = true
                try {
                    if (withContext(Dispatchers.IO) { repository.correct(ActivityKind.Delete, actual.id) }) onDismiss()
                    else error = repository.state.value.error
                } catch (cause: kotlinx.coroutines.CancellationException) {
                    throw cause
                } catch (cause: Exception) {
                    error = cause.message
                } finally {
                    saving = false
                    deleting = false
                }
            }
        },
    )
}
