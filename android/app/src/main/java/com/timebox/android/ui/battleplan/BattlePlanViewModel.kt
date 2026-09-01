package com.timebox.android.ui.battleplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.BattlePlanPreferences
import com.timebox.android.data.BattlePlanSort
import com.timebox.android.data.BattleTask
import com.timebox.android.data.BattleTaskCreate
import com.timebox.android.data.BattleTaskPatch
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.Project
import com.timebox.android.data.TaskCollection
import com.timebox.android.data.TaskPlacement
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.data.remote.PatchField
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.time.Duration
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BattlePlanScopeKind { All, Admin, Project }

data class BattlePlanScope(val kind: BattlePlanScopeKind, val projectId: Int? = null, val label: String) {
    val preferenceKey: String get() = when (kind) {
        BattlePlanScopeKind.All -> "all"
        BattlePlanScopeKind.Admin -> "admin"
        BattlePlanScopeKind.Project -> "project:$projectId"
    }

    companion object {
        val All = BattlePlanScope(BattlePlanScopeKind.All, label = "All Tasks")
        val Admin = BattlePlanScope(BattlePlanScopeKind.Admin, label = "Admin")
        fun project(project: Project) = BattlePlanScope(BattlePlanScopeKind.Project, project.id, project.name)
    }
}

data class ProjectDeleteSummary(val project: Project, val taskCount: Int)

data class TaskComposerDraft(
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
    val moreOpen: Boolean = false,
    val dirty: Boolean = false,
)

data class CreatedTaskNotice(val taskId: Int, val message: String)

data class BattlePlanUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val projects: List<Project> = emptyList(),
    val taskTypes: List<TaskType> = emptyList(),
    val tasks: List<BattleTask> = emptyList(),
    val collection: TaskCollection = TaskCollection.Active,
    val selectedScope: BattlePlanScope = BattlePlanScope.All,
    val selectedStatus: TaskStatus = TaskStatus.Open,
    val sort: BattlePlanSort = BattlePlanSort.Manual,
    val hideCompleted: Boolean = false,
    val urgencyFilter: Set<String> = emptySet(),
    val importanceFilter: Set<String> = emptySet(),
    val taskTypeFilter: Set<String> = emptySet(),
    val timezone: String = "UTC",
    val serverNow: Instant = Instant.EPOCH,
    val error: String? = null,
    val message: String? = null,
    val showComposer: Boolean = false,
    val composerDraft: TaskComposerDraft = TaskComposerDraft(),
    val composerSubmitted: Boolean = false,
    val composerError: String? = null,
    val createdTaskNotice: CreatedTaskNotice? = null,
    val deleteSummaryLoading: Boolean = false,
    val projectDeleteSummary: ProjectDeleteSummary? = null,
    val undoTaskId: Int? = null,
    val permanentDeleteTask: BattleTask? = null,
) {
    val scopes: List<BattlePlanScope>
        get() = listOf(BattlePlanScope.All, BattlePlanScope.Admin) + projects.map(BattlePlanScope::project)

    val filteredTasks: List<BattleTask>
        get() = tasks.inScope(selectedScope).filter { task ->
            (!hideCompleted || task.status != TaskStatus.Completed) &&
                urgencyFilter.matches(task.urgency?.wire) &&
                importanceFilter.matches(task.importance?.wire) &&
                taskTypeFilter.matches(task.taskTypeId?.toString())
        }.sortedBy { it.position }

    val visibleTasks: List<BattleTask>
        get() = if (collection == TaskCollection.Active) filteredTasks.filter { it.status == selectedStatus }
        else filteredTasks

    val completedForArchive: List<BattleTask>
        get() = filteredTasks.filter { it.status == TaskStatus.Completed }

    fun count(status: TaskStatus): Int = filteredTasks.count { it.status == status }
}

internal fun Set<String>.matches(value: String?): Boolean = isEmpty() || (value ?: "unset") in this

