package com.timebox.android.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var start by remember(actual.id) { mutableStateOf(ActivityTimeValue.from(parseActivityInstant(actual.startAt), zone)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<String?>(null) }
    var timing by remember { mutableStateOf<ActivityTimeValue?>(null) }
    var nextName by remember { mutableStateOf("") }
    var nextTypeId by remember { mutableStateOf<Int?>(null) }
    var choosingType by remember { mutableStateOf(false) }
    val types = state.snapshot?.taskTypes.orEmpty().map { it.toModel() }
    val enabled = !busy && !state.busy
    val title = actual.name?.takeIf { it.isNotBlank() } ?: actual.taskType.name.takeUnless { it == "unspecified" } ?: "Unnamed activity"
    fun edit() {
        name = actual.name.orEmpty(); note = actual.note.orEmpty(); typeId = actual.taskTypeId
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
        SwitchActivitySheet(title, types, types.find { it.id == nextTypeId }, { nextTypeId = it.id },
            nextName, { nextName = it }, timing, { timing = it; error = null }, now, zone,
            enabled, busy || state.busy, error, { if (!busy) { action = null; error = null } }, {
                nextTypeId?.let { id -> submit({ repository.command(ActivityKind.Switch, id, nextName,
                    effectiveAt = timing?.resolve(zone), observedTargetId = actual.id) }, onDismiss) }
            })
        return
    }
    if (action == "stop") {
        StopTrackingSheet(title, parseActivityInstant(actual.startAt), timing, { timing = it; error = null },
            now, zone, enabled, busy || state.busy, error, { if (!busy) { action = null; error = null } }, {
                submit({ repository.command(ActivityKind.Stop, effectiveAt = timing?.resolve(zone), observedTargetId = actual.id) }, onDismiss)
            })
        return
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg, contentColor = colors.on, shape = TimeboxShapes.sheet) {
        Column(Modifier.fillMaxWidth().imePadding().heightIn(max = 760.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("●  ACTUAL · ONGOING", Modifier.weight(1f), color = colors.actual, style = TimeboxTheme.type.laneLabel)
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Close", color = colors.onVariant) }
                }
                Text(title, style = TimeboxTheme.type.screenTitle)
                val started = parseActivityInstant(actual.startAt).atZone(zone)
                val clock = DateTimeFormatter.ofPattern(if (started.toLocalDate() == now.atZone(zone).toLocalDate()) "HH:mm" else "d MMM, HH:mm")
                val category = actual.taskType.name.takeUnless { it == "unspecified" }?.let { "$it · " }.orEmpty()
                Text("$category${clock.format(started)} → Now · ${Duration.between(started.toInstant(), now).toMinutes().coerceAtLeast(0)} min",
                    color = colors.actual, style = TimeboxTheme.type.body)
                if (!editing) {
                    actual.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.onVariant, style = TimeboxTheme.type.body) }
                    actual.task?.let { task -> TextButton(enabled = enabled, onClick = { onOpenLinkedTask(task.id) }, contentPadding = PaddingValues(0.dp)) {
                        Text("↗  ${task.title}", color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
                    } }
                    TextButton(enabled = enabled, onClick = ::edit) { Text("Edit details", color = colors.actual) }
                } else {
                    OutlinedTextField(name, { name = it.take(500) }, enabled = enabled, singleLine = true,
                        label = { Text("Block Name (optional)") }, modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field)
                    ActivityTimeField("Started", start, zone, compact = true, enabled = enabled) { start = it }
                    Text("Reporting Time Zone: $zone", color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
                    Box {
                        OutlinedButton(enabled = enabled, onClick = { choosingType = true }, modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field) {
                            Text("Task Type: ${types.find { it.id == typeId }?.name ?: actual.taskType.name}", Modifier.weight(1f)); Text("⌄")
                        }
                        DropdownMenu(choosingType, { choosingType = false }) {
                            types.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { typeId = item.id; choosingType = false }) }
                        }
                    }
                    OutlinedTextField(note, { note = it }, enabled = enabled, label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, shape = TimeboxShapes.field)
                }
                error?.let { Text(it, color = colors.error, style = TimeboxTheme.type.bodySmall) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editing) {
                    OutlinedButton(enabled = enabled, onClick = { editing = false; error = null }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(enabled = enabled, onClick = {
                        submit({ repository.correct(ActivityKind.Edit, actual.id, start.resolve(zone).toString(),
                            taskTypeId = typeId, name = name.trim(), note = note, clearName = name.isBlank(), clearNote = note.isBlank(), runningOnly = true) }, { editing = false })
                    }, modifier = Modifier.weight(1f)) { Text(if (busy) "Saving…" else "Save changes") }
                } else {
                    Button(enabled = enabled, onClick = { action = "switch"; timing = null; error = null }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.actual, contentColor = colors.bg)) { Text("Switch activity") }
                    OutlinedButton(enabled = enabled, onClick = { action = "stop"; timing = null; error = null }, modifier = Modifier.weight(1f)) { Text("Stop tracking", color = colors.on) }
                }
            }
        }
    }
}
