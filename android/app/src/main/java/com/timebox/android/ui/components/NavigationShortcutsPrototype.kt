package com.timebox.android.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.BuildConfig
import com.timebox.android.ui.theme.TimeboxTheme

/** THROWAWAY: five native bar treatments on the real app, selected by ?variant=A|B|C|D|E.
 * Preferences deliberately live in memory. Production persistence is not being tested.
 */
internal object ShortcutPrototype {
    var enabled by mutableStateOf(false)
    var variant by mutableStateOf(0)
    var pins by mutableStateOf(navigationDestinations.take(2).map { it.route })
    var editing by mutableStateOf(false)
    val keys = listOf("A", "B", "C", "D", "E")
    val names = listOf("A · Equal tabs", "B · Split controls", "C · Inset dock", "D · Translucent overlay", "E · Floating pills")
}

internal fun configureShortcutPrototype(intent: Intent?) {
    ShortcutPrototype.enabled = BuildConfig.DEBUG && intent?.data?.path == "/navigation-shortcuts"
    if (ShortcutPrototype.enabled) {
        ShortcutPrototype.variant = ShortcutPrototype.keys.indexOf(intent?.data?.getQueryParameter("variant")).coerceAtLeast(0)
    }
}

@Composable
internal fun ShortcutPrototypeBar(current: String, onSelect: (String) -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(if (ShortcutPrototype.variant >= 3) Color.Transparent else TimeboxTheme.colors.bg).navigationBarsPadding()) {
        if (ShortcutPrototype.variant == 4) {
            PrototypeControls(current)
            Spacer(Modifier.height(12.dp))
        }
        PrototypeBarLayout(ShortcutPrototype.pins, current, onSelect, onOpen)
        if (ShortcutPrototype.variant != 4) PrototypeControls(current)
    }
}

@Composable
private fun PrototypeBarLayout(pins: List<String>, current: String, onSelect: (String) -> Unit, onOpen: () -> Unit) {
    val c = TimeboxTheme.colors
    val variant = ShortcutPrototype.variant
    val inset = variant >= 2
    val navigate = NavigationDestination("", "Navigate", "directory", Icons.Outlined.GridView)
    val entries = pins.map { route -> navigationDestinations.first { it.route == route } }
    if (variant == 0) Hairline()
    Row(Modifier.fillMaxWidth().padding(horizontal = if (inset) 12.dp else 4.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(if (inset) 20.dp else 0.dp))
        .background(when (variant) { 3 -> c.bg.copy(alpha = 0.78f); 2 -> c.low; else -> Color.Transparent })
        .padding(if (inset) 6.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (variant == 4) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically) {
        entries.forEach { item ->
            PrototypeNavItem(item, current == item.route, variant, Modifier.weight(1f)) { if (current != item.route) onSelect(item.route) }
        }
        if (variant < 3) repeat(3 - pins.size) { Spacer(Modifier.weight(1f)) }
        if (variant == 1) Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(36.dp).background(c.hairline))
        PrototypeNavItem(navigate, false, variant, Modifier.weight(1f), onOpen)
    }
}

