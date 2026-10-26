package com.timebox.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.timebox.android.ui.AppRoutes
import com.timebox.android.ui.theme.TimeboxTheme
import java.io.IOException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs

internal fun navigationShortcutRoutes(saved: List<String>?): List<String> =
    (saved ?: listOf(AppRoutes.DayPattern, AppRoutes.BattlePlan))
        .filter { route -> navigationDestinations.any { it.route == route } }.distinct()

// Clearance belongs inside scrolling content so it can move behind the floating dock.
internal val LocalNavigationDockInset = compositionLocalOf { 0.dp }

@Composable
internal fun NavigationDock(
    shortcuts: List<String>, currentRoute: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetScroll: Boolean = false,
) {
    if (shortcuts.isEmpty()) return
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
        ShortcutDockItems(shortcuts, currentRoute, onSelect, resetScroll)
    }
}

@Composable
private fun ShortcutDockItems(shortcuts: List<String>, currentRoute: String, onSelect: (String) -> Unit, resetScroll: Boolean = false) {
    val colors = TimeboxTheme.colors
    val entries = navigationShortcutRoutes(shortcuts).map { route -> navigationDestinations.first { it.route == route } }
    if (entries.isEmpty()) return
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center) {
        val availableWidth = (maxWidth - 12.dp).coerceAtLeast(1.dp)
        // Four destinations fit before the strip scrolls; small selections stay compact.
        val visibleItems = 4
        val itemWidthDp = 88.dp.coerceAtMost(availableWidth / visibleItems)
        val itemWidth = with(density) { itemWidthDp.toPx() }
        val snapLayout = remember(scroll, itemWidth) {
            object : SnapLayoutInfoProvider {
                override fun calculateApproachOffset(velocity: Float, decayOffset: Float) = decayOffset

                override fun calculateSnapOffset(velocity: Float): Float {
                    val boundary = ((scroll.value / itemWidth).roundToInt() * itemWidth)
                        .coerceIn(0f, scroll.maxValue.toFloat())
                    val end = scroll.maxValue.toFloat()
                    val target = if (abs(end - scroll.value) < abs(boundary - scroll.value)) end else boundary
                    return target - scroll.value
                }
            }
        }
        val flingBehavior = rememberSnapFlingBehavior(snapLayout)
        LaunchedEffect(currentRoute, resetScroll) { scroll.scrollTo(0) }
        Row(Modifier.width(itemWidthDp * minOf(entries.size, visibleItems) + 12.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(colors.low).border(1.dp, colors.hairline, RoundedCornerShape(20.dp)).padding(6.dp)
            .horizontalScroll(scroll, flingBehavior = flingBehavior).testTag("shortcut-strip")
            .semantics { isTraversalGroup = true },
            verticalAlignment = Alignment.CenterVertically) {
            entries.forEach { destination ->
                ShortcutDockItem(destination, currentRoute == destination.route, Modifier.width(itemWidthDp)) {
                    if (currentRoute != destination.route) onSelect(destination.route)
                }
            }
        }
    }
}

