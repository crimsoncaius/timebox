package com.timebox.android.ui.battleplan.prototype

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

/** THROWAWAY #109: compare chip, calendar action, and switch within a Battle Plan board.
 * Native equivalent of ?variant=A; fixture actions only, no repository or persistence.
 */
class TaskCardPrototypeActivity : ComponentActivity() {
    private var variant by mutableIntStateOf(0)
    private fun cycle(delta: Int) {
        variant = (variant + delta + 7) % 7
        intent.data = Uri.parse("timebox://prototype/task-cards?variant=${"ABCDEFG"[variant]}")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        variant = "ABCDEFG".indexOf(savedInstanceState?.getString("variant") ?: intent.data?.getQueryParameter("variant") ?: "A").coerceIn(0, 6)
        setContent { TimeboxTheme(darkTheme = true) { CardPrototype(variant, ::cycle) } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("variant", "ABCDEFG"[variant].toString())
        super.onSaveInstanceState(outState)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { cycle(-1); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { cycle(1); true }
        else -> super.onKeyUp(keyCode, event)
    }
}

@Composable
private fun CardPrototype(variant: Int, cycle: (Int) -> Unit) {
    val c = TimeboxTheme.colors
    val titles = listOf("Sketch the landing page", "Review the updated onboarding flow with Alex", "Prepare launch checklist", "Send the project update", "Book a dentist appointment")
    val ready = remember { mutableStateListOf(true, false, true, false, false) }
    val done = remember { mutableStateListOf(false, false, false, false, false) }
    var menu by remember { mutableIntStateOf(-1) }
    var action by remember { mutableStateOf("Tap any readiness control to compare both states") }
    val names = listOf("Inline chip", "Calendar action", "Compact switch", "Bookmark", "Pin to plan", "Queue beside completion", "Reference pill")
    Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Battle Plan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
            Text("Tasks     Projects     Recurring", color = c.onVariant)
            Text("ALL TASKS     ${titles.size}", style = TimeboxTheme.type.label, modifier = Modifier.padding(top = 12.dp))
            Text("Urgency  ·  Importance  ·  Task type", color = c.onVariant, style = TimeboxTheme.type.bodySmall)
            titles.forEachIndexed { i, title ->
                val toggle = { ready[i] = !ready[i]; action = "${i + 1}: ${if (ready[i]) "added to" else "removed from"} Ready to Plan" }
                val label = if (ready[i]) "Ready to Plan" else "Add to Ready to Plan"
                if (variant == 6) {
                    ReferenceCard(title, if (i < 4) "Website refresh · ${if (i == 1) "Due tomorrow" else "Design"}" else "Admin", ready[i], done[i], toggle,
                        { done[i] = !done[i]; if (done[i]) ready[i] = false; action = "${i + 1}: ${if (done[i]) "completed" else "reopened"}" },
                        { action = it })
                } else Surface(shape = TimeboxShapes.card, color = c.card) {
                    Row(Modifier.fillMaxWidth().padding(start = 2.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                        IconButton(onClick = { done[i] = !done[i]; if (done[i]) ready[i] = false; action = "${i + 1}: ${if (done[i]) "completed" else "reopened"}" }) {
                            Icon(if (done[i]) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, "Toggle completion for $title", tint = c.onVariant)
                        }
                        if (variant == 5 && !done[i]) CompactReadyIcon(variant, ready[i], title, toggle) { action = it }
                        Column(Modifier.weight(1f).padding(top = 10.dp, bottom = if (variant >= 3) 10.dp else 0.dp)) {
                            Text(title, style = TimeboxTheme.type.label, color = c.on)
                            if (variant == 4) Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (i < 4) "Website refresh · ${if (i == 1) "Due tomorrow" else "Design"}" else "Admin", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = if (i < 4) c.project else c.onVariant)
                                if (!done[i]) CompactReadyIcon(variant, ready[i], title, toggle) { action = it }
                            }
                            else Text(if (i < 4) "Website refresh · ${if (i == 1) "Due tomorrow" else "Design"}" else "Admin", style = TimeboxTheme.type.bodySmall, color = if (i < 4) c.project else c.onVariant, modifier = Modifier.padding(top = 6.dp))
                            if (done[i]) Text("Completed", color = c.onVariant, style = TimeboxTheme.type.bodySmall)
                            else when (variant) {
                                0 -> FilterChip(selected = ready[i], onClick = toggle, label = { Text(label, style = TimeboxTheme.type.bodySmall) }, leadingIcon = { Icon(if (ready[i]) Icons.Outlined.EventAvailable else Icons.Outlined.Add, null, Modifier.size(16.dp)) })
                                1 -> Text(if (ready[i]) "Ready to Plan" else "Not Ready to Plan", color = if (ready[i]) c.planned else c.onVariant, style = TimeboxTheme.type.bodySmall, modifier = Modifier.padding(top = 5.dp, bottom = 8.dp))
                                2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Ready to Plan", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = c.onVariant)
                                    Switch(checked = ready[i], onCheckedChange = { toggle() }, modifier = Modifier.semantics { contentDescription = "$label for $title" })
                                }
                            }
                        }
                        if (variant == 3 && !done[i]) CompactReadyIcon(variant, ready[i], title, toggle) { action = it }
                        Column {
                            Box {
                                IconButton(onClick = { menu = i }) { Icon(Icons.Outlined.MoreHoriz, "More options for $title", tint = c.onVariant) }
                                DropdownMenu(expanded = menu == i, onDismissRequest = { menu = -1 }) {
                                    listOf("Edit task", "Move to Project", "Move to Trash").forEach { item ->
                                        DropdownMenuItem(text = { Text(item) }, onClick = { action = "$item: preview only"; menu = -1 })
                                    }
                                }
                            }
                            if (variant == 1 && !done[i]) IconToggleButton(checked = ready[i], onCheckedChange = { toggle() }) {
                                Icon(if (ready[i]) Icons.Outlined.EventAvailable else Icons.Outlined.CalendarMonth, "$label for $title", tint = if (ready[i]) c.planned else c.onVariant)
                            }
                        }
                    }
                }
            }
        }
        Surface(color = c.bg, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PROTOTYPE #109 · in-memory only", style = TimeboxTheme.type.bodySmall, color = c.onVariant)
                Text("Ready: ${ready.mapIndexedNotNull { i, r -> if (r) i + 1 else null }} · Completed: ${done.mapIndexedNotNull { i, d -> if (d) i + 1 else null }}", style = TimeboxTheme.type.bodySmall, color = c.onVariant)
                Text(action, style = TimeboxTheme.type.bodySmall, color = c.onVariant, maxLines = 1)
                Surface(shape = TimeboxShapes.chip, color = c.highest, shadowElevation = 8.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { cycle(-1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous variant") }
                        Text("${"ABCDEFG"[variant]} — ${names[variant]}")
                        IconButton(onClick = { cycle(1) }) { Icon(Icons.Outlined.ChevronRight, "Next variant") }
                    }
                }
            }
        }
    }
}