@Composable
private fun PrototypeNavItem(item: NavigationDestination, active: Boolean, variant: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = TimeboxTheme.colors
    if (variant == 4) {
        val shape = RoundedCornerShape(50)
        val tint = if (active) c.onSelected else c.on
        Column(modifier.clip(shape)
            .background((if (active) c.selected else c.low).copy(alpha = 0.94f))
            .border(1.dp, (if (active) c.outline else c.hairline).copy(alpha = 0.7f), shape)
            .semantics { selected = active }.clickable(onClickLabel = "Open ${item.label}", onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(item.icon, null, Modifier.size(23.dp), tint = tint)
            Spacer(Modifier.height(4.dp))
            Text(item.label, color = tint, style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp),
                textAlign = TextAlign.Center, maxLines = 2)
        }
        return
    }
    if (variant == 1) {
        Row(modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(8.dp))
            .background(if (active) c.selected else Color.Transparent)
            .semantics { selected = active }.clickable(onClickLabel = "Open ${item.label}", onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (active) c.onSelected else c.onVariant
            Icon(item.icon, null, Modifier.size(20.dp), tint = tint)
            Text(item.label, Modifier.padding(start = 5.dp), color = tint,
                style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp), maxLines = 3)
        }
        return
    }
    Column(modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(if (variant >= 2) 14.dp else 8.dp))
        .background(if (active && variant != 0) c.selected.copy(alpha = if (variant == 3) 0.65f else 1f) else Color.Transparent)
        .semantics { selected = active }.clickable(onClickLabel = "Open ${item.label}", onClick = onClick)
        .heightIn(min = 64.dp).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val tint = if (active && variant != 0) c.onSelected else if (active) c.on else c.onVariant
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (active && variant == 0) c.high else Color.Transparent)
            .padding(horizontal = if (variant == 0) 15.dp else 4.dp, vertical = 3.dp)) {
            Icon(item.icon, null, Modifier.size(22.dp), tint = tint)
        }
        Text(item.label, color = tint, style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp), textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun PrototypeControls(current: String) {
    val c = TimeboxTheme.colors
    val activity = LocalContext.current as? Activity
    fun change(delta: Int) {
        ShortcutPrototype.variant = (ShortcutPrototype.variant + delta + ShortcutPrototype.keys.size) % ShortcutPrototype.keys.size
        activity?.intent?.data = Uri.parse("timebox://prototype/navigation-shortcuts?variant=${ShortcutPrototype.keys[ShortcutPrototype.variant]}")
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.clip(RoundedCornerShape(24.dp)).background(c.on), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { change(-1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous variant", tint = c.bg) }
            Text("Prototype · ${ShortcutPrototype.names[ShortcutPrototype.variant]}", style = TimeboxTheme.type.bodySmall, color = c.bg)
            IconButton(onClick = { change(1) }) { Icon(Icons.Outlined.ChevronRight, "Next variant", tint = c.bg) }
        }
        val names = ShortcutPrototype.pins.map { route -> navigationDestinations.first { it.route == route }.label }
        val screen = navigationDestinations.firstOrNull { it.route == current }?.label ?: current
        Text("Temporary: ${names.joinToString(" / ").ifEmpty { "no pins" }} · $screen", style = TimeboxTheme.type.bodySmall.copy(fontSize = 10.sp), color = c.onVariant)
    }
}

@Composable
internal fun ShortcutPrototypeEditor() {
    val c = TimeboxTheme.colors
    var draft by remember { mutableStateOf(ShortcutPrototype.pins) }
    BackHandler { ShortcutPrototype.editing = false }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ShortcutPrototype.editing = false }) { Text("Cancel", color = c.on) }
            Text("Edit shortcuts", Modifier.weight(1f), textAlign = TextAlign.Center, style = TimeboxTheme.type.label, color = c.on)
            TextButton(onClick = { ShortcutPrototype.pins = draft; ShortcutPrototype.editing = false }) { Text("Save", color = c.on) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("${draft.size} of 3 selected", style = TimeboxTheme.type.screenTitle, color = c.on, modifier = Modifier.padding(top = 16.dp))
            Text(if (draft.size == 3) "Remove a shortcut to choose another." else "Choose the screens you reach for most.", style = TimeboxTheme.type.body, color = c.onVariant, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            if (draft.isNotEmpty()) {
                Text("Your shortcuts · in bar order", style = TimeboxTheme.type.label, color = c.onVariant)
                draft.forEachIndexed { index, route ->
                    val item = navigationDestinations.first { it.route == route }
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, null, Modifier.size(20.dp), tint = c.onVariant)
                        Text(item.label, Modifier.weight(1f).padding(start = 12.dp), style = TimeboxTheme.type.body, color = c.on)
                        IconButton(enabled = index > 0, onClick = {
                            draft = draft.toMutableList().also { it[index] = it[index - 1]; it[index - 1] = route }
                        }) { Icon(Icons.Outlined.ArrowUpward, "Move ${item.label} earlier", tint = if (index > 0) c.on else c.disabledContent) }
                        IconButton(onClick = { draft = draft - route }) { Icon(Icons.Outlined.RemoveCircleOutline, "Remove ${item.label}", tint = c.on) }
                    }
                }
                Hairline(Modifier.padding(vertical = 12.dp))
            }
            navigationDestinations.groupBy { it.group }.forEach { (group, items) ->
                Text(group, style = TimeboxTheme.type.bodySmall, color = c.onVariant, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                items.forEach { item ->
                    val chosen = item.route in draft
                    val enabled = chosen || draft.size < 3
                    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { draft = if (chosen) draft - item.route else draft + item.route }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, null, Modifier.size(20.dp), tint = if (enabled) c.onVariant else c.disabledContent)
                        Text(item.label, Modifier.weight(1f).padding(start = 12.dp), style = TimeboxTheme.type.body, color = if (enabled) c.on else c.disabledContent)
                        Checkbox(checked = chosen, enabled = enabled, onCheckedChange = { draft = if (chosen) draft - item.route else draft + item.route })
                    }
                }
            }
        }
        Text("Bar preview", Modifier.padding(start = 20.dp, top = 8.dp), style = TimeboxTheme.type.bodySmall, color = c.onVariant)
        PrototypeBarLayout(draft, "", {}, {})
    }
}
