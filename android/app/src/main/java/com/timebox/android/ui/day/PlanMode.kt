package com.timebox.android.ui.day

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.MIN_PLANNED_BLOCK_MINUTES
import com.timebox.android.data.SLOT_MINUTES
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.planning.PlanningDraftPlacement
import com.timebox.android.ui.planning.PlanningEditResult
import com.timebox.android.ui.planning.planningRangeAvailable
import com.timebox.android.ui.readiness.ReadyToPlanFailureNotice
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.components.Kicker
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val AUTO_SCROLL_FRAME_MILLIS = 16L

private data class TaskDragState(
    val task: BattleTask,
    val pointerRoot: Offset,
    val draft: PlanningDraftPlacement? = null,
    val grabOffsetPx: Float = 0f,
)

@Composable
internal fun PlanningModeHeaders(showTaskRail: Boolean, modifier: Modifier = Modifier) {
    val colors = TimeboxTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val contentWidth = maxWidth - TimeboxDimens.screenPadding * 2
        val afterGutter = contentWidth - TimeboxDimens.gutterWidth - TimeboxDimens.laneGap * 2
        val railWidth = (afterGutter * 0.38f).coerceIn(112.dp, 152.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap)) {
                Box(Modifier.width(TimeboxDimens.gutterWidth))
                Kicker("Planned", colors.planned, Modifier.weight(1f))
            }
            if (showTaskRail) {
                Kicker("Tasks to plan", colors.planned, Modifier.width(railWidth))
            }
        }
    }
}

