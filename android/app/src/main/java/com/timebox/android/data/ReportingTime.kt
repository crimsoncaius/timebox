package com.timebox.android.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Offline editor conversion. Repeated times require a deliberate occurrence choice. */
object ReportingTime {
    enum class Occurrence { Earlier, Later }
    fun candidates(local: LocalDateTime, zone: ZoneId): List<Instant> =
        zone.rules.getValidOffsets(local).map { local.toInstant(it) }.sorted()

    fun resolve(local: LocalDateTime, zone: ZoneId, occurrence: Occurrence? = null): Instant {
        val candidates = candidates(local, zone)
        require(candidates.isNotEmpty()) { "That local time does not exist in $zone." }
        require(candidates.size == 1 || occurrence != null) { "That local time occurs twice; choose the earlier or later occurrence." }
        return if (occurrence == Occurrence.Later) candidates.last() else candidates.first()
    }
}
