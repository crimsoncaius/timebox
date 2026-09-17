package com.timebox.android.ui.day


import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.EmptyStateCard
import com.timebox.android.ui.components.Kicker
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.planning.PlanningDraftPlacement
import com.timebox.android.ui.planning.PlanningEditResult
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch
import com.timebox.android.ui.components.TransientFeedback
import kotlinx.coroutines.delay
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
    onDropPlanningTask: ((PlanningDraftPlacement) -> PlanningEditResult)? = null,
    onUpdatePlanningDraft: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onReturnPlanningDraft: (Int) -> Unit = {},
    onArmAccessibleTask: (Int?) -> Unit,
    onRetryReadyTasks: () -> Unit,
    onNavigateToday: (LocalDate) -> Unit = {},
    onOpenWorkMode: () -> Unit = {},
    onEnterFocus: () -> Unit = {},
    onRecordPlanned: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onUndoRecording: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val preferences = remember(context) { com.timebox.android.data.AppPreferences(context) }
    val visibility by preferences.dayViewPreferences.collectAsState(initial = com.timebox.android.data.DayViewPreferences())
    val preferenceScope = rememberCoroutineScope()
    fun setVisible(section: com.timebox.android.data.DayViewSection, value: Boolean) {
        preferenceScope.launch { preferences.setDaySectionVisible(section, value) }
    }
    val calendarVisible = visibility.calendar
    val trackingVisible = visibility.tracking
    val zoomVisible = visibility.zoom
    var viewOpen by remember { mutableStateOf(false) }
    val activityRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.timebox.android.TimeboxApplication).activityRepository
    val activityState by activityRepository.state.collectAsState()
    val trackingAttention = when {
        activityState.pending -> "Change not confirmed"
        activityState.error != null -> "Activity needs attention"
        activityState.offline -> "Offline"
        activityState.snapshot?.checkIn?.question != null -> "Check-in waiting"
        activityState.rejectedRecovery != null || activityState.legacyRecovery != null -> "Recovery available"
        else -> null
    }
    val zoom = rememberSaveable(saver = TimelineZoom.Saver) { TimelineZoom() }
    if (viewOpen) DayViewOptionsDialog(
        calendar = calendarVisible, tracking = trackingVisible, zoom = zoomVisible,
        zoomScale = zoom.scale, onResetZoom = { zoom.set(1f) },
        onCalendar = { setVisible(com.timebox.android.data.DayViewSection.Calendar, it) }, onTracking = { setVisible(com.timebox.android.data.DayViewSection.Tracking, it) },
        onZoom = { setVisible(com.timebox.android.data.DayViewSection.Zoom, it) }, onDismiss = { viewOpen = false },
    )
    var displayedDate by remember(state.date) { mutableStateOf(state.date) }

    BackHandler(enabled = state.isPlanningMode) {
        if (!state.saving && !state.planning.saving) onCancelPlanningMode()
    }

    CompositionLocalProvider(LocalTimelineZoom provides zoom) {
        Column(Modifier.fillMaxSize()) {
            DayCalendarHeader(
                showCalendar = calendarVisible,
                compactDate = true,
                viewAction = {
                    androidx.compose.material3.TextButton(onClick = { viewOpen = true }) { androidx.compose.material3.Text("View") }
                },
                hiddenTrackingAction = {
                    if (!trackingVisible) androidx.compose.material3.TextButton(modifier = Modifier.semantics { stateDescription = trackingAttention ?: if (activityState.snapshot?.current != null) "Recording" else "Tracking stopped" }, onClick = { setVisible(com.timebox.android.data.DayViewSection.Tracking, true) }) {
                        androidx.compose.material3.Text((if (activityState.snapshot?.current != null) "● Tracking" else "Tracking") + (if (trackingAttention != null) " !" else ""), color = TimeboxTheme.colors.actual)
                    }
                },
                selectedDate = displayedDate,
                today = state.today,
                isPlanningMode = state.isPlanningMode,
                planningActionEnabled = !state.saving && !state.planning.saving && state.workModeRestored && state.workMode == null,
                onOpenWorkMode = onOpenWorkMode,
                onSetPlanningMode = { enabled ->
                    if (enabled) onSetPlanningMode(true) else onCommitPlanningMode()
                },
                onSelectDate = onDateSettled,
                onNavigateToday = onNavigateToday,
            )

            if (com.timebox.android.BuildConfig.ACTIVITY_TRACKING_DEV) {
                ActivityTracking(controlsVisible = trackingVisible, taskTypes = state.taskTypes, onChanged = { onRetry(state.date) }, onEnterFocus = onEnterFocus, planning = state.focusPlanningBlocked)
                if (trackingVisible) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.HorizontalDivider(color = TimeboxTheme.colors.hairline)
                Spacer(Modifier.height(8.dp))
                }
            }
            if (zoomVisible) Row(
                Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.Surface(
                    shape = com.timebox.android.ui.theme.TimeboxShapes.chip,
                    color = TimeboxTheme.colors.low,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TimeboxTheme.colors.hairline),
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Text("Zoom", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Text("${"%.1f".format(zoom.scale)}×", style = TimeboxTheme.type.monoSmall, color = TimeboxTheme.colors.on)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.VerticalDivider(Modifier.height(16.dp), color = TimeboxTheme.colors.hairline)
                        androidx.compose.material3.TextButton(
                            onClick = { zoom.set(1f) },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = TimeboxTheme.colors.planned),
                        ) {
                            androidx.compose.material3.Text("Reset", style = TimeboxTheme.type.bodySmall)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                if (state.isPlanningMode) {
                    PlanningDayPage(
                        state = state,
                        onRetry = onRetry,
                        onSelectBlock = onSelectBlock,
                        onCommitMove = onCommitMove,
                        onPlanTask = onPlanTask,
                        onDropPlanningTask = onDropPlanningTask,
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
            onChangeTimes = onCommitMove,
            onRecordPlanned = onRecordPlanned,
            onOpenActual = onSelectBlock,
            onUndoRecording = onUndoRecording,
        )
    }
    state.recordingPreview?.let { (_, preview) ->
        RecordingPreview(preview, state.day?.timezone ?: "UTC", state.saving, onRecordPlanned, onCancelRecording, state.recordingError)
    }
    if (state.recordingUndo != null && !state.sheetOpen) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            TransientFeedback("Actual recorded", Modifier.padding(16.dp), actionLabel = "Undo", onAction = onUndoRecording, actionsEnabled = !state.saving)
        }
    }
}

