package com.timebox.android.ui.day

import com.timebox.android.ui.elapsedDuration

import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.TaskType
import com.timebox.android.data.toModel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.remote.ActivityKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/** Actual-specific fields in the existing Day block sheet. */
@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
        contentColor = colors.on,
        shape = TimeboxShapes.sheet,
    ) {
        if (actual != null && actual.endAt != null) {
            val startAt = parseActivityInstant(actual.startAt)
            val endAt = parseActivityInstant(actual.endAt)
            val localStart = startAt.atZone(zone)
            val localEnd = endAt.atZone(zone)
            val clock = DateTimeFormatter.ofPattern("HH:mm")
            val dateTime = DateTimeFormatter.ofPattern("d MMM HH:mm")
            val format = if (localStart.toLocalDate() == localEnd.toLocalDate()) clock else dateTime
            Column(Modifier.fillMaxWidth().heightIn(max = 760.dp).imePadding()) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = colors.actualSurface, shape = TimeboxShapes.chip) {
                            Text("●  ACTUAL BLOCK", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = TimeboxTheme.type.laneLabel, color = colors.actual)
                        }
                        TextButton(enabled = !saving, onClick = onDismiss) { Text("Close") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        Text("${format.format(localStart)} – ${format.format(localEnd)}", Modifier.weight(1f),
                            style = TimeboxTheme.type.display)
                        Text(elapsedDuration(Duration.between(startAt, endAt).toMinutes()), style = TimeboxTheme.type.label, color = colors.actual)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { ActivityTimeField("Start", start, zone, compact = true, enabled = !saving, onChange = { start = it }) }
                        Box(Modifier.weight(1f)) { ActivityTimeField("End", end, zone, compact = true, enabled = !saving, onChange = { end = it }) }
                    }
                    val plan = actual.plannedBlockId?.let { planId ->
                        state.day?.blocks?.firstOrNull { it.lane == com.timebox.android.data.Lane.Planned && it.id == planId }
                    }
                    plan?.let {
                        val startMinute = localStart.hour * 60 + localStart.minute
                        val endMinute = localEnd.hour * 60 + localEnd.minute
                        val timesMatch = startMinute == it.startMinute && endMinute == it.endMinute
                        ActualPlanLink(
                            it,
                            caption = if (timesMatch) "From this plan · already recorded" else "From this plan · times differ",
                            onOpen = { onOpenPlanned(it.id) },
                        )
                    }
                    OutlinedTextField(name, { name = it.take(500) }, enabled = !saving,
                        label = { Text("Block Name (optional)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field, textStyle = TimeboxTheme.type.body)
                    Column {
                        Text("TASK TYPE", style = TimeboxTheme.type.kicker, color = colors.onVariant)
                        Spacer(Modifier.height(8.dp))
                        TaskTypePicker(
                            recommendationName = name, recommendationEnabled = !saving && actual.plannedBlockId == null,
                            taskTypes = taskTypes,
                            query = typeQuery,
                            onQueryChange = { if (!saving) typeQuery = it },
                            selectedTypeId = type,
                            onChoose = { if (!saving) { type = it.id; typeQuery = it.name } },
                            onCreate = createType,
                        )
                    }
                    OutlinedTextField(note, { note = it }, enabled = !saving, label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, shape = TimeboxShapes.field, textStyle = TimeboxTheme.type.body)
                    actual.task?.let { task ->
                        Surface(shape = TimeboxShapes.group, color = colors.low, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("LINKED BATTLE PLAN TASK", style = TimeboxTheme.type.kicker, color = colors.onVariant)
                                Text(task.title + if (task.status == "completed") "  ✓" else "", style = TimeboxTheme.type.label)
                                Text(if (task.status == "completed") "Completed · Recording time does not change Task Completion."
                                    else "Recording time does not change Task Completion.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                            }
                        }
                    }
                    error?.let { Text(it, color = colors.error, style = TimeboxTheme.type.bodySmall) }
                    TextButton(enabled = !saving, onClick = { deleting = true }) { Text("Delete", color = colors.error) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled = !saving, onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(enabled = !saving, onClick = save, modifier = Modifier.weight(1f)) { Text(if (saving) "Saving…" else "Save changes") }
                }
            }
        } else if (actual == null) {
            LogTimeForm(
                start = start, end = end, zone = zone, taskTypes = taskTypes, selectedTypeId = type,
                query = typeQuery, name = name, note = note, saving = saving, error = error,
                onStart = { start = it }, onEnd = { end = it },
                onQuery = { if (!saving) typeQuery = it },
                onChooseType = { if (!saving) { type = it.id; typeQuery = it.name } },
                onCreateType = createType, onName = { name = it.take(500) }, onNote = { note = it },
                onSave = save, onDismiss = onDismiss,
            )
        } else {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current Activity", style = TimeboxTheme.type.screenTitle)
                Text("Use Switch or Stop above the Day timeline to correct the Current Activity.")
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }

    if (deleting) AlertDialog(onDismissRequest = { if (!saving) deleting = false }, title = { Text("Delete Actual Block?") }, text = { Text("This time will be unrecorded. Adjacent activities stay unchanged.") },
        dismissButton = { TextButton(enabled = !saving, onClick = { deleting = false }) { Text("Cancel") } }, confirmButton = {
            TextButton(enabled = !saving, onClick = { scope.launch {
                saving = true
                try {
                    if (withContext(Dispatchers.IO) { repository.correct(ActivityKind.Delete, actual!!.id) }) onDismiss()
                    else error = repository.state.value.error
                } catch (cause: kotlinx.coroutines.CancellationException) {
                    throw cause
                } catch (cause: Exception) {
                    error = cause.message
                } finally {
                    saving = false
                    deleting = false
                }
            } }) { Text("Delete") }
        })
}
