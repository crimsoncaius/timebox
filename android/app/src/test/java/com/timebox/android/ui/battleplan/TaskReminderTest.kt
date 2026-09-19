package com.timebox.android.ui.battleplan

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskReminderTest {
    @Test fun suggestsNextLocalHourOrTomorrowMorning() {
        listOf(
            Triple("2026-11-01T05:30:00Z", "America/New_York", "2026-11-01T07:00:00Z"),
            Triple("2026-03-08T06:30:00Z", "America/New_York", "2026-03-08T07:00:00Z"),
            Triple("2026-09-19T04:30:00Z", "Asia/Singapore", "2026-09-19T05:00:00Z"),
            Triple("2026-09-19T04:00:00Z", "Asia/Singapore", "2026-09-19T05:00:00Z"),
            Triple("2026-09-19T15:00:00Z", "Asia/Singapore", "2026-09-20T01:00:00Z"),
            Triple("2026-09-19T23:30:00Z", "America/New_York", "2026-09-20T00:00:00Z"),
        ).forEach { (now, zone, expected) ->
            assertEquals(Instant.parse(expected), suggestedReminderStart(Instant.parse(now), ZoneId.of(zone)).toInstant())
        }
    }
}
