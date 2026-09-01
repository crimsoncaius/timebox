package com.timebox.android.ui.day

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.SLOT_MINUTES
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.gutterLabel
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.planning.PlanningDraftPlacement
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/** A move or resize in progress, held locally so dragging stays at 60fps. */
private data class DragState(
    val id: Int,
    val startMinute: Int,
    val endMinute: Int,
)

private enum class DragMode { Move, ResizeStart, ResizeEnd }

private fun PointerInputScope.dragModeForPress(pressY: Float): DragMode {
    val grab = minOf(
        maxOf(TimeboxDimens.grooveHeight.toPx(), 14.dp.toPx()),
        size.height / 3f,
    )
    return when {
        pressY < grab -> DragMode.ResizeStart
        pressY > size.height - grab -> DragMode.ResizeEnd
        else -> DragMode.Move
    }
}

enum class PlanningPreviewState { Valid, Invalid, Pending }

data class PlanningDropPreview(
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val state: PlanningPreviewState,
)

@Composable
fun DayTimeline(
    day: Day,
    selectedBlockId: Int?,
    draft: Draft?,
    onTapSlot: (Lane, Int) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
    showActual: Boolean = true,
    planningPreview: PlanningDropPreview? = null,
    onPlannedLaneBoundsChanged: (Rect) -> Unit = {},
    blockGesturesEnabled: Boolean = true,
    planningDrafts: List<PlanningDraftPlacement> = emptyList(),
    planningDraftGesturesEnabled: Boolean = true,
    draggingPlanningTaskId: Int? = null,
    onPlanningDraftDragStart: (PlanningDraftPlacement, Offset, Float) -> Unit = { _, _, _ -> },
    onPlanningDraftDrag: (Offset) -> Unit = {},
    onPlanningDraftDragEnd: (PlanningDraftPlacement, Offset) -> Unit = { _, _ -> },
    onPlanningDraftDragCancel: () -> Unit = {},
    onPlanningDraftResize: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onReturnPlanningDraft: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val slotHeight = TimeboxDimens.slotHeight
    val slots = day.slotCount
    val totalHeight = slotHeight * slots

    var drag by remember { mutableStateOf<DragState?>(null) }

    Box(modifier = modifier.fillMaxWidth().height(totalHeight)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
        ) {
            HourGutter(
                day = day,
                slotHeight = slotHeight,
                modifier = Modifier
                    .width(TimeboxDimens.gutterWidth)
                    .fillMaxSize(),
            )
            LaneColumn(
                day = day,
                lane = Lane.Planned,
                slotHeight = slotHeight,
                selectedBlockId = selectedBlockId,
                draft = draft,
                drag = drag,
                onDragChange = { drag = it },
                onTapSlot = onTapSlot,
                onSelectBlock = onSelectBlock,
                onCommitMove = onCommitMove,
                planningPreview = planningPreview,
                onBoundsChanged = onPlannedLaneBoundsChanged,
                blockGesturesEnabled = blockGesturesEnabled,
                planningDrafts = planningDrafts,
                planningDraftGesturesEnabled = planningDraftGesturesEnabled,
                draggingPlanningTaskId = draggingPlanningTaskId,
                onPlanningDraftDragStart = onPlanningDraftDragStart,
                onPlanningDraftDrag = onPlanningDraftDrag,
                onPlanningDraftDragEnd = onPlanningDraftDragEnd,
                onPlanningDraftDragCancel = onPlanningDraftDragCancel,
                onPlanningDraftResize = onPlanningDraftResize,
                onReturnPlanningDraft = onReturnPlanningDraft,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            if (showActual) {
                LaneColumn(
                    day = day,
                    lane = Lane.Actual,
                    slotHeight = slotHeight,
                    selectedBlockId = selectedBlockId,
                    draft = draft,
                    drag = drag,
                    onDragChange = { drag = it },
                    onTapSlot = onTapSlot,
                    onSelectBlock = onSelectBlock,
                    onCommitMove = onCommitMove,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }

        val nowMinute = rememberNowMinute(day)
        if (day.date == day.today && nowMinute != null &&
            nowMinute >= day.visibleStart && nowMinute < day.visibleEnd
        ) {
            val y = slotHeight * ((nowMinute - day.visibleStart).toFloat() / SLOT_MINUTES)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The lanes sit one gap past the gutter, so the rule has to start there
                    // too or it hangs over the gutter's hairline.
                    .padding(start = TimeboxDimens.gutterWidth + TimeboxDimens.laneGap)
                    .offset(y = y)
                    .height(1.dp)
                    .background(colors.now),
                // The knob is seven times the rule's height, so it is centred on the rule
                // rather than hung off its top corner.
                contentAlignment = Alignment.CenterStart,
            ) {
                // requiredSize, not size: the rule is 1dp tall and hands that down as a
                // maximum, which squashes the knob into a dash floating clear of the line.
                Box(
                    modifier = Modifier
                        .offset(x = (-3).dp)
                        .requiredSize(7.dp)
                        .clip(CircleShape)
                        .background(colors.now),
                )
            }
        }
    }
}

/** A slot is 30 minutes tall, so anything finer than this moves the rule sub-pixel. */
private const val NOW_TICK_MILLIS = 30_000L

/**
 * The now line's minute, ticking locally so the rule creeps down between fetches.
 *
 * `serverNowMinute` is a snapshot taken when the day was loaded; without this the line
 * would sit wherever it was when the screen opened until something reloaded the day.
 */
@Composable
private fun rememberNowMinute(day: Day): Int? {
    var minute by remember(day) { mutableStateOf(day.nowMinuteAt(System.currentTimeMillis())) }
    LaunchedEffect(day) {
        // Re-synced at the top of the loop as well as on first composition, so a fresh day
        // takes effect immediately rather than after the next tick.
        minute = day.nowMinuteAt(System.currentTimeMillis())
        while (true) {
            delay(NOW_TICK_MILLIS)
            minute = day.nowMinuteAt(System.currentTimeMillis())
        }
    }
    return minute
}

@Composable
private fun HourGutter(day: Day, slotHeight: Dp, modifier: Modifier = Modifier) {
    val colors = TimeboxTheme.colors
    // The rule is drawn against the gutter's full width so the hour ticks meet it.
    // Only the labels are inset, which is what keeps them clear of the rule.
    Box(modifier = modifier.drawRightHairline(colors.hairline)) {
        repeat(day.slotCount) { index ->
            val minute = day.visibleStart + index * SLOT_MINUTES
            val onHour = minute % 60 == 0
            Column(
                modifier = Modifier
                    .offset(y = slotHeight * index)
                    .fillMaxWidth()
                    .height(slotHeight),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(if (onHour) colors.gridStrong else colors.gridSoft),
                )
                if (onHour || index == 0) {
                    Text(
                        text = gutterLabel(minute),
                        style = TimeboxTheme.type.gutter,
                        color = colors.timelineLabel,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, end = TimeboxDimens.gutterLabelGap),
                    )
                }
            }
        }
    }
}

