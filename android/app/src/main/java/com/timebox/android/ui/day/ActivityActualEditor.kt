package com.timebox.android.ui.day

import com.timebox.android.data.parseActivityInstant

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
                         repository: ActivityRepository = (LocalContext.current.applicationContext as TimeboxApplication).activityRepository) {
    val snapshot by repository.state.collectAsState()
    val actual = snapshot.snapshot?.records?.find { it.id == state.selectedBlockId }
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
    val colors = TimeboxTheme.colors
    val save: () -> Unit = {
        scope.launch {
            saving = true
            error = null
            try {
                val a = start.resolve(zone)
                val b = end.resolve(zone)
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
        containerColor = if (actual != null) colors.bg else MaterialTheme.colorScheme.surface,
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
            var choosingType by remember { mutableStateOf(false) }
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
                        Text("${Duration.between(startAt, endAt).toMinutes()} min", style = TimeboxTheme.type.label, color = colors.actual)
                    }
                    OutlinedTextField(name, { name = it.take(500) }, enabled = !saving,
                        label = { Text("Block Name (optional)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field, textStyle = TimeboxTheme.type.body)
                    Box {
                        OutlinedButton(enabled = !saving, onClick = { choosingType = true },
                            modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field) {
                            Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
                                Text("TASK TYPE", style = TimeboxTheme.type.kicker)
                                Text(snapshot.snapshot?.taskTypes?.find { it.id == type }?.name ?: actual.taskType.name,
                                    style = TimeboxTheme.type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text("⌄")
                        }
                        DropdownMenu(expanded = choosingType, onDismissRequest = { choosingType = false }) {
                            snapshot.snapshot?.taskTypes.orEmpty().forEach { item ->
                                DropdownMenuItem(text = { Text(item.name) }, onClick = { type = item.id; choosingType = false })
                            }
                        }
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
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (actual == null) "New Actual Block" else "Actual Block", style = MaterialTheme.typography.titleLarge)
                Text("Reporting Time Zone: $zone")
                if (actual != null && actual.endAt == null) Text("Use Switch or Stop above the Day timeline to correct the Current Activity.") else {
                    ActivityTimeField("Start", start, zone) { start = it }
                    ActivityTimeField("End", end, zone) { end = it }
                    Text("Task Type")
                    snapshot.snapshot?.taskTypes.orEmpty().forEach { item ->
                        Row { RadioButton(type == item.id, { type = item.id }); TextButton(onClick = { type = item.id }) { Text(item.name) } }
                    }
                    OutlinedTextField(name, { name = it.take(500) }, label = { Text("Block Name (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                    actual?.task?.let { Text("Task: ${it.title} · Task Completion stays independent.") }
                    error?.let { Text(it) }
                    Button(enabled = !saving, onClick = save, modifier = Modifier.fillMaxWidth()) { Text(if (actual == null) "Create block" else "Save changes") }
                    if (actual != null) TextButton(enabled = !saving, onClick = { deleting = true }) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
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
