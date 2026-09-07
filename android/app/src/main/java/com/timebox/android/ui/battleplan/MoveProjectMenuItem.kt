package com.timebox.android.ui.battleplan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Project
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

/** Screen-scoped move state shared with the mobile card menus. */
internal data class ProjectMoveActions(
    val projects: List<Project>,
    val saving: Boolean,
    val message: String?,
    val move: (BattleTask, Int?) -> Unit,
)

internal val LocalProjectMove = staticCompositionLocalOf {
    ProjectMoveActions(emptyList(), false, null) { _, _ -> }
}

@Composable
internal fun MoveProjectMenuItem(task: BattleTask, actions: ProjectMoveActions, onMoved: () -> Unit) {
    val colors = TimeboxTheme.colors
    var expanded by remember(task.id) { mutableStateOf(false) }
    var destination by remember(task.id) { mutableStateOf(task.projectId) }
    var submitted by remember(task.id) { mutableStateOf(false) }
    var query by remember(task.id) { mutableStateOf("") }
    val destinations = (listOf(null to "Admin") + actions.projects.map { it.id to it.name })
        .filter { it.first != task.projectId }
    val destinationName = destinations.firstOrNull { it.first == destination }?.second
    LaunchedEffect(task.projectId) {
        if (submitted && task.projectId == destination) onMoved()
        destination = task.projectId
    }
    Box {
        DropdownMenuItem(
            text = { Text("Move to project", style = TimeboxTheme.type.label) },
            leadingIcon = {
                Box(Modifier.size(32.dp).background(colors.surf, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Inbox, null, Modifier.size(18.dp), tint = colors.onVariant)
                }
            },
            trailingIcon = { Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, Modifier.size(20.dp)) },
            enabled = !actions.saving,
            modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            onClick = { expanded = !expanded; submitted = false; destination = task.projectId; query = "" },
        )
        if (expanded) {
            val density = LocalDensity.current
            var availableHeight by remember { mutableStateOf<Int?>(null) }
            val margin = with(density) { 8.dp.roundToPx() }
            val parentView = LocalView.current
            val position = remember(margin, parentView) {
                DownwardProjectPositionProvider(margin, parentOffset = {
                    val location = IntArray(2)
                    parentView.getLocationOnScreen(location)
                    IntOffset(location[0], location[1])
                }) { availableHeight = it }
            }
            Popup(
                popupPositionProvider = position,
                onDismissRequest = { if (!actions.saving) { expanded = false; submitted = false; destination = task.projectId } },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(shape = TimeboxShapes.field, color = colors.low, shadowElevation = 12.dp) {
                    Column(
                        Modifier.widthIn(min = 264.dp, max = 280.dp)
                            .heightIn(max = availableHeight?.let { with(density) { it.toDp() } } ?: LocalConfiguration.current.screenHeightDp.dp)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("Current: ${task.project?.name ?: actions.projects.find { it.id == task.projectId }?.name ?: "Admin"}",
                            style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(bottom = 4.dp))
                        if (destinations.size > 6) {
                            OutlinedTextField(query, { query = it }, label = { Text("Find project") }, singleLine = true,
                                enabled = !actions.saving, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                textStyle = TimeboxTheme.type.bodySmall)
                        }
                        Column(Modifier.fillMaxWidth().heightIn(max = 192.dp).verticalScroll(rememberScrollState())) {
                            val matches = destinations.filter { it.second.contains(query, ignoreCase = true) }
                            if (matches.isEmpty()) Text("No projects found", style = TimeboxTheme.type.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
                            matches.forEach { (id, name) ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .selectable(destination == id, enabled = !actions.saving, role = Role.RadioButton,
                                            onClick = { destination = id; submitted = false })
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    RadioButton(destination == id, onClick = null, enabled = !actions.saving, modifier = Modifier.size(20.dp))
                                    Text(name, style = TimeboxTheme.type.bodySmall, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (submitted && !actions.saving && task.projectId != destination && actions.message != null) {
                            Text(actions.message, style = TimeboxTheme.type.bodySmall, color = colors.error,
                                modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Assertive })
                        }
                        if (destinationName != null) {
                            Text("Move to $destinationName?", style = TimeboxTheme.type.bodySmall,
                                modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !actions.saving, onClick = { expanded = false; submitted = false; destination = task.projectId }) { Text("Cancel") }
                            Button(enabled = !actions.saving && destinationName != null, onClick = {
                                submitted = true
                                actions.move(task, destination)
                            }) { Text(if (actions.saving) "Moving…" else if (submitted) "Retry" else "Move") }
                        }
                    }
                }
            }
        }
    }
}

/** Keep the origin below the menu row even when selection/error content grows. */
internal class DownwardProjectPositionProvider(
    private val margin: Int,
    private val parentOffset: () -> IntOffset = { IntOffset.Zero },
    private val onAvailableHeight: (Int) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val offset = parentOffset()
        val x = offset.x + if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val y = offset.y + anchorBounds.bottom
        onAvailableHeight((windowSize.height - y - margin).coerceAtLeast(1))
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y)
    }
}
