package com.timebox.android.ui.battleplan

import com.timebox.android.ui.components.HelperText
import android.app.TimePickerDialog
import com.timebox.android.ui.showMondayDatePicker
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.first
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
    // The text panel edits the title and description together.
    TaskSheetField.Title, TaskSheetField.Description -> current.copy(title = edited.title, description = edited.description)
    TaskSheetField.Project -> current.copy(projectId = edited.projectId)
    TaskSheetField.TaskType -> current.copy(taskTypeId = edited.taskTypeId)
    TaskSheetField.Importance -> current.copy(importance = edited.importance)
    TaskSheetField.Urgency -> current.copy(urgency = edited.urgency)
    TaskSheetField.Status -> current.copy(status = edited.status)
    TaskSheetField.Deadline -> current.copy(deadlineMode = edited.deadlineMode, deadlineDate = edited.deadlineDate,
        deadlineTime = edited.deadlineTime)
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
    onCreateTaskType: (String) -> Unit = {},
    onComplete: () -> Unit = {},
    onReady: (Boolean) -> Unit = { onChange(draft.copy(readyToPlan = it)) },
    savedReminder: java.time.Instant? = null,
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
    val recommendationName = if (creating) createTitle else if (field == TaskSheetField.Title) edited.title else draft.title
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
        val opened = if (creating) draft.copy(title = createTitle) else draft
        focus.clearFocus(); edited = opened; fieldBaseline = opened; field = next
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
                    TaskFieldChip(Icons.AutoMirrored.Outlined.Label, taskTypes.firstOrNull { it.id == draft.taskTypeId }?.takeUnless { it.name == "unspecified" }?.name ?: "Unset", editable) { open(TaskSheetField.TaskType) }
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
        val validation = validateTaskDraft(TaskDetailUiState(timezone = timezone).withDraft(merged.copy(title = merged.title.ifBlank { "New task" })), savedReminder = savedReminder)
        val validationMessage = (validation as? TaskDraftValidation.Invalid)?.message
        fun commit(value: TaskDetailDraft, close: Boolean = true) {
            val next = mergeTaskField(active, draft, value)
            edited = value
            if (creating && (active == TaskSheetField.Title || active == TaskSheetField.Description)) createTitle = value.title
            if (!dirty && next.normalized() == draft.normalized()) { field = null; return }
            publish(next)
            if (creating) { if (close) field = null; fieldBaseline = value }
            else submitted = true
        }
        if (active == TaskSheetField.Title || active == TaskSheetField.Description) TaskTextPanel(
            title = edited.title, description = edited.description, focusDescription = active == TaskSheetField.Description,
            saving = saving, error = validationMessage ?: error, onTitleChange = { edited = edited.copy(title = it) },
            onDescriptionChange = { edited = edited.copy(description = it) },
            onSave = { commit(edited) }, onBack = ::dismissField,
        ) else ModalBottomSheet(onDismissRequest = ::dismissField,
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
                    TaskSheetField.Title, TaskSheetField.Description -> Unit
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
                    TaskSheetField.Project -> {
                        OutlinedTextField(search, { search = it }, singleLine = true, placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp))
                        val choices = listOf("Admin" to null) + projects.map { it.name to it.id }
                        val matches = choices.filter { it.first.contains(search.trim(), true) }
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (matches.isEmpty()) HelperText("No matches. Try another search.", Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                            matches.forEach { (label, id) ->
                                val selected = id == draft.projectId
                                Surface(selected = selected, onClick = { commit(edited.copy(projectId = id)) }, enabled = !saving,
                                    shape = RoundedCornerShape(12.dp), color = if (selected) colors.selected else colors.low, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(16.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, Modifier.weight(1f)); if (selected) Icon(Icons.Outlined.Check, "Selected")
                                    }
                                }
                            }
                        }
                    }
                    TaskSheetField.TaskType -> {
                        com.timebox.android.ui.day.TaskTypePicker(
                            recommendationName = recommendationName, recommendationEnabled = !saving && !fieldsLocked && !completed,
                            taskTypes = taskTypes,
                            query = search,
                            onQueryChange = { search = it },
                            selectedTypeId = draft.taskTypeId.takeUnless { id -> taskTypes.find { it.id == id }?.name == "unspecified" },
                            onChoose = { commit(edited.copy(taskTypeId = it.id)) },
                            onCreate = onCreateTaskType,
                            allowUnset = true,
                            onUnset = { commit(edited.copy(taskTypeId = null)) },
                        )
                    }
                    TaskSheetField.Status -> listOf(TaskStatus.Open, TaskStatus.InProgress).forEach { status ->
                        TextButton(onClick = { commit(edited.copy(status = status)) }, enabled = !saving) { Text(status.label) }
                    }
                    TaskSheetField.Deadline, TaskSheetField.Reminder -> TaskScheduleEditor(edited, active == TaskSheetField.Reminder, timezone, today, !saving,
                        notificationsAllowed, onRequestNotificationPermission, { edited = it })
                }
                if (active in listOf(TaskSheetField.Deadline, TaskSheetField.Reminder)) {
                    if (validationMessage != null) Text(validationMessage, color = colors.error)
                    if (error != null) Text(error, color = colors.error)
                    Button(onClick = { commit(edited) }, enabled = !saving && validationMessage == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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

/** Full-screen editor for the title and description, opened from either one on the task sheet. */
@Composable
private fun TaskTextPanel(title: String, description: String, focusDescription: Boolean, saving: Boolean, error: String?,
    onTitleChange: (String) -> Unit, onDescriptionChange: (String) -> Unit, onSave: () -> Unit, onBack: () -> Unit) {
    val colors = TimeboxTheme.colors
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        val panelColor = colors.low.copy(alpha = 1f)
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Surface(Modifier.fillMaxSize(), color = panelColor, contentColor = colors.on) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, enabled = !saving) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to task") }
                    Text("Edit task", Modifier.weight(1f).padding(start = 12.dp), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    TextButton(onClick = onSave, enabled = !saving && error == null && title.isNotBlank()) {
                        Text(if (saving) "Saving…" else "Save", fontWeight = FontWeight.Medium)
                    }
                }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    val titleFocus = remember { FocusRequester() }
                    val descriptionFocus = remember { FocusRequester() }
                    TaskTextCard(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp), {
                        Icon(Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(28.dp), tint = colors.onVariant)
                    }) {
                        TaskTextField(title, onTitleChange, "Task name", "Task title", !saving,
                            TextStyle(color = colors.on, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
                            Modifier.focusRequester(titleFocus), ImeAction.Next) { descriptionFocus.requestFocus() }
                    }
                    Spacer(Modifier.height(3.dp))
                    TaskTextCard(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp), {
                        Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.size(24.dp), tint = colors.onVariant)
                    }) {
                        TaskTextField(description, onDescriptionChange, "Description", "Task description", !saving,
                            TextStyle(color = colors.on, fontSize = 16.sp, lineHeight = 24.sp), Modifier.focusRequester(descriptionFocus))
                    }
                    if (error != null) Text(error, color = colors.error, modifier = Modifier.padding(12.dp))
                    val windowInfo = LocalWindowInfo.current
                    val keyboard = LocalSoftwareKeyboardController.current
                    // Focus once the dialog window has it, so the keyboard opens with the panel.
                    LaunchedEffect(Unit) {
                        snapshotFlow { windowInfo.isWindowFocused }.first { it }
                        (if (focusDescription) descriptionFocus else titleFocus).requestFocus()
                        keyboard?.show()
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskTextCard(shape: androidx.compose.ui.graphics.Shape, leading: @Composable () -> Unit, content: @Composable () -> Unit) {
    Surface(color = TimeboxTheme.colors.surf, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { leading() }
            Box(Modifier.weight(1f).padding(start = 8.dp, top = 16.dp, bottom = 16.dp)) { content() }
        }
    }
}

