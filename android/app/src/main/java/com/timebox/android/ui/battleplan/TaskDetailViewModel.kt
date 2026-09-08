package com.timebox.android.ui.battleplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.BattleTask
import com.timebox.android.data.BattleTaskCreate
import com.timebox.android.data.BattleTaskPatch
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.Project
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.data.remote.PatchField
import com.timebox.android.ui.taskcompletion.TaskCompletion
import com.timebox.android.ui.readiness.ReadyToPlanCoordinator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskDeadlineMode { None, DateOnly, DateTime }

enum class TaskDetailOperation { SavingChanges, Completing, Reopening }

enum class TaskDraftField { Title, DeadlineDate, DeadlineTime, ReminderDate, ReminderTime }

data class TaskDetailDraft(
    val title: String,
    val description: String,
    val status: TaskStatus,
    val projectId: Int?,
    val taskTypeId: Int?,
    val urgency: PriorityLevel?,
    val importance: PriorityLevel?,
    val deadlineMode: TaskDeadlineMode,
    val deadlineDate: String,
    val deadlineTime: String,
    val reminderEnabled: Boolean,
    val reminderDate: String,
    val reminderTime: String,
    val readyToPlan: Boolean,
)

data class TaskDetailRecoveryConflict(
    val draft: TaskDetailDraft,
    val baselineVersion: Int,
    val currentVersion: Int,
)

data class TaskDetailUiState(
    val taskId: Int? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val task: BattleTask? = null,
    val parentTask: BattleTask? = null,
    val projects: List<Project> = emptyList(),
    val taskTypes: List<TaskType> = emptyList(),
    val timezone: String = "UTC",
    val serverNow: Instant = Instant.EPOCH,
    val title: String = "",
    val description: String = "",
    val status: TaskStatus = TaskStatus.Open,
    val projectId: Int? = null,
    val taskTypeId: Int? = null,
    val urgency: PriorityLevel? = null,
    val importance: PriorityLevel? = null,
    val deadlineMode: TaskDeadlineMode = TaskDeadlineMode.None,
    val deadlineDate: String = "",
    val deadlineTime: String = "",
    val reminderEnabled: Boolean = false,
    val reminderDate: String = "",
    val reminderTime: String = "",
    val readyToPlan: Boolean = false,
    val baselineDraft: TaskDetailDraft? = null,
    val editing: Boolean = false,
    val dirty: Boolean = false,
    val operation: TaskDetailOperation? = null,
    val validationError: TaskDraftValidation.Invalid? = null,
    val saveError: String? = null,
    val recoveryConflict: TaskDetailRecoveryConflict? = null,
    val trashed: Boolean = false,
    val confirmTrash: Boolean = false,
    val pendingSubtaskTrash: Subtask? = null,
    val trashUndoTarget: TrashUndoTarget? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val isSubtask: Boolean get() = task?.parentId != null
    val subtasks: List<Subtask> get() = task?.subtasks.orEmpty()
}

data class TrashUndoTarget(
    val taskId: Int,
    val title: String,
    val leaveTaskDetail: Boolean,
)

