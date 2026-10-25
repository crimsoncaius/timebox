package com.timebox.android.ui.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DayCalendarHeaderLogicTest {

    @Test
    fun weekDatesAreMondayFirstAcrossMonthAndYearBoundaries() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 12, 28),
                LocalDate.of(2026, 12, 29),
                LocalDate.of(2026, 12, 30),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 2),
                LocalDate.of(2027, 1, 3),
            ),
            weekDates(LocalDate.of(2027, 1, 1)),
        )
    }

    @Test
    fun titleNamesTheWeekdayAndWhetherItIsTodayYesterdayOrTomorrow() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals("Saturday · Today", dayTitleKicker(today, today))
        assertEquals("Friday · Yesterday", dayTitleKicker(today.minusDays(1), today))
        assertEquals("Sunday · Tomorrow", dayTitleKicker(today.plusDays(1), today))
        assertEquals("Monday", dayTitleKicker(today.plusDays(2), today))
        assertEquals("Saturday", dayTitleKicker(today, null))
    }

    @Test
    fun titleShowsTheYearOnlyOutsideTodaysYear() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals("24 October", dayTitle(today, today))
        assertEquals("31 December 2025", dayTitle(LocalDate.of(2025, 12, 31), today))
    }

    @Test
    fun weekSwipeCommitsExactlySevenDaysPastThreshold() {
        assertNull(committedWeekDelta(dragPx = 55f, thresholdPx = 55f))
        assertEquals(7L, committedWeekDelta(dragPx = -55.1f, thresholdPx = 55f))
        assertEquals(-7L, committedWeekDelta(dragPx = 55.1f, thresholdPx = 55f))
    }
}
