package com.timebox.android.ui.battleplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.*
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant

// Task type filter study: an isolated, local-state design experiment reachable only by a
// debug route. Nothing here writes to the repository or the saved Battle Plan view.

private val manyPaths = listOf(
    "coding", "coding/ai", "coding/ai/agents", "coding/ai/evals", "coding/android", "coding/backend",
    "coding/backend/migrations", "coding/web", "coding/infra",
    "writing", "writing/blog", "writing/docs", "writing/newsletter",
    "admin", "admin/finance", "admin/email", "admin/errands",
    "health", "health/gym", "health/running", "health/meal prep",
    "learning", "learning/japanese", "learning/reading", "learning/courses",
    "planning", "planning/weekly review", "planning/travel",
    "home", "home/cleaning", "home/repairs",
    "meetings", "meetings/1:1", "meetings/standup",
    "design", "design/ux", "design/visual", "research", "social",
)

private val sampleTasks = listOf(
    Triple("Wire the agent eval harness", "coding/ai/evals", TaskStatus.InProgress),
    Triple("Prompt-cache the planner agent", "coding/ai/agents", TaskStatus.Open),
    Triple("Try tool-use for the Assistant", "coding/ai/agents", TaskStatus.Open),
    Triple("Fix filter sheet overflow", "coding/android", TaskStatus.Open),
    Triple("Keyboard insets on composer", "coding/android", TaskStatus.Completed),
    Triple("Add task type index", "coding/backend/migrations", TaskStatus.Open),
    Triple("Paginate trash endpoint", "coding/backend", TaskStatus.Blocked),
    Triple("Rotate Railway secrets", "coding/infra", TaskStatus.Open),
    Triple("Refactor day view reducers", "coding", TaskStatus.Open),
    Triple("Draft timeboxing essay", "writing/blog", TaskStatus.InProgress),
    Triple("Update README screenshots", "writing/docs", TaskStatus.Open),
    Triple("September newsletter", "writing/newsletter", TaskStatus.Open),
    Triple("File quarterly taxes", "admin/finance", TaskStatus.Open),
    Triple("Inbox zero", "admin/email", TaskStatus.Completed),
    Triple("Pick up dry cleaning", "admin/errands", TaskStatus.Open),
    Triple("Renew passport", "admin", TaskStatus.Blocked),
    Triple("Leg day", "health/gym", TaskStatus.Open),
    Triple("10k tempo run", "health/running", TaskStatus.Open),
    Triple("Sunday meal prep", "health/meal prep", TaskStatus.Open),
    Triple("WaniKani reviews", "learning/japanese", TaskStatus.InProgress),
    Triple("Finish Designing Data-Intensive Apps", "learning/reading", TaskStatus.Open),
    Triple("Weekly review", "planning/weekly review", TaskStatus.Open),
    Triple("Book Kyoto stay", "planning/travel", TaskStatus.Open),
    Triple("Fix the leaking tap", "home/repairs", TaskStatus.Open),
    Triple("1:1 with Sam", "meetings/1:1", TaskStatus.Completed),
    Triple("Card compact layout pass", "design/ux", TaskStatus.Open),
    Triple("Compare local-first sync libraries", "research", TaskStatus.Open),
    Triple("Call grandma", null, TaskStatus.Open),
    Triple("Sort the garage", null, TaskStatus.Open),
)

private data class FilterFixture(val types: List<TaskType>, val tasks: List<BattleTask>)

