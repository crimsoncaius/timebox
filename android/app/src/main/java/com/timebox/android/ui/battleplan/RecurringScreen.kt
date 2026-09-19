package com.timebox.android.ui.battleplan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.RecurrenceStatus
import com.timebox.android.data.RecurringTemplate
import com.timebox.android.ui.formatMinuteLabel24
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.EmptyStateCard
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.SectionCard
import com.timebox.android.ui.components.SectionHeader
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun RecurringScreen(
    state: RecurringUiState,
    onRetry: () -> Unit,
    onSelectStatus: (RecurrenceStatus) -> Unit,
    onNew: () -> Unit,
    onOpen: (Int) -> Unit,
    onRequestDelete: (RecurringTemplate) -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    navigationState: BattlePlanUiState = BattlePlanUiState(),
    onSelectScope: (BattlePlanScope) -> Unit = {},
    onSelectCollection: (com.timebox.android.data.TaskCollection) -> Unit = {},
    onReorderProjects: (List<Int>) -> Unit = {},
    onEditProject: (com.timebox.android.data.Project) -> Unit = {},
    onPrepareDeleteProject: (com.timebox.android.data.Project) -> Unit = {},
    onNewProject: () -> Unit = {},
    onOpenTaskTypes: () -> Unit = {},
) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            TaskNavigationMenu(
                state = navigationState, recurring = true,
                onSelectScope = onSelectScope, onSelectCollection = onSelectCollection,
                onReorderProjects = onReorderProjects, onEditProject = onEditProject,
                onPrepareDeleteProject = onPrepareDeleteProject, onNewProject = onNewProject,
                onOpenTaskTypes = onOpenTaskTypes,
            )
        }
        RecurringStatusTabs(state.selectedStatus, onSelectStatus)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.templates.size} series",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                "New recurrence",
                onNew,
                leading = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = colors.onAction) },
            )
        }
        when {
            state.loading -> LoadingState()
            state.error != null && state.templates.isEmpty() -> ErrorState(state.error, onRetry)
            state.templates.isEmpty() -> EmptyStateCard(
                title = "No ${state.selectedStatus.label.lowercase()} series",
                description = if (state.selectedStatus == RecurrenceStatus.Active) {
                    "Create a recurrence to generate Tasks on a schedule or quota."
                } else {
                    "Recurring Task Series appear here when their lifecycle changes."
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.templates, key = { it.id }) { template ->
                    RecurringTemplateRow(template, onOpen, onRequestDelete)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    state.pendingDelete?.let { template ->
        RecurringDeleteDialog(template, onDismissDelete, onConfirmDelete)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringTemplateRow(
    template: RecurringTemplate,
    onOpen: (Int) -> Unit,
    onRequestDelete: (RecurringTemplate) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var menu by remember(template.id) { mutableStateOf(false) }
    val ended = template.status == RecurrenceStatus.Ended
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TimeboxShapes.card)
            .background(mobileTaskCardSurface(colors))
            .border(1.dp, colors.hairline, TimeboxShapes.card)
            .clickable { onOpen(template.id) }
            .padding(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                template.title,
                Modifier.weight(1f).padding(start = 8.dp, top = 11.dp, end = 4.dp, bottom = 9.dp),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.on,
            )
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(TimeboxDimens.touchTarget),
                ) {
                    Icon(Icons.Outlined.MoreVert, "Actions for ${template.title}", tint = colors.onVariant)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menu = false
                            onRequestDelete(template)
                        },
                        enabled = ended,
                    )
                }
            }
        }
        Column(
            Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                template.taskType?.let { RecurringListChip(Icons.AutoMirrored.Outlined.Label, it.name, "Task type") }
                RecurringListChip(Icons.Outlined.Repeat, template.cadence, "Cadence")
                RecurringListChip(
                    Icons.Outlined.CalendarToday,
                    recurringListTimingFact(template),
                    if (template.mode == RecurrenceMode.Quota) "Quota period" else "Next Task Occurrence",
                )
            }
            if (template.status != RecurrenceStatus.Active) {
                Text(
                    template.status.label,
                    modifier = Modifier
                        .clip(TimeboxShapes.chip)
                        .background(if (ended) colors.surf else colors.error.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    color = if (ended) colors.onVariant else colors.error,
                )
            }
        }
    }
}

