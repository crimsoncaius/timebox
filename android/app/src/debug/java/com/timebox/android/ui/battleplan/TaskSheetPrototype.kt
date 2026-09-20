package com.timebox.android.ui.battleplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.*
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sheetDateFormat = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
private fun sheetDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.substringBefore(" · "), sheetDateFormat) }.getOrNull()
private fun sheetTime(value: String): LocalTime? = if (" · " in value) runCatching { LocalTime.parse(value.substringAfter(" · ")) }.getOrNull() else null
private fun sheetDateLabel(date: LocalDate, time: LocalTime?): String = date.format(sheetDateFormat) + (time?.let { " · ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}" } ?: "")
private fun deadlineBoundary(value: String): LocalDateTime? = sheetDate(value)?.let { date -> sheetTime(value)?.let { date.atTime(it) } ?: date.plusDays(1).atStartOfDay() }

// Issue 97: an isolated, local-state design experiment, reachable only by a debug route.
private data class SheetTask(
    val title: String = "Prepare the Japan itinerary",
    val description: String = "Keep the first two days relaxed. Compare Kyoto stays near the station.",
    val project: String = "Japan trip",
    val deadline: String = "18 Sep 2026",
    val ready: Boolean = true,
    val completed: Boolean = false,
    val importance: String = "High",
    val urgency: String = "Not set",
    val reminder: String = "None",
    val type: String = "Planning",
    val subtasks: List<Pair<String, Boolean>> = listOf("Choose the neighbourhood" to true, "Shortlist three places to stay" to false, "Check train connections" to false),
)

