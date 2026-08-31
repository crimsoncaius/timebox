package com.timebox.android.ui.day

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
            moveTo(center + Offset(0f, 120f))
            up()
        }
        compose.runOnIdle {
            check(committedMove?.first == 7)
            check(committedMove?.second != 9 * 60)
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
    fun plannedBlockResizeGroovesRequireLongPress() {
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
            advanceEventTime(1_000)
            moveTo(topGroove + Offset(0f, 150f))
            up()
        }
        compose.runOnIdle {
            check(committedMove?.first == 7)
            check(committedMove?.second != 9 * 60)
            check(committedMove?.third == 10 * 60)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
            committedMove = null
            haptics.events.clear()
        }

        block.performTouchInput {
            val bottomGroove = Offset(center.x, height - 2f)
            down(bottomGroove)
            advanceEventTime(1_000)
            moveTo(bottomGroove + Offset(0f, -150f))
            up()
        }
        compose.runOnIdle {
            check(committedMove?.first == 7)
            check(committedMove?.second == 9 * 60)
            check(committedMove?.third != 10 * 60)
            check(haptics.events == listOf(HapticFeedbackType.LongPress))
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
        onSelectBlock: (Int) -> Unit = {},
        onCommitMove: (Int, Int, Int) -> Unit = { _, _, _ -> },
    ) {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    DayScreen(
                        state = state,
                        onDateSettled = onDateSettled, onRetry = {}, onTapSlot = { _, _ -> },
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

    private fun stateWithBlock(date: LocalDate, lane: Lane = Lane.Planned): DayUiState {
        val day = Day(
            date = date,
            startHour = 8,
            endHour = 20,
            showFullDay = false,
            blocks = listOf(
                TimeBlock(
                    id = 7,
                    lane = lane,
                    taskTypeId = 1,
                    taskTypeName = "Focused work",
                    taskId = null,
                    task = null,
                    note = null,
                    plannedBlockId = null,
                    startMinute = 9 * 60,
                    endMinute = 10 * 60,
                ),
            ),
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
