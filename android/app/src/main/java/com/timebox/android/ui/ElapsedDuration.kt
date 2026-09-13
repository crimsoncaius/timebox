package com.timebox.android.ui

/** Elapsed durations use fixed 24-hour days, independently of calendar dates. */
fun elapsedDuration(minutes: Long): String = durationUnits(minutes * 60, false)

fun elapsedDurationSeconds(seconds: Long): String = durationUnits(seconds, true)

private fun durationUnits(seconds: Long, includeSeconds: Boolean): String {
    val units = listOf(
        seconds / 86400 to "day",
        seconds / 3600 % 24 to "hour",
        seconds / 60 % 60 to "min",
    ) + if (includeSeconds) listOf(seconds % 60 to "sec") else emptyList()
    return units.filter { it.first > 0 }.joinToString(" ") { (value, unit) ->
        "$value $unit${if (value == 1L) "" else "s"}"
    }.ifEmpty { if (includeSeconds) "0 secs" else "0 mins" }
}