@Composable
private fun PlanningDayPage(
    state: DayUiState,
    onRetry: (LocalDate) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
    onPlanTask: (Int, Int) -> Unit,
    onDropPlanningTask: ((PlanningDraftPlacement) -> PlanningEditResult)?,
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
                onDropPlanningTask = onDropPlanningTask,
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
private val SAVED_BLOCK_DRAG_EDGE_ZONE = 48.dp
private const val SAVED_BLOCK_DRAG_SCROLL_STEP_PX = 18f
private const val SAVED_BLOCK_DRAG_SCROLL_FRAME_MILLIS = 16L

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
    // Only the visible page owns this state. Offscreen pages mirror its position
    // using independent states, so their measurements cannot clamp the visible page.
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
                    val previewScroll = rememberScrollState(timelineScroll.value)
                    androidx.compose.runtime.LaunchedEffect(pagePosition, timelineScroll.value, previewScroll.maxValue) {
                        if (pagePosition != 0) previewScroll.scrollTo(timelineScroll.value)
                    }
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
                            scrollState = if (pagePosition == 0) timelineScroll else previewScroll,
                            autoScrollToNow = pagePosition == 0 && date == state.today && !state.skipScrollToNow,
                            scrollToNowRequest = state.scrollToNowRequest,
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
    scrollToNowRequest: Int,
    selectedBlockId: Int?,
    draft: Draft?,
    onRetry: () -> Unit,
    onTapSlot: (Lane, Int) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var viewportHeightPx by remember(date) { mutableIntStateOf(0) }
    var viewportBounds by remember(date) { mutableStateOf(Rect.Zero) }
    var savedBlockDragPointerY by remember(date) { mutableStateOf<Float?>(null) }
    var placementFailure by remember(date) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(placementFailure) {
        if (placementFailure != null) { delay(4_000); placementFailure = null }
    }
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
                    .onGloballyPositioned { viewportBounds = it.boundsInRoot() }
                    .timelinePinch(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = TimeboxDimens.screenPadding)
                    .padding(bottom = TimeboxDimens.bottomInset),
            ) {
                val elapsedDay = com.timebox.android.BuildConfig.ACTIVITY_TRACKING_DEV && day.actualBlocks.any { it.dayLengthMinutes != 1440 }
                Column {
                if (elapsedDay) ReportingDayActuals(day, onSelectBlock)
                DayTimeline(
                    day = if (elapsedDay) day.copy(blocks = day.blocks.filter { it.lane != com.timebox.android.data.Lane.Actual }) else day,
                    selectedBlockId = selectedBlockId,
                    draft = draft,
                    onTapSlot = onTapSlot,
                    onSelectBlock = onSelectBlock,
                    onCommitMove = onCommitMove,
                    onSavedPlannedBlockDragPointer = { savedBlockDragPointerY = it },
                    onPlacementError = { placementFailure = it },
                )
                }
                SavedPlannedBlockDragEdgeScroll(
                    pointerY = savedBlockDragPointerY,
                    viewportBounds = viewportBounds,
                    scrollState = scrollState,
                )
                AutoScrollTimelineToNowOnce(
                    day = day,
                    enabled = autoScrollToNow,
                    scrollState = scrollState,
                    viewportHeightPx = viewportHeightPx,
                    scrollToNowRequest = scrollToNowRequest,
                )
            }
        }
        placementFailure?.let { message ->
            TransientFeedback(message, Modifier.padding(8.dp), isError = true)
        }
    }
}

