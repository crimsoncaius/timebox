package com.timebox.android.ui.battleplan

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Project
import kotlin.math.roundToInt

@Composable
internal fun ProjectOrderDialog(
    projects: List<Project>,
    saving: Boolean,
    error: String?,
    onReorder: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val insertionColor = MaterialTheme.colorScheme.primary
    val rowHeight = with(LocalDensity.current) { 56.dp.toPx() }
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    fun move(from: Int, to: Int) {
        if (saving || to !in projects.indices || from == to) return
        val ids = projects.map { it.id }.toMutableList()
        ids.add(to, ids.removeAt(from))
        onReorder(ids)
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Reorder projects") },
        text = {
            Column {
                Text("Hold a drag handle to move a project, or use the arrows.")
                if (saving) Text("Saving project order…")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    projects.forEachIndexed { index, project ->
                        key(project.id) {
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().height(56.dp)
                                        .drawWithContent {
                                            drawContent()
                                            if (draggedId != null && targetIndex == index) {
                                                val sourceIndex = projects.indexOfFirst { it.id == draggedId }
                                                val y = if (index > sourceIndex) size.height else 0f
                                                drawLine(insertionColor, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                                            }
                                        }
                                        .background(if (draggedId == project.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                        .semantics {
                                            customActions = buildList {
                                                if (!saving && index > 0) add(CustomAccessibilityAction("Move ${project.name} up") { move(index, index - 1); true })
                                                if (!saving && index < projects.lastIndex) add(CustomAccessibilityAction("Move ${project.name} down") { move(index, index + 1); true })
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.DragHandle, "Drag ${project.name}", Modifier.size(40.dp)
                                        .pointerInput(projects, saving) {
                                            var distance = 0f
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { if (!saving) { draggedId = project.id; targetIndex = index; distance = 0f } },
                                                onDragCancel = { draggedId = null; targetIndex = -1 },
                                                onDragEnd = {
                                                    if (draggedId == project.id) move(index, targetIndex)
                                                    draggedId = null; targetIndex = -1
                                                },
                                                onDrag = { change, amount ->
                                                    if (!saving && draggedId == project.id) {
                                                        change.consume()
                                                        distance += amount.y
                                                        targetIndex = (index + (distance / rowHeight).roundToInt()).coerceIn(projects.indices)
                                                    }
                                                },
                                            )
                                        })
                                    Text(project.name, Modifier.weight(1f), maxLines = 1)
                                    IconButton(onClick = { move(index, index - 1) }, enabled = !saving && index > 0) {
                                        Icon(Icons.Outlined.ArrowUpward, "Move ${project.name} up")
                                    }
                                    IconButton(onClick = { move(index, index + 1) }, enabled = !saving && index < projects.lastIndex) {
                                        Icon(Icons.Outlined.ArrowDownward, "Move ${project.name} down")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Done") } },
    )
}
