package com.timebox.android.ui.calendar

import com.timebox.android.ui.day.ActivityTimeValue
import kotlinx.serialization.json.*
import java.time.Duration
import java.time.ZoneId

/** Local conflict-resolution draft; conversion requires deliberate DST occurrence choices. */
internal data class CalendarPlanEdit(
    val id: Int,
    val operation: String,
    val start: ActivityTimeValue,
    val end: ActivityTimeValue,
) {
    fun duration(zone: ZoneId): Long {
        val minutes = Duration.between(start.resolve(zone), end.resolve(zone)).toMinutes()
        require(minutes > 0) { "End must be after start." }
        return minutes
    }

    fun payload(zone: ZoneId): JsonObject = buildJsonObject {
        put("id", id); put("action", operation)
        if (operation == "move") {
            duration(zone)
            put("start", start.resolve(zone).toString())
            put("end", end.resolve(zone).toString())
        }
    }
}