internal fun taskComparator(sort: BattlePlanSort): Comparator<BattleTask> = Comparator { left, right ->
    when (sort) {
        BattlePlanSort.Manual -> compareValues(left.position, right.position)
        BattlePlanSort.Deadline -> compareValues(deadlineRank(left), deadlineRank(right))
            .takeIf { it != 0 } ?: compareValues(left.position, right.position)
        BattlePlanSort.Urgency -> compareValues(priorityRank(right.urgency), priorityRank(left.urgency))
            .takeIf { it != 0 } ?: compareValues(left.position, right.position)
        BattlePlanSort.Importance -> compareValues(priorityRank(right.importance), priorityRank(left.importance))
            .takeIf { it != 0 } ?: compareValues(left.position, right.position)
    }
}

private fun deadlineRank(task: BattleTask): Long = task.deadlineAt?.toEpochMilli()
    ?: task.deadlineDate?.toEpochDay()?.times(86_400_000L)
    ?: Long.MAX_VALUE

private fun priorityRank(priority: PriorityLevel?): Int = when (priority) {
    PriorityLevel.High -> 3
    PriorityLevel.Medium -> 2
    PriorityLevel.Low -> 1
    null -> 0
}

internal fun trashRetentionDays(serverNow: Instant, deletedAt: Instant?): Int? {
    deletedAt ?: return null
    val elapsed = Duration.between(deletedAt, serverNow).toDays().coerceAtLeast(0)
    return (30 - elapsed).coerceAtLeast(0).toInt()
}

