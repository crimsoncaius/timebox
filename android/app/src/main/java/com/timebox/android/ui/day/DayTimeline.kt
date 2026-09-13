package com.timebox.android.ui.day

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.findRootCoordinates
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
import com.timebox.android.data.MIN_ACTUAL_BLOCK_MINUTES
import com.timebox.android.data.MIN_PLANNED_BLOCK_MINUTES
import com.timebox.android.data.SLOT_MINUTES
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.primaryIdentity
import com.timebox.android.data.secondaryIdentity
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
    val previewStartMinute: Int = startMinute,
    val previewEndMinute: Int = endMinute,
    val valid: Boolean = true,
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
    onSavedPlannedBlockDragPointer: (Float?) -> Unit = {},
    showActual: Boolean = true,
    planningPreview: PlanningDropPreview? = null,
    placementSelected: Boolean = false,
    onPlacementError: (String) -> Unit = {},
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
    val slotHeight = timelineSlotHeight()
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
                onSavedPlannedBlockDragPointer = onSavedPlannedBlockDragPointer,
                planningPreview = planningPreview,
                placementSelected = placementSelected,
                onPlacementError = onPlacementError,
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
                    onPlacementError = onPlacementError,
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
                    .background(colors.now)
                    .testTag("day-now-line"),
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
    onSavedPlannedBlockDragPointer: (Float?) -> Unit = {},
    planningPreview: PlanningDropPreview? = null,
    placementSelected: Boolean = false,
    onPlacementError: (String) -> Unit = {},
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
            .testTag("day-lane-${lane.name.lowercase()}")
            .then(
                if (onBoundsChanged != null) {
                    Modifier.onGloballyPositioned {
                        // Drop times need the full lane origin, including the portion above
                        // the scroll viewport. Clipped bounds would reset it to the viewport top.
                        onBoundsChanged(it.findRootCoordinates().localBoundingBoxOf(it, clipBounds = false))
                    }
                } else {
                    Modifier
                }
            )
            .pointerInput(day.visibleStart, day.visibleEnd, lane, slotHeight) {
                detectTapGestures { offset ->
                    val rawMinute = day.visibleStart + offset.y / slotPx * SLOT_MINUTES
                    val minute = snapToBlockInteractionStep(rawMinute)
                        .coerceIn(day.visibleStart, day.visibleEnd - SLOT_MINUTES)
                    onTapSlot(lane, minute)
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

        val nowMinute = rememberNowMinute(day)
        day.lane(lane).forEach { block ->
            val timeEditable = blockGesturesEnabled && !(block.lane == Lane.Actual &&
                day.actualBlocks.any { it.actualBlock.id == block.actualBlockId && it.actualBlock.endAt == null })
            val live = if (drag != null && drag.id == block.id) drag else null
            val start = live?.startMinute ?: block.startMinute
            val running = day.date == day.today && day.actualBlocks.any {
                it.actualBlock.id == block.actualBlockId && it.actualBlock.endAt == null
            }
            val end = if (running) (nowMinute ?: block.endMinute).coerceIn(start, maxOf(start, day.visibleEnd))
                else live?.endMinute ?: block.endMinute
            val previewStart = live?.previewStartMinute ?: start
            val previewEnd = live?.previewEndMinute ?: end
            BlockCard(
                block = block,
                startMinute = start,
                endMinute = end,
                previewStartMinute = previewStart,
                previewEndMinute = previewEnd,
                visibleStart = day.visibleStart,
                slotHeight = slotHeight,
                selected = selectedBlockId == block.id,
                dragging = live != null,
                invalid = live?.valid == false,
                moveEnabled = timeEditable,
                resizeEnabled = timeEditable,
                onTap = { onSelectBlock(block.id) },
                // Both callbacks recompute from the block's committed times and the raw
                // gesture delta. Reading the drag state here instead would capture the
                // value from when the gesture started — always null — and never commit.
                onDrag = { mode, deltaPx ->
                    resolveSavedBlockDrag(mode, deltaPx, slotPx, block, day).also(onDragChange)
                },
                onDragEnd = { resolved ->
                    onDragChange(null)
                    resolved?.let {
                        if (resolved.valid) onCommitMove(block.id, resolved.previewStartMinute, resolved.previewEndMinute)
                        else onPlacementError(NO_NEARBY_BLOCK_SPACE)
                    }
                },
                onDragCancel = { onDragChange(null) },
                onAccessibleMove = if (timeEditable) {
                    { deltaMinutes ->
                        val duration = block.endMinute - block.startMinute
                        nearestSavedBlockDragStart(
                            day = day,
                            movingBlockId = block.id,
                            intendedStart = block.startMinute + deltaMinutes,
                            durationMinutes = duration,
                            lane = block.lane,
                        )?.let { start -> onCommitMove(block.id, start, start + duration) }
                            ?: run { onPlacementError(NO_NEARBY_BLOCK_SPACE) }
                    }
                } else {
                    null
                },
                onDragPointer = if (block.lane == Lane.Planned) onSavedPlannedBlockDragPointer else null,
            )
        }

        if (drag != null && drag.valid && day.lane(lane).any { it.id == drag.id }) {
            SavedBlockMovePreview(
                lane = lane,
                startMinute = drag.previewStartMinute,
                endMinute = drag.previewEndMinute,
                visibleStart = day.visibleStart,
                slotHeight = slotHeight,
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
                    resizeBounds = blockResizeBounds(day, placement.startMinute, placement.endMinute,
                        drafts = planningDrafts, movingTaskId = placement.taskId),
                    onAccessibleMove = { delta ->
                        val duration = placement.endMinute - placement.startMinute
                        val start = nearestPlanningDragStart(day, planningDrafts, placement.taskId,
                            placement.startMinute + delta, duration)
                        if (start == null) onPlacementError(NO_NEARBY_BLOCK_SPACE)
                        else onPlanningDraftResize(placement.taskId, start, start + duration)
                    },
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
        if (lane == Lane.Planned && placementSelected) {
            Box(Modifier.matchParentSize().testTag("planned-placement-target")
                .pointerInput(day.visibleStart, day.visibleEnd, onTapSlot) {
                    detectTapGestures { offset ->
                        val minute = snapToBlockInteractionStep(day.visibleStart + offset.y / slotPx * SLOT_MINUTES)
                        onTapSlot(Lane.Planned, minute)
                    }
                })
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
    Box(
        modifier = Modifier
            .offset(y = top)
            .padding(horizontal = 3.dp)
            .fillMaxWidth()
            .height(height)
            .border(1.dp, border.copy(alpha = 0.65f), TimeboxShapes.block)
            .testTag("planning-drop-outline"),
    )
}

/** Snap a raw pixel delta to five-minute steps and apply it to the block's saved times. */
private fun resolveDrag(
    mode: DragMode,
    deltaPx: Float,
    slotPx: Float,
    block: TimeBlock,
    day: Day,
): Pair<Int, Int> = applyDrag(
    mode = mode,
    deltaMinutes = snapBlockDeltaMinutes(deltaPx, slotPx),
    originalStart = block.startMinute,
    originalEnd = block.endMinute,
    visibleStart = day.visibleStart,
    visibleEnd = day.visibleEnd,
    minimumDuration = if (block.lane == Lane.Planned) {
        MIN_PLANNED_BLOCK_MINUTES
    } else {
        MIN_ACTUAL_BLOCK_MINUTES
    },
)

/** Keeps the carried card under the finger while committing the same nearest valid preview. */
private fun resolveSavedBlockDrag(
    mode: DragMode,
    deltaPx: Float,
    slotPx: Float,
    block: TimeBlock,
    day: Day,
): DragState? {
    var (start, end) = resolveDrag(mode, deltaPx, slotPx, block, day)
    if (mode != DragMode.Move) {
        val (lower, upper) = blockResizeBounds(day, block.startMinute, block.endMinute, movingBlockId = block.id, lane = block.lane)
        if (mode == DragMode.ResizeStart) start = maxOf(start, lower)
        else end = minOf(end, upper)
    }
    val previewStart = if (mode == DragMode.Move) {
        nearestSavedBlockDragStart(day, block.id,
            block.startMinute + snapBlockDeltaMinutes(deltaPx, slotPx), block.endMinute - block.startMinute, lane = block.lane)
    } else {
        start
    }
    return DragState(
        id = block.id,
        startMinute = start,
        endMinute = end,
        previewStartMinute = previewStart ?: start,
        previewEndMinute = (previewStart ?: start) + (end - start),
        valid = previewStart != null,
    )
}

private fun snapBlockDeltaMinutes(deltaPx: Float, slotPx: Float): Int {
    val rawDeltaMinutes = deltaPx / slotPx * SLOT_MINUTES
    return snapToBlockInteractionStep(rawDeltaMinutes)
}

@Composable
private fun SavedBlockMovePreview(
    lane: Lane,
    startMinute: Int,
    endMinute: Int,
    visibleStart: Int,
    slotHeight: Dp,
) {
    val top = slotHeight * ((startMinute - visibleStart).toFloat() / SLOT_MINUTES)
    val height = slotHeight * ((endMinute - startMinute).toFloat() / SLOT_MINUTES)
    Box(
        modifier = Modifier
            .offset(y = top)
            .padding(horizontal = 3.dp)
            .fillMaxWidth()
            .height(height)
            .border(1.dp, (if (lane == Lane.Planned) TimeboxTheme.colors.planned else TimeboxTheme.colors.actual).copy(alpha = 0.65f), TimeboxShapes.block)
            .testTag("saved-${lane.wire}-move-preview"),
    )
}

/** Clamp a move/resize to the visible window, mirroring the prototype's rules. */
private fun applyDrag(
    mode: DragMode,
    deltaMinutes: Int,
    originalStart: Int,
    originalEnd: Int,
    visibleStart: Int,
    visibleEnd: Int,
    minimumDuration: Int,
): Pair<Int, Int> {
    var start = originalStart
    var end = originalEnd
    when (mode) {
        DragMode.Move -> {
            start = originalStart + deltaMinutes
            end = originalEnd + deltaMinutes
        }
        DragMode.ResizeStart -> start = minOf(
            originalStart + deltaMinutes,
            originalEnd - minimumDuration,
        )
        DragMode.ResizeEnd -> end = maxOf(
            originalEnd + deltaMinutes,
            originalStart + minimumDuration,
        )
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
    resizeBounds: Pair<Int, Int>,
    onAccessibleMove: (Int) -> Unit,
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
    val currentResizeBounds by rememberUpdatedState(resizeBounds)
    var resizePreview by remember(placement.taskId) { mutableStateOf<Pair<Int, Int>?>(null) }
    val startMinute = resizePreview?.first ?: placement.startMinute
    val endMinute = resizePreview?.second ?: placement.endMinute
    val top = slotHeight * ((startMinute - visibleStart).toFloat() / SLOT_MINUTES)
    val slotsTall = (endMinute - startMinute).toFloat() / SLOT_MINUTES
    val height = max(slotHeight.value * slotsTall, 1f).dp
    val grooves = height >= 64.dp || resizePreview != null
    val innerHeight = height - if (grooves) TimeboxDimens.grooveHeight * 2 else 0.dp
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

    Box(
        modifier = Modifier
            .offset(y = if (resizePreview != null) top else animatedTop)
            .padding(horizontal = 3.dp)
            .fillMaxWidth()
            .height(if (resizePreview != null) height else animatedHeight)
            .onGloballyPositioned { cardRoot = it.positionInRoot() }
            .graphicsLayer {
                alpha = if (dragging) 0f else 1f
                scaleX = if (dragging) 0.98f else 1f
                scaleY = if (dragging) 0.98f else 1f
            }
            .shadow(if (dragging) 12.dp else 3.dp, TimeboxShapes.block, clip = false)
            .clip(TimeboxShapes.block)
            .background(colors.planned.copy(alpha = if (colors.isDark) 0.30f else 0.13f))
            .border(1.dp, colors.plannedBorder, TimeboxShapes.block)
            .semantics {
                contentDescription = "Planning draft ${placement.taskTitle}"
                customActions = if (gesturesEnabled) listOf(
                    CustomAccessibilityAction("Move 5 minutes earlier") {
                        onAccessibleMove(-BLOCK_INTERACTION_STEP_MINUTES)
                        true
                    },
                    CustomAccessibilityAction("Move 5 minutes later") {
                        onAccessibleMove(BLOCK_INTERACTION_STEP_MINUTES)
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
                slotHeight,
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
                    armImmediately = { down ->
                        grooves && dragModeForPress(down.y) != DragMode.Move
                    },
                    onDragStart = { down ->
                        mode = if (grooves) dragModeForPress(down.y) else DragMode.Move
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
                            val rawDeltaMinutes = total.y / slotPx * SLOT_MINUTES
                            val minuteDelta = snapToBlockInteractionStep(rawDeltaMinutes)
                            resizePreview = when (mode) {
                                DragMode.ResizeStart -> Pair(
                                    (placement.startMinute + minuteDelta)
                                        .coerceIn(
                                            currentResizeBounds.first,
                                            placement.endMinute - MIN_PLANNED_BLOCK_MINUTES,
                                        ),
                                    placement.endMinute,
                                )
                                DragMode.ResizeEnd -> Pair(
                                    placement.startMinute,
                                    (placement.endMinute + minuteDelta)
                                        .coerceIn(
                                            placement.startMinute + MIN_PLANNED_BLOCK_MINUTES,
                                            currentResizeBounds.second,
                                        ),
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
            if (grooves) Groove()
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (innerHeight >= 22.dp) Text(
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
            if (grooves) Groove()
        }
    }
}

@Composable
private fun BlockCard(
    block: TimeBlock,
    startMinute: Int,
    endMinute: Int,
    previewStartMinute: Int,
    previewEndMinute: Int,
    visibleStart: Int,
    slotHeight: Dp,
    selected: Boolean,
    dragging: Boolean,
    invalid: Boolean = false,
    moveEnabled: Boolean,
    resizeEnabled: Boolean,
    onTap: () -> Unit,
    onDrag: (DragMode, Float) -> DragState?,
    onDragEnd: (DragState?) -> Unit,
    onDragCancel: () -> Unit,
    onAccessibleMove: ((Int) -> Unit)?,
    onDragPointer: ((Float?) -> Unit)?,
) {
    val colors = TimeboxTheme.colors
    val haptics = LocalHapticFeedback.current
    val top = slotHeight * ((startMinute - visibleStart).toFloat() / SLOT_MINUTES)
    val slotsTall = (endMinute - startMinute).toFloat() / SLOT_MINUTES
    val height = max(slotHeight.value * slotsTall, 1f).dp
    val grooves = resizeEnabled && (height >= 64.dp || dragging)
    val innerHeight = height - if (grooves) TimeboxDimens.grooveHeight * 2 else 0.dp
    val elevation = when {
        dragging -> 16.dp
        selected -> 8.dp
        else -> 0.dp
    }
    var cardTopInRoot by remember(block.id) { mutableFloatStateOf(0f) }
    var resolvedDrag by remember(block.id) { mutableStateOf<DragState?>(null) }
    val currentDrag by rememberUpdatedState(onDrag)
    val currentTap by rememberUpdatedState(onTap)

    Box(
        modifier = Modifier
            .offset(y = top)
            .padding(horizontal = 3.dp)
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { cardTopInRoot = it.positionInRoot().y }
            .testTag("day-block-${block.id}")
            .graphicsLayer { if (dragging) rotationZ = -1f }
            .shadow(elevation, TimeboxShapes.block, clip = false)
            .clip(TimeboxShapes.block)
            .background(
                when {
                    height < 22.dp ->
                        (if (block.lane == Lane.Planned) colors.planned else colors.actual).copy(alpha = 0.65f)
                    dragging || selected -> colors.paperRaised
                    else -> colors.paper
                }
            )
            .semantics {
                contentDescription = "${block.primaryIdentity()}, ${hhmm(startMinute)} to ${hhmm(endMinute)}"
                customActions = onAccessibleMove?.let { move ->
                    listOf(
                        CustomAccessibilityAction("Move 5 minutes earlier") {
                            move(-BLOCK_INTERACTION_STEP_MINUTES)
                            true
                        },
                        CustomAccessibilityAction("Move 5 minutes later") {
                            move(BLOCK_INTERACTION_STEP_MINUTES)
                            true
                        },
                    )
                } ?: emptyList()
            }
            // Tap, move and both resizes start with a press on the same card, so one
            // handler owns all of them. Edge presses resize immediately; a body press
            // still arms movement with a long press. Movement before body arming remains
            // available to the surrounding timeline scroll and day pager.
            .pointerInput(block.id, block.startMinute, block.endMinute, moveEnabled, resizeEnabled, slotHeight) {
                var mode = DragMode.Move
                var total = 0f
                detectLongPressArmedDragGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onTap = { currentTap() },
                    gestureEnabled = { down ->
                        when (if (grooves) dragModeForPress(down.y) else DragMode.Move) {
                            DragMode.Move -> moveEnabled
                            DragMode.ResizeStart, DragMode.ResizeEnd -> resizeEnabled
                        }
                    },
                    armImmediately = { down ->
                        resizeEnabled && grooves && dragModeForPress(down.y) != DragMode.Move
                    },
                    onDragStart = { down ->
                        // The drawn groove is only 8dp, so the grab zone is given a
                        // little more reach than the paint — but never more than a third
                        // of the card, or a one-slot block would have no move surface.
                        mode = if (grooves) dragModeForPress(down.y) else DragMode.Move
                        total = 0f
                        resolvedDrag = null
                        if (mode == DragMode.Move) onDragPointer?.invoke(cardTopInRoot + down.y)
                    },
                    onDrag = { change, moved ->
                        total += moved.y
                        if (mode == DragMode.Move) onDragPointer?.invoke(cardTopInRoot + change.position.y)
                        resolvedDrag = currentDrag(mode, total)
                    },
                    onDragEnd = {
                        onDragPointer?.invoke(null)
                        onDragEnd(resolvedDrag)
                        resolvedDrag = null
                    },
                    onDragCancel = {
                        onDragPointer?.invoke(null)
                        onDragCancel()
                        resolvedDrag = null
                    },
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (grooves) Groove()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (innerHeight >= 22.dp) Text(
                    text = buildString {
                        append(if (com.timebox.android.BuildConfig.ACTIVITY_TRACKING_DEV && block.lane == Lane.Actual)
                            block.name?.takeIf { it.isNotBlank() } ?: block.taskTypeName
                        else block.primaryIdentity())
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
                val secondary = if (invalid) {
                    NO_NEARBY_BLOCK_SPACE
                } else if (dragging) {
                    "${hhmm(previewStartMinute)} – ${hhmm(previewEndMinute)}"
                } else {
                    block.secondaryIdentity()
                }
                if (secondary != null && innerHeight >= 30.dp) {
                    Text(
                        text = secondary,
                        style = TimeboxTheme.type.monoSmall,
                        color = colors.onVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (innerHeight >= 30.dp) {
                    Text(
                        text = "${hhmm(previewStartMinute)} – ${hhmm(previewEndMinute)}",
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
            if (grooves) Groove()
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
