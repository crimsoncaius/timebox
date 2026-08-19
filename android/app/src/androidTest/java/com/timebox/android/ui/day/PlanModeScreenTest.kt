package com.timebox.android.ui.day

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.Instant

class PlanModeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun planModeReplacesActualWithReadyTaskRail() {
        val date = LocalDate.of(2026, 8, 20)
        val day = Day(
            date = date,
            startHour = 8,
            endHour = 20,
            showFullDay = false,
            blocks = emptyList(),
            timezone = "Asia/Singapore",
            today = date,
            serverNowMinute = 9 * 60,
        )
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = day, loading = false, materialized = true)),
            isPlanningMode = true,
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {}, onCompleteSelected = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithText("Planned", ignoreCase = true).fetchSemanticsNode()
        compose.onNodeWithText("Tasks to plan", ignoreCase = true).fetchSemanticsNode()
        check(compose.onAllNodesWithText("Actual", ignoreCase = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Nothing waiting to be planned.").fetchSemanticsNode()
    }

    @Test
    fun accessibleHandleArmsTaskWithoutMakingCardClickable() {
        val date = LocalDate.of(2026, 8, 20)
        val day = emptyDay(date)
        var armedTaskId: Int? = null
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = day, loading = false, materialized = true)),
            readyTasks = listOf(task(42, "Write brief")),
            isPlanningMode = true,
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {}, onCompleteSelected = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                    onArmAccessibleTask = { armedTaskId = it }, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Schedule Write brief")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { check(armedTaskId == 42) }
    }

    @Test
    fun draggingHandleOntoOpenPlannedTimeRequestsPlacement() {
        val date = LocalDate.of(2026, 8, 20)
        var placement: Pair<Int, Int>? = null
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
            readyTasks = listOf(task(42, "Write brief")),
            isPlanningMode = true,
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {}, onCompleteSelected = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {},
                    onPlanTask = { taskId, minute -> placement = taskId to minute },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Schedule Write brief").performTouchInput {
            swipe(center, center + Offset(-500f, 0f), durationMillis = 700)
        }
        compose.runOnIdle {
            check(placement?.first == 42)
            check(placement?.second != null)
        }
    }

    @Test
    fun draggingHandleOntoOccupiedPlannedTimeIsRejected() {
        val date = LocalDate.of(2026, 8, 20)
        var placement: Pair<Int, Int>? = null
        val occupied = emptyDay(date).copy(
            blocks = listOf(
                TimeBlock(
                    id = 1,
                    lane = Lane.Planned,
                    taskTypeId = 1,
                    taskTypeName = "work",
                    taskId = null,
                    task = null,
                    note = null,
                    plannedBlockId = null,
                    startMinute = 8 * 60,
                    endMinute = 12 * 60,
                ),
            ),
        )
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = occupied, loading = false, materialized = true)),
            readyTasks = listOf(task(42, "Write brief")),
            isPlanningMode = true,
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {}, onCompleteSelected = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {},
                    onPlanTask = { taskId, minute -> placement = taskId to minute },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Schedule Write brief").performTouchInput {
            swipe(center, center + Offset(-500f, 0f), durationMillis = 700)
        }
        compose.runOnIdle { check(placement == null) }
    }

    private fun emptyDay(date: LocalDate) = Day(
        date = date,
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        blocks = emptyList(),
        timezone = "Asia/Singapore",
        today = date,
        serverNowMinute = 9 * 60,
    )

    private fun task(id: Int, title: String) = BattleTask(
        id = id,
        parentId = null,
        parentTitle = null,
        projectId = null,
        project = null,
        taskTypeId = null,
        taskType = null,
        recurringTemplateId = null,
        recurringTemplateTitle = null,
        occurrenceKey = null,
        recurrenceKind = null,
        quotaPeriodStart = null,
        quotaPeriodEnd = null,
        expectedSessions = null,
        sessionIndex = null,
        quotaCompleted = null,
        title = title,
        description = "",
        readyToPlan = true,
        status = TaskStatus.Open,
        urgency = null,
        importance = null,
        deadlineDate = null,
        deadlineAt = null,
        reminderAt = null,
        reminderDeliveredAt = null,
        position = 0,
        archivedAt = null,
        deletedAt = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        overdue = false,
        subtasks = emptyList(),
    )
}
