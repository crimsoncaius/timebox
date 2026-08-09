package com.timebox.android.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** `13:00` — the timeline and sheet both use 24-hour times. */
fun hhmm(minute: Int): String {
    val h = (minute / 60).coerceIn(0, 24)
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}

/** `1 PM` — hour marks in the timeline gutter. */
fun gutterLabel(minute: Int): String {
    val hour = minute / 60
    val suffix = if (hour < 12 || hour == 24) "AM" else "PM"
    val h12 = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return "$h12 $suffix"
}

/** `2h 30m` — durations and totals. */
fun duration(minutes: Int): String = "${minutes / 60}h ${"%02d".format(minutes % 60)}m"

/** `2h 30m`, or `45m` when under an hour — used in prose. */
fun durationShort(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

private val fullDate = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
private val shortDate = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
private val isoDate = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.getDefault())
private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

fun formatFullDate(date: LocalDate): String = date.format(fullDate)

fun formatShortDate(date: LocalDate): String = date.format(shortDate)

fun formatDayStrip(date: LocalDate): String = date.format(isoDate)

fun formatMonthTitle(date: LocalDate): String = date.format(monthTitle)

/** Last path segment: `coding/ai` renders as `ai` with the full path beneath. */
fun leafOf(path: String): String = path.substringAfterLast('/')
