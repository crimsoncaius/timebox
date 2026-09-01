package com.timebox.android.ui.battleplan

import androidx.lifecycle.SavedStateHandle
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.TaskStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskComposerValidationTest {
    @Test
    fun titleIsRequired() {
        val result = validateTaskComposer(TaskComposerDraft(title = "   "), "UTC")

        assertEquals(TaskComposerValidation.Invalid("Task title is required."), result)
    }

    @Test
    fun fullDraftMapsToOneCreateRequest() {
        val result = validateTaskComposer(
            TaskComposerDraft(
                title = "  Ship the composer  ",
                description = "  Verify the Android flow.  ",
                status = TaskStatus.InProgress,
                projectId = 3,
                taskTypeId = 7,
                urgency = PriorityLevel.High,
                importance = PriorityLevel.Medium,
                deadlineMode = TaskDeadlineMode.DateTime,
                deadlineDate = "2026-09-05",
                deadlineTime = "14:30",
                reminderEnabled = true,
                reminderDate = "2026-09-05",
                reminderTime = "13:30",
                readyToPlan = true,
            ),
            "Asia/Singapore",
        )

        assertTrue(result is TaskComposerValidation.Valid)
        val request = (result as TaskComposerValidation.Valid).request
        assertEquals("Ship the composer", request.title)
        assertEquals("Verify the Android flow.", request.description)
        assertEquals(TaskStatus.InProgress, request.status)
        assertEquals(3, request.projectId)
        assertEquals(7, request.taskTypeId)
        assertEquals(PriorityLevel.High, request.urgency)
        assertEquals(PriorityLevel.Medium, request.importance)
        assertEquals(true, request.readyToPlan)
        assertEquals(null, request.deadlineDate)
        assertEquals(Instant.parse("2026-09-05T06:30:00Z"), request.deadlineAt)
        assertEquals(Instant.parse("2026-09-05T05:30:00Z"), request.reminderAt)
    }

    @Test
    fun dateOnlyDeadlineRemainsAConfiguredLocalDate() {
        val result = validateTaskComposer(
            TaskComposerDraft(
                title = "Submit paperwork",
                deadlineMode = TaskDeadlineMode.DateOnly,
                deadlineDate = "2026-09-08",
            ),
            "Asia/Singapore",
        ) as TaskComposerValidation.Valid

        assertEquals(LocalDate.parse("2026-09-08"), result.request.deadlineDate)
        assertEquals(null, result.request.deadlineAt)
    }

    @Test
    fun completedIsNeverAValidCreationStatus() {
        val result = validateTaskComposer(
            TaskComposerDraft(title = "Reopened work", status = TaskStatus.Completed),
            "UTC",
        ) as TaskComposerValidation.Valid

        assertEquals(TaskStatus.Open, result.request.status)
    }

    @Test
    fun scopedDefaultsInheritProjectWithoutInheritingCompleted() {
        val draft = initialComposerDraft(
            BattlePlanScope(BattlePlanScopeKind.Project, projectId = 42, label = "Launch"),
            TaskStatus.Completed,
        )

        assertEquals(42, draft.projectId)
        assertEquals(TaskStatus.Open, draft.status)
    }

    @Test
    fun savedDraftRestoresAcrossViewModelRecreation() {
        val draft = restoreComposerDraft(
            SavedStateHandle(
                mapOf(
                    "battlePlan.composer.title" to "Durable draft",
                    "battlePlan.composer.status" to TaskStatus.InProgress.name,
                    "battlePlan.composer.projectId" to 9,
                    "battlePlan.composer.taskTypeId" to 12,
                    "battlePlan.composer.moreOpen" to true,
                    "battlePlan.composer.dirty" to true,
                ),
            ),
        )

        assertEquals("Durable draft", draft.title)
        assertEquals(TaskStatus.InProgress, draft.status)
        assertEquals(9, draft.projectId)
        assertEquals(12, draft.taskTypeId)
        assertEquals(true, draft.moreOpen)
        assertEquals(true, draft.dirty)
    }
}
