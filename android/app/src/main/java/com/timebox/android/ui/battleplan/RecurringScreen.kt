package com.timebox.android.ui.battleplan

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Repeat
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.EmptyStateCard
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.SectionCard
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
internal val RecurrenceFrequency.label: String get() = wire.replaceFirstChar(Char::uppercase)
internal val PriorityLevel.label: String get() = wire.replaceFirstChar(Char::uppercase)
internal val RecurrenceEndMode.label: String get() = when (this) {
    RecurrenceEndMode.Never -> "Never"
    RecurrenceEndMode.EndDate -> "On a date"
    RecurrenceEndMode.CycleLimit -> "After cycles"
}
