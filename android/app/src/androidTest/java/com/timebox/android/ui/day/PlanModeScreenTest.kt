package com.timebox.android.ui.day

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.planning.PlanningDraftPlacement
import com.timebox.android.ui.planning.PlanningSessionState
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
            planning = PlanningSessionState(
                active = true,
                readyTasks = listOf(task(42, "Write brief")),
            ),
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {},
                    onConfirmSelectedTaskCompletion = {},
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
        compose.onNodeWithContentDescription("Schedule Write brief").fetchSemanticsNode()
    }

    @Test
    fun androidBackCancelsInsteadOfCommittingPlanningSession() {
        val date = LocalDate.of(2026, 8, 20)
        var cancelled = false
        var committed = false
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
            planning = PlanningSessionState(active = true),
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {},
                    onConfirmSelectedTaskCompletion = {},
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
            planning = PlanningSessionState(
                active = true,
                readyTasks = listOf(task(42, "Write brief")),
            ),
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {},
                    onConfirmSelectedTaskCompletion = {},
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
        val haptics = RecordingHaptics()
        val readyTask = task(42, "Write brief")
        var state by mutableStateOf(
            DayUiState(
                date = date,
                pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
                planning = PlanningSessionState(
                    active = true,
                    readyTasks = listOf(readyTask),
                ),
            )
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    DayScreen(
                        state = state,
                        onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                        onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                        onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                        onNoteChange = {}, onDeleteSelected = {},
                        onConfirmSelectedTaskCompletion = {},
                        onReopenSelectedTask = {},
                        onOpenLinkedTask = {}, onSetPlanningMode = {},
                        onPlanTask = { taskId, minute ->
                            placement = taskId to minute
                            state = state.copy(
                                planning = state.planning.copy(
                                    drafts = state.planningDrafts + (
                                        taskId to PlanningDraftPlacement(date, readyTask, minute, minute + 30)
                                    ),
                                ),
                            )
                        },
                        onArmAccessibleTask = {}, onRetryReadyTasks = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Write brief").performTouchInput {
            swipe(center, center + Offset(-500f, 0f), durationMillis = 200)
        }
        compose.runOnIdle {
            check(placement == null)
            check(haptics.events.isEmpty())
        }

        compose.onNodeWithText("Write brief").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(-500f, 0f))
            up()
        }
        compose.runOnIdle {
            check(placement?.first == 42)
            check(placement?.second != null)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
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
            planning = PlanningSessionState(
                active = true,
                readyTasks = listOf(task(42, "Write brief")),
            ),
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {},
                    onConfirmSelectedTaskCompletion = {},
                    onReopenSelectedTask = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {},
                    onPlanTask = { taskId, minute -> placement = taskId to minute },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithText("Write brief").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(-500f, 0f))
            up()
        }
        compose.runOnIdle { check(placement == null) }
    }

    @Test
    fun persistedPlannedCardIsLockedInPlanMode() {
        val date = LocalDate.of(2026, 8, 20)
        var moved = false
        val haptics = RecordingHaptics()
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
            planning = PlanningSessionState(active = true),
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    DayScreen(
                        state = state,
                        onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                        onSelectBlock = {}, onCommitMove = { _, _, _ -> moved = true }, onDismissSheet = {},
                        onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                        onNoteChange = {}, onDeleteSelected = {},
                        onConfirmSelectedTaskCompletion = {},
                        onReopenSelectedTask = {},
                        onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                        onArmAccessibleTask = {}, onRetryReadyTasks = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("day-block-1").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, 180f))
            up()
        }
        compose.runOnIdle {
            check(!moved)
            check(haptics.events.isEmpty())
        }
    }

    @Test
    fun planningDraftResizeGroovesRequireLongPress() {
        val date = LocalDate.of(2026, 8, 20)
        val draftTask = task(42, "Resize me")
        var updated: Triple<Int, Int, Int>? = null
        val haptics = RecordingHaptics()
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
            planning = PlanningSessionState(
                active = true,
                readyTasks = listOf(draftTask, task(43, "Other task")),
                drafts = mapOf(
                    draftTask.id to PlanningDraftPlacement(date, draftTask, 9 * 60, 10 * 60)
                ),
            ),
        )
        setPlanningContent(
            state = state,
            haptics = haptics,
            onUpdatePlanningDraft = { id, start, end -> updated = Triple(id, start, end) },
        )

        val draft = compose.onNodeWithContentDescription("Planning draft Resize me")
        draft.performTouchInput {
            val topGroove = Offset(center.x, 2f)
            swipe(topGroove, topGroove + Offset(0f, 150f), durationMillis = 200)
        }
        compose.runOnIdle {
            check(updated == null)
            check(haptics.events.isEmpty())
        }

        draft.performTouchInput {
            val topGroove = Offset(center.x, 2f)
            down(topGroove)
            advanceEventTime(1_000)
            moveTo(topGroove + Offset(0f, height / 12f))
            up()
        }
        compose.runOnIdle {
            check(updated?.first == 42)
            check(updated?.second == 9 * 60 + 5)
            check(updated?.third == 10 * 60)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
            updated = null
            haptics.events.clear()
        }

        draft.performTouchInput {
            val bottomGroove = Offset(center.x, height - 2f)
            down(bottomGroove)
            advanceEventTime(1_000)
            moveTo(bottomGroove - Offset(0f, height / 12f))
            up()
        }
        compose.runOnIdle {
            check(updated?.first == 42)
            check(updated?.second == 9 * 60)
            check(updated?.third == 10 * 60 - 5)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
        }
    }

    @Test
    fun savingDisabledPlanningDraftDoesNotArm() {
        val date = LocalDate.of(2026, 8, 20)
        val draftTask = task(42, "Saving draft")
        var changed = false
        val haptics = RecordingHaptics()
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
            planning = PlanningSessionState(
                active = true,
                readyTasks = listOf(draftTask, task(43, "Other task")),
                drafts = mapOf(
                    draftTask.id to PlanningDraftPlacement(date, draftTask, 9 * 60, 10 * 60)
                ),
                saving = true,
            ),
        )
        setPlanningContent(
            state = state,
            haptics = haptics,
            onUpdatePlanningDraft = { _, _, _ -> changed = true },
            onReturnPlanningDraft = { _ -> changed = true },
        )

        compose.onNodeWithContentDescription("Planning draft Saving draft").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, 150f))
            up()
        }
        compose.runOnIdle {
            check(!changed)
            check(haptics.events.isEmpty())
        }
    }

    @Test
    fun taskRailEarlyVerticalMovementScrollsWithoutArming() {
        val date = LocalDate.of(2026, 8, 20)
        var placed = false
        val haptics = RecordingHaptics()
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
            planning = PlanningSessionState(
                active = true,
                readyTasks = (1..15).map { task(it, "Task $it") },
            ),
        )
        setPlanningContent(
            state = state,
            haptics = haptics,
            onPlanTask = { _, _ -> placed = true },
        )

        val card = compose.onNodeWithContentDescription("Schedule Task 5")
        val before = card.fetchSemanticsNode().boundsInRoot.top
        card.performTouchInput {
            swipe(center, center + Offset(0f, -180f), durationMillis = 200)
        }
        val after = compose.onNodeWithContentDescription("Schedule Task 5")
            .fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            check(after < before)
            check(!placed)
            check(haptics.events.isEmpty())
        }
    }

    @Test
    fun bluePlanningDraftCanReturnToTaskRail() {
        val date = LocalDate.of(2026, 8, 20)
        val draftTask = task(42, "Return me")
        val haptics = RecordingHaptics()
        var returnedTaskId: Int? = null
        var state by mutableStateOf(
            DayUiState(
                date = date,
                pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
                planning = PlanningSessionState(
                    active = true,
                    readyTasks = listOf(draftTask, task(43, "Other task")),
                    drafts = mapOf(
                        draftTask.id to PlanningDraftPlacement(date, draftTask, 9 * 60, 10 * 60)
                    ),
                ),
            )
        )

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    DayScreen(
                        state = state,
                        onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                        onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                        onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                        onNoteChange = {}, onDeleteSelected = {},
                        onConfirmSelectedTaskCompletion = {},
                        onReopenSelectedTask = {},
                        onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                        onUpdatePlanningDraft = { taskId, start, end ->
                            val draft = state.planningDrafts.getValue(taskId)
                            state = state.copy(
                                planning = state.planning.copy(
                                    drafts = state.planningDrafts + (
                                        taskId to draft.copy(startMinute = start, endMinute = end)
                                    ),
                                ),
                            )
                        },
                        onReturnPlanningDraft = { taskId ->
                            returnedTaskId = taskId
                            state = state.copy(
                                planning = state.planning.copy(
                                    drafts = state.planningDrafts - taskId,
                                ),
                            )
                        },
                        onArmAccessibleTask = {}, onRetryReadyTasks = {},
                    )
                }
            }
        }

        val moveLater = compose.onNodeWithContentDescription("Planning draft Return me")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == "Move 5 minutes later" }
        check(moveLater.action())
        compose.runOnIdle { check(state.planningDrafts.getValue(42).startMinute == 9 * 60 + 5) }

        val draftNode = compose.onNodeWithContentDescription("Planning draft Return me")
        val draftBounds = draftNode.fetchSemanticsNode().boundsInRoot
        val railCenter = compose.onNodeWithTag("planning-task-rail")
            .fetchSemanticsNode().boundsInRoot.center
        draftNode.performTouchInput {
            swipe(center, railCenter - draftBounds.topLeft, durationMillis = 200)
        }
        compose.runOnIdle {
            check(returnedTaskId == null)
            check(haptics.events.isEmpty())
        }

        draftNode.performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(railCenter - draftBounds.topLeft)
            up()
        }
        compose.runOnIdle {
            check(returnedTaskId == 42)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
        }
        compose.onNodeWithContentDescription("Schedule Return me").fetchSemanticsNode()
    }

    private fun setPlanningContent(
        state: DayUiState,
        haptics: RecordingHaptics,
        onPlanTask: (Int, Int) -> Unit = { _, _ -> },
        onUpdatePlanningDraft: (Int, Int, Int) -> Unit = { _, _, _ -> },
        onReturnPlanningDraft: (Int) -> Unit = {},
    ) {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    DayScreen(
                        state = state,
                        onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                        onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                        onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                        onNoteChange = {}, onDeleteSelected = {},
                        onConfirmSelectedTaskCompletion = {}, onReopenSelectedTask = {},
                        onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = onPlanTask,
                        onUpdatePlanningDraft = onUpdatePlanningDraft,
                        onReturnPlanningDraft = onReturnPlanningDraft,
                        onArmAccessibleTask = {}, onRetryReadyTasks = {},
                    )
                }
            }
        }
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