/** Keeps the ordinary Day timeline moving while a saved Planned Block is held at an edge. */
@Composable
private fun SavedPlannedBlockDragEdgeScroll(
    pointerY: Float?,
    viewportBounds: Rect,
    scrollState: ScrollState,
) {
    val edgeZonePx = with(LocalDensity.current) { SAVED_BLOCK_DRAG_EDGE_ZONE.toPx() }
    val latestPointerY by rememberUpdatedState(pointerY)
    androidx.compose.runtime.LaunchedEffect(pointerY != null, viewportBounds, scrollState.maxValue) {
        while (latestPointerY != null) {
            val pointer = latestPointerY ?: break
            val delta = when {
                pointer < viewportBounds.top + edgeZonePx -> -SAVED_BLOCK_DRAG_SCROLL_STEP_PX
                pointer > viewportBounds.bottom - edgeZonePx -> SAVED_BLOCK_DRAG_SCROLL_STEP_PX
                else -> 0f
            }
            if (delta != 0f) scrollState.scrollBy(delta)
            delay(SAVED_BLOCK_DRAG_SCROLL_FRAME_MILLIS)
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
    scrollToNowRequest: Int = 0,
) {
    var completed by remember(day.date, scrollToNowRequest) { mutableStateOf(false) }
    val slotHeight = timelineSlotHeight()
    val slotHeightPx = with(LocalDensity.current) { slotHeight.toPx() }
    val maxScroll = scrollState.maxValue

    androidx.compose.runtime.LaunchedEffect(
        enabled,
        day.date,
        viewportHeightPx,
        maxScroll,
        scrollToNowRequest,
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
