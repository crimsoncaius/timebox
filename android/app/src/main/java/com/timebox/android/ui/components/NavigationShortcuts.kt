package com.timebox.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.AppRoutes
import com.timebox.android.ui.theme.TimeboxTheme
import java.io.IOException
import kotlinx.coroutines.launch

internal fun navigationShortcutRoutes(saved: List<String>?): List<String> =
    (saved ?: listOf(AppRoutes.DayPattern, AppRoutes.BattlePlan))
        .filter { route -> navigationDestinations.any { it.route == route } }.distinct().take(3)

@Composable
internal fun NavigationDock(
    shortcuts: List<String>, currentRoute: String, onSelect: (String) -> Unit, onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(TimeboxTheme.colors.bg).navigationBarsPadding()) {
        ShortcutDockItems(shortcuts, currentRoute, onSelect, onOpen)
    }
}

@Composable
private fun ShortcutDockItems(shortcuts: List<String>, currentRoute: String, onSelect: (String) -> Unit, onOpen: () -> Unit, preview: Boolean = false) {
    val colors = TimeboxTheme.colors
    val entries = navigationShortcutRoutes(shortcuts).map { route -> navigationDestinations.first { it.route == route } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (entries.isEmpty()) Spacer(Modifier.weight(3f))
        else Row(Modifier.weight(3f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
            .background(colors.low).padding(6.dp).semantics { isTraversalGroup = true },
            verticalAlignment = Alignment.CenterVertically) {
            entries.forEach { destination ->
                ShortcutDockItem(destination, currentRoute == destination.route, Modifier.weight(1f), preview) {
                    if (currentRoute != destination.route) onSelect(destination.route)
                }
            }
            repeat(3 - entries.size) { Spacer(Modifier.weight(1f)) }
        }
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
            .background(colors.low).padding(6.dp), contentAlignment = Alignment.Center) {
            ShortcutDockItem(NavigationDestination("", "Navigate", "directory", Icons.Outlined.GridView),
                false, Modifier.fillMaxWidth(), preview, onOpen)
        }
    }
}

@Composable
private fun ShortcutDockItem(destination: NavigationDestination, current: Boolean, modifier: Modifier, preview: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val tint = if (current) colors.onSelected else colors.onVariant
    Column(modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(14.dp))
        .background(if (current) colors.selected else Color.Transparent)
        .then(if (preview) Modifier.clearAndSetSemantics { } else Modifier
            .semantics { selected = current }
            .clickable(role = Role.Button, onClickLabel = "Open ${destination.label}", onClick = onClick))
        .heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(destination.icon, null, Modifier.padding(vertical = 3.dp).size(22.dp), tint = tint)
        Text(destination.label, color = tint, style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp), textAlign = TextAlign.Center)
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
            Text("Edit shortcuts", Modifier.weight(1f).semantics { heading() }, textAlign = TextAlign.Center,
                style = TimeboxTheme.type.label, color = colors.on)
            TextButton(enabled = !saving, onClick = {
                saving = true
                error = null
                scope.launch {
                    try { onSave(draft) }
                    catch (_: IOException) { error = "Couldn't save shortcuts. Try again." }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Saving…" else "Save") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("${draft.size} of 3 selected", style = TimeboxTheme.type.screenTitle, color = colors.on,
                modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite })
            Text(if (draft.size == 3) "Remove a shortcut to choose another." else "Choose the screens you reach for most.",
                style = TimeboxTheme.type.body, color = colors.onVariant, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            error?.let { Text(it, color = colors.error, style = TimeboxTheme.type.body,
                modifier = Modifier.padding(bottom = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
            if (draft.isNotEmpty()) {
                Text("Your shortcuts · in bar order", style = TimeboxTheme.type.label, color = colors.onVariant)
                draft.forEachIndexed { index, route ->
                    val destination = navigationDestinations.first { it.route == route }
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(destination.icon, null, Modifier.size(20.dp), tint = colors.onVariant)
                        Text(destination.label, Modifier.weight(1f).padding(start = 12.dp), style = TimeboxTheme.type.body, color = colors.on)
                        IconButton(enabled = !saving && index > 0, onClick = {
                            draft = draft.toMutableList().also { it[index] = it[index - 1]; it[index - 1] = route }
                        }) { Icon(Icons.Outlined.ArrowUpward, "Move ${destination.label} earlier") }
                        IconButton(enabled = !saving, onClick = { draft = draft - route }) {
                            Icon(Icons.Outlined.RemoveCircleOutline, "Remove ${destination.label}")
                        }
                    }
                }
                Hairline(Modifier.padding(vertical = 12.dp))
            }
            navigationDestinations.groupBy { it.group }.forEach { (group, destinations) ->
                Text(group, style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() })
                destinations.forEach { destination ->
                    val chosen = destination.route in draft
                    val enabled = !saving && (chosen || draft.size < 3)
                    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(8.dp))
                        .toggleable(value = chosen, role = Role.Checkbox, enabled = enabled) {
                            draft = if (chosen) draft - destination.route else draft + destination.route
                        }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(destination.icon, null, Modifier.size(20.dp), tint = if (enabled) colors.onVariant else colors.disabledContent)
                        Text(destination.label, Modifier.weight(1f).padding(start = 12.dp), style = TimeboxTheme.type.body,
                            color = if (enabled) colors.on else colors.disabledContent)
                        Checkbox(checked = chosen, enabled = enabled, onCheckedChange = null)
                    }
                }
            }
        }
        Text("Bar preview", Modifier.padding(start = 20.dp, top = 8.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        ShortcutDockItems(draft, "", {}, {}, preview = true)
    }
}
