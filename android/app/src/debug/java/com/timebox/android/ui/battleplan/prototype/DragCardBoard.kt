package com.timebox.android.ui.battleplan.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun SampleTask.lane() = if (done) 2 else if (inProgress) 1 else 0
private val lanes = listOf("To do", "In progress", "Completed")

/** THROWAWAY: A's content with three different pickup affordances. No backend writes. */
@Composable
internal fun DragCardBoard(
    tasks: List<SampleTask>, update: (List<SampleTask>) -> Unit, variant: Int,
    complete: (SampleTask) -> Unit, open: (Int) -> Unit, modifier: Modifier,
) {
    val currentTasks by rememberUpdatedState(tasks)
    val currentUpdate by rememberUpdatedState(update)
    var lane by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<SampleTask?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var originalBounds by remember { mutableStateOf(Rect.Zero) }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var lastMove by remember { mutableStateOf("Order unchanged") }
    val cards = remember { mutableStateMapOf<Int, Rect>() }
    val handles = remember { mutableStateMapOf<Int, Rect>() }
    val tabs = remember { mutableStateMapOf<Int, Rect>() }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val targetId = tasks.filter { it.lane() == lane && it.id != dragging?.id }
        .firstOrNull { (cards[it.id]?.center?.y ?: Float.MAX_VALUE) > pointer.y }?.id

    fun finish() {
        val task = dragging ?: return
        val rest = currentTasks.filter { it.id != task.id }.toMutableList()
        val target = rest.filter { it.lane() == lane }.firstOrNull { (cards[it.id]?.center?.y ?: Float.MAX_VALUE) > pointer.y }
        val index = target?.let { rest.indexOf(it) } ?: ((rest.indexOfLast { it.lane() == lane } + 1).takeIf { it > 0 } ?: rest.size)
        rest.add(index, task.copy(done = lane == 2, inProgress = lane == 1, ready = if (lane == 2) false else task.ready))
        currentUpdate(rest)
        lastMove = "Moved ${task.title} → ${lanes[lane]}"
        dragging = null
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun accessibleMove(task: SampleTask, delta: Int): Boolean {
        val list = currentTasks.toMutableList()
        val siblings = list.filter { it.lane() == task.lane() }
        val next = siblings.getOrNull(siblings.indexOf(task) + delta) ?: return false
        val a = list.indexOf(task); val b = list.indexOf(next)
        list[a] = next; list[b] = task
        currentUpdate(list)
        lastMove = "Moved ${task.title} ${if (delta < 0) "up" else "down"}"
        return true
    }
    LaunchedEffect(variant) { dragging = null }
    // Hover a tab or hold a screen edge to change status while still holding the task.
    LaunchedEffect(dragging?.id) {
        var pending = -1
        var ticks = 0
        while (dragging != null) {
            val edge = with(density) { 28.dp.toPx() }
            val candidate = tabs.entries.firstOrNull { it.value.contains(pointer) }?.key
                ?: when { pointer.x < rootBounds.left + edge -> lane - 1; pointer.x > rootBounds.right - edge -> lane + 1; else -> lane }
            if (candidate in 0..2 && candidate != lane) {
                if (candidate == pending) ticks++ else { pending = candidate; ticks = 0 }
                if (ticks >= 8) { lane = candidate; cards.clear(); handles.clear(); scroll.scrollTo(0); ticks = 0 }
            } else { pending = -1; ticks = 0 }
            val margin = with(density) { 60.dp.toPx() }
            if (pointer.y > viewport.bottom - margin) scroll.scrollTo((scroll.value + 18).coerceAtMost(scroll.maxValue))
            else if (pointer.y < viewport.top + margin && pointer.y > viewport.top) scroll.scrollTo((scroll.value - 18).coerceAtLeast(0))
            delay(50)
        }
    }
    Box(modifier.fillMaxWidth().onGloballyPositioned { rootBounds = it.boundsInRoot() }
        .pointerInput(variant) {
            val start: (Offset) -> Unit = { offset ->
                val global = offset + rootBounds.topLeft
                val hit = (if (variant == 6) cards else handles).entries.firstOrNull { it.value.contains(global) }?.key
                val task = currentTasks.find { it.id == hit }
                if (task != null) {
                    originalBounds = cards.getValue(task.id)
                    grabOffset = global - originalBounds.topLeft
                    pointer = global
                    dragging = task
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            if (variant == 6) detectDragGesturesAfterLongPress(onDragStart = start, onDragEnd = { finish() }, onDragCancel = { dragging = null }, onDrag = { change, _ ->
                if (dragging != null) { change.consume(); pointer = change.position + rootBounds.topLeft }
            }) else awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                start(down.position)
                if (dragging != null) {
                    down.consume()
                    try {
                        var pressed = true
                        while (pressed) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            pointer = change.position + rootBounds.topLeft
                            pressed = change.pressed
                            change.consume()
                        }
                        finish()
                    } finally { dragging = null }
                }
            }
        }) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                lanes.forEachIndexed { index, name ->
                    TextButton(onClick = { lane = index; cards.clear(); handles.clear() }, modifier = Modifier.weight(1f).onGloballyPositioned { tabs[index] = it.boundsInRoot() }, colors = ButtonDefaults.textButtonColors(containerColor = if (lane == index) colors.secondaryContainer else colors.background)) {
                        Text("$name (${tasks.count { it.lane() == index }})", fontSize = 11.sp)
                    }
                }
            }
            Text(if (dragging != null) "Release to drop · hold a tab or side edge to change status" else when (variant) {
                4 -> "Drag the right handle to reorder or change status"
                5 -> "Drag the left grip · circle completes the task"
                else -> "Hold a card to lift · drag to reorder or change status"
            }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 11.sp, color = colors.onSurfaceVariant)
            Column(Modifier.weight(1f).onGloballyPositioned { viewport = it.boundsInRoot() }.verticalScroll(scroll, enabled = dragging == null).padding(horizontal = 12.dp)) {
                tasks.filter { it.lane() == lane }.forEach { task ->
                    key(task.id) {
                        if (dragging != null && task.id == targetId) HorizontalDivider(thickness = 3.dp, color = colors.primary)
                        Row(Modifier.fillMaxWidth().onGloballyPositioned { cards[task.id] = it.boundsInRoot() }
                            .alpha(if (dragging?.id == task.id) 0.22f else 1f)
                            .semantics { customActions = listOf(
                                CustomAccessibilityAction("Move up") { accessibleMove(task, -1) },
                                CustomAccessibilityAction("Move down") { accessibleMove(task, 1) },
                            ) }, verticalAlignment = Alignment.CenterVertically) {
                            if (variant == 5) Box(Modifier.width(40.dp).height(64.dp).onGloballyPositioned { handles[task.id] = it.boundsInRoot() }.semantics { contentDescription = "Drag ${task.title}" }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.DragIndicator, null, tint = colors.onSurfaceVariant)
                            }
                            Box(Modifier.weight(1f)) {
                                if (variant == 4) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { TaskRow(task, 0, false, { complete(task) }, { open(task.id) }, {}) }
                                    Box(Modifier.width(44.dp).height(72.dp).onGloballyPositioned { handles[task.id] = it.boundsInRoot() }.semantics { contentDescription = "Drag ${task.title}" }, contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.DragIndicator, null, tint = colors.onSurfaceVariant)
                                    }
                                } else {
                                    TaskRow(task, 0, false, { complete(task) }, { open(task.id) }, {})
                                    if (variant == 6) Icon(Icons.Outlined.DragHandle, "Hold card to drag", Modifier.align(Alignment.TopEnd).padding(end = 8.dp).size(14.dp), tint = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (dragging != null && targetId == null) HorizontalDivider(thickness = 3.dp, color = colors.primary)
                if (tasks.none { it.lane() == lane }) Text("Drop a task here", Modifier.fillMaxWidth().padding(32.dp), color = colors.onSurfaceVariant)
                Spacer(Modifier.height(90.dp))
            }
            Text(lastMove, Modifier.padding(horizontal = 16.dp), maxLines = 1, fontSize = 10.sp, color = colors.onSurfaceVariant)
            Text("Order: ${tasks.filter { it.lane() == lane }.joinToString { it.id.toString() }}", Modifier.padding(horizontal = 16.dp), fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
        dragging?.let { task ->
            Surface(Modifier.offset { val pos = pointer - grabOffset - rootBounds.topLeft; IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .width(with(density) { originalBounds.width.toDp() }), shape = RoundedCornerShape(11.dp), shadowElevation = 16.dp, color = colors.secondaryContainer) {
                Column(Modifier.padding(16.dp)) {
                    Text(task.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    Text("Moving to ${lanes[lane]}", fontSize = 11.sp)
                }
            }
        }
    }
}
