package com.timebox.android.ui.battleplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TaskDetailValidationTest {
    @Test
    fun dateOnlyDeadlineUsesNextMidnightAsReminderBoundary() {
        val valid = validateTaskDraft(
            TaskDetailUiState(
                title = "Task", timezone = "Asia/Singapore", deadlineMode = TaskDeadlineMode.DateOnly,
                deadlineDate = "2026-08-17", reminderEnabled = true,
                reminderDate = "2026-08-17", reminderTime = "23:59",
            )
        ) as TaskDraftValidation.Valid

        assertEquals(LocalDate.parse("2026-08-17"), valid.deadlineDate)
        assertEquals(Instant.parse("2026-08-17T15:59:00Z"), valid.reminderAt)
    }

    @Test
    fun reminderAtOrAfterDeadlineIsRejected() {
        val result = validateTaskDraft(
            TaskDetailUiState(
                title = "Task", timezone = "Asia/Singapore", deadlineMode = TaskDeadlineMode.DateTime,
                deadlineDate = "2026-08-17", deadlineTime = "10:00", reminderEnabled = true,
                reminderDate = "2026-08-17", reminderTime = "10:00",
            )
        )
        assertTrue(result is TaskDraftValidation.Invalid)
        assertEquals("Reminder must be before the deadline.", (result as TaskDraftValidation.Invalid).message)
    }

    @Test
    fun clearingDeadlineProducesExplicitlyEmptyDomainValues() {
        val valid = validateTaskDraft(TaskDetailUiState(title = "Task", deadlineMode = TaskDeadlineMode.None)) as TaskDraftValidation.Valid
        assertEquals(null, valid.deadlineDate)
        assertEquals(null, valid.deadlineAt)
        assertEquals(null, valid.reminderAt)
    }
}
