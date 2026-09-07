package com.timebox.android.ui.battleplan.prototype

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch

/** PROTOTYPE: three compact layouts plus a current-layout approximation, using debug-only fixtures. */
class TaskCardPrototypeActivity : ComponentActivity() {
    private var variant by mutableIntStateOf(0)
    private fun readVariant() { variant = (intent.data?.getQueryParameter("variant")?.firstOrNull()?.minus('A') ?: 4).coerceIn(0, 6) }
    private fun changeVariant(next: Int) {
        variant = (next + 7) % 7
        setIntent(Intent(intent).setData(Uri.parse("timebox://prototype/task-cards?variant=${'A' + variant}")))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readVariant()
        setContent { Prototype(variant, ::changeVariant) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readVariant() }
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { changeVariant(variant - 1); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { changeVariant(variant + 1); true }
        else -> super.onKeyUp(keyCode, event)
    }
}

internal data class SampleTask(
    val id: Int, val title: String, val due: String? = null, val progress: String? = null,
    val blocker: String? = null, val project: String? = null, val planned: String? = null,
    val ready: Boolean = false, val done: Boolean = false, val recurring: Boolean = false,
    val priority: String = "None", val inProgress: Boolean = false,
)
private val fixtures = listOf(
    SampleTask(1, "Figure out my EPAM stocks", "5 Sep · overdue", "0/3", project = "Personal finances", priority = "High importance"),
    SampleTask(2, "Pack room", progress = "0/2", ready = true),
    SampleTask(3, "Gaps time", "18 Aug · overdue", planned = "Today · 7 Sep"),
    SampleTask(4, "Review my expenses", progress = "0/3"),
    SampleTask(5, "Confirm the transfer with the bank", "10 Sep", "1/3", "Waiting for the bank to confirm the receiving account and international transfer details.", "Personal finances", "Jan 1, 2099"),
    SampleTask(6, "Prepare the supporting documents and check every detail before submitting the application", "12 Sep", "3/3", project = "Move to the new apartment"),
    SampleTask(7, "Water the plants", recurring = true),
    SampleTask(8, "Call Sam"),
    SampleTask(9, "Send the signed form", progress = "1/2", done = true),
)
private val names = listOf("A · Compact cards", "B · Divided list", "C · Expand on demand", "D · Current baseline", "E · Right handle", "F · Left grip", "G · Hold card")

@Composable
private fun Prototype(variant: Int, onVariant: (Int) -> Unit) {
    var dark by remember { mutableStateOf(true) }
    var tasks by remember { mutableStateOf(fixtures) }
    var detailId by remember { mutableStateOf<Int?>(null) }
    var inspector by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    TimeboxTheme(darkTheme = dark) {
        val colors = MaterialTheme.colorScheme
        val complete: (SampleTask) -> Unit = { task ->
            tasks = tasks.map { if (it.id == task.id) it.copy(done = !it.done, ready = false) else it }
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                if (snackbar.showSnackbar(if (task.done) "Task reopened" else "Task completed", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                    tasks = tasks.map { if (it.id == task.id) task else it }
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Battle Plan", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text("THROWAWAY · #65 · sample tasks", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(onClick = { dark = !dark }) { Icon(Icons.Outlined.Contrast, "Toggle light/dark theme") }
                    IconButton(onClick = { inspector = true }) { Icon(Icons.Outlined.Info, "Inspect prototype state") }
                }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("All tasks", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("${tasks.count { !it.done }} open · ${tasks.count { it.done }} completed", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                if (variant >= 4) DragCardBoard(tasks, { tasks = it }, variant, complete, { detailId = it }, Modifier.weight(1f))
                else BoxWithConstraints(Modifier.weight(1f).padding(top = 14.dp)) {
                    val wide = maxWidth >= 600.dp
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Text("TO DO", Modifier.padding(start = 4.dp, bottom = 12.dp), color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            tasks.filter { !it.done }.forEach { task ->
                                TaskRow(task, variant, task.id in expanded, { complete(task) }, { detailId = task.id }, {
                                    expanded = if (task.id in expanded) expanded - task.id else expanded + task.id
                                })
                            }
                            if (!wide) CompletedLane(tasks, variant, complete) { detailId = it }
                            Spacer(Modifier.height(16.dp))
                        }
                        if (wide) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            CompletedLane(tasks, variant, complete) { detailId = it }
                        }
                    }
                }
                SnackbarHost(snackbar)
                Surface(Modifier.align(Alignment.CenterHorizontally).padding(8.dp), shape = RoundedCornerShape(32.dp), color = colors.inverseSurface, shadowElevation = 8.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onVariant(variant - 1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous variant") }
                        Text(names[variant], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { onVariant(variant + 1) }) { Icon(Icons.Outlined.ChevronRight, "Next variant") }
                    }
                }
                Text("${'A' + variant} · ${tasks.count { it.done }} completed · ${expanded.size} expanded · in memory", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp), fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
        }
        val task = tasks.find { it.id == detailId }
        if (task != null) AlertDialog(
            onDismissRequest = { detailId = null },
            title = { Text(task.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (task.done) "Completed" else "Incomplete")
                    Text("Project: ${task.project ?: "None (Admin)"}")
                    Text("Deadline: ${task.due ?: "None"}")
                    Text("Subtasks: ${task.progress ?: "None"}")
                    Text("Blocked: ${task.blocker ?: "No"}")
                    Text("Priority: ${task.priority}")
                    Text("Recurring: ${if (task.recurring) "Yes" else "No"}")
                    Text("Planned Blocks: ${task.planned ?: "None"}")
                    if (!task.done) OutlinedButton(onClick = { tasks = tasks.map { if (it.id == task.id) it.copy(ready = !it.ready) else it } }) {
                        Text(if (task.ready) "Remove from Ready to Plan" else "Add to Ready to Plan")
                    }
                    Text("Sample data · changes are not saved", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { detailId = null }) { Text("Close") } },
        )
        if (inspector) AlertDialog(onDismissRequest = { inspector = false }, title = { Text("Prototype state") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${names[variant]}\nTheme: ${if (dark) "dark" else "light"}\nExpanded: $expanded\n")
                tasks.forEach { Text(it.toString(), Modifier.padding(bottom = 12.dp), fontSize = 11.sp) }
            }
        }, confirmButton = { TextButton(onClick = { inspector = false }) { Text("Close") } }, dismissButton = {
            TextButton(onClick = { tasks = fixtures; expanded = emptySet(); inspector = false }) { Text("Reset samples") }
        })
    }
}

@Composable
private fun CompletedLane(tasks: List<SampleTask>, variant: Int, complete: (SampleTask) -> Unit, open: (Int) -> Unit) {
    Text("COMPLETED", Modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    tasks.filter { it.done }.forEach { task -> TaskRow(task, variant, false, { complete(task) }, { open(task.id) }, {}) }
}

@Composable
internal fun TaskRow(task: SampleTask, variant: Int, expanded: Boolean, complete: () -> Unit, open: () -> Unit, toggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val metadata = task.due != null || task.progress != null || (task.blocker != null && !task.done)
    if (variant == 3) {
        Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = open), shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerLow, border = BorderStroke(1.dp, colors.outlineVariant)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (task.project != null) "PROJECT TASK" else "ADMIN TASK", Modifier.weight(1f), fontSize = 10.sp, color = colors.onSurfaceVariant)
                    if (task.blocker != null && !task.done) Text("BLOCKED", fontSize = 10.sp, color = colors.error)
                    Icon(Icons.Outlined.MoreVert, null, Modifier.size(24.dp))
                }
                Text(task.title, Modifier.padding(top = 18.dp), fontSize = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(task.project ?: "Admin", fontSize = 13.sp, color = colors.onSurfaceVariant)
                task.blocker?.takeIf { !task.done }?.let { Text("Blocker: $it", Modifier.padding(top = 9.dp), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(when { task.done -> "Completed"; task.planned != null -> "▣ Planned ${task.planned}"; task.ready -> "Ready to Plan"; else -> "+ Add to Ready to Plan" }, Modifier.padding(top = 14.dp).background(colors.secondaryContainer, RoundedCornerShape(6.dp)).padding(8.dp), color = colors.onSecondaryContainer, fontSize = 13.sp)
                if (metadata) Metadata(task, Modifier.padding(top = 8.dp))
            }
        }
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = if (variant == 1) 0.dp else 8.dp),
        shape = RoundedCornerShape(if (variant == 1) 0.dp else 11.dp),
        color = if (variant == 1) colors.background else colors.surfaceContainerLow,
        border = if (variant == 1) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = if (variant == 2) 2.dp else 6.dp), verticalAlignment = Alignment.Top) {
                IconButton(onClick = complete, modifier = Modifier.size(48.dp)) {
                    Icon(if (task.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        if (task.done) "Reopen ${task.title}" else "Complete ${task.title}", Modifier.size(21.dp), tint = colors.onSurfaceVariant)
                }
                Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 10.dp, end = if (variant == 0) 12.dp else 4.dp)) {
                    Text(task.title, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, maxLines = if (variant == 1) 1 else 2,
                        overflow = TextOverflow.Ellipsis, textDecoration = if (task.done) TextDecoration.LineThrough else null,
                        color = if (task.done) colors.onSurfaceVariant else colors.onSurface)
                    if (variant == 0 && metadata) Metadata(task, Modifier.padding(top = 7.dp))
                    if (variant == 1 && (task.progress != null || task.blocker != null)) Metadata(task.copy(due = null), Modifier.padding(top = 5.dp))
                    if (variant == 2 && task.blocker != null && !task.done) Text("Blocked", fontSize = 11.sp, color = colors.error)
                }
                if (variant == 1 && task.due != null) Text(task.due.substringBefore(" ·"), Modifier.padding(top = 14.dp, end = 8.dp), fontSize = 12.sp, color = if ("overdue" in task.due) colors.error else colors.onSurfaceVariant)
                if (variant == 2) IconButton(onClick = toggle, Modifier.size(48.dp)) { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse metadata" else "Expand metadata") }
            }
            if (variant == 2 && expanded) Column(Modifier.padding(start = 48.dp, bottom = 12.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Metadata(task)
                Text(when { task.done -> "Completed"; task.planned != null -> "Planned: ${task.planned}"; task.ready -> "Ready to Plan"; else -> "No Planned Blocks" }, fontSize = 12.sp, color = colors.onSurfaceVariant)
                task.blocker?.takeIf { !task.done }?.let { Text(it, fontSize = 12.sp, color = colors.onSurfaceVariant) }
            }
            if (variant == 1) HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Metadata(task: SampleTask, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (task.blocker != null && !task.done) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Block, "Blocked", Modifier.size(13.dp), tint = colors.error)
            Text(" Blocked", fontSize = 11.sp, color = colors.error)
        }
        task.progress?.let { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Checklist, "Subtask progress", Modifier.size(14.dp), tint = colors.onSurfaceVariant)
            Text(" $it", fontSize = 11.sp, color = colors.onSurfaceVariant)
        } }
        task.due?.let { Row(verticalAlignment = Alignment.CenterVertically) {
            val tint = if ("overdue" in it && !task.done) colors.error else colors.onSurfaceVariant
            Icon(Icons.Outlined.CalendarToday, "Deadline", Modifier.size(12.dp), tint = tint)
            Text(" $it", fontSize = 11.sp, color = tint)
        } }
    }
}