@Composable
private fun ShortcutDockItem(destination: NavigationDestination, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val tint = if (current) colors.onSelected else colors.onVariant
    Column(modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(14.dp))
        .background(if (current) colors.selected else Color.Transparent)
        .semantics { selected = current }
        .clickable(role = Role.Button, onClickLabel = "Open ${destination.label}", onClick = onClick)
        .heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(destination.icon, null, Modifier.padding(vertical = 3.dp).size(22.dp), tint = tint)
        Text(destination.label, color = tint, style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun EditableShortcutBar(shortcuts: List<String>, enabled: Boolean, onChange: (List<String>) -> Unit) {
    val colors = TimeboxTheme.colors
    val scroll = rememberScrollState()
    val latestShortcuts by rememberUpdatedState(shortcuts)
    val change by rememberUpdatedState(onChange)
    var dragged by remember { mutableStateOf<String?>(null) }
    var pointerX by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center) {
        val itemWidthDp = 88.dp.coerceAtMost((maxWidth - 12.dp) / 4)
        val itemWidth = with(density) { itemWidthDp.toPx() }
        val viewportWidth = itemWidth * minOf(shortcuts.size, 4)
        fun move(route: String, target: Int) {
            val items = latestShortcuts
            val from = items.indexOf(route)
            if (from >= 0 && target in items.indices && from != target) {
                change(items.toMutableList().apply { add(target, removeAt(from)) })
            }
        }
        fun reorder() {
            dragged?.let { route ->
                val center = pointerX + scroll.value - grabOffset + itemWidth / 2
                if (route in latestShortcuts) {
                    move(route, (center / itemWidth).toInt().coerceIn(0, latestShortcuts.lastIndex))
                } else dragged = null
            }
        }
        // Keep scrolling even while the finger is held still at either edge.
        LaunchedEffect(dragged, enabled) {
            if (!enabled) dragged = null
            if (dragged != null) {
                var previousFrame = withFrameNanos { it }
                while (dragged != null) {
                    val frame = withFrameNanos { it }
                    val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                    previousFrame = frame
                    val edge = itemWidth / 2
                    val direction = when {
                        pointerX < edge -> -((edge - pointerX) / edge).coerceAtMost(1f)
                        pointerX > viewportWidth - edge -> ((pointerX - viewportWidth + edge) / edge).coerceAtMost(1f)
                        else -> 0f
                    }
                    scroll.scrollBy(direction * itemWidth * 4 * seconds)
                    reorder()
                }
            }
        }
        Box(Modifier.width(if (shortcuts.isEmpty()) itemWidthDp * 4 + 12.dp
                else itemWidthDp * minOf(shortcuts.size, 4) + 12.dp)
            .clip(RoundedCornerShape(20.dp)).background(colors.low)
            .border(1.dp, colors.hairline, RoundedCornerShape(20.dp)).padding(6.dp)) {
            if (shortcuts.isEmpty()) {
                Text("No shortcuts selected", Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    textAlign = TextAlign.Center, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            } else {
                Box(Modifier.pointerInput(enabled, itemWidth) {
                    if (enabled) detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            val index = ((position.x + scroll.value) / itemWidth).toInt()
                            latestShortcuts.getOrNull(index)?.let {
                                pointerX = position.x
                                grabOffset = position.x + scroll.value - index * itemWidth
                                dragged = it
                            }
                        },
                        onDragEnd = { dragged = null },
                        onDragCancel = { dragged = null },
                        onDrag = { event, amount ->
                            event.consume()
                            pointerX += amount.x
                            reorder()
                        },
                    )
                }) {
                    Row(Modifier.horizontalScroll(scroll, enabled = dragged == null)
                        .testTag("editable-shortcut-strip").semantics { isTraversalGroup = true }) {
                        shortcuts.forEachIndexed { index, route ->
                            key(route) {
                                val destination = navigationDestinations.first { it.route == route }
                                val active = dragged == route
                                Column(Modifier.width(itemWidthDp).zIndex(if (active) 1f else 0f)
                                    .graphicsLayer {
                                        translationX = if (active) pointerX + scroll.value - grabOffset - index * itemWidth else 0f
                                        scaleX = if (active) 1.05f else 1f
                                        scaleY = if (active) 1.05f else 1f
                                    }
                                    .clip(RoundedCornerShape(14.dp)).background(if (active) colors.high else Color.Transparent)
                                    .testTag("edit-shortcut-$route")
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = destination.label
                                        stateDescription = "${index + 1} of ${shortcuts.size}"
                                        customActions = if (!enabled) emptyList() else buildList {
                                            if (index > 0) add(CustomAccessibilityAction("Move left") { move(route, index - 1); true })
                                            if (index < shortcuts.lastIndex) add(CustomAccessibilityAction("Move right") { move(route, index + 1); true })
                                            add(CustomAccessibilityAction("Remove") { change(latestShortcuts - route); true })
                                        }
                                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(enabled = enabled, onClick = { onChange(shortcuts - route) },
                                        modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Outlined.RemoveCircleOutline, "Remove ${destination.label}",
                                            Modifier.size(20.dp), tint = colors.onVariant)
                                    }
                                    Icon(destination.icon, null, Modifier.size(22.dp), tint = colors.onVariant)
                                    Text(destination.label, Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                        style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp),
                                        color = colors.onVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NavigationShortcutEditor(initialShortcuts: List<String>, onCancel: () -> Unit, onSave: suspend (List<String>) -> Unit) {
    val colors = TimeboxTheme.colors
    var draft by rememberSaveable(stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })) {
        mutableStateOf(navigationShortcutRoutes(initialShortcuts))
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler { if (!saving) onCancel() }
    Column(Modifier.fillMaxSize().background(colors.bg).pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding().navigationBarsPadding()
        .semantics { paneTitle = "Edit shortcuts" }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !saving, onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !saving, onClick = {
                saving = true
                error = null
                scope.launch {
                    try { onSave(draft) }
                    catch (_: IOException) { error = "Couldn't save shortcuts. Try again." }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Saving..." else "Save") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Edit shortcuts", style = TimeboxTheme.type.display, color = colors.on,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() })
            Text("This device",
                style = TimeboxTheme.type.body, color = colors.onVariant, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            error?.let { Text(it, color = colors.error, style = TimeboxTheme.type.body,
                modifier = Modifier.padding(bottom = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
            Text("Choose screens", style = TimeboxTheme.type.sectionTitle, color = colors.on,
                modifier = Modifier.padding(bottom = 4.dp).semantics { heading() })
            navigationDestinations.groupBy { it.group }.forEach { (group, destinations) ->
                Text(group, style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() })
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.low).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    destinations.forEach { destination ->
                        val chosen = destination.route in draft
                        val enabled = !saving
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (chosen) colors.high else Color.Transparent)
                            .toggleable(value = chosen, role = Role.Checkbox, enabled = enabled) {
                                draft = if (chosen) draft - destination.route else draft + destination.route
                            }.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(destination.icon, null, Modifier.size(20.dp), tint = if (enabled) colors.onVariant else colors.disabledContent)
                            Text(destination.label, Modifier.weight(1f).padding(start = 12.dp), style = TimeboxTheme.type.body,
                                color = if (enabled) colors.on else colors.disabledContent)
                            Checkbox(checked = chosen, enabled = enabled, onCheckedChange = null)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Hairline()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Your shortcut bar", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            Text("${draft.size} selected", style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        EditableShortcutBar(draft, enabled = !saving) { draft = it }
    }
}
