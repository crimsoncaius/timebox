package com.timebox.android.ui.day

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.ActualBlock
import com.timebox.android.data.Day
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.elapsedDuration
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun PlannedTimeRangeHeader(startMinute: Int, endMinute: Int, accent: Boolean = true) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "${hhmm(startMinute)} – ${hhmm(endMinute)}",
            Modifier.weight(1f),
            style = TimeboxTheme.type.display,
            color = colors.on,
        )
        Text(
            elapsedDuration((endMinute - startMinute).toLong().coerceAtLeast(0)),
            style = TimeboxTheme.type.label,
            color = if (accent) colors.planned else colors.onVariant,
        )
    }
}

@Composable
internal fun CompactBlockTimeFields(block: TimeBlock, day: Day?, saving: Boolean, onSave: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var startText by remember(block.id, block.startMinute) { mutableStateOf(hhmm(block.startMinute)) }
    var endText by remember(block.id, block.endMinute) { mutableStateOf(hhmm(block.endMinute)) }
    val start = parseBlockMinute(startText)
    val end = parseBlockMinute(endText)
    val error = when {
        start == null || end == null -> "Enter times as HH:mm."
        end <= start -> "End must be after start."
        day != null && (start < day.visibleStart || end > day.visibleEnd) -> "Choose times within the visible day."
        day?.lane(block.lane)?.any { it.id != block.id && start < it.endMinute && end > it.startMinute } == true ->
            "This time overlaps another block."
        else -> null
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactTimePicker(
            label = "Start",
            value = startText,
            enabled = !saving,
            modifier = Modifier.weight(1f),
            onShowPicker = { hour, minute ->
                TimePickerDialog(context, { _, nextHour, nextMinute -> startText = hhmm(nextHour * 60 + nextMinute) }, hour, minute, true).show()
            },
        )
        CompactTimePicker(
            label = "End",
            value = endText,
            enabled = !saving,
            modifier = Modifier.weight(1f),
            onShowPicker = { hour, minute ->
                TimePickerDialog(context, { _, nextHour, nextMinute -> endText = hhmm(nextHour * 60 + nextMinute) }, hour, minute, true).show()
            },
        )
    }
    error?.let { Text(it, color = colors.error, style = type.bodySmall) }
    TextButton(
        enabled = !saving && error == null && (start != block.startMinute || end != block.endMinute),
        onClick = { if (start != null && end != null && error == null) onSave(start, end) },
    ) { Text("Save times") }
}

@Composable
private fun CompactTimePicker(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onShowPicker: (Int, Int) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val parsed = parseBlockMinute(value)
    val pickerHour = ((parsed ?: 0) / 60).coerceIn(0, 23)
    val pickerMinute = (parsed ?: 0) % 60
    Column(modifier) {
        Text(label, style = type.bodySmall, color = colors.onVariant)
        TextButton(
            enabled = enabled,
            onClick = { onShowPicker(pickerHour, pickerMinute) },
            modifier = Modifier.semantics { contentDescription = "$label time" },
        ) {
            Text(value, style = type.screenTitle, color = colors.on)
        }
    }
}

