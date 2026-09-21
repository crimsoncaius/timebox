package com.timebox.android.ui.day

import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.PlannedRecordingDto
import com.timebox.android.data.remote.RecordingReplacement
import com.timebox.android.data.remote.TaskTypeDto
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RecordingTimelineModelTest {
    private fun at(time: String) = "2026-09-13T${time}:00Z"
    private fun row(id: Int, start: String, end: String?) = ActualBlockDto(id, 1, TaskTypeDto(1, "writing"),
        startAt = at(start), endAt = end?.let(::at), createdAt = at(start), updatedAt = at(start), name = "Old $id")
    private fun preview(vararg rows: ActualBlockDto) = PlannedRecordingDto("confirmation_required", at("10:00"), at("11:00"),
        "frozen", conflicts = rows.toList(), replacement = RecordingReplacement("New", "Plan note"))

    @Test fun `spanning actual keeps both outside portions`() {
        val model = recordingTimelineModel(preview(row(1, "09:45", "11:15")))
        assertEquals(listOf("Old 1 · writing", "New", "Old 1 · writing"), model.after.map { it.title })
        assertEquals(listOf(at("09:45"), at("10:00"), at("11:00")), model.after.map { it.start.toString() })
        assertEquals(listOf(at("10:00"), at("11:00"), at("11:15")), model.after.map { it.end.toString() })
    }

    @Test fun `multiple overlaps remove contained records and preserve only outside edges`() {
        val model = recordingTimelineModel(preview(row(3, "10:50", "11:30"), row(1, "09:45", "10:15"), row(2, "10:20", "10:40")))
        assertEquals(listOf("Old 1 · writing", "Old 2 · writing", "Old 3 · writing"), model.before.map { it.title })
        assertEquals(listOf("Old 1 · writing", "New", "Old 3 · writing"), model.after.map { it.title })
        assertEquals(1, model.after.count { it.fresh })
        assertEquals(Instant.parse(at("11:00")), model.after.last().start)
    }

    @Test fun `running activity resumes at frozen end and earlier piece is closed`() {
        val model = recordingTimelineModel(preview(row(1, "09:45", null)))
        assertFalse(model.after.first().running)
        assertTrue(model.after.last().running)
        assertEquals(model.end, model.after.last().start)
        assertEquals(Instant.parse(at("11:15")), model.last)
    }

    @Test fun `stale empty preview still contains one replacement`() {
        val model = recordingTimelineModel(preview().copy(stale = true))
        assertTrue(model.before.isEmpty())
        assertEquals(1, model.after.size)
        assertEquals(model.start, model.first)
        assertEquals(model.end, model.last)
    }

    @Test fun `tiny interval across midnight preserves precise endpoints`() {
        val source = preview(row(1, "23:50", null)).copy(startAt = "2026-09-13T23:59:59.500Z", endAt = "2026-09-14T00:00:00.100Z")
        val model = recordingTimelineModel(source)
        assertEquals(Instant.parse(source.endAt), model.after.last().start)
        assertEquals(Instant.parse(source.startAt), model.after.first().end)
        assertEquals(600L, java.time.Duration.between(model.after[1].start, model.after[1].end).toMillis())
    }
}
