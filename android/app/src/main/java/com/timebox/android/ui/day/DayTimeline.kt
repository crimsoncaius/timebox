package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.SLOT_MINUTES
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.gutterLabel
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** A move or resize in progress, held locally so dragging stays at 60fps. */
private data class DragState(
    val id: Int,
    val startMinute: Int,
    val endMinute: Int,
)

private enum class DragMode { Move, ResizeStart, ResizeEnd }

@Composable
fun DayTimeline(
    day: Day,
    selectedBlockId: Int?,
    draft: Draft?,
    onTapSlot: (Lane, Int) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
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
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
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
private fun rememberNowMinute(day: Day): Int? = produceState(
    initialValue = day.nowMinuteAt(System.currentTimeMillis()),
    key1 = day,
) {
    // Re-synced at the top of the loop as well as on first composition, so a fresh day
    // takes effect immediately rather than after the next tick.
    while (true) {
        value = day.nowMinuteAt(System.currentTimeMillis())
        delay(NOW_TICK_MILLIS)
    }
}.value

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
            )
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
private fun BlockCard(
    block: TimeBlock,
    startMinute: Int,
    endMinute: Int,
    visibleStart: Int,
    slotHeight: Dp,
    selected: Boolean,
    dragging: Boolean,
    onTap: () -> Unit,
    onDrag: (DragMode, Float) -> Unit,
    onDragEnd: (DragMode, Float) -> Unit,
) {
    val colors = TimeboxTheme.colors
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
            .graphicsLayer { if (dragging) rotationZ = -1f }
            .shadow(elevation, TimeboxShapes.block, clip = false)
            .clip(TimeboxShapes.block)
            .background(if (dragging || selected) colors.paperRaised else colors.paper)
            // Tap, move and both resizes start with a press on the same card, so one
            // handler owns all of them. Detectors on nested nodes — a groove inside the
            // card — fight over the pointer and neither wins reliably, so where the
            // press lands decides what the drag means. Move past the slop and it is a
            // drag; lift without moving and it is a tap.
            .pointerInput(block.id, block.startMinute, block.endMinute) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The drawn groove is only 8dp, so the grab zone is given a little
                    // more reach than the paint — but never more than a third of the
                    // card, or a one-slot block would have nothing left to move by.
                    val grab = minOf(
                        maxOf(TimeboxDimens.grooveHeight.toPx(), 14.dp.toPx()),
                        size.height / 3f,
                    )
                    val mode = when {
                        down.position.y < grab -> DragMode.ResizeStart
                        down.position.y > size.height - grab -> DragMode.ResizeEnd
                        else -> DragMode.Move
                    }
                    // Claim the press so the lane underneath does not also treat it as a
                    // tap on empty space and open a draft over the top of our selection.
                    down.consume()
                    var total = 0f
                    var sideways = 0f
                    var isDrag = false
                    var swiping = false

                    // The timeline's own vertical scroll claims these moves, and a claimed
                    // change reports a zero delta to `positionChange`. Reading past the
                    // claim is what lets a block outrank the scroll it sits in — without it
                    // the total never grows and every drag decays into a tap.
                    while (true) {
                        val change = awaitPointerEvent().changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val moved = change.positionChangeIgnoreConsumed()
                        total += moved.y
                        sideways += moved.x
                        // A sideways drag is the screen's day swipe passing through. Reading
                        // past the claim means that swipe cannot cancel this gesture the way
                        // it cancels an ordinary tap, so the card has to stand down itself —
                        // otherwise the lift lands as a tap and the sheet opens over the day
                        // the user just swiped to.
                        if (abs(sideways) > slop && abs(sideways) > abs(total)) {
                            swiping = true
                            break
                        }
                        if (abs(total) > slop) {
                            isDrag = true
                            change.consume()
                            break
                        }
                    }

                    if (isDrag) {
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            total += change.positionChangeIgnoreConsumed().y
                            change.consume()
                            onDrag(mode, total)
                        }
                        onDragEnd(mode, total)
                    } else if (!swiping) {
                        onTap()
                    }
                }
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
                    text = block.taskTypeName,
                    style = if (selected) {
                        TimeboxTheme.type.blockTitleSelected
                    } else {
                        TimeboxTheme.type.blockTitle
                    },
                    color = colors.on,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (innerHeight >= 30.dp) {
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
