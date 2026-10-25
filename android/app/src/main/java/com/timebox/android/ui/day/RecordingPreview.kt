package com.timebox.android.ui.day

import com.timebox.android.data.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.data.remote.PlannedRecordingDto
import com.timebox.android.ui.components.RelatedCard
import com.timebox.android.ui.components.SheetField
import com.timebox.android.ui.components.SheetHelp
import com.timebox.android.ui.components.SheetPrimary
import com.timebox.android.ui.components.TimeboxSheet
import com.timebox.android.ui.elapsedDuration
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecordingPreview(preview: PlannedRecordingDto, timezone: String, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit, error: String? = null, plannedBlock: TimeBlock? = null) {
    if (com.timebox.android.BuildConfig.RECORDING_PROTOTYPE) {
        RecordingPreviewStudy(preview, timezone, onCancel)
        return
    }
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val zone = ZoneId.of(timezone)
    val title = activityPrimaryIdentity(preview.replacement.name, plannedBlock?.task?.title, plannedBlock?.taskTypeName)
    val secondary = activitySecondaryIdentity(preview.replacement.name, plannedBlock?.task?.title, plannedBlock?.taskTypeName)
    val model = remember(preview, title, secondary) { recordingTimelineModel(preview, listOfNotNull(title, secondary).joinToString(" · ")) }
    val start = model.start
    val end = model.end
    fun time(at: Instant) = recordingClock(at, start, zone)
    var details by remember(preview.fingerprint) { mutableStateOf(false) }
    val changedDetails = preview.conflicts.filter { it.name != preview.replacement.name || it.note != preview.replacement.note }
    val replaced = replacedRecordingMinutes(preview)
    TimeboxSheet(
        lane = Lane.Actual,
        kicker = "Record from plan",
        title = "${time(start)} – ${time(end)}",
        subtitle = listOfNotNull(title, secondary).joinToString(" · "),
        meta = elapsedDuration(Duration.between(start, end).toMinutes()),
        onDismiss = onCancel,
        busy = busy,
        primary = when {
            busy -> SheetPrimary.Disabled("Recording…")
            preview.conflicts.isEmpty() -> SheetPrimary.Action("Record ${elapsedDuration(Duration.between(start, end).toMinutes())}", onConfirm)
            else -> SheetPrimary.Action("Record and replace ${elapsedDuration(replaced)}", onConfirm)
        },
    ) {
        SheetHelp("${DateTimeFormatter.ofPattern("EEE, d MMM").withZone(zone).format(start)} · $timezone")
        error?.let { Text(it, style = type.bodySmall, color = colors.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (preview.stale) Text("Records changed. Review this updated preview before confirming again.", style = type.bodySmall, color = colors.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (model.before.isNotEmpty()) {
            SheetField("Before and after") {
                RecordingTimeline(model.before, model.after, model.first, model.last, start, end, false, zone)
            }
            // Exact text remains available for tiny fragments, long names, dates and DST offsets.
            preview.conflicts.sortedBy { it.startAt }.forEach { row ->
                val a = Instant.parse(row.startAt)
                val b = row.endAt?.let(Instant::parse)
                val from = maxOf(a, start)
                val until = minOf(b ?: end, end)
                val keeps = listOfNotNull(
                    if (a < start) "Keeps ${time(a)} – ${time(start)}." else null,
                    if (b != null && b > end) "Keeps ${time(end)} – ${time(b)}." else null,
                    if (b == null) "Tracking stays on from ${time(end)}." else null,
                )
                val renamed = row.name != preview.replacement.name || row.note != preview.replacement.note
                RelatedCard(
                    lane = Lane.Actual,
                    warn = true,
                    caption = "Replaces recorded time",
                    trailing = elapsedDuration(Duration.between(from, until).toMinutes().coerceAtLeast(0)),
                    title = row.primaryIdentity(),
                    text = (listOfNotNull(
                        listOfNotNull(row.secondaryIdentity(), "${time(from)} – ${time(until)} becomes $title.").joinToString(" · "),
                        if (renamed) "Its name and note change." else null,
                    ) + keeps).joinToString(" "),
                )
            }
        } else SheetHelp("No Actual time overlaps this updated range.")
        if (changedDetails.isNotEmpty()) {
            TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) {
                Text(if (details) "Hide details" else "See name and note changes", style = type.bodySmall)
            }
            if (details) Column(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.low).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                changedDetails.forEach { row ->
                    Text("Existing: ${row.identityText()}", style = type.label)
                    Text("${time(Instant.parse(row.startAt))} – ${row.endAt?.let { time(Instant.parse(it)) } ?: "Running"}", style = type.monoSmall, color = colors.onVariant)
                    Text(row.note ?: "No note", style = type.bodySmall)
                }
                Text("After: $title", style = type.label)
                Text(preview.replacement.note ?: "No note", style = type.bodySmall)
                Text("Kept time retains its original details.", style = type.bodySmall, color = colors.onVariant)
            }
        }
        SheetHelp(if (preview.conflicts.isEmpty()) "The plan is kept. Task completion is unchanged."
            else "Only overlapping time is replaced. The plan is kept. Task completion is unchanged.")
    }
}

/** Minutes of recorded time the preview overwrites, clipped to its interval. */
internal fun replacedRecordingMinutes(preview: PlannedRecordingDto): Long {
    val start = Instant.parse(preview.startAt)
    val end = Instant.parse(preview.endAt)
    val seconds = preview.conflicts.sumOf { row ->
        val a = maxOf(Instant.parse(row.startAt), start)
        val b = minOf(row.endAt?.let(Instant::parse) ?: end, end)
        Duration.between(a, b).seconds.coerceAtLeast(0)
    }
    return (seconds + 30) / 60
}

/** Minute precision; the date appears only when [at] falls on another day than [reference]. */
internal fun recordingClock(at: Instant, reference: Instant, zone: ZoneId): String {
    val sameDay = at.atZone(zone).toLocalDate() == reference.atZone(zone).toLocalDate()
    return DateTimeFormatter.ofPattern(if (sameDay) "HH:mm" else "d MMM HH:mm").withZone(zone).format(at)
}
