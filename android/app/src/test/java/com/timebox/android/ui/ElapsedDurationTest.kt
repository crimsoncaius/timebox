package com.timebox.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedDurationTest {
    @Test fun `minutes roll into hours and days`() {
        listOf(0L to "0 mins", 1L to "1 min", 30L to "30 mins", 60L to "1 hour",
            90L to "1 hour 30 mins", 120L to "2 hours", 180L to "3 hours",
            1440L to "1 day", 1530L to "1 day 1 hour 30 mins", 2881L to "2 days 1 min",
        ).forEach { (minutes, expected) -> assertEquals(expected, elapsedDuration(minutes)) }
    }

    @Test fun `focus keeps second precision across unit boundaries`() {
        listOf(0L to "0 secs", 1L to "1 sec", 59L to "59 secs", 60L to "1 min",
            3599L to "59 mins 59 secs", 3600L to "1 hour", 10807L to "3 hours 7 secs",
            90061L to "1 day 1 hour 1 min 1 sec",
        ).forEach { (seconds, expected) -> assertEquals(expected, elapsedDurationSeconds(seconds)) }
    }
}
