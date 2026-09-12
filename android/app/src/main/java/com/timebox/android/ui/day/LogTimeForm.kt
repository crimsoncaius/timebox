package com.timebox.android.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timebox.android.data.TaskType
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.ZoneId

/** Past-time entry; recording time never implies Task Completion. */
@Composable
internal fun LogTimeForm(
    start: ActivityTimeValue, end: ActivityTimeValue, zone: ZoneId,
    taskTypes: List<TaskType>, selectedTypeId: Int?, query: String,
    name: String, note: String, saving: Boolean, error: String?,
    onStart: (ActivityTimeValue) -> Unit, onEnd: (ActivityTimeValue) -> Unit,
    onQuery: (String) -> Unit, onChooseType: (TaskType) -> Unit, onCreateType: (String) -> Unit,
    onName: (String) -> Unit, onNote: (String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    var detailsExpanded by remember { mutableStateOf(name.isNotBlank() || note.isNotBlank()) }
    val range = runCatching {
        val duration = Duration.between(start.resolve(zone), end.resolve(zone))
        require(!duration.isNegative && !duration.isZero) { "End must be after start." }
        duration.toMinutes()
    }
    val minutes = range.getOrNull()
    val duration = minutes?.let { if (it >= 60) "${it / 60} hr" + if (it % 60 > 0) " ${it % 60} min" else "" else "$it min" }
    Column(Modifier.fillMaxWidth().imePadding()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
            Text("Log time", style = TimeboxTheme.type.screenTitle)
            Text("Reporting Time Zone: $zone", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            Surface(color = colors.low, shape = TimeboxShapes.group) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { ActivityTimeField("Start", start, zone, compact = true, enabled = !saving, onChange = onStart) }
                        Box(Modifier.weight(1f)) { ActivityTimeField("End", end, zone, compact = true, enabled = !saving, onChange = onEnd) }
                    }
                    HorizontalDivider(color = colors.hairline)
                    Text(duration?.let { "$it recorded" } ?: (range.exceptionOrNull()?.message ?: "Choose a valid time range."),
                        Modifier.padding(top = 12.dp), style = TimeboxTheme.type.bodySmall,
                        color = if (minutes != null) colors.actual else colors.error)
                }
            }
            Column {
                Text("TASK TYPE", style = TimeboxTheme.type.kicker, color = colors.onVariant)
                Spacer(Modifier.height(8.dp))
                TaskTypePicker(taskTypes, query, onQuery, selectedTypeId, onChooseType, onCreateType)
                Text("Selected: ${taskTypes.find { it.id == selectedTypeId }?.name ?: "unspecified"}",
                    Modifier.padding(top = 8.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            HorizontalDivider(color = colors.hairline)
            TextButton(enabled = !saving, onClick = { detailsExpanded = !detailsExpanded }) {
                Text("${if (detailsExpanded) "▾" else "▸"} Block Name & note · optional")
            }
            if (detailsExpanded) {
                OutlinedTextField(name, onName, enabled = !saving, label = { Text("Block Name (optional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = TimeboxShapes.field)
                OutlinedTextField(note, onNote, enabled = !saving, label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, shape = TimeboxShapes.field)
            }
            error?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Button(enabled = !saving && minutes != null, onClick = onSave, modifier = Modifier.fillMaxWidth(),
                shape = TimeboxShapes.field) { Text(if (saving) "Saving…" else duration?.let { "Log $it" } ?: "Log time") }
            Text("Records past time · Does not complete a task", Modifier.padding(top = 8.dp),
                style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        }
    }
}
