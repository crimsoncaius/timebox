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

    @Test
    fun `scheduled creation maps one valid Recurring Pre-planning Schedule slot`() {
        val state = base().copy(
            preplanningSlots = listOf(
                RecurringPreplanningSlotDraft(start = "08:30", end = "09:15"),
            ),
        )

        assertNull(validateRecurrenceDraft(state, requireTitle = true))
        val slot = state.toPreplanningSchedule()!!.slots.single()
        assertEquals(510, slot.startMinute)
        assertEquals(555, slot.endMinute)
        assertNull(slot.weekday)

        assertEquals(
            "A Planned Block must end at least 30 minutes after it starts.",
            validateRecurrenceDraft(
                state.copy(preplanningSlots = listOf(
                    RecurringPreplanningSlotDraft(start = "09:00", end = "09:20"),
                )),
                requireTitle = true,
            ),
        )
    }

    @Test
    fun `scheduled creation represents a Planned Block ending at midnight as minute 1440`() {
        val state = base().copy(
            preplanningSlots = listOf(
                RecurringPreplanningSlotDraft(start = "23:00", end = "00:00"),
            ),
        )

        assertNull(validateRecurrenceDraft(state, requireTitle = true))
        val slot = state.toPreplanningSchedule()!!.slots.single()
        assertEquals(1380, slot.startMinute)
        assertEquals(1440, slot.endMinute)
    }

    @Test
    fun `existing Recurring Pre-planning Schedule maps multiple keyed slot edits`() {
        val state = base().copy(preplanningSlots = listOf(
            RecurringPreplanningSlotDraft(
                key = "morning",
                start = "08:30",
                end = "09:15",
            ),
            RecurringPreplanningSlotDraft(
                key = "afternoon",
                start = "15:00",
                end = "16:00",
            ),
        ))

        assertNull(validateRecurrenceDraft(state, requireTitle = true))
        val slots = state.toPreplanningSchedule()!!.slots
        assertEquals(listOf("morning", "afternoon"), slots.map { it.key })
        assertEquals(listOf(510, 900), slots.map { it.startMinute })
        assertEquals(listOf(555, 960), slots.map { it.endMinute })
    }

    private fun base() = RecurringEditorUiState(
        title = "Template",
        startDate = "2026-08-17",
    )
}
