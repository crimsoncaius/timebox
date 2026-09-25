package com.timebox.android.ui.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.timebox.android.data.remote.TaskTypeMergePreview
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RenameTaskTypeSheet(state: TypesUiState, onChange: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit, onMerge: () -> Unit = {}, onBack: () -> Unit = {}) {
    val type = state.renaming ?: return
    val colors = TimeboxTheme.colors
    val busy by rememberUpdatedState(state.saving)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !busy })
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val types = state.groups.flatMap { it.items }
    val affected = types.filter { it.id == type.id || it.name.startsWith("${type.name}/") }
    val path = state.renameInput.split('/').joinToString("/") { it.trim().lowercase(java.util.Locale.ROOT) }
    val target = types.firstOrNull { it.name == path && it.id != type.id }
    val blocked = target != null && (target.name == "unspecified" || target.name.startsWith("${type.name}/") || type.name.startsWith("${target.name}/"))
    val valid = path.split('/').none { it.isEmpty() } && !blocked
    LaunchedEffect(type.id, state.mergePreview != null) {
        if (state.mergePreview == null) {
            snapshotFlow { sheet.currentValue }.first { it == SheetValue.Expanded }
            focus.requestFocus()
        } else keyboard?.hide()
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onCancel() }, sheetState = sheet, sheetMaxWidth = 640.dp,
        shape = TimeboxShapes.sheet, containerColor = colors.sheet.copy(alpha = 1f), contentColor = colors.on, scrimColor = colors.scrim) {
        val preview = state.mergePreview
        if (preview != null) MergeSummary(preview, state.saving, state.renameError, onMerge, onBack)
        else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Rename task type", style = TimeboxTheme.type.sectionTitle)
            Text(type.name, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            OutlinedTextField(state.renameInput, onChange, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Task type path") }, singleLine = true, enabled = !busy, isError = state.renameError != null,
                shape = TimeboxShapes.field, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!busy && valid) onSave() }))
            when {
                blocked -> Text("Choose a separate branch. A type cannot merge with its ancestors, descendants, or unspecified.", color = colors.error)
                target != null -> Column(Modifier.fillMaxWidth().background(colors.low, TimeboxShapes.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${target.name} already exists.", style = MaterialTheme.typography.titleMedium)
                    Text("Combine these categories. Review the changes before confirming.", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    Text("Updates this type" + (if (affected.size > 1) " and ${affected.size - 1} nested types" else "") + " everywhere, including past work.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    Column(Modifier.fillMaxWidth().background(colors.low, TimeboxShapes.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AFTER SAVING", style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
                        if (!valid) Text("Enter a path with no empty segments.", style = TimeboxTheme.type.bodySmall)
                        else affected.forEach { Text(path + it.name.removePrefix(type.name), style = TimeboxTheme.type.bodySmall) }
                    }
                }
            }
            state.renameError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onSave, enabled = !busy && valid) { Text(if (busy) "Loading…" else if (target != null) "Review merge" else "Save") }
            }
        }
    }
}

@Composable
private fun MergeSummary(preview: TaskTypeMergePreview, busy: Boolean, error: String?, onMerge: () -> Unit, onBack: () -> Unit) {
    val colors = TimeboxTheme.colors
    var details by remember(preview.previewToken) { mutableStateOf(preview.changes.size > 1) }
    val counts = listOf("Tasks" to preview.taskCount, "Planned Blocks" to preview.plannedBlockCount, "Actual Blocks" to preview.actualBlockCount, "Recurring Task Series" to preview.recurringSeriesCount)
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.93f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Merge task types", style = MaterialTheme.typography.headlineSmall)
            Text("${preview.sourceName} → ${preview.targetName}", style = MaterialTheme.typography.titleMedium, color = colors.onVariant)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.fillMaxWidth().background(colors.low, TimeboxShapes.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CATEGORY TO KEEP", style = MaterialTheme.typography.labelSmall, color = colors.onVariant)
                Text(preview.targetName, style = MaterialTheme.typography.titleLarge)
                Text("All work from ${preview.sourceName} joins this branch.", style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Affected work", style = MaterialTheme.typography.titleMedium)
                counts.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, count) -> Column(Modifier.weight(1f).background(colors.low, TimeboxShapes.card).padding(12.dp)) {
                        Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onVariant)
                    } }
                } }
                Text("Includes ${preview.completedTaskCount} completed, ${preview.archivedTaskCount} archived and ${preview.trashedTaskCount} trashed Tasks. These counts may overlap.", style = MaterialTheme.typography.bodySmall, color = colors.onVariant)
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Branch changes", style = MaterialTheme.typography.titleMedium)
                        Text("${preview.changes.count { it.action == "combine" }} combine · ${preview.changes.count { it.action == "move" }} move", style = MaterialTheme.typography.bodySmall, color = colors.onVariant)
                    }
                    TextButton(onClick = { details = !details }) { Text(if (details) "Hide ↑" else "View ↓") }
                }
                if (details) Column(Modifier.fillMaxWidth().background(colors.low, TimeboxShapes.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    preview.changes.forEachIndexed { index, change ->
                        if (index > 0) HorizontalDivider(color = colors.hairline)
                        Text(change.targetName, style = MaterialTheme.typography.titleSmall)
                        Text("${if (change.action == "combine") "Combine" else "Move"} from ${change.sourceName}", style = MaterialTheme.typography.bodySmall, color = colors.onVariant)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your work is preserved", style = MaterialTheme.typography.titleSmall)
                Text("History combines. Task and Block links stay intact. Other classifications stay unchanged.", style = MaterialTheme.typography.bodySmall, color = colors.onVariant)
                Text("Activity tracking continues", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider(color = colors.hairline)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
            Text("Permanent merge. No undo.", style = MaterialTheme.typography.labelLarge)
            Text("${preview.sourceName} disappears; its work is preserved.", style = MaterialTheme.typography.bodySmall, color = colors.onVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, enabled = !busy) { Text("Back") }
                Button(onClick = onMerge, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (busy) "Merging…" else "Confirm merge") }
            }
        }
    }
}
