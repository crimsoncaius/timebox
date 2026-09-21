package com.timebox.android.ui.day

import com.timebox.android.data.identityText
import com.timebox.android.data.activityPrimaryIdentity
import com.timebox.android.data.remote.PlannedRecordingDto
import java.time.Instant

internal data class RecordingTimelineModel(
    val start: Instant, val end: Instant, val first: Instant, val last: Instant,
    val before: List<RecordingPiece>, val after: List<RecordingPiece>,
)

/** Presentation of the server's frozen interval; never evaluates the current clock. */
internal fun recordingTimelineModel(preview: PlannedRecordingDto, replacementTitle: String = activityPrimaryIdentity(preview.replacement.name, null, null)): RecordingTimelineModel {
    val start = Instant.parse(preview.startAt)
    val end = Instant.parse(preview.endAt)
    val last = maxOf(end, preview.conflicts.mapNotNull { it.endAt?.let(Instant::parse) }.maxOrNull() ?: end)
        .let { if (preview.conflicts.any { row -> row.endAt == null }) it.plusSeconds(900) else it }
    val before = preview.conflicts.map { row ->
        RecordingPiece(row.identityText(), Instant.parse(row.startAt), row.endAt?.let(Instant::parse) ?: last, running = row.endAt == null)
    }.sortedBy { it.start }
    val after = buildList {
        before.forEach { row ->
            if (row.start < start) add(row.copy(end = start, running = false))
            if (row.end > end || row.running) add(row.copy(start = end))
        }
        add(RecordingPiece(replacementTitle, start, end, fresh = true))
    }.sortedBy { it.start }
    return RecordingTimelineModel(start, end, minOf(start, before.minOfOrNull { it.start } ?: start), last, before, after)
}
