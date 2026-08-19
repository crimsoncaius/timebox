package com.timebox.android.ui.battleplan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.RecurrenceStatus
import com.timebox.android.data.RecurringTemplate
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.SectionCard
import com.timebox.android.ui.components.SectionHeader
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.theme.TimeboxTheme

@Composable
fun RecurringScreen(
    state: RecurringUiState,
    onRetry: () -> Unit,
    onSelectStatus: (RecurrenceStatus) -> Unit,
    onNew: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxSize()) {
        RecurringStatusTabs(state.selectedStatus, onSelectStatus)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.templates.size} template${if (state.templates.size == 1) "" else "s"}",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                "New recurrence",
                onNew,
                leading = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = colors.bg) },
            )
        }
        when {
            state.loading -> LoadingState()
            state.error != null && state.templates.isEmpty() -> ErrorState(state.error, onRetry)
            state.templates.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ${state.selectedStatus.label.lowercase()} recurring templates.", color = colors.onVariant)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.templates, key = { it.id }) { template ->
                    RecurringTemplateRow(template, onOpen)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RecurringStatusTabs(selected: RecurrenceStatus, onSelect: (RecurrenceStatus) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = RecurrenceStatus.entries.indexOf(selected),
        edgePadding = 8.dp,
        containerColor = TimeboxTheme.colors.bg,
        contentColor = TimeboxTheme.colors.on,
        divider = {},
    ) {
        RecurrenceStatus.entries.forEach { status ->
            Tab(selected == status, { onSelect(status) }, text = { Text(status.label) })
        }
    }
}

@Composable
private fun RecurringTemplateRow(template: RecurringTemplate, onOpen: (Int) -> Unit) {
    val colors = TimeboxTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.low)
            .clickable { onOpen(template.id) }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                template.title,
                style = TimeboxTheme.type.sectionTitle,
                color = colors.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(template.status.label, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        }
        Text(
            "${template.project?.name ?: "Admin"}${template.taskType?.let { " · ${it.name}" }.orEmpty()}",
            style = TimeboxTheme.type.bodySmall,
            color = colors.onVariant,
        )
        Row {
            Text(template.cadence, style = TimeboxTheme.type.bodySmall, color = colors.on, modifier = Modifier.weight(1f))
            Text(
                template.nextOccurrence?.let { "Next $it" } ?: "No next occurrence",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
            )
        }
    }
}

@Composable
fun RecurringDetailScreen(
    state: RecurringUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (Int) -> Unit,
    onOpenTask: (Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    var confirmEnd by remember(state.selectedTemplate?.id) { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    when {
        state.detailLoading -> LoadingState()
        state.selectedTemplate == null -> ErrorState(state.error ?: "Recurring template not found.", onRetry)
        else -> RecurringDetailContent(
            template = state.selectedTemplate,
            busy = state.actionInProgress,
            onBack = onBack,
            onEdit = onEdit,
            onOpenTask = onOpenTask,
            onPause = onPause,
            onResume = onResume,
            onEnd = { confirmEnd = true },
            onRequestDelete = onRequestDelete,
        )
    }
    if (confirmEnd && state.selectedTemplate?.status != RecurrenceStatus.Ended) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End recurring template?") },
            text = {
                Text(
                    "Ending this template may remove today's and future generated tasks that are still pristine. " +
                        "Customized or completed tasks are preserved.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    onEnd()
                }) { Text("End template", color = TimeboxTheme.colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancel") } },
        )
    }
    state.pendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Permanently delete ${template.title}?") },
            text = { Text("Generated tasks are preserved and detached from this template. The template itself cannot be recovered.") },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text("Delete permanently", color = TimeboxTheme.colors.error) } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecurringDetailContent(
    template: RecurringTemplate,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onOpenTask: (Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            Spacer(Modifier.width(5.dp))
            Text("Recurring")
        }
        Text(template.status.label.uppercase(), style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
        Text(template.title, style = TimeboxTheme.type.screenTitle, color = colors.on)
        if (template.description.isNotBlank()) Text(template.description, style = TimeboxTheme.type.body, color = colors.onVariant)
        SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            DetailLine("Mode", template.mode.label)
            DetailLine("Cadence", template.cadence)
            DetailLine("Location", template.project?.name ?: "Admin")
            DetailLine("Task type", template.taskType?.name ?: "Unset")
            DetailLine("Starts", template.startDate.toString())
            DetailLine("Ends", template.endDate?.toString() ?: template.cycleLimit?.let { "$it cycles" } ?: "Never")
            DetailLine("Priorities", listOfNotNull(template.urgency?.label, template.importance?.label).joinToString(" · ").ifBlank { "Unset" })
        }
        SectionCard {
            SectionHeader("Next five windows")
            if (template.upcoming.isEmpty()) Text("No upcoming windows", Modifier.padding(16.dp), color = colors.onVariant)
            template.upcoming.forEach { window ->
                Text(
                    if (window.start == window.end) window.start.toString() else "${window.start} – ${window.end}",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.on,
                )
            }
        }
        if (template.checklistItems.isNotEmpty()) SectionCard {
            SectionHeader("Checklist")
            template.checklistItems.sortedBy { it.position }.forEach {
                Text("• ${it.title}", Modifier.padding(horizontal = 16.dp, vertical = 5.dp), color = colors.on)
            }
        }
        SectionCard {
            SectionHeader("Current and overdue tasks", "Open a generated task in Battle Plan.")
            if (template.currentTasks.isEmpty()) Text("No current tasks", Modifier.padding(16.dp), color = colors.onVariant)
            template.currentTasks.forEach { task ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenTask(task.id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(task.title, modifier = Modifier.weight(1f), color = colors.on)
                    Text(
                        if (task.overdue) "Overdue" else task.deadlineDate?.toString() ?: "Open",
                        style = TimeboxTheme.type.bodySmall,
                        color = if (task.overdue) colors.error else colors.onVariant,
                    )
                }
            }
        }
        PrimaryButton("Edit template", { onEdit(template.id) }, Modifier.fillMaxWidth(), enabled = !busy, leading = {
            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = colors.bg)
        })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (template.status == RecurrenceStatus.Active) LifecycleButton("Pause", Icons.Outlined.Pause, onPause, busy)
            if (template.status == RecurrenceStatus.Paused) LifecycleButton("Resume", Icons.Outlined.PlayArrow, onResume, busy)
            if (template.status != RecurrenceStatus.Ended) LifecycleButton("End", Icons.Outlined.StopCircle, onEnd, busy)
            if (template.status == RecurrenceStatus.Ended) LifecycleButton("Delete", Icons.Outlined.DeleteForever, onRequestDelete, busy, destructive = true)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant, modifier = Modifier.width(100.dp))
        Text(value, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.on, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LifecycleButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    busy: Boolean,
    destructive: Boolean = false,
) {
    TextButton(onClick = onClick, enabled = !busy) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (destructive) TimeboxTheme.colors.error else TimeboxTheme.colors.on)
    }
}

