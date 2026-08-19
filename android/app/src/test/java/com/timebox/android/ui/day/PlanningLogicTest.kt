package com.timebox.android.ui.day

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanningLogicTest {
    @Test
    fun `drop position snaps and clamps to the visible day`() {
        val lane = Rect(40f, 100f, 240f, 820f)
        val viewport = Rect(0f, 80f, 300f, 500f)

        assertEquals(
            9 * 60,
            planningDropStart(Offset(100f, 196f), lane, viewport, 8 * 60, 20 * 60, 48f),
        )
        assertEquals(
            8 * 60,
            planningDropStart(Offset(100f, 82f), lane, viewport, 8 * 60, 20 * 60, 48f),
        )
        assertNull(planningDropStart(Offset(260f, 196f), lane, viewport, 8 * 60, 20 * 60, 48f))
        assertNull(planningDropStart(Offset(100f, 520f), lane, viewport, 8 * 60, 20 * 60, 48f))
    }

    @Test
    fun `occupied planned slots and pending placements are unavailable`() {
        val day = dayWithBlocks(block(1, 10 * 60, 11 * 60))

        assertFalse(isPlanningDropAvailable(day, 10 * 60))
        assertTrue(isPlanningDropAvailable(day, 11 * 60))
        assertFalse(
            isPlanningDropAvailable(
                day,
                12 * 60,
                PendingPlanningPlacement(day.date, 7, "Pending", 12 * 60, 12 * 60 + 30),
            ),
        )
    }

    @Test
    fun `unspecified fallback is case insensitive`() {
        val types = listOf(
            TaskType(1, "work", 0),
            TaskType(2, "Unspecified", 0),
        )

        assertEquals(2, types.unspecifiedTypeId())
        assertNull(types.filterNot { it.id == 2 }.unspecifiedTypeId())
    }

    @Test
    fun `touching intervals do not overlap`() {
        assertFalse(blocksOverlap(480, 510, 510, 540))
        assertTrue(blocksOverlap(480, 510, 500, 530))
    }

    private fun dayWithBlocks(vararg blocks: TimeBlock) = Day(
        date = LocalDate.of(2026, 8, 20),
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        blocks = blocks.toList(),
        timezone = "Asia/Singapore",
        today = LocalDate.of(2026, 8, 20),
        serverNowMinute = 9 * 60,
    )

    private fun block(id: Int, start: Int, end: Int) = TimeBlock(
        id = id,
        lane = Lane.Planned,
        taskTypeId = 1,
        taskTypeName = "work",
        taskId = null,
        task = null,
        note = null,
        plannedBlockId = null,
        startMinute = start,
        endMinute = end,
    )
}
