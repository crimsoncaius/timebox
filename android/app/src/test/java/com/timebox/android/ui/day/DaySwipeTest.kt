package com.timebox.android.ui.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DaySwipeTest {

    @Test
    fun shortAndBoundaryDragsSnapBack() {
        assertNull(committedDayDelta(dragPx = 0f, thresholdPx = 55f))
        assertNull(committedDayDelta(dragPx = 54.9f, thresholdPx = 55f))
        assertNull(committedDayDelta(dragPx = -55f, thresholdPx = 55f))
    }

    @Test
    fun leftDragAdvancesAndRightDragRewindsOneDay() {
        assertEquals(1L, committedDayDelta(dragPx = -55.1f, thresholdPx = 55f))
        assertEquals(-1L, committedDayDelta(dragPx = 55.1f, thresholdPx = 55f))
    }

    @Test
    fun nowLineTargetsOneThirdFromTopAndClampsToTimelineBounds() {
        assertEquals(500, initialNowScrollOffset(lineOffsetPx = 800f, viewportHeightPx = 900, maxScroll = 1_000))
        assertEquals(0, initialNowScrollOffset(lineOffsetPx = 100f, viewportHeightPx = 900, maxScroll = 1_000))
        assertEquals(600, initialNowScrollOffset(lineOffsetPx = 1_200f, viewportHeightPx = 900, maxScroll = 600))
    }
}
