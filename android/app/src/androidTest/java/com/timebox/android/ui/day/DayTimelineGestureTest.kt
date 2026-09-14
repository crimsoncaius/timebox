package com.timebox.android.ui.day

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.semantics.SemanticsActions
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.LinkedTask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DayTimelineGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun droppingBlockCannotJumpViewportDuringBackgroundRefresh() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date, startMinute = 1320, endMinute = 1350, endHour = 24, serverNowMinute = 1380)
        val moving = base.day!!.blocks.single()
        val loaded = base.copy(pages = base.pages + (date to base.page(date).copy(
            day = base.day!!.copy(blocks = listOf(moving, moving.copy(id = 8, startMinute = 1350, endMinute = 1380))))))
        val state = androidx.compose.runtime.mutableStateOf(loaded)
        var committed = false
        setDayContent(loaded, RecordingHaptics(), stateProvider = { state.value }, onCommitMove = { id, start, end ->
            committed = true
            state.value = loaded.copy(saving = true, pages = loaded.pages +
                (date to loaded.page(date).copy(day = loaded.day!!.copy(blocks = loaded.day!!.blocks.map {
                    if (it.id == id) it.copy(startMinute = start, endMinute = end) else it
                }))) + (date.plusDays(1) to DayPageState(
                    day = base.day!!.copy(date = date.plusDays(1), endHour = 20), loading = false)))
        })
        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, height * 1.5f))
        }
        val before = compose.onNodeWithTag("day-now-line").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { up() }
        val positions = mutableListOf<Float>()
        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            positions += compose.onNodeWithTag("day-now-line").fetchSemanticsNode().boundsInRoot.top
        }
        compose.runOnUiThread { state.value = state.value.copy(saving = false) }
        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            positions += compose.onNodeWithTag("day-now-line").fetchSemanticsNode().boundsInRoot.top
        }
        compose.mainClock.autoAdvance = true
        check(committed)
        check(positions.all { kotlin.math.abs(it - before) < 2f }) { "Viewport jumped: before=$before frames=$positions" }
    }

    @Test
    fun droppingIntoOccupiedTimeKeepsTimelineStable() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date, serverNowMinute = 600)
        val moving = base.day!!.blocks.single()
        val blocker = moving.copy(id = 8, startMinute = 600, endMinute = 660)
        val state = androidx.compose.runtime.mutableStateOf(base.copy(pages = base.pages +
            (date to base.page(date).copy(day = base.day!!.copy(blocks = listOf(moving, blocker))))))
        setDayContent(base, RecordingHaptics(), stateProvider = { state.value }, onCommitMove = { id, start, end ->
            val current = state.value
            state.value = current.copy(saving = true, pages = current.pages + (date to current.page(date).copy(
                day = current.day!!.copy(blocks = current.day!!.blocks.map {
                    if (it.id == id) it.copy(startMinute = start, endMinute = end) else it
                }))))
        })
        val laneTop = compose.onNodeWithTag("day-lane-planned").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, height * 1.5f))
        }
        val destination = compose.onNodeWithTag("saved-planned-move-preview").fetchSemanticsNode().boundsInRoot.top
        compose.onRoot().performTouchInput { up() }
        val released = compose.onNodeWithTag("day-block-7").fetchSemanticsNode().boundsInRoot.top
        check(kotlin.math.abs(released - destination) < 2f) { "Drop jumped from preview $destination to $released" }
        check(kotlin.math.abs(compose.onNodeWithTag("day-lane-planned").fetchSemanticsNode().boundsInRoot.top - laneTop) < 2f) { "Timeline jumped on release" }
        compose.runOnIdle { state.value = state.value.copy(saving = false) }
        check(kotlin.math.abs(compose.onNodeWithTag("day-block-7").fetchSemanticsNode().boundsInRoot.top - released) < 2f) { "Block jumped after save" }
    }

    @Test
    fun savedActualBlockDragCommitsNearestAvailableRange() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date, lane = Lane.Actual, serverNowMinute = 1200)
        val moving = base.day!!.blocks.single()
        val blocker = moving.copy(id = 8, startMinute = 10 * 60, endMinute = 11 * 60)
        var committedMove: Triple<Int, Int, Int>? = null
        setDayContent(
            state = base.copy(
                pages = base.pages + (date to base.page(date).copy(day = base.day!!.copy(blocks = listOf(moving, blocker)))),
            ),
            haptics = RecordingHaptics(),
            onCommitMove = { id, start, end -> committedMove = Triple(id, start, end) },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, height * 1.5f))
        }
        compose.onNodeWithTag("saved-actual-move-preview").assertIsDisplayed()
        compose.onNodeWithText("11:00 – 12:00").assertIsDisplayed()
        compose.onRoot().performTouchInput { up() }

        compose.runOnIdle {
            check(committedMove == Triple(7, 11 * 60, 12 * 60)) { "Committed $committedMove" }
        }
    }

    @Test
    fun savedActualBlockAcceptsDistantGap() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date, lane = Lane.Actual, serverNowMinute = 1200)
        val moving = base.day!!.blocks.single()
        val blocker = moving.copy(id = 8, startMinute = 600, endMinute = 660)
        var committed: Triple<Int, Int, Int>? = null
        setDayContent(base.copy(pages = base.pages + (date to base.page(date).copy(
            day = base.day!!.copy(blocks = listOf(moving, blocker))))), RecordingHaptics(),
            onCommitMove = { id, start, end -> committed = Triple(id, start, end) })
        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, height.toFloat()))
        }
        compose.onNodeWithTag("saved-actual-move-preview").assertIsDisplayed()
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { check(committed == Triple(7, 660, 720)) }
    }

    @Test
    fun actualResizeStopsAtExactNeighborBoundaries() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date, lane = Lane.Actual, serverNowMinute = 1200)
        val moving = base.day!!.blocks.single()
        val before = moving.copy(id = 8, startMinute = 480, endMinute = 527)
        val after = moving.copy(id = 9, startMinute = 613, endMinute = 660)
        var committed: Triple<Int, Int, Int>? = null
        setDayContent(base.copy(pages = base.pages + (date to base.page(date).copy(
            day = base.day!!.copy(blocks = listOf(before, moving, after))))), RecordingHaptics(),
            onCommitMove = { id, start, end -> committed = Triple(id, start, end) })
        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(Offset(center.x, 2f))
            moveTo(Offset(center.x, -height.toFloat()))
            up()
        }
        compose.runOnIdle { check(committed == Triple(7, 527, 600)) { "$committed" } }
        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(Offset(center.x, height - 2f))
            moveTo(Offset(center.x, height * 2f))
            up()
        }
        compose.runOnIdle { check(committed == Triple(7, 540, 613)) { "$committed" } }
    }

    @Test
    fun derivedActualAndRevisedPlanPresentTheirIndependentNames() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date)
        val planned = base.day!!.blocks.single().copy(name = "Revised plan")
        val actual = planned.copy(
            id = -44,
            lane = Lane.Actual,
            name = "Original snapshot",
            plannedBlockId = planned.id,
            actualBlockId = 44,
        )
        setDayContent(
            state = base.copy(
                pages = base.pages + (
                    date to base.page(date).copy(day = base.day!!.copy(blocks = listOf(planned, actual)))
                ),
            ),
            haptics = RecordingHaptics(),
        )

        compose.onNodeWithText("Revised plan").assertIsDisplayed()
        compose.onNodeWithText("Original snapshot").assertIsDisplayed()
    }

    @Test
    fun standaloneActualUsesNameThenMeaningfulTaskTypeThenUntitled() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date, lane = Lane.Actual)
        val named = base.day!!.blocks.single().copy(name = "Dinner with Alex", taskTypeName = "social")
        setDayContent(
            state = base.copy(pages = base.pages + (date to base.page(date).copy(day = base.day!!.copy(blocks = listOf(named))))),
            haptics = RecordingHaptics(),
        )

        compose.onNodeWithText("Dinner with Alex").assertIsDisplayed()
        compose.onNodeWithText("social").assertIsDisplayed()
    }

    @Test
    fun taskBackedDayBlockUsesNameThenLinkedTaskContext() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date)
        val task = LinkedTask(
            id = 42,
            title = "Prepare launch",
            status = TaskStatus.InProgress,
            taskTypeId = 1,
            archivedAt = null,
            deletedAt = null,
        )
        val named = base.day!!.blocks.single().copy(
            name = "Outline session",
            taskId = task.id,
            task = task,
        )
        setDayContent(
            state = base.copy(
                pages = base.pages + (date to base.page(date).copy(day = base.day!!.copy(blocks = listOf(named)))),
            ),
            haptics = RecordingHaptics(),
        )

        compose.onNodeWithText("Outline session · Task ○").assertIsDisplayed()
        compose.onNodeWithText("Prepare launch").assertIsDisplayed()
    }

    @Test
    fun todayInitiallyScrollsTheCurrentTimeLineIntoView() {
        val date = LocalDate.of(2026, 8, 20)
        setDayContent(
            state = stateWithBlock(date, serverNowMinute = 17 * 60),
            haptics = RecordingHaptics(),
        )

        compose.onNodeWithTag("day-now-line").assertIsDisplayed()
    }

    @Test
    fun plannedBlockMoveUsesFiveMinuteDeltaWithoutNormalizing() {
        val date = LocalDate.of(2026, 8, 20)
        var committedMove: Triple<Int, Int, Int>? = null
        val haptics = RecordingHaptics()

        setDayContent(
            state = stateWithBlock(date, startMinute = 9 * 60 + 7, endMinute = 10 * 60 + 7),
            haptics = haptics,
            onCommitMove = { id, start, end -> committedMove = Triple(id, start, end) },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, height / 12f))
            up()
        }

        compose.runOnIdle {
            check(committedMove == Triple(7, 9 * 60 + 12, 10 * 60 + 12))
        }
    }

    @Test
    fun savedPlannedBlockDragCommitsNearestAvailableRange() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date)
        val moving = base.day!!.blocks.single()
        val blocker = moving.copy(id = 8, startMinute = 10 * 60, endMinute = 11 * 60)
        var committedMove: Triple<Int, Int, Int>? = null
        setDayContent(
            state = base.copy(
                pages = base.pages + (date to base.page(date).copy(day = base.day!!.copy(blocks = listOf(moving, blocker)))),
            ),
            haptics = RecordingHaptics(),
            onCommitMove = { id, start, end -> committedMove = Triple(id, start, end) },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, height * 1.5f))
        }
        compose.onNodeWithTag("saved-planned-move-preview").assertIsDisplayed()
        compose.onNodeWithText("11:00 – 12:00").assertIsDisplayed()
        compose.onRoot().performTouchInput { up() }

        compose.runOnIdle {
            check(committedMove == Triple(7, 11 * 60, 12 * 60)) { "Committed $committedMove" }
        }
    }

    @Test
    fun savedPlannedBlockAcceptsDistantGap() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date)
        val moving = base.day!!.blocks.single()
        val blocker = moving.copy(id = 8, startMinute = 600, endMinute = 660)
        var committed: Triple<Int, Int, Int>? = null
        setDayContent(base.copy(pages = base.pages + (date to base.page(date).copy(
            day = base.day!!.copy(blocks = listOf(moving, blocker))))), RecordingHaptics(),
            onCommitMove = { id, start, end -> committed = Triple(id, start, end) })
        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, height.toFloat()))
        }
        compose.onNodeWithTag("saved-planned-move-preview").assertIsDisplayed()
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { check(committed == Triple(7, 660, 720)) }
    }

    @Test
    fun plannedResizeStopsAtExactNeighborBoundaries() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date)
        val moving = base.day!!.blocks.single()
        val before = moving.copy(id = 8, startMinute = 480, endMinute = 527)
        val after = moving.copy(id = 9, startMinute = 613, endMinute = 660)
        var committed: Triple<Int, Int, Int>? = null
        setDayContent(base.copy(pages = base.pages + (date to base.page(date).copy(
            day = base.day!!.copy(blocks = listOf(before, moving, after))))), RecordingHaptics(),
            onCommitMove = { id, start, end -> committed = Triple(id, start, end) })
        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(Offset(center.x, 2f))
            moveTo(Offset(center.x, -height.toFloat()))
            up()
        }
        compose.runOnIdle { check(committed == Triple(7, 527, 600)) { "$committed" } }
        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(Offset(center.x, height - 2f))
            moveTo(Offset(center.x, height * 2f))
            up()
        }
        compose.runOnIdle { check(committed == Triple(7, 540, 613)) { "$committed" } }
    }

    @Test
    fun savedPlannedBlockAccessibleMoveUsesNearestAvailableRange() {
        val date = LocalDate.of(2026, 8, 20)
        val base = stateWithBlock(date)
        val moving = base.day!!.blocks.single()
        val blocker = moving.copy(id = 8, startMinute = 10 * 60, endMinute = 11 * 60)
        var committedMove: Triple<Int, Int, Int>? = null
        setDayContent(
            state = base.copy(
                pages = base.pages + (date to base.page(date).copy(day = base.day!!.copy(blocks = listOf(moving, blocker)))),
            ),
            haptics = RecordingHaptics(),
            onCommitMove = { id, start, end -> committedMove = Triple(id, start, end) },
        )

        val moveLater = compose.onNodeWithTag("day-block-7")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == "Move 5 minutes later" }
        check(moveLater.action())

        compose.runOnIdle {
            check(committedMove == Triple(7, 9 * 60, 10 * 60)) { "Committed $committedMove" }
        }
    }

    @Test
    fun tappingEitherLaneCreatesAtContainingHalfHour() {
        val date = LocalDate.of(2026, 8, 20)
        var tapped: Pair<Lane, Int>? = null

        setDayContent(
            state = stateWithBlock(date, endHour = 10, includeBlock = false),
            haptics = RecordingHaptics(),
            onTapSlot = { lane, minute -> tapped = lane to minute },
        )

        for ((tag, lane) in listOf("planned" to Lane.Planned, "actual" to Lane.Actual)) {
            for ((offset, expected) in listOf(80f to 540, 90f to 570, 119f to 570)) {
                compose.onNodeWithTag("day-lane-$tag").performTouchInput {
                    click(Offset(center.x, height * offset / 120f))
                }
                compose.runOnIdle {
                    check(tapped == lane to expected) { "Tapped $tapped; expected $lane at $expected" }
                }
            }
        }
    }

    @Test
    fun plannedBlockRequiresLongPressBeforeMove() {
        val date = LocalDate.of(2026, 8, 20)
        var committedMove: Triple<Int, Int, Int>? = null
        val haptics = RecordingHaptics()
        val state = stateWithBlock(date)

        setDayContent(
            state = state,
            haptics = haptics,
            onCommitMove = { id, start, end -> committedMove = Triple(id, start, end) },
        )

        val beforeScroll = compose.onNodeWithTag("day-block-7").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("day-block-7").performTouchInput {
            swipe(center, center + Offset(0f, -160f), durationMillis = 200)
        }
        compose.runOnIdle {
            check(committedMove == null)
            check(haptics.events.isEmpty())
        }
        val afterScroll = compose.onNodeWithTag("day-block-7").fetchSemanticsNode().boundsInRoot.top
        check(afterScroll < beforeScroll)

        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, height / 4f))
            up()
        }
        compose.runOnIdle {
            check(committedMove == Triple(7, 9 * 60 + 15, 10 * 60 + 15))
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
        }
    }

    @Test
    fun plannedBlockQuickTapSelectsButStationaryLongPressDoesNot() {
        val date = LocalDate.of(2026, 8, 20)
        var selections = 0
        var commits = 0
        val haptics = RecordingHaptics()
        setDayContent(
            state = stateWithBlock(date),
            haptics = haptics,
            onSelectBlock = { selections += 1 },
            onCommitMove = { _, _, _ -> commits += 1 },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            up()
        }
        compose.runOnIdle {
            check(selections == 1)
            check(commits == 0)
            check(haptics.events.isEmpty())
        }

        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        compose.runOnIdle {
            check(selections == 1)
            check(commits == 0)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
        }
    }

    @Test
    fun plannedBlockResizeGroovesAreImmediate() {
        val date = LocalDate.of(2026, 8, 20)
        var committedMove: Triple<Int, Int, Int>? = null
        val haptics = RecordingHaptics()
        setDayContent(
            state = stateWithBlock(date),
            haptics = haptics,
            onCommitMove = { id, start, end -> committedMove = Triple(id, start, end) },
        )

        val block = compose.onNodeWithTag("day-block-7")
        block.performTouchInput {
            val topGroove = Offset(center.x, 2f)
            down(topGroove)
            moveTo(topGroove - Offset(0f, height / 12f))
            up()
        }
        compose.runOnIdle {
            check(committedMove == Triple(7, 9 * 60 - 5, 10 * 60))
            check(haptics.events.isEmpty())
            committedMove = null
            haptics.events.clear()
        }

        block.performTouchInput {
            val bottomGroove = Offset(center.x, height - 2f)
            down(bottomGroove)
            moveTo(bottomGroove + Offset(0f, height / 12f))
            up()
        }
        compose.runOnIdle {
            check(committedMove == Triple(7, 9 * 60, 10 * 60 + 5))
            check(haptics.events.isEmpty())
        }
    }

    @Test
    fun earlyHorizontalMovementNavigatesDayWithoutSelectingOrMovingBlock() {
        val date = LocalDate.of(2026, 8, 20)
        var settledDate: LocalDate? = null
        var selected = false
        var moved = false
        val haptics = RecordingHaptics()
        setDayContent(
            state = stateWithBlock(date),
            haptics = haptics,
            onDateSettled = { settledDate = it },
            onSelectBlock = { selected = true },
            onCommitMove = { _, _, _ -> moved = true },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            swipe(center, center + Offset(-500f, 0f), durationMillis = 200)
        }
        compose.runOnIdle {
            check(settledDate == date.plusDays(1))
            check(!selected)
            check(!moved)
            check(haptics.events.isEmpty())
        }
    }

    @Test
    fun nextDaySwipeUpdatesHeaderWithinFirstFrame() {
        val thursday = LocalDate.of(2026, 8, 20)
        val friday = thursday.plusDays(1)
        val thursdayState = stateWithBlock(thursday)
        val fridayDay = thursdayState.day!!.copy(
            date = friday,
            blocks = listOf(thursdayState.day!!.blocks.single().copy(id = 8, taskTypeName = "Friday content")),
        )
        val navigated = mutableListOf<LocalDate>()
        setDayContent(
            state = thursdayState.copy(
                pages = thursdayState.pages + (
                    friday to DayPageState(day = fridayDay, loading = false, materialized = false)
                ),
            ),
            haptics = RecordingHaptics(),
            onDateSettled = navigated::add,
        )
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("day-pager").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(16L)

        compose.onNodeWithText("Friday content").assertIsDisplayed()
        compose.onNodeWithText("Fri, August 21").assertIsDisplayed()
        compose.runOnIdle { check(navigated.isEmpty()) }
    }

    @Test
    fun previousDaySwipeUpdatesHeaderWithinFirstFrame() {
        val friday = LocalDate.of(2026, 8, 21)
        val thursday = friday.minusDays(1)
        val fridayState = stateWithBlock(friday)
        val thursdayDay = fridayState.day!!.copy(
            date = thursday,
            blocks = listOf(fridayState.day!!.blocks.single().copy(id = 6, taskTypeName = "Thursday content")),
        )
        val navigated = mutableListOf<LocalDate>()
        setDayContent(
            state = fridayState.copy(
                pages = fridayState.pages + (
                    thursday to DayPageState(day = thursdayDay, loading = false, materialized = false)
                ),
            ),
            haptics = RecordingHaptics(),
            onDateSettled = navigated::add,
        )
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("day-pager").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(16L)

        compose.onNodeWithText("Thu, August 20").assertIsDisplayed()
        compose.onNodeWithText("Thursday content").assertIsDisplayed()
        compose.runOnIdle { check(navigated.isEmpty()) }
    }

    @Test
    fun cancelledDaySwipeKeepsCurrentHeaderAndDoesNotNavigate() {
        val thursday = LocalDate.of(2026, 8, 20)
        val navigated = mutableListOf<LocalDate>()
        setDayContent(
            state = stateWithBlock(thursday),
            haptics = RecordingHaptics(),
            onDateSettled = navigated::add,
        )
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("day-pager").performTouchInput {
            down(center)
            moveTo(center + Offset(-500f, 0f))
            cancel()
        }
        compose.mainClock.advanceTimeBy(16L)

        compose.onNodeWithText("Thu, August 20").assertIsDisplayed()
        compose.runOnIdle { check(navigated.isEmpty()) }
    }

    @Test
    fun actualBlockMovesLikePlannedBlock() {
        val date = LocalDate.of(2026, 8, 20)
        var committedMove: Triple<Int, Int, Int>? = null
        val haptics = RecordingHaptics()
        setDayContent(
            state = stateWithBlock(date, Lane.Actual),
            haptics = haptics,
            onCommitMove = { id, start, end -> committedMove = Triple(id, start, end) },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, height / 4f))
            up()
        }
        compose.runOnIdle {
            check(committedMove == Triple(7, 9 * 60 + 15, 10 * 60 + 15))
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
        }
    }

    @Test
    fun actualBlockResizeGroovesAreImmediate() {
        val date = LocalDate.of(2026, 8, 20)
        var committedMove: Triple<Int, Int, Int>? = null
        val haptics = RecordingHaptics()
        setDayContent(
            state = stateWithBlock(date, Lane.Actual, serverNowMinute = 12 * 60),
            haptics = haptics,
            onCommitMove = { id, start, end -> committedMove = Triple(id, start, end) },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            val bottomGroove = Offset(center.x, height - 2f)
            down(bottomGroove)
            moveTo(bottomGroove + Offset(0f, height / 12f))
            up()
        }

        compose.runOnIdle {
            check(committedMove == Triple(7, 9 * 60, 10 * 60 + 5))
            check(haptics.events.isEmpty())
        }
    }

    @Test
    fun actualBlockResizeCanRemainShorterThanThirtyMinutes() {
        val date = LocalDate.of(2026, 8, 20)
        var committedMove: Triple<Int, Int, Int>? = null

        setDayContent(
            state = stateWithBlock(
                date = date,
                lane = Lane.Actual,
                startMinute = 9 * 60,
                endMinute = 9 * 60 + 10,
            ),
            haptics = RecordingHaptics(),
            onCommitMove = { id, start, end -> committedMove = Triple(id, start, end) },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            val bottomGroove = Offset(center.x, height - 2f)
            down(bottomGroove)
            moveTo(bottomGroove - Offset(0f, height / 6f))
            up()
        }

        compose.runOnIdle {
            check(committedMove == Triple(7, 9 * 60, 9 * 60 + 5))
        }
    }

    @Test
    fun canceledArmedDragDoesNotCommitPartialMove() {
        val date = LocalDate.of(2026, 8, 20)
        var moved = false
        val haptics = RecordingHaptics()
        setDayContent(
            state = stateWithBlock(date),
            haptics = haptics,
            onCommitMove = { _, _, _ -> moved = true },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, 150f))
            cancel()
        }
        compose.runOnIdle {
            check(!moved)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
        }
    }

    private fun setDayContent(
        state: DayUiState,
        haptics: RecordingHaptics,
        onDateSettled: (LocalDate) -> Unit = {},
        onTapSlot: (Lane, Int) -> Unit = { _, _ -> },
        onSelectBlock: (Int) -> Unit = {},
        onCommitMove: (Int, Int, Int) -> Unit = { _, _, _ -> },
        stateProvider: () -> DayUiState = { state },
    ) {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    DayScreen(
                        state = stateProvider(),
                        onDateSettled = onDateSettled, onRetry = {}, onTapSlot = onTapSlot,
                        onSelectBlock = onSelectBlock, onCommitMove = onCommitMove,
                        onDismissSheet = {}, onChooseType = {}, onTypeQueryChange = {},
                        onCreateType = {}, onNoteChange = {}, onDeleteSelected = {},
                        onConfirmSelectedTaskCompletion = {}, onReopenSelectedTask = {},
                        onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                        onArmAccessibleTask = {}, onRetryReadyTasks = {},
                    )
                }
            }
        }
    }

    private fun stateWithBlock(
        date: LocalDate,
        lane: Lane = Lane.Planned,
        startMinute: Int = 9 * 60,
        endMinute: Int = 10 * 60,
        endHour: Int = 20,
        includeBlock: Boolean = true,
        serverNowMinute: Int = 9 * 60,
    ): DayUiState {
        val day = Day(
            date = date,
            startHour = 8,
            endHour = endHour,
            showFullDay = false,
            blocks = if (includeBlock) listOf(
                TimeBlock(
                    id = 7,
                    lane = lane,
                    taskTypeId = 1,
                    taskTypeName = "Focused work",
                    taskId = null,
                    task = null,
                    note = null,
                    plannedBlockId = null,
                    startMinute = startMinute,
                    endMinute = endMinute,
                ),
            ) else emptyList(),
            timezone = "Asia/Singapore",
            today = date,
            serverNowMinute = serverNowMinute,
        )
        return DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = day, loading = false, materialized = true)),
            today = date,
        )
    }
}