class TaskDetailViewModel(
    private val repository: TimeboxRepository,
    private val taskCompletion: TaskCompletion,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val readinessCoordinator: ReadyToPlanCoordinator? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(TaskDetailUiState())
    val state: StateFlow<TaskDetailUiState> = _state.asStateFlow()
    private var clockJob: Job? = null

    init {
        readinessCoordinator?.let { coordinator ->
            viewModelScope.launch {
                coordinator.projections.collect {
                    val taskId = _state.value.taskId ?: return@collect
                    val projected = coordinator.projectedTask(taskId) ?: return@collect
                    _state.update { current ->
                        if (current.taskId != taskId) return@update current
                        if (current.editing) {
                            current.copy(task = projected)
                        } else {
                            current.copy(
                                task = projected,
                                readyToPlan = projected.readyToPlan,
                                baselineDraft = current.baselineDraft?.copy(
                                    readyToPlan = projected.readyToPlan,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun load(taskId: Int) {
        _state.value = TaskDetailUiState(taskId = taskId)
        viewModelScope.launch {
            val tasksDeferred = async { repository.listBattleTasks() }
            val projectsDeferred = async { repository.listProjects() }
            val typesDeferred = async { repository.listTaskTypes() }
            val tasksResult = tasksDeferred.await()
            val projectsResult = projectsDeferred.await()
            val typesResult = typesDeferred.await()
            val failure = tasksResult.exceptionOrNull() ?: projectsResult.exceptionOrNull() ?: typesResult.exceptionOrNull()
            val taskList = tasksResult.getOrNull()
            taskList?.let { readinessCoordinator?.mergeServerTasks(it.items) }
            val task = taskList?.items
                ?.let { readinessCoordinator?.projectTasks(it) ?: it }
                ?.findTask(taskId)
            val parent: BattleTask? = null
            if (failure != null || task == null) {
                _state.update { it.copy(loading = false, error = failure?.apiError?.message ?: "Task not found.") }
                return@launch
            }
            val resolvedList = tasksResult.getOrThrow()
            val resolvedTask = task
            val zone = runCatching { ZoneId.of(resolvedList.timezone) }.getOrDefault(ZoneId.of("UTC"))
            val baseline = resolvedTask.toTaskDetailDraft(zone)
            val recovered = restoreTaskDetailDraft(savedStateHandle, taskId)
            val matchingRecovery = recovered?.takeIf { it.baselineVersion == resolvedTask.version }
            val recoveryConflict = recovered
                ?.takeIf { it.baselineVersion != resolvedTask.version }
                ?.let { TaskDetailRecoveryConflict(it.draft, it.baselineVersion, resolvedTask.version) }
            val visibleDraft = matchingRecovery?.draft ?: baseline
            _state.value = TaskDetailUiState(
                taskId = taskId,
                loading = false,
                task = resolvedTask,
                parentTask = parent?.takeIf { root -> root.id != resolvedTask.id },
                projects = projectsResult.getOrThrow(),
                taskTypes = typesResult.getOrThrow(),
                timezone = resolvedList.timezone,
                serverNow = resolvedList.serverNow,
                baselineDraft = baseline,
                editing = matchingRecovery != null,
                recoveryConflict = recoveryConflict,
            ).withDraft(visibleDraft)
            anchorClock(resolvedList.serverNow, resolvedList.timezone)
        }
    }

    private fun anchorClock(serverNow: Instant, timezone: String) {
        clockJob?.cancel()
        val anchor = AppClockAnchor(serverNow)
        clockJob = viewModelScope.launch {
            while (true) {
                val current = anchor.current()
                delay(millisUntilNextAppMidnight(current, timezone))
                _state.update { it.copy(serverNow = anchor.current()) }
            }
        }
    }

    fun setTitle(value: String) = edit { copy(title = value) }
    fun setDescription(value: String) = edit { copy(description = value) }
    fun setStatus(value: TaskStatus) {
        if (value == TaskStatus.Completed) return
        edit { copy(status = value) }
    }
    fun setProject(value: Int?) = edit { copy(projectId = value) }
    fun setTaskType(value: Int?) = edit { copy(taskTypeId = value) }
    fun setUrgency(value: PriorityLevel?) = edit { copy(urgency = value) }
    fun setImportance(value: PriorityLevel?) = edit { copy(importance = value) }
    fun setDeadlineMode(value: TaskDeadlineMode) = edit {
        copy(deadlineMode = value, reminderEnabled = if (value == TaskDeadlineMode.None) false else reminderEnabled)
    }
    fun setDeadlineDate(value: String) = edit { copy(deadlineDate = value) }
    fun setDeadlineTime(value: String) = edit { copy(deadlineTime = value) }
    fun setReminderEnabled(value: Boolean) = edit { copy(reminderEnabled = value) }
    fun setReminderDate(value: String) = edit { copy(reminderDate = value) }
    fun setReminderTime(value: String) = edit { copy(reminderTime = value) }
    fun setReady(value: Boolean) = edit { copy(readyToPlan = value) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeTrashUndoTarget() = _state.update { it.copy(trashUndoTarget = null) }
    fun requestTrash() = _state.update { it.copy(confirmTrash = true) }
    fun dismissTrash() = _state.update { it.copy(confirmTrash = false) }

    fun startEditing() {
        val current = _state.value
        if (current.task?.status == TaskStatus.Completed || current.saving) return
        _state.update { it.copy(editing = true, validationError = null, saveError = null) }
        persistCurrentDraft()
    }

    fun discardChanges() {
        val current = _state.value
        val baseline = current.baselineDraft ?: return
        clearPersistedDraft()
        _state.value = current.withDraft(baseline).copy(
            editing = false,
            dirty = false,
            validationError = null,
            saveError = null,
            recoveryConflict = null,
        )
    }

    fun useLatestTask() {
        val current = _state.value
        val baseline = current.baselineDraft ?: return
        clearPersistedDraft()
        _state.value = current.withDraft(baseline).copy(
            editing = false,
            dirty = false,
            recoveryConflict = null,
            validationError = null,
            saveError = null,
        )
    }

    fun restoreRecoveredDraft() {
        val current = _state.value
        val conflict = current.recoveryConflict ?: return
        if (current.task?.status == TaskStatus.Completed) return
        _state.value = current.withDraft(conflict.draft).copy(
            editing = true,
            dirty = conflict.draft.normalized() != current.baselineDraft?.normalized(),
            recoveryConflict = null,
            validationError = null,
            saveError = null,
        )
        persistCurrentDraft()
    }

    fun reopenTask() {
        val task = _state.value.task ?: return
        if (task.status != TaskStatus.Completed || _state.value.saving || _state.value.editing) return
        _state.update { it.copy(saving = true, operation = TaskDetailOperation.Reopening, message = null) }
        viewModelScope.launch {
            taskCompletion.transition(task.id, task.status, TaskStatus.Open).fold(
                onSuccess = {
                    clearPersistedDraft()
                    load(task.id)
                },
                onFailure = {
                    _state.update { it.copy(saving = false, operation = null) }
                    refreshAfterTaskCompletion(task.id)
                },
            )
        }
    }

    fun completeTask() {
        val task = _state.value.task ?: return
        if (task.status == TaskStatus.Completed || _state.value.saving || _state.value.editing) return
        _state.update { it.copy(saving = true, operation = TaskDetailOperation.Completing, message = null) }
        viewModelScope.launch {
            taskCompletion.transition(task.id, task.status, TaskStatus.Completed).fold(
                onSuccess = {
                    clearPersistedDraft()
                    load(task.id)
                },
                onFailure = {
                    _state.update { it.copy(saving = false, operation = null) }
                    refreshAfterTaskCompletion(task.id)
                },
            )
        }
    }

    fun addSubtask(title: String) {
        val parent = _state.value.task?.takeIf { it.parentId == null && it.status != TaskStatus.Completed } ?: return
        if (title.isBlank() || _state.value.saving) return
        mutate("Subtask created") { repository.createBattleTask(BattleTaskCreate(title.trim(), parentId = parent.id, projectId = parent.projectId)) }
    }

    fun toggleSubtask(task: Subtask) {
        if (_state.value.saving || _state.value.task?.status == TaskStatus.Completed) return
        val parentTaskId = _state.value.taskId ?: return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result = if (task.checked) repository.uncheckSubtask(task.id) else repository.checkSubtask(task.id)
            result.fold(
                onSuccess = { load(parentTaskId) },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    fun requestSubtaskTrash(task: Subtask) {
        if (_state.value.task?.status == TaskStatus.Completed) return
        _state.update { it.copy(pendingSubtaskTrash = task) }
    }
    fun dismissSubtaskTrash() = _state.update { it.copy(pendingSubtaskTrash = null) }

    fun confirmSubtaskTrash() {
        val task = _state.value.pendingSubtaskTrash ?: return
        val taskId = _state.value.taskId ?: return
        _state.update { it.copy(pendingSubtaskTrash = null, saving = true) }
        viewModelScope.launch {
            repository.trashBattleTask(task.id).fold(
                onSuccess = {
                    load(taskId)
                    _state.update {
                        it.copy(trashUndoTarget = TrashUndoTarget(task.id, task.title, leaveTaskDetail = false))
                    }
                },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    fun confirmTrash() {
        val task = _state.value.task ?: return
        _state.update { it.copy(confirmTrash = false, saving = true) }
        viewModelScope.launch {
            repository.trashBattleTask(task.id).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            saving = false,
                            trashed = true,
                            trashUndoTarget = TrashUndoTarget(task.id, task.title, leaveTaskDetail = true),
                        )
                    }
                },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    fun save() {
        val current = _state.value
        val original = current.task ?: return
        val baseline = current.baselineDraft ?: return
        if (!current.editing || !current.dirty || current.saving) return
        val parsed = validateTaskDraft(current)
        if (parsed is TaskDraftValidation.Invalid) {
            _state.update { it.copy(validationError = parsed, saveError = null) }
            return
        }
        parsed as TaskDraftValidation.Valid
        val draft = current.toTaskDetailDraft().normalized()
        val normalizedBaseline = baseline.normalized()
        val readinessBoundary = readinessCoordinator?.intentVersion(original.id)
        val nonReadinessChanged = draft.copy(readyToPlan = normalizedBaseline.readyToPlan) != normalizedBaseline
        _state.update {
            it.copy(
                saving = true,
                operation = TaskDetailOperation.SavingChanges,
                validationError = null,
                saveError = null,
                message = null,
            )
        }
        viewModelScope.launch {
            val patch = BattleTaskPatch(
                    title = draft.title.changedFrom(normalizedBaseline.title),
                    description = draft.description.changedFrom(normalizedBaseline.description),
                    status = draft.status.changedFrom(normalizedBaseline.status),
                    projectId = if (current.isSubtask) PatchField.Absent else draft.projectId.changedNullableFrom(normalizedBaseline.projectId),
                    taskTypeId = draft.taskTypeId.changedNullableFrom(normalizedBaseline.taskTypeId),
                    urgency = draft.urgency.changedNullableFrom(normalizedBaseline.urgency),
                    importance = draft.importance.changedNullableFrom(normalizedBaseline.importance),
                    deadlineDate = parsed.deadlineDate.changedNullableFrom(original.deadlineDate),
                    deadlineAt = parsed.deadlineAt.changedNullableFrom(original.deadlineAt),
                    reminderAt = parsed.reminderAt.changedNullableFrom(original.reminderAt),
                    readyToPlan = if (readinessCoordinator == null) {
                        draft.readyToPlan.changedFrom(normalizedBaseline.readyToPlan)
                    } else {
                        PatchField.Absent
                    },
                )
            val saved = if (readinessCoordinator == null || nonReadinessChanged) {
                repository.patchBattleTask(original.id, patch).getOrElse { error ->
                    _state.update {
                        it.copy(
                            saving = false,
                            operation = null,
                            saveError = error.apiError.message,
                        )
                    }
                    return@launch
                }
            } else {
                original
            }
            readinessCoordinator?.let { coordinator ->
                coordinator.mergeServerTasks(listOf(saved))
                coordinator.setReadyFromDraft(
                    task = saved,
                    ready = draft.readyToPlan,
                    observedIntentVersion = checkNotNull(readinessBoundary),
                )
            }
            clearPersistedDraft()
            load(original.id)
        }
    }

    fun refreshAfterTaskCompletion(taskId: Int) {
        if (_state.value.taskId == taskId) load(taskId)
    }

    private fun mutate(message: String, operation: suspend () -> Result<*>) {
        if (_state.value.saving) return
        val taskId = _state.value.taskId ?: return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            operation().fold(
                onSuccess = { _state.update { it.copy(saving = false, message = message) }; load(taskId) },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    private fun edit(block: TaskDetailUiState.() -> TaskDetailUiState) {
        if (!_state.value.editing || _state.value.saving) return
        _state.update { current ->
            val updated = current.block().copy(validationError = null, saveError = null)
            updated.copy(dirty = updated.toTaskDetailDraft().normalized() != updated.baselineDraft?.normalized())
        }
        persistCurrentDraft()
    }

    private fun persistCurrentDraft() {
        val current = _state.value
        val task = current.task ?: return
        if (!current.editing) return
        persistTaskDetailDraft(savedStateHandle, task.id, task.version, current.toTaskDetailDraft())
    }

    private fun clearPersistedDraft() = clearTaskDetailDraft(savedStateHandle)

    companion object { private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm") }
}

internal data class PersistedTaskDetailDraft(val draft: TaskDetailDraft, val baselineVersion: Int)

private fun BattleTask.toTaskDetailDraft(zone: ZoneId): TaskDetailDraft {
    val deadlineMode = when {
        deadlineAt != null -> TaskDeadlineMode.DateTime
        deadlineDate != null -> TaskDeadlineMode.DateOnly
        else -> TaskDeadlineMode.None
    }
    val deadlineLocal = deadlineAt?.atZone(zone)
    val reminderLocal = reminderAt?.atZone(zone)
    return TaskDetailDraft(
        title = title,
        description = description,
        status = status,
        projectId = projectId,
        taskTypeId = taskTypeId,
        urgency = urgency,
        importance = importance,
        deadlineMode = deadlineMode,
        deadlineDate = deadlineDate?.toString() ?: deadlineLocal?.toLocalDate()?.toString().orEmpty(),
        deadlineTime = deadlineLocal?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty(),
        reminderEnabled = reminderAt != null,
        reminderDate = reminderLocal?.toLocalDate()?.toString().orEmpty(),
        reminderTime = reminderLocal?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty(),
        readyToPlan = readyToPlan,
    )
}

internal fun TaskDetailUiState.toTaskDetailDraft() = TaskDetailDraft(
    title = title,
    description = description,
    status = status,
    projectId = projectId,
    taskTypeId = taskTypeId,
    urgency = urgency,
    importance = importance,
    deadlineMode = deadlineMode,
    deadlineDate = deadlineDate,
    deadlineTime = deadlineTime,
    reminderEnabled = reminderEnabled,
    reminderDate = reminderDate,
    reminderTime = reminderTime,
    readyToPlan = readyToPlan,
)

internal fun TaskDetailUiState.withDraft(draft: TaskDetailDraft) = copy(
    title = draft.title,
    description = draft.description,
    status = draft.status,
    projectId = draft.projectId,
    taskTypeId = draft.taskTypeId,
    urgency = draft.urgency,
    importance = draft.importance,
    deadlineMode = draft.deadlineMode,
    deadlineDate = draft.deadlineDate,
    deadlineTime = draft.deadlineTime,
    reminderEnabled = draft.reminderEnabled,
    reminderDate = draft.reminderDate,
    reminderTime = draft.reminderTime,
    readyToPlan = draft.readyToPlan,
)

internal fun TaskDetailDraft.normalized() = copy(title = title.trim(), description = description.trim())

private object TaskDetailDraftKeys {
    const val TaskId = "taskDetail.draft.taskId"
    const val BaselineVersion = "taskDetail.draft.baselineVersion"
    const val Editing = "taskDetail.draft.editing"
    const val Title = "taskDetail.draft.title"
    const val Description = "taskDetail.draft.description"
    const val Status = "taskDetail.draft.status"
    const val ProjectId = "taskDetail.draft.projectId"
    const val HasProject = "taskDetail.draft.hasProject"
    const val TaskTypeId = "taskDetail.draft.taskTypeId"
    const val HasTaskType = "taskDetail.draft.hasTaskType"
    const val Urgency = "taskDetail.draft.urgency"
    const val Importance = "taskDetail.draft.importance"
    const val DeadlineMode = "taskDetail.draft.deadlineMode"
    const val DeadlineDate = "taskDetail.draft.deadlineDate"
    const val DeadlineTime = "taskDetail.draft.deadlineTime"
    const val ReminderEnabled = "taskDetail.draft.reminderEnabled"
    const val ReminderDate = "taskDetail.draft.reminderDate"
    const val ReminderTime = "taskDetail.draft.reminderTime"
    const val ReadyToPlan = "taskDetail.draft.readyToPlan"

    val all = listOf(
        TaskId, BaselineVersion, Editing, Title, Description, Status, ProjectId, HasProject,
        TaskTypeId, HasTaskType, Urgency, Importance, DeadlineMode, DeadlineDate, DeadlineTime,
        ReminderEnabled, ReminderDate, ReminderTime, ReadyToPlan,
    )
}

internal fun persistTaskDetailDraft(
    handle: SavedStateHandle,
    taskId: Int,
    baselineVersion: Int,
    draft: TaskDetailDraft,
) {
    handle[TaskDetailDraftKeys.TaskId] = taskId
    handle[TaskDetailDraftKeys.BaselineVersion] = baselineVersion
    handle[TaskDetailDraftKeys.Editing] = true
    handle[TaskDetailDraftKeys.Title] = draft.title
    handle[TaskDetailDraftKeys.Description] = draft.description
    handle[TaskDetailDraftKeys.Status] = draft.status.name
    handle[TaskDetailDraftKeys.HasProject] = draft.projectId != null
    handle[TaskDetailDraftKeys.ProjectId] = draft.projectId
    handle[TaskDetailDraftKeys.HasTaskType] = draft.taskTypeId != null
    handle[TaskDetailDraftKeys.TaskTypeId] = draft.taskTypeId
    handle[TaskDetailDraftKeys.Urgency] = draft.urgency?.name
    handle[TaskDetailDraftKeys.Importance] = draft.importance?.name
    handle[TaskDetailDraftKeys.DeadlineMode] = draft.deadlineMode.name
    handle[TaskDetailDraftKeys.DeadlineDate] = draft.deadlineDate
    handle[TaskDetailDraftKeys.DeadlineTime] = draft.deadlineTime
    handle[TaskDetailDraftKeys.ReminderEnabled] = draft.reminderEnabled
    handle[TaskDetailDraftKeys.ReminderDate] = draft.reminderDate
    handle[TaskDetailDraftKeys.ReminderTime] = draft.reminderTime
    handle[TaskDetailDraftKeys.ReadyToPlan] = draft.readyToPlan
}

internal fun restoreTaskDetailDraft(handle: SavedStateHandle, taskId: Int): PersistedTaskDetailDraft? {
    if (handle.get<Boolean>(TaskDetailDraftKeys.Editing) != true) return null
    if (handle.get<Int>(TaskDetailDraftKeys.TaskId) != taskId) return null
    val baselineVersion = handle.get<Int>(TaskDetailDraftKeys.BaselineVersion) ?: return null
    val status = handle.get<String>(TaskDetailDraftKeys.Status)
        ?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() }
        ?.takeIf { it != TaskStatus.Completed }
        ?: TaskStatus.Open
    val draft = TaskDetailDraft(
        title = handle[TaskDetailDraftKeys.Title] ?: "",
        description = handle[TaskDetailDraftKeys.Description] ?: "",
        status = status,
        projectId = handle.get<Int>(TaskDetailDraftKeys.ProjectId)
            .takeIf { handle.get<Boolean>(TaskDetailDraftKeys.HasProject) == true },
        taskTypeId = handle.get<Int>(TaskDetailDraftKeys.TaskTypeId)
            .takeIf { handle.get<Boolean>(TaskDetailDraftKeys.HasTaskType) == true },
        urgency = handle.get<String>(TaskDetailDraftKeys.Urgency)?.let { runCatching { PriorityLevel.valueOf(it) }.getOrNull() },
        importance = handle.get<String>(TaskDetailDraftKeys.Importance)?.let { runCatching { PriorityLevel.valueOf(it) }.getOrNull() },
        deadlineMode = handle.get<String>(TaskDetailDraftKeys.DeadlineMode)
            ?.let { runCatching { TaskDeadlineMode.valueOf(it) }.getOrNull() } ?: TaskDeadlineMode.None,
        deadlineDate = handle[TaskDetailDraftKeys.DeadlineDate] ?: "",
        deadlineTime = handle[TaskDetailDraftKeys.DeadlineTime] ?: "",
        reminderEnabled = handle[TaskDetailDraftKeys.ReminderEnabled] ?: false,
        reminderDate = handle[TaskDetailDraftKeys.ReminderDate] ?: "",
        reminderTime = handle[TaskDetailDraftKeys.ReminderTime] ?: "",
        readyToPlan = handle[TaskDetailDraftKeys.ReadyToPlan] ?: false,
    )
    return PersistedTaskDetailDraft(draft, baselineVersion)
}

internal fun clearTaskDetailDraft(handle: SavedStateHandle) {
    TaskDetailDraftKeys.all.forEach { handle.remove<Any>(it) }
}

private fun <T> T.changedFrom(original: T): PatchField<T> =
    if (this == original) PatchField.Absent else PatchField.of(this)

private fun <T : Any> T?.changedNullableFrom(original: T?): PatchField<T> = when {
    this == original -> PatchField.Absent
    this == null -> PatchField.Null
    else -> PatchField.of(this)
}

sealed interface TaskDraftValidation {
    data class Valid(val deadlineDate: LocalDate?, val deadlineAt: Instant?, val reminderAt: Instant?) : TaskDraftValidation
    data class Invalid(val field: TaskDraftField?, val message: String) : TaskDraftValidation
}

internal fun validateTaskDraft(state: TaskDetailUiState): TaskDraftValidation {
    if (state.title.isBlank()) return TaskDraftValidation.Invalid(TaskDraftField.Title, "Task title is required.")
    val zone = runCatching { ZoneId.of(state.timezone) }
        .getOrElse { return TaskDraftValidation.Invalid(null, "Unknown app timezone.") }
    val date = if (state.deadlineMode != TaskDeadlineMode.None) runCatching { LocalDate.parse(state.deadlineDate) }.getOrNull() else null
    if (state.deadlineMode != TaskDeadlineMode.None && date == null) {
        return TaskDraftValidation.Invalid(TaskDraftField.DeadlineDate, "Enter a deadline date as YYYY-MM-DD.")
    }
    val deadlineAt = if (state.deadlineMode == TaskDeadlineMode.DateTime) {
        val time = runCatching { LocalTime.parse(state.deadlineTime) }.getOrNull()
            ?: return TaskDraftValidation.Invalid(TaskDraftField.DeadlineTime, "Enter a deadline time as HH:MM.")
        LocalDateTime.of(date, time).atZone(zone).toInstant()
    } else null
    val deadlineDate = date.takeIf { state.deadlineMode == TaskDeadlineMode.DateOnly }
    val reminderAt = if (state.reminderEnabled) {
        if (state.deadlineMode == TaskDeadlineMode.None) {
            return TaskDraftValidation.Invalid(TaskDraftField.ReminderDate, "A reminder requires a deadline.")
        }
        val reminderDate = runCatching { LocalDate.parse(state.reminderDate) }.getOrNull()
            ?: return TaskDraftValidation.Invalid(TaskDraftField.ReminderDate, "Enter a reminder date as YYYY-MM-DD.")
        val reminderTime = runCatching { LocalTime.parse(state.reminderTime) }.getOrNull()
            ?: return TaskDraftValidation.Invalid(TaskDraftField.ReminderTime, "Enter a reminder time as HH:MM.")
        LocalDateTime.of(reminderDate, reminderTime).atZone(zone).toInstant()
    } else null
    val boundary = deadlineAt ?: deadlineDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()
    if (reminderAt != null && boundary != null && !reminderAt.isBefore(boundary)) {
        return TaskDraftValidation.Invalid(TaskDraftField.ReminderTime, "Reminder must be before the deadline.")
    }
    return TaskDraftValidation.Valid(deadlineDate, deadlineAt, reminderAt)
}

private fun <T> T?.toPatchField(): PatchField<T> = if (this == null) PatchField.Null else PatchField.of(this)

internal fun List<BattleTask>.findTask(id: Int): BattleTask? =
    firstNotNullOfOrNull { task -> task.takeIf { it.id == id } ?: task.sessionTasks.findTask(id) }