@Composable
internal fun PlannedRecordingStudy(
    block: TimeBlock,
    day: Day?,
    now: Instant,
    saving: Boolean,
    notice: String?,
    undoAvailable: Boolean,
    error: String?,
    onRecord: () -> Unit,
    onOpenActual: (Int) -> Unit,
    onUndo: () -> Unit,
) {
    val zone = ZoneId.of(day?.timezone ?: "UTC")
    val recording = plannedRecordingState(block, day, now)
    val actionLabel = if (recording.underway) "Record Actual until now" else "Record Actual as planned"
    val requestedRange = "${hhmm(recording.requestedStartMinute)} – ${hhmm(recording.requestedEndMinute)}"
    val requestedDuration = elapsedDuration((recording.requestedEndMinute - recording.requestedStartMinute).toLong().coerceAtLeast(0))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (recording.kind) {
            PlannedRecordingKind.AlreadyRecorded -> {
                val linked = recording.matching!!
                IntervalCard(
                    range = "${hhmm(linked.startMinute)} – ${hhmm(linked.endMinute)}",
                    duration = elapsedDuration((linked.endMinute - linked.startMinute).toLong()),
                    caption = "Already recorded · no update needed",
                    accentActual = true,
                    actionLabel = "Open Actual",
                    actionEnabled = true,
                    onAction = { onOpenActual(linked.id) },
                )
                Text(
                    "This time is already recorded. Plan notes stay on the plan.",
                    style = TimeboxTheme.type.bodySmall,
                    color = TimeboxTheme.colors.onVariant,
                )
            }
            PlannedRecordingKind.Override -> {
                val linked = recording.override!!
                LinkedOverrideCard(
                    currentRange = "${hhmm(linked.startMinute)} – ${hhmm(linked.endMinute)}",
                    currentDuration = elapsedDuration((linked.endMinute - linked.startMinute).toLong()),
                    proposedRange = requestedRange,
                    proposedDuration = requestedDuration,
                    replaceEnabled = !saving && recording.available,
                    onOpenActual = { onOpenActual(linked.id) },
                    onReplace = onRecord,
                )
                if (!recording.available) {
                    Text("Available after this block starts.", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                }
            }
            PlannedRecordingKind.Record -> {
                block.actualBlockIds.forEach { id ->
                    val actual = day?.actualBlocks?.find { it.actualBlock.id == id }?.actualBlock
                    IntervalCard(
                        range = actual?.let { formatActualRange(it, zone) } ?: "Linked Actual",
                        duration = actual?.let { elapsedBetween(it.startAt, it.endAt ?: now) },
                        caption = "Linked Actual · times may differ from this plan",
                        accentActual = true,
                        actionLabel = "Open Actual",
                        actionEnabled = true,
                        onAction = { onOpenActual(id) },
                    )
                }
                IntervalCard(
                    range = requestedRange,
                    duration = requestedDuration,
                    caption = if (!recording.available) "Available after this block starts."
                        else if (block.actualBlockIds.isNotEmpty()) "Record again from this plan"
                        else "Record from this plan",
                    accentActual = true,
                    actionLabel = actionLabel,
                    actionEnabled = !saving && recording.available,
                    onAction = onRecord,
                )
            }
        }
        if (undoAvailable) {
            com.timebox.android.ui.components.TransientFeedback(
                notice ?: "Actual recorded",
                actionLabel = "Undo",
                onAction = onUndo,
                actionsEnabled = !saving,
            )
        } else if (notice != null) {
            com.timebox.android.ui.components.TransientFeedback(notice)
        }
        error?.let { Text(it, color = TimeboxTheme.colors.error, style = TimeboxTheme.type.bodySmall) }
    }
}

@Composable
private fun LinkedOverrideCard(
    currentRange: String,
    currentDuration: String?,
    proposedRange: String,
    proposedDuration: String,
    replaceEnabled: Boolean,
    onOpenActual: () -> Unit,
    onReplace: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TimeboxShapes.card)
            .background(colors.actualSurface)
            .border(1.dp, colors.actualBorder, TimeboxShapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(currentRange, Modifier.weight(1f), style = type.mono.copy(textDecoration = TextDecoration.LineThrough), color = colors.onVariant)
            currentDuration?.let { Text(it, style = type.label, color = colors.onVariant) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(proposedRange, Modifier.weight(1f), style = type.display.copy(fontSize = 28.sp))
            Text(proposedDuration, style = type.label, color = colors.actual)
        }
        Text("Would replace this Actual. Replace lists every overlapping Actual.", style = type.bodySmall, color = colors.onVariant)
        TextButton(onClick = onOpenActual, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
            Text("Open Actual", color = colors.actual)
        }
        PrimaryButton(
            text = "Replace overlapping time",
            onClick = onReplace,
            enabled = replaceEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ActualPlanLink(plan: TimeBlock, caption: String, onOpen: () -> Unit) {
    IntervalCard(
        range = "${hhmm(plan.startMinute)} – ${hhmm(plan.endMinute)}",
        duration = elapsedDuration(plan.durationMinutes.toLong()),
        caption = caption,
        accentActual = false,
        actionLabel = "Open Planned Block",
        actionEnabled = true,
        onAction = onOpen,
    )
}

@Composable
private fun IntervalCard(
    range: String,
    duration: String?,
    caption: String,
    accentActual: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val surface = if (accentActual) colors.actualSurface else colors.plannedSurface
    val border = if (accentActual) colors.actualBorder else colors.plannedBorder
    val accent = if (accentActual) colors.actual else colors.planned
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TimeboxShapes.card)
            .background(surface)
            .border(1.dp, border, TimeboxShapes.card)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(range, Modifier.weight(1f), style = type.display.copy(fontSize = 28.sp))
            duration?.let { Text(it, style = type.label, color = accent) }
        }
        Text(caption, style = type.bodySmall, color = colors.onVariant)
        TextButton(
            enabled = actionEnabled,
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        ) { Text(actionLabel, color = if (actionEnabled) accent else colors.disabledContent) }
    }
}

private fun elapsedBetween(start: Instant, end: Instant): String =
    elapsedDuration(Duration.between(start, end).toMinutes().coerceAtLeast(0))

private fun formatActualRange(actual: ActualBlock, zone: ZoneId): String {
    val start = actual.startAt.atZone(zone)
    val end = actual.endAt?.atZone(zone)
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val dateTime = DateTimeFormatter.ofPattern("d MMM HH:mm")
    val format = if (end != null && start.toLocalDate() == end.toLocalDate()) clock else dateTime
    return "${format.format(start)} – ${end?.let(format::format) ?: "Now"}"
}