private fun fixture(data: String): FilterFixture {
    val paths = if (data == "few") manyPaths.filter { '/' !in it }.take(6) else manyPaths
    fun pathFor(path: String?) = when {
        path == null -> null
        data == "few" -> path.substringBefore('/').takeIf { it in paths }
        else -> path
    }
    val placed = sampleTasks.map { (title, path, status) -> Triple(title, pathFor(path), status) }
    val types = paths.mapIndexed { index, path ->
        val branch = placed.count { it.second == path || it.second?.startsWith("$path/") == true }
        TaskType(id = index + 1, name = path, usageCount = branch * 3 + index % 5, taskUsageCount = branch)
    }
    val byName = types.associateBy { it.name }
    val now = Instant.parse("2026-09-26T09:00:00Z")
    val tasks = placed.mapIndexed { index, (title, path, status) ->
        val type = path?.let(byName::get)
        BattleTask(
            id = 5000 + index, parentId = null, parentTitle = null, projectId = null, project = null,
            taskTypeId = type?.id, taskType = type, recurringTemplateId = null, recurringTemplateTitle = null,
            occurrenceKey = null, recurrenceKind = null, quotaPeriodStart = null, quotaPeriodEnd = null,
            expectedSessions = null, sessionIndex = null, quotaCompleted = null,
            title = title, description = "", readyToPlan = index % 3 == 0, status = status,
            urgency = null, importance = null, deadlineDate = null, deadlineAt = null,
            reminderAt = null, reminderDeliveredAt = null, position = index,
            archivedAt = null, deletedAt = null, createdAt = now, updatedAt = now, overdue = false,
            subtasks = emptyList(),
        )
    }
    return FilterFixture(types, tasks)
}

/** Directly chosen Task Types; a chosen parent covers its whole branch (except in Current). */
private data class TypeSelection(val ids: Set<Int> = emptySet(), val unset: Boolean = false) {
    val isEmpty get() = ids.isEmpty() && !unset
}

private fun TaskType.isUnder(ancestor: TaskType) = name.startsWith(ancestor.name + "/")

/** The chosen ancestor that covers [type], if [type] is not itself chosen. */
private fun coveringAncestor(type: TaskType, types: List<TaskType>, selection: TypeSelection): TaskType? =
    types.filter { it.id in selection.ids && type.isUnder(it) }.minByOrNull { it.name.length }

private fun toggleBranch(selection: TypeSelection, type: TaskType, types: List<TaskType>): TypeSelection =
    if (type.id in selection.ids) selection.copy(ids = selection.ids - type.id)
    // Choosing a parent absorbs any of its descendants that were chosen individually.
    else selection.copy(ids = selection.ids.filterNot { id -> types.first { it.id == id }.isUnder(type) }.toSet() + type.id)

private val variants = listOf("current" to "Current", "inline" to "Inline search", "drill" to "Drill-in", "tree" to "Tree")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTypeFilterPrototype(initialVariant: String = "inline", initialData: String = "many") {
    var variant by remember { mutableStateOf(initialVariant.takeIf { v -> variants.any { it.first == v } } ?: "inline") }
    var data by remember { mutableStateOf(initialData) }
    val fixture = remember(data) { fixture(data) }
    var selection by remember { mutableStateOf(TypeSelection()) }
    var urgency by remember { mutableStateOf(emptySet<String>()) }
    var importance by remember { mutableStateOf(emptySet<String>()) }
    var hideCompleted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(TaskStatus.Open) }
    val colors = TimeboxTheme.colors

    val effective = remember(selection, fixture, variant) {
        val ids = if (variant == "current") selection.ids
        else fixture.types.filter { it.id in selection.ids || coveringAncestor(it, fixture.types, selection) != null }.map { it.id }.toSet()
        ids.map(Int::toString).toSet() + if (selection.unset) setOf("unset") else emptySet()
    }
    val state = BattlePlanUiState(
        loading = false, taskTypes = fixture.types, tasks = fixture.tasks, selectedStatus = status,
        hideCompleted = hideCompleted, urgencyFilter = urgency, importanceFilter = importance,
        taskTypeFilter = effective, serverNow = Instant.parse("2026-09-26T09:00:00Z"),
    )
    val sectionProps = SectionProps(fixture.types, fixture.tasks, selection,
        onToggle = { selection = toggleBranch(selection, it, fixture.types) },
        onToggleUnset = { selection = selection.copy(unset = !selection.unset) },
        onClear = { selection = TypeSelection() })
    val section: (@Composable () -> Unit)? = when (variant) {
        "inline" -> { { InlineSearchSection(sectionProps) } }
        "drill" -> { { DrillInSection(sectionProps) } }
        "tree" -> { { TreeSection(sectionProps) } }
        else -> null
    }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(colors.low).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("TASK TYPE FILTER · PROTOTYPE — open Filters (funnel icon)", fontSize = 10.sp, color = colors.onVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                variants.forEach { (key, label) -> TimeboxChip(label, variant == key, { variant = key }, height = 32.dp) }
                Spacer(Modifier.width(6.dp))
                TimeboxChip("Many types", data == "many", { data = "many"; selection = TypeSelection() }, height = 32.dp)
                TimeboxChip("Few types", data == "few", { data = "few"; selection = TypeSelection() }, height = 32.dp)
                TextButton(onClick = { selection = TypeSelection(); urgency = emptySet(); importance = emptySet(); hideCompleted = false }) { Text("Reset") }
            }
        }
        CompositionLocalProvider(LocalPrototypeTaskTypeFilter provides section) {
            BattlePlanScreen(
                state = state,
                onRetry = {},
                filterActions = BattlePlanFilterActions(
                    selectStatus = { status = it },
                    toggleUrgency = { urgency = urgency.toggled(it) },
                    toggleImportance = { importance = importance.toggled(it) },
                    toggleTaskType = { raw ->
                        raw.toIntOrNull()?.let { id -> selection = selection.copy(ids = selection.ids.toggled(id)) }
                    },
                    clearFilters = { selection = TypeSelection(); urgency = emptySet(); importance = emptySet() },
                    setHideCompleted = { hideCompleted = it },
                ),
            )
        }
    }
}