/** Opens with the cursor after the existing text, ready to continue typing. */
@Composable
private fun TaskTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, description: String, enabled: Boolean, style: TextStyle,
    modifier: Modifier = Modifier, imeAction: ImeAction = ImeAction.Default, onNext: () -> Unit = {}) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = field.copy(text = value)
    BasicTextField(field, { field = it; if (it.text != value) onValueChange(it.text) }, enabled = enabled, textStyle = style,
        cursorBrush = SolidColor(TimeboxTheme.colors.on),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, style = style.copy(color = TimeboxTheme.colors.onVariant, fontWeight = FontWeight.Normal)); inner() })
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
internal fun TaskFieldChip(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = TimeboxTheme.colors.high, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(18.dp)); Text(label, fontSize = 13.sp)
        }
    }
}

private val scheduleTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val scheduleDateFormat = DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH)

/** The picked date, or null while the deadline or reminder is unset. */
internal fun scheduleDate(draft: TaskDetailDraft, reminder: Boolean): LocalDate? = when {
    reminder && !draft.reminderEnabled -> null
    !reminder && draft.deadlineMode == TaskDeadlineMode.None -> null
    else -> runCatching { LocalDate.parse(if (reminder) draft.reminderDate else draft.deadlineDate) }.getOrNull()
}