class BattlePlanViewModel(
    private val repository: TimeboxRepository,
    private val taskCompletion: TaskCompletion,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        BattlePlanUiState(
            showComposer = savedStateHandle[COMPOSER_VISIBLE] ?: false,
            composerDraft = restoreComposerDraft(savedStateHandle),
        ),
    )
    val state: StateFlow<BattlePlanUiState> = _state.asStateFlow()
    private var preferencesLoaded = false
    private var clockJob: Job? = null

    fun load(showSpinner: Boolean = _state.value.tasks.isEmpty()) {
        val collection = _state.value.collection
        _state.update { it.copy(loading = showSpinner, refreshing = !showSpinner, error = null) }
        viewModelScope.launch {
            val saved = if (!preferencesLoaded) repository.battlePlanPreferences.first() else null
            val projectsDeferred = async { repository.listProjects() }
            val typesDeferred = async { repository.listTaskTypes() }
            val tasksDeferred = async { repository.listBattleTasks(collection) }
            val projectsResult = projectsDeferred.await()
            val typesResult = typesDeferred.await()
            val tasksResult = tasksDeferred.await()
            val failure = projectsResult.exceptionOrNull() ?: typesResult.exceptionOrNull() ?: tasksResult.exceptionOrNull()
            if (failure != null) {
                _state.update { it.copy(loading = false, refreshing = false, error = failure.apiError.message) }
                return@launch
            }
            val projects = projectsResult.getOrThrow()
            val taskList = tasksResult.getOrThrow()
            preferencesLoaded = true
            _state.update { current ->
                val scope = saved?.resolveScope(projects) ?: current.selectedScope.takeIf { candidate ->
                    candidate.kind != BattlePlanScopeKind.Project || projects.any { it.id == candidate.projectId }
                } ?: BattlePlanScope.All
                current.copy(
                    loading = false, refreshing = false, projects = projects,
                    taskTypes = typesResult.getOrThrow(), tasks = taskList.items,
                    selectedScope = scope,
                    selectedStatus = (saved?.status ?: current.selectedStatus)
                        .takeIf { it in battlePlanStatuses } ?: TaskStatus.Open,
                    sort = BattlePlanSort.Manual,
                    hideCompleted = saved?.hideCompleted ?: current.hideCompleted,
                    urgencyFilter = saved?.urgency ?: current.urgencyFilter,
                    importanceFilter = saved?.importance ?: current.importanceFilter,
                    taskTypeFilter = saved?.taskTypes ?: current.taskTypeFilter,
                    timezone = taskList.timezone, serverNow = taskList.serverNow, error = null,
                )
            }
            anchorClock(taskList.serverNow, taskList.timezone)
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

    fun selectCollection(collection: TaskCollection) {
        if (_state.value.collection == collection) return
        _state.update { it.copy(collection = collection, tasks = emptyList(), loading = true) }
        load(true)
    }

    fun selectScope(scope: BattlePlanScope) { _state.update { it.copy(selectedScope = scope) }; persistView() }
    fun selectStatus(status: TaskStatus) { _state.update { it.copy(selectedStatus = status) }; persistView() }
    fun setHideCompleted(value: Boolean) { _state.update { it.copy(hideCompleted = value) }; persistView() }
    fun toggleUrgency(value: String) { _state.update { it.copy(urgencyFilter = it.urgencyFilter.toggle(value)) }; persistView() }
    fun toggleImportance(value: String) { _state.update { it.copy(importanceFilter = it.importanceFilter.toggle(value)) }; persistView() }
    fun toggleTaskType(value: String) { _state.update { it.copy(taskTypeFilter = it.taskTypeFilter.toggle(value)) }; persistView() }
    fun clearFilters() { _state.update { it.copy(urgencyFilter = emptySet(), importanceFilter = emptySet(), taskTypeFilter = emptySet()) }; persistView() }
    fun setComposerVisible(visible: Boolean) {
        if (!visible) {
            discardComposer()
            return
        }
        _state.update { current ->
            if (current.showComposer) current else {
                current.copy(
                    showComposer = true,
                    composerDraft = initialComposerDraft(current.selectedScope, current.selectedStatus),
                    composerSubmitted = false,
                    composerError = null,
                )
            }
        }
        persistComposer()
    }

    fun updateComposerDraft(draft: TaskComposerDraft) {
        _state.update { it.copy(composerDraft = draft.copy(dirty = true), composerError = null) }
        persistComposer()
    }

    fun setComposerReminderEnabled(enabled: Boolean) {
        val current = _state.value
        val draft = current.composerDraft
        if (!enabled) {
            updateComposerDraft(draft.copy(reminderEnabled = false))
            return
        }
        val zone = runCatching { ZoneId.of(current.timezone) }.getOrDefault(ZoneId.of("UTC"))
        val date = runCatching { LocalDate.parse(draft.deadlineDate) }.getOrNull()
            ?: current.serverNow.atZone(zone).toLocalDate()
        val suggested = if (draft.deadlineMode == TaskDeadlineMode.DateTime) {
            val time = runCatching { LocalTime.parse(draft.deadlineTime) }.getOrDefault(LocalTime.of(9, 0))
            LocalDateTime.of(date, time).minusHours(1)
        } else {
            LocalDateTime.of(date, LocalTime.of(9, 0))
        }
        updateComposerDraft(
            draft.copy(
                reminderEnabled = true,
                reminderDate = suggested.toLocalDate().toString(),
                reminderTime = suggested.toLocalTime().format(TIME_FORMAT),
            ),
        )
    }

    fun discardComposer() {
        _state.update {
            it.copy(
                showComposer = false,
                composerDraft = TaskComposerDraft(),
                composerSubmitted = false,
                composerError = null,
            )
        }
        clearSavedComposer()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeCreatedTaskNotice() = _state.update { it.copy(createdTaskNotice = null) }
    fun dismissUndo() = _state.update { it.copy(undoTaskId = null) }
    fun offerUndo(taskId: Int) = _state.update { it.copy(undoTaskId = taskId) }

    fun createTask() {
        val current = _state.value
        val validation = validateTaskComposer(current.composerDraft, current.timezone)
        if (validation is TaskComposerValidation.Invalid) {
            _state.update { it.copy(composerSubmitted = true, composerError = validation.message) }
            return
        }
        validation as TaskComposerValidation.Valid
        if (current.saving) return
        _state.update { it.copy(saving = true, composerSubmitted = true, composerError = null) }
        viewModelScope.launch {
            repository.createBattleTask(validation.request).fold(
                onSuccess = { task ->
                    val message = if (current.selectedStatus == TaskStatus.Completed) "Task created in Open" else "Task created"
                    _state.update {
                        it.copy(
                            saving = false,
                            showComposer = false,
                            composerDraft = TaskComposerDraft(),
                            composerSubmitted = false,
                            composerError = null,
                            createdTaskNotice = CreatedTaskNotice(task.id, message),
                        )
                    }
                    clearSavedComposer()
                    load(false)
                },
                onFailure = { error ->
                    _state.update { it.copy(saving = false, composerError = error.apiError.message) }
                },
            )
        }
    }

    fun createSubtask(parent: BattleTask, title: String) {
        if (title.isBlank() || parent.status == TaskStatus.Completed) return
        mutate("Subtask created") { repository.createBattleTask(BattleTaskCreate(title.trim(), parentId = parent.id, projectId = parent.projectId)) }
    }

    fun toggleReady(task: BattleTask) {
        if (task.status == TaskStatus.Completed) return
        mutate(if (task.readyToPlan) "Removed from Ready to Plan" else "Ready to Plan") {
            repository.patchBattleTask(task.id, BattleTaskPatch(readyToPlan = PatchField.of(!task.readyToPlan)))
        }
    }

    fun toggleSubtaskComplete(subtask: Subtask) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result = if (subtask.checked) repository.uncheckSubtask(subtask.id)
            else repository.checkSubtask(subtask.id)
            result.fold(
                onSuccess = { load(showSpinner = false) },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    fun moveTask(task: BattleTask, target: TaskStatus) {
        if (task.status == target) return
        if (task.status == TaskStatus.Completed && target != TaskStatus.Open) return
        if (target == TaskStatus.Completed || task.status == TaskStatus.Completed) {
            if (target == TaskStatus.Completed) requestCompletion(task) else setCompletion(task, false)
        } else {
            optimisticReorder(statusMovePlacements(_state.value.tasks, task, target), "Moved to ${target.label}")
        }
    }

    fun dropTask(task: BattleTask, target: TaskStatus, targetIndex: Int) {
        if (target !in battlePlanStatuses) return
        if (task.status == TaskStatus.Completed && target != TaskStatus.Open) return
        if (task.status != target && (target == TaskStatus.Completed || task.status == TaskStatus.Completed)) {
            if (target == TaskStatus.Completed) requestCompletion(task) else setCompletion(task, false)
            if (_state.value.selectedStatus != target) {
                _state.update { it.copy(selectedStatus = target) }
                persistView()
            }
            return
        }
        val placements = dropTaskPlacements(
            tasks = _state.value.tasks,
            visible = _state.value.filteredTasks,
            moving = task,
            target = target,
            targetIndex = targetIndex,
        )
        optimisticReorder(placements, "Moved to ${target.label}")
        if (_state.value.selectedStatus != target) {
            _state.update { it.copy(selectedStatus = target) }
            persistView()
        }
    }

    private fun requestCompletion(task: BattleTask) {
        setCompletion(task, true)
    }

    private fun setCompletion(task: BattleTask, completed: Boolean) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            if (completed) {
                taskCompletion.transition(task.id, task.status, TaskStatus.Completed).fold(
                    onSuccess = {
                        _state.update { it.copy(saving = false) }
                        load(showSpinner = false)
                    },
                    onFailure = {
                        _state.update { it.copy(saving = false) }
                        refreshAfterTaskCompletion()
                    },
                )
            } else {
                taskCompletion.transition(task.id, task.status, TaskStatus.Open).fold(
                    onSuccess = {
                        _state.update { state -> state.copy(saving = false) }
                        load(showSpinner = false)
                    },
                    onFailure = {
                        _state.update { it.copy(saving = false) }
                        refreshAfterTaskCompletion()
                    },
                )
            }
        }
    }

    fun refreshAfterTaskCompletion() = load(showSpinner = false)

    fun setBlocked(task: BattleTask, blocked: Boolean, reason: String?) {
        if (task.status == TaskStatus.Completed) return
        mutate(if (blocked) "Task blocked" else "Task unblocked") {
            repository.patchBattleTask(
                task.id,
                BattleTaskPatch(
                    isBlocked = PatchField.of(blocked),
                    blockingReason = if (blocked) PatchField.of(reason?.trim().orEmpty()) else PatchField.Null,
                ),
            )
        }
    }

    fun reorderTask(task: BattleTask, offset: Int) {
        if (_state.value.sort != BattlePlanSort.Manual || offset == 0) return
        val visible = _state.value.visibleTasks
        val index = visible.indexOfFirst { it.id == task.id }
        val other = visible.getOrNull(index + offset) ?: return
        val ordered = _state.value.tasks.filter { it.status == task.status }.sortedBy { it.position }.toMutableList()
        val from = ordered.indexOfFirst { it.id == task.id }
        val to = ordered.indexOfFirst { it.id == other.id }
        if (from < 0 || to < 0) return
        val moving = ordered.removeAt(from)
        ordered.add(to, moving)
        optimisticReorder(ordered.mapIndexed { position, item -> TaskPlacement(item.id, item.status, position) }, "Order saved")
    }

    fun archiveCompleted() {
        val ids = _state.value.completedForArchive.map { it.id }
        if (ids.isNotEmpty()) mutate("Archived ${ids.size} completed task${if (ids.size == 1) "" else "s"}") { repository.archiveCompletedBattleTasks(ids) }
    }

    fun restoreArchived(task: BattleTask) = mutate("Task restored") { repository.unarchiveBattleTask(task.id) }
    fun restoreTrashed(task: BattleTask) = mutate("Task restored") { repository.restoreBattleTask(task.id) }

    fun trashTask(task: BattleTask) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            repository.trashBattleTask(task.id).fold(
                onSuccess = { _state.update { it.copy(saving = false, undoTaskId = task.id, message = "Moved to Trash") }; load(false) },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    fun undoTrash() {
        val id = _state.value.undoTaskId ?: return
        _state.update { it.copy(undoTaskId = null) }
        mutate("Trash move undone") { repository.restoreBattleTask(id) }
    }

    fun requestPermanentDelete(task: BattleTask) = _state.update { it.copy(permanentDeleteTask = task) }
    fun dismissPermanentDelete() = _state.update { it.copy(permanentDeleteTask = null) }
    fun confirmPermanentDelete() {
        val task = _state.value.permanentDeleteTask ?: return
        _state.update { it.copy(permanentDeleteTask = null) }
        mutate("Task permanently deleted") { repository.permanentlyDeleteBattleTask(task.id) }
    }

    fun prepareProjectDelete(project: Project) {
        _state.update { it.copy(deleteSummaryLoading = true, message = null) }
        viewModelScope.launch {
            val collections = TaskCollection.entries.map { repository.listBattleTasks(it) }
            val error = collections.firstNotNullOfOrNull { it.exceptionOrNull() }
            if (error != null) _state.update { it.copy(deleteSummaryLoading = false, message = error.apiError.message) }
            else _state.update { it.copy(deleteSummaryLoading = false, projectDeleteSummary = ProjectDeleteSummary(project, collections.sumOf { result -> result.getOrThrow().items.countProjectTasks(project.id) })) }
        }
    }

    fun dismissProjectDelete() = _state.update { it.copy(projectDeleteSummary = null) }
    fun confirmProjectDelete() {
        val project = _state.value.projectDeleteSummary?.project ?: return
        _state.update { it.copy(projectDeleteSummary = null) }
        mutate("Project deleted") { repository.deleteProject(project.id) }
    }

    private fun optimisticReorder(placements: List<TaskPlacement>, message: String) {
        if (_state.value.saving || placements.isEmpty()) return
        val previous = _state.value.tasks
        val byId = placements.associateBy { it.taskId }
        val next = previous.map { task -> byId[task.id]?.let { task.copy(status = it.status, position = it.position) } ?: task }
        _state.update { it.copy(tasks = next, saving = true, message = null) }
        viewModelScope.launch {
            repository.reorderBattleTasks(placements).fold(
                onSuccess = { _state.update { it.copy(saving = false, message = message) }; load(false) },
                onFailure = { error -> _state.update { it.copy(tasks = previous, saving = false, message = error.apiError.message) } },
            )
        }
    }

    private fun mutate(successMessage: String, operation: suspend () -> Result<*>) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            operation().fold(
                onSuccess = { _state.update { it.copy(saving = false, showComposer = false, message = successMessage) }; load(false) },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
            )
        }
    }

    private fun persistView() {
        val current = _state.value
        viewModelScope.launch {
            repository.setBattlePlanView(BattlePlanPreferences(current.selectedScope.preferenceKey, current.selectedStatus, current.sort, current.hideCompleted, current.urgencyFilter, current.importanceFilter, current.taskTypeFilter))
        }
    }

    private fun persistComposer() {
        val state = _state.value
        val draft = state.composerDraft
        savedStateHandle[COMPOSER_VISIBLE] = state.showComposer
        savedStateHandle[COMPOSER_TITLE] = draft.title
        savedStateHandle[COMPOSER_DESCRIPTION] = draft.description
        savedStateHandle[COMPOSER_STATUS] = draft.status.name
        savedStateHandle[COMPOSER_PROJECT_ID] = draft.projectId
        savedStateHandle[COMPOSER_TASK_TYPE_ID] = draft.taskTypeId
        savedStateHandle[COMPOSER_URGENCY] = draft.urgency?.name
        savedStateHandle[COMPOSER_IMPORTANCE] = draft.importance?.name
        savedStateHandle[COMPOSER_DEADLINE_MODE] = draft.deadlineMode.name
        savedStateHandle[COMPOSER_DEADLINE_DATE] = draft.deadlineDate
        savedStateHandle[COMPOSER_DEADLINE_TIME] = draft.deadlineTime
        savedStateHandle[COMPOSER_REMINDER_ENABLED] = draft.reminderEnabled
        savedStateHandle[COMPOSER_REMINDER_DATE] = draft.reminderDate
        savedStateHandle[COMPOSER_REMINDER_TIME] = draft.reminderTime
        savedStateHandle[COMPOSER_READY] = draft.readyToPlan
        savedStateHandle[COMPOSER_MORE_OPEN] = draft.moreOpen
        savedStateHandle[COMPOSER_DIRTY] = draft.dirty
    }

    private fun clearSavedComposer() {
        COMPOSER_KEYS.forEach { key -> savedStateHandle.remove<Any?>(key) }
    }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        private const val COMPOSER_VISIBLE = "battlePlan.composer.visible"
        private const val COMPOSER_TITLE = "battlePlan.composer.title"
        private const val COMPOSER_DESCRIPTION = "battlePlan.composer.description"
        private const val COMPOSER_STATUS = "battlePlan.composer.status"
        private const val COMPOSER_PROJECT_ID = "battlePlan.composer.projectId"
        private const val COMPOSER_TASK_TYPE_ID = "battlePlan.composer.taskTypeId"
        private const val COMPOSER_URGENCY = "battlePlan.composer.urgency"
        private const val COMPOSER_IMPORTANCE = "battlePlan.composer.importance"
        private const val COMPOSER_DEADLINE_MODE = "battlePlan.composer.deadlineMode"
        private const val COMPOSER_DEADLINE_DATE = "battlePlan.composer.deadlineDate"
        private const val COMPOSER_DEADLINE_TIME = "battlePlan.composer.deadlineTime"
        private const val COMPOSER_REMINDER_ENABLED = "battlePlan.composer.reminderEnabled"
        private const val COMPOSER_REMINDER_DATE = "battlePlan.composer.reminderDate"
        private const val COMPOSER_REMINDER_TIME = "battlePlan.composer.reminderTime"
        private const val COMPOSER_READY = "battlePlan.composer.ready"
        private const val COMPOSER_MORE_OPEN = "battlePlan.composer.moreOpen"
        private const val COMPOSER_DIRTY = "battlePlan.composer.dirty"
        private val COMPOSER_KEYS = listOf(
            COMPOSER_VISIBLE, COMPOSER_TITLE, COMPOSER_DESCRIPTION, COMPOSER_STATUS,
            COMPOSER_PROJECT_ID, COMPOSER_TASK_TYPE_ID, COMPOSER_URGENCY, COMPOSER_IMPORTANCE,
            COMPOSER_DEADLINE_MODE, COMPOSER_DEADLINE_DATE, COMPOSER_DEADLINE_TIME,
            COMPOSER_REMINDER_ENABLED, COMPOSER_REMINDER_DATE, COMPOSER_REMINDER_TIME,
            COMPOSER_READY, COMPOSER_MORE_OPEN, COMPOSER_DIRTY,
        )
    }
}

