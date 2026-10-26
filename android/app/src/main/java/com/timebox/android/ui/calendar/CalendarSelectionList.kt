package com.timebox.android.ui.calendar

import com.timebox.android.ui.components.ContextualHelp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

@Composable
internal fun CalendarSelectionList(calendars: List<JsonObject>, order: List<String>, enabled: Boolean, onSelect: (List<String>) -> Unit) {
    val colors = TimeboxTheme.colors
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    val cardGap = with(LocalDensity.current) { 8.dp.toPx() }
    val heights = remember { mutableStateMapOf<String, Int>() }
    var dragged by remember(order, enabled) { mutableStateOf<String?>(null) }
    var distance by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val source = order.indexOf(dragged)
    val draggedHeight = heights[dragged] ?: 0
    val centers = order.mapIndexed { index, id -> order.take(index).sumOf { heights[it] ?: 0 } + index * cardGap + (heights[id] ?: 0) / 2f }
    val target = if (source < 0) -1 else centers.indices.minByOrNull { kotlin.math.abs(centers[it] - (centers[source] + distance)) } ?: source
    fun move(from: Int, to: Int) {
        if (enabled && from in order.indices && to in order.indices && from != to) {
            onSelect(order.toMutableList().apply { add(to, removeAt(from)) })
        }
    }
    val currentTarget by rememberUpdatedState(target)
    val currentMove by rememberUpdatedState(::move)
    // Scroll only this group's bounded viewport while a held row reaches its edge.
    LaunchedEffect(dragged) {
        while (dragged != null) {
            val edge = draggedHeight * .6f
            val step = when {
                pointerY < viewport.top + edge -> -12f
                pointerY > viewport.bottom - edge -> 12f
                else -> 0f
            }
            if (step != 0f) distance += scroll.scrollBy(step)
            delay(16)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Imported calendars", style = TimeboxTheme.type.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
        if (order.isEmpty()) Text("Select a calendar below to start importing.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        ContextualHelp("Calendar priority", "Hold and drag to change priority, or use the Move up and Move down accessibility actions. The first calendar supplies details when a meeting appears more than once.")
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(TimeboxShapes.field)
            .onGloballyPositioned { viewport = it.boundsInWindow() }
            .verticalScroll(scroll, enabled = dragged == null), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            order.forEachIndexed { index, id ->
                key(id) {
                    val name = calendars.find { it.s("id") == id }?.s("name") ?: "Unavailable calendar"
                    val held = dragged == id
                    var rowBounds by remember { mutableStateOf(Rect.Zero) }
                    val shift = when {
                        source < 0 -> 0f
                        index > source && index <= target -> -(draggedHeight + cardGap)
                        index < source && index >= target -> draggedHeight + cardGap
                        else -> 0f
                    }
                    val animatedShift by animateFloatAsState(shift, label = "Calendar position")
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .onSizeChanged { heights[id] = it.height }
                            .zIndex(if (held) 1f else 0f)
                            .graphicsLayer { translationY = if (held) distance else animatedShift }
                            .clip(TimeboxShapes.cell)
                            .background(if (held) colors.high else colors.raised)
                            .onGloballyPositioned { rowBounds = it.boundsInWindow() }
                            .pointerInput(order, enabled) {
                                if (!enabled || order.size < 2) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        distance = 0f; pointerY = rowBounds.top + offset.y; dragged = id
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragCancel = { dragged = null; distance = 0f },
                                    onDragEnd = { currentMove(index, currentTarget); dragged = null; distance = 0f },
                                    onDrag = { change, amount -> change.consume(); pointerY += amount.y; distance += amount.y },
                                )
                            }
                            .toggleable(value = true, enabled = enabled, role = Role.Checkbox, onValueChange = { onSelect(order - id) })
                            .semantics {
                                customActions = buildList {
                                    if (enabled && index > 0) add(CustomAccessibilityAction("Move up") { move(index, index - 1); true })
                                    if (enabled && index < order.lastIndex) add(CustomAccessibilityAction("Move down") { move(index, index + 1); true })
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = true, onCheckedChange = null, enabled = enabled)
                        Column(Modifier.weight(1f)) {
                            Text(name, style = TimeboxTheme.type.bodySmall)
                            if (index == 0) Text("First choice", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                        }
                        if (order.size > 1) Icon(Icons.Outlined.DragHandle, null, tint = if (enabled) colors.onVariant else colors.disabledContent, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        val others = calendars.filter { it.s("id") !in order }
        if (others.isNotEmpty()) {
            Text("Other calendars", style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            others.forEach { calendar ->
                val id = calendar.s("id") ?: return@forEach
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clip(TimeboxShapes.cell)
                    .background(colors.raised.copy(alpha = 0.55f))
                    .toggleable(value = false, enabled = enabled, role = Role.Checkbox, onValueChange = { onSelect(order + id) })
                    .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Checkbox(checked = false, onCheckedChange = null, enabled = enabled)
                    Text(calendar.s("name") ?: id, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
            }
        }
        if (calendars.isEmpty()) Text("No calendars are available yet. Try syncing again, or check this account in Google Calendar.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
    }
}
