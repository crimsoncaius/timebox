package com.timebox.android.data

import com.timebox.android.data.remote.PatchField
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PatchEncodingTest {
    @Test
    fun `task patch distinguishes absent value and explicit null`() {
        val body = BattleTaskPatch(
            title = PatchField.of("Renamed"),
            projectId = PatchField.clear(),
            taskTypeId = PatchField.Absent,
            deadlineDate = PatchField.of(LocalDate.parse("2026-09-01")),
            reminderAt = PatchField.clear(),
        ).toJson()

        assertEquals("Renamed", body.getValue("title").jsonPrimitive.content)
        assertSame(JsonNull, body.getValue("project_id"))
        assertFalse("task_type_id" in body)
        assertEquals("2026-09-01", body.getValue("deadline_date").jsonPrimitive.content)
        assertSame(JsonNull, body.getValue("reminder_at"))
    }

    @Test
    fun `recurrence patch can clear rule and relationship fields`() {
        val body = RecurringTemplatePatch(
            projectId = PatchField.clear(),
            endDate = PatchField.clear(),
            cycleLimit = PatchField.of(8),
            weekdays = PatchField.of(listOf(0, 2, 4)),
        ).toJson()
        assertSame(JsonNull, body.getValue("project_id"))
        assertSame(JsonNull, body.getValue("end_date"))
        assertEquals("8", body.getValue("cycle_limit").jsonPrimitive.content)
        assertTrue("weekdays" in body)
        assertFalse("title" in body)
    }

    @Test
    fun `time block patch explicitly clears only linked task`() {
        val body = timeBlockPatchBody(
            taskTypeId = null,
            taskId = PatchField.clear(),
            note = null,
            startMinute = 600,
            endMinute = null,
        )
        assertSame(JsonNull, body.getValue("task_id"))
        assertEquals("600", body.getValue("start_minute").jsonPrimitive.content)
        assertFalse("task_type_id" in body)
        assertFalse("note" in body)
    }
}
