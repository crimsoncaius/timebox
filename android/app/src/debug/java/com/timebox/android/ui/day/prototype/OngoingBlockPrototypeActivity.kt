package com.timebox.android.ui.day.prototype

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.theme.TimeboxShapes

/** THROWAWAY: three ongoing Actual Block layouts at timebox://prototype/ongoing-block?variant=A.
 * Debug-only, local sample data. No repository calls or persistence.
 */
class OngoingBlockPrototypeActivity : ComponentActivity() {
    private var variant by mutableStateOf("A")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        variant = intent.data?.getQueryParameter("variant") ?: "A"
        setContent { TimeboxTheme(darkTheme = false) { OngoingBlockPrototype(variant, ::change) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        variant = intent.data?.getQueryParameter("variant") ?: "A"
    }
    private fun change(next: String) {
        variant = next
        intent.data = Uri.parse("timebox://prototype/ongoing-block?variant=$next")
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (currentFocus?.onCheckIsTextEditor() != true &&
            !window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime()) &&
            keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            val keys = listOf("A", "B", "C")
            change(keys[(keys.indexOf(variant).coerceAtLeast(0) + if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) 2 else 1) % 3])
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
private fun OngoingBlockPrototype(variant: String, onVariant: (String) -> Unit) {
    val c = TimeboxTheme.colors
    val t = TimeboxTheme.type
    var expanded by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("Ongoing") }
    var name by remember { mutableStateOf("Draft the launch brief") }
    var note by remember { mutableStateOf("Clarify the audience and the first milestone.") }
    var start by remember { mutableStateOf("09:30") }
    var action by remember { mutableStateOf<String?>(null) }
    val running = mode == "Ongoing"
    val accent = if (mode == "Planned") c.planned else c.actual
    val names = listOf("A — Shared sheet", "B — Live strip", "C — Details group")
    val index = listOf("A", "B", "C").indexOf(variant).coerceAtLeast(0)
    Surface(color = c.bg, contentColor = c.on, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 76.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("DAY", style = t.kicker, color = c.onVariant)
                Text("Saturday, 19 September", style = t.screenTitle)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("PLANNED", style = t.kicker, color = c.planned)
                    Text("ACTUAL", style = t.kicker, color = c.actual)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Planned", "Actual", "Ongoing").forEach { label ->
                        FilterChip(selected = mode == label, onClick = { mode = label; expanded = false }, label = { Text(label) })
                    }
                }
                Surface(shape = TimeboxShapes.sheet, color = c.surf, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (mode == "Planned") "●  PLANNED" else "●  ACTUAL", style = t.laneLabel, color = accent)
                            Text(if (running) "Recording" else "45 min", style = t.bodySmall, color = accent)
                        }
                        if (variant != "B" || !running) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$start – ${if (running) "Now" else "10:15"}", Modifier.weight(1f), style = t.display)
                                if (running) Text("45 min", style = t.label, color = accent)
                            }
                        }
                        if (running && variant == "B") {
                            Text(name, style = t.screenTitle)
                            Surface(color = c.actualSurface, shape = TimeboxShapes.group) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column { Text("STARTED", style = t.kicker); Text(start, style = t.label) }
                                    Column { Text("ONGOING", style = t.kicker); Text("45 min elapsed", style = t.label, color = accent) }
                                }
                            }
                        }
                        if (running && !expanded) {
                            if (variant != "B") Text(name, style = t.label)
                            Text("Work / Writing", style = t.bodySmall, color = c.onVariant)
                            if (variant == "C") {
                                Surface(color = c.low, shape = TimeboxShapes.group) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("BLOCK DETAILS", style = t.kicker, color = c.onVariant)
                                        Text(note, style = t.body)
                                        Text("↗  Prepare product launch", style = t.bodySmall)
                                        TextButton(onClick = { expanded = true }) { Text("Expand to edit") }
                                    }
                                }
                            } else {
                                Text(note, style = t.body, color = c.onVariant)
                                LinkedTask()
                                TextButton(onClick = { expanded = true }) { Text("Edit details") }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(start, { start = it }, label = { Text("Start") }, modifier = Modifier.weight(1f), singleLine = true, shape = TimeboxShapes.field)
                                OutlinedTextField(if (running) "Now · running" else "10:15", {}, readOnly = true, label = { Text("End") }, modifier = Modifier.weight(1f), shape = TimeboxShapes.field)
                            }
                            OutlinedTextField(name, { name = it }, label = { Text("Block Name (optional)") }, modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field)
                            Text("TASK TYPE", style = t.kicker, color = c.onVariant)
                            // Reuse the application's typed-path picker; selection stays in this prototype.
                            var query by remember { mutableStateOf("Work / Writing") }
                            com.timebox.android.ui.day.TaskTypePicker(
                                taskTypes = listOf(com.timebox.android.data.TaskType(1, "Work / Writing", 0)),
                                query = query, onQueryChange = { query = it }, selectedTypeId = 1,
                                onChoose = { query = it.name }, onCreate = { query = it },
                            )
                            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = TimeboxShapes.field)
                            LinkedTask()
                            if (running) TextButton(onClick = { expanded = false }) { Text("Done editing") }
                        }
                        HorizontalDivider(color = c.hairline)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (running) {
                                OutlinedButton(onClick = { action = "Switch activity" }, modifier = Modifier.weight(1f)) { Text("Switch") }
                                Button(onClick = { action = "Stop tracking" }, modifier = Modifier.weight(1f)) { Text("Stop") }
                            } else Button(onClick = { action = "Saved locally" }, modifier = Modifier.fillMaxWidth()) { Text("Save changes") }
                        }
                    }
                }
                Text("PROTOTYPE · Sample data · ${names[index]}\nState: $mode · ${if (expanded) "editing" else "summary"} · $start → ${if (running) "open end" else "10:15"}", style = t.bodySmall, color = c.onVariant)
            }
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp), color = c.on, contentColor = c.bg, shape = TimeboxShapes.group, shadowElevation = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onVariant(listOf("A", "B", "C")[(index + 2) % 3]) }) { Text("←", color = c.bg) }
                    Text(names[index], style = t.bodySmall)
                    TextButton(onClick = { onVariant(listOf("A", "B", "C")[(index + 1) % 3]) }) { Text("→", color = c.bg) }
                }
            }
            action?.let { title -> AlertDialog(onDismissRequest = { action = null }, title = { Text(title) }, text = { Text("Prototype only. ${if (title == "Stop tracking") "Stop ends this Actual Block and shows its saved details." else "This action does not change your recorded activity."}") }, confirmButton = { TextButton(onClick = { if (title == "Stop tracking") { mode = "Actual"; expanded = false }; action = null }) { Text(if (title == "Stop tracking") "Preview stopped block" else "Close") } }) }
        }
    }
}

@Composable
private fun LinkedTask() {
    val c = TimeboxTheme.colors
    Surface(shape = TimeboxShapes.group, color = c.low, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("LINKED BATTLE PLAN TASK", style = TimeboxTheme.type.kicker, color = c.onVariant)
            Text("Prepare product launch", style = TimeboxTheme.type.label)
            Text("Recording time does not change Task Completion.", style = TimeboxTheme.type.bodySmall, color = c.onVariant)
        }
    }
}