sealed interface TaskComposerValidation {
    data class Valid(val request: BattleTaskCreate) : TaskComposerValidation
    data class Invalid(val message: String) : TaskComposerValidation
}

internal fun validateTaskComposer(draft: TaskComposerDraft, timezone: String): TaskComposerValidation {
    if (draft.title.isBlank()) return TaskComposerValidation.Invalid("Task title is required.")
    if (draft.title.length > 500) return TaskComposerValidation.Invalid("Task title must be 500 characters or fewer.")
    val taskValidation = validateTaskDraft(
        TaskDetailUiState(
            title = draft.title,
            timezone = timezone,
            deadlineMode = draft.deadlineMode,
            deadlineDate = draft.deadlineDate,
            deadlineTime = draft.deadlineTime,
            reminderEnabled = draft.reminderEnabled,
            reminderDate = draft.reminderDate,
            reminderTime = draft.reminderTime,
        ),
    )
    if (taskValidation is TaskDraftValidation.Invalid) return TaskComposerValidation.Invalid(taskValidation.message)
    taskValidation as TaskDraftValidation.Valid
    return TaskComposerValidation.Valid(
        BattleTaskCreate(
            title = draft.title.trim(),
            description = draft.description.trim(),
            readyToPlan = draft.readyToPlan,
            status = draft.status.takeIf { it == TaskStatus.Open || it == TaskStatus.InProgress } ?: TaskStatus.Open,
            projectId = draft.projectId,
            taskTypeId = draft.taskTypeId,
            urgency = draft.urgency,
            importance = draft.importance,
            deadlineDate = taskValidation.deadlineDate,
            deadlineAt = taskValidation.deadlineAt,
            reminderAt = taskValidation.reminderAt,
        ),
    )
}

