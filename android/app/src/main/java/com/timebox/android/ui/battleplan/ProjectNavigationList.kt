package com.timebox.android.ui.battleplan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.timebox.android.data.Project
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The primary Project list: taps navigate, long-pressing a handle reorders. */
@Composable
internal fun ProjectNavigationList(
    projects: List<Project>,
    selectedId: Int?,
    saving: Boolean,
    onSelect: (Project) -> Unit,
    onReorder: (List<Int>) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val haptics = LocalHapticFeedback.current
    val rowHeight = with(LocalDensity.current) { 56.dp.toPx() }
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var distance by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var actionsId by remember { mutableStateOf<Int?>(null) }
    val sourceIndex = projects.indexOfFirst { it.id == draggedId }
    val targetIndex = if (sourceIndex < 0) -1 else
        (sourceIndex + (distance / rowHeight).roundToInt()).coerceIn(projects.indices)
    val latestOnReorder by rememberUpdatedState(onReorder)

    fun move(from: Int, to: Int) {
        if (saving || from !in projects.indices || to !in projects.indices || from == to) return
        val ids = projects.map { it.id }.toMutableList()
        ids.add(to, ids.removeAt(from))
        latestOnReorder(ids)
    }

    // Keep the held row under the finger as longer Project lists scroll.
    LaunchedEffect(draggedId) {
        while (draggedId != null) {
            val edge = rowHeight * 0.65f
            val step = when {
                pointerY < viewport.top + edge -> -rowHeight / 8f
                pointerY > viewport.bottom - edge -> rowHeight / 8f
                else -> 0f
            }
            if (step != 0f) distance += scroll.scrollBy(step)
            delay(16)
        }
    }

    Column(Modifier.fillMaxWidth().heightIn(max = 336.dp)
        .onGloballyPositioned { viewport = it.boundsInWindow() }
        .verticalScroll(scroll, enabled = draggedId == null)) {
        projects.forEachIndexed { index, project ->
            key(project.id) {
                val dragging = draggedId == project.id
                val shift = when {
                    sourceIndex < 0 -> 0f
                    index > sourceIndex && index <= targetIndex -> -rowHeight
                    index < sourceIndex && index >= targetIndex -> rowHeight
                    else -> 0f
                }
                val animatedShift by animateFloatAsState(shift, label = "Project position")
                var handleBounds by remember { mutableStateOf(Rect.Zero) }
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) distance else animatedShift
                            shadowElevation = if (dragging) 6.dp.toPx() else 0f
                            shape = TimeboxShapes.cell
                        }
                        .background(if (dragging || project.id == selectedId) colors.selected else colors.lowest, TimeboxShapes.cell)
                        .semantics {
                            customActions = buildList {
                                if (!saving && index > 0) add(CustomAccessibilityAction("Move ${project.name} up") { move(index, index - 1); true })
                                if (!saving && index < projects.lastIndex) add(CustomAccessibilityAction("Move ${project.name} down") { move(index, index + 1); true })
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp)
                        .onGloballyPositioned { handleBounds = it.boundsInWindow() }
                        .pointerInput(projects.map { it.id }, saving) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    if (!saving) {
                                        actionsId = null
                                        distance = 0f
                                        pointerY = handleBounds.top + offset.y
                                        draggedId = project.id
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDragCancel = { draggedId = null; distance = 0f },
                                onDragEnd = {
                                    if (draggedId == project.id) {
                                        val destination = (index + (distance / rowHeight).roundToInt()).coerceIn(projects.indices)
                                        move(index, destination)
                                    }
                                    draggedId = null
                                    distance = 0f
                                },
                                onDrag = { change, amount ->
                                    if (draggedId == project.id && !saving) {
                                        change.consume()
                                        pointerY += amount.y
                                        distance += amount.y
                                    }
                                },
                            )
                        }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.DragHandle, "Drag ${project.name}", tint = colors.onVariant, modifier = Modifier.size(20.dp))
                    }
                    Row(Modifier.weight(1f).fillMaxHeight().clickable(enabled = draggedId == null) { onSelect(project) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Folder, null, tint = colors.onVariant, modifier = Modifier.size(18.dp))
                        Text(project.name, style = TimeboxTheme.type.label, color = colors.on, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box {
                        IconButton(onClick = { actionsId = project.id }, enabled = !saving && draggedId == null) {
                            Icon(Icons.Outlined.MoreHoriz, "More actions for ${project.name}", tint = colors.onVariant)
                        }
                        DropdownMenu(expanded = actionsId == project.id, onDismissRequest = { actionsId = null }) {
                            DropdownMenuItem(text = { Text("Move up") }, enabled = !saving && index > 0,
                                onClick = { actionsId = null; move(index, index - 1) })
                            DropdownMenuItem(text = { Text("Move down") }, enabled = !saving && index < projects.lastIndex,
                                onClick = { actionsId = null; move(index, index + 1) })
                        }
                    }
                }
            }
        }
    }
}
