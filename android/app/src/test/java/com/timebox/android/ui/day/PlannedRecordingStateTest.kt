package com.timebox.android.ui.day

import com.timebox.android.data.ActualBlock
import com.timebox.android.data.ActualBlockDayProjection
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeBlock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedRecordingStateTest {
    private val date = LocalDate.parse("2026-08-30")
    private val zone = ZoneOffset.UTC

    @Test
    fun `no linked Actual offers Record`() {
        val state = plannedRecordingState(plan(), day(), Instant.parse("2026-08-30T12:00:00Z"))
        assertEquals(PlannedRecordingKind.Record, state.kind)
        assertTrue(state.available)
        assertFalse(state.underway)
        assertEquals(600, state.requestedStartMinute)
        assertEquals(660, state.requestedEndMinute)
        assertNull(state.matching)
    }

    @Test
    fun `matching times are already recorded even when the Actual note changed`() {
        val actual = actual(id = 9, start = 600, end = 660, note = "Written after recording")
        val plan = plan(actualIds = listOf(9))
        val state = plannedRecordingState(plan, day(actual), Instant.parse("2026-08-30T12:00:00Z"))
        assertEquals(PlannedRecordingKind.AlreadyRecorded, state.kind)
        assertEquals(9, state.matching?.id)
        assertEquals(660, state.matching?.endMinute)
    }

    @Test
    fun `shorter linked Actual is an override of the plan interval`() {
        val actual = actual(id = 9, start = 600, end = 630)
        val plan = plan(actualIds = listOf(9))
        val state = plannedRecordingState(plan, day(actual), Instant.parse("2026-08-30T12:00:00Z"))
        assertEquals(PlannedRecordingKind.Override, state.kind)
        assertEquals(630, state.override?.endMinute)
        assertEquals(660, state.requestedEndMinute)
        assertNull(state.matching)
    }

    @Test
    fun `underway recording asks only through now`() {
        val state = plannedRecordingState(plan(), day(), Instant.parse("2026-08-30T10:20:00Z"))
        assertEquals(PlannedRecordingKind.Record, state.kind)
        assertTrue(state.underway)
        assertEquals(620, state.requestedEndMinute)
    }

    @Test
    fun `matching linked Actual with another overlap is not already recorded`() {
        val linked = actual(id = 9, start = 600, end = 660)
        val other = actual(id = 10, start = 645, end = 700, plannedId = null)
        val plan = plan(actualIds = listOf(9))
        val state = plannedRecordingState(plan, day(linked, other), Instant.parse("2026-08-30T12:00:00Z"))
        assertEquals(PlannedRecordingKind.Record, state.kind)
    }

    private fun plan(actualIds: List<Int> = emptyList()) = TimeBlock(
        id = 1,
        lane = Lane.Planned,
        taskTypeId = 3,
        taskTypeName = "writing",
        taskId = null,
        task = null,
        note = "Plan note",
        plannedBlockId = null,
        startMinute = 600,
        endMinute = 660,
        name = "Chapter",
        actualBlockIds = actualIds,
    )

    private fun actual(id: Int, start: Int, end: Int, note: String? = null, plannedId: Int? = 1): ActualBlockDayProjection {
        val startAt = date.atStartOfDay().plusMinutes(start.toLong()).toInstant(zone)
        val endAt = date.atStartOfDay().plusMinutes(end.toLong()).toInstant(zone)
        return ActualBlockDayProjection(
            actualBlock = ActualBlock(
                id = id,
                taskTypeId = 3,
                taskTypeName = "writing",
                taskId = null,
                task = null,
                note = note,
                plannedBlockId = plannedId,
                startAt = startAt,
                endAt = endAt,
                name = "Chapter",
            ),
            date = date,
            startMinute = start,
            endMinute = end,
            durationMinutes = end - start,
        )
    }

    private fun day(vararg actuals: ActualBlockDayProjection) = Day(
        date = date,
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        blocks = listOf(plan()),
        actualBlocks = actuals.toList(),
        timezone = "UTC",
        today = date,
        serverNowMinute = 12 * 60,
    )
}
