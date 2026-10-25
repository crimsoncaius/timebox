package com.timebox.android.ui.day

internal fun parseBlockMinute(value: String): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return when {
        hour == 24 && minute == 0 -> 1440
        hour in 0..23 && minute in 0..59 -> hour * 60 + minute
        else -> null
    }
}