/** 1px rule down the right edge of the hour gutter. */
private fun Modifier.drawRightHairline(color: Color): Modifier = drawBehind {
    drawRect(
        color = color,
        topLeft = Offset(size.width - 1f, 0f),
        size = Size(1f, size.height),
    )
}

@Composable
private fun LaneColumn(
    day: Day,
    lane: Lane,
    slotHeight: Dp,
    selectedBlockId: Int?,
    draft: Draft?,
    drag: DragState?,
    onDragChange: (DragState?) -> Unit,
    onTapSlot: (Lane, Int) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
    planningPreview: PlanningDropPreview? = null,
    onBoundsChanged: ((Rect) -> Unit)? = null,
    blockGesturesEnabled: Boolean = true,
    planningDrafts: List<PlanningDraftPlacement> = emptyList(),
    planningDraftGesturesEnabled: Boolean = true,
    draggingPlanningTaskId: Int? = null,
    onPlanningDraftDragStart: (PlanningDraftPlacement, Offset, Float) -> Unit = { _, _, _ -> },
    onPlanningDraftDrag: (Offset) -> Unit = {},
    onPlanningDraftDragEnd: (PlanningDraftPlacement, Offset) -> Unit = { _, _ -> },
    onPlanningDraftDragCancel: () -> Unit = {},
    onPlanningDraftResize: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onReturnPlanningDraft: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val surface = if (lane == Lane.Planned) colors.plannedSurface else colors.actualSurface
    val borderColor = if (lane == Lane.Planned) colors.plannedBorder else colors.actualBorder
    val density = LocalDensity.current
    val slotPx = with(density) { slotHeight.toPx() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(surface)
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .then(
                if (onBoundsChanged != null) {
                    Modifier.onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
                } else {
                    Modifier
                }
            )
            .pointerInput(day.visibleStart, day.visibleEnd, lane) {
                detectTapGestures { offset ->
                    val index = (offset.y / slotPx).toInt()
                    onTapSlot(lane, day.visibleStart + index * SLOT_MINUTES)
                }
            },
    ) {
        // Slot rules, matching the gutter's hour/half-hour weighting.
        repeat(day.slotCount) { index ->
            val minute = day.visibleStart + index * SLOT_MINUTES
            Box(
                modifier = Modifier
                    .offset(y = slotHeight * index)
                    .fillMaxWidth()
                    .height(1.dp)
                    .graphicsLayer { alpha = 0.55f }
                    .background(
                        if (minute % 60 == 0) colors.gridStrong else colors.gridSoft
                    ),
            )
        }

        day.lane(lane).forEach { block ->
            val live = if (drag != null && drag.id == block.id) drag else null
            val start = live?.startMinute ?: block.startMinute
            val end = live?.endMinute ?: block.endMinute
            BlockCard(
                block = block,
                startMinute = start,
                endMinute = end,
                visibleStart = day.visibleStart,
                slotHeight = slotHeight,
                selected = selectedBlockId == block.id,
                dragging = live != null,
                gesturesEnabled = blockGesturesEnabled && lane == Lane.Planned,
                onTap = { onSelectBlock(block.id) },
                // Both callbacks recompute from the block's committed times and the raw
                // gesture delta. Reading the drag state here instead would capture the
                // value from when the gesture started — always null — and never commit.
                onDrag = { mode, deltaPx ->
                    val (start, end) = resolveDrag(mode, deltaPx, slotPx, block, day)
                    onDragChange(DragState(block.id, start, end))
                },
                onDragEnd = { mode, deltaPx ->
                    onDragChange(null)
                    val (start, end) = resolveDrag(mode, deltaPx, slotPx, block, day)
                    onCommitMove(block.id, start, end)
                },
                onDragCancel = { onDragChange(null) },
            )
        }

        if (lane == Lane.Planned && planningPreview != null) {
            PlanningPreviewCard(
                preview = planningPreview,
                visibleStart = day.visibleStart,
                slotHeight = slotHeight,
            )
        }

        if (lane == Lane.Planned) {
            planningDrafts.forEach { placement ->
                PlanningDraftCard(
                    placement = placement,
                    visibleStart = day.visibleStart,
                    visibleEnd = day.visibleEnd,
                    slotHeight = slotHeight,
                    dragging = placement.taskId == draggingPlanningTaskId,
                    gesturesEnabled = planningDraftGesturesEnabled,
                    onDragStart = { pointer, grabOffset ->
                        onPlanningDraftDragStart(placement, pointer, grabOffset)
                    },
                    onDrag = onPlanningDraftDrag,
                    onDragEnd = { pointer -> onPlanningDraftDragEnd(placement, pointer) },
                    onDragCancel = onPlanningDraftDragCancel,
                    onResize = { start, end -> onPlanningDraftResize(placement.taskId, start, end) },
                    onReturn = { onReturnPlanningDraft(placement.taskId) },
                )
            }
        }

        if (draft != null && draft.lane == lane) {
            val top = slotHeight * ((draft.startMinute - day.visibleStart).toFloat() / SLOT_MINUTES)
            val height = slotHeight * ((draft.endMinute - draft.startMinute).toFloat() / SLOT_MINUTES)
            Box(
                modifier = Modifier
                    .offset(y = top)
                    .padding(horizontal = 3.dp)
                    .fillMaxWidth()
                    .height(height)
                    .border(1.5.dp, colors.outline, TimeboxShapes.block)
                    .background(Color(0x14808080), TimeboxShapes.block),
            )
        }
    }
}

