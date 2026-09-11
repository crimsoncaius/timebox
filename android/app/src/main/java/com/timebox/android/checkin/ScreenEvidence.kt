package com.timebox.android.checkin

/** A system screen transition, never an inference from a missing application event. */
data class ScreenObservation(val at: Long, val interactive: Boolean)

object ScreenEvidence {
    fun supported(api: Int) = api >= 28

    /** Latest explicit off interval; an open interval requires the current power state. */
    fun interval(events: List<ScreenObservation>, now: Long, interactive: Boolean): Pair<Long, Long>? {
        var off: Long? = null
        var completed: Pair<Long, Long>? = null
        for (event in events.sortedBy { it.at }) {
            if (event.at > now) return null
            if (!event.interactive) {
                if (off == null) off = event.at
            } else {
                off?.let { if (it < event.at) completed = it to event.at }
                off = null
            }
        }
        return off?.let { if (!interactive && it < now) it to now else null } ?: completed
    }
}
