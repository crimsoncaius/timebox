package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.SLOT_MINUTES
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
)

@Composable
internal fun PlanningModeHeaders(modifier: Modifier = Modifier) {
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
            Kicker("Tasks to plan", colors.planned, Modifier.width(railWidth))
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
    onArmAccessibleTask: (Int?) -> Unit,
    onRetryReadyTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val density = LocalDensity.current
    val slotPx = with(density) { TimeboxDimens.slotHeight.toPx() }
    val edgeZonePx = with(density) { 48.dp.toPx() }
    val scrollStepPx = with(density) { 12.dp.toPx() }
    val ghostHalfWidthPx = with(density) { 54.dp.toPx() }
    val ghostHalfHeightPx = with(density) { 24.dp.toPx() }
    val timelineScroll = rememberScrollState()
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var laneBounds by remember { mutableStateOf(Rect.Zero) }
    var viewportBounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<TaskDragState?>(null) }

    val candidateStart = drag?.let {
        planningDropStart(
            pointerRoot = it.pointerRoot,
            laneBounds = laneBounds,
            viewportBounds = viewportBounds,
            visibleStart = day.visibleStart,
            visibleEnd = day.visibleEnd,
            slotPx = slotPx,
        )
    }
    val candidateValid = candidateStart?.let { start ->
        isPlanningDropAvailable(day, start, state.pendingPlanningPlacement)
    } ?: false
    val activePreview = drag?.let { active ->
        candidateStart?.let { start ->
            PlanningDropPreview(
                title = active.task.title,
                startMinute = start,
                endMinute = start + SLOT_MINUTES,
                state = if (candidateValid) PlanningPreviewState.Valid else PlanningPreviewState.Invalid,
            )
        }
    }
    val pendingPreview = state.pendingPlanningPlacement
        ?.takeIf { it.date == day.date }
        ?.let {
            PlanningDropPreview(
                title = it.taskTitle,
                startMinute = it.startMinute,
                endMinute = it.endMinute,
                state = PlanningPreviewState.Pending,
            )
        }

    LaunchedEffect(drag != null, viewportBounds) {
        while (drag != null) {
            val pointerY = drag?.pointerRoot?.y ?: break
            val delta = when {
                pointerY < viewportBounds.top + edgeZonePx -> -scrollStepPx
                pointerY > viewportBounds.bottom - edgeZonePx -> scrollStepPx
                else -> 0f
            }
            if (delta != 0f) timelineScroll.scrollBy(delta)
            delay(AUTO_SCROLL_FRAME_MILLIS)
        }
    }

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
                    planningPreview = activePreview ?: pendingPreview,
                    onPlannedLaneBoundsChanged = { laneBounds = it },
                )
            }

            PlanningTaskRail(
                state = state,
                enabled = state.pendingPlanningPlacement == null,
                onRetry = onRetryReadyTasks,
                onArmAccessibleTask = onArmAccessibleTask,
                onDragStart = { task, pointer -> drag = TaskDragState(task, pointer) },
                onDrag = { pointer -> drag = drag?.copy(pointerRoot = pointer) },
                onDragEnd = { task, pointer ->
                    val start = planningDropStart(
                        pointerRoot = pointer,
                        laneBounds = laneBounds,
                        viewportBounds = viewportBounds,
                        visibleStart = day.visibleStart,
                        visibleEnd = day.visibleEnd,
                        slotPx = slotPx,
                    )
                    val valid = start?.let {
                        isPlanningDropAvailable(day, it, state.pendingPlanningPlacement)
                    } ?: false
                    drag = null
                    if (start != null && valid) {
                        onPlanTask(task.id, start)
                    }
                },
                onDragCancel = { drag = null },
                modifier = Modifier.width(railWidth).fillMaxHeight(),
            )
        }

        drag?.let { active ->
            DragGhost(
                task = active.task,
                modifier = Modifier.offset {
                    val local = active.pointerRoot - rootPosition
                    IntOffset(
                        (local.x - ghostHalfWidthPx).roundToInt(),
                        (local.y - ghostHalfHeightPx).roundToInt(),
                    )
                },
            )
        }
    }
}