@Composable
private fun PlanningPreviewCard(
    preview: PlanningDropPreview,
    visibleStart: Int,
    slotHeight: Dp,
) {
    val colors = TimeboxTheme.colors
    val top = slotHeight * ((preview.startMinute - visibleStart).toFloat() / SLOT_MINUTES)
    val height = slotHeight * ((preview.endMinute - preview.startMinute).toFloat() / SLOT_MINUTES)
    val border = when (preview.state) {
        PlanningPreviewState.Invalid -> colors.error
        PlanningPreviewState.Valid, PlanningPreviewState.Pending -> colors.planned
    }
    val fill = when (preview.state) {
        PlanningPreviewState.Invalid -> colors.error.copy(alpha = 0.12f)
        PlanningPreviewState.Valid -> colors.planned.copy(alpha = 0.16f)
        PlanningPreviewState.Pending -> colors.planned.copy(alpha = 0.24f)
    }
    Box(
        modifier = Modifier
            .offset(y = top)
            .padding(horizontal = 3.dp)
            .fillMaxWidth()
            .height(height)
            .border(1.5.dp, border, TimeboxShapes.block)
            .background(fill, TimeboxShapes.block)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = preview.title,
            style = TimeboxTheme.type.blockTitle,
            color = if (preview.state == PlanningPreviewState.Invalid) colors.error else colors.on,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Snap a raw pixel delta to slot boundaries and apply it to the block's saved times. */
private fun resolveDrag(
    mode: DragMode,
    deltaPx: Float,
    slotPx: Float,
    block: TimeBlock,
    day: Day,
): Pair<Int, Int> = applyDrag(
    mode = mode,
    deltaMinutes = (deltaPx / slotPx).roundToInt() * SLOT_MINUTES,
    originalStart = block.startMinute,
    originalEnd = block.endMinute,
    visibleStart = day.visibleStart,
    visibleEnd = day.visibleEnd,
)

/** Clamp a move/resize to the visible window, mirroring the prototype's rules. */
private fun applyDrag(
    mode: DragMode,
    deltaMinutes: Int,
    originalStart: Int,
    originalEnd: Int,
    visibleStart: Int,
    visibleEnd: Int,
): Pair<Int, Int> {
    var start = originalStart
    var end = originalEnd
    when (mode) {
        DragMode.Move -> {
            start = originalStart + deltaMinutes
            end = originalEnd + deltaMinutes
        }
        DragMode.ResizeStart -> start = minOf(originalStart + deltaMinutes, originalEnd - SLOT_MINUTES)
        DragMode.ResizeEnd -> end = maxOf(originalEnd + deltaMinutes, originalStart + SLOT_MINUTES)
    }
    if (start < visibleStart) {
        val shift = visibleStart - start
        start += shift
        if (mode == DragMode.Move) end += shift
    }
    if (end > visibleEnd) {
        val shift = end - visibleEnd
        end -= shift
        if (mode == DragMode.Move) start -= shift
    }
    return start to end
}

@Composable
private fun PlanningDraftCard(
    placement: PlanningDraftPlacement,
    visibleStart: Int,
    visibleEnd: Int,
    slotHeight: Dp,
    dragging: Boolean,
    gesturesEnabled: Boolean,
    onDragStart: (Offset, Float) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    onDragCancel: () -> Unit,
    onResize: (Int, Int) -> Unit,
    onReturn: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val haptics = LocalHapticFeedback.current
    var cardRoot by remember(placement.taskId) { mutableStateOf(Offset.Zero) }
    var resizePreview by remember(placement.taskId) { mutableStateOf<Pair<Int, Int>?>(null) }
    val startMinute = resizePreview?.first ?: placement.startMinute
    val endMinute = resizePreview?.second ?: placement.endMinute
    val top = slotHeight * ((startMinute - visibleStart).toFloat() / SLOT_MINUTES)
    val slotsTall = (endMinute - startMinute).toFloat() / SLOT_MINUTES
    val height = max(slotHeight.value * slotsTall, slotHeight.value).dp
    val innerHeight = height - TimeboxDimens.grooveHeight * 2
    val animatedTop by animateDpAsState(
        targetValue = top,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
        label = "planning draft position",
    )
    val animatedHeight by animateDpAsState(
        targetValue = height,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
        label = "planning draft height",
    )
    val sourceAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.10f else 1f,
        label = "planning draft source",
    )

    Box(
        modifier = Modifier
            .offset(y = if (resizePreview != null) top else animatedTop)
            .padding(horizontal = 3.dp)
            .fillMaxWidth()
            .height(if (resizePreview != null) height else animatedHeight)
            .onGloballyPositioned { cardRoot = it.positionInRoot() }
            .graphicsLayer {
                alpha = sourceAlpha
                scaleX = if (dragging) 0.98f else 1f
                scaleY = if (dragging) 0.98f else 1f
            }
            .shadow(if (dragging) 12.dp else 3.dp, TimeboxShapes.block, clip = false)
            .clip(TimeboxShapes.block)
            .background(colors.planned.copy(alpha = if (colors.isDark) 0.30f else 0.13f))
            .border(1.5.dp, colors.planned, TimeboxShapes.block)
            .semantics {
                contentDescription = "Planning draft ${placement.taskTitle}"
                customActions = if (gesturesEnabled) listOf(
                    CustomAccessibilityAction("Move 30 minutes earlier") {
                        onResize(placement.startMinute - SLOT_MINUTES, placement.endMinute - SLOT_MINUTES)
                        true
                    },
                    CustomAccessibilityAction("Move 30 minutes later") {
                        onResize(placement.startMinute + SLOT_MINUTES, placement.endMinute + SLOT_MINUTES)
                        true
                    },
                    CustomAccessibilityAction("Return to Tasks to Plan") {
                        onReturn()
                        true
                    },
                ) else emptyList()
            }
            .pointerInput(
                placement.taskId,
                placement.startMinute,
                placement.endMinute,
                visibleStart,
                visibleEnd,
                gesturesEnabled,
            ) {
                if (!gesturesEnabled) return@pointerInput
                val slotPx = slotHeight.toPx()
                var mode = DragMode.Move
                var total = Offset.Zero
                var pointerRoot = Offset.Zero
                detectLongPressArmedDragGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragStart = { down ->
                        mode = dragModeForPress(down.y)
                        total = Offset.Zero
                        pointerRoot = cardRoot + down
                        if (mode == DragMode.Move) onDragStart(pointerRoot, down.y)
                    },
                    onDrag = { _, moved ->
                        total += moved
                        pointerRoot += moved
                        if (mode == DragMode.Move) {
                            onDrag(pointerRoot)
                        } else {
                            val slotDelta = (total.y / slotPx).roundToInt() * SLOT_MINUTES
                            resizePreview = when (mode) {
                                DragMode.ResizeStart -> Pair(
                                    (placement.startMinute + slotDelta)
                                        .coerceIn(visibleStart, placement.endMinute - SLOT_MINUTES),
                                    placement.endMinute,
                                )
                                DragMode.ResizeEnd -> Pair(
                                    placement.startMinute,
                                    (placement.endMinute + slotDelta)
                                        .coerceIn(placement.startMinute + SLOT_MINUTES, visibleEnd),
                                )
                                DragMode.Move -> null
                            }
                        }
                    },
                    onDragEnd = {
                        if (mode == DragMode.Move) {
                            onDragEnd(pointerRoot)
                        } else {
                            resizePreview?.let { onResize(it.first, it.second) }
                            resizePreview = null
                        }
                    },
                    onDragCancel = {
                        if (mode == DragMode.Move) onDragCancel()
                        resizePreview = null
                    },
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Groove()
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    placement.taskTitle,
                    style = TimeboxTheme.type.blockTitleSelected,
                    color = colors.on,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (innerHeight >= 30.dp) {
                    Text(
                        "${hhmm(startMinute)} – ${hhmm(endMinute)}",
                        style = TimeboxTheme.type.monoSmall,
                        color = colors.planned,
                        maxLines = 1,
                    )
                }
            }
            Groove()
        }
    }
}

