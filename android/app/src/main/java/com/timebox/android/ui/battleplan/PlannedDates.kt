package com.timebox.android.ui.battleplan

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class PlannedDateTone { Today, Future, Past }

internal data class PlannedDateSummary(
    val primaryDate: LocalDate,
    val dateLabel: String,
    val relativeLabel: String?,
    val additionalCount: Int,
    val tone: PlannedDateTone,
) {
    val label: String
        get() = buildString {
            append("Planned ")
            relativeLabel?.let { append(it).append(" · ") }
            append(dateLabel)
            if (additionalCount > 0) append(" +").append(additionalCount)
        }
}

internal fun orderedPlannedDates(dates: List<LocalDate>, today: LocalDate): List<LocalDate> {
    val distinct = dates.distinct().sorted()
    return distinct.filter { it == today } +
        distinct.filter { it > today } +
        distinct.filter { it < today }.asReversed()
}

internal fun plannedDateSummary(
    dates: List<LocalDate>,
    now: Instant,
    timezone: String,
    locale: Locale = Locale.getDefault(),
): PlannedDateSummary? {
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
    val today = now.atZone(zone).toLocalDate()
    val ordered = orderedPlannedDates(dates, today)
    val primary = ordered.firstOrNull() ?: return null
    val difference = primary.toEpochDay() - today.toEpochDay()
    return PlannedDateSummary(
        primaryDate = primary,
        dateLabel = formatPlannedCalendarDate(primary, today, locale),
        relativeLabel = when (difference) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> null
        },
        additionalCount = ordered.size - 1,
        tone = when {
            difference == 0L -> PlannedDateTone.Today
            difference > 0L -> PlannedDateTone.Future
            else -> PlannedDateTone.Past
        },
    )
}

internal data class AppClockAnchor(
    val serverNow: Instant,
    val clientNowAtReceipt: Instant = Instant.now(),
) {
    fun current(clientNow: Instant = Instant.now()): Instant =
        serverNow.plus(Duration.between(clientNowAtReceipt, clientNow))
}

internal fun millisUntilNextAppMidnight(now: Instant, timezone: String): Long {
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
    val nextMidnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
}

internal fun formatPlannedDetailDate(
    date: LocalDate,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): String {
    val dateLabel = formatPlannedCalendarDate(date, today, locale)
    val relative = when (date.toEpochDay() - today.toEpochDay()) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> null
    }
    return if (relative == null) dateLabel else "$relative · $dateLabel"
}

private fun formatPlannedCalendarDate(date: LocalDate, today: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern(
        if (date.year == today.year) "MMM d" else "MMM d, uuuu",
        locale,
    ))
