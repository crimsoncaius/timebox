package com.timebox.android.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DailyReminderScheduleTest {
    private val singapore = ZoneId.of("Asia/Singapore")

    @Test
    fun `enabled reminders schedule independently at their selected local times`() {
        val schedule = nextDailyReminderSchedules(
            DailyReminderSettings(
                planning = DailyReminder(enabled = true, time = LocalTime.of(9, 0)),
                review = DailyReminder(enabled = true, time = LocalTime.of(18, 0)),
            ),
            LocalDateTime.of(2026, 9, 7, 8, 30).atZone(singapore),
        )

        assertEquals(
            listOf(
                DailyReminderScheduleEntry(DailyReminderKind.Planning, LocalDateTime.of(2026, 9, 7, 9, 0).atZone(singapore).toInstant()),
                DailyReminderScheduleEntry(DailyReminderKind.Review, LocalDateTime.of(2026, 9, 7, 18, 0).atZone(singapore).toInstant()),
            ),
            schedule,
        )
    }

    @Test
    fun `a missed prompt is scheduled tomorrow rather than delivered late`() {
        val schedule = nextDailyReminderSchedules(
            DailyReminderSettings(planning = DailyReminder(true, LocalTime.of(9, 0))),
            LocalDateTime.of(2026, 9, 7, 9, 1).atZone(singapore),
        )

        assertEquals(
            listOf(DailyReminderScheduleEntry(DailyReminderKind.Planning, LocalDateTime.of(2026, 9, 8, 9, 0).atZone(singapore).toInstant())),
            schedule,
        )
    }
}
