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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Day
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
    onNameChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit,
    onCreateDraft: () -> Unit = {},
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
    var displayedDate by remember(state.date) { mutableStateOf(state.date) }

    BackHandler(enabled = state.isPlanningMode) {
        if (!state.saving && !state.planning.saving) onCancelPlanningMode()
    }

    Column(Modifier.fillMaxSize()) {
        DayCalendarHeader(
            selectedDate = displayedDate,
            today = state.today,
            isPlanningMode = state.isPlanningMode,
            planningActionEnabled = !state.saving && !state.planning.saving,
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
                    onDisplayedDateChange = { displayedDate = it },
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
            onNameChange = onNameChange,
            onNoteChange = onNoteChange,
            onCreateDraft = onCreateDraft,
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
 * the adjacent page or returns home. A committed target is exposed immediately for
 * the header; durable navigation still waits for the animation to finish.
 */
@Composable
private fun InteractiveDayPager(
    state: DayUiState,
    onDateSettled: (LocalDate) -> Unit,
    onDisplayedDateChange: (LocalDate) -> Unit,
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

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("day-pager")) {
        val pageWidthPx = constraints.maxWidth.toFloat()
        val thresholdPx = with(density) { DAY_SWIPE_THRESHOLD.toPx() }

        fun settle(dayDelta: Long?) {
            if (settling || pageWidthPx <= 0f) return
            val baseDate = state.date
            val targetDate = dayDelta?.let(baseDate::plusDays) ?: baseDate
            settling = true
            onDisplayedDateChange(targetDate)
            scope.launch {
                val target = dayDelta?.let { -it * pageWidthPx } ?: 0f
                animate(
                    initialValue = dragOffsetPx,
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 520f),
                ) { value, _ ->
                    dragOffsetPx = value
                }
                if (dayDelta != null) settleDate.value(targetDate)
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
                            autoScrollToNow = pagePosition == 0 && date == state.today,
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
    autoScrollToNow: Boolean,
    selectedBlockId: Int?,
    draft: Draft?,
    onRetry: () -> Unit,
    onTapSlot: (Lane, Int) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var viewportHeightPx by remember(date) { mutableIntStateOf(0) }
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
                    .onSizeChanged { viewportHeightPx = it.height }
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
                AutoScrollTimelineToNowOnce(
                    day = day,
                    enabled = autoScrollToNow,
                    scrollState = scrollState,
                    viewportHeightPx = viewportHeightPx,
                )
            }
        }
    }
}

/** Scroll once per date so the now line sits one-third down the visible timeline. */
@Composable
internal fun AutoScrollTimelineToNowOnce(
    day: Day,
    enabled: Boolean,
    scrollState: ScrollState,
    viewportHeightPx: Int,
) {
    var completed by remember(day.date) { mutableStateOf(false) }
    val slotHeightPx = with(LocalDensity.current) { TimeboxDimens.slotHeight.toPx() }
    val maxScroll = scrollState.maxValue

    androidx.compose.runtime.LaunchedEffect(
        enabled,
        day.date,
        viewportHeightPx,
        maxScroll,
    ) {
        if (!enabled || completed || viewportHeightPx <= 0) return@LaunchedEffect
        val nowMinute = day.nowMinuteAt(System.currentTimeMillis())
        if (nowMinute == null || nowMinute !in day.visibleStart until day.visibleEnd) {
            completed = true
            return@LaunchedEffect
        }
        val lineOffsetPx = slotHeightPx *
            (nowMinute - day.visibleStart).toFloat() /
            com.timebox.android.data.SLOT_MINUTES
        // A zero maximum can be transient during first layout. Wait if the line is
        // demonstrably below the viewport; maxValue changing will restart this effect.
        if (maxScroll == 0 && lineOffsetPx > viewportHeightPx) return@LaunchedEffect
        scrollState.scrollTo(initialNowScrollOffset(lineOffsetPx, viewportHeightPx, maxScroll))
        completed = true
    }
}

internal fun initialNowScrollOffset(
    lineOffsetPx: Float,
    viewportHeightPx: Int,
    maxScroll: Int,
): Int = (lineOffsetPx - viewportHeightPx / 3f)
    .roundToInt()
    .coerceIn(0, maxScroll)
