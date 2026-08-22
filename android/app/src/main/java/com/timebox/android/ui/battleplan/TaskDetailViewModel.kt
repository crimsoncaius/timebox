package com.timebox.android.ui.battleplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.BattleTask
import com.timebox.android.data.BattleTaskCreate
import com.timebox.android.data.BattleTaskPatch
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.Project
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.data.remote.PatchField
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
    val dirty: Boolean = false,
    val saved: Boolean = false,
    val trashed: Boolean = false,
    val confirmTrash: Boolean = false,
    val pendingSubtaskTrash: BattleTask? = null,
    val undoSubtaskId: Int? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val isSubtask: Boolean get() = task?.parentId != null
    val subtasks: List<BattleTask> get() = task?.subtasks.orEmpty()
}

class TaskDetailViewModel(private val repository: TimeboxRepository) : ViewModel() {
    private val _state = MutableStateFlow(TaskDetailUiState())
    val state: StateFlow<TaskDetailUiState> = _state.asStateFlow()
    private var clockJob: Job? = null

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
            val parent = taskList?.items?.firstOrNull { root -> root.id == taskId || root.subtasks.any { it.id == taskId } }
            val task = parent?.takeIf { it.id == taskId } ?: parent?.subtasks?.findTask(taskId)
            if (failure != null || task == null) {
                _state.update { it.copy(loading = false, error = failure?.apiError?.message ?: "Task not found.") }
                return@launch
            }
            val resolvedList = tasksResult.getOrThrow()
            val resolvedTask = task
            val zone = runCatching { ZoneId.of(resolvedList.timezone) }.getOrDefault(ZoneId.of("UTC"))
            val deadlineMode = when {
                resolvedTask.deadlineAt != null -> TaskDeadlineMode.DateTime
                resolvedTask.deadlineDate != null -> TaskDeadlineMode.DateOnly
                else -> TaskDeadlineMode.None
            }
            val deadlineLocal = resolvedTask.deadlineAt?.atZone(zone)
            val reminderLocal = resolvedTask.reminderAt?.atZone(zone)
            _state.update {
                it.copy(
                    loading = false, task = resolvedTask, parentTask = parent?.takeIf { root -> root.id != resolvedTask.id },
                    projects = projectsResult.getOrThrow(), taskTypes = typesResult.getOrThrow(), timezone = resolvedList.timezone,
                    serverNow = resolvedList.serverNow,
                    title = resolvedTask.title, description = resolvedTask.description, status = resolvedTask.status,
                    projectId = resolvedTask.projectId, taskTypeId = resolvedTask.taskTypeId, urgency = resolvedTask.urgency,
                    importance = resolvedTask.importance, deadlineMode = deadlineMode,
                    deadlineDate = resolvedTask.deadlineDate?.toString() ?: deadlineLocal?.toLocalDate()?.toString().orEmpty(),
                    deadlineTime = deadlineLocal?.toLocalTime()?.format(TIME_FORMAT).orEmpty(),
                    reminderEnabled = resolvedTask.reminderAt != null,
                    reminderDate = reminderLocal?.toLocalDate()?.toString().orEmpty(),
                    reminderTime = reminderLocal?.toLocalTime()?.format(TIME_FORMAT).orEmpty(),
                    readyToPlan = resolvedTask.readyToPlan, error = null,
                )
            }
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
    fun setStatus(value: TaskStatus) = edit { copy(status = value) }
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
    fun requestTrash() = _state.update { it.copy(confirmTrash = true) }
    fun dismissTrash() = _state.update { it.copy(confirmTrash = false) }

    fun addSubtask(title: String) {
        val parent = _state.value.task?.takeIf { it.parentId == null } ?: return
        if (title.isBlank() || _state.value.saving) return
        mutate("Subtask created") { repository.createBattleTask(BattleTaskCreate(title.trim(), parentId = parent.id, projectId = parent.projectId)) }
    }

    fun toggleSubtask(task: BattleTask) = mutate("Subtask updated") {
        repository.patchBattleTask(task.id, BattleTaskPatch(status = PatchField.of(if (task.status == TaskStatus.Completed) TaskStatus.Open else TaskStatus.Completed)))
    }

    fun requestSubtaskTrash(task: BattleTask) = _state.update { it.copy(pendingSubtaskTrash = task) }
    fun dismissSubtaskTrash() = _state.update { it.copy(pendingSubtaskTrash = null) }

