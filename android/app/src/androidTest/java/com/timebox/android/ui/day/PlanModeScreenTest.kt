package com.timebox.android.ui.day

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import com.timebox.android.ui.theme.TimeboxDimens
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
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
import com.timebox.android.ui.planning.PlanningSession
import com.timebox.android.ui.planning.PlanningSessionTransport
import com.timebox.android.data.PlanningCommitPlacement
import kotlinx.coroutines.runBlocking
import java.io.File
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.Instant

class PlanModeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun queueDropAtTimelineTopUsesVisibleTime() = queueDropUsesVisibleTime(0)
    @Test fun queueDropAroundNoonUsesVisibleTime() = queueDropUsesVisibleTime(4)
    @Test fun queueDropLaterInDayUsesVisibleTime() = queueDropUsesVisibleTime(7)

    @Test
    fun pendingReadyToPlanAdditionIsShownAsSavingAndUnavailable() {
        val date = LocalDate.of(2026, 8, 20)
        val day = emptyDay(date)
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = day, loading = false, materialized = true)),
            planning = PlanningSessionState(
                active = true,
                readyTasks = listOf(
                    task(42, "Pending Task").copy(readinessPending = true),
                ),
            ),
        )
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                PlanningWorkspace(
                    state = state, day = day,
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> },
                    onPlanTask = { _, _ -> error("Pending Task cannot be planned") },
                    onUpdatePlanningDraft = { _, _, _ -> }, onReturnPlanningDraft = {},
                    onArmAccessibleTask = { error("Pending Task cannot be selected") },
                    onRetryReadyTasks = {}, modifier = Modifier.height(400.dp),
                )
            }
        }

        compose.onNodeWithContentDescription("Pending Task is saving and unavailable")
            .assertIsNotEnabled()
        compose.onNodeWithText("Saving").fetchSemanticsNode()
    }

    private fun queueDropUsesVisibleTime(scrollHours: Int) {
        val date = LocalDate.of(2026, 8, 20)
        var placement: Pair<Int, Int>? = null
        val day = emptyDay(date).copy(endHour = 24)
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = day, loading = false, materialized = true)),
            planning = PlanningSessionState(active = true, readyTasks = listOf(task(42, "Drop at visible time"))),
        )
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                PlanningWorkspace(
                    state = state, day = day,
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> },
                    onPlanTask = { id, minute -> placement = id to minute },
                    onUpdatePlanningDraft = { _, _, _ -> }, onReturnPlanningDraft = {},
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                    modifier = Modifier.height(400.dp),
                )
            }
        }
        val slotPx = with(compose.density) { TimeboxDimens.slotHeight.toPx() }
        val timeline = compose.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag("day-lane-planned")))
        val initialTop = compose.onNodeWithTag("day-lane-planned").fetchSemanticsNode().boundsInRoot.top
        timeline.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, scrollHours * 2 * slotPx) }
        compose.waitForIdle()
        val scrollPx = timeline.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        check(kotlin.math.abs(scrollPx - scrollHours * 2 * slotPx) < 1f) { "Scroll was $scrollPx, wanted ${scrollHours * 2 * slotPx}" }
        val lane = compose.onNodeWithTag("day-lane-planned").fetchSemanticsNode().boundsInRoot
        // The carried 30-minute block is grabbed at its center; its top should land one hour below the viewport top.
        val target = Offset(lane.center.x, initialTop + 2.5f * slotPx)
        val source = compose.onNodeWithContentDescription("Schedule Drop at visible time").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(source)
            advanceEventTime(1_000)
            moveTo(source)
        }
        val heldScrollPx = timeline.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        check(heldScrollPx == scrollPx) { "Picking up a queue task moved the timeline from $scrollPx to $heldScrollPx" }
        compose.onRoot().performTouchInput { moveTo(target) }
        val preview = compose.onNodeWithTag("planning-drop-outline").fetchSemanticsNode().boundsInRoot
        check(kotlin.math.abs(preview.top - (target.y - slotPx / 2f)) < 2f)
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle {
            val expected = 42 to ((8 + scrollHours + 1) * 60)
            check(placement == expected) { "Expected $expected after scrolling $scrollHours hours, got $placement" }
        }
    }

    @Test
    fun carriedDraftMatchesDestinationSizeAndCancellationClearsBoth() {
        val date = LocalDate.of(2026, 8, 20)
        val draftTask = task(42, "Carry this hour")
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
            planning = PlanningSessionState(
                active = true,
                readyTasks = listOf(draftTask),
                drafts = mapOf(42 to PlanningDraftPlacement(date, draftTask, 540, 600)),
            ),
        )
        var updated = false
        setPlanningContent(state, RecordingHaptics(), onUpdatePlanningDraft = { _, _, _ -> updated = true })
        val source = compose.onNodeWithContentDescription("Planning draft Carry this hour").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(source.center)
            advanceEventTime(1_000)
            moveTo(source.center + Offset(10f, 20f))
        }
        val carried = compose.onNodeWithTag("planning-drag-block").fetchSemanticsNode().boundsInRoot
        val outline = compose.onNodeWithTag("planning-drop-outline").fetchSemanticsNode()
        check(kotlin.math.abs(carried.width - outline.boundsInRoot.width) <= 1f)
        check(kotlin.math.abs(carried.height - source.height) <= 1f)
        check(kotlin.math.abs(carried.height - outline.boundsInRoot.height) <= 1f)
        check(outline.children.isEmpty())
        compose.onRoot().performTouchInput { cancel() }
        compose.onNodeWithTag("planning-drag-block").assertDoesNotExist()
        compose.onNodeWithTag("planning-drop-outline").assertDoesNotExist()
        compose.runOnIdle { check(!updated) }
    }

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
    fun taskCardHoldThenVerticalStartRequestsPlacementWithHaptic() {
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
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, 40f))
            moveTo(center + Offset(-500f, 40f))
        }
        compose.onNodeWithTag("planning-drop-outline").fetchSemanticsNode()
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle {
            check(placement?.first == 42)
            check(placement?.second != null)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
        }
        compose.onNodeWithContentDescription("Planning draft Write brief").fetchSemanticsNode()
    }

    @Test
    fun queueHoldPicksUpBeforeMovementAndSupportsEveryDirection() {
        val date = LocalDate.of(2026, 8, 20)
        var placements = 0
        val haptics = RecordingHaptics()
        setPlanningContent(
            DayUiState(
                date = date,
                pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
                planning = PlanningSessionState(active = true, readyTasks = listOf(task(42, "Pick me up"))),
            ),
            haptics,
            onPlanTask = { _, _ -> placements++ },
        )
        val source = compose.onNodeWithContentDescription("Schedule Pick me up")
            .fetchSemanticsNode().boundsInRoot.center
        val directions = listOf(
            Offset(-40f, 0f), Offset(40f, 0f), Offset(0f, -40f), Offset(0f, 40f),
            Offset(-40f, -40f), Offset(40f, 40f),
        )
        directions.forEachIndexed { index, direction ->
            compose.onRoot().performTouchInput {
                down(source)
                advanceEventTime(1_000)
                moveTo(source)
            }
            val before = compose.onNodeWithTag("planning-drag-block").fetchSemanticsNode().boundsInRoot.center
            compose.runOnIdle { check(haptics.events.size == index + 1) }
            compose.onRoot().performTouchInput { moveTo(source + direction) }
            val after = compose.onNodeWithTag("planning-drag-block").fetchSemanticsNode().boundsInRoot.center
            check((after - before - direction).getDistance() < 2f)
            compose.onRoot().performTouchInput { cancel() }
            compose.onNodeWithTag("planning-drag-block").assertDoesNotExist()
            compose.onNodeWithTag("planning-drop-outline").assertDoesNotExist()
        }
        compose.onRoot().performTouchInput {
            down(source)
            advanceEventTime(1_000)
            moveTo(source)
            up()
        }
        compose.onNodeWithTag("planning-drag-block").assertDoesNotExist()
        compose.onRoot().performTouchInput {
            down(source)
            up()
        }
        compose.runOnIdle {
            check(placements == 0)
            check(haptics.events.size == directions.size + 1)
        }
        compose.onNodeWithContentDescription("Schedule Pick me up").fetchSemanticsNode()
    }

    @Test
    fun draggingTaskCardOntoOccupiedPlannedTimeSnapsToPreview() {
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

        val source = compose.onNodeWithContentDescription("Schedule Write brief").fetchSemanticsNode().boundsInRoot.center
        val lane = compose.onNodeWithTag("day-lane-planned").fetchSemanticsNode().boundsInRoot
        val slotPx = with(compose.density) { TimeboxDimens.slotHeight.toPx() }
        compose.onRoot().performTouchInput {
            down(source)
            advanceEventTime(1_000)
            moveTo(Offset(lane.center.x, lane.top + 2.5f * slotPx))
        }
        compose.onNodeWithText("12:00–12:30").fetchSemanticsNode()
        saveDragScreenshot("nearest-placement")
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { check(placement == 42 to 720) }
    }

    @Test
    fun draftMoveSnapsAroundAnotherDraftAndPreservesDuration() {
        val date = LocalDate.of(2026, 8, 20)
        val moving = task(42, "Move this hour")
        val obstacle = task(43, "Keep this half hour")
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
            planning = PlanningSessionState(
                active = true, readyTasks = listOf(moving, obstacle),
                drafts = mapOf(
                    42 to PlanningDraftPlacement(date, moving, 540, 600),
                    43 to PlanningDraftPlacement(date, obstacle, 600, 630),
                ),
            ),
        )
        var placement: Triple<Int, Int, Int>? = null
        setPlanningContent(state, RecordingHaptics(), onUpdatePlanningDraft = { id, start, end ->
            placement = Triple(id, start, end)
        })
        val source = compose.onNodeWithContentDescription("Planning draft Move this hour").fetchSemanticsNode().boundsInRoot
        val slotPx = with(compose.density) { TimeboxDimens.slotHeight.toPx() }
        compose.onRoot().performTouchInput {
            down(source.center)
            advanceEventTime(1_000)
            moveTo(source.center + Offset(0f, 2 * slotPx))
        }
        compose.onNodeWithText("10:30–11:30").fetchSemanticsNode()
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { check(placement == Triple(42, 630, 690)) { "$placement" } }
    }

    @Test
    fun fullDayShowsNoSpaceAndLeavesQueueTaskUnplaced() {
        var placement: Pair<Int, Int>? = null
        val date = LocalDate.of(2026, 8, 20)
        val day = emptyDay(date).copy(blocks = listOf(plannedBlock(480, 1200)))
        val state = DayUiState(
            date = date, pages = mapOf(date to DayPageState(day = day, loading = false, materialized = true)),
            planning = PlanningSessionState(active = true, readyTasks = listOf(task(42, "No room"))),
        )
        setPlanningContent(state, RecordingHaptics(), onPlanTask = { id, start -> placement = id to start })
        dragQueueTaskToMorning("No room")
        compose.onNodeWithText("No available space in this time range").fetchSemanticsNode()
        compose.onNodeWithTag("planning-drop-outline").assertDoesNotExist()
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { check(placement == null) }
        compose.onNodeWithContentDescription("Schedule No room").fetchSemanticsNode()
    }

    @Test
    fun offscreenSnapShowsTimeWithoutJumpingAndEdgeHoldStillScrolls() {
        val date = LocalDate.of(2026, 8, 20)
        val day = emptyDay(date).copy(blocks = listOf(plannedBlock(480, 960)))
        val state = DayUiState(
            date = date, pages = mapOf(date to DayPageState(day = day, loading = false, materialized = true)),
            planning = PlanningSessionState(active = true, readyTasks = listOf(task(42, "Later today"))),
        )
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                PlanningWorkspace(
                    state = state, day = day,
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onPlanTask = { _, _ -> },
                    onUpdatePlanningDraft = { _, _, _ -> }, onReturnPlanningDraft = {},
                    onArmAccessibleTask = {}, onRetryReadyTasks = {}, modifier = Modifier.height(400.dp),
                )
            }
        }
        val timeline = compose.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag("day-lane-planned")))
        val initialScroll = timeline.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        dragQueueTaskToMorning("Later today")
        compose.onNodeWithText("16:00–16:30").fetchSemanticsNode()
        check(timeline.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() == initialScroll)
        val viewport = timeline.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { moveTo(Offset(viewport.center.x, viewport.bottom - 10f)) }
        compose.mainClock.advanceTimeBy(500)
        check(timeline.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > initialScroll)
        compose.onRoot().performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun availabilityChangeAfterPreviewRejectsReleaseWithoutChoosingAnotherTime() {
        val date = LocalDate.of(2026, 8, 20)
        val ready = task(42, "Changed schedule")
        val day = emptyDay(date)
        var latestDay = day
        val session = PlanningSession(object : PlanningSessionTransport {
            override suspend fun loadScopedTasks(planningDate: LocalDate?) = Result.success(listOf(ready))
            override suspend fun commit(placements: List<PlanningCommitPlacement>): Result<List<Day>> =
                error("Dropping a draft must not save the plan")
        })
        runBlocking { session.refreshQueue() }
        session.begin()
        val state = DayUiState(
            date = date, pages = mapOf(date to DayPageState(day = day, loading = false, materialized = true)),
            planning = session.state.value,
        )
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                PlanningWorkspace(
                    state = state, day = day,
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onPlanTask = { _, _ -> error("Use drag validation") },
                    onDropPlanningTask = { session.drop(it.taskId, latestDay, it.startMinute, it.endMinute) },
                    onUpdatePlanningDraft = { _, _, _ -> }, onReturnPlanningDraft = {},
                    onArmAccessibleTask = {}, onRetryReadyTasks = {}, modifier = Modifier.height(400.dp),
                )
            }
        }
        dragQueueTaskToMorning("Changed schedule")
        compose.onNodeWithText("09:00–09:30").fetchSemanticsNode()
        // Update the authoritative schedule without recomposing the displayed preview.
        compose.runOnIdle { latestDay = day.copy(blocks = listOf(plannedBlock(540, 570))) }
        compose.onRoot().performTouchInput { up() }
        compose.onNodeWithText("That time is no longer available").fetchSemanticsNode()
        compose.runOnIdle { check(session.state.value.drafts.isEmpty()) }
    }

    private fun saveDragScreenshot(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "issue-85")
        check(directory.exists() || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun dragQueueTaskToMorning(title: String) {
        val source = compose.onNodeWithContentDescription("Schedule $title").fetchSemanticsNode().boundsInRoot.center
        val lane = compose.onNodeWithTag("day-lane-planned").fetchSemanticsNode().boundsInRoot
        val slotPx = with(compose.density) { TimeboxDimens.slotHeight.toPx() }
        compose.onRoot().performTouchInput {
            down(source)
            advanceEventTime(1_000)
            moveTo(Offset(lane.center.x, lane.top + 2.5f * slotPx))
        }
    }

    private fun plannedBlock(start: Int, end: Int) = TimeBlock(
        id = 1, lane = Lane.Planned, taskTypeId = 1, taskTypeName = "work",
        taskId = null, task = null, note = null, plannedBlockId = null,
        startMinute = start, endMinute = end,
    )

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
    fun draftPlannedBlockResizeGroovesStartImmediatelyAndExpandDuration() {
        val date = LocalDate.of(2026, 8, 20)
        val draftTask = task(42, "Resize me")
        var updated: DraftPlannedBlockUpdate? = null
        val haptics = RecordingHaptics()
        val state = DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = emptyDay(date), loading = false, materialized = true)),
            planning = PlanningSessionState(
                active = true,
                readyTasks = listOf(draftTask, task(43, "Other task")),
                drafts = mapOf(
                    draftTask.id to PlanningDraftPlacement(date, draftTask, 9 * 60, 9 * 60 + 30)
                ),
            ),
        )
        setPlanningContent(
            state = state,
            haptics = haptics,
            onUpdatePlanningDraft = { id, start, end ->
                updated = DraftPlannedBlockUpdate(id, start, end)
            },
        )

        val draft = compose.onNodeWithContentDescription("Planning draft Resize me")
        draft.performTouchInput {
            val bottomGroove = Offset(center.x, height - 2f)
            swipe(bottomGroove, bottomGroove + Offset(0f, height.toFloat()), durationMillis = 200)
        }
        compose.runOnIdle {
            check(updated == DraftPlannedBlockUpdate(42, 9 * 60, 10 * 60)) { "$updated" }
            check(haptics.events.isEmpty())
            updated = null
        }

        draft.performTouchInput {
            val topGroove = Offset(center.x, 2f)
            swipe(topGroove, topGroove - Offset(0f, height.toFloat()), durationMillis = 200)
        }
        compose.runOnIdle {
            check(updated == DraftPlannedBlockUpdate(42, 8 * 60 + 30, 9 * 60 + 30)) { "$updated" }
            check(haptics.events.isEmpty())
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

    private data class DraftPlannedBlockUpdate(
        val taskId: Int,
        val startMinute: Int,
        val endMinute: Int,
    )

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
