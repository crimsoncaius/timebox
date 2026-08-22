package com.timebox.android.ui.battleplan

import androidx.lifecycle.ViewModel
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
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.data.remote.PatchField
import java.time.Duration
import java.time.Instant
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

class BattlePlanViewModel(private val repository: TimeboxRepository) : ViewModel() {
    private val _state = MutableStateFlow(BattlePlanUiState())
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
    fun setComposerVisible(visible: Boolean) = _state.update { it.copy(showComposer = visible) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun dismissUndo() = _state.update { it.copy(undoTaskId = null) }
    fun offerUndo(taskId: Int) = _state.update { it.copy(undoTaskId = taskId) }

    fun createTask(title: String, description: String, projectId: Int?) {
        if (title.isBlank()) { _state.update { it.copy(message = "Task title is required.") }; return }
        val initialStatus = _state.value.selectedStatus.takeUnless { it == TaskStatus.Completed } ?: TaskStatus.Open
        if (_state.value.selectedStatus == TaskStatus.Completed) {
            _state.update { it.copy(selectedStatus = TaskStatus.Open) }
            persistView()
        }
        mutate("Task created") {
            repository.createBattleTask(BattleTaskCreate(title.trim(), description.trim(), status = initialStatus, projectId = projectId))
        }
    }

    fun createSubtask(parent: BattleTask, title: String) {
        if (title.isBlank()) return
        mutate("Subtask created") { repository.createBattleTask(BattleTaskCreate(title.trim(), parentId = parent.id, projectId = parent.projectId)) }
    }

    fun toggleReady(task: BattleTask) = mutate(if (task.readyToPlan) "Removed from Ready to Plan" else "Ready to Plan") {
        repository.patchBattleTask(task.id, BattleTaskPatch(readyToPlan = PatchField.of(!task.readyToPlan)))
    }

    fun toggleSubtaskComplete(task: BattleTask) = mutate("Subtask updated") {
        repository.patchBattleTask(task.id, BattleTaskPatch(status = PatchField.of(if (task.status == TaskStatus.Completed) TaskStatus.Open else TaskStatus.Completed)))
    }

    fun moveTask(task: BattleTask, target: TaskStatus) {
        if (task.status != target) optimisticReorder(statusMovePlacements(_state.value.tasks, task, target), "Moved to ${target.label}")
    }

    fun dropTask(task: BattleTask, target: TaskStatus, targetIndex: Int) {
        if (target !in battlePlanStatuses) return
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

    fun setBlocked(task: BattleTask, blocked: Boolean, reason: String?) = mutate(
        if (blocked) "Task blocked" else "Task unblocked"
    ) {
        repository.patchBattleTask(
            task.id,
            BattleTaskPatch(
                isBlocked = PatchField.of(blocked),
                blockingReason = if (blocked) PatchField.of(reason?.trim().orEmpty()) else PatchField.Null,
            ),
        )
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
}

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

private fun List<BattleTask>.countProjectTasks(projectId: Int): Int = sumOf { parent ->
    (if (parent.projectId == projectId) 1 else 0) + parent.subtasks.count { it.projectId == projectId }
}

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
