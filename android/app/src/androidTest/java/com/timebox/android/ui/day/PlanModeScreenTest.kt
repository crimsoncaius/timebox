package com.timebox.android.ui.day

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
                    onReverseSelectedCompletion = {}, onRequestSelectedTaskCompletion = {},
                    onConfirmSelectedTaskCompletion = {}, onDismissTaskCompletion = {},
                    onReopenSelectedTask = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithText("Planned", ignoreCase = true).fetchSemanticsNode()
        compose.onNodeWithText("Done").fetchSemanticsNode()
        compose.onNodeWithText("Tasks to plan", ignoreCase = true).fetchSemanticsNode()
        check(compose.onAllNodesWithText("Actual", ignoreCase = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Nothing waiting to be planned.").fetchSemanticsNode()
    }

    @Test
    fun androidBackCancelsInsteadOfCommittingPlanningSession() {
        val date = LocalDate.of(2026, 8, 20)
        var cancelled = false
        var committed = false
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
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
                    onReverseSelectedCompletion = {}, onRequestSelectedTaskCompletion = {},
                    onConfirmSelectedTaskCompletion = {}, onDismissTaskCompletion = {},
                    onReopenSelectedTask = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {},
                    onCommitPlanningMode = { committed = true },
                    onCancelPlanningMode = { cancelled = true },
                    onPlanTask = { _, _ -> }, onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        pressBack()

        compose.runOnIdle {
            check(cancelled)
            check(!committed)
        }
    }

    @Test
    fun accessibleTaskCardArmsTask() {
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
                    onReverseSelectedCompletion = {}, onRequestSelectedTaskCompletion = {},
                    onConfirmSelectedTaskCompletion = {}, onDismissTaskCompletion = {},
                    onReopenSelectedTask = {},
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
    fun draggingTaskCardOntoOpenPlannedTimeRequestsPlacement() {
        val date = LocalDate.of(2026, 8, 20)
        var placement: Pair<Int, Int>? = null
        val readyTask = task(42, "Write brief")
        var state by mutableStateOf(
            DayUiState(
                date = date,
                pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
                readyTasks = listOf(readyTask),
                isPlanningMode = true,
            )
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {}, onCompleteSelected = {},
                    onReverseSelectedCompletion = {}, onRequestSelectedTaskCompletion = {},
                    onConfirmSelectedTaskCompletion = {}, onDismissTaskCompletion = {},
                    onReopenSelectedTask = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {},
                    onPlanTask = { taskId, minute ->
                        placement = taskId to minute
                        state = state.copy(
                            planningDrafts = state.planningDrafts + (
                                taskId to PlanningDraftPlacement(date, readyTask, minute, minute + 30)
                            )
                        )
                    },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithText("Write brief").performTouchInput {
            swipe(center, center + Offset(-500f, 0f), durationMillis = 700)
        }
        compose.runOnIdle {
            check(placement?.first == 42)
            check(placement?.second != null)
        }
        compose.onNodeWithContentDescription("Planning draft Write brief").fetchSemanticsNode()
    }

    @Test
    fun draggingTaskCardOntoOccupiedPlannedTimeIsRejected() {
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
                    onReverseSelectedCompletion = {}, onRequestSelectedTaskCompletion = {},
                    onConfirmSelectedTaskCompletion = {}, onDismissTaskCompletion = {},
                    onReopenSelectedTask = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {},
                    onPlanTask = { taskId, minute -> placement = taskId to minute },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithText("Write brief").performTouchInput {
            swipe(center, center + Offset(-500f, 0f), durationMillis = 700)
        }
        compose.runOnIdle { check(placement == null) }
    }

    @Test
    fun persistedPlannedCardIsLockedInPlanMode() {
        val date = LocalDate.of(2026, 8, 20)
        var moved = false
        val state = DayUiState(
            date = date,
            pages = mapOf(
                date to DayPageState(
                    day = emptyDay(date).copy(
                        blocks = listOf(
                            TimeBlock(
                                id = 1,
                                lane = Lane.Planned,
                                taskTypeId = 1,
                                taskTypeName = "Locked work",
                                taskId = null,
                                task = null,
                                note = null,
                                plannedBlockId = null,
                                startMinute = 9 * 60,
                                endMinute = 10 * 60,
                            ),
                        ),
                    ),
                    loading = false,
                    materialized = true,
                )
            ),
            isPlanningMode = true,
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> moved = true }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {}, onCompleteSelected = {},
                    onReverseSelectedCompletion = {}, onRequestSelectedTaskCompletion = {},
                    onConfirmSelectedTaskCompletion = {}, onDismissTaskCompletion = {},
                    onReopenSelectedTask = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithText("Locked work").performTouchInput {
            swipe(center, center + Offset(0f, 180f), durationMillis = 500)
        }
        compose.runOnIdle { check(!moved) }
    }

    @Test
    fun bluePlanningDraftCanReturnToTaskRail() {
        val date = LocalDate.of(2026, 8, 20)
        val draftTask = task(42, "Return me")
        var returnedTaskId: Int? = null
        var state by mutableStateOf(
            DayUiState(
                date = date,
                pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
                readyTasks = listOf(draftTask),
                planningDrafts = mapOf(
                    draftTask.id to PlanningDraftPlacement(date, draftTask, 9 * 60, 10 * 60)
                ),
                isPlanningMode = true,
            )
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {}, onCompleteSelected = {},
                    onReverseSelectedCompletion = {}, onRequestSelectedTaskCompletion = {},
                    onConfirmSelectedTaskCompletion = {}, onDismissTaskCompletion = {},
                    onReopenSelectedTask = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                    onUpdatePlanningDraft = { taskId, start, end ->
                        val draft = state.planningDrafts.getValue(taskId)
                        state = state.copy(
                            planningDrafts = state.planningDrafts + (
                                taskId to draft.copy(startMinute = start, endMinute = end)
                            )
                        )
                    },
                    onReturnPlanningDraft = { taskId ->
                        returnedTaskId = taskId
                        state = state.copy(planningDrafts = state.planningDrafts - taskId)
                    },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        val moveLater = compose.onNodeWithContentDescription("Planning draft Return me")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == "Move 30 minutes later" }
        check(moveLater.action())
        compose.runOnIdle { check(state.planningDrafts.getValue(42).startMinute == 9 * 60 + 30) }

        val draftNode = compose.onNodeWithContentDescription("Planning draft Return me")
        val draftBounds = draftNode.fetchSemanticsNode().boundsInRoot
        val railCenter = compose.onNodeWithText("Nothing waiting to be planned.")
            .fetchSemanticsNode().boundsInRoot.center
        draftNode.performTouchInput {
            swipe(center, railCenter - draftBounds.topLeft, durationMillis = 700)
        }
        compose.runOnIdle { check(returnedTaskId == 42) }
        compose.onNodeWithContentDescription("Schedule Return me").fetchSemanticsNode()
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