@Composable
internal fun PlanningWorkspace(
    state: DayUiState,
    day: Day,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
    onPlanTask: (Int, Int) -> Unit,
    onDropPlanningTask: ((PlanningDraftPlacement) -> PlanningEditResult)? = null,
    onUpdatePlanningDraft: (Int, Int, Int) -> Unit,
    onReturnPlanningDraft: (Int) -> Unit,
    onArmAccessibleTask: (Int?) -> Unit,
    onRetryReadyTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val density = LocalDensity.current
    val slotPx = with(density) { TimeboxDimens.slotHeight.toPx() }
    val edgeZonePx = with(density) { 48.dp.toPx() }
    val scrollStepPx = with(density) { 12.dp.toPx() }
    val timelineScroll = rememberScrollState()
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var laneBounds by remember { mutableStateOf(Rect.Zero) }
    var viewportBounds by remember { mutableStateOf(Rect.Zero) }
    var railBounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<TaskDragState?>(null) }
    var dropFailure by remember(day.date) { mutableStateOf<String?>(null) }
    var returningTaskId by remember { mutableStateOf<Int?>(null) }
    val planningDrafts = state.planningDrafts(day.date)
    val showTaskRail = state.hasPlanningRailContent(day.date)
    val dragDuration = drag?.draft?.let { it.endMinute - it.startMinute }
        ?: MIN_PLANNED_BLOCK_MINUTES

    val candidateStart = drag?.let {
        planningDropStart(
            pointerRoot = it.pointerRoot,
            laneBounds = laneBounds,
            viewportBounds = viewportBounds,
            visibleStart = day.visibleStart,
            visibleEnd = day.visibleEnd,
            slotPx = slotPx,
            durationMinutes = dragDuration,
            grabOffsetPx = it.grabOffsetPx,
            originalStartMinute = it.draft?.startMinute,
        )
    }
    val resolvedStart = candidateStart?.let { start ->
        nearestPlanningDragStart(day, planningDrafts, drag?.draft?.taskId, start, dragDuration)
    }
    val noSpace = candidateStart != null && resolvedStart == null
    val returningDraft = drag?.draft != null && railBounds.contains(drag?.pointerRoot ?: Offset.Zero)
    LaunchedEffect(returningTaskId) {
        if (returningTaskId != null) {
            delay(360)
            returningTaskId = null
        }
    }
    val activePreview = drag?.let { active ->
        resolvedStart?.let { start ->
            PlanningDropPreview(
                title = active.task.title,
                startMinute = start,
                endMinute = start + dragDuration,
                state = PlanningPreviewState.Valid,
            )
        }
    }

    LaunchedEffect(drag != null, viewportBounds) {
        while (drag != null) {
            val pointer = drag?.pointerRoot ?: break
            val delta = when {
                pointer.x !in viewportBounds.left..viewportBounds.right -> 0f
                pointer.y < viewportBounds.top + edgeZonePx -> -scrollStepPx
                pointer.y > viewportBounds.bottom - edgeZonePx -> scrollStepPx
                else -> 0f
            }
            if (delta != 0f) timelineScroll.scrollBy(delta)
            delay(AUTO_SCROLL_FRAME_MILLIS)
        }
    }

    LaunchedEffect(dropFailure) {
        if (dropFailure != null) {
            delay(4_000)
            dropFailure = null
        }
    }
    // Draft pointer handlers outlive recompositions. Both drag sources must release
    // against the latest displayed destination, never recompute a new snap on drop.
    val finishDrag by rememberUpdatedState<(Offset) -> Unit>(finish@{ pointer ->
        val active = drag ?: return@finish
        val returnToRail = active.draft != null && railBounds.contains(pointer)
        val insideTimeline = planningDropStart(
            pointer, laneBounds, viewportBounds, day.visibleStart, day.visibleEnd,
            slotPx, dragDuration, active.grabOffsetPx, active.draft?.startMinute,
        ) != null
        drag = null
        when {
            returnToRail -> {
                returningTaskId = active.task.id
                onReturnPlanningDraft(active.task.id)
            }
            !insideTimeline -> Unit
            resolvedStart == null -> {
                if (noSpace) dropFailure = "No available space in this time range"
            }
            onDropPlanningTask != null -> {
                val placement = PlanningDraftPlacement(day.date, active.task, resolvedStart, resolvedStart + dragDuration)
                val result = onDropPlanningTask(placement)
                if (result is PlanningEditResult.Rejected) dropFailure = result.reason
            }
            active.draft != null -> onUpdatePlanningDraft(
                active.task.id, resolvedStart, resolvedStart + dragDuration,
            )
            else -> onPlanTask(active.task.id, resolvedStart)
        }
    })

    AutoScrollTimelineToNowOnce(
        day = day,
        enabled = day.date == state.today,
        scrollState = timelineScroll,
        viewportHeightPx = viewportBounds.height.roundToInt(),
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootPosition = it.positionInRoot() },
    ) {
        val contentWidth = maxWidth - TimeboxDimens.screenPadding * 2
        val afterGutter = contentWidth - TimeboxDimens.gutterWidth - TimeboxDimens.laneGap * 2
        val railWidth = (afterGutter * 0.38f).coerceIn(112.dp, 152.dp)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TimeboxDimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onGloballyPositioned { viewportBounds = it.boundsInRoot() }
                    .verticalScroll(timelineScroll)
                    .padding(bottom = TimeboxDimens.bottomInset),
            ) {
                DayTimeline(
                    day = day,
                    selectedBlockId = state.selectedBlockId,
                    draft = null,
                    onTapSlot = { lane, minute ->
                        val accessibleTask = state.accessibilityPlanningTaskId
                        if (lane == Lane.Planned && accessibleTask != null) {
                            onPlanTask(accessibleTask, minute)
                        }
                    },
                    onSelectBlock = onSelectBlock,
                    onCommitMove = onCommitMove,
                    showActual = false,
                    planningPreview = activePreview,
                    onPlannedLaneBoundsChanged = { laneBounds = it },
                    blockGesturesEnabled = false,
                    planningDrafts = planningDrafts,
                    planningDraftGesturesEnabled = !state.saving && !state.planning.saving,
                    draggingPlanningTaskId = drag?.draft?.taskId,
                    onPlanningDraftDragStart = { draft, pointer, grabOffset ->
                        dropFailure = null
                        drag = TaskDragState(draft.task, pointer, draft, grabOffset)
                    },
                    onPlanningDraftDrag = { pointer -> drag = drag?.copy(pointerRoot = pointer) },
                    onPlanningDraftDragEnd = { _, pointer -> finishDrag(pointer) },
                    onPlanningDraftDragCancel = { drag = null },
                    onPlanningDraftResize = onUpdatePlanningDraft,
                    onReturnPlanningDraft = onReturnPlanningDraft,
                )
            }

            if (showTaskRail) PlanningTaskRail(
                state = state,
                enabled = !state.saving && !state.planning.saving,
                draggingTaskId = drag?.takeIf { it.draft == null }?.task?.id,
                returnDropActive = returningDraft,
                appearingTaskId = returningTaskId,
                onBoundsChanged = { railBounds = it },
                onRetry = onRetryReadyTasks,
                onArmAccessibleTask = onArmAccessibleTask,
                onDragStart = { task, pointer ->
                    dropFailure = null
                    drag = TaskDragState(task, pointer, grabOffsetPx = slotPx * MIN_PLANNED_BLOCK_MINUTES / SLOT_MINUTES / 2f)
                },
                onDrag = { pointer -> drag = drag?.copy(pointerRoot = pointer) },
                onDragEnd = { _, pointer -> finishDrag(pointer) },
                onDragCancel = { drag = null },
                modifier = Modifier.width(railWidth).fillMaxHeight(),
            )
        }

        drag?.let { active ->
            val blockWidth = with(density) { laneBounds.width.toDp() } - 6.dp
            val blockHeight = TimeboxDimens.slotHeight * (dragDuration.toFloat() / SLOT_MINUTES)
            DragGhost(
                task = active.task,
                width = blockWidth.coerceAtLeast(1.dp),
                height = blockHeight,
                invalid = noSpace,
                timeRange = resolvedStart?.let { "${hhmm(it)}–${hhmm(it + dragDuration)}" },
                modifier = Modifier.offset {
                    val local = active.pointerRoot - rootPosition
                    IntOffset(
                        (local.x - with(density) { blockWidth.toPx() } / 2f).roundToInt(),
                        (local.y - active.grabOffsetPx).roundToInt(),
                    )
                },
            )
        }
        val message = if (noSpace) "No available space in this time range" else dropFailure
        if (message != null) {
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                Text(message)
            }
        }
    }
}