    fun confirmSubtaskTrash() {
        val task = _state.value.pendingSubtaskTrash ?: return
        val taskId = _state.value.taskId ?: return
        _state.update { it.copy(pendingSubtaskTrash = null, saving = true) }
        viewModelScope.launch {
            repository.trashBattleTask(task.id).fold(
                onSuccess = {
                    load(taskId)
                    _state.update { it.copy(undoSubtaskId = task.id, message = "Subtask moved to Trash") }
                },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    fun undoSubtaskTrash() {
        val id = _state.value.undoSubtaskId ?: return
        val taskId = _state.value.taskId ?: return
        _state.update { it.copy(undoSubtaskId = null, saving = true) }
        viewModelScope.launch {
            repository.restoreBattleTask(id).fold(
                onSuccess = { load(taskId) },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    fun confirmTrash() {
        val task = _state.value.task ?: return
        _state.update { it.copy(confirmTrash = false, saving = true) }
        viewModelScope.launch {
            repository.trashBattleTask(task.id).fold(
                onSuccess = { _state.update { it.copy(saving = false, trashed = true, message = "Moved to Trash") } },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    fun save() {
        val current = _state.value
        val original = current.task ?: return
        val parsed = validateTaskDraft(current)
        if (parsed is TaskDraftValidation.Invalid) {
            _state.update { it.copy(message = parsed.message) }
            return
        }
        parsed as TaskDraftValidation.Valid
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            repository.patchBattleTask(
                original.id,
                BattleTaskPatch(
                    title = PatchField.of(current.title.trim()), description = PatchField.of(current.description.trim()),
                    status = PatchField.of(current.status),
                    projectId = if (current.isSubtask) PatchField.Absent else current.projectId.toPatchField(),
                    taskTypeId = current.taskTypeId.toPatchField(), urgency = current.urgency.toPatchField(),
                    importance = current.importance.toPatchField(),
                    deadlineDate = parsed.deadlineDate.toPatchField(), deadlineAt = parsed.deadlineAt.toPatchField(),
                    reminderAt = parsed.reminderAt.toPatchField(), readyToPlan = PatchField.of(current.readyToPlan),
                ),
            ).fold(
                onSuccess = { updated ->
                    _state.update { it.copy(saving = false, task = updated, dirty = false, saved = true, message = "Task saved") }
                    load(original.id)
                },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
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

    private fun edit(block: TaskDetailUiState.() -> TaskDetailUiState) =
        _state.update { it.block().copy(dirty = true, saved = false) }

    companion object { private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm") }
}

sealed interface TaskDraftValidation {
    data class Valid(val deadlineDate: LocalDate?, val deadlineAt: Instant?, val reminderAt: Instant?) : TaskDraftValidation
    data class Invalid(val message: String) : TaskDraftValidation
}

internal fun validateTaskDraft(state: TaskDetailUiState): TaskDraftValidation {
    if (state.title.isBlank()) return TaskDraftValidation.Invalid("Task title is required.")
    val zone = runCatching { ZoneId.of(state.timezone) }.getOrElse { return TaskDraftValidation.Invalid("Unknown app timezone.") }
    val date = if (state.deadlineMode != TaskDeadlineMode.None) runCatching { LocalDate.parse(state.deadlineDate) }.getOrNull() else null
    if (state.deadlineMode != TaskDeadlineMode.None && date == null) return TaskDraftValidation.Invalid("Enter a deadline date as YYYY-MM-DD.")
    val deadlineAt = if (state.deadlineMode == TaskDeadlineMode.DateTime) {
        val time = runCatching { LocalTime.parse(state.deadlineTime) }.getOrNull()
            ?: return TaskDraftValidation.Invalid("Enter a deadline time as HH:MM.")
        LocalDateTime.of(date, time).atZone(zone).toInstant()
    } else null
    val deadlineDate = date.takeIf { state.deadlineMode == TaskDeadlineMode.DateOnly }
    val reminderAt = if (state.reminderEnabled) {
        if (state.deadlineMode == TaskDeadlineMode.None) return TaskDraftValidation.Invalid("A reminder requires a deadline.")
        val reminderDate = runCatching { LocalDate.parse(state.reminderDate) }.getOrNull()
            ?: return TaskDraftValidation.Invalid("Enter a reminder date as YYYY-MM-DD.")
        val reminderTime = runCatching { LocalTime.parse(state.reminderTime) }.getOrNull()
            ?: return TaskDraftValidation.Invalid("Enter a reminder time as HH:MM.")
        LocalDateTime.of(reminderDate, reminderTime).atZone(zone).toInstant()
    } else null
    val boundary = deadlineAt ?: deadlineDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()
    if (reminderAt != null && boundary != null && !reminderAt.isBefore(boundary)) {
        return TaskDraftValidation.Invalid("Reminder must be before the deadline.")
    }
    return TaskDraftValidation.Valid(deadlineDate, deadlineAt, reminderAt)
}

private fun <T> T?.toPatchField(): PatchField<T> = if (this == null) PatchField.Null else PatchField.of(this)

internal fun List<BattleTask>.findTask(id: Int): BattleTask? =
    firstNotNullOfOrNull { task -> task.takeIf { it.id == id } ?: task.subtasks.findTask(id) }