private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value

private class SectionProps(
    val types: List<TaskType>,
    val tasks: List<BattleTask>,
    val selection: TypeSelection,
    val onToggle: (TaskType) -> Unit,
    val onToggleUnset: () -> Unit,
    val onClear: () -> Unit,
) {
    fun branchCount(type: TaskType) = tasks.count { it.taskType?.let { t -> t.id == type.id || t.isUnder(type) } == true }
    val unsetCount get() = tasks.count { it.taskTypeId == null }
    fun rowState(type: TaskType): RowState = when {
        type.id in selection.ids -> RowState.Selected
        coveringAncestor(type, types, selection) != null -> RowState.Included
        else -> RowState.None
    }
    fun ranked(query: String) = rankTaskTypes(types.filter { it.name != "unspecified" }, query)
}

private enum class RowState { None, Selected, Included }

// ---- Variant A: inline search -------------------------------------------------------

@Composable
private fun InlineSearchSection(p: SectionProps) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        SelectedChips(p)
        FilterSearchField(query, { query = it }, "Search types")
        Spacer(Modifier.height(8.dp))
        BoundedList(maxHeight = RowHeight * 5) {
            FlatResults(p, query)
        }
    }
}

// ---- Variant B: drill-in picker -----------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrillInSection(p: SectionProps) {
    val colors = TimeboxTheme.colors
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        SelectedChips(p)
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(TimeboxShapes.field)
                .border(1.dp, colors.hairline, TimeboxShapes.field)
                .clickable { query = ""; open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Label, null, tint = colors.onVariant, modifier = Modifier.size(18.dp))
            Text(
                if (p.selection.isEmpty) "Any type" else "Change types",
                style = TimeboxTheme.type.body, color = if (p.selection.isEmpty) colors.onVariant else colors.on,
                modifier = Modifier.weight(1f),
            )
            Text("${p.types.size} types", style = TimeboxTheme.type.mono, color = colors.onVariant)
            Icon(Icons.Outlined.ChevronRight, null, tint = colors.onVariant, modifier = Modifier.size(18.dp))
        }
    }
    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            containerColor = colors.lowest,
            scrimColor = colors.scrim,
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Task types", style = TimeboxTheme.type.sectionTitle.copy(fontSize = 19.sp), color = colors.on, modifier = Modifier.weight(1f))
                    if (!p.selection.isEmpty) TextButton(onClick = p.onClear) { Text("Clear", color = colors.planned) }
                    TextButton(onClick = { open = false }) { Text("Done", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(6.dp))
                SelectedChips(p)
                FilterSearchField(query, { query = it }, "Search types")
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().weight(1f).clip(TimeboxShapes.field).background(colors.bg)
                        .border(1.dp, colors.hairline, TimeboxShapes.field).verticalScroll(rememberScrollState()),
                ) { FlatResults(p, query) }
                Spacer(Modifier.height(12.dp).navigationBarsPadding())
            }
        }
    }
}

