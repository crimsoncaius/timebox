package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.remote.PlannedRecordingDto
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingPreview(preview: PlannedRecordingDto, timezone: String, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit, error: String? = null) {
    if (com.timebox.android.BuildConfig.RECORDING_PROTOTYPE) {
        RecordingPreviewStudy(preview, timezone, onCancel)
        return
    }
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val zone = ZoneId.of(timezone)
    val model = remember(preview) { recordingTimelineModel(preview) }
    val start = model.start
    val end = model.end
    val title = preview.replacement.name ?: "Unnamed activity"
    val sameDay = start.atZone(zone).toLocalDate() == end.atZone(zone).toLocalDate()
    val clockPattern = if (start.epochSecond % 60 == 0L && end.epochSecond % 60 == 0L) "HH:mm" else "HH:mm:ss"
    val short = DateTimeFormatter.ofPattern(if (sameDay) clockPattern else "MMM d $clockPattern").withZone(zone)
    val exact = DateTimeFormatter.ofPattern("MMM d HH:mm:ss XXX").withZone(zone)
    fun time(at: Instant) = exact.format(at)
    var details by remember(preview.fingerprint) { mutableStateOf(false) }
    val changedDetails = preview.conflicts.filter { it.name != preview.replacement.name || it.note != preview.replacement.note }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onCancel() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy }),
        containerColor = colors.bg, contentColor = colors.on, scrimColor = colors.scrim, shape = TimeboxShapes.sheet,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp)
            .padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("RECORD ACTUAL", style = type.kicker, color = colors.actual)
                Text(if (preview.conflicts.isEmpty()) "Record Actual" else "Replace overlapping time", style = type.screenTitle.copy(fontSize = 26.sp))
                Text("${DateTimeFormatter.ofPattern("EEE, d MMM").withZone(zone).format(start)} · $timezone", style = type.bodySmall, color = colors.onVariant)
                error?.let { Text(it, style = type.bodySmall, color = colors.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                if (preview.stale) Text("Records changed. Review this updated preview before confirming again.", style = type.bodySmall, color = colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Column(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.actualSurface)
                    .border(1.dp, colors.actualBorder, TimeboxShapes.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${short.format(start)} – ${short.format(end)}", style = type.display.copy(fontSize = 26.sp))
                    Text("${Duration.between(start, end).seconds / 60} min ${Duration.between(start, end).seconds % 60} sec", style = type.monoSmall, color = colors.onVariant)
                    Text(title, style = type.label)
                    Text("From your Planned Block", style = type.bodySmall, color = colors.onVariant)
                }
                if (model.before.isNotEmpty()) {
                    RecordingTimeline(model.before, model.after, model.first, model.last, start, end, false, zone)
                    Text("Only overlapping time is replaced. Time outside this range keeps its original details.", style = type.bodySmall, color = colors.onVariant)
                    // Exact text remains available for tiny fragments, long names, dates and DST offsets.
                    preview.conflicts.sortedBy { it.startAt }.forEach { row ->
                        val a = Instant.parse(row.startAt)
                        val b = row.endAt?.let(Instant::parse)
                        Column(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.low).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(row.name ?: row.taskType.name, style = type.label)
                            Text("Replace ${time(maxOf(a, start))} – ${time(minOf(b ?: end, end))}", style = type.bodySmall, color = colors.error)
                            if (a < start) Text("Keep ${time(a)} – ${time(start)}", style = type.bodySmall)
                            if (b != null && b > end) Text("Keep ${time(end)} – ${time(b)}", style = type.bodySmall)
                            if (b == null) Text("Keep tracking from ${time(end)}. Tracking stays on.", style = type.bodySmall, color = colors.actual)
                        }
                    }
                } else Text("No Actual time overlaps this updated range.", style = type.bodySmall)
                if (changedDetails.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.low).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Name or note will change for ${changedDetails.size} overlapping ${if (changedDetails.size == 1) "block" else "blocks"}.", style = type.bodySmall)
                        TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (details) "Hide details" else "See name and note changes", style = type.bodySmall)
                        }
                        if (details) {
                            changedDetails.forEach { row ->
                                Text("Existing: ${row.name ?: "Unnamed activity"}", style = type.label)
                                Text("${time(Instant.parse(row.startAt))} – ${row.endAt?.let { time(Instant.parse(it)) } ?: "Running"}", style = type.monoSmall, color = colors.onVariant)
                                Text(row.note ?: "No note", style = type.bodySmall)
                            }
                            Text("After: $title", style = type.label)
                            Text(preview.replacement.note ?: "No note", style = type.bodySmall)
                            Text("Kept time retains its original details.", style = type.bodySmall, color = colors.onVariant)
                        }
                    }
                }
                Text("The plan is kept. Task completion is unchanged.", style = type.bodySmall, color = colors.onVariant)
            }
            HorizontalDivider(color = colors.hairline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel", style = type.button, color = colors.onVariant) }
                Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = TimeboxShapes.chip,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.action, contentColor = colors.onAction)) {
                    Text(if (busy) "Recording…" else if (preview.conflicts.isEmpty()) "Record Actual" else "Replace overlapping time", style = type.button)
                }
            }
        }
    }
}