@Composable
private fun BlockCard(
    block: TimeBlock,
    startMinute: Int,
    endMinute: Int,
    visibleStart: Int,
    slotHeight: Dp,
    selected: Boolean,
    dragging: Boolean,
    gesturesEnabled: Boolean,
    onTap: () -> Unit,
    onDrag: (DragMode, Float) -> Unit,
    onDragEnd: (DragMode, Float) -> Unit,
    onDragCancel: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val haptics = LocalHapticFeedback.current
    val top = slotHeight * ((startMinute - visibleStart).toFloat() / SLOT_MINUTES)
    val slotsTall = (endMinute - startMinute).toFloat() / SLOT_MINUTES
    val height = max(slotHeight.value * slotsTall, slotHeight.value).dp
    val innerHeight = height - TimeboxDimens.grooveHeight * 2
    val elevation = when {
        dragging -> 16.dp
        selected -> 8.dp
        else -> 0.dp
    }

    Box(
        modifier = Modifier
            .offset(y = top)
            .padding(horizontal = 3.dp)
            .fillMaxWidth()
            .height(height)
            .testTag("day-block-${block.id}")
            .graphicsLayer { if (dragging) rotationZ = -1f }
            .shadow(elevation, TimeboxShapes.block, clip = false)
            .clip(TimeboxShapes.block)
            .background(
                when {
                    dragging || selected -> colors.paperRaised
                    else -> colors.paper
                }
            )
            // Tap, move and both resizes start with a press on the same card, so one
            // handler owns all of them. Where the armed press began decides what the
            // manipulation means; movement before arming remains available to the
            // surrounding timeline scroll and day pager.
            .pointerInput(block.id, block.startMinute, block.endMinute, gesturesEnabled) {
                if (!gesturesEnabled) {
                    detectTapGestures { onTap() }
                    return@pointerInput
                }
                var mode = DragMode.Move
                var total = 0f
                detectLongPressArmedDragGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onTap = { onTap() },
                    onDragStart = { down ->
                        // The drawn groove is only 8dp, so the grab zone is given a
                        // little more reach than the paint — but never more than a third
                        // of the card, or a one-slot block would have no move surface.
                        mode = dragModeForPress(down.y)
                        total = 0f
                    },
                    onDrag = { _, moved ->
                        total += moved.y
                        onDrag(mode, total)
                    },
                    onDragEnd = { onDragEnd(mode, total) },
                    onDragCancel = onDragCancel,
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Groove()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = buildString {
                        append(block.task?.title ?: block.taskTypeName)
                        block.task?.let { append(if (it.status == com.timebox.android.data.TaskStatus.Completed) " · Task ✓" else " · Task ○") }
                    },
                    style = if (selected) {
                        TimeboxTheme.type.blockTitleSelected
                    } else {
                        TimeboxTheme.type.blockTitle
                    },
                    color = colors.on,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (block.task != null && innerHeight >= 30.dp) {
                    Text(
                        text = block.taskTypeName,
                        style = TimeboxTheme.type.monoSmall,
                        color = colors.onVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (innerHeight >= 30.dp) {
                    Text(
                        text = "${hhmm(startMinute)} – ${hhmm(endMinute)}",
                        style = TimeboxTheme.type.monoSmall,
                        color = colors.onVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val note = block.note
                if (!note.isNullOrBlank() && innerHeight >= 48.dp) {
                    Text(
                        text = note,
                        style = TimeboxTheme.type.monoSmall,
                        color = colors.outlineVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Groove()
        }
    }
}

/**
 * The ruled resize grip at the top and bottom of every block. Purely paint: the
 * gesture that goes with it lives in the card's single pointer handler.
 */
@Composable
private fun Groove() {
    val colors = TimeboxTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimeboxDimens.grooveHeight)
            .background(colors.groove),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
            repeat(2) {
                Box(
                    Modifier
                        .width(30.dp)
                        .height(1.dp)
                        .background(colors.rule),
                )
            }
        }
    }
}
