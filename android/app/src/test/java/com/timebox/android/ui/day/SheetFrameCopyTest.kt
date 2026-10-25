package com.timebox.android.ui.day

import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.PlannedRecordingDto
import com.timebox.android.data.remote.RecordingReplacement
import com.timebox.android.data.remote.TaskTypeDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The words the M5 sheet frame puts on screen: overlaps named exactly, times to the minute. */
class SheetFrameCopyTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private fun at(time: String) = "2026-10-25T${time}+08:00"
    private fun instant(time: String): Instant = java.time.OffsetDateTime.parse(at(time)).toInstant()

    private fun row(id: Int, start: String, end: String?) = ActualBlockDto(id, 1, TaskTypeDto(1, "work/deep work"),
        startAt = instant(start).toString(), endAt = end?.let { instant(it).toString() },
        createdAt = instant(start).toString(), updatedAt = instant(start).toString(), name = "Chapter 3 draft")

    private fun preview(start: String, end: String, vararg rows: ActualBlockDto) = PlannedRecordingDto(
        "confirmation_required", instant(start).toString(), instant(end).toString(), "frozen",
        conflicts = rows.toList(), replacement = RecordingReplacement("Inbox zero", null))

    @Test fun `record from plan counts only the overlapping recorded minutes`() {
        val recording = preview("11:00:00", "11:48:00", row(1, "11:01:00", "11:44:00"), row(2, "10:30:00", "11:10:00"))
        assertEquals(43L + 10L, replacedRecordingMinutes(recording))
    }

    @Test fun `record from plan clips a running activity at the plan end`() {
        assertEquals(18L, replacedRecordingMinutes(preview("11:00:00", "11:30:00", row(1, "11:12:00", null))))
    }

    @Test fun `record from plan shows times to the minute, not raw timestamps`() {
        val start = instant("11:01:37")
        assertEquals("11:01", recordingClock(start, start, zone))
        assertEquals("26 Oct 00:15", recordingClock(instant("11:01:00").plusSeconds(13 * 3600 + 14 * 60), start, zone))
    }

    private fun plan(id: Int, name: String, start: String, end: String) =
        ActivityPlanDto(id, taskTypeId = 1, name = name, startAt = instant(start).toString(), endAt = instant(end).toString())

    @Test fun `replace planned time names the exact overlap and its length`() {
        val sync = plan(7, "Thesis supervisor sync", "11:55:00", "12:25:00")
        val from = instant("11:41:00")
        val until = instant("12:41:00")
        assertEquals(30L, replacedPlanMinutes(from, until, listOf(sync)))
        assertEquals(
            "work / admin until 12:41 takes 11:55 – 12:25 from Thesis supervisor sync. The rest of your plan stays in place.",
            replacePlanBody("work / admin", from, until, listOf(sync), zone) { it.name!! },
        )
    }

    @Test fun `replace planned time counts a plan already in progress only from now`() {
        val running = plan(8, "Reading", "11:00:00", "12:00:00")
        assertEquals(19L, replacedPlanMinutes(instant("11:41:00"), instant("12:41:00"), listOf(running)))
    }

    private val date = LocalDate.parse("2026-10-25")
    private fun block(id: Int, start: Int, end: Int) = TimeBlock(
        id = id, lane = Lane.Planned, taskTypeId = 3, taskTypeName = "writing", taskId = null, task = null,
        note = null, plannedBlockId = null, startMinute = start, endMinute = end, name = null,
    )
    private val day = Day(
        date = date, startHour = 8, endHour = 20, showFullDay = false,
        blocks = listOf(block(1, 540, 600), block(2, 660, 720)), actualBlocks = emptyList(),
        timezone = "UTC", today = date, elapsedRealtime = { 0L }, serverNowMinute = 12 * 60,
    )

    @Test fun `planned sheet times save only when they fit`() {
        val selected = day.blocks.first()
        assertNull(plannedTimesError(selected, day, 540, 630))
        assertEquals("End must be after start.", plannedTimesError(selected, day, 600, 600))
        assertEquals("This time overlaps another block.", plannedTimesError(selected, day, 540, 670))
        assertEquals("Choose times within the visible day.", plannedTimesError(selected, day, 420, 540))
    }
}