// ---- Variant C: collapsible tree ----------------------------------------------------

@Composable
private fun TreeSection(p: SectionProps) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(emptySet<Int>()) }
    val types = p.types.filter { it.name != "unspecified" }
    fun children(of: TaskType?) = types.filter {
        if (of == null) '/' !in it.name else it.isUnder(of) && it.depth == of.depth + 1
    }
    Column(Modifier.fillMaxWidth()) {
        SelectedChips(p)
        FilterSearchField(query, { query = it }, "Search types")
        Spacer(Modifier.height(8.dp))
        BoundedList(maxHeight = RowHeight * 6) {
            if (query.isNotBlank()) {
                FlatResults(p, query)
            } else {
                @Composable
                fun branch(type: TaskType, level: Int) {
                    val kids = children(type).sortedBy { it.name }
                    val isOpen = type.id in expanded
                    TypeRow(
                        text = if (level == 0) AnnotatedString(type.name) else AnnotatedString(type.leaf),
                        state = p.rowState(type),
                        count = p.branchCount(type),
                        includedVia = coveringAncestor(type, p.types, p.selection)?.name,
                        indent = (level * 22).dp,
                        onClick = { p.onToggle(type) },
                        leading = {
                            if (kids.isNotEmpty()) {
                                Box(
                                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                        .clickable { expanded = expanded.toggled(type.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.ChevronRight,
                                        contentDescription = if (isOpen) "Collapse ${type.name}" else "Expand ${type.name}",
                                        tint = TimeboxTheme.colors.onVariant,
                                        modifier = Modifier.size(18.dp).rotate(if (isOpen) 90f else 0f),
                                    )
                                }
                            } else Spacer(Modifier.width(32.dp))
                        },
                    )
                    if (isOpen) kids.forEach { branch(it, level + 1) }
                }
                children(null).sortedWith(compareByDescending<TaskType> { p.branchCount(it) }.thenBy { it.name })
                    .forEach { branch(it, 0) }
                UnsetRow(p)
            }
        }
    }
}

// ---- Shared pieces ------------------------------------------------------------------

private val RowHeight = 44.dp

@Composable
private fun FlatResults(p: SectionProps, query: String) {
    val results = p.ranked(query)
    if (results.isEmpty()) {
        Text(
            "No type matches that path.",
            style = TimeboxTheme.type.bodySmall.copy(fontSize = 12.5.sp),
            color = TimeboxTheme.colors.onVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
        )
    }
    results.forEach { type ->
        val parts = taskTypePathParts(type.name)
        TypeRow(
            text = twoTone(parts.ancestors, parts.leaf),
            state = p.rowState(type),
            count = p.branchCount(type),
            includedVia = coveringAncestor(type, p.types, p.selection)?.name,
            onClick = { p.onToggle(type) },
        )
    }
    if (query.isBlank()) UnsetRow(p)
}

@Composable
private fun BoundedList(maxHeight: Dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = TimeboxTheme.colors
    Column(
        Modifier.fillMaxWidth().heightIn(max = maxHeight).clip(TimeboxShapes.field).background(colors.bg)
            .border(1.dp, colors.hairline, TimeboxShapes.field).verticalScroll(rememberScrollState()),
        content = content,
    )
}

