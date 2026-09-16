package com.timebox.android.ui.day

import com.timebox.android.data.ActualBlock
import com.timebox.android.data.ActualBlockDayProjection
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeBlock
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayTimelineDisplayTest {
    private val today = LocalDate.of(2026, 9, 16)
    private val planned = TimeBlock(
        id = 8,
        lane = Lane.Planned,
        taskTypeId = 1,
        taskTypeName = "Writing",
        taskId = 7,
        task = null,
        note = "Outline",
        plannedBlockId = null,
        actualBlockId = 44,
        startMinute = 10 * 60,
        endMinute = 12 * 60,
        name = "Chapter",
    )
    private val actualCard = planned.copy(
        id = -44,
        lane = Lane.Actual,
        plannedBlockId = planned.id,
        actualBlockId = 44,
        startMinute = 10 * 60 + 30,
        endMinute = 10 * 60 + 30,
        name = "Chapter",
        taskId = 7,
        note = "Outline",
    )
    private val day = Day(
        date = today,
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        blocks = listOf(planned, actualCard),
        actualBlocks = listOf(
            ActualBlockDayProjection(
                actualBlock = ActualBlock(
                    id = 44,
                    taskTypeId = 1,
                    taskTypeName = "Writing",
                    taskId = 7,
                    task = null,
                    note = "Outline",
                    plannedBlockId = planned.id,
                    startAt = Instant.parse("2026-09-16T02:30:00Z"),
                    endAt = null,
                    name = "Chapter",
                ),
                date = today,
                startMinute = actualCard.startMinute,
                endMinute = actualCard.endMinute,
                durationMinutes = 0,
            ),
        ),
        timezone = "Asia/Singapore",
        today = today,
        serverNowMinute = 10 * 60 + 45,
    )

    @Test
    fun plannedBlockKeepsAllocatedTimesAfterSwitchingOntoIt() {
        val now = 10 * 60 + 45
        assertFalse(isLiveActualBlock(planned, day))
        assertEquals(12 * 60, displayedBlockEndMinute(planned, day, now))
        assertTrue(isLiveActualBlock(actualCard, day))
        assertEquals(now, displayedBlockEndMinute(actualCard, day, now))
    }
}
