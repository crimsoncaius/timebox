package com.timebox.android.ui.day

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.timebox.android.data.Day
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Instant

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
    fun `occupied planned slots and session drafts are unavailable`() {
        val day = dayWithBlocks(block(1, 10 * 60, 11 * 60))
        val draft = PlanningDraftPlacement(
            day.date,
            task(7, "Draft"),
            12 * 60,
            12 * 60 + 30,
        )

        assertFalse(isPlanningDropAvailable(day, 10 * 60))
        assertTrue(isPlanningDropAvailable(day, 11 * 60))
        assertFalse(
            isPlanningDropAvailable(
                day,
                12 * 60,
                12 * 60 + 30,
                listOf(draft),
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

    @Test
    fun `a draft ignores itself but not other drafts and dates are independent`() {
        val day = dayWithBlocks()
        val current = PlanningDraftPlacement(day.date, task(7, "Current"), 540, 570)
        val other = PlanningDraftPlacement(day.date, task(8, "Other"), 600, 630)
        val anotherDate = PlanningDraftPlacement(day.date.plusDays(1), task(9, "Tomorrow"), 660, 690)

        assertTrue(isPlanningDropAvailable(day, 540, 570, listOf(current, other), excludeTaskId = 7))
        assertFalse(isPlanningDropAvailable(day, 600, 630, listOf(current, other), excludeTaskId = 7))
        assertTrue(isPlanningDropAvailable(day, 660, 690, listOf(anotherDate)))
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

    private fun task(id: Int, title: String) = BattleTask(
        id = id,
        parentId = null,
        parentTitle = null,
        projectId = null,
        project = null,
        taskTypeId = null,
        taskType = null,
        recurringTemplateId = null,
        recurringTemplateTitle = null,
        occurrenceKey = null,
        recurrenceKind = null,
        quotaPeriodStart = null,
        quotaPeriodEnd = null,
        expectedSessions = null,
        sessionIndex = null,
        quotaCompleted = null,
        title = title,
        description = "",
        readyToPlan = true,
        status = TaskStatus.Open,
        urgency = null,
        importance = null,
        deadlineDate = null,
        deadlineAt = null,
        reminderAt = null,
        reminderDeliveredAt = null,
        position = 0,
        archivedAt = null,
        deletedAt = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        overdue = false,
        subtasks = emptyList(),
    )
}
