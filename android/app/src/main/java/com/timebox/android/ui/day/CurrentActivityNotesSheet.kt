package com.timebox.android.ui.day

import com.timebox.android.data.primaryIdentity
import com.timebox.android.data.secondaryIdentity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.BattleTask
import com.timebox.android.data.BattleTaskPatch
import com.timebox.android.data.TaskCollection
import com.timebox.android.data.flattenBattleTasks
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.PatchField
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrentActivityNotesSheet(
    actual: ActualBlockDto,
    activityRepository: ActivityRepository,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as TimeboxApplication
    val activityState by activityRepository.state.collectAsState()
    val linked = actual.taskId != null
    val readOnly = linked && (actual.task?.archivedAt != null || actual.task?.deletedAt != null)
    val fieldName = if (linked) "Task Description" else "Supporting Note"
    val title = actual.primaryIdentity()
    var task by remember(actual.id) { mutableStateOf<BattleTask?>(null) }
    var loading by remember(actual.id) { mutableStateOf(linked) }
    var draft by remember(actual.id) { mutableStateOf(actual.note.orEmpty()) }
    var saving by remember(actual.id) { mutableStateOf(false) }
    var error by remember(actual.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val colors = TimeboxTheme.colors
    val online = !activityState.offline && activityState.snapshot != null

    LaunchedEffect(actual.id, actual.taskId) {
        if (!linked) return@LaunchedEffect
        val collection = when {
            actual.task?.deletedAt != null -> TaskCollection.Trash
            actual.task?.archivedAt != null -> TaskCollection.Archived
            else -> TaskCollection.Active
        }
        app.repository.listBattleTasks(collection).fold(
            onSuccess = { result ->
                task = result.items.flattenBattleTasks().find { it.id == actual.taskId }
                draft = task?.description.orEmpty()
                if (task == null) error = "Could not load this Task Description."
            },
            onFailure = { error = "Could not load this Task Description. Check your connection and try again." },
        )
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
        contentColor = colors.on,
        shape = TimeboxShapes.sheet,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CURRENT ACTIVITY", style = TimeboxTheme.type.kicker, color = colors.actual)
                    Text(title, style = TimeboxTheme.type.screenTitle, color = colors.on)
                    actual.secondaryIdentity()?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
                }
                TextButton(enabled = !saving, onClick = onDismiss) { Text("Close", color = colors.onVariant) }
            }
            HorizontalDivider(color = colors.hairline)
            Text(fieldName, style = TimeboxTheme.type.sectionTitle, color = colors.on)
            when {
                loading -> Text("Loading…", style = TimeboxTheme.type.body, color = colors.onVariant)
                readOnly -> {
                    Text(draft.ifBlank { "No Task Description." }, style = TimeboxTheme.type.body, color = colors.onVariant)
                    Text(
                        "This linked Task is ${if (actual.task?.deletedAt != null) "in Trash" else "archived"}, so its Task Description is read-only.",
                        style = TimeboxTheme.type.bodySmall,
                        color = colors.onVariant,
                    )
                }
                else -> {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it; error = null },
                        enabled = !saving,
                        label = { Text(fieldName) },
                        placeholder = {
                            Text(if (linked) "Add context for this Task…" else "Add context for this recorded activity…")
                        },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        shape = TimeboxShapes.field,
                    )
                    Text(
                        if (linked) "Replaces this Task’s complete Description." else "Replaces this running Actual Block’s complete Supporting Note.",
                        style = TimeboxTheme.type.bodySmall,
                        color = colors.onVariant,
                    )
                    if (!online) Text("Connect to save notes.", style = TimeboxTheme.type.bodySmall, color = colors.error)
                }
            }
            error?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error) }
            Spacer(Modifier.heightIn(min = 4.dp))
            if (readOnly || loading) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Close") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled = !saving, onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        enabled = online && !saving,
                        onClick = {
                            saving = true
                            error = null
                            scope.launch {
                                val result = if (linked) {
                                    app.repository.patchBattleTask(
                                        actual.taskId!!,
                                        BattleTaskPatch(description = PatchField.of(draft)),
                                    ).map { Unit }
                                } else {
                                    runCatching {
                                        check(activityRepository.updateCurrentNoteOnline(actual.id, draft)) {
                                            activityRepository.state.value.error ?: "Could not save Supporting Note."
                                        }
                                    }
                                }
                                result.onSuccess {
                                    activityRepository.refresh()
                                    onDismiss()
                                }.onFailure {
                                    error = "Could not save $fieldName. Check your connection and try again."
                                }
                                saving = false
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(if (saving) "Saving…" else "Save notes") }
                }
            }
        }
    }
}
