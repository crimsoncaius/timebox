package com.timebox.android.ui.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RenameTaskTypeSheet(state: TypesUiState, onChange: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    val type = state.renaming ?: return
    val colors = TimeboxTheme.colors
    val busy by rememberUpdatedState(state.saving)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !busy })
    val focus = remember { FocusRequester() }
    val affected = state.groups.flatMap { it.items }.filter { it.id == type.id || it.name.startsWith("${type.name}/") }
    val path = state.renameInput.split('/').joinToString("/") { it.trim().lowercase(java.util.Locale.ROOT) }
    val valid = path.split('/').none { it.isEmpty() }
    LaunchedEffect(type.id) {
        snapshotFlow { sheet.currentValue }.first { it == SheetValue.Expanded }
        focus.requestFocus()
    }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onCancel() }, sheetState = sheet, sheetMaxWidth = 640.dp,
        shape = TimeboxShapes.sheet, containerColor = colors.sheet, contentColor = colors.on, scrimColor = colors.scrim,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Rename task type", style = TimeboxTheme.type.sectionTitle)
            Text(type.name, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            OutlinedTextField(
                value = state.renameInput, onValueChange = onChange, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Task type path") }, singleLine = true, enabled = !busy, isError = state.renameError != null,
                shape = TimeboxShapes.field, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!busy && valid) onSave() }),
            )
            Text("Updates this type" + (if (affected.size > 1) " and ${affected.size - 1} nested types" else "") + " everywhere, including past work.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            Column(Modifier.fillMaxWidth().background(colors.low, TimeboxShapes.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AFTER SAVING", style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
                if (!valid) Text("Enter a path with no empty segments.", style = TimeboxTheme.type.bodySmall)
                else affected.forEach { Text(path + it.name.removePrefix(type.name), style = TimeboxTheme.type.bodySmall) }
            }
            state.renameError?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onSave, enabled = !busy && valid) { Text(if (busy) "Saving…" else "Save") }
            }
        }
    }
}
