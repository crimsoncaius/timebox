package com.timebox.android.ui.day

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/** A fixed elapsed-time window keeps dragging stable as the live clock advances. */
internal data class SwitchTimelineWindow(val start: Instant) {
    val end: Instant get() = start.plusSeconds(3 * 3600)
    fun shift(hours: Long) = SwitchTimelineWindow(start.plusSeconds(hours * 3600))
    fun fraction(at: Instant) = Duration.between(start, at).toMillis().toFloat() / (3 * 3600 * 1000)

    fun select(fraction: Float, earliest: Instant, now: Instant, boundaries: List<Instant>): Instant {
        val raw = Instant.ofEpochSecond(((start.toEpochMilli() / 60000.0) + fraction.coerceIn(0f, 1f) * 180).roundToLong() * 60)
        val bounded = raw.coerceIn(earliest, maxOf(earliest, now))
        // Preserve the actual instant (including seconds and DST occurrence) at a nearby boundary.
        return boundaries.filter { it >= earliest && it <= now }
            .minByOrNull { abs(Duration.between(bounded, it).toMillis()) }
            ?.takeIf { abs(Duration.between(bounded, it).seconds) <= 120 } ?: bounded
    }

    companion object {
        fun around(at: Instant) = SwitchTimelineWindow(Instant.ofEpochSecond(Math.floorDiv(at.epochSecond, 1800) * 1800).minusSeconds(2 * 3600))
    }
}

internal fun switchTimeLabel(at: Instant, zone: ZoneId): String {
    val local = at.atZone(zone)
    val pattern = if (zone.rules.getValidOffsets(local.toLocalDateTime()).size > 1) "HH:mm xxx" else "HH:mm"
    return local.format(DateTimeFormatter.ofPattern(pattern))
}