@Composable
private fun RecurringListChip(icon: ImageVector, label: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, description, Modifier.size(12.dp), tint = TimeboxTheme.colors.onVariant)
        Text(label, fontSize = 11.sp, lineHeight = 16.sp, color = TimeboxTheme.colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun RecurringDeleteDialog(
    template: RecurringTemplate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permanently delete ${template.title}?") },
        text = { Text("Generated tasks are preserved and detached from this template. The template itself cannot be recovered.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete permanently", color = TimeboxTheme.colors.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
        RecurringDeleteDialog(template, onDismissDelete, onConfirmDelete)
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
    val quota = template.mode == RecurrenceMode.Quota
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val extras = listOfNotNull(
        template.description.takeIf { it.isNotBlank() },
        template.taskType?.name,
        template.urgency?.label?.let { "$it urgency" },
        template.importance?.label?.let { "$it importance" },
    )
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            Spacer(Modifier.width(5.dp))
            Text("Recurring")
        }
        Text(template.status.label.uppercase(), style = TimeboxTheme.type.kicker, color = colors.onVariant)
        Text(template.title, style = TimeboxTheme.type.screenTitle, color = colors.on)
        Text("${template.cadence} · from ${template.startDate.format(formatter)}", style = TimeboxTheme.type.body, color = colors.onVariant)
        SectionCard {
            SectionHeader(
                if (quota) "This period's Session Tasks" else "Current Task Occurrence",
                if (quota) "Independently completable work that counts toward this Quota Tracker."
                else "This Recurring Task Series's current work.",
            )
            if (template.currentTasks.isEmpty()) {
                Text("No current tasks", Modifier.padding(16.dp), color = colors.onVariant)
            } else {
                template.currentTasks.forEach { task ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenTask(task.id) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(task.title, modifier = Modifier.weight(1f), color = colors.on)
                        Text(
                            if (task.overdue) "Overdue" else task.deadlineDate?.format(formatter) ?: "Open",
                            style = TimeboxTheme.type.bodySmall,
                            color = if (task.overdue) colors.error else colors.onVariant,
                        )
                    }
                }
            }
        }
        SectionCard {
            SectionHeader("Upcoming")
            if (template.upcoming.isEmpty()) Text("No upcoming windows", Modifier.padding(16.dp), color = colors.onVariant)
            template.upcoming.forEach { window ->
                Text(
                    if (window.start == window.end) window.start.format(formatter) else "${window.start.format(formatter)} – ${window.end.format(formatter)}",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.on,
                )
            }
        }
        Text("Series settings", style = TimeboxTheme.type.kicker, color = colors.onVariant)
        SectionCard {
            SectionHeader("Subtasks")
            val items = template.checklistItems.sortedBy { it.position }
            if (items.isEmpty()) {
                Text("No subtasks", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            } else {
                items.forEach {
                    Text(it.title, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), color = colors.on)
                }
        }
        }
        template.preplanningSchedule?.let { schedule ->
            SectionCard(Modifier.semantics { contentDescription = "Recurring Pre-planning Schedule" }) {
                SectionHeader("Recurring Pre-planning Schedule")
                schedule.slots.sortedBy { it.position }.forEach { slot ->
                    val weekday = slot.weekday?.let { preplanningWeekdayLabel(it) }
                    val times = "${formatMinuteLabel24(slot.startMinute)}–${formatMinuteLabel24(slot.endMinute)}"
                    Text(
                        listOfNotNull(weekday, times).joinToString(" · "),
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        style = TimeboxTheme.type.bodySmall,
                        color = colors.on,
                    )
                }
                if (schedule.unavailableSlots.isNotEmpty()) {
                    Text(
                        "Unavailable configured slots",
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        style = TimeboxTheme.type.bodySmall,
                        color = colors.error,
                    )
                    schedule.unavailableSlots.forEach { slot ->
                        val times = "${formatMinuteLabel24(slot.startMinute)}–${formatMinuteLabel24(slot.endMinute)}"
                        Text(
                            "Unavailable on ${slot.date}, $times",
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics {
                                    contentDescription = "Unavailable configured slot on ${slot.date}, $times"
                                },
                            style = TimeboxTheme.type.bodySmall,
                            color = colors.error,
                        )
                    }
                }
            }
        }
        SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            SectionHeader("Notes & more")
            Column(Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (template.description.isNotBlank()) Text(template.description, style = TimeboxTheme.type.body, color = colors.onVariant)
                DetailLine("Mode", template.mode.label)
                DetailLine("Ends", template.endDate?.format(formatter) ?: template.cycleLimit?.let { "$it cycles" } ?: "Never")
                extras.filterNot { it == template.description }.takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
            }
        }
        PrimaryButton("Edit series", { onEdit(template.id) }, Modifier.fillMaxWidth(), enabled = !busy, leading = {
            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = colors.onAction)
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

private fun preplanningWeekdayLabel(weekday: Int): String =
    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").getOrElse(weekday) { "" }

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
    onTaskType: (Int?) -> Unit,
    onCreateTaskType: (String) -> Unit = {},
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
    onKeepUnfinishedOverdue: (Boolean) -> Unit = {},
    onPreplanningEnabled: (Boolean) -> Unit = {},
    onAddPreplanningSlot: () -> Unit = {},
    onRemovePreplanningSlot: (Int) -> Unit = {},
    onPreplanningStart: (Int, String) -> Unit = { _, _ -> },
    onPreplanningEnd: (Int, String) -> Unit = { _, _ -> },
    onPreplanningWeekday: (Int, Int) -> Unit = { _, _ -> },
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
        else -> RecurringCreationContent(
                state = state,
                onBack = requestBack,
                onTitle = onTitle,
                onDescription = onDescription,
                onTaskType = onTaskType,
                onCreateTaskType = onCreateTaskType,
                onUrgency = onUrgency,
                onImportance = onImportance,
                onMode = onMode,
                onFrequency = onFrequency,
                onInterval = onInterval,
                onToggleWeekday = onToggleWeekday,
                onMonthDay = onMonthDay,
                onQuotaCount = onQuotaCount,
                onStartDate = onStartDate,
                onEndMode = onEndMode,
                onEndDate = onEndDate,
                onCycleLimit = onCycleLimit,
                onChecklist = onChecklist,
                onKeepUnfinishedOverdue = onKeepUnfinishedOverdue,
                onPreplanningEnabled = onPreplanningEnabled,
                onAddPreplanningSlot = onAddPreplanningSlot,
                onRemovePreplanningSlot = onRemovePreplanningSlot,
                onPreplanningStart = onPreplanningStart,
                onPreplanningEnd = onPreplanningEnd,
                onPreplanningWeekday = onPreplanningWeekday,
                onRefreshPreview = onRefreshPreview,
                onSave = onSave,
        )
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
internal fun PreviewCard(state: RecurringEditorUiState, onRefresh: () -> Unit) {
    val colors = TimeboxTheme.colors
    SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Upcoming",
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
internal fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    Column {
        Text("Weekdays", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEachIndexed { index, name -> TimeboxChip(name, index in selected, { onToggle(index) }) }
        }
    }
}

@Composable
internal fun <T> RecurrenceMenu(label: String, selected: String, values: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        TextButton(onClick = { expanded = true }) { Text(selected) }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { (name, value) -> DropdownMenuItem({ Text(name) }, { expanded = false; onSelect(value) }) }
        }
    }
}

internal fun recurringListDateLabel(date: LocalDate?): String? =
    date?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))

internal fun recurringListTimingFact(template: RecurringTemplate): String {
    val date = recurringListDateLabel(template.nextOccurrence)
    return if (template.mode == RecurrenceMode.Quota) {
        date?.let { "Period ends $it" } ?: "No current period"
    } else {
        date?.let { "Next $it" } ?: "No next occurrence"
    }
}

private val RecurrenceStatus.label: String get() = wire.replaceFirstChar(Char::uppercase)
private val RecurrenceMode.label: String get() = wire.replaceFirstChar(Char::uppercase)
internal val RecurrenceFrequency.label: String get() = wire.replaceFirstChar(Char::uppercase)
internal val PriorityLevel.label: String get() = wire.replaceFirstChar(Char::uppercase)
internal val RecurrenceEndMode.label: String get() = when (this) {
    RecurrenceEndMode.Never -> "Never"
    RecurrenceEndMode.EndDate -> "On a date"
    RecurrenceEndMode.CycleLimit -> "After cycles"
}
