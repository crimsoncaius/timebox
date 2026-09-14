package com.timebox.android.ui.battleplan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Subtask
import com.timebox.android.ui.theme.TimeboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskSubtasks(
    subtasks: List<Subtask>,
    enabled: Boolean,
    saving: Boolean,
    error: String?,
    onToggle: (Subtask) -> Unit,
    onTrash: (Subtask) -> Unit,
    onAdd: (String) -> Unit,
    draftNames: List<String> = emptyList(),
    onRemoveDraft: (Int) -> Unit = {},
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var submittedCount by rememberSaveable { mutableStateOf<Int?>(null) }
    val count = subtasks.size + draftNames.size
    LaunchedEffect(count) {
        if (submittedCount != null && count > submittedCount!!) { title = ""; submittedCount = null }
    }
    fun close() { if (!saving) { if (title.isNotBlank()) discard = true else adding = false } }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Subtasks", Modifier.weight(1f), style = TimeboxTheme.type.label)
        if (subtasks.isNotEmpty()) Text("${subtasks.count { it.checked }} / ${subtasks.size}")
    }
    subtasks.forEach { task ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(task.checked, { onToggle(task) }, enabled = enabled && !saving, modifier = Modifier.semantics { contentDescription = "Check ${task.title}" })
            Text(task.title, Modifier.weight(1f), color = if (task.effectivelyResolved) TimeboxTheme.colors.onVariant else TimeboxTheme.colors.on,
                textDecoration = if (task.checked) TextDecoration.LineThrough else null)
            IconButton(onClick = { onTrash(task) }, enabled = enabled && !saving) { Icon(Icons.Outlined.DeleteOutline, "Move ${task.title} to Trash") }
        }
    }
    draftNames.forEachIndexed { index, name ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f))
            IconButton(onClick = { onRemoveDraft(index) }, enabled = enabled && !saving) { Icon(Icons.Outlined.Close, "Remove $name") }
        }
    }
    TextButton(onClick = { adding = true }, enabled = enabled && !saving) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(10.dp)); Text("Add subtask") }
    if (adding) ModalBottomSheet(onDismissRequest = ::close,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
            if (it == SheetValue.Hidden && (saving || title.isNotBlank())) { if (!saving) discard = true; false } else true
        }), containerColor = TimeboxTheme.colors.surf) {
        TaskSheetBackHandler(saving, ::close)
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add subtask", Modifier.weight(1f), style = TimeboxTheme.type.sectionTitle)
                IconButton(onClick = ::close, enabled = !saving) { Icon(Icons.Outlined.Close, "Close subtask") }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Subtask name") }, enabled = !saving, modifier = Modifier.fillMaxWidth())
            if (error != null) Text(error, color = TimeboxTheme.colors.error)
            Button(onClick = { submittedCount = count; onAdd(title.trim()) }, enabled = !saving && title.isNotBlank() && title.length <= 500, modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "Adding…" else "Add subtask")
            }
            Text("$count subtasks · Add another or close to return", color = TimeboxTheme.colors.onVariant)
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard this subtask draft?") },
        confirmButton = { TextButton(onClick = { discard = false; adding = false; title = "" }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}