internal fun DayUiState.hasPlanningRailContent(date: java.time.LocalDate): Boolean {
    return planning.hasRailContent(date)
}

@Composable
private fun PlanningTaskRail(
    state: DayUiState,
    enabled: Boolean,
    draggingTaskId: Int?,
    returnDropActive: Boolean,
    appearingTaskId: Int?,
    onBoundsChanged: (Rect) -> Unit,
    onRetry: () -> Unit,
    onArmAccessibleTask: (Int?) -> Unit,
    onDragStart: (BattleTask, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (BattleTask, Offset) -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val visibleTasks = state.readyTasks.filterNot { it.id in state.planningDrafts }
    Column(
        modifier = modifier
            .testTag("planning-task-rail")
            .clip(RoundedCornerShape(3.dp))
            .background(if (returnDropActive) colors.plannedSurface else colors.low)
            .border(
                if (returnDropActive) 2.dp else 1.dp,
                if (returnDropActive) colors.planned else colors.hairline,
                RoundedCornerShape(3.dp),
            )
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) },
    ) {
        state.accessibilityPlanningTask?.let { task ->
            Column(
                Modifier.fillMaxWidth().background(colors.plannedSurface).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("${task.title} selected", style = TimeboxTheme.type.label, color = colors.on, maxLines = 1)
                Text("Tap an open time", style = TimeboxTheme.type.bodySmall, color = colors.planned)
                TextButton(onClick = { onArmAccessibleTask(null) }) { Text("Cancel") }
            }
        }
        when {
            state.readyTasksLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = colors.planned, strokeWidth = 2.dp)
            }
            state.readyTasksError != null -> Column(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Tasks unavailable", style = TimeboxTheme.type.label, color = colors.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            visibleTasks.isEmpty() -> Text(
                "Nothing waiting to be planned.",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                modifier = Modifier.padding(10.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(7.dp),
            ) {
                items(visibleTasks, key = BattleTask::id) { task ->
                    CollapsiblePlanningTaskCard(
                        task = task,
                        enabled = enabled && !task.readinessPending,
                        armed = task.id == state.accessibilityPlanningTaskId,
                        dragging = task.id == draggingTaskId,
                        appearing = task.id == appearingTaskId,
                        onArmAccessible = { onArmAccessibleTask(task.id) },
                        onDragStart = { onDragStart(task, it) },
                        onDrag = onDrag,
                        onDragEnd = { pointer -> onDragEnd(task, pointer) },
                        onDragCancel = onDragCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsiblePlanningTaskCard(
    task: BattleTask,
    enabled: Boolean,
    armed: Boolean,
    dragging: Boolean,
    appearing: Boolean,
    onArmAccessible: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    onDragCancel: () -> Unit,
) {
    val density = LocalDensity.current
    var expandedHeightPx by remember(task.id) { mutableIntStateOf(0) }
    var waitingToAppear by remember(task.id) { mutableStateOf(appearing) }
    LaunchedEffect(appearing) {
        if (appearing) {
            waitingToAppear = true
            delay(16)
            waitingToAppear = false
        }
    }
    val targetHeight = with(density) {
        if ((dragging || waitingToAppear) && expandedHeightPx > 0) 0.dp else expandedHeightPx.toDp()
    }
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "planning task gap",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (dragging || waitingToAppear) 0f else 1f,
        animationSpec = tween(durationMillis = if (dragging) 120 else 180),
        label = "planning task visibility",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expandedHeightPx > 0) Modifier.height(animatedHeight)
                else Modifier,
            )
            .clip(RoundedCornerShape(1.dp))
            .onSizeChanged { size ->
                if (!dragging && expandedHeightPx == 0) expandedHeightPx = size.height
            },
    ) {
        PlanningTaskCard(
            task = task,
            enabled = enabled,
            armed = armed,
            onArmAccessible = onArmAccessible,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 7.dp)
                .graphicsLayer {
                    alpha = cardAlpha
                    scaleX = 0.98f + (0.02f * cardAlpha)
                    scaleY = 0.98f + (0.02f * cardAlpha)
                },
        )
    }
}

