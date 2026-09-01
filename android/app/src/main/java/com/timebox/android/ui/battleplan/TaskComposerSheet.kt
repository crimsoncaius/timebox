package com.timebox.android.ui.battleplan

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.TaskStatus
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.components.SectionCard
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.components.TimeboxSwitch
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TaskComposerOverlay(
    state: BattlePlanUiState,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onDraftChange: (TaskComposerDraft) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestDismiss = {
        when {
            state.saving -> Unit
            state.composerDraft.dirty -> confirmDiscard = true
            else -> onDismiss()
        }
    }

    if (wide) {
        Dialog(
            onDismissRequest = requestDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !state.saving, dismissOnClickOutside = !state.saving),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.88f).widthIn(max = 980.dp),
                shape = RoundedCornerShape(26.dp),
                color = TimeboxTheme.colors.sheet,
                contentColor = TimeboxTheme.colors.on,
                shadowElevation = 18.dp,
            ) {
                TaskComposerContent(state, wide = true, notificationsAllowed, onRequestNotificationPermission, onDraftChange, onReminderEnabledChange, requestDismiss, onCreate)
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = requestDismiss,
            sheetState = sheetState,
            modifier = Modifier.padding(bottom = 96.dp),
            containerColor = TimeboxTheme.colors.sheet,
            contentColor = TimeboxTheme.colors.on,
            scrimColor = TimeboxTheme.colors.scrim,
            shape = TimeboxShapes.sheet,
            dragHandle = { ComposerDragHandle() },
        ) {
            TaskComposerContent(
                state,
                wide = false,
                notificationsAllowed,
                onRequestNotificationPermission,
                onDraftChange,
                onReminderEnabledChange,
                requestDismiss,
                onCreate,
                Modifier.fillMaxHeight(0.94f),
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = TimeboxTheme.colors.sheet,
            title = { Text("Discard new task?", style = TimeboxTheme.type.sectionTitle) },
            text = { Text("The details in this composer have not been created.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun ComposerDragHandle() {
    Box(
        Modifier.padding(top = 10.dp, bottom = 8.dp).width(34.dp).height(4.dp)
            .clip(CircleShape).background(TimeboxTheme.colors.outlineVariant.copy(alpha = 0.6f)),
    )
}

@Composable
private fun TaskComposerContent(
    state: BattlePlanUiState,
    wide: Boolean,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onDraftChange: (TaskComposerDraft) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
        ComposerHeader(state, onDismiss)
        HorizontalDivider(color = colors.hairline)
        state.composerError?.let { error ->
            Text(
                error,
                modifier = Modifier.fillMaxWidth().background(colors.errorContainer).padding(horizontal = 18.dp, vertical = 10.dp),
                style = TimeboxTheme.type.bodySmall,
                color = colors.onErrorContainer,
            )
        }
        val scroll = rememberScrollState()
        if (wide) {
            Row(
                Modifier.weight(1f).verticalScroll(scroll).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EssentialsSection(state, onDraftChange)
                    OrganizationSection(state, onDraftChange)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlanningSection(state, onDraftChange)
                    MoreOptionsSection(state, notificationsAllowed, onRequestNotificationPermission, onDraftChange, onReminderEnabledChange)
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EssentialsSection(state, onDraftChange)
                OrganizationSection(state, onDraftChange)
                PlanningSection(state, onDraftChange)
                MoreOptionsSection(state, notificationsAllowed, onRequestNotificationPermission, onDraftChange, onReminderEnabledChange)
            }
        }
        HorizontalDivider(color = colors.hairline)
        PrimaryButton(
            text = if (state.saving) "Creating…" else "Create task",
            onClick = onCreate,
            enabled = state.composerDraft.title.isNotBlank() && !state.saving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun ComposerHeader(state: BattlePlanUiState, onDismiss: () -> Unit) {
    val colors = TimeboxTheme.colors
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("BATTLE PLAN", style = TimeboxTheme.type.kicker, color = colors.onVariant)
                Text("New task", style = TimeboxTheme.type.screenTitle, color = colors.on)
            }
            RoundIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "Close new task composer",
                onClick = onDismiss,
                border = colors.hairline,
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContextPill(state.selectedScope.label)
            ContextPill(state.composerDraft.status.label)
        }
    }
}

@Composable
private fun ContextPill(label: String) {
    Text(
        label,
        modifier = Modifier.clip(TimeboxShapes.chip).background(TimeboxTheme.colors.selected)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        style = TimeboxTheme.type.bodySmall,
        color = TimeboxTheme.colors.onSelected,
    )
}

@Composable
private fun EssentialsSection(state: BattlePlanUiState, onDraftChange: (TaskComposerDraft) -> Unit) {
    val draft = state.composerDraft
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }
    ComposerSection("01", "Essentials") {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChange(draft.copy(title = it.take(500))) },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label = { Text("Title") },
            placeholder = { Text("Prepare launch notes") },
            singleLine = true,
            isError = state.composerSubmitted && draft.title.isBlank(),
            supportingText = when {
                state.composerSubmitted && draft.title.isBlank() -> { { Text("A title is required.") } }
                draft.title.length >= 450 -> { { Text("${draft.title.length} / 500") } }
                else -> null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            colors = composerTextFieldColors(),
        )
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onDraftChange(draft.copy(description = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") },
            placeholder = { Text("Optional context or intended outcome") },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = composerTextFieldColors(),
        )
    }
}

@Composable
private fun OrganizationSection(state: BattlePlanUiState, onDraftChange: (TaskComposerDraft) -> Unit) {
    val draft = state.composerDraft
    val locationLocked = state.selectedScope.kind != BattlePlanScopeKind.All
    ComposerSection("02", "Organization") {
        BeautifulDropdown(
            label = "Location",
            selected = draft.projectId,
            values = listOf("Admin" to null) + state.projects.map { it.name to it.id },
            enabled = !locationLocked,
            locked = locationLocked,
            supporting = if (locationLocked) "Inherited from the current scope" else null,
            onSelect = { onDraftChange(draft.copy(projectId = it)) },
        )
        BeautifulDropdown(
            label = "Task Type",
            selected = draft.taskTypeId,
            values = listOf("Unset" to null) + state.taskTypes.map { it.name to it.id },
            searchable = true,
            onSelect = { onDraftChange(draft.copy(taskTypeId = it)) },
        )
        BeautifulDropdown(
            label = "Status",
            selected = draft.status,
            values = listOf(TaskStatus.Open.label to TaskStatus.Open, TaskStatus.InProgress.label to TaskStatus.InProgress),
            onSelect = { onDraftChange(draft.copy(status = it)) },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PlanningSection(state: BattlePlanUiState, onDraftChange: (TaskComposerDraft) -> Unit) {
    val draft = state.composerDraft
    val zone = runCatching { ZoneId.of(state.timezone) }.getOrDefault(ZoneId.of("UTC"))
    val today = state.serverNow.atZone(zone).toLocalDate()
    ComposerSection("03", "Planning") {
        BeautifulDropdown(
            label = "Deadline",
            selected = draft.deadlineMode,
            values = listOf(
                "None" to TaskDeadlineMode.None,
                "Date only" to TaskDeadlineMode.DateOnly,
                "Date and time" to TaskDeadlineMode.DateTime,
            ),
            onSelect = { mode ->
                onDraftChange(
                    draft.copy(
                        deadlineMode = mode,
                        reminderEnabled = if (mode == TaskDeadlineMode.None) false else draft.reminderEnabled,
                        reminderDate = if (mode == TaskDeadlineMode.None) "" else draft.reminderDate,
                        reminderTime = if (mode == TaskDeadlineMode.None) "" else draft.reminderTime,
                    ),
                )
            },
        )
        AnimatedVisibility(draft.deadlineMode != TaskDeadlineMode.None) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDateChip("Today", today, draft.deadlineDate) { onDraftChange(draft.copy(deadlineDate = it.toString())) }
                    QuickDateChip("Tomorrow", today.plusDays(1), draft.deadlineDate) { onDraftChange(draft.copy(deadlineDate = it.toString())) }
                    QuickDateChip("Next week", today.plusWeeks(1), draft.deadlineDate) { onDraftChange(draft.copy(deadlineDate = it.toString())) }
                }
                DatePickerButton("Deadline date", draft.deadlineDate, today) { onDraftChange(draft.copy(deadlineDate = it)) }
                if (draft.deadlineMode == TaskDeadlineMode.DateTime) {
                    TimePickerButton("Deadline time · ${state.timezone}", draft.deadlineTime) { onDraftChange(draft.copy(deadlineTime = it)) }
                }
            }
        }
        PrioritySelector("Urgency", draft.urgency) { onDraftChange(draft.copy(urgency = it)) }
        PrioritySelector("Importance", draft.importance) { onDraftChange(draft.copy(importance = it)) }
    }
}

@Composable
private fun MoreOptionsSection(
    state: BattlePlanUiState,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onDraftChange: (TaskComposerDraft) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
) {
    val draft = state.composerDraft
    val rotation by animateFloatAsState(if (draft.moreOpen) 180f else 0f, label = "moreArrow")
    SectionCard {
        Row(
            Modifier.fillMaxWidth().clickable { onDraftChange(draft.copy(moreOpen = !draft.moreOpen)) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("MORE OPTIONS", style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.on)
                Text("Ready to Plan and reminder", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            }
            Icon(Icons.Outlined.ArrowDropDown, null, Modifier.rotate(rotation), tint = TimeboxTheme.colors.onVariant)
        }
        AnimatedVisibility(draft.moreOpen) {
            Column {
                HorizontalDivider(color = TimeboxTheme.colors.hairline)
                ToggleSetting(
                    title = "Add to Ready to Plan",
                    description = "Make this task available when planning your day.",
                    checked = draft.readyToPlan,
                    onCheckedChange = { onDraftChange(draft.copy(readyToPlan = it)) },
                )
                if (draft.deadlineMode != TaskDeadlineMode.None) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = TimeboxTheme.colors.hairline)
                    ToggleSetting(
                        title = "Reminder",
                        description = "Notify me before the deadline.",
                        checked = draft.reminderEnabled,
                        onCheckedChange = {
                            if (it && !notificationsAllowed) onRequestNotificationPermission()
                            onReminderEnabledChange(it)
                        },
                    )
                    AnimatedVisibility(draft.reminderEnabled) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!notificationsAllowed) {
                                Text(
                                    "This reminder will be saved, but notifications are disabled for this device.",
                                    style = TimeboxTheme.type.bodySmall,
                                    color = TimeboxTheme.colors.error,
                                )
                            }
                            val date = runCatching { LocalDate.parse(draft.deadlineDate) }.getOrNull() ?: LocalDate.now()
                            DatePickerButton("Reminder date", draft.reminderDate, date) { onDraftChange(draft.copy(reminderDate = it)) }
                            TimePickerButton("Reminder time · ${state.timezone}", draft.reminderTime) { onDraftChange(draft.copy(reminderTime = it)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerSection(number: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(number, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            Text(title.uppercase(), style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.on)
            HorizontalDivider(Modifier.weight(1f), color = TimeboxTheme.colors.hairline)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun <T> BeautifulDropdown(
    label: String,
    selected: T,
    values: List<Pair<String, T>>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    locked: Boolean = false,
    supporting: String? = null,
    searchable: Boolean = false,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var anchorWidth by remember { mutableStateOf(280.dp) }
    val density = LocalDensity.current
    val colors = TimeboxTheme.colors
    val selectedLabel = values.firstOrNull { it.second == selected }?.first ?: "Unset"
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "dropdownArrow")
    Box(modifier) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = "$label, $selectedLabel"; stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .clickable(enabled = enabled) { expanded = true }
                    .onSizeChanged { size -> with(density) { anchorWidth = size.width.toDp() } },
                shape = TimeboxShapes.field,
                color = if (enabled) colors.raised else colors.field,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (expanded) colors.outline else colors.hairline),
            ) {
                Row(Modifier.heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label.uppercase(), style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
                        Text(selectedLabel, style = TimeboxTheme.type.body, color = if (enabled) colors.on else colors.disabledContent)
                    }
                    Icon(
                        if (locked) Icons.Outlined.Lock else Icons.Outlined.ArrowDropDown,
                        contentDescription = null,
                        modifier = if (locked) Modifier.size(18.dp) else Modifier.rotate(rotation),
                        tint = colors.onVariant,
                    )
                }
            }
            supporting?.let { Text(it, Modifier.padding(start = 4.dp, top = 4.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; query = "" },
            modifier = Modifier.width(anchorWidth).padding(vertical = 6.dp),
            offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = colors.lowest,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
        ) {
            if (searchable) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    label = { Text("Search $label") },
                    singleLine = true,
                    colors = composerTextFieldColors(),
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.hairline)
            }
            val visibleValues = values.filter { it.first.contains(query, ignoreCase = true) }
            visibleValues.forEach { (name, value) ->
                val isSelected = value == selected
                DropdownMenuItem(
                    text = { Text(name, style = TimeboxTheme.type.body, color = colors.on) },
                    onClick = { expanded = false; query = ""; onSelect(value) },
                    modifier = Modifier.padding(horizontal = 6.dp).clip(TimeboxShapes.card)
                        .background(if (isSelected) colors.selected else Color.Transparent),
                    trailingIcon = { if (isSelected) Icon(Icons.Outlined.Check, "Selected", tint = colors.onSelected) },
                )
            }
            if (visibleValues.isEmpty()) {
                Text("No matching Task Types", Modifier.padding(16.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
        }
    }
}

@Composable
private fun PrioritySelector(label: String, selected: PriorityLevel?, onSelect: (PriorityLevel?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label.uppercase(), style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.onVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Unset" to null,
                "Low" to PriorityLevel.Low,
                "Medium" to PriorityLevel.Medium,
                "High" to PriorityLevel.High,
            ).forEach { (name, value) ->
                TimeboxChip(
                    label = name,
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickDateChip(label: String, date: LocalDate, selectedDate: String, onSelect: (LocalDate) -> Unit) {
    TimeboxChip(label, selectedDate == date.toString(), { onSelect(date) }, height = 42.dp)
}

@Composable
private fun DatePickerButton(label: String, value: String, fallback: LocalDate, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(value) }.getOrDefault(fallback)
    PickerButton(label, value.ifBlank { "Choose date" }) {
        DatePickerDialog(
            context,
            { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day).toString()) },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).show()
    }
}

@Composable
private fun TimePickerButton(label: String, value: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val time = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(9, 0))
    PickerButton(label, value.ifBlank { "Choose time" }) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onSelect("%02d:%02d".format(hour, minute)) },
            time.hour,
            time.minute,
            true,
        ).show()
    }
}

@Composable
private fun PickerButton(label: String, value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = TimeboxShapes.field,
        color = TimeboxTheme.colors.raised,
        border = androidx.compose.foundation.BorderStroke(1.dp, TimeboxTheme.colors.hairline),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label.uppercase(), style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.onVariant)
            Text(value, style = TimeboxTheme.type.body, color = TimeboxTheme.colors.on)
        }
    }
}

@Composable
private fun ToggleSetting(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TimeboxTheme.type.label, color = TimeboxTheme.colors.on)
            Text(description, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        }
        TimeboxSwitch(checked, onCheckedChange)
    }
}

@Composable
private fun composerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = TimeboxTheme.colors.raised,
    unfocusedContainerColor = TimeboxTheme.colors.raised,
    disabledContainerColor = TimeboxTheme.colors.field,
    focusedBorderColor = TimeboxTheme.colors.outline,
    unfocusedBorderColor = TimeboxTheme.colors.hairline,
    cursorColor = TimeboxTheme.colors.on,
)
