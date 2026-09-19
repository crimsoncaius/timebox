package com.timebox.android.ui.battleplan

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal fun suggestedReminderStart(now: Instant, zone: ZoneId): ZonedDateTime {
    val local = now.atZone(zone)
    var nextHour = local.withSecond(0).withNano(0).plusMinutes(1)
    // Skip a repeated local hour: the editor stores local time without an offset.
    while (nextHour.minute != 0 || !nextHour.toLocalDateTime().isAfter(local.toLocalDateTime())) {
        nextHour = nextHour.plusMinutes(1)
    }
    return if (nextHour.toLocalDate() == local.toLocalDate()) nextHour
    else local.toLocalDate().plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone)
}
