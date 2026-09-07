package com.timebox.android.ui.day

import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.battleplan.task
import com.timebox.android.ui.planning.PlanningDraftPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PlanningDragPlacementTest {
    @Test
    fun `occupied destination chooses closest fitting start with later ties`() {
        val day = day(block(1, 600, 630))

        assertEquals(630, nearestPlanningDragStart(day, emptyList(), null, 600, 30))
        assertEquals(570, nearestPlanningDragStart(day, emptyList(), null, 590, 30))
    }

    @Test
    fun `exact off-grid gap fits without shrinking or moving its neighbours`() {
        val day = day(block(1, 480, 602), block(2, 632, 1200))
        assertEquals(602, nearestPlanningDragStart(day, emptyList(), null, 600, 30))
        assertNull(nearestPlanningDragStart(day, emptyList(), null, 600, 60))
        assertEquals(listOf(480 to 602, 632 to 1200), day.blocks.map { it.startMinute to it.endMinute })
    }

    @Test
    fun `valid off-grid start remains unchanged and Actual Blocks do not obstruct it`() {
        val day = day(block(1, 480, 1200).copy(lane = Lane.Actual))
        assertEquals(547, nearestPlanningDragStart(day, emptyList(), null, 547, 60))
    }

    @Test
    fun `search includes distant times but excludes hidden hours and other days`() {
        val day = day(block(1, 480, 1150))
        assertEquals(1150, nearestPlanningDragStart(day, emptyList(), null, 600, 30))
        assertNull(nearestPlanningDragStart(day, emptyList(), null, 600, 60))
        assertNull(nearestPlanningDragStart(day(), emptyList(), null, 600, 750))
    }

    @Test
    fun `touching boundaries and unsorted overlapping obstacles are handled`() {
        val day = day(block(1, 630, 700), block(2, 480, 600), block(3, 490, 590))
        assertEquals(600, nearestPlanningDragStart(day, emptyList(), null, 595, 30))
        assertEquals(700, nearestPlanningDragStart(day, emptyList(), null, 650, 30))
    }

    @Test
    fun `same-date drafts obstruct placement except the moving draft itself`() {
        val day = day()
        val moving = PlanningDraftPlacement(day.date, task(42), 540, 600)
        val other = PlanningDraftPlacement(day.date, task(43), 600, 630)
        val anotherDay = PlanningDraftPlacement(day.date.plusDays(1), task(44), 480, 1200)
        val drafts = listOf(moving, other, anotherDay)
        assertEquals(540, nearestPlanningDragStart(day, drafts, 42, 540, 60))
        assertEquals(630, nearestPlanningDragStart(day, drafts, 42, 600, 60))
        assertEquals(630, nearestPlanningDragStart(day, drafts, null, 575, 30))
    }

    private fun day(vararg blocks: TimeBlock) = Day(
        date = LocalDate.of(2026, 8, 20), startHour = 8, endHour = 20,
        showFullDay = false, blocks = blocks.toList(), timezone = "Asia/Singapore",
        today = LocalDate.of(2026, 8, 20), serverNowMinute = 540,
    )

    private fun block(id: Int, start: Int, end: Int) = TimeBlock(
        id = id, lane = Lane.Planned, taskTypeId = 1, taskTypeName = "work",
        taskId = null, task = null, note = null, plannedBlockId = null,
        startMinute = start, endMinute = end,
    )
}
