package com.timebox.android.ui.battleplan.prototype

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.timebox.android.data.*
import com.timebox.android.ui.battleplan.*
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.LocalDate

/** PROTOTYPE #89: three native mobile move flows inside the real Battle Plan.
 * timebox://prototype/move-project?variant=A (B / C). Memory-only fixture data.
 * Question: sheet, inline expansion, or dedicated picker for moving a task?
 */
class MoveProjectPrototypeActivity : ComponentActivity() {
    private var variant by mutableStateOf("A")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        variant = savedInstanceState?.getString("variant") ?: intent.data?.getQueryParameter("variant") ?: "A"
        setContent { TimeboxTheme { key(variant) { MovePrototype(variant, ::changeVariant) } } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("variant", variant)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        variant = intent.data?.getQueryParameter("variant") ?: "A"
    }
    private fun changeVariant(value: String) {
        variant = value
        setIntent(Intent(intent).setData(Uri.parse("timebox://prototype/move-project?variant=$value")))
    }
}

private val now = Instant.parse("2026-09-07T06:00:00Z")
private val projects = listOf("Website refresh", "Studio operations", "Personal", "Learning", "Autumn product launch", "Home improvements", "Travel planning", "Community workshop").mapIndexed { i, name -> Project(i + 1, name, now, now) }
private fun fixture(id: Int, title: String) = BattleTask(
    id = id, parentId = null, parentTitle = null, projectId = 1, project = projects[0],
    taskTypeId = null, taskType = null, recurringTemplateId = null, recurringTemplateTitle = null,
    occurrenceKey = null, recurrenceKind = null, quotaPeriodStart = null, quotaPeriodEnd = null,
    expectedSessions = null, sessionIndex = null, quotaCompleted = null, title = title,
    description = "Review the draft, capture feedback, and prepare the next iteration.",
    readyToPlan = true, status = TaskStatus.Open, urgency = null, importance = PriorityLevel.High,
    deadlineDate = LocalDate.parse("2026-09-11"), deadlineAt = null, reminderAt = null,
    reminderDeliveredAt = null, position = id, archivedAt = null, deletedAt = null,
    createdAt = now, updatedAt = now, overdue = false,
    subtasks = listOf(Subtask(id * 10, id, "Review first draft", true, true, 0, now, now), Subtask(id * 10 + 1, id, "Share feedback", false, false, 1, now, now)),
    plannedDates = listOf(LocalDate.parse("2026-09-08")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovePrototype(variant: String, changeVariant: (String) -> Unit) {
    var tasks by remember { mutableStateOf(listOf(fixture(1, "Review the homepage concepts"), fixture(2, "Prepare launch checklist"), fixture(3, "Collect customer feedback"))) }
    var scope by remember { mutableStateOf(BattlePlanScope.project(projects[0])) }
    var status by remember { mutableStateOf(TaskStatus.Open) }
    var task by remember { mutableStateOf<BattleTask?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var fail by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var movedTo by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf(false) }
    val open: (BattleTask) -> Unit = { task = it; selected = it.projectId; query = ""; error = false; notice = "" }
    val close = { task = null; error = false; query = "" }
    val confirm = {
        if (fail) { error = true; fail = false } else {
            val moving = task!!
            tasks = tasks.map { if (it.id == moving.id) it.copy(projectId = selected, project = projects.find { p -> p.id == selected }) else it }
            movedTo = selected
            notice = "${moving.title} moved to ${name(selected)}"
            close()
        }
    }
    LaunchedEffect(variant, task, selected, error, tasks) {
        Log.d("MovePrototype", "variant=$variant task=${task?.id} current=${task?.projectId} destination=$selected error=$error assignments=${tasks.map { it.id to it.projectId }}")
    }
    BackHandler(task != null) { close() }
    val cycle: (Int) -> Unit = { delta -> changeVariant(listOf("A", "B", "C")[(listOf("A", "B", "C").indexOf(variant) + delta + 3) % 3]) }
    val switcher: @Composable () -> Unit = {
        PrototypeSwitcher(variant, cycle, fail, { fail = it })
    }
    val picker: @Composable (Boolean) -> Unit = { compact ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!compact) {
                Text("Move to project", style = TimeboxTheme.type.screenTitle)
                Text(task?.title.orEmpty(), style = TimeboxTheme.type.body)
            }
            Text("From ${name(task?.projectId)}", color = TimeboxTheme.colors.onVariant, style = TimeboxTheme.type.bodySmall)
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Find a project or Admin") }, modifier = Modifier.fillMaxWidth().onFocusChanged { editing = it.isFocused })
            Column(Modifier.fillMaxWidth().heightIn(max = if (compact) 170.dp else 260.dp).verticalScroll(rememberScrollState())) {
                val options = (listOf(null) + projects.map { it.id }).filter { name(it).contains(query, ignoreCase = true) }
                if (options.isEmpty()) Text("No destinations found", Modifier.padding(16.dp))
                options.forEach { id ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected == id, enabled = id != task?.projectId, role = Role.RadioButton, onClick = { selected = id; error = false }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == id, onClick = null, enabled = id != task?.projectId)
                        Text(name(id), Modifier.weight(1f), style = TimeboxTheme.type.body)
                        if (id == task?.projectId) Text("Current", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                    }
                }
            }
            if (error) Text("Couldn't move task. It's still in ${name(task?.projectId)}. Try again.", color = TimeboxTheme.colors.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            if (selected != task?.projectId) Text("${name(task?.projectId)} → ${name(selected)}", style = TimeboxTheme.type.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = close) { Text("Cancel") }
                Button(onClick = confirm, enabled = selected != task?.projectId, modifier = Modifier.weight(1f)) { Text(if (error) "Retry move" else if (selected == task?.projectId) "Choose destination" else "Move to ${name(selected)}") }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(TimeboxTheme.colors.bg).statusBarsPadding().navigationBarsPadding().onPreviewKeyEvent {
        if (!editing && it.type == KeyEventType.KeyUp && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight)) { cycle(if (it.key == Key.DirectionLeft) -1 else 1); true } else false
    }) {
        Column(Modifier.fillMaxSize().padding(bottom = 100.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Battle Plan", style = TimeboxTheme.type.screenTitle, modifier = Modifier.weight(1f))
                Text("Mobile prototype", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            }
            if (notice.isNotEmpty()) Surface(color = TimeboxTheme.colors.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(notice, style = TimeboxTheme.type.body)
                    TextButton(onClick = { scope = movedTo?.let { id -> BattlePlanScope.project(projects.first { it.id == id }) } ?: BattlePlanScope.Admin; notice = "" }) { Text("View ${name(movedTo)}") }
                }
            }
            CompositionLocalProvider(LocalPrototypeMove provides open, LocalPrototypeCard provides { card ->
                if (variant == "B" && task?.id == card.id) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    picker(true)
                } else {
                    TextButton(onClick = { open(card) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(when (variant) { "B" -> "${name(card.projectId)}  ▾  Change project"; "C" -> "Move task  →"; else -> "Move to project  ↗" })
                    }
                }
            }) {
                BattlePlanScreen(
                    state = BattlePlanUiState(loading = false, projects = projects, tasks = tasks, selectedScope = scope, selectedStatus = status, serverNow = now, timezone = "Asia/Singapore"),
                    onRetry = {}, onSelectScope = { scope = it }, onSelectStatus = { status = it },
                    onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {}, onClearFilters = {},
                    onOpenTask = { id -> open(tasks.first { it.id == id }) }, onToggleReady = {}, onMoveTask = { _, _ -> },
                    onReorderTask = { _, _ -> }, onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {}, onOpenRecurring = {}, onNewProject = {},
                    onPrepareDeleteProject = {}, onDismissDeleteProject = {}, onConfirmDeleteProject = {},
                    onRestoreArchived = {}, onRestoreTrashed = {}, onUndoTrash = {}, onDismissUndo = {},
                    onRequestPermanentDelete = {}, onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }
        if (task != null && variant == "C") VariantC({
            if (selected == task?.projectId) picker(false) else {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("Review move", style = TimeboxTheme.type.screenTitle)
                    Text(task?.title.orEmpty(), style = TimeboxTheme.type.body)
                    Surface(color = TimeboxTheme.colors.low, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("FROM  ${name(task?.projectId)}", style = TimeboxTheme.type.bodySmall)
                            Text("↓", style = TimeboxTheme.type.screenTitle)
                            Text(name(selected), style = TimeboxTheme.type.screenTitle)
                            TextButton(onClick = { selected = task?.projectId; error = false }) { Text("Change destination") }
                        }
                    }
                    Text("Your planned dates, subtasks and deadline stay with this task.", style = TimeboxTheme.type.body)
                    if (error) Text("Couldn't move task. It's still in ${name(task?.projectId)}.", color = TimeboxTheme.colors.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                    Button(onClick = confirm, modifier = Modifier.fillMaxWidth()) { Text(if (error) "Retry move" else "Confirm move") }
                    TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }, close)
        Box(Modifier.align(Alignment.BottomCenter)) { switcher() }
    }
    if (task != null && variant == "A") VariantA(picker, close, switcher)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantA(picker: @Composable (Boolean) -> Unit, close: () -> Unit, switcher: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) { picker(false); Spacer(Modifier.height(16.dp)); switcher() }
    }
}

@Composable
private fun VariantC(picker: @Composable (Boolean) -> Unit, close: () -> Unit) {
    Surface(Modifier.fillMaxSize().padding(bottom = 100.dp), color = TimeboxTheme.colors.bg) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = close) { Text("‹  Battle Plan") }
            Text("Give this task a new home", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            picker(false)
        }
    }
}

private fun name(id: Int?) = projects.find { it.id == id }?.name ?: "Admin"

@Composable
private fun PrototypeSwitcher(variant: String, cycle: (Int) -> Unit, fail: Boolean, setFail: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = TimeboxTheme.colors.on, contentColor = TimeboxTheme.colors.bg, shadowElevation = 8.dp, modifier = Modifier.padding(8.dp).widthIn(max = 400.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { cycle(-1) }) { Text("←", color = TimeboxTheme.colors.bg, modifier = Modifier.semantics { contentDescription = "Previous variant" }) }
                Text("$variant · ${when (variant) { "B" -> "Inline card"; "C" -> "Full-screen picker"; else -> "Bottom sheet" }}", style = TimeboxTheme.type.body)
                IconButton(onClick = { cycle(1) }) { Text("→", color = TimeboxTheme.colors.bg, modifier = Modifier.semantics { contentDescription = "Next variant" }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SAMPLE DATA · Fail next move", style = TimeboxTheme.type.bodySmall)
                Checkbox(checked = fail, onCheckedChange = setFail, modifier = Modifier.size(40.dp))
            }
        }
    }
}
