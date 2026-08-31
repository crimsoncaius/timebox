package com.timebox.android.ui.day

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.EmptyStateCard
import com.timebox.android.ui.components.Kicker
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DayScreen(
    state: DayUiState,
    onDateSettled: (LocalDate) -> Unit,
    onRetry: (LocalDate) -> Unit,
    onTapSlot: (Lane, Int) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
    onDismissSheet: () -> Unit,
    onChooseType: (TaskType) -> Unit,
    onTypeQueryChange: (String) -> Unit,
    onCreateType: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onConfirmSelectedTaskCompletion: () -> Unit,
    onReopenSelectedTask: () -> Unit,
    onOpenLinkedTask: (Int) -> Unit,
    onSetPlanningMode: (Boolean) -> Unit,
    onCommitPlanningMode: () -> Unit = {},
    onCancelPlanningMode: () -> Unit = {},
    onPlanTask: (Int, Int) -> Unit,
    onUpdatePlanningDraft: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onReturnPlanningDraft: (Int) -> Unit = {},
    onArmAccessibleTask: (Int?) -> Unit,
    onRetryReadyTasks: () -> Unit,
    onNavigateToday: (LocalDate) -> Unit = {},
    onOpenWorkMode: () -> Unit = {},
) {
    BackHandler(enabled = state.isPlanningMode) {
        if (!state.saving) onCancelPlanningMode()
    }

    Column(Modifier.fillMaxSize()) {
        DayCalendarHeader(
            selectedDate = state.date,
            today = state.today,
            isPlanningMode = state.isPlanningMode,
            planningActionEnabled = !state.saving,
            onOpenWorkMode = onOpenWorkMode,
            onSetPlanningMode = { enabled ->
                if (enabled) onSetPlanningMode(true) else onCommitPlanningMode()
            },
            onSelectDate = onDateSettled,
            onNavigateToday = onNavigateToday,
        )

        Box(Modifier.weight(1f)) {
            if (state.isPlanningMode) {
                PlanningDayPage(
                    state = state,
                    onRetry = onRetry,
                    onSelectBlock = onSelectBlock,
                    onCommitMove = onCommitMove,
                    onPlanTask = onPlanTask,
                    onUpdatePlanningDraft = onUpdatePlanningDraft,
                    onReturnPlanningDraft = onReturnPlanningDraft,
                    onArmAccessibleTask = onArmAccessibleTask,
                    onRetryReadyTasks = onRetryReadyTasks,
                )
            } else {
                InteractiveDayPager(
                    state = state,
                    onDateSettled = onDateSettled,
                    onRetry = onRetry,
                    onTapSlot = onTapSlot,
                    onSelectBlock = onSelectBlock,
                    onCommitMove = onCommitMove,
                )
            }
        }
    }

    if (state.sheetOpen) {
        BlockSheet(
            state = state,
            onDismiss = onDismissSheet,
            onChooseType = onChooseType,
            onTypeQueryChange = onTypeQueryChange,
            onCreateType = onCreateType,
            onNoteChange = onNoteChange,
            onDelete = onDeleteSelected,
            onConfirmTaskCompletion = onConfirmSelectedTaskCompletion,
            onReopenTask = onReopenSelectedTask,
            onOpenLinkedTask = onOpenLinkedTask,
            allowComplete = !state.isPlanningMode,
        )
    }
}