@Composable
private fun TypeRow(
    text: AnnotatedString,
    state: RowState,
    count: Int,
    onClick: () -> Unit,
    includedVia: String? = null,
    indent: Dp = 0.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .background(if (state == RowState.Selected) colors.surf else Color.Transparent)
            .clickable(enabled = state != RowState.Included, onClick = onClick)
            .heightIn(min = RowHeight)
            .padding(start = if (leading != null) 4.dp + indent else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text, style = TimeboxTheme.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (state == RowState.Included) colors.onVariant else colors.on,
            )
            if (state == RowState.Included && includedVia != null) {
                Text("Included via $includedVia", style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp), color = colors.onVariant)
            }
        }
        Text(count.toString(), style = TimeboxTheme.type.mono, color = colors.onVariant)
        CheckMark(state)
    }
}

@Composable
private fun UnsetRow(p: SectionProps) {
    TypeRow(
        text = AnnotatedString("Unset"),
        state = if (p.selection.unset) RowState.Selected else RowState.None,
        count = p.unsetCount,
        onClick = p.onToggleUnset,
    )
}

@Composable
private fun CheckMark(state: RowState) {
    val colors = TimeboxTheme.colors
    val on = state != RowState.None
    Box(
        Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
            .background(if (state == RowState.Selected) colors.tertiary else Color.Transparent)
            .border(1.5.dp, if (on) colors.tertiary else colors.outline, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(
            Icons.Outlined.Check, null,
            tint = if (state == RowState.Selected) colors.lowest else colors.tertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedChips(p: SectionProps) {
    if (p.selection.isEmpty) return
    val colors = TimeboxTheme.colors
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        p.types.filter { it.id in p.selection.ids }.sortedBy { it.name }.forEach { type ->
            val subtypes = p.types.count { it.isUnder(type) }
            val parts = taskTypePathParts(type.name)
            RemovableChip(
                text = buildAnnotatedString {
                    append(twoTone(parts.ancestors, parts.leaf))
                    if (subtypes > 0) withStyle(SpanStyle(color = colors.onVariant)) { append("  +$subtypes") }
                },
                description = if (subtypes > 0) "${type.name} and $subtypes sub-types" else type.name,
                onRemove = { p.onToggle(type) },
            )
        }
        if (p.selection.unset) RemovableChip(AnnotatedString("Unset"), "Unset", p.onToggleUnset)
    }
}

@Composable
private fun RemovableChip(text: AnnotatedString, description: String, onRemove: () -> Unit) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.height(34.dp).clip(TimeboxShapes.chip).background(colors.selected)
            .border(1.dp, colors.outlineVariant, TimeboxShapes.chip)
            .clickable(onClickLabel = "Remove $description", onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = TimeboxTheme.type.bodySmall.copy(fontSize = 12.5.sp), color = colors.on, maxLines = 1)
        Icon(Icons.Outlined.Close, null, tint = colors.onVariant, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun FilterSearchField(query: String, onChange: (String) -> Unit, placeholder: String) {
    val colors = TimeboxTheme.colors
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TimeboxTheme.type.body.copy(color = colors.on),
        cursorBrush = SolidColor(colors.planned),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None, autoCorrect = false,
            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search,
        ),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(TimeboxShapes.field).background(colors.bg)
                    .border(1.dp, colors.hairline, TimeboxShapes.field)
                    .padding(start = 12.dp, end = if (query.isEmpty()) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Search, null, tint = colors.onVariant, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, style = TimeboxTheme.type.body, color = colors.onVariant)
                    field()
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.size(44.dp).clickable { onChange("") }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Cancel, "Clear search", tint = colors.onVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun twoTone(ancestors: String, leaf: String): AnnotatedString {
    val colors = TimeboxTheme.colors
    return buildAnnotatedString {
        if (ancestors.isNotEmpty()) withStyle(SpanStyle(color = colors.onVariant)) { append(ancestors) }
        withStyle(SpanStyle(color = colors.on, fontWeight = FontWeight.Medium)) { append(leaf) }
    }
}
