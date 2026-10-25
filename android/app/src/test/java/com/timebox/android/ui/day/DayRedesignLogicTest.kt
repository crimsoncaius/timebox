package com.timebox.android.ui.day

import com.timebox.android.data.ActualBlock
import com.timebox.android.data.ActualBlockDayProjection
import com.timebox.android.data.ApiError
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.remote.ActivityPlanDto
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayRedesignLogicTest {
    private val today = LocalDate.of(2026, 10, 24)

    private fun block(id: Int, lane: Lane, start: Int, end: Int) = TimeBlock(
        id = id, lane = lane, taskTypeId = 1, taskTypeName = "work", taskId = null, task = null,
        note = null, plannedBlockId = null, actualBlockId = if (lane == Lane.Actual) id else null,
        startMinute = start, endMinute = end,
    )

    private fun day(date: LocalDate = today, now: Int? = 17 * 60, vararg blocks: TimeBlock) = Day(
        date = date, blocks = blocks.toList(), timezone = "UTC", today = today,
        serverNowMinute = now, elapsedRealtime = { 0L }, capturedAtElapsedMillis = 0L,
    )

    @Test
    fun todayOpensAtTheNowLineAndOtherDatesAtTheirFirstBlockOrEightAm() {
        assertEquals(InitialScroll(17 * 60, oneThirdDown = true), initialScroll(day(), null))
        assertEquals(InitialScroll(600, oneThirdDown = true), initialScroll(day(), 600))
        val past = day(today.minusDays(1), null, block(1, Lane.Planned, 540, 600), block(2, Lane.Actual, 420, 450))
        assertEquals(InitialScroll(420, oneThirdDown = false), initialScroll(past, null))
        assertEquals(InitialScroll(DEFAULT_SCROLL_MINUTE, oneThirdDown = false), initialScroll(day(today.plusDays(1), null), null))
    }

    @Test
    fun untrackedGapsLieBetweenActualBlocksAndUpToNowOnToday() {
        val d = day(today, 17 * 60, block(1, Lane.Actual, 8 * 60, 9 * 60), block(2, Lane.Actual, 9 * 60 + 10, 10 * 60),
            block(3, Lane.Actual, 11 * 60, 12 * 60), block(4, Lane.Planned, 13 * 60, 14 * 60))
        assertEquals(
            listOf(UntrackedGap(10 * 60, 11 * 60), UntrackedGap(12 * 60, 17 * 60)),
            untrackedGaps(d, 17 * 60),
        )
    }

    @Test
    fun pastDaysHaveNoTrailingGapAndFutureOrEmptyDaysHaveNone() {
        val past = day(today.minusDays(1), null, block(1, Lane.Actual, 8 * 60, 9 * 60), block(2, Lane.Actual, 10 * 60, 11 * 60))
        assertEquals(listOf(UntrackedGap(9 * 60, 10 * 60)), untrackedGaps(past, null))
        assertTrue(untrackedGaps(day(today.plusDays(1), null, block(1, Lane.Actual, 60, 120), block(2, Lane.Actual, 300, 360)), null).isEmpty())
        assertTrue(untrackedGaps(day(today, 17 * 60), 17 * 60).isEmpty())
    }

    @Test
    fun runningClockShowsMinutesAndSecondsThenHours() {
        assertEquals("0:00", runningClock(-5))
        assertEquals("27:14", runningClock(27 * 60 + 14))
        assertEquals("1:02:03", runningClock(3723))
    }

    @Test
    fun plannedNowHintSwitchesCountsOrConfirmsThePlan() {
        val plan = ActivityPlanDto(4, 2, null, "Gym", null, "2026-10-24T09:30:00Z", "2026-10-24T10:30:00Z")
        val zone = ZoneId.of("Asia/Singapore")
        assertNull(plannedNowHint(null, null, false, "", zone))
        assertEquals(PlanHint.OnPlan(), plannedNowHint(plan, 4, true, "Gym", zone))
        assertTrue(plannedNowHint(plan, null, true, "Gym", zone) is PlanHint.CountToward)
        assertEquals(PlanHint.SwitchTo("Gym planned at 17:30"), plannedNowHint(plan, null, false, "Gym", zone, Locale.US))
    }

    @Test
    fun loadProblemsMapFromTheErrorNeverItsText() {
        assertEquals(LoadProblem.Offline, ApiError("socket closed", isNetwork = true).loadProblem())
        assertEquals(LoadProblem.SignedOut, ApiError("x", isAuth = true, statusCode = 401).loadProblem())
        assertEquals(LoadProblem.ServerProblem, ApiError("x", statusCode = 500).loadProblem())
        assertEquals(LoadProblem.Other, ApiError("x", statusCode = 404).loadProblem())
        LoadProblem.entries.forEach { problem ->
            assertTrue(!neverLoadedCause(problem).contains("socket") && !staleDayStatus(problem).contains("500"))
        }
    }

    @Test
    fun blockTitlesWrapAsTheBlockGrowsTaller() {
        assertEquals(1, blockTitleLines(30f))
        assertEquals(2, blockTitleLines(44f))
        assertEquals(3, blockTitleLines(90f))
    }

    @Test
    fun laneHeadersShowNonZeroTotals() {
        assertEquals("Planned 8h 15m", laneHeader("Planned", 495))
        assertEquals("Actual", laneHeader("Actual", 0))
    }
}
