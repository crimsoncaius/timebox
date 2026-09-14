package com.timebox.android.ui.battleplan

import androidx.lifecycle.SavedStateHandle
import com.timebox.android.data.PriorityLevel
import org.junit.Assert.*
import org.junit.Test

class TaskSheetFieldTest {
    @Test fun selectingTaskTypeCannotOverwriteAnotherSavedField() {
        val opened = TaskComposerDraft(title = "Task", description = "Old notes").toDetailDraft()
        val latest = opened.copy(description = "Saved notes", importance = PriorityLevel.High)
        val result = mergeTaskField(TaskSheetField.TaskType, latest, opened.copy(taskTypeId = 4))
        assertEquals("Saved notes", result.description)
        assertEquals(PriorityLevel.High, result.importance)
        assertEquals(4, result.taskTypeId)
        assertEquals(latest, mergeTaskField(TaskSheetField.TaskType, result, opened))
    }

    @Test fun importanceAndUrgencyAreIndependent() {
        val initial = TaskComposerDraft(title = "Task", urgency = PriorityLevel.Low).toDetailDraft()
        val changed = mergeTaskField(TaskSheetField.Importance, initial, initial.copy(importance = PriorityLevel.High, urgency = null))
        assertEquals(PriorityLevel.Low, changed.urgency)
        assertEquals(PriorityLevel.High, changed.importance)
    }

    @Test fun removingDeadlineAlsoRemovesReminderButDoesNotChangeReadiness() {
        val initial = TaskComposerDraft(title = "Task", readyToPlan = true,
            deadlineMode = TaskDeadlineMode.DateOnly, deadlineDate = "2026-09-19",
            reminderEnabled = true, reminderDate = "2026-09-19", reminderTime = "09:00").toDetailDraft()
        val changed = mergeTaskField(TaskSheetField.Deadline, initial, initial.copy(deadlineMode = TaskDeadlineMode.None, readyToPlan = false))
        assertFalse(changed.reminderEnabled)
        assertTrue(changed.readyToPlan)
        val validation = validateTaskDraft(TaskDetailUiState(timezone = "Asia/Singapore").withDraft(changed)) as TaskDraftValidation.Valid
        assertNull(validation.deadlineDate)
        assertNull(validation.reminderAt)
    }

    @Test fun creatingFieldEditsPreservesSubtasksAndRestoresThem() {
        val draft = TaskComposerDraft(title = "Task", subtasks = listOf("First", "Second"))
        val changed = draft.withDetailDraft(draft.toDetailDraft().copy(taskTypeId = 3))
        assertEquals(draft.subtasks, changed.subtasks)
        assertTrue(changed.hasMeaningfulChangesFrom(TaskComposerDraft()))
        val handle = SavedStateHandle(mapOf("battlePlan.composer.subtasks" to arrayListOf("First", "Second")))
        assertEquals(draft.subtasks, restoreComposerDraft(handle).subtasks)
    }
}
