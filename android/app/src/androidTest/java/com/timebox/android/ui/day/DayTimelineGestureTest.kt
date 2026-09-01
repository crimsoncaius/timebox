package com.timebox.android.ui.day

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DayTimelineGestureTest {
    @get:Rule val compose = createComposeRule()

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
    fun tappingTimelineCreatesAtNearestFiveMinuteMark() {
        val date = LocalDate.of(2026, 8, 20)
        var tapped: Pair<Lane, Int>? = null

        setDayContent(
            state = stateWithBlock(date, endHour = 10, includeBlock = false),
            haptics = RecordingHaptics(),
            onTapSlot = { lane, minute -> tapped = lane to minute },
        )

        compose.onNodeWithTag("day-lane-planned").performTouchInput {
            val minuteOffset = 67f
            val visibleMinutes = 2 * 60f
            click(Offset(center.x, height * minuteOffset / visibleMinutes))
        }

        compose.runOnIdle {
            check(tapped == Lane.Planned to (9 * 60 + 5)) { "Tapped $tapped" }
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
    fun actualBlockDoesNotArmOrMove() {
        val date = LocalDate.of(2026, 8, 20)
        var moved = false
        val haptics = RecordingHaptics()
        setDayContent(
            state = stateWithBlock(date, Lane.Actual),
            haptics = haptics,
            onCommitMove = { _, _, _ -> moved = true },
        )

        compose.onNodeWithTag("day-block-7").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, 120f))
            up()
        }
        compose.runOnIdle {
            check(!moved)
            check(haptics.events.isEmpty())
        }
    }

    @Test
    fun actualBlockResizeGroovesAreImmediate() {
        val date = LocalDate.of(2026, 8, 20)
        var committedMove: Triple<Int, Int, Int>? = null
        val haptics = RecordingHaptics()
        setDayContent(
            state = stateWithBlock(date, Lane.Actual),
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
    ) {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    DayScreen(
                        state = state,
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
            serverNowMinute = 9 * 60,
        )
        return DayUiState(
            date = date,
            pages = mapOf(date to DayPageState(day = day, loading = false, materialized = true)),
            today = date,
        )
    }
}