// A 48dp touch target keeps the symbol compact without making tapping fiddly.
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun CompactReadyIcon(variant: Int, ready: Boolean, title: String, toggle: () -> Unit, explain: (String) -> Unit) {
    val c = TimeboxTheme.colors
    val symbol = when (variant) {
        3 -> if (ready) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
        4 -> if (ready) Icons.Filled.PushPin else Icons.Outlined.PushPin
        else -> if (ready) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow
    }
    Box(Modifier.size(48.dp).semantics {
        contentDescription = "${if (ready) "Remove" else "Add"} $title ${if (ready) "from" else "to"} Ready to Plan"
        toggleableState = if (ready) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
        stateDescription = if (ready) "Ready to Plan" else "Not Ready to Plan"
    }.combinedClickable(
        role = androidx.compose.ui.semantics.Role.Checkbox,
        onClick = toggle,
        onLongClickLabel = "Explain Ready to Plan",
        onLongClick = { explain("${if (ready) "Ready to Plan" else "Not Ready to Plan"} ï¿½ tap to toggle") },
    ), contentAlignment = Alignment.Center) {
        Icon(symbol, null, Modifier.size(20.dp), tint = if (ready) c.planned else c.onVariant)
    }
}





/** G follows the supplied card reference: title first, subtle metadata, lower-right pill. */
@Composable
private fun ReferenceCard(title: String, metadata: String, ready: Boolean, done: Boolean, toggle: () -> Unit, complete: () -> Unit, report: (String) -> Unit) {
    val c = TimeboxTheme.colors
    var menu by remember { mutableStateOf(false) }
    Surface(shape = TimeboxShapes.card, color = c.card) {
        Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            IconButton(onClick = complete) {
                Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, "Toggle completion for $title", tint = c.onVariant)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 10.dp)) {
                        Text(title, style = TimeboxTheme.type.label, color = c.on)
                        Text(metadata, style = TimeboxTheme.type.bodySmall, color = c.onVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "More options for $title", tint = c.onVariant) }
                        DropdownMenu(menu, { menu = false }) {
                            listOf("Edit task", "Move to Project", "Move to Trash").forEach { item ->
                                DropdownMenuItem(text = { Text(item) }, onClick = { report("$item: preview only"); menu = false })
                            }
                        }
                    }
                }
                if (done) Text("Completed", Modifier.padding(vertical = 8.dp), style = TimeboxTheme.type.bodySmall, color = c.onVariant)
                else Box(Modifier.align(Alignment.End).heightIn(min = 48.dp).semantics {
                    contentDescription = "${if (ready) "Remove" else "Add"} $title ${if (ready) "from" else "to"} Ready to Plan"
                    stateDescription = if (ready) "Ready to Plan" else "Not Ready to Plan"
                }.then(Modifier.toggleable(value = ready, role = Role.Checkbox, onValueChange = { toggle() })), contentAlignment = Alignment.Center) {
                    Surface(shape = TimeboxShapes.chip, color = if (ready) c.plannedSurface else c.card,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (ready) c.plannedBorder else c.outlineVariant)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (ready) "Ready to Plan" else "Add to Plan", style = TimeboxTheme.type.bodySmall, color = if (ready) c.planned else c.onVariant)
                            Text(if (ready) "→" else "+", style = TimeboxTheme.type.bodySmall, color = if (ready) c.planned else c.onVariant)
                        }
                    }
                }
            }
        }
    }
}
