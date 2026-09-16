package com.timebox.android.ui.battleplan

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.*
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class TaskSheetField(val label: String) {
    Title("Title"), Description("Description"), Project("Project"), TaskType("Task type"),
    Importance("Importance"), Urgency("Urgency"), Deadline("Deadline"), Reminder("Reminder"), Status("Status"),
}

internal fun taskSheetHeightFraction(imeVisible: Boolean): Float = 0.94f

/** Only the open field can change; other values may have advanced while it was open. */
internal fun mergeTaskField(field: TaskSheetField, current: TaskDetailDraft, edited: TaskDetailDraft): TaskDetailDraft = when (field) {
    TaskSheetField.Title -> current.copy(title = edited.title)
    TaskSheetField.Description -> current.copy(description = edited.description)
    TaskSheetField.Project -> current.copy(projectId = edited.projectId)
    TaskSheetField.TaskType -> current.copy(taskTypeId = edited.taskTypeId)
    TaskSheetField.Importance -> current.copy(importance = edited.importance)
    TaskSheetField.Urgency -> current.copy(urgency = edited.urgency)
    TaskSheetField.Status -> current.copy(status = edited.status)
    TaskSheetField.Deadline -> current.copy(deadlineMode = edited.deadlineMode, deadlineDate = edited.deadlineDate,
        deadlineTime = edited.deadlineTime, reminderEnabled = current.reminderEnabled && edited.deadlineMode != TaskDeadlineMode.None)
    TaskSheetField.Reminder -> current.copy(reminderEnabled = edited.reminderEnabled, reminderDate = edited.reminderDate, reminderTime = edited.reminderTime)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TaskFieldsSheet(
    draft: TaskDetailDraft,
    projects: List<Project>,
    taskTypes: List<TaskType>,
    timezone: String,
    today: LocalDate,
    creating: Boolean,
    saving: Boolean,
    dirty: Boolean,
    error: String?,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onChange: (TaskDetailDraft) -> Unit,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onRetrySave: () -> Unit,
    onCreate: () -> Unit = {},
    onComplete: () -> Unit = {},
    onReady: (Boolean) -> Unit = { onChange(draft.copy(readyToPlan = it)) },
    fieldsLocked: Boolean = false,
    projectLocked: Boolean = false,
    contextLabel: String? = null,
    completable: Boolean = true,
    feedback: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = TimeboxTheme.colors
    val focus = LocalFocusManager.current
    var field by rememberSaveable { mutableStateOf<TaskSheetField?>(null) }
    var edited by rememberSaveable { mutableStateOf(draft) }
    var fieldBaseline by rememberSaveable { mutableStateOf(draft) }
    var confirmFieldDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var createTitle by rememberSaveable { mutableStateOf(draft.title) }
    val completed = draft.status == TaskStatus.Completed
    val editable = !fieldsLocked && !saving && !completed && (creating || !dirty)
    val sheetDirty = dirty || (creating && createTitle.isNotBlank())
    val sheetDirtyLatest by rememberUpdatedState(sheetDirty)
    val savingLatest by rememberUpdatedState(saving)
    val confirmSheetHide = remember {
        { value: SheetValue ->
            if (value == SheetValue.Hidden && (savingLatest || sheetDirtyLatest)) {
                if (!savingLatest) confirmDiscard = true
                false
            } else true
        }
    }
    fun publish(next: TaskDetailDraft) {
        onChange(if (creating) next.copy(title = createTitle) else next)
    }
    fun open(next: TaskSheetField) {
        focus.clearFocus(); edited = draft; fieldBaseline = draft; field = next
    }
    fun dismiss() {
        if (saving) return
        focus.clearFocus()
        if (sheetDirty) confirmDiscard = true else onDismiss()
    }
    fun dismissField() {
        if (saving) return
        val active = field ?: return
        if (mergeTaskField(active, fieldBaseline, edited).normalized() != fieldBaseline.normalized() || (!creating && dirty)) confirmFieldDiscard = true
        else { field = null; focus.clearFocus() }
    }
    LaunchedEffect(saving, dirty, error, submitted) {
        if (submitted && !saving && !dirty && error == null) {
            field = null; submitted = false; focus.clearFocus()
        }
    }
    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirmSheetHide),
        containerColor = colors.sheet.copy(alpha = 1f), contentColor = colors.on,
    ) {
        TaskSheetBackHandler(saving, ::dismiss)
        Column(Modifier.fillMaxWidth().imePadding().fillMaxHeight(taskSheetHeightFraction(false))) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (contextLabel == null) Icons.Outlined.Folder else Icons.Outlined.Repeat, null, Modifier.size(20.dp), tint = colors.project)
                TextButton(onClick = { open(TaskSheetField.Project) }, enabled = editable && !projectLocked, modifier = Modifier.weight(1f)) {
                    Text(contextLabel ?: (projects.firstOrNull { it.id == draft.projectId }?.name ?: "Admin"), Modifier.weight(1f), color = colors.project)
                    if (!projectLocked) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
                IconButton(onClick = ::dismiss, enabled = !saving) { Icon(Icons.Outlined.Close, "Close task") }
            }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    if (!creating && completable) IconButton(onClick = onComplete, enabled = !saving && !dirty) {
                        Icon(if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            if (completed) "Reopen task" else "Complete task", Modifier.size(28.dp), tint = if (completed) colors.project else colors.onVariant)
                    }
                    if (creating) BasicTextField(createTitle, { createTitle = it }, enabled = !saving && !fieldsLocked,
                        textStyle = TextStyle(color = colors.on, fontSize = 25.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(colors.on), modifier = Modifier.weight(1f).padding(top = 7.dp, bottom = 14.dp).semantics { contentDescription = "Task title" },
                        decorationBox = { inner -> if (createTitle.isEmpty()) Text("What needs doing?", color = colors.onVariant, fontSize = 25.sp); inner() })
                    else Text(draft.title, color = colors.on, fontSize = 25.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).clickable(enabled = editable) { open(TaskSheetField.Title) }.padding(top = 7.dp, bottom = 14.dp))
                }
                if (completed) Text("Completed tasks are frozen. Reopen to edit.", color = colors.onVariant)
                if (draft.description.isNotEmpty()) {
                    Text(draft.description, color = colors.onVariant, fontSize = 15.sp, lineHeight = 22.sp,
                        maxLines = if (descriptionExpanded || draft.description.length < 220) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = editable) { open(TaskSheetField.Description) }.padding(bottom = 8.dp))
                    if (draft.description.length >= 220) TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) { Text(if (descriptionExpanded) "Show less" else "Read full description") }
                }
                HorizontalDivider(color = colors.hairline)
                TaskSheetRow(Icons.Outlined.CalendarToday, deadlineText(draft), "Deadline", editable) { open(TaskSheetField.Deadline) }
                if (completable) Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EventAvailable, null, Modifier.size(21.dp), tint = colors.onVariant)
                    Text("Ready to Plan", Modifier.weight(1f).padding(start = 16.dp), color = colors.on)
                    Switch(draft.readyToPlan, onReady, enabled = !fieldsLocked && !completed && !saving && (creating || !dirty), modifier = Modifier.semantics { contentDescription = "Ready to Plan" })
                }
                HorizontalDivider(color = colors.hairline)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (draft.description.isEmpty()) TaskFieldChip(Icons.AutoMirrored.Outlined.Notes, "Description", editable) { open(TaskSheetField.Description) }
                    TaskFieldChip(Icons.Outlined.Flag, "Importance${draft.importance?.let { ": ${it.name}" }.orEmpty()}", editable) { open(TaskSheetField.Importance) }
                    TaskFieldChip(Icons.Outlined.Schedule, "Urgency${draft.urgency?.let { ": ${it.name}" }.orEmpty()}", editable) { open(TaskSheetField.Urgency) }
                    TaskFieldChip(Icons.Outlined.NotificationsNone, if (draft.reminderEnabled) "Reminder: ${draft.reminderDate} ${draft.reminderTime}" else "Reminder", editable) { open(TaskSheetField.Reminder) }
                    TaskFieldChip(Icons.AutoMirrored.Outlined.Label, taskTypes.firstOrNull { it.id == draft.taskTypeId }?.name ?: "Task type", editable) { open(TaskSheetField.TaskType) }
                    TaskFieldChip(Icons.Outlined.Circle, draft.status.label, editable && completable) { open(TaskSheetField.Status) }
                }
                content()
                Spacer(Modifier.height(16.dp))
            }
            feedback()
            if (error != null) Text(error, color = colors.error, modifier = Modifier.padding(horizontal = 20.dp))
            if (!creating && dirty && field == null) Row(Modifier.padding(horizontal = 20.dp)) {
                TextButton(onClick = onDiscard, enabled = !saving) { Text("Discard field edit") }
                TextButton(onClick = onRetrySave, enabled = !saving) { Text("Retry save") }
            }
            if (creating) Button(onClick = { focus.clearFocus(); publish(draft.copy(title = createTitle)); onCreate() }, enabled = !saving && createTitle.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(20.dp, 12.dp).heightIn(min = 48.dp)) { Text(if (saving) "Adding…" else if (fieldsLocked) "Retry remaining subtasks" else "Add task") }
        }
    }
    field?.let { active ->
        var search by rememberSaveable(active) { mutableStateOf("") }
        val merged = mergeTaskField(active, draft, edited)
        val validation = validateTaskDraft(TaskDetailUiState(timezone = timezone).withDraft(merged.copy(title = merged.title.ifBlank { "New task" })))
        val validationMessage = (validation as? TaskDraftValidation.Invalid)?.message
        fun commit(value: TaskDetailDraft, close: Boolean = true) {
            val next = mergeTaskField(active, draft, value)
            edited = value
            if (!dirty && next.normalized() == draft.normalized()) { field = null; return }
            publish(next)
            if (creating) { if (close) field = null; fieldBaseline = value }
            else submitted = true
        }
        ModalBottomSheet(onDismissRequest = ::dismissField,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
                if (it == SheetValue.Hidden) { dismissField(); false } else true
            }), containerColor = colors.surf, contentColor = colors.on) {
            TaskSheetBackHandler(saving, ::dismissField)
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (active == TaskSheetField.Project) "Move to project" else active.label, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    IconButton(onClick = ::dismissField, enabled = !saving) { Icon(Icons.Outlined.Close, "Close ${active.label}") }
                }
                when (active) {
                    TaskSheetField.Title, TaskSheetField.Description -> OutlinedTextField(
                        value = if (active == TaskSheetField.Title) edited.title else edited.description,
                        onValueChange = { edited = if (active == TaskSheetField.Title) edited.copy(title = it) else edited.copy(description = it) },
                        label = { Text(active.label) }, modifier = Modifier.fillMaxWidth(), enabled = !saving, minLines = if (active == TaskSheetField.Description) 3 else 1)
                    TaskSheetField.Importance, TaskSheetField.Urgency -> {
                        val selected = if (active == TaskSheetField.Importance) draft.importance else draft.urgency
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(PriorityLevel.High, PriorityLevel.Medium, PriorityLevel.Low, null).forEach { level ->
                                Surface(selected = selected == level, onClick = { commit(if (active == TaskSheetField.Importance) edited.copy(importance = level) else edited.copy(urgency = level)) },
                                    enabled = !saving, shape = RoundedCornerShape(14.dp), color = if (selected == level) colors.selected else colors.low,
                                    border = BorderStroke(1.dp, if (selected == level) colors.on else colors.outlineVariant), modifier = Modifier.widthIn(min = 76.dp)) {
                                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(if (selected == level) Icons.Outlined.CheckCircle else Icons.Outlined.Flag, null)
                                        Text(level?.name ?: "Not set")
                                    }
                                }
                            }
                        }
                    }
                    TaskSheetField.Project, TaskSheetField.TaskType -> {
                        OutlinedTextField(search, { search = it }, singleLine = true, placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp))
                        val choices = if (active == TaskSheetField.Project) listOf("Admin" to null) + projects.map { it.name to it.id }
                            else listOf("No task type" to null) + taskTypes.map { it.name to it.id }
                        val matches = choices.filter { it.first.contains(search.trim(), true) }
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (matches.isEmpty()) Text("No matches. Try another search.")
                            matches.forEach { (label, id) ->
                                val selected = id == if (active == TaskSheetField.Project) draft.projectId else draft.taskTypeId
                                Surface(selected = selected, onClick = { commit(if (active == TaskSheetField.Project) edited.copy(projectId = id) else edited.copy(taskTypeId = id)) }, enabled = !saving,
                                    shape = RoundedCornerShape(12.dp), color = if (selected) colors.selected else colors.low, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(16.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, Modifier.weight(1f)); if (selected) Icon(Icons.Outlined.Check, "Selected")
                                    }
                                }
                            }
                        }
                    }
                    TaskSheetField.Status -> listOf(TaskStatus.Open, TaskStatus.InProgress).forEach { status ->
                        TextButton(onClick = { commit(edited.copy(status = status)) }, enabled = !saving) { Text(status.label) }
                    }
                    TaskSheetField.Deadline, TaskSheetField.Reminder -> TaskScheduleEditor(edited, active == TaskSheetField.Reminder, timezone, today, !saving,
                        notificationsAllowed, onRequestNotificationPermission, { edited = it })
                }
                if (active in listOf(TaskSheetField.Title, TaskSheetField.Description, TaskSheetField.Deadline, TaskSheetField.Reminder)) {
                    if (validationMessage != null) Text(validationMessage, color = colors.error)
                    if (error != null) Text(error, color = colors.error)
                    Button(onClick = { commit(edited) }, enabled = !saving && validationMessage == null && (active != TaskSheetField.Title || edited.title.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (saving) "Saving…" else if (creating) "Apply to draft" else "Save ${active.label.lowercase()}")
                    }
                } else if (error != null) {
                    Text(error, color = colors.error)
                    TextButton(onClick = onRetrySave, enabled = !saving) { Text("Retry save") }
                }
            }
        }
    }
    if (confirmFieldDiscard) AlertDialog(onDismissRequest = { confirmFieldDiscard = false }, title = { Text("Discard this field edit?") },
        text = { Text("Other saved changes are kept.") }, confirmButton = { TextButton(onClick = { confirmFieldDiscard = false; submitted = false; field = null; focus.clearFocus(); if (!creating && dirty) onDiscard() }) { Text("Discard field edit") } },
        dismissButton = { TextButton(onClick = { confirmFieldDiscard = false }) { Text("Keep editing") } })
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text(if (creating && fieldsLocked) "Discard remaining subtasks?" else if (creating) "Discard new task?" else "Discard this field edit?") },
        text = { Text(if (creating && fieldsLocked) "The task and saved subtasks are kept. Remaining subtasks will be discarded." else if (creating) "This task has not been created." else "Other saved changes are kept.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard(); onDismiss() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
}

/** Use the sheet window's normal Back dispatcher; Material's overlay callback hides it before draft confirmation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskSheetBackHandler(saving: Boolean, onBack: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides checkNotNull(LocalView.current.findViewTreeOnBackPressedDispatcherOwner())) {
        BackHandler { if (!saving) { if (imeVisible) keyboard?.hide() else onBack() } }
    }
}


internal fun deadlineText(draft: TaskDetailDraft): String = when (draft.deadlineMode) {
    TaskDeadlineMode.None -> "Add deadline"
    TaskDeadlineMode.DateOnly -> draft.deadlineDate
    TaskDeadlineMode.DateTime -> "${draft.deadlineDate} · ${draft.deadlineTime}"
}

@Composable
internal fun TaskSheetRow(icon: ImageVector, value: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(21.dp), tint = TimeboxTheme.colors.onVariant)
        Text(value, Modifier.weight(1f).padding(start = 16.dp), color = TimeboxTheme.colors.on)
        Text(label, color = TimeboxTheme.colors.onVariant, fontSize = 12.sp)
    }
}

@Composable
private fun TaskFieldChip(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = TimeboxTheme.colors.high, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(18.dp)); Text(label, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskScheduleEditor(draft: TaskDetailDraft, reminder: Boolean, timezone: String, today: LocalDate, enabled: Boolean,
    notificationsAllowed: Boolean, requestPermission: () -> Unit, onChange: (TaskDetailDraft) -> Unit) {
    val context = LocalContext.current
    val active = if (reminder) draft.reminderEnabled else draft.deadlineMode != TaskDeadlineMode.None
    val fallback = runCatching { LocalDate.parse(draft.deadlineDate) }.getOrDefault(today)
    val date = runCatching { LocalDate.parse(if (reminder) draft.reminderDate else draft.deadlineDate) }.getOrDefault(fallback)
    val time = runCatching { LocalTime.parse(if (reminder) draft.reminderTime else draft.deadlineTime) }.getOrDefault(LocalTime.of(9, 0))
    fun setDate(value: LocalDate) = onChange(if (reminder) draft.copy(reminderDate = value.toString()) else draft.copy(deadlineDate = value.toString()))
    fun setTime(value: LocalTime) {
        val text = value.format(DateTimeFormatter.ofPattern("HH:mm"))
        onChange(if (reminder) draft.copy(reminderTime = text) else draft.copy(deadlineTime = text))
    }
    val missingDeadline = reminder && draft.deadlineMode == TaskDeadlineMode.None
    if (missingDeadline) Text("Set a deadline before adding a reminder.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (reminder) "Remind me" else "Set a deadline", Modifier.weight(1f))
        Switch(active, { checked ->
            if (reminder) {
                if (checked && !notificationsAllowed) requestPermission()
                val suggested = if (draft.deadlineMode == TaskDeadlineMode.DateTime) fallback.atTime(runCatching { LocalTime.parse(draft.deadlineTime) }.getOrDefault(LocalTime.of(9, 0))).minusHours(1) else fallback.atTime(9, 0)
                onChange(draft.copy(reminderEnabled = checked, reminderDate = suggested.toLocalDate().toString(), reminderTime = suggested.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))))
            } else onChange(draft.copy(deadlineMode = if (checked) TaskDeadlineMode.DateOnly else TaskDeadlineMode.None, deadlineDate = date.toString(), reminderEnabled = draft.reminderEnabled && checked))
        }, enabled = enabled && !missingDeadline)
    }
    if (active) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1)).forEach { (label, day) ->
                FilterChip(selected = date == day, onClick = { setDate(day) }, enabled = enabled, label = { Text(label) })
            }
        }
        TaskSheetRow(Icons.Outlined.CalendarToday, date.toString(), "Choose date", enabled) {
            DatePickerDialog(context, { _, y, m, d -> setDate(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        if (!reminder) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Include a time", Modifier.weight(1f))
            Switch(draft.deadlineMode == TaskDeadlineMode.DateTime, { onChange(draft.copy(deadlineMode = if (it) TaskDeadlineMode.DateTime else TaskDeadlineMode.DateOnly, deadlineTime = time.format(DateTimeFormatter.ofPattern("HH:mm")))) }, enabled = enabled)
        }
        if (reminder || draft.deadlineMode == TaskDeadlineMode.DateTime) TaskSheetRow(Icons.Outlined.Schedule, time.toString(), "Choose time", enabled) {
            TimePickerDialog(context, { _, h, m -> setTime(LocalTime.of(h, m)) }, time.hour, time.minute, true).show()
        }
        Text(timezone, color = TimeboxTheme.colors.onVariant, fontSize = 12.sp)
    }
    if (!reminder && !active) Text("Removing a deadline also removes its reminder.", color = TimeboxTheme.colors.onVariant)
    if (reminder && !notificationsAllowed) Text("Reminders are saved, but notifications are disabled on this device.", color = TimeboxTheme.colors.error)
}
