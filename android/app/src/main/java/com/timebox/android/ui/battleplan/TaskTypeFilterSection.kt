package com.timebox.android.ui.battleplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.BattleTask
import com.timebox.android.data.TaskType
import com.timebox.android.data.rankTaskTypes
import com.timebox.android.data.taskTypePathParts
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

/** Minimum comfortable tap height for a result row. */
private val RowHeight = 44.dp

/** Five rows of results before the list scrolls inside the filter sheet. */
private val ListMaxHeight = RowHeight * 5

private const val UnspecifiedName = "unspecified"

/**
 * The filter sheet's Task Type section: chosen types as removable chips, then a search
 * over existing types with a bounded, multi-select result list. It never creates types.
 *
 * @param tasks the tasks whose counts each row shows, before any other filter applies.
 */
@Composable
internal fun TaskTypeFilterSection(
    taskTypes: List<TaskType>,
    tasks: List<BattleTask>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val colors = TimeboxTheme.colors
    val types = remember(taskTypes) { taskTypes.filter { it.name != UnspecifiedName } }
    val results = remember(types, query) { rankTaskTypes(types, query) }
    val counts = remember(types, tasks) {
        types.associate { type ->
            type.id to tasks.count { task -> task.taskType?.let { it.id == type.id || it.isUnder(type) } == true }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        SelectedTypeChips(types, selected, onToggle)
        FilterSearchField(query) { query = it }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = ListMaxHeight)
                .clip(TimeboxShapes.field)
                .background(colors.bg)
                .border(1.dp, colors.hairline, TimeboxShapes.field)
                .verticalScroll(rememberScrollState()),
        ) {
            if (results.isEmpty()) {
                Text(
                    text = if (types.isEmpty()) "No task types yet." else "No type matches that path.",
                    style = TimeboxTheme.type.bodySmall.copy(fontSize = 12.5.sp),
                    color = colors.onVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
            results.forEach { type ->
                val id = type.id.toString()
                TypeResultRow(
                    type = type,
                    chosen = id in selected,
                    includedVia = if (id in selected) null else coveringTaskType(type, selected, types)?.name,
                    count = counts[type.id] ?: 0,
                    onToggle = { onToggle(id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedTypeChips(types: List<TaskType>, selected: Set<String>, onToggle: (String) -> Unit) {
    val chosen = types.filter { it.id.toString() in selected }.sortedBy { it.name }
    if (chosen.isEmpty()) return
    val colors = TimeboxTheme.colors
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chosen.forEach { type ->
            val subtypes = types.count { it.isUnder(type) }
            val parts = taskTypePathParts(type.name)
            val description = if (subtypes > 0) "${type.name} and $subtypes sub-types" else type.name
            Row(
                Modifier
                    .height(34.dp)
                    .clip(TimeboxShapes.chip)
                    .background(colors.selected)
                    .border(1.dp, colors.outlineVariant, TimeboxShapes.chip)
                    .clickable(onClickLabel = "Remove $description") { onToggle(type.id.toString()) }
                    .semantics { contentDescription = description }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(twoTonePath(parts.ancestors, parts.leaf))
                        // The count says the chip reaches past the one type it names.
                        if (subtypes > 0) withStyle(SpanStyle(color = colors.onVariant)) { append("  +$subtypes") }
                    },
                    style = TimeboxTheme.type.bodySmall.copy(fontSize = 12.5.sp),
                    color = colors.on,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Outlined.Close, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun TypeResultRow(type: TaskType, chosen: Boolean, includedVia: String?, count: Int, onToggle: () -> Unit) {
    val colors = TimeboxTheme.colors
    val included = includedVia != null
    val parts = taskTypePathParts(type.name)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (chosen) colors.surf else Color.Transparent)
            // A covered row is already matched by its ancestor; clear that ancestor to narrow.
            .toggleable(value = chosen || included, enabled = !included, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = RowHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = twoTonePath(parts.ancestors, parts.leaf),
                style = TimeboxTheme.type.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (includedVia != null) {
                Text(
                    text = "Included via $includedVia",
                    style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp),
                    color = colors.onVariant,
                )
            }
        }
        Text(count.toString(), style = TimeboxTheme.type.mono, color = colors.onVariant)
        CheckBoxMark(chosen = chosen, included = included)
    }
}

@Composable
private fun CheckBoxMark(chosen: Boolean, included: Boolean) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(20.dp)
            .clip(shape)
            .background(if (chosen) colors.tertiary else Color.Transparent)
            .border(1.5.dp, if (chosen || included) colors.tertiary else colors.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (chosen || included) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = if (chosen) colors.lowest else colors.tertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun FilterSearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TimeboxTheme.type.body.copy(color = colors.on),
        cursorBrush = SolidColor(colors.planned),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            // Uri keeps `/` on the main keyboard plane, as in the Task Type picker.
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Search,
        ),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(TimeboxShapes.field)
                    .background(colors.bg)
                    .border(1.dp, colors.hairline, TimeboxShapes.field)
                    .padding(start = 12.dp, end = if (query.isEmpty()) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search types", style = TimeboxTheme.type.body, color = colors.onVariant)
                    field()
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.size(44.dp).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Cancel, contentDescription = "Clear search", tint = colors.onVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun twoTonePath(ancestors: String, leaf: String): AnnotatedString {
    val colors = TimeboxTheme.colors
    return buildAnnotatedString {
        if (ancestors.isNotEmpty()) withStyle(SpanStyle(color = colors.onVariant)) { append(ancestors) }
        withStyle(SpanStyle(color = colors.on, fontWeight = FontWeight.Medium)) { append(leaf) }
    }
}