@Composable
fun RecurringEditorScreen(
    state: RecurringEditorUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onProject: (Int?) -> Unit,
    onTaskType: (Int?) -> Unit,
    onUrgency: (PriorityLevel?) -> Unit,
    onImportance: (PriorityLevel?) -> Unit,
    onMode: (RecurrenceMode) -> Unit,
    onFrequency: (RecurrenceFrequency) -> Unit,
    onInterval: (String) -> Unit,
    onToggleWeekday: (Int) -> Unit,
    onMonthDay: (String) -> Unit,
    onQuotaCount: (String) -> Unit,
    onStartDate: (String) -> Unit,
    onEndMode: (RecurrenceEndMode) -> Unit,
    onEndDate: (String) -> Unit,
    onCycleLimit: (String) -> Unit,
    onChecklist: (String) -> Unit,
    onRefreshPreview: () -> Unit,
    onSave: () -> Unit,
    onConfirmBackfill: () -> Unit,
    onDismissBackfill: () -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = { if (state.dirty && !state.saving) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)
    when {
        state.loading -> LoadingState()
        state.error != null -> ErrorState(state.error, onRetry)
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            TextButton(onClick = requestBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                Spacer(Modifier.width(5.dp))
                Text("Recurring")
            }
            Text(
                if (state.templateId == null) "New recurring template" else "Edit recurring template",
                style = TimeboxTheme.type.screenTitle,
                color = TimeboxTheme.colors.on,
            )
            OutlinedTextField(state.title, onTitle, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
            OutlinedTextField(state.description, onDescription, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3)
            RecurrenceMenu("Location", state.projects.firstOrNull { it.id == state.projectId }?.name ?: "Admin", listOf("Admin" to null) + state.projects.map { it.name to it.id }, onProject)
            RecurrenceMenu("Task type", state.taskTypes.firstOrNull { it.id == state.taskTypeId }?.name ?: "Unset", listOf("Unset" to null) + state.taskTypes.map { it.name to it.id }, onTaskType)
            RecurrenceMenu("Urgency", state.urgency?.label ?: "Unset", listOf("Unset" to null) + PriorityLevel.entries.map { it.label to it }, onUrgency)
            RecurrenceMenu("Importance", state.importance?.label ?: "Unset", listOf("Unset" to null) + PriorityLevel.entries.map { it.label to it }, onImportance)
            Text("Mode", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceMode.entries.forEach { mode ->
                    TimeboxChip(mode.label, state.mode == mode, { onMode(mode) })
                }
            }
            if (state.templateId != null) Text("Mode cannot be changed after creation.", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            Text("Period", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceFrequency.entries.forEach { frequency ->
                    TimeboxChip(frequency.label, state.frequency == frequency, { onFrequency(frequency) })
                }
            }
            if (state.mode == RecurrenceMode.Scheduled) {
                OutlinedTextField(state.interval, onInterval, Modifier.fillMaxWidth(), label = { Text("Repeat every (periods)") }, singleLine = true)
                if (state.frequency == RecurrenceFrequency.Weekly) WeekdayPicker(state.weekdays, onToggleWeekday)
                if (state.frequency == RecurrenceFrequency.Monthly) OutlinedTextField(state.monthDay, onMonthDay, Modifier.fillMaxWidth(), label = { Text("Day of month (1–31)") }, singleLine = true)
            } else {
                OutlinedTextField(state.quotaCount, onQuotaCount, Modifier.fillMaxWidth(), label = { Text("Times per period") }, singleLine = true)
                Text("Quota sessions are generated Ready to Plan. The server controls calendar period boundaries.", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            }
            OutlinedTextField(state.startDate, onStartDate, Modifier.fillMaxWidth(), label = { Text("Start date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
            RecurrenceMenu("Ends", state.endMode.label, RecurrenceEndMode.entries.map { it.label to it }, onEndMode)
            if (state.endMode == RecurrenceEndMode.EndDate) OutlinedTextField(state.endDate, onEndDate, Modifier.fillMaxWidth(), label = { Text("Inclusive end date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
            if (state.endMode == RecurrenceEndMode.CycleLimit) OutlinedTextField(state.cycleLimit, onCycleLimit, Modifier.fillMaxWidth(), label = { Text("Number of cycles") }, singleLine = true)
            OutlinedTextField(
                state.checklistText,
                onChecklist,
                Modifier.fillMaxWidth(),
                label = { Text("Checklist") },
                placeholder = { Text("One item per line") },
                minLines = 3,
            )
            PreviewCard(state, onRefreshPreview)
            PrimaryButton(
                if (state.templateId == null) "Create recurrence" else "Save changes",
                onSave,
                Modifier.fillMaxWidth(),
                enabled = !state.saving && state.title.isNotBlank(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    state.pendingBackfill?.let { detail ->
        AlertDialog(
            onDismissRequest = onDismissBackfill,
            title = { Text("Create past occurrences?") },
            text = { Text("This change will backfill ${detail.pastCycles} past cycles and create ${detail.pastTasks} tasks. These tasks are generated by the server.") },
            confirmButton = { TextButton(onClick = onConfirmBackfill) { Text("Backfill and save") } },
            dismissButton = { TextButton(onClick = onDismissBackfill) { Text("Cancel") } },
        )
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard changes?") },
        text = { Text("Your recurring template changes have not been saved.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
    )
}

@Composable
private fun PreviewCard(state: RecurringEditorUiState, onRefresh: () -> Unit) {
    val colors = TimeboxTheme.colors
    SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Server preview",
                style = TimeboxTheme.type.sectionTitle,
                color = colors.on,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh, enabled = !state.previewLoading) { Text("Refresh") }
        }
        when {
            state.previewLoading -> Text("Loading preview…", color = colors.onVariant)
            state.previewError != null -> Text(state.previewError, color = colors.error)
            state.preview == null -> Text("Complete a valid rule to preview it.", color = colors.onVariant)
            else -> {
                Text(
                    "Past cycles: ${state.preview.pastCycles} · Tasks if backfilled: ${state.preview.pastTasks}",
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                )
                Spacer(Modifier.height(8.dp))
                state.preview.upcoming.forEach { window ->
                    Text(
                        if (window.start == window.end) window.start.toString() else "${window.start} – ${window.end}",
                        Modifier.padding(vertical = 3.dp),
                        style = TimeboxTheme.type.bodySmall,
                        color = colors.on,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    Column {
        Text("Weekdays", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEachIndexed { index, name -> TimeboxChip(name, index in selected, { onToggle(index) }) }
        }
    }
}

@Composable
private fun <T> RecurrenceMenu(label: String, selected: String, values: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        TextButton(onClick = { expanded = true }) { Text(selected) }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { (name, value) -> DropdownMenuItem({ Text(name) }, { expanded = false; onSelect(value) }) }
        }
    }
}

private val RecurrenceStatus.label: String get() = wire.replaceFirstChar(Char::uppercase)
private val RecurrenceMode.label: String get() = wire.replaceFirstChar(Char::uppercase)
private val RecurrenceFrequency.label: String get() = wire.replaceFirstChar(Char::uppercase)
private val PriorityLevel.label: String get() = wire.replaceFirstChar(Char::uppercase)
private val RecurrenceEndMode.label: String get() = when (this) {
    RecurrenceEndMode.Never -> "Never"
    RecurrenceEndMode.EndDate -> "On a date"
    RecurrenceEndMode.CycleLimit -> "After cycles"
}
