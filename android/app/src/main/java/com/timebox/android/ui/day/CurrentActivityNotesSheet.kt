package com.timebox.android.ui.day

import com.timebox.android.data.primaryIdentity
import com.timebox.android.data.secondaryIdentity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.BattleTask
import com.timebox.android.data.BattleTaskPatch
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskCollection
import com.timebox.android.data.flattenBattleTasks
import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.PatchField
import com.timebox.android.ui.components.SheetField
import com.timebox.android.ui.components.SheetHelp
import com.timebox.android.ui.components.SheetPrimary
import com.timebox.android.ui.components.SheetTextField
import com.timebox.android.ui.components.TimeboxSheet
import com.timebox.android.ui.elapsedDuration
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

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
    val fieldLabel = if (linked) "Task description" else "Supporting note"
    val title = actual.primaryIdentity()
    var task by remember(actual.id) { mutableStateOf<BattleTask?>(null) }
    var loading by remember(actual.id) { mutableStateOf(linked) }
    var draft by remember(actual.id) { mutableStateOf(actual.note.orEmpty()) }
    var baseline by remember(actual.id) { mutableStateOf(actual.note.orEmpty()) }
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
                baseline = draft
                if (task == null) error = "Could not load this Task Description."
            },
            onFailure = { error = "Could not load this Task Description. Check your connection and try again." },
        )
        loading = false
    }

    val now by produceState(activityRepository.now()) {
        while (true) { delay(30_000); value = activityRepository.now() }
    }
    val running = elapsedDuration(Duration.between(parseActivityInstant(actual.startAt), now).toMinutes().coerceAtLeast(0))
    val save: () -> Unit = {
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
    }
    TimeboxSheet(
        lane = Lane.Actual,
        kicker = "Current activity",
        title = title,
        subtitle = listOfNotNull(actual.secondaryIdentity(), "running $running").joinToString(" · "),
        onDismiss = onDismiss,
        busy = saving,
        primary = when {
            loading -> SheetPrimary.Disabled("Loading…")
            readOnly -> SheetPrimary.Disabled("Read-only")
            saving -> SheetPrimary.Disabled("Saving…")
            !online -> SheetPrimary.Disabled("Connect to save")
            draft == baseline -> SheetPrimary.Disabled("No changes")
            else -> SheetPrimary.Action(if (linked) "Save description" else "Save note", save)
        },
    ) {
        when {
            loading -> SheetField(fieldLabel) { Text("Loading…", style = TimeboxTheme.type.body, color = colors.onVariant) }
            readOnly -> SheetField(fieldLabel) {
                Text(draft.ifBlank { "No Task Description." }, style = TimeboxTheme.type.body, color = colors.onVariant)
                SheetHelp("This linked Task is ${if (actual.task?.deletedAt != null) "in Trash" else "archived"}, so its Task Description is read-only.")
            }
            else -> {
                SheetTextField(
                    value = draft,
                    onValueChange = { draft = it; error = null },
                    label = fieldLabel,
                    placeholder = if (linked) "Add context for this task" else "What are you working on?",
                    enabled = !saving,
                    singleLine = false,
                    minLines = 5,
                )
                SheetHelp(if (linked) "Replaces this task's whole description." else "Replaces the running block's whole note.")
                if (!online) SheetHelp("Connect to save notes.", color = colors.error)
            }
        }
        error?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error) }
    }
}
