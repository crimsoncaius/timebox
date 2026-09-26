package com.timebox.android.ui.day

import com.timebox.android.data.ActualBlock
import com.timebox.android.data.ActualBlockDayProjection
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.durationShort
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockDurationTest {
    private val date = LocalDate.of(2026, 9, 16)
    private val planned = TimeBlock(
        id = 8, lane = Lane.Planned, taskTypeId = 1, taskTypeName = "Writing", taskId = null, task = null,
        note = null, plannedBlockId = null, startMinute = 9 * 60, endMinute = 10 * 60 + 30,
    )

    private fun dayWith(startMinute: Int, endMinute: Int, startAt: String, endAt: String?): Pair<Day, TimeBlock> {
        val card = planned.copy(id = -44, lane = Lane.Actual, actualBlockId = 44, startMinute = startMinute, endMinute = endMinute)
        val record = ActualBlock(44, 1, "Writing", null, null, null, null, Instant.parse(startAt), endAt?.let(Instant::parse))
        val day = Day(
            date = date, startHour = 0, endHour = 24, showFullDay = true, blocks = listOf(card),
            actualBlocks = listOf(ActualBlockDayProjection(record, date, startMinute, endMinute, endMinute - startMinute)),
            timezone = "UTC", today = date, elapsedRealtime = { 0L }, serverNowMinute = 12 * 60,
        )
        return day to card
    }

    @Test fun `planned block duration is its span`() {
        val (day, _) = dayWith(0, 30, "2026-09-16T00:00:00Z", "2026-09-16T00:30:00Z")
        assertEquals(90, blockDurationMinutes(planned, day, planned.startMinute, planned.endMinute, dragging = false))
    }

    @Test fun `actual block that began the previous day shows its whole duration`() {
        val (day, card) = dayWith(0, 90, "2026-09-15T23:00:00Z", "2026-09-16T01:30:00Z")
        assertEquals(150, blockDurationMinutes(card, day, 0, 90, dragging = false))
    }

    @Test fun `running actual block counts up with its displayed end, including the previous day`() {
        val (day, card) = dayWith(0, 60, "2026-09-15T23:30:00Z", null)
        assertEquals(30 + 12 * 60, blockDurationMinutes(card, day, 0, 12 * 60, dragging = false))
    }

    @Test fun `a drag shows the previewed span`() {
        val (day, card) = dayWith(0, 90, "2026-09-15T23:00:00Z", "2026-09-16T01:30:00Z")
        assertEquals(45, blockDurationMinutes(card, day, 60, 105, dragging = true))
    }

    @Test fun `short duration labels`() {
        assertEquals(listOf("0m", "45m", "2h", "1h 30m"), listOf(0, 45, 120, 90).map(::durationShort))
    }
}