internal fun initialComposerDraft(scope: BattlePlanScope, selectedStatus: TaskStatus): TaskComposerDraft =
    TaskComposerDraft(
        status = selectedStatus.takeIf { it == TaskStatus.Open || it == TaskStatus.InProgress } ?: TaskStatus.Open,
        projectId = scope.projectId.takeIf { scope.kind == BattlePlanScopeKind.Project },
    )

internal fun restoreComposerDraft(handle: SavedStateHandle): TaskComposerDraft = TaskComposerDraft(
    title = handle["battlePlan.composer.title"] ?: "",
    description = handle["battlePlan.composer.description"] ?: "",
    status = handle.get<String>("battlePlan.composer.status")?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() }
        ?.takeIf { it == TaskStatus.Open || it == TaskStatus.InProgress } ?: TaskStatus.Open,
    projectId = handle["battlePlan.composer.projectId"],
    taskTypeId = handle["battlePlan.composer.taskTypeId"],
    urgency = handle.get<String>("battlePlan.composer.urgency")?.let { runCatching { PriorityLevel.valueOf(it) }.getOrNull() },
    importance = handle.get<String>("battlePlan.composer.importance")?.let { runCatching { PriorityLevel.valueOf(it) }.getOrNull() },
    deadlineMode = handle.get<String>("battlePlan.composer.deadlineMode")?.let { runCatching { TaskDeadlineMode.valueOf(it) }.getOrNull() }
        ?: TaskDeadlineMode.None,
    deadlineDate = handle["battlePlan.composer.deadlineDate"] ?: "",
    deadlineTime = handle["battlePlan.composer.deadlineTime"] ?: "",
    reminderEnabled = handle["battlePlan.composer.reminderEnabled"] ?: false,
    reminderDate = handle["battlePlan.composer.reminderDate"] ?: "",
    reminderTime = handle["battlePlan.composer.reminderTime"] ?: "",
    readyToPlan = handle["battlePlan.composer.ready"] ?: false,
    moreOpen = handle["battlePlan.composer.moreOpen"] ?: false,
    dirty = handle["battlePlan.composer.dirty"] ?: false,
)