/** The picked time, or null when unset; a date-only deadline has no time. */
internal fun scheduleTime(draft: TaskDetailDraft, reminder: Boolean): LocalTime? = when {
    scheduleDate(draft, reminder) == null -> null
    reminder -> runCatching { LocalTime.parse(draft.reminderTime) }.getOrNull()
    draft.deadlineMode == TaskDeadlineMode.DateTime -> runCatching { LocalTime.parse(draft.deadlineTime) }.getOrNull()
    else -> null
}

/**
 * Picking a date is what sets a deadline or reminder. A deadline keeps any time already chosen;
 * a new reminder starts at the suggested time on the suggested day, otherwise 09:00.
 */
internal fun withScheduleDate(draft: TaskDetailDraft, reminder: Boolean, date: LocalDate, suggestedReminder: java.time.ZonedDateTime): TaskDetailDraft {
    val time = scheduleTime(draft, reminder)
    return if (reminder) draft.copy(reminderEnabled = true, reminderDate = date.toString(), reminderTime = (time
        ?: if (date == suggestedReminder.toLocalDate()) suggestedReminder.toLocalTime() else LocalTime.of(9, 0)).format(scheduleTimeFormat))
    else draft.copy(deadlineMode = if (time == null) TaskDeadlineMode.DateOnly else TaskDeadlineMode.DateTime, deadlineDate = date.toString())
}

/** Choosing a time needs a date first; without one the draft is unchanged. */
internal fun withScheduleTime(draft: TaskDetailDraft, reminder: Boolean, time: LocalTime): TaskDetailDraft = when {
    scheduleDate(draft, reminder) == null -> draft
    reminder -> draft.copy(reminderTime = time.format(scheduleTimeFormat))
    else -> draft.copy(deadlineMode = TaskDeadlineMode.DateTime, deadlineTime = time.format(scheduleTimeFormat))
}

internal fun withoutDeadlineTime(draft: TaskDetailDraft): TaskDetailDraft =
    if (draft.deadlineMode == TaskDeadlineMode.DateTime) draft.copy(deadlineMode = TaskDeadlineMode.DateOnly) else draft

internal fun withoutSchedule(draft: TaskDetailDraft, reminder: Boolean): TaskDetailDraft =
    if (reminder) draft.copy(reminderEnabled = false) else draft.copy(deadlineMode = TaskDeadlineMode.None)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskScheduleEditor(draft: TaskDetailDraft, reminder: Boolean, timezone: String, today: LocalDate, enabled: Boolean,
    notificationsAllowed: Boolean, requestPermission: () -> Unit, onChange: (TaskDetailDraft) -> Unit) {
    val context = LocalContext.current
    val date = scheduleDate(draft, reminder)
    val time = scheduleTime(draft, reminder)
    fun setDate(value: LocalDate) {
        if (reminder && date == null && !notificationsAllowed) requestPermission()
        onChange(withScheduleDate(draft, reminder, value, suggestedReminderStart(java.time.Instant.now(), ZoneId.of(timezone))))
    }
    fun pickTime() = TimePickerDialog(context, { _, h, m -> onChange(withScheduleTime(draft, reminder, LocalTime.of(h, m))) },
        (time ?: LocalTime.of(if (reminder) 9 else 17, 0)).hour, time?.minute ?: 0, true).show()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1)).forEach { (label, day) ->
            FilterChip(selected = date == day, onClick = { setDate(day) }, enabled = enabled, label = { Text(label) })
        }
    }
    TaskSheetRow(Icons.Outlined.CalendarToday, date?.format(scheduleDateFormat) ?: "Choose date", "Choose date", enabled) {
        showMondayDatePicker(context, date ?: today) { selected -> setDate(selected) }
    }
    // The time row stays visible but muted until a date is picked.
    if (!reminder && time != null) Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { TaskSheetRow(Icons.Outlined.Schedule, time.format(scheduleTimeFormat), "Change time", enabled, ::pickTime) }
        IconButton(onClick = { onChange(withoutDeadlineTime(draft)) }, enabled = enabled) { Icon(Icons.Outlined.Close, "Remove time") }
    } else Box(Modifier.alpha(if (date == null) 0.38f else 1f)) {
        TaskSheetRow(Icons.Outlined.Schedule, time?.format(scheduleTimeFormat) ?: "Add time",
            if (date == null) "Pick a date first" else if (reminder) "Choose time" else "Optional", enabled && date != null, ::pickTime)
    }
    if (date != null) TextButton(onClick = { onChange(withoutSchedule(draft, reminder)) }, enabled = enabled) {
        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (reminder) "Clear reminder" else "Clear deadline")
    }
    Text(timezone, color = TimeboxTheme.colors.onVariant, fontSize = 12.sp)
    if (reminder && !notificationsAllowed) Text("Reminders are saved, but notifications are disabled on this device.", color = TimeboxTheme.colors.error)
}
