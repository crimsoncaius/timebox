package com.timebox.android.ui.battleplan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.timebox.android.data.Project
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The primary Project list: taps navigate, while holding a Project row reorders it. */
@Composable
internal fun ProjectNavigationList(
    projects: List<Project>,
    selectedId: Int?,
    saving: Boolean,
    onSelect: (Project) -> Unit,
    onReorder: (List<Int>) -> Unit,
    onEdit: (Project) -> Unit = {},
    onDelete: (Project) -> Unit = {},
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
    var orderedProjects by remember { mutableStateOf(projects) }
    var pendingOrder by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(projects) {
        val incomingOrder = projects.map { it.id }
        if (pendingOrder == null || incomingOrder == pendingOrder) {
            orderedProjects = projects
            pendingOrder = null
        }
    }
    val sourceIndex = orderedProjects.indexOfFirst { it.id == draggedId }
    val targetIndex = if (sourceIndex < 0) -1 else
        (sourceIndex + (distance / rowHeight).roundToInt()).coerceIn(orderedProjects.indices)
    val latestOnReorder by rememberUpdatedState(onReorder)

    fun move(from: Int, to: Int) {
        if (saving || from !in orderedProjects.indices || to !in orderedProjects.indices || from == to) return
        val ids = orderedProjects.map { it.id }.toMutableList()
        ids.add(to, ids.removeAt(from))
        orderedProjects = ids.map { id -> orderedProjects.first { it.id == id } }
        pendingOrder = ids
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

    Column(
        Modifier.fillMaxWidth().heightIn(max = 336.dp)
            .clip(TimeboxShapes.card)
            .background(colors.card)
            .onGloballyPositioned { viewport = it.boundsInWindow() }
            .verticalScroll(scroll, enabled = draggedId == null),
    ) {
        orderedProjects.forEachIndexed { index, project ->
            key(project.id) {
                val dragging = draggedId == project.id
                val shift = when {
                    sourceIndex < 0 -> 0f
                    index > sourceIndex && index <= targetIndex -> -rowHeight
                    index < sourceIndex && index >= targetIndex -> rowHeight
                    else -> 0f
                }
                val animatedShift by animateFloatAsState(shift, label = "Project position")
                var rowBounds by remember { mutableStateOf(Rect.Zero) }
                val selected = project.id == selectedId
                val projectColor = colors.project
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) distance else animatedShift
                            shadowElevation = if (dragging) 6.dp.toPx() else 0f
                            shape = TimeboxShapes.cell
                        }
                        .clip(TimeboxShapes.cell)
                        .background(if (dragging || selected) colors.raised else Color.Transparent)
                        .then(if (dragging || selected) Modifier.border(1.dp, projectColor, TimeboxShapes.cell) else Modifier)
                        .onGloballyPositioned { rowBounds = it.boundsInWindow() }
                        .semantics {
                            customActions = buildList {
                                if (!saving && index > 0) add(CustomAccessibilityAction("Move ${project.name} up") { move(index, index - 1); true })
                                if (!saving && index < orderedProjects.lastIndex) add(CustomAccessibilityAction("Move ${project.name} down") { move(index, index + 1); true })
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).fillMaxHeight()
                        .pointerInput(orderedProjects.map { it.id }, saving) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    if (!saving) {
                                        actionsId = null
                                        distance = 0f
                                        pointerY = rowBounds.top + offset.y
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
                        }
                        .clickable(enabled = draggedId == null) { onSelect(project) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (selected) {
                            Box(Modifier.size(10.dp).clip(TimeboxShapes.chip).background(projectColor))
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                Text("CURRENT PROJECT", style = TimeboxTheme.type.bodySmall, color = projectColor, fontWeight = FontWeight.Bold)
                                Text(project.name, style = TimeboxTheme.type.label, color = colors.on, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Icon(Icons.Outlined.Folder, null, tint = colors.onVariant, modifier = Modifier.size(18.dp))
                            Text(project.name, style = TimeboxTheme.type.label, color = colors.on, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box {
                        IconButton(onClick = { actionsId = project.id }, enabled = !saving && draggedId == null) {
                            Icon(Icons.Outlined.MoreHoriz, "More actions for ${project.name}", tint = colors.onVariant)
                        }
                        DropdownMenu(expanded = actionsId == project.id, onDismissRequest = { actionsId = null }) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { actionsId = null; onEdit(project) },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = colors.error) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = colors.error) },
                                onClick = { actionsId = null; onDelete(project) },
                            )
                        }
                    }
                }
            }
        }
    }
}
