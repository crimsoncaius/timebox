package com.timebox.android.ui.battleplan

import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.RecurrenceStatus
import com.timebox.android.data.RecurringTemplate
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurringListCardLogicTest {
    @Test
    fun scheduledSeriesShowNextOccurrenceOnTheThirdChip() {
        val template = series(mode = RecurrenceMode.Scheduled, next = LocalDate.of(2026, 9, 21))
        assertEquals("21 Sep", recurringListDateLabel(template.nextOccurrence))
        assertEquals("Next 21 Sep", recurringListTimingFact(template))
    }

    @Test
    fun quotaSeriesShowPeriodEndOnTheThirdChip() {
        val template = series(mode = RecurrenceMode.Quota, next = LocalDate.of(2026, 9, 21))
        assertEquals("Period ends 21 Sep", recurringListTimingFact(template))
    }

    @Test
    fun missingDatesUseModeSpecificEmptyCopy() {
        assertEquals("No next occurrence", recurringListTimingFact(series(mode = RecurrenceMode.Scheduled, next = null)))
        assertEquals("No current period", recurringListTimingFact(series(mode = RecurrenceMode.Quota, next = null)))
        assertNull(recurringListDateLabel(null))
    }

    private fun series(mode: RecurrenceMode, next: LocalDate?) = RecurringTemplate(
        id = 1,
        title = "Weekly review",
        description = "",
        taskTypeId = null,
        taskType = null,
        mode = mode,
        status = RecurrenceStatus.Active,
        frequency = RecurrenceFrequency.Weekly,
        interval = 1,
        weekdays = emptyList(),
        monthDay = null,
        quotaCount = if (mode == RecurrenceMode.Quota) 3 else null,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = null,
        cycleLimit = null,
        urgency = null,
        importance = null,
        pausedAt = null,
        endedAt = null,
        createdAt = Instant.parse("2026-09-15T02:00:00Z"),
        updatedAt = Instant.parse("2026-09-15T02:00:00Z"),
        checklistItems = emptyList(),
        upcoming = emptyList(),
        currentTasks = emptyList(),
        cadence = if (mode == RecurrenceMode.Quota) "3 times per week" else "Every 1 week · Mon",
        nextOccurrence = next,
    )
}