internal fun statusMovePlacements(tasks: List<BattleTask>, moving: BattleTask, target: TaskStatus): List<TaskPlacement> {
    val targetTasks = tasks.filter { it.id != moving.id && it.status == target }.sortedBy { it.position }
    val sourceTasks = tasks.filter { it.id != moving.id && it.status == moving.status }.sortedBy { it.position }
    return sourceTasks.mapIndexed { index, item -> TaskPlacement(item.id, moving.status, index) } +
        targetTasks.mapIndexed { index, item -> TaskPlacement(item.id, target, index) } +
        TaskPlacement(moving.id, target, targetTasks.size)
}

internal fun dropTaskPlacements(
    tasks: List<BattleTask>,
    visible: List<BattleTask>,
    moving: BattleTask,
    target: TaskStatus,
    targetIndex: Int,
): List<TaskPlacement> {
    val sourceStatus = moving.status
    val source = tasks.filter { it.id != moving.id && it.status == sourceStatus }.sortedBy { it.position }
    val destination = if (sourceStatus == target) source.toMutableList()
    else tasks.filter { it.id != moving.id && it.status == target }.sortedBy { it.position }.toMutableList()
    val visibleDestination = visible.filter { it.id != moving.id && it.status == target }
    val clamped = targetIndex.coerceIn(0, visibleDestination.size)
    val beforeId = visibleDestination.getOrNull(clamped)?.id
    val afterId = visibleDestination.getOrNull(clamped - 1)?.id
    val insertion = when {
        beforeId != null -> destination.indexOfFirst { it.id == beforeId }.takeIf { it >= 0 } ?: destination.size
        afterId != null -> (destination.indexOfFirst { it.id == afterId } + 1).coerceAtLeast(0)
        else -> destination.size
    }
    destination.add(insertion.coerceIn(0, destination.size), moving.copy(status = target))
    val targetPlacements = destination.mapIndexed { position, item -> TaskPlacement(item.id, target, position) }
    if (sourceStatus == target) return targetPlacements
    return source.mapIndexed { position, item -> TaskPlacement(item.id, sourceStatus, position) } + targetPlacements
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

private fun BattlePlanPreferences.resolveScope(projects: List<Project>): BattlePlanScope = when {
    scope == "admin" -> BattlePlanScope.Admin
    scope.startsWith("project:") -> scope.substringAfter(':').toIntOrNull()?.let { id -> projects.firstOrNull { it.id == id } }?.let(BattlePlanScope::project) ?: BattlePlanScope.All
    else -> BattlePlanScope.All
}

private fun List<BattleTask>.countProjectTasks(projectId: Int): Int = count { it.projectId == projectId }

internal fun List<BattleTask>.inScope(scope: BattlePlanScope): List<BattleTask> = when (scope.kind) {
    BattlePlanScopeKind.All -> this
    BattlePlanScopeKind.Admin -> filter { it.projectId == null }
    BattlePlanScopeKind.Project -> filter { it.projectId == scope.projectId }
}

val TaskStatus.label: String get() = when (this) {
    TaskStatus.Open -> "Open"
    TaskStatus.InProgress -> "In Progress"
    TaskStatus.Blocked -> "Blocked"
    TaskStatus.Completed -> "Completed"
}

internal val battlePlanStatuses = listOf(
    TaskStatus.Open,
    TaskStatus.InProgress,
    TaskStatus.Completed,
)

val BattlePlanSort.label: String get() = when (this) {
    BattlePlanSort.Manual -> "Manual order"
    BattlePlanSort.Deadline -> "Deadline"
    BattlePlanSort.Urgency -> "Urgency"
    BattlePlanSort.Importance -> "Importance"
}
