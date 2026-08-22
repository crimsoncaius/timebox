package com.timebox.android.ui.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

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
    fun monthDatesAreMondayFirstAndAlwaysFillSixWeeks() {
        val dates = monthDates(YearMonth.of(2026, 8))

        assertEquals(42, dates.size)
        assertEquals(LocalDate.of(2026, 7, 27), dates.first())
        assertEquals(LocalDate.of(2026, 9, 6), dates.last())
    }

    @Test
    fun selectedDateUsesTitleLedEnglishHeaderFormat() {
        assertEquals("Friday, August 28", formatCalendarHeaderDate(LocalDate.of(2026, 8, 28)))
    }

    @Test
    fun weekSwipeCommitsExactlySevenDaysPastThreshold() {
        assertNull(committedWeekDelta(dragPx = 55f, thresholdPx = 55f))
        assertEquals(7L, committedWeekDelta(dragPx = -55.1f, thresholdPx = 55f))
        assertEquals(-7L, committedWeekDelta(dragPx = 55.1f, thresholdPx = 55f))
    }

    @Test
    fun monthSwipeCommitsOneMonthPastThreshold() {
        assertNull(committedMonthDelta(dragPx = 55f, thresholdPx = 55f))
        assertEquals(1L, committedMonthDelta(dragPx = -55.1f, thresholdPx = 55f))
        assertEquals(-1L, committedMonthDelta(dragPx = 55.1f, thresholdPx = 55f))
    }
}
