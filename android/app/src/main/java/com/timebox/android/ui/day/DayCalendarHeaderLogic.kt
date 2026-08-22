package com.timebox.android.ui.day

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val calendarHeaderFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)

internal fun weekDates(selectedDate: LocalDate): List<LocalDate> {
    val monday = selectedDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    return (0L..6L).map(monday::plusDays)
}

internal fun monthDates(month: YearMonth): List<LocalDate> {
    val firstDate = month.atDay(1)
    val gridStart = firstDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    return (0L until 42L).map(gridStart::plusDays)
}

internal fun formatCalendarHeaderDate(date: LocalDate): String = date.format(calendarHeaderFormatter)

internal fun committedWeekDelta(dragPx: Float, thresholdPx: Float): Long? = when {
    dragPx < -thresholdPx -> 7L
    dragPx > thresholdPx -> -7L
    else -> null
}

internal fun committedMonthDelta(dragPx: Float, thresholdPx: Float): Long? = when {
    dragPx < -thresholdPx -> 1L
    dragPx > thresholdPx -> -1L
    else -> null
}
