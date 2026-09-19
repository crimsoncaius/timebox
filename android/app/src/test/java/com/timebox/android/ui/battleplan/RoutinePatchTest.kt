package com.timebox.android.ui.battleplan

import com.timebox.android.data.*
import com.timebox.android.data.remote.PatchField
import org.junit.Assert.*
import org.junit.Test

class RoutinePatchTest {
    private val baseline = RecurringEditorUiState(templateId = 1, title = "Review", startDate = "2026-09-21", frequency = RecurrenceFrequency.Weekly, weekdays = setOf(0), description = "Original")

    @Test fun `renaming does not send recurrence or checklist fields`() {
        val patch = recurringDraftPatch(baseline, baseline.copy(title = "New name"), false)
        assertEquals(PatchField.of("New name"), patch.title)
        assertEquals(PatchField.Absent, patch.frequency)
        assertEquals(PatchField.Absent, patch.startDate)
        assertEquals(PatchField.Absent, patch.weekdays)
        assertEquals(PatchField.Absent, patch.checklistTitles)
        assertEquals(PatchField.Absent, patch.preplanningSchedule)
    }
    @Test fun `clearing optional fields sends null and leaves others absent`() {
        val before = baseline.copy(importance = PriorityLevel.High, taskTypeId = 4)
        val patch = recurringDraftPatch(before, before.copy(importance = null, taskTypeId = null), false)
        assertEquals(PatchField.Null, patch.importance)
        assertEquals(PatchField.Null, patch.taskTypeId)
        assertEquals(PatchField.Absent, patch.urgency)
        assertEquals(PatchField.Absent, patch.description)
    }
    @Test fun `changing monthly to weekly clears the monthly day`() {
        val before = baseline.copy(frequency = RecurrenceFrequency.Monthly, monthDay = "31", weekdays = emptySet())
        val patch = recurringDraftPatch(before, before.copy(frequency = RecurrenceFrequency.Weekly, weekdays = setOf(0, 3)), true)
        assertEquals(PatchField.Null, patch.monthDay)
        assertEquals(PatchField.of(listOf(0, 3)), patch.weekdays)
        assertEquals(PatchField.of(true), patch.confirmBackfill)
    }
    @Test fun `replacing dated ending with cycle limit clears end date`() {
        val before = baseline.copy(endMode = RecurrenceEndMode.EndDate, endDate = "2026-12-21")
        val patch = recurringDraftPatch(before, before.copy(endMode = RecurrenceEndMode.CycleLimit, cycleLimit = "5"), false)
        assertEquals(PatchField.Null, patch.endDate)
        assertEquals(PatchField.of(5), patch.cycleLimit)
    }
    @Test fun `removing preplanning sends explicit null`() {
        val before = baseline.copy(preplanningSlots = listOf(RecurringPreplanningSlotDraft(weekday = 0)))
        assertEquals(PatchField.Null, recurringDraftPatch(before, baseline, false).preplanningSchedule)
    }
    @Test fun `quota summaries retain their period and scheduled summaries their selected days`() {
        val quota = baseline.copy(mode = RecurrenceMode.Quota, quotaCount = "3", frequency = RecurrenceFrequency.Monthly)
        assertEquals("3 times per month", routineRuleSummary(quota))
        assertEquals("Every 2 weeks · Mon, Thu", routineRuleSummary(baseline.copy(interval = "2", weekdays = setOf(3, 0))))
    }
}
