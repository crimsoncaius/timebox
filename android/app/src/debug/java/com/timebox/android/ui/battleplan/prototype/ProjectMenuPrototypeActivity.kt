package com.timebox.android.ui.battleplan.prototype

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

/** PROTOTYPE #111: three Android Project menu structures on one debug deep link.
 * timebox://prototype/project-menu?variant=A — fixture data; no persistent actions.
 */
class ProjectMenuPrototypeActivity : ComponentActivity() {
    private var variant by mutableIntStateOf(0)
    private fun cycle(delta: Int) {
        variant = (variant + delta + 3) % 3
        intent.data = Uri.parse("timebox://prototype/project-menu?variant=${"ABC"[variant]}")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        variant = "ABC".indexOf(intent.data?.getQueryParameter("variant") ?: "A").coerceAtLeast(0)
        setContent { TimeboxTheme(darkTheme = true) { Prototype(variant, ::cycle) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        variant = "ABC".indexOf(intent.data?.getQueryParameter("variant") ?: "A").coerceAtLeast(0)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { cycle(-1); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { cycle(1); true }
        else -> super.onKeyUp(keyCode, event)
    }
}

@Composable
private fun Prototype(variant: Int, cycle: (Int) -> Unit) {
    val c = TimeboxTheme.colors
    var expanded by remember { mutableStateOf(true) }
    var action by remember { mutableStateOf("No action selected") }
    LaunchedEffect(variant) { expanded = true; action = "No action selected" }
    val names = listOf("Compact outline", "Divided surface", "Inset actions")
    Box(Modifier.fillMaxSize().background(c.bg).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Battle Plan", style = TimeboxTheme.type.screenTitle)
            Text("Projects     /     Website refresh", color = c.project)
            Surface(shape = TimeboxShapes.card, color = c.card) {
                Column {
                    listOf("Website refresh", "Home improvements", "Learn photography").forEachIndexed { i, name ->
                        Row(Modifier.fillMaxWidth().height(56.dp).background(if (i == 0) c.raised else c.card).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, null, tint = if (i == 0) c.project else c.onVariant)
                            Text(name, Modifier.weight(1f).padding(start = 8.dp), style = TimeboxTheme.type.label)
                            Box {
                                IconButton(onClick = { expanded = true }) { Icon(Icons.Outlined.MoreHoriz, "Open project menu") }
                                if (i == 0) DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.width(if (variant == 0) 176.dp else 208.dp),
                                    shape = if (variant == 0) TimeboxShapes.cell else TimeboxShapes.card,
                                    containerColor = c.raised,
                                    tonalElevation = 0.dp,
                                    shadowElevation = if (variant == 0) 4.dp else 8.dp,
                                    border = BorderStroke(1.dp, c.outlineVariant),
                                    properties = PopupProperties(focusable = false),
                                ) {
                                    if (variant == 2) {
                                        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(onClick = { action = "Edit selected (preview only)"; expanded = false }, shape = TimeboxShapes.cell, color = c.high) {
                                                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp)); Text("Edit", Modifier.padding(start = 12.dp))
                                                }
                                            }
                                            Surface(onClick = { action = "Delete selected (preview only)"; expanded = false }, shape = TimeboxShapes.cell, color = c.error.copy(alpha = .09f)) {
                                                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(20.dp), tint = c.error); Text("Delete", Modifier.padding(start = 12.dp), color = c.error)
                                                }
                                            }
                                        }
                                    } else {
                                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { action = "Edit selected (preview only)"; expanded = false })
                                        if (variant == 1) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = c.outlineVariant)
                                        DropdownMenuItem(text = { Text("Delete", color = c.error) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = c.error) }, onClick = { action = "Delete selected (preview only)"; expanded = false })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("Website refresh", style = TimeboxTheme.type.sectionTitle)
            Text("3 tasks", color = c.onVariant)
            listOf("Sketch the landing page", "Review content with Alex", "Prepare launch checklist").forEach {
                Surface(shape = TimeboxShapes.card, color = c.card) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = c.onVariant)
                        Text(it, Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PROTOTYPE · ${"ABC"[variant]} · ${if (expanded) "menu open" else "menu closed"}", color = c.onVariant, style = TimeboxTheme.type.bodySmall)
            Text(action, color = c.onVariant, style = TimeboxTheme.type.bodySmall)
            Surface(shape = TimeboxShapes.chip, color = c.highest, shadowElevation = 8.dp, modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cycle(-1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous variant") }
                    Text("${"ABC"[variant]} — ${names[variant]}", Modifier.clickable { expanded = true })
                    IconButton(onClick = { cycle(1) }) { Icon(Icons.Outlined.ChevronRight, "Next variant") }
                }
            }
        }
    }
}
