package com.timebox.android.ui.day

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
import java.time.Instant
import java.time.ZoneId

/** Actual-specific fields in the existing Day block sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityActualEditor(state: DayUiState, onDismiss: () -> Unit,
                         repository: ActivityRepository = (LocalContext.current.applicationContext as TimeboxApplication).activityRepository) {
    val snapshot by repository.state.collectAsState()
    val actual = snapshot.snapshot?.records?.find { it.id == state.selectedBlockId }
    val zone = ZoneId.of(snapshot.snapshot?.reportingTimezone ?: state.day?.timezone ?: "UTC")
    val initialStart = actual?.let { ActivityTimeValue.from(Instant.parse(it.startAt), zone) }
        ?: ActivityTimeValue(state.date.atStartOfDay().plusMinutes(state.sheetStart.toLong()).toString())
    val initialEnd = actual?.endAt?.let { ActivityTimeValue.from(Instant.parse(it), zone) }
        ?: ActivityTimeValue(state.date.atStartOfDay().plusMinutes(state.sheetEnd.toLong()).toString())
    var start by remember { mutableStateOf(initialStart) }; var end by remember { mutableStateOf(initialEnd) }
    var name by remember { mutableStateOf(actual?.name.orEmpty()) }; var note by remember { mutableStateOf(actual?.note.orEmpty()) }
    var type by remember { mutableStateOf(actual?.taskTypeId ?: state.draft?.taskTypeId ?: snapshot.snapshot?.taskTypes?.find { it.name == "unspecified" }?.id) }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                Button(enabled = !saving, onClick = {
                    scope.launch {
                        saving = true; error = null
                        try {
                            val a = start.resolve(zone); val b = end.resolve(zone)
                            val saved = withContext(Dispatchers.IO) { repository.correct(if (actual == null) ActivityKind.Add else ActivityKind.Edit,
                                actual?.id, a.toString(), b.toString(), type, name.trim().ifBlank { null }, note.ifBlank { null }, taskId = actual?.taskId ?: state.draft?.taskId,
                                clearName = name.isBlank(), clearNote = note.isBlank()) }
                            if (saved) onDismiss() else error = repository.state.value.error
                        } catch (cause: Exception) { error = cause.message } finally { saving = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (actual == null) "Create block" else "Save changes") }
                if (actual != null) TextButton(enabled = !saving, onClick = { deleting = true }) { Text("Delete") }
            }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete Actual Block?") }, text = { Text("This time will be unrecorded. Adjacent activities stay unchanged.") },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }, confirmButton = {
            TextButton(onClick = { scope.launch {
                saving = true
                if (withContext(Dispatchers.IO) { repository.correct(ActivityKind.Delete, actual!!.id) }) onDismiss() else error = repository.state.value.error
                saving = false; deleting = false
            } }) { Text("Delete") }
        })
}