private fun longSheetSample() = SheetTask(
    title = "Prepare a flexible Japan itinerary for everyone, including rainy-day alternatives and accessible train connections",
    description = "Keep the first two days relaxed after the overnight flight. Compare Kyoto stays near the station, including step-free access and a quiet room.\n\nCheck each booking's cancellation window before paying. Keep the confirmation number, arrival instructions and contact details together so someone else can take over if needed.\n\nPlan one main activity per day and keep a nearby indoor alternative. Leave enough time for meals and unplanned stops. Review the final route with everyone before booking the remaining trains.",
    project = "Japan trip · travel and accommodation planning",
    subtasks = listOf(
        "Choose the neighbourhood and check walking distances from the nearest accessible station",
        "Shortlist three places to stay, including cancellation terms and late check-in arrangements",
        "Check train connections and allow time for changing platforms with luggage",
        "Confirm passport validity", "Compare travel insurance", "Book the first two nights",
        "Check dietary requirements", "Save offline maps", "Choose rainy-day alternatives",
        "Share the draft itinerary", "Confirm the airport transfer", "Download booking confirmations",
    ).mapIndexed { index, title -> title to (index < 3) },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskSheetPrototype(startCreating: Boolean = false, initialLayout: String = "full", initialSample: String = "normal") {
    var saved by remember { mutableStateOf(if (initialSample == "long") longSheetSample() else SheetTask()) }
    var draft by remember { mutableStateOf(SheetTask(title = "", description = "", deadline = "", ready = false, importance = "Not set", type = "None", subtasks = emptyList())) }
    var creating by remember { mutableStateOf(startCreating) }
    var visible by remember { mutableStateOf(true) }
    var editor by remember { mutableStateOf<String?>(null) }
    var editorValue by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    var fieldDiscard by remember { mutableStateOf(false) }
    val compactEntry = initialLayout == "compact"
    var expandedEntry by remember { mutableStateOf(false) }
    var fullDescription by remember { mutableStateOf(false) }
    val compact = creating && compactEntry && !expandedEntry
    val keyboardVisible = WindowInsets.isImeVisible
    val colors = TimeboxTheme.colors
    val focus = LocalFocusManager.current
    val task = if (creating) draft else saved
    fun update(value: SheetTask) { if (creating) draft = value else saved = value }
    fun edit(field: String, value: String) { focus.clearFocus(); search = ""; editorValue = value; editor = field }
    fun close() {
        focus.clearFocus()
        if (creating && draft.title.isNotBlank()) discard = true
        else visible = false
    }
    val fieldOriginal = when (editor) {
        "Title" -> task.title
        "Description" -> task.description
        "Deadline" -> task.deadline
        "Reminder" -> task.reminder
        "Add subtask" -> ""
        else -> null
    }
    val fieldDirty = fieldOriginal != null && editorValue != fieldOriginal
    fun closeEditor() { if (fieldDirty) fieldDiscard = true else { focus.clearFocus(); editor = null } }
    val now = Instant.parse("2026-09-14T09:00:00Z")
    val project = Project(1, saved.project, now, now)
    val sample = BattleTask(
        id = 9701, parentId = null, parentTitle = null, projectId = 1, project = project,
        taskTypeId = null, taskType = null, recurringTemplateId = null, recurringTemplateTitle = null,
        occurrenceKey = null, recurrenceKind = null, quotaPeriodStart = null, quotaPeriodEnd = null,
        expectedSessions = null, sessionIndex = null, quotaCompleted = null,
        title = saved.title, description = saved.description, readyToPlan = saved.ready,
        status = if (saved.completed) TaskStatus.Completed else TaskStatus.Open,
        urgency = PriorityLevel.entries.firstOrNull { it.name == saved.urgency }, importance = PriorityLevel.entries.firstOrNull { it.name == saved.importance },
        deadlineDate = sheetDate(saved.deadline).takeIf { sheetTime(saved.deadline) == null },
        deadlineAt = sheetDate(saved.deadline)?.let { date -> sheetTime(saved.deadline)?.let { date.atTime(it).atZone(ZoneId.of("Asia/Singapore")).toInstant() } },
        reminderAt = sheetDate(saved.reminder)?.let { date -> sheetTime(saved.reminder)?.let { date.atTime(it).atZone(ZoneId.of("Asia/Singapore")).toInstant() } }, reminderDeliveredAt = null, position = 0,
        archivedAt = null, deletedAt = null, createdAt = now, updatedAt = now, overdue = false,
        subtasks = saved.subtasks.mapIndexed { i, value -> Subtask(i + 1, 9701, value.first, value.second, value.second || saved.completed, i, now, now) },
    )
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("TASK SHEET · PROTOTYPE", fontSize = 10.sp, color = colors.onVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { creating = false; visible = true }) { Text("Details") }
            TextButton(onClick = { creating = true; visible = true }) { Text("New task") }
        }
        Text("Battle Plan", fontSize = 30.sp, modifier = Modifier.padding(20.dp, 8.dp), color = colors.on)
        BattlePlanScreen(
            state = BattlePlanUiState(loading = false, projects = listOf(project), tasks = listOf(sample), serverNow = now),
            onRetry = {}, onSelectScope = {}, onSelectStatus = {}, onToggleUrgency = {}, onToggleImportance = {},
            onToggleTaskType = {}, onClearFilters = {}, onOpenTask = { creating = false; visible = true },
            onToggleReady = { saved = saved.copy(ready = !saved.ready) },
            onMoveTask = { _, status -> saved = saved.copy(completed = status == TaskStatus.Completed) },
            onCreateTask = { _, _, _ -> creating = true; visible = true },
            onShowComposer = { if (it) { creating = true; visible = true } }, onOpenRecurring = {}, onNewProject = {},
            onPrepareDeleteProject = {}, onDismissDeleteProject = {}, onConfirmDeleteProject = {},
            onRestoreArchived = {}, onRestoreTrashed = {}, onUndoTrash = {}, onDismissUndo = {},
            onRequestPermanentDelete = {}, onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
        )
    }
    if (visible) ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { target ->
            if (target == SheetValue.Hidden && creating && draft.title.isNotBlank()) {
                discard = true
                false
            } else true
        }),
        containerColor = colors.sheet.copy(alpha = 1f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().then(if (compact) Modifier.heightIn(max = 600.dp) else Modifier.fillMaxHeight(if (keyboardVisible) .94f else .78f))) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, null, tint = colors.project, modifier = Modifier.size(18.dp))
                TextButton(onClick = { edit("Project", task.project) }, modifier = Modifier.weight(1f)) {
                    Text(task.project, modifier = Modifier.weight(1f), color = colors.project)
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
                IconButton(onClick = ::close) { Icon(Icons.Outlined.Close, "Close task") }
            }
            Column(Modifier.weight(1f, fill = !compact).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    IconButton(onClick = { update(task.copy(completed = !task.completed, ready = false)) }, enabled = !creating) {
                        Icon(if (task.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            if (task.completed) "Reopen task" else "Complete task", tint = if (task.completed) colors.project else colors.onVariant, modifier = Modifier.size(28.dp))
                    }
                    if (creating) BasicTextField(value = task.title, onValueChange = { update(task.copy(title = it)) },
                        textStyle = TextStyle(color = colors.on, fontSize = 25.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(colors.on), modifier = Modifier.weight(1f).padding(top = 7.dp, bottom = 14.dp),
                        decorationBox = { inner -> if (task.title.isEmpty()) Text("What needs doing?", color = colors.onVariant, fontSize = 25.sp); inner() })
                    else Text(task.title, color = colors.on, fontSize = 25.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).clickable { edit("Title", task.title) }.padding(top = 7.dp, bottom = 14.dp))
                }
                if (compact) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SheetChip(Icons.Outlined.CalendarToday, task.deadline.ifBlank { "Deadline" }) { edit("Deadline", task.deadline) }
                        SheetChip(Icons.Outlined.Notes, if (task.description.isBlank()) "Description" else "Description added") { edit("Description", task.description) }
                    }
                    TextButton(onClick = { focus.clearFocus(); expandedEntry = true }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("More details")
                    }
                } else {
                if (creating && compactEntry) TextButton(onClick = { focus.clearFocus(); expandedEntry = false }) { Text("Fewer details") }
                if (task.description.isNotEmpty()) {
                    Text(task.description, color = colors.onVariant, fontSize = 15.sp, lineHeight = 22.sp,
                        maxLines = if (fullDescription || task.description.length < 220) Int.MAX_VALUE else 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { edit("Description", task.description) }.padding(start = 48.dp, bottom = 8.dp))
                    if (task.description.length >= 220) TextButton(onClick = { fullDescription = !fullDescription }, modifier = Modifier.padding(start = 36.dp)) {
                        Text(if (fullDescription) "Show less" else "Read full description")
                    }
                }
                HorizontalDivider(color = colors.hairline)
                SheetRow(Icons.Outlined.CalendarToday, if (task.deadline.isBlank()) "Add deadline" else task.deadline, "Deadline") { edit("Deadline", task.deadline) }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EventAvailable, null, Modifier.size(21.dp), tint = colors.onVariant)
                    Text("Ready to Plan", color = colors.on, modifier = Modifier.weight(1f).padding(start = 16.dp), fontSize = 15.sp)
                    Switch(checked = task.ready, onCheckedChange = { update(task.copy(ready = it)) }, enabled = !task.completed)
                }
                HorizontalDivider(color = colors.hairline)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.description.isEmpty()) SheetChip(Icons.Outlined.Notes, "Description") { edit("Description", task.description) }
                    SheetChip(Icons.Outlined.Flag, "Importance") { edit("Importance", "") }
                    SheetChip(Icons.Outlined.Schedule, "Urgency") { edit("Urgency", "") }
                    SheetChip(Icons.Outlined.NotificationsNone, if (task.reminder == "None") "Reminder" else task.reminder) { edit("Reminder", task.reminder) }
                    SheetChip(Icons.Outlined.Label, if (task.type == "None") "Task type" else task.type) { edit("Task type", task.type) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtasks", fontWeight = FontWeight.Medium, color = colors.on, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (task.subtasks.isNotEmpty()) Text("${task.subtasks.count { it.second }} / ${task.subtasks.size}", color = colors.onVariant, fontSize = 12.sp)
                }
                task.subtasks.forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.second, enabled = !task.completed, onCheckedChange = { checked -> update(task.copy(subtasks = task.subtasks.mapIndexed { index, pair -> if (index == i) pair.first to checked else pair })) })
                        Text(item.first, fontSize = 15.sp, color = if (item.second) colors.onVariant else colors.on,
                            textDecoration = if (item.second) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
                    }
                }
                TextButton(onClick = { edit("Add subtask", "") }, enabled = !task.completed) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Add subtask")
                }
                Spacer(Modifier.height(16.dp))
                }
            }
            if (creating) Button(onClick = { focus.clearFocus(); saved = draft; creating = false }, enabled = task.title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(20.dp, 12.dp).height(48.dp)) { Text("Add task") }
            if (!keyboardVisible && !creating) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LOAD SAMPLE", fontSize = 9.sp, color = colors.onVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { focus.clearFocus(); saved = SheetTask(); fullDescription = false }) { Text("Normal", fontSize = 12.sp) }
                TextButton(onClick = { focus.clearFocus(); saved = longSheetSample(); fullDescription = false }) { Text("Long content", fontSize = 12.sp) }
            }
            if (!keyboardVisible) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LOCAL PROTOTYPE", fontSize = 9.sp, color = colors.onVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { focus.clearFocus(); creating = !creating }) { Text(if (creating) "Try details" else "Try task entry", fontSize = 12.sp) }
                TextButton(onClick = { focus.clearFocus(); saved = SheetTask(); draft = SheetTask(title = "", description = "", deadline = "", ready = false, importance = "Not set", type = "None", subtasks = emptyList()) }) { Text("Reset", fontSize = 12.sp) }
            }
        }
    }
    if (editor != null) ModalBottomSheet(
        onDismissRequest = ::closeEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
            if (it == SheetValue.Hidden && fieldDirty) { fieldDiscard = true; false } else true
        }),
        containerColor = colors.surf,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editor == "Project") "Move to project" else editor!!, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = colors.on, modifier = Modifier.weight(1f))
                IconButton(onClick = ::closeEditor) { Icon(Icons.Outlined.Close, "Close ${editor}") }
            }
            when (editor) {
                "Deadline", "Reminder" -> ScheduleFieldEditor(
                    reminder = editor == "Reminder", value = editorValue,
                    deadline = task.deadline, existingReminder = task.reminder,
                    onChange = { editorValue = it },
                    onSave = {
                        update(if (editor == "Deadline") task.copy(deadline = editorValue, reminder = if (editorValue.isBlank()) "None" else task.reminder)
                            else task.copy(reminder = editorValue))
                        editor = null
                    },
                    creating = creating,
                )
                "Importance", "Urgency" -> {
                    val importance = editor == "Importance"
                    PriorityChoices(editor!!, if (importance) task.importance else task.urgency) {
                        update(if (importance) task.copy(importance = it) else task.copy(urgency = it))
                    }
                    Button(onClick = { editor = null }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done") }
                }
                "Project", "Task type" -> {
                    val isProject = editor == "Project"
                    val options = if (isProject) listOf("Admin", "Japan trip", "Home", "Website refresh", "Reading list", "Studio setup") else listOf("None", "Planning", "Research", "Errands")
                    OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                        placeholder = { Text(if (isProject) "Search projects" else "Search task types") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth())
                    Column(Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val matches = options.filter { it.contains(search.trim(), ignoreCase = true) }
                        if (matches.isEmpty()) Text("No matches. Try another search.", color = colors.onVariant, modifier = Modifier.padding(vertical = 20.dp))
                        matches.forEach { option ->
                            val chosen = option == if (isProject) task.project else task.type
                            Surface(selected = chosen, onClick = { focus.clearFocus(); update(if (isProject) task.copy(project = option) else task.copy(type = option)); editor = null },
                                shape = RoundedCornerShape(12.dp), color = if (chosen) colors.selected else colors.low,
                                modifier = Modifier.fillMaxWidth().semantics { selected = chosen }) {
                                Row(Modifier.padding(16.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Icon(if (isProject) Icons.Outlined.Folder else Icons.Outlined.Label, null, tint = if (isProject) colors.project else colors.onVariant, modifier = Modifier.size(22.dp))
                                    Text(option, color = colors.on, modifier = Modifier.weight(1f))
                                    if (chosen) Icon(Icons.Outlined.Check, "Selected", Modifier.size(20.dp), tint = colors.on)
                                }
                            }
                        }
                    }
                }
                "Add subtask" -> {
                    val inputFocus = remember { FocusRequester() }
                    fun add() {
                        if (editorValue.isNotBlank()) {
                            update(task.copy(subtasks = task.subtasks + (editorValue.trim() to false)))
                            editorValue = ""
                        }
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = colors.low) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            BasicTextField(value = editorValue, onValueChange = { editorValue = it }, textStyle = TextStyle(color = colors.on, fontSize = 20.sp),
                                cursorBrush = SolidColor(colors.on), singleLine = true,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRequester(inputFocus).semantics { contentDescription = "Subtask name" },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { add() }),
                                decorationBox = { inner -> if (editorValue.isEmpty()) Text("Subtask name", color = colors.onVariant, fontSize = 20.sp); inner() })
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.SubdirectoryArrowRight, null, tint = colors.onVariant)
                                Text(task.title.ifBlank { "New task" }, maxLines = 2, fontSize = 13.sp, color = colors.onVariant, modifier = Modifier.weight(1f))
                                FilledIconButton(onClick = { add() }, enabled = editorValue.isNotBlank()) { Icon(Icons.Outlined.ArrowUpward, "Add subtask") }
                            }
                        }
                    }
                    Text("${task.subtasks.size} subtasks · Add another or close to return", color = colors.onVariant, fontSize = 12.sp)
                    LaunchedEffect(Unit) { inputFocus.requestFocus() }
                }
                else -> {
                    OutlinedTextField(value = editorValue, onValueChange = { editorValue = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (editor == "Deadline") "Date (e.g. 18 Sep 2026)" else editor!!) })
                    Button(onClick = {
                        update(when (editor) {
                            "Title" -> task.copy(title = editorValue.trim())
                            "Description" -> task.copy(description = editorValue)
                            else -> task
                        }); editor = null
                    }, enabled = editor != "Title" || editorValue.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(if (creating) "Apply to draft" else "Save field") }
                }
            }
        }
    }
    if (fieldDiscard) AlertDialog(onDismissRequest = { fieldDiscard = false }, title = { Text("Discard this field edit?") },
        text = { Text("This field change has not been saved. Other changes are kept.") },
        confirmButton = { TextButton(onClick = { fieldDiscard = false; focus.clearFocus(); editor = null }) { Text("Discard field edit") } },
        dismissButton = { TextButton(onClick = { fieldDiscard = false }) { Text("Keep editing") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Keep this draft?") },
        text = { Text("Your draft stays available in this prototype until you reset it.") },
        confirmButton = { TextButton(onClick = { discard = false; visible = false }) { Text("Keep draft and close") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Continue editing") } })
}

@Composable
private fun ScheduleFieldEditor(
    reminder: Boolean,
    value: String,
    deadline: String,
    existingReminder: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    creating: Boolean,
) {
    val colors = TimeboxTheme.colors
    val context = LocalContext.current
    val today = LocalDate.now(ZoneId.of("Asia/Singapore"))
    val date = sheetDate(value)
    val time = sheetTime(value)
    val enabled = date != null
    val fallback = sheetDate(deadline) ?: today
    val selectedDate = date ?: if (reminder) fallback else today
    val selectedTime = time ?: LocalTime.of(9, 0)
    val reminderValue = if (reminder) value else existingReminder
    val reminderAt = sheetDate(reminderValue)?.let { day -> sheetTime(reminderValue)?.let { day.atTime(it) } }
    val boundary = deadlineBoundary(if (reminder) deadline else value)
    val invalid = enabled && reminderAt != null && boundary != null && !reminderAt.isBefore(boundary)
    val missingDeadline = reminder && deadline.isBlank()
    fun changeDate(next: LocalDate) { onChange(sheetDateLabel(next, if (reminder) selectedTime else time)) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (missingDeadline) Text("Set a deadline before adding a reminder.", color = colors.onVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (reminder) "Remind me" else "Set a deadline", color = colors.on, modifier = Modifier.weight(1f))
            Switch(checked = enabled, enabled = !missingDeadline, onCheckedChange = {
                onChange(if (!it) { if (reminder) "None" else "" }
                else if (reminder) {
                    val initial = if (sheetTime(deadline) != null) deadlineBoundary(deadline)!!.minusHours(1) else fallback.atTime(9, 0)
                    sheetDateLabel(initial.toLocalDate(), initial.toLocalTime())
                } else sheetDateLabel(today, null))
            })
        }
        if (enabled) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1)).forEach { (label, day) ->
                    FilterChip(selected = date == day, onClick = { changeDate(day) }, label = { Text(label) })
                }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = colors.low) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SheetRow(Icons.Outlined.CalendarToday, selectedDate.format(sheetDateFormat), "Choose date") {
                        android.app.DatePickerDialog(context, { _, year, month, day -> changeDate(LocalDate.of(year, month + 1, day)) }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
                    }
                    if (!reminder) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Include a time", color = colors.on, modifier = Modifier.weight(1f))
                        Switch(checked = time != null, onCheckedChange = { onChange(sheetDateLabel(selectedDate, if (it) selectedTime else null)) })
                    }
                    if (reminder || time != null) SheetRow(Icons.Outlined.Schedule, selectedTime.toString(), "Choose time") {
                        android.app.TimePickerDialog(context, { _, hour, minute -> onChange(sheetDateLabel(selectedDate, LocalTime.of(hour, minute))) }, selectedTime.hour, selectedTime.minute, true).show()
                    }
                }
            }
            Text("Asia/Singapore", color = colors.onVariant, fontSize = 12.sp)
        }
        if (invalid) Text(if (reminder) "Choose a reminder before the deadline." else "This deadline is earlier than the current reminder. Change or remove the reminder first.", color = colors.error)
        if (!reminder && !enabled && existingReminder != "None") Text("Removing this deadline also removes its reminder.", color = colors.onVariant)
        if (reminder) Text("Prototype only · no notification will be sent.", color = colors.onVariant, fontSize = 12.sp)
        Button(onClick = onSave, enabled = !invalid && !missingDeadline,
            modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(if (creating) "Apply to draft" else if (!enabled) { if (reminder) "Remove reminder" else "Remove deadline" } else if (reminder) "Save reminder" else "Save deadline")
        }
    }
}

@Composable
private fun PriorityChoices(label: String, value: String, onSelect: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = colors.onVariant, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("High", "Medium", "Low", "Not set").forEach { level ->
                val chosen = value == level
                Surface(selected = chosen, onClick = { onSelect(level) }, modifier = Modifier.weight(1f).semantics { contentDescription = "$label: $level" },
                    shape = RoundedCornerShape(14.dp), color = if (chosen) colors.selected else colors.low,
                    border = BorderStroke(1.dp, if (chosen) colors.on else colors.outlineVariant)) {
                    Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (chosen) Icons.Outlined.CheckCircle else Icons.Outlined.Flag, null,
                            tint = when (level) { "High" -> colors.error; "Medium" -> colors.tertiary; "Low" -> colors.planned; else -> colors.onVariant }, modifier = Modifier.size(24.dp))
                        Text(level, color = colors.on, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, value: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(21.dp), tint = colors.onVariant)
        Text(value, color = colors.on, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
        Text(label, color = colors.onVariant, fontSize = 12.sp)
    }
}

@Composable
private fun SheetChip(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(onClick = onClick, enabled = enabled, color = colors.high, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.onVariant)
            Text(label, color = colors.on, fontSize = 13.sp)
        }
    }
}