@Composable
private fun PlanningDayPage(
    state: DayUiState,
    onRetry: (LocalDate) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
    onPlanTask: (Int, Int) -> Unit,
    onUpdatePlanningDraft: (Int, Int, Int) -> Unit,
    onReturnPlanningDraft: (Int) -> Unit,
    onArmAccessibleTask: (Int?) -> Unit,
    onRetryReadyTasks: () -> Unit,
) {
    val page = state.currentPage
    val showTaskRail = state.hasPlanningRailContent(state.date)
    Column(Modifier.fillMaxSize()) {
        PlanningModeHeaders(showTaskRail, Modifier.padding(bottom = 8.dp))
        if (!showTaskRail) {
            EmptyStateCard(
                title = "Your planning queue is clear",
                description = "Mark a Battle Plan Task Ready to Plan when you want to schedule it.",
                modifier = Modifier.padding(horizontal = TimeboxDimens.screenPadding, vertical = 4.dp),
            )
        }
        val day = page.day
        when {
            page.loading && day == null -> LoadingState(Modifier.weight(1f))
            page.error != null && day == null -> ErrorState(
                message = page.error,
                onRetry = { onRetry(state.date) },
                modifier = Modifier.weight(1f),
            )
            day != null -> PlanningWorkspace(
                state = state,
                day = day,
                onSelectBlock = onSelectBlock,
                onCommitMove = onCommitMove,
                onPlanTask = onPlanTask,
                onUpdatePlanningDraft = onUpdatePlanningDraft,
                onReturnPlanningDraft = onReturnPlanningDraft,
                onArmAccessibleTask = onArmAccessibleTask,
                onRetryReadyTasks = onRetryReadyTasks,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** How far sideways a drag must travel before it counts as changing the day. */
private val DAY_SWIPE_THRESHOLD = 55.dp

/**
 * A clipped three-page track. The track follows the finger directly, then settles to
 * the adjacent page or returns home. The selected date changes only after animation.
 */
@Composable
private fun InteractiveDayPager(
    state: DayUiState,
    onDateSettled: (LocalDate) -> Unit,
    onRetry: (LocalDate) -> Unit,
    onTapSlot: (Lane, Int) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val isSettling = rememberUpdatedState(settling)
    val settleDate = rememberUpdatedState(onDateSettled)
    val scope = rememberCoroutineScope()
    // The incoming timeline mirrors the current vertical position. If its height differs,
    // ScrollState clamps to the legal range for the page being measured.
    val timelineScroll = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val pageWidthPx = constraints.maxWidth.toFloat()
        val thresholdPx = with(density) { DAY_SWIPE_THRESHOLD.toPx() }

        fun settle(dayDelta: Long?) {
            if (settling || pageWidthPx <= 0f) return
            val baseDate = state.date
            settling = true
            scope.launch {
                val target = dayDelta?.let { -it * pageWidthPx } ?: 0f
                animate(
                    initialValue = dragOffsetPx,
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 520f),
                ) { value, _ ->
                    dragOffsetPx = value
                }
                if (dayDelta != null) settleDate.value(baseDate.plusDays(dayDelta))
                dragOffsetPx = 0f
                settling = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // This remains outside the vertical scroll and block handlers. Compose's
                // gesture arbitration therefore preserves their existing priority.
                .pointerInput(state.date, pageWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (!isSettling.value) dragOffsetPx = 0f
                        },
                        onDragCancel = { settle(null) },
                        onDragEnd = {
                            settle(committedDayDelta(dragOffsetPx, thresholdPx))
                        },
                        onHorizontalDrag = { change, amount ->
                            if (!isSettling.value) {
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + amount)
                                    .coerceIn(-pageWidthPx, pageWidthPx)
                            }
                        },
                    )
                },
        ) {
            (-1..1).forEach { pagePosition ->
                val date = state.date.plusDays(pagePosition.toLong())
                val interactive = pagePosition == 0 && !settling
                key(date) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                IntOffset(
                                    x = (pagePosition * pageWidthPx + dragOffsetPx).roundToInt(),
                                    y = 0,
                                )
                            },
                    ) {
                        DayPage(
                            date = date,
                            page = state.page(date),
                            scrollState = timelineScroll,
                            selectedBlockId = if (interactive) state.selectedBlockId else null,
                            draft = if (interactive) state.draft else null,
                            onRetry = { onRetry(date) },
                            onTapSlot = if (interactive) onTapSlot else ({ _, _ -> }),
                            onSelectBlock = if (interactive) onSelectBlock else ({ }),
                            onCommitMove = if (interactive) onCommitMove else ({ _, _, _ -> }),
                        )
                    }
                }
            }
        }
    }
}

/** The date change represented by a released drag, or null when it should snap back. */
internal fun committedDayDelta(dragPx: Float, thresholdPx: Float): Long? = when {
    dragPx < -thresholdPx -> 1L
    dragPx > thresholdPx -> -1L
    else -> null
}

@Composable
private fun DayPage(
    date: LocalDate,
    page: DayPageState,
    scrollState: ScrollState,
    selectedBlockId: Int?,
    draft: Draft?,
    onRetry: () -> Unit,
    onTapSlot: (Lane, Int) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TimeboxDimens.screenPadding)
                .padding(top = 8.dp, bottom = 8.dp)
                .testTag("day-lane-headers"),
            horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
        ) {
            Spacer(Modifier.width(TimeboxDimens.gutterWidth))
            Kicker("Planned", colors.planned, Modifier.weight(1f))
            Kicker("Actual", colors.actual, Modifier.weight(1f))
        }

        val day = page.day
        when {
            page.loading && day == null -> LoadingState(Modifier.weight(1f))
            page.error != null && day == null -> ErrorState(
                message = page.error,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
            day != null -> Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = TimeboxDimens.screenPadding)
                    .padding(bottom = TimeboxDimens.bottomInset),
            ) {
                DayTimeline(
                    day = day,
                    selectedBlockId = selectedBlockId,
                    draft = draft,
                    onTapSlot = onTapSlot,
                    onSelectBlock = onSelectBlock,
                    onCommitMove = onCommitMove,
                )
            }
        }
    }
}
