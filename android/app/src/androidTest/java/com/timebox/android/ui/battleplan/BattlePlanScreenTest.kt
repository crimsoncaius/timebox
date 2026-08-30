package com.timebox.android.ui.battleplan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.timebox.android.data.BattleTask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TaskType
import com.timebox.android.ui.theme.DarkTimeboxColors
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BattlePlanScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactCardsRenderTheTimezoneAwarePlannedSummary() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                BattlePlanScreen(
                    state = BattlePlanUiState(
                        loading = false,
                        tasks = listOf(battleTask(1, listOf(LocalDate.parse("2026-08-21"), LocalDate.parse("2026-08-22"), LocalDate.parse("2026-08-24")))),
                        timezone = "Asia/Singapore",
                        serverNow = Instant.parse("2026-08-21T16:00:00Z"),
                    ),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                    onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                    onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {}, onNewProject = {},
                    onOpenRecurring = {}, onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {}, onRestoreTrashed = {},
                    onUndoTrash = {}, onDismissUndo = {}, onRequestPermanentDelete = {},
                    onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }

        val expected = checkNotNull(plannedDateSummary(
            listOf(LocalDate.parse("2026-08-21"), LocalDate.parse("2026-08-22"), LocalDate.parse("2026-08-24")),
            Instant.parse("2026-08-21T16:00:00Z"),
            "Asia/Singapore",
        )).label
        compose.onNodeWithText(expected).fetchSemanticsNode()
    }

    @Test
    fun taskDetailsShowFivePlannedDatesExpandAndOpenDay() {
        val dates = (21..27).map { LocalDate.of(2026, 8, it) }
        var openedDay: LocalDate? = null
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                TaskDetailScreen(
                    state = TaskDetailUiState(
                        taskId = 1,
                        loading = false,
                        task = battleTask(1, dates),
                        timezone = "UTC",
                        serverNow = Instant.parse("2026-08-22T12:00:00Z"),
                    ),
                    onBack = {}, onRetry = {}, onOpenTask = {}, onTitleChange = {},
                    onDescriptionChange = {}, onStatusChange = {}, onProjectChange = {},
                    onTaskTypeChange = {}, onUrgencyChange = {}, onImportanceChange = {},
                    onDeadlineModeChange = {}, onDeadlineDateChange = {}, onDeadlineTimeChange = {},
                    onReminderEnabledChange = {}, notificationsAllowed = true,
                    onReminderDateChange = {}, onReminderTimeChange = {}, onReadyChange = {},
                    onOpenDay = { date, _ -> openedDay = date }, onAddSubtask = {},
                    onToggleSubtask = {},
                    onTrashSubtask = {}, onDismissSubtaskTrash = {}, onConfirmSubtaskTrash = {},
                    onUndoSubtaskTrash = {}, onRequestTrash = {}, onDismissTrash = {},
                    onConfirmTrash = {}, onTrashed = {}, onReopen = {}, onSave = {},
                )
            }
        }

        compose.onNodeWithText("Planned Dates").fetchSemanticsNode()
        compose.onNodeWithText("Show all (7)").performScrollTo().performClick()
        compose.onNodeWithText(formatPlannedDetailDate(LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 22))).performScrollTo().fetchSemanticsNode()
        compose.onNodeWithText(formatPlannedDetailDate(LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 22))).performScrollTo().performClick()
        compose.runOnIdle { check(openedDay == LocalDate.of(2026, 8, 22)) }
        compose.onNodeWithText("Show less").fetchSemanticsNode()
    }

    @Test
    fun compactActiveViewUsesOneLogicalNavigationGroup() {
        var recurringOpened = false
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                BattlePlanScreen(
                    state = BattlePlanUiState(loading = false),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {},
                    onToggleTaskType = {}, onClearFilters = {}, onOpenTask = {},
                    onToggleReady = {}, onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {}, onNewProject = {},
                    onOpenRecurring = { recurringOpened = true },
                    onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {},
                    onRestoreTrashed = {}, onUndoTrash = {}, onDismissUndo = {},
                    onRequestPermanentDelete = {}, onDismissPermanentDelete = {},
                    onConfirmPermanentDelete = {},
                )
            }
        }

        compose.onNodeWithText("All Tasks", substring = true).performClick()
        compose.onNodeWithText("Admin").fetchSemanticsNode()
        compose.onNodeWithText("Recurring").performClick()
        compose.runOnIdle { check(recurringOpened) }
        compose.onNodeWithText("Open", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("In Progress", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("Completed", substring = true).fetchSemanticsNode()
    }

    @Test
    fun compactFiltersUseSectionsWithoutUnsetAndForwardChipTaps() {
        var urgencyTapped: String? = null
        var taskTypeTapped: String? = null
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                BattlePlanScreen(
                    state = BattlePlanUiState(
                        loading = false,
                        taskTypes = listOf(TaskType(id = 7, name = "Work / Focus", usageCount = 0)),
                    ),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = { urgencyTapped = it }, onToggleImportance = {},
                    onToggleTaskType = { taskTypeTapped = it }, onClearFilters = {}, onOpenTask = {},
                    onToggleReady = {}, onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {}, onNewProject = {},
                    onOpenRecurring = {}, onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {}, onRestoreTrashed = {},
                    onUndoTrash = {}, onDismissUndo = {}, onRequestPermanentDelete = {},
                    onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Filter tasks").performClick()
        compose.onNodeWithText("Filters").fetchSemanticsNode()
        compose.onNodeWithText("URGENCY").fetchSemanticsNode()
        compose.onNodeWithText("IMPORTANCE").fetchSemanticsNode()
        compose.onNodeWithText("TASK TYPE").fetchSemanticsNode()
        check(compose.onAllNodesWithText("Unset").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Show 0 tasks").fetchSemanticsNode()

        compose.onAllNodesWithText("High")[0].performClick()
        compose.onNodeWithText("Work / Focus").performClick()
        compose.runOnIdle {
            check(urgencyTapped == "high")
            check(taskTypeTapped == "7")
        }
    }

    @Test
    fun compactTaskActionMenuKeepsActionAvailabilityAndForwardsChoices() {
        val dropped = mutableListOf<Triple<Int, TaskStatus, Int>>()
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                BattlePlanScreen(
                    state = BattlePlanUiState(
                        loading = false,
                        tasks = listOf(battleTask(1), battleTask(2)),
                    ),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                    onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                    onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onDropTask = { task, status, index -> dropped += Triple(task.id, status, index) },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {}, onNewProject = {},
                    onOpenRecurring = {}, onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {}, onRestoreTrashed = {},
                    onUndoTrash = {}, onDismissUndo = {}, onRequestPermanentDelete = {},
                    onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Actions for Task 1").performClick()
        compose.onNodeWithTag("battle-plan-task-actions-menu").fetchSemanticsNode()
        compose.onNodeWithText("REORDER").fetchSemanticsNode()
        compose.onNodeWithText("MOVE TO").fetchSemanticsNode()
        check(compose.onAllNodesWithText("Move earlier").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Move later").fetchSemanticsNode()
        check(compose.onAllNodesWithText("Move to Open").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Move to Completed").fetchSemanticsNode()
        compose.onNodeWithText("Move to In Progress").performClick()
        compose.runOnIdle { check(dropped.last() == Triple(1, TaskStatus.InProgress, 0)) }

        compose.onNodeWithContentDescription("Actions for Task 1").performClick()
        compose.onNodeWithText("Move to Completed").performClick()
        compose.runOnIdle { check(dropped.last() == Triple(1, TaskStatus.Completed, 0)) }

        compose.onNodeWithContentDescription("Actions for Task 1").performClick()
        compose.onNodeWithText("Move later").performClick()
        compose.runOnIdle { check(dropped.last() == Triple(1, TaskStatus.Open, 1)) }

        compose.onNodeWithContentDescription("Actions for Task 2").performClick()
        compose.onNodeWithText("Move earlier").performClick()
        compose.runOnIdle {
            check(dropped == listOf(
                Triple(1, TaskStatus.InProgress, 0),
                Triple(1, TaskStatus.Completed, 0),
                Triple(1, TaskStatus.Open, 1),
                Triple(2, TaskStatus.Open, 0),
            ))
        }
    }

    @Test
    fun longPressCollapsesSourceShowsPreviewAndDropsAtMeasuredInsertion() {
        val haptics = RecordingHaptics()
        var dropped: Triple<Int, TaskStatus, Int>? = null
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                TimeboxTheme(darkTheme = false) {
                    BattlePlanScreen(
                        state = BattlePlanUiState(
                            loading = false,
                            tasks = listOf(battleTask(1), battleTask(2), battleTask(3)),
                        ),
                        onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                        onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                        onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                        onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                        onDropTask = { task, status, index -> dropped = Triple(task.id, status, index) },
                        onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                        onCreateTask = { _, _, _ -> }, onShowComposer = {}, onNewProject = {},
                        onOpenRecurring = {}, onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                        onConfirmDeleteProject = {}, onRestoreArchived = {}, onRestoreTrashed = {},
                        onUndoTrash = {}, onDismissUndo = {}, onRequestPermanentDelete = {},
                        onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                    )
                }
            }
        }

        val root = compose.onRoot()
        val firstCenter = compose.onNodeWithTag("battle-plan-task-1").fetchSemanticsNode().boundsInRoot.center
        root.performTouchInput {
            down(firstCenter)
            advanceEventTime(1_000)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 1f))
        }
        compose.waitForIdle()

        check(compose.onAllNodesWithTag("battle-plan-task-1").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("battle-plan-drag-preview").fetchSemanticsNode()
        compose.onNodeWithTag("battle-plan-drop-indicator-open-0").fetchSemanticsNode()

        val thirdCenter = compose.onNodeWithTag("battle-plan-task-3").fetchSemanticsNode().boundsInRoot.center
        root.performTouchInput {
            moveTo(thirdCenter)
            advanceEventTime(32)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("battle-plan-drop-indicator-open-2").fetchSemanticsNode()

        root.performTouchInput { up() }
        compose.runOnIdle {
            check(dropped == Triple(1, TaskStatus.Open, 2))
            check(haptics.events == listOf(HapticFeedbackType.LongPress, HapticFeedbackType.TextHandleMove))
        }
        check(compose.onAllNodesWithTag("battle-plan-drag-preview").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun pickupLiftsPreviewAndClosesSourceGapOverTime() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                BattlePlanScreen(
                    state = BattlePlanUiState(
                        loading = false,
                        tasks = listOf(battleTask(1), battleTask(2), battleTask(3)),
                    ),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                    onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                    onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onDropTask = { _, _, _ -> }, onCreateSubtask = { _, _ -> },
                    onToggleSubtask = {}, onCreateTask = { _, _, _ -> }, onShowComposer = {},
                    onNewProject = {}, onOpenRecurring = {}, onPrepareDeleteProject = {},
                    onDismissDeleteProject = {}, onConfirmDeleteProject = {}, onRestoreArchived = {},
                    onRestoreTrashed = {}, onUndoTrash = {}, onDismissUndo = {},
                    onRequestPermanentDelete = {}, onDismissPermanentDelete = {},
                    onConfirmPermanentDelete = {},
                )
            }
        }

        val root = compose.onRoot()
        val firstCenter = compose.onNodeWithTag("battle-plan-task-1")
            .fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        root.performTouchInput {
            down(firstCenter)
            advanceEventTime(500)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 1f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()

        val pickupStartProgress = compose.onNodeWithTag("battle-plan-drag-preview")
            .fetchSemanticsNode().config[MobilePickupProgressKey]
        val pickupStartSecondTop = compose.onNodeWithTag("battle-plan-task-2")
            .fetchSemanticsNode().boundsInRoot.top

        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        val pickupMiddleProgress = compose.onNodeWithTag("battle-plan-drag-preview")
            .fetchSemanticsNode().config[MobilePickupProgressKey]
        val pickupMiddleSecondTop = compose.onNodeWithTag("battle-plan-task-2")
            .fetchSemanticsNode().boundsInRoot.top

        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val pickupEndProgress = compose.onNodeWithTag("battle-plan-drag-preview")
            .fetchSemanticsNode().config[MobilePickupProgressKey]
        val pickupEndSecondTop = compose.onNodeWithTag("battle-plan-task-2")
            .fetchSemanticsNode().boundsInRoot.top

        check(pickupMiddleProgress > pickupStartProgress) {
            "pickup did not begin: start=$pickupStartProgress, middle=$pickupMiddleProgress, end=$pickupEndProgress"
        }
        check(pickupEndProgress > pickupMiddleProgress) {
            "pickup did not finish: start=$pickupStartProgress, middle=$pickupMiddleProgress, end=$pickupEndProgress"
        }
        check(pickupMiddleSecondTop < pickupStartSecondTop) {
            "source gap did not begin closing: start=$pickupStartSecondTop, middle=$pickupMiddleSecondTop, end=$pickupEndSecondTop"
        }
        check(pickupEndSecondTop < pickupMiddleSecondTop) {
            "source gap did not finish closing: start=$pickupStartSecondTop, middle=$pickupMiddleSecondTop, end=$pickupEndSecondTop"
        }
    }

    @Test
    fun droppedPreviewSettlesIntoDestinationBeforeReorderIsCommitted() {
        var dropped: Triple<Int, TaskStatus, Int>? = null
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                BattlePlanScreen(
                    state = BattlePlanUiState(
                        loading = false,
                        tasks = listOf(battleTask(1), battleTask(2), battleTask(3)),
                    ),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                    onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                    onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onDropTask = { task, status, index -> dropped = Triple(task.id, status, index) },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {}, onNewProject = {},
                    onOpenRecurring = {}, onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {}, onRestoreTrashed = {},
                    onUndoTrash = {}, onDismissUndo = {}, onRequestPermanentDelete = {},
                    onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }

        val root = compose.onRoot()
        val firstCenter = compose.onNodeWithTag("battle-plan-task-1").fetchSemanticsNode().boundsInRoot.center
        root.performTouchInput {
            down(firstCenter)
            advanceEventTime(1_000)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 1f))
        }
        compose.waitForIdle()

        val thirdCenter = compose.onNodeWithTag("battle-plan-task-3").fetchSemanticsNode().boundsInRoot.center
        root.performTouchInput {
            moveTo(thirdCenter)
            advanceEventTime(32)
        }
        compose.waitForIdle()
        val releaseTop = compose.onNodeWithTag("battle-plan-drag-preview")
            .fetchSemanticsNode().boundsInRoot.top

        compose.mainClock.autoAdvance = false
        root.performTouchInput { up() }
        compose.waitForIdle()

        check(dropped == null)
        compose.onNodeWithTag("battle-plan-drag-preview").fetchSemanticsNode()

        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val settlingTop = compose.onNodeWithTag("battle-plan-drag-preview")
            .fetchSemanticsNode().boundsInRoot.top
        check(settlingTop > releaseTop)

        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        check(dropped == Triple(1, TaskStatus.Open, 2))
        check(compose.onAllNodesWithTag("battle-plan-drag-preview").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun darkDragPreviewKeepsRaisedSurfaceWhileHeldMovingAndSettling() {
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                BattlePlanScreen(
                    state = BattlePlanUiState(
                        loading = false,
                        tasks = listOf(
                            battleTask(1).copy(readyToPlan = true, isBlocked = true),
                            battleTask(2),
                            battleTask(3),
                        ),
                    ),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                    onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                    onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onDropTask = { _, _, _ -> }, onCreateSubtask = { _, _ -> },
                    onToggleSubtask = {}, onCreateTask = { _, _, _ -> }, onShowComposer = {},
                    onNewProject = {}, onOpenRecurring = {}, onPrepareDeleteProject = {},
                    onDismissDeleteProject = {}, onConfirmDeleteProject = {}, onRestoreArchived = {},
                    onRestoreTrashed = {}, onUndoTrash = {}, onDismissUndo = {},
                    onRequestPermanentDelete = {}, onDismissPermanentDelete = {},
                    onConfirmPermanentDelete = {},
                )
            }
        }

        fun previewSurface() = compose.onNodeWithTag("battle-plan-drag-preview")
            .fetchSemanticsNode().config[MobileDragPreviewSurfaceKey]

        val root = compose.onRoot()
        val firstCenter = compose.onNodeWithTag("battle-plan-task-1")
            .fetchSemanticsNode().boundsInRoot.center
        root.performTouchInput {
            down(firstCenter)
            advanceEventTime(1_000)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 1f))
        }
        compose.waitForIdle()
        check(previewSurface() == DarkTimeboxColors.surf)

        val thirdCenter = compose.onNodeWithTag("battle-plan-task-3")
            .fetchSemanticsNode().boundsInRoot.center
        root.performTouchInput {
            moveTo(thirdCenter)
            advanceEventTime(32)
        }
        compose.waitForIdle()
        check(previewSurface() == DarkTimeboxColors.surf)

        compose.mainClock.autoAdvance = false
        root.performTouchInput { up() }
        compose.waitForIdle()
        check(previewSurface() == DarkTimeboxColors.surf)

        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        check(previewSurface() == DarkTimeboxColors.surf)

        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        check(compose.onAllNodesWithTag("battle-plan-drag-preview").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun cancelledDragRestoresTheSourceWithoutDropFeedback() {
        val haptics = RecordingHaptics()
        var dropped = false
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                TimeboxTheme(darkTheme = false) {
                    BattlePlanScreen(
                        state = BattlePlanUiState(loading = false, tasks = listOf(battleTask(1), battleTask(2))),
                        onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                        onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                        onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                        onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                        onDropTask = { _, _, _ -> dropped = true }, onCreateSubtask = { _, _ -> },
                        onToggleSubtask = {}, onCreateTask = { _, _, _ -> }, onShowComposer = {},
                        onNewProject = {}, onOpenRecurring = {}, onPrepareDeleteProject = {},
                        onDismissDeleteProject = {}, onConfirmDeleteProject = {}, onRestoreArchived = {},
                        onRestoreTrashed = {}, onUndoTrash = {}, onDismissUndo = {},
                        onRequestPermanentDelete = {}, onDismissPermanentDelete = {},
                        onConfirmPermanentDelete = {},
                    )
                }
            }
        }

        val root = compose.onRoot()
        val firstCenter = compose.onNodeWithTag("battle-plan-task-1").fetchSemanticsNode().boundsInRoot.center
        root.performTouchInput {
            down(firstCenter)
            advanceEventTime(1_000)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 1f))
        }
        compose.waitForIdle()
        root.performTouchInput { cancel() }

        compose.onNodeWithTag("battle-plan-task-1").fetchSemanticsNode()
        check(compose.onAllNodesWithTag("battle-plan-drag-preview").fetchSemanticsNodes().isEmpty())
        compose.runOnIdle {
            check(!dropped)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
        }
    }

    @Test
    fun unchangedDropConfirmsPlacementWithoutPersistingAReorder() {
        val haptics = RecordingHaptics()
        var dropped = false
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                TimeboxTheme(darkTheme = false) {
                    BattlePlanScreen(
                        state = BattlePlanUiState(loading = false, tasks = listOf(battleTask(1), battleTask(2))),
                        onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                        onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                        onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                        onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                        onDropTask = { _, _, _ -> dropped = true }, onCreateSubtask = { _, _ -> },
                        onToggleSubtask = {}, onCreateTask = { _, _, _ -> }, onShowComposer = {},
                        onNewProject = {}, onOpenRecurring = {}, onPrepareDeleteProject = {},
                        onDismissDeleteProject = {}, onConfirmDeleteProject = {}, onRestoreArchived = {},
                        onRestoreTrashed = {}, onUndoTrash = {}, onDismissUndo = {},
                        onRequestPermanentDelete = {}, onDismissPermanentDelete = {},
                        onConfirmPermanentDelete = {},
                    )
                }
            }
        }

        val root = compose.onRoot()
        val firstCenter = compose.onNodeWithTag("battle-plan-task-1").fetchSemanticsNode().boundsInRoot.center
        root.performTouchInput {
            down(firstCenter)
            advanceEventTime(1_000)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 1f))
        }
        compose.waitForIdle()
        root.performTouchInput { up() }

        compose.onNodeWithTag("battle-plan-task-1").fetchSemanticsNode()
        compose.runOnIdle {
            check(!dropped)
            check(haptics.events == listOf(HapticFeedbackType.LongPress, HapticFeedbackType.TextHandleMove))
        }
    }
}

private class RecordingHaptics : HapticFeedback {
    val events = mutableListOf<HapticFeedbackType>()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        events += hapticFeedbackType
    }
}

private fun battleTask(id: Int, plannedDates: List<LocalDate> = emptyList()) = BattleTask(
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
    title = "Task $id",
    description = "",
    readyToPlan = false,
    status = TaskStatus.Open,
    urgency = null,
    importance = null,
    deadlineDate = null,
    deadlineAt = null,
    reminderAt = null,
    reminderDeliveredAt = null,
    position = id - 1,
    archivedAt = null,
    deletedAt = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    overdue = false,
    subtasks = emptyList(),
    plannedDates = plannedDates,
)
