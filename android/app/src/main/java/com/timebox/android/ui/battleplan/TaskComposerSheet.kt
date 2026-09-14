package com.timebox.android.ui.battleplan

import androidx.compose.runtime.Composable
import java.time.ZoneId

@Composable
internal fun TaskComposerOverlay(
    state: BattlePlanUiState,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onDraftChange: (TaskComposerDraft) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val draft = state.composerDraft
    TaskFieldsSheet(
        draft = draft.toDetailDraft(), projects = state.projects, taskTypes = state.taskTypes,
        timezone = state.timezone, today = state.serverNow.atZone(ZoneId.of(state.timezone)).toLocalDate(),
        creating = true, saving = state.saving, dirty = draft.dirty,
        error = state.composerError, notificationsAllowed = notificationsAllowed,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onChange = { onDraftChange(draft.withDetailDraft(it)) },
        onDismiss = onDismiss, onDiscard = {}, onRetrySave = onCreate, onCreate = onCreate,
        fieldsLocked = state.composerCreatedTaskId != null,
    ) {
        TaskSubtasks(emptyList(), state.composerCreatedTaskId == null, state.saving, null, {}, {},
            onAdd = { onDraftChange(draft.copy(subtasks = draft.subtasks + it)) },
            draftNames = draft.subtasks,
            onRemoveDraft = { index -> onDraftChange(draft.copy(subtasks = draft.subtasks.filterIndexed { i, _ -> i != index })) })
    }
}

internal fun TaskComposerDraft.toDetailDraft() = TaskDetailDraft(
    title, description, status, projectId, taskTypeId, urgency, importance,
    deadlineMode, deadlineDate, deadlineTime, reminderEnabled, reminderDate, reminderTime, readyToPlan,
)

internal fun TaskComposerDraft.withDetailDraft(draft: TaskDetailDraft) = copy(
    title = draft.title, description = draft.description, status = draft.status,
    projectId = draft.projectId, taskTypeId = draft.taskTypeId, urgency = draft.urgency, importance = draft.importance,
    deadlineMode = draft.deadlineMode, deadlineDate = draft.deadlineDate, deadlineTime = draft.deadlineTime,
    reminderEnabled = draft.reminderEnabled, reminderDate = draft.reminderDate, reminderTime = draft.reminderTime,
    readyToPlan = draft.readyToPlan,
)
