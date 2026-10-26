package com.timebox.android.ui.day

import com.timebox.android.ui.components.ContextualHelp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.SheetField
import com.timebox.android.ui.components.SheetHelp
import com.timebox.android.ui.components.SheetPrimary
import com.timebox.android.ui.components.SheetTextField
import com.timebox.android.ui.components.TimeboxSheet
import com.timebox.android.ui.elapsedDuration
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val range = runCatching {
        val duration = Duration.between(start.resolve(zone), end.resolve(zone))
        require(!duration.isNegative && !duration.isZero) { "End must be after start." }
        duration.toMinutes()
    }
    val minutes = range.getOrNull()
    val date = runCatching { LocalDateTime.parse(start.local).format(DateTimeFormatter.ofPattern("EEE d MMM")) }.getOrNull()
    TimeboxSheet(
        lane = Lane.Actual,
        kicker = "Actual block · New",
        title = "Log time",
        subtitle = listOfNotNull(date, zone.id).joinToString(" · "),
        meta = minutes?.let(::elapsedDuration),
        onDismiss = onDismiss,
        busy = saving,
        primary = when {
            saving -> SheetPrimary.Disabled("Saving…")
            minutes == null -> SheetPrimary.Disabled("Choose a valid time range")
            else -> SheetPrimary.Action("Log ${elapsedDuration(minutes)}", onSave)
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { ActivityTimeField("Start", start, zone, compact = true, enabled = !saving, onChange = onStart) }
            Box(Modifier.weight(1f)) { ActivityTimeField("End", end, zone, compact = true, enabled = !saving, onChange = onEnd) }
        }
        range.exceptionOrNull()?.let { SheetHelp(it.message ?: "Choose a valid time range.", color = colors.error) }
        SheetTextField(name, onName, "Name", optional = true, placeholder = "Add a name", enabled = !saving, fieldModifier = Modifier.testTag("log-name-input"))
        SheetField("Task type") {
            TaskTypePicker(taskTypes, query, onQuery, selectedTypeId, onChooseType, onCreateType, recommendationName = name, recommendationEnabled = !saving)
        }
        SheetTextField(note, onNote, "Note", optional = true, placeholder = "Add a note", enabled = !saving, singleLine = false, minLines = 2)
        ContextualHelp("Recording time", "Records past time. Recording time does not change Task Completion.")
        error?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error) }
    }
}
