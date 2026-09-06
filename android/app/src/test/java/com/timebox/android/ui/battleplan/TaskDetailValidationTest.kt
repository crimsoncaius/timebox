package com.timebox.android.ui.battleplan

import androidx.lifecycle.SavedStateHandle
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun validationIdentifiesTheFieldThatNeedsCorrection() {
        val invalid = validateTaskDraft(
            TaskDetailUiState(
                title = "Task",
                deadlineMode = TaskDeadlineMode.DateOnly,
                deadlineDate = "not-a-date",
            )
        ) as TaskDraftValidation.Invalid

        assertEquals(TaskDraftField.DeadlineDate, invalid.field)
        assertEquals("Enter a deadline date as YYYY-MM-DD.", invalid.message)
    }

    @Test
    fun recoveredDraftRoundTripsWithItsBaselineVersionAndNullableSelections() {
        val handle = SavedStateHandle()
        val draft = TaskDetailDraft(
            title = "Recovered title",
            description = "Recovered description",
            status = TaskStatus.InProgress,
            projectId = null,
            taskTypeId = 9,
            urgency = PriorityLevel.High,
            importance = null,
            deadlineMode = TaskDeadlineMode.DateTime,
            deadlineDate = "2026-09-04",
            deadlineTime = "14:30",
            reminderEnabled = true,
            reminderDate = "2026-09-04",
            reminderTime = "13:30",
            readyToPlan = true,
        )

        persistTaskDetailDraft(handle, taskId = 54, baselineVersion = 7, draft = draft)

        assertEquals(PersistedTaskDetailDraft(draft, 7), restoreTaskDetailDraft(handle, 54))
        assertNull(restoreTaskDetailDraft(handle, 55))
        clearTaskDetailDraft(handle)
        assertNull(restoreTaskDetailDraft(handle, 54))
    }

    @Test
    fun normalizationTreatsWhitespaceOnlyTitleAndDescriptionChangesAsClean() {
        val baseline = TaskDetailDraft(
            title = "Task",
            description = "Notes",
            status = TaskStatus.Open,
            projectId = null,
            taskTypeId = null,
            urgency = null,
            importance = null,
            deadlineMode = TaskDeadlineMode.None,
            deadlineDate = "",
            deadlineTime = "",
            reminderEnabled = false,
            reminderDate = "",
            reminderTime = "",
            readyToPlan = false,
        )

        assertEquals(baseline.normalized(), baseline.copy(title = " Task ", description = "Notes ").normalized())
    }
}