@Composable
private fun PlanningTaskRail(
    state: DayUiState,
    enabled: Boolean,
    onRetry: () -> Unit,
    onArmAccessibleTask: (Int?) -> Unit,
    onDragStart: (BattleTask, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (BattleTask, Offset) -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val visibleTasks = state.readyTasks.filterNot { it.id == state.pendingPlanningPlacement?.taskId }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(colors.low)
            .border(1.dp, colors.hairline, RoundedCornerShape(3.dp)),
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
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(visibleTasks, key = BattleTask::id) { task ->
                    PlanningTaskCard(
                        task = task,
                        enabled = enabled,
                        armed = task.id == state.accessibilityPlanningTaskId,
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
private fun PlanningTaskCard(
    task: BattleTask,
    enabled: Boolean,
    armed: Boolean,
    onArmAccessible: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    onDragCancel: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    var handleRoot by remember(task.id) { mutableStateOf(Offset.Zero) }
    var pointerRoot by remember(task.id) { mutableStateOf(Offset.Zero) }
    val currentOnArmAccessible by rememberUpdatedState(onArmAccessible)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TimeboxShapes.card)
            .background(if (armed) colors.plannedSurface else colors.lowest)
            .border(1.dp, if (armed) colors.plannedBorder else colors.hairline, TimeboxShapes.card)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = task.taskType?.leaf ?: "Unspecified",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .semantics {
                        contentDescription = "Schedule ${task.title}"
                        onClick {
                            currentOnArmAccessible()
                            true
                        }
                    }
                    .onGloballyPositioned { handleRoot = it.positionInRoot() }
                    .pointerInput(task.id, enabled) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                pointerRoot = handleRoot + offset
                                currentOnDragStart(pointerRoot)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                pointerRoot += amount
                                currentOnDrag(pointerRoot)
                            },
                            onDragEnd = { currentOnDragEnd(pointerRoot) },
                            onDragCancel = currentOnDragCancel,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    contentDescription = null,
                    tint = if (enabled) colors.planned else colors.outlineVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun DragGhost(task: BattleTask, modifier: Modifier = Modifier) {
    val colors = TimeboxTheme.colors
    Box(
        modifier = modifier
            .width(108.dp)
            .height(48.dp)
            .clip(TimeboxShapes.card)
            .background(colors.paperRaised)
            .border(1.5.dp, colors.planned, TimeboxShapes.card)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            task.title,
            style = TimeboxTheme.type.label,
            color = colors.on,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun planningDropStart(
    pointerRoot: Offset,
    laneBounds: Rect,
    viewportBounds: Rect,
    visibleStart: Int,
    visibleEnd: Int,
    slotPx: Float,
): Int? {
    if (slotPx <= 0f || laneBounds == Rect.Zero || viewportBounds == Rect.Zero) return null
    if (pointerRoot.x < laneBounds.left || pointerRoot.x > laneBounds.right) return null
    if (pointerRoot.y < viewportBounds.top || pointerRoot.y > viewportBounds.bottom) return null
    val slot = ((pointerRoot.y - laneBounds.top) / slotPx).roundToInt()
    return (visibleStart + slot * SLOT_MINUTES).coerceIn(visibleStart, visibleEnd - SLOT_MINUTES)
}

internal fun isPlanningDropAvailable(
    day: Day,
    startMinute: Int,
    pending: PendingPlanningPlacement? = null,
): Boolean {
    val endMinute = startMinute + SLOT_MINUTES
    if (startMinute < day.visibleStart || endMinute > day.visibleEnd) return false
    if (day.lane(Lane.Planned).any {
            blocksOverlap(startMinute, endMinute, it.startMinute, it.endMinute)
        }
    ) return false
    return pending == null || pending.date != day.date ||
        !blocksOverlap(startMinute, endMinute, pending.startMinute, pending.endMinute)
}
