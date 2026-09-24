package com.timebox.android.ui.battleplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.*
import com.timebox.android.ui.components.*
import com.timebox.android.ui.day.TaskTypePicker
import com.timebox.android.ui.theme.TimeboxTheme

/** Shared creation and routine details. Focused edits use the existing recurrence repository. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(
    state: RecurringEditorUiState,
    viewModel: RecurringEditorViewModel,
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit = {},
    lifecycle: RecurringUiState? = null,
    lifecycleViewModel: RecurringViewModel? = null,
) {
    if (state.loading) { LoadingState(); return }
    if (state.error != null) { ErrorState(state.error, onRetry = { viewModel.open(state.templateId) }); return }
    LaunchedEffect(lifecycle?.selectedTemplate) { lifecycle?.selectedTemplate?.let(viewModel::acceptTemplate) }
    val creating = state.templateId == null
    val colors = TimeboxTheme.colors
    var editor by rememberSaveable(state.templateId) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(state.templateId, stateSaver = routineDraftSaver(state)) { mutableStateOf(state) }
    var discard by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var discardField by remember { mutableStateOf(false) }
    val busy = state.saving || lifecycle?.actionInProgress == true
    val editable = !busy && !(state.dirty && !creating)
    val recommendationName = if (editor == "Title") draft.title else state.title
    val (recommendationState, recommendation) = com.timebox.android.ui.day.rememberTaskTypeRecommendation(
        recommendationName, state.taskTypes, state.taskTypeId)
    fun close() { if (!busy) { if (state.dirty) discard = true else onBack() } }
    fun open(field: String) { if (editable) { draft = state; editor = field } }
    fun commit(next: RecurringEditorUiState) {
        viewModel.applyDraft(next)
        editor = null
        if (!creating) viewModel.save()
    }
    fun closeField() { if (draft != state) discardField = true else editor = null }
    val template = state.template
    ModalBottomSheet(onDismissRequest = ::close,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { value ->
            if (value == SheetValue.Hidden && (busy || state.dirty)) { if (!busy) discard = true; false } else true
        }), containerColor = colors.sheet.copy(alpha = 1f)) {
        TaskSheetBackHandler(busy, ::close)
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Repeat, null, tint = colors.onVariant)
                Text("Recurring routine", Modifier.weight(1f).padding(start = 12.dp), color = colors.onVariant)
                IconButton(::close, enabled = !busy) { Icon(Icons.Outlined.Close, "Close routine") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (template != null) Text((lifecycle?.selectedTemplate?.status ?: template.status).name, style = TimeboxTheme.type.kicker, color = colors.onVariant)
                Text(state.title.ifBlank { "What repeats?" }, fontSize = 25.sp, color = colors.on,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = editable) { open("Title") }.padding(vertical = 12.dp))
                Text(state.description.ifBlank { "Add description" }, color = colors.onVariant,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = editable) { open("Description") }.padding(bottom = 8.dp))
                HorizontalDivider(color = colors.hairline)
                TaskSheetRow(Icons.Outlined.Repeat, routineRuleSummary(state), "Repeat", editable) { open("Repeat") }
                Text("From ${state.startDate}" + when (state.endMode) {
                    RecurrenceEndMode.Never -> " · no end date"
                    RecurrenceEndMode.EndDate -> " · through ${state.endDate}"
                    RecurrenceEndMode.CycleLimit -> " · ${state.cycleLimit} cycles"
                }, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                if (state.mode == RecurrenceMode.Scheduled) TaskSheetRow(Icons.Outlined.EventAvailable,
                    if (state.queuePreplanning) "Ready to Plan" else if (state.preplanningSlots.isEmpty()) "No pre-planning" else "Planned time · ${state.preplanningSlots.size} slots", "Pre-planning", editable) { open("Pre-planning") }
                template?.preplanningSchedule?.unavailableSlots?.forEach { slot ->
                    Text("Unavailable on ${slot.date}: ${com.timebox.android.ui.formatMinuteLabel24(slot.startMinute)}–${com.timebox.android.ui.formatMinuteLabel24(slot.endMinute)}", color = colors.error, style = TimeboxTheme.type.bodySmall)
                }
                HorizontalDivider(color = colors.hairline)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskFieldChip(Icons.Outlined.Flag, "Importance: ${state.importance?.label ?: "Not set"}", editable) { open("Importance") }
                    TaskFieldChip(Icons.Outlined.Schedule, "Urgency: ${state.urgency?.label ?: "Not set"}", editable) { open("Urgency") }
                    TaskFieldChip(Icons.AutoMirrored.Outlined.Label, "Task: ${state.taskTypes.find { it.id == state.taskTypeId }?.name ?: "Unset"}", editable) { open("Task Type") }
                }
                if (editor == null) com.timebox.android.ui.day.TaskTypeRecommendation(recommendation, {
                    recommendationState.markChosen(); commit(state.copy(taskTypeId = it.id))
                }, { recommendationState.dismiss(recommendationName) }, editable)
                val names = state.checklistText.lineSequence().filter { it.isNotBlank() }.toList()
                TaskSubtasks(emptyList(), editable, busy, state.saveError, {}, {}, { commit(state.copy(checklistText = (names + it).joinToString("\n"))) },
                    draftNames = names, onRemoveDraft = { index -> commit(state.copy(checklistText = names.filterIndexed { i, _ -> i != index }.joinToString("\n"))) })
                Text("Included in each ${if (state.mode == RecurrenceMode.Quota) "session" else "occurrence"}.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                if (creating) PreviewCard(state, viewModel::refreshPreview)
                if (template != null) {
                    HorizontalDivider(Modifier.padding(top = 16.dp), color = colors.hairline)
                    Text("Current work", style = TimeboxTheme.type.sectionTitle)
                    if (template.currentTasks.isEmpty()) Text("No current tasks", color = colors.onVariant)
                    template.currentTasks.forEach { task ->
                        TextButton({ onOpenTask(task.id) }, enabled = !busy && !state.dirty) { Text(task.title + if (task.overdue) " · Overdue" else "") }
                    }
                    RoutineCalendarSection(template, viewModel::calendar)
                    val status = lifecycle?.selectedTemplate?.status ?: template.status
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        if (status == RecurrenceStatus.Active) TextButton({ lifecycleViewModel?.pause() }, enabled = !busy && !state.dirty) { Text("Pause") }
                        if (status == RecurrenceStatus.Paused) TextButton({ lifecycleViewModel?.resume() }, enabled = !busy && !state.dirty) { Text("Resume") }
                        if (status != RecurrenceStatus.Ended) TextButton({ confirmEnd = true }, enabled = !busy && !state.dirty) { Text("End", color = colors.error) }
                        else TextButton({ lifecycleViewModel?.requestDelete() }, enabled = !busy && !state.dirty) { Text("Delete", color = colors.error) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            lifecycle?.message?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp)) }
            state.saveError?.let { Text(it, color = colors.error, modifier = Modifier.padding(horizontal = 20.dp)) }
            if (!creating && state.dirty && !busy) Row(Modifier.padding(horizontal = 20.dp)) {
                TextButton({ viewModel.save() }) { Text("Retry save") }
                TextButton(viewModel::discardDraft) { Text("Discard edit") }
            }
            if (creating) Button({ viewModel.save() }, enabled = !busy && validateRecurrenceDraft(state, true) == null, modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text("Create routine") }
        }
    }
    if (editor == "Repeat") RoutineRepeatEditor(state, creating, { editor = null }, ::commit)
    editor?.takeUnless { it == "Repeat" }?.let { field ->
        ModalBottomSheet(onDismissRequest = ::closeField,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { value -> if (value == SheetValue.Hidden && draft != state) { discardField = true; false } else true }), containerColor = colors.sheet.copy(alpha = 1f)) {
            TaskSheetBackHandler(false, ::closeField)
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (field == "Pre-planning") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(field, Modifier.weight(1f), style = TimeboxTheme.type.screenTitle)
                        IconButton(::closeField) { Icon(Icons.Outlined.Close, "Close pre-planning") }
                    }
                    Text("${state.title} \u00b7 ${routineRuleSummary(state)}", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    Spacer(Modifier.height(10.dp))
                } else Text(field, style = TimeboxTheme.type.sectionTitle)
                when (field) {
                    "Title" -> OutlinedTextField(draft.title, { draft = draft.copy(title = it) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    "Description" -> OutlinedTextField(draft.description, { draft = draft.copy(description = it) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    "Importance", "Urgency" -> (listOf<PriorityLevel?>(null) + PriorityLevel.entries).forEach { value ->
                        val selected = if (field == "Importance") draft.importance else draft.urgency
                        TimeboxChip(value?.label ?: "Not set", selected == value, { draft = if (field == "Importance") draft.copy(importance = value) else draft.copy(urgency = value) })
                    }
                    "Task Type" -> {
                        var query by remember { mutableStateOf("") }
                        TaskTypePicker(recommendationState = recommendationState, taskTypes = state.taskTypes, query = query, onQueryChange = { query = it }, selectedTypeId = draft.taskTypeId,
                            onChoose = { draft = draft.copy(taskTypeId = it.id) }, onCreate = { path -> viewModel.createRoutineTaskType(path) { id -> draft = draft.copy(taskTypeId = id) } }, allowUnset = true, onUnset = { draft = draft.copy(taskTypeId = null) })
                    }
                    "Pre-planning" -> RoutinePreplanningFields(draft) { draft = it }
                }
                if (field == "Task Type") state.saveError?.let { Text(it, color = colors.error) }
                val error = if (field == "Pre-planning") validateRecurrenceDraft(draft, false) else null
                if (error != null) Text(error, color = colors.error)
                if (field == "Pre-planning") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(::closeField, Modifier.weight(1f)) { Text("Cancel") }
                        Button({ commit(draft) }, enabled = error == null, modifier = Modifier.weight(2f).height(48.dp)) { Text("Save") }
                    }
                } else {
                    Button({ commit(draft) }, enabled = error == null && (field != "Title" || draft.title.isNotBlank()), modifier = Modifier.fillMaxWidth()) { Text("Save") }
                    TextButton(::closeField) { Text("Cancel") }
                }
            }
        }
    }
    if (discardField) AlertDialog(onDismissRequest = { discardField = false }, title = { Text("Discard this edit?") }, confirmButton = { TextButton({ discardField = false; editor = null }) { Text("Discard") } }, dismissButton = { TextButton({ discardField = false }) { Text("Keep editing") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard unsaved changes?") }, confirmButton = { TextButton({ viewModel.discardDraft(); onBack() }) { Text("Discard") } }, dismissButton = { TextButton({ discard = false }) { Text("Keep editing") } })
    state.pendingBackfill?.let { detail -> AlertDialog(onDismissRequest = viewModel::dismissBackfill, title = { Text("Create past occurrences?") }, text = { Text("This change creates ${detail.pastTasks} tasks across ${detail.pastCycles} past cycles.") }, confirmButton = { TextButton({ viewModel.save(confirmBackfill = true) }) { Text("Backfill and save") } }, dismissButton = { TextButton(viewModel::dismissBackfill) { Text("Cancel") } }) }
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text("End this routine?") }, text = { Text("Today's and future pristine tasks may be removed. Customized and completed tasks are preserved.") }, confirmButton = { TextButton({ confirmEnd = false; lifecycleViewModel?.end() }) { Text("End routine") } }, dismissButton = { TextButton({ confirmEnd = false }) { Text("Cancel") } })
    lifecycle?.pendingDelete?.let { templateToDelete -> RecurringDeleteDialog(templateToDelete, { lifecycleViewModel?.dismissDelete() }, { lifecycleViewModel?.confirmDelete(onBack) }) }
}

