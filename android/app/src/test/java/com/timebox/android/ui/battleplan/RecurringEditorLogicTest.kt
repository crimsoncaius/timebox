package com.timebox.android.ui.battleplan

import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringEditorLogicTest {
    @Test
    fun `scheduled rules cover daily weekly and monthly backend shapes`() {
        val daily = base().copy(interval = "3").toRule()!!
        assertEquals(3, daily.interval)
        assertTrue(daily.weekdays.isEmpty())
        assertNull(daily.monthDay)

        val weekly = base().copy(
            frequency = RecurrenceFrequency.Weekly,
            weekdays = setOf(4, 0, 2),
        ).toRule()!!
        assertEquals(listOf(0, 2, 4), weekly.weekdays)
        assertNull(weekly.monthDay)

        val monthly = base().copy(
            frequency = RecurrenceFrequency.Monthly,
            monthDay = "31",
        ).toRule()!!
        assertEquals(31, monthly.monthDay)
        assertTrue(monthly.weekdays.isEmpty())
    }

    @Test
    fun `quota rules use server calendar periods and force interval one`() {
        RecurrenceFrequency.entries.forEach { frequency ->
            val rule = base().copy(
                mode = RecurrenceMode.Quota,
                frequency = frequency,
                interval = "99",
                quotaCount = "4",
                weekdays = setOf(0),
                monthDay = "20",
            ).toRule()!!
            assertEquals(1, rule.interval)
            assertEquals(4, rule.quotaCount)
            assertTrue(rule.weekdays.isEmpty())
            assertNull(rule.monthDay)
        }
    }

    @Test
    fun `end date and cycle limit remain mutually exclusive`() {
        val dated = base().copy(
            endMode = RecurrenceEndMode.EndDate,
            endDate = "2026-09-01",
            cycleLimit = "12",
        ).toRule()!!
        assertEquals(LocalDate.parse("2026-09-01"), dated.endDate)
        assertNull(dated.cycleLimit)

        val limited = base().copy(
            endMode = RecurrenceEndMode.CycleLimit,
            endDate = "2026-09-01",
            cycleLimit = "12",
        ).toRule()!!
        assertNull(limited.endDate)
        assertEquals(12, limited.cycleLimit)
    }

    @Test
    fun `invalid rules are rejected before preview or save`() {
        assertEquals(
            "Choose at least one weekday for a weekly schedule.",
            validateRecurrenceDraft(base().copy(frequency = RecurrenceFrequency.Weekly), requireTitle = false),
        )
        assertEquals(
            "Month day must be between 1 and 31.",
            validateRecurrenceDraft(base().copy(frequency = RecurrenceFrequency.Monthly, monthDay = "32"), requireTitle = false),
        )
        assertEquals(
            "End date cannot be before the start date.",
            validateRecurrenceDraft(
                base().copy(endMode = RecurrenceEndMode.EndDate, endDate = "2026-08-01"),
                requireTitle = false,
            ),
        )
        assertEquals(
            "Quota count must be between 1 and 100.",
            validateRecurrenceDraft(base().copy(mode = RecurrenceMode.Quota, quotaCount = "0"), requireTitle = false),
        )
    }

    private fun base() = RecurringEditorUiState(
        title = "Template",
        startDate = "2026-08-17",
    )
}
