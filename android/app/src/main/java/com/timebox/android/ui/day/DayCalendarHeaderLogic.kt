package com.timebox.android.ui.day

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val titleWeekdayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val titleFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
private val titleWithYearFormatter = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH)

internal fun weekDates(selectedDate: LocalDate): List<LocalDate> {
    val monday = selectedDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    return (0L..6L).map(monday::plusDays)
}

/** The weekday above the Day title, naming Today, Yesterday or Tomorrow when it is one of them. */
internal fun dayTitleKicker(date: LocalDate, today: LocalDate?): String {
    val relative = when (today) {
        null -> null
        date -> "Today"
        date.plusDays(1) -> "Yesterday"
        date.minusDays(1) -> "Tomorrow"
        else -> null
    }
    val weekday = date.format(titleWeekdayFormatter)
    return if (relative == null) weekday else "$weekday \u00B7 $relative"
}

/** The Day title; the year appears only outside Today's year. */
internal fun dayTitle(date: LocalDate, today: LocalDate?): String =
    date.format(if (today == null || date.year == today.year) titleFormatter else titleWithYearFormatter)

internal fun committedWeekDelta(dragPx: Float, thresholdPx: Float): Long? = when {
    dragPx < -thresholdPx -> 7L
    dragPx > thresholdPx -> -7L
    else -> null
}