@Composable
private fun PlanningTaskCard(
    task: BattleTask,
    enabled: Boolean,
    armed: Boolean,
    onArmAccessible: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val haptics = LocalHapticFeedback.current
    var cardRoot by remember(task.id) { mutableStateOf(Offset.Zero) }
    var pointerRoot by remember(task.id) { mutableStateOf(Offset.Zero) }
    val currentOnArmAccessible by rememberUpdatedState(onArmAccessible)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Column(
        modifier = modifier
            .clip(TimeboxShapes.card)
            .background(if (armed) colors.plannedSurface else colors.lowest)
            .border(1.dp, if (armed) colors.plannedBorder else colors.hairline, TimeboxShapes.card)
            .semantics {
                contentDescription = if (task.readinessPending) {
                    "${task.title} is saving and unavailable"
                } else {
                    "Schedule ${task.title}"
                }
                if (enabled) {
                    onClick {
                        currentOnArmAccessible()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .onGloballyPositioned { cardRoot = it.positionInRoot() }
            .pointerInput(task.id, enabled) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        pointerRoot = cardRoot + offset
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnDragStart(pointerRoot)
                    },
                    onDrag = { _, amount ->
                        pointerRoot += amount
                        currentOnDrag(pointerRoot)
                    },
                    onDragEnd = { currentOnDragEnd(pointerRoot) },
                    onDragCancel = currentOnDragCancel,
                )
            }
            .padding(start = 8.dp, top = 8.dp, bottom = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = task.parentTitle?.let { "$it · ${task.title}" } ?: task.title,
            style = TimeboxTheme.type.label,
            color = colors.on,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 7.dp),
        )
        if (task.readinessPending) {
            Text(
                "Saving",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
            )
        }
        ReadyToPlanFailureNotice(task, Modifier.padding(end = 7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = task.taskType?.leaf ?: "Unspecified",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DragGhost(
    task: BattleTask,
    width: Dp,
    height: Dp,
    invalid: Boolean,
    timeRange: String?,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    var lifted by remember(task.id) { mutableStateOf(false) }
    val liftProgress by animateFloatAsState(
        targetValue = if (lifted) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "planning task lift",
    )
    LaunchedEffect(task.id) { lifted = true }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                shadowElevation = 14f * liftProgress
                shape = TimeboxShapes.block
            }
            .clip(TimeboxShapes.block)
            .background(colors.paperRaised)
            .background(colors.planned.copy(alpha = if (colors.isDark) 0.25f else 0.13f))
            .border(2.dp, if (invalid) colors.error else colors.planned, TimeboxShapes.block)
            .testTag("planning-drag-block")
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                task.title,
                style = TimeboxTheme.type.blockTitle,
                color = colors.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (timeRange != null) {
                Text(timeRange, style = TimeboxTheme.type.monoSmall, color = colors.on, maxLines = 1)
            }
        }
    }
}

internal fun planningDropStart(
    pointerRoot: Offset,
    laneBounds: Rect,
    viewportBounds: Rect,
    visibleStart: Int,
    visibleEnd: Int,
    slotPx: Float,
    durationMinutes: Int = MIN_PLANNED_BLOCK_MINUTES,
    grabOffsetPx: Float = 0f,
    originalStartMinute: Int? = null,
): Int? {
    if (slotPx <= 0f || laneBounds == Rect.Zero || viewportBounds == Rect.Zero) return null
    if (pointerRoot.x < laneBounds.left || pointerRoot.x > laneBounds.right) return null
    if (pointerRoot.y < viewportBounds.top || pointerRoot.y > viewportBounds.bottom) return null
    val rawStart = visibleStart +
        (pointerRoot.y - grabOffsetPx - laneBounds.top) / slotPx * SLOT_MINUTES
    val start = if (originalStartMinute == null) {
        snapToBlockInteractionStep(rawStart)
    } else {
        originalStartMinute + snapToBlockInteractionStep(rawStart - originalStartMinute)
    }

    // A too-long draft still has an in-lane candidate so the UI can explain no space.
    return start.coerceIn(visibleStart, maxOf(visibleStart, visibleEnd - durationMinutes))
}

internal fun isPlanningDropAvailable(
    day: Day,
    startMinute: Int,
    endMinute: Int = startMinute + MIN_PLANNED_BLOCK_MINUTES,
    drafts: List<PlanningDraftPlacement> = emptyList(),
    excludeTaskId: Int? = null,
): Boolean {
    return planningRangeAvailable(day, drafts, excludeTaskId, startMinute, endMinute)
}
