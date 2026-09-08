package com.timebox.android.ui.planning

import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.MIN_PLANNED_BLOCK_MINUTES
import com.timebox.android.data.PlanningCommitPlacement
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.data.flattenBattleTasks
import com.timebox.android.ui.readiness.ReadyToPlanCoordinator
import com.timebox.android.ui.readiness.ReadyToPlanCoordinators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class PlanningDraftPlacement(
    val date: LocalDate,
    val task: BattleTask,
    val startMinute: Int,
    val endMinute: Int,
) {
    val taskId: Int get() = task.id
    val taskTitle: String get() = task.title
}

data class PlanningSessionState(
    val active: Boolean = false,
    val readyTasks: List<BattleTask> = emptyList(),
    val queueLoading: Boolean = false,
    val queueError: String? = null,
    val selectedTaskId: Int? = null,
    val drafts: Map<Int, PlanningDraftPlacement> = emptyMap(),
    val saving: Boolean = false,
    val failure: String? = null,
) {
    val selectedTask: BattleTask?
        get() = readyTasks.firstOrNull { it.id == selectedTaskId }

    fun drafts(date: LocalDate): List<PlanningDraftPlacement> =
        drafts.values.filter { it.date == date }

    fun hasRailContent(date: LocalDate): Boolean {
        if (queueLoading || queueError != null || selectedTaskId != null) return true
        val drafted = drafts(date).mapTo(mutableSetOf()) { it.taskId }
        return readyTasks.any { it.id !in drafted }
    }
}

sealed interface PlanningEditResult {
    data object Accepted : PlanningEditResult
    data class Rejected(val reason: String) : PlanningEditResult
}

sealed interface PlanningCommitOutcome {
    data object CancelledEmptySession : PlanningCommitOutcome
    data class Saved(val days: List<Day>) : PlanningCommitOutcome
    data class Failed(val reason: String) : PlanningCommitOutcome
    data object Ignored : PlanningCommitOutcome
}

/** Internal seam between planning policy and the owned Timebox transport. */
internal interface PlanningSessionTransport {
    suspend fun loadScopedTasks(planningDate: LocalDate?): Result<List<BattleTask>>
    suspend fun commit(placements: List<PlanningCommitPlacement>): Result<List<Day>>
}

/** Production adapter; module tests use an in-memory adapter at the same seam. */
internal class RepositoryPlanningSessionTransport(
    private val repository: TimeboxRepository,
    private val readinessCoordinator: ReadyToPlanCoordinator =
        ReadyToPlanCoordinators.forRepository(repository),
) : PlanningSessionTransport {
    override suspend fun loadScopedTasks(planningDate: LocalDate?): Result<List<BattleTask>> =
        repository.listBattleTasks(planningDate = planningDate).map { result ->
            readinessCoordinator.mergeServerTasks(result.items)
            readinessCoordinator.projectTasks(result.items)
        }

    override suspend fun commit(placements: List<PlanningCommitPlacement>): Result<List<Day>> =
        repository.commitPlan(placements)
}

/**
 * Owns a complete Plan Mode session.
 *
 * Callers express user intent against a loaded Day. This module owns queue loading,
 * selection, draft invariants, overlap policy, atomic commit ordering, optimistic
 * queue reconciliation, and failure recovery. The backend
 * derives Task Type for linked Tasks; this interface deliberately has no Task Type.
 */
class PlanningSession internal constructor(
    private val transport: PlanningSessionTransport,
) {
    private val commitMutex = Mutex()
    private val _state = MutableStateFlow(PlanningSessionState())
    val state: StateFlow<PlanningSessionState> = _state.asStateFlow()

    private var calendar = emptyMap<LocalDate, Day>()
    private var planningDate: LocalDate? = null
    private var scopedTasks: List<BattleTask> = emptyList()

    suspend fun refreshQueue(date: LocalDate? = planningDate) {
        planningDate = date
        val current = _state.value
        _state.value = current.copy(
            queueLoading = current.readyTasks.isEmpty(),
            queueError = null,
        )
        transport.loadScopedTasks(date).fold(
            onSuccess = { tasks ->
                scopedTasks = tasks
                val ready = tasks.readyToPlanTasks()
                _state.value = _state.value.copy(
                    readyTasks = ready,
                    queueLoading = false,
                    selectedTaskId = _state.value.selectedTaskId
                        ?.takeIf { id -> ready.any { it.id == id } && id !in _state.value.drafts },
                )
            },
            onFailure = { failure ->
                _state.value = _state.value.copy(
                    queueLoading = false,
                    queueError = failure.apiError.message,
                )
            },
        )
    }

    fun begin() {
        if (_state.value.active || _state.value.saving) return
        calendar = emptyMap()
        _state.value = _state.value.copy(
            active = true,
            selectedTaskId = null,
            drafts = emptyMap(),
            saving = false,
            failure = null,
        )
    }

    fun cancel() {
        if (_state.value.saving) return
        calendar = emptyMap()
        _state.value = _state.value.copy(
            active = false,
            selectedTaskId = null,
            drafts = emptyMap(),
            saving = false,
            failure = null,
        )
    }

    fun toggleSelection(taskId: Int?) {
        val current = _state.value
        val selectable = taskId?.takeIf { id ->
            current.active && !current.saving && id !in current.drafts &&
                current.readyTasks.any { it.id == id && !it.readinessPending }
        }
        _state.value = current.copy(
            selectedTaskId = selectable?.takeUnless { it == current.selectedTaskId },
            failure = null,
        )
    }

    fun place(taskId: Int, day: Day, startMinute: Int): PlanningEditResult {
        val current = _state.value
        val task = current.readyTasks.firstOrNull { it.id == taskId }
            ?: return reject("That Task is no longer Ready to Plan")
        if (task.readinessPending) return reject("That Task is still saving")
        if (!current.active || current.saving || taskId in current.drafts) {
            return reject("That Task cannot be planned right now")
        }
        val start = startMinute.coerceIn(
            day.visibleStart,
            day.visibleEnd - MIN_PLANNED_BLOCK_MINUTES,
        )
        val end = start + MIN_PLANNED_BLOCK_MINUTES
        if (!planningRangeAvailable(day, current.drafts.values, taskId, start, end)) {
            return reject("That time is already planned")
        }
        calendar = calendar + (day.date to day)
        _state.value = current.copy(
            drafts = current.drafts + (taskId to PlanningDraftPlacement(day.date, task, start, end)),
            selectedTaskId = null,
            failure = null,
        )
        return PlanningEditResult.Accepted
    }

    /** Commit exactly the drag preview, validating against the caller's freshly loaded Day. */
    fun drop(taskId: Int, day: Day, startMinute: Int, endMinute: Int): PlanningEditResult {
        val current = _state.value
        if (!current.active || current.saving) return reject("That Task cannot be planned right now")
        val original = current.drafts[taskId]
        val duration = original?.let { it.endMinute - it.startMinute } ?: MIN_PLANNED_BLOCK_MINUTES
        if ((original != null && original.date != day.date) || endMinute - startMinute != duration ||
            !planningRangeAvailable(day, current.drafts.values, taskId, startMinute, endMinute)
        ) return reject("That time is no longer available")

        return if (original == null) {
            place(taskId, day, startMinute)
        } else {
            calendar = calendar + (day.date to day)
            update(taskId, startMinute, endMinute)
        }
    }

    fun update(taskId: Int, startMinute: Int, endMinute: Int): PlanningEditResult {
        val current = _state.value
        val draft = current.drafts[taskId] ?: return reject("That planning draft no longer exists")
        val day = calendar[draft.date] ?: return reject("That day is not available yet")
        if (!current.active || current.saving ||
            !planningRangeAvailable(day, current.drafts.values, taskId, startMinute, endMinute)
        ) {
            return reject("That time is already planned")
        }
        _state.value = current.copy(
            drafts = current.drafts + (
                taskId to draft.copy(startMinute = startMinute, endMinute = endMinute)
            ),
            failure = null,
        )
        return PlanningEditResult.Accepted
    }

    fun returnTask(taskId: Int) {
        if (!_state.value.active || _state.value.saving) return
        _state.value = _state.value.copy(
            drafts = _state.value.drafts - taskId,
            failure = null,
        )
    }

    /** Project the app-scoped readiness state over this planning date's authoritative Task set. */
    internal fun applyReadinessProjection(project: (List<BattleTask>) -> List<BattleTask>) {
        scopedTasks = project(scopedTasks)
        val readyTasks = scopedTasks.readyToPlanTasks()
        val eligibleIds = readyTasks.asSequence()
            .filterNot(BattleTask::readinessPending)
            .mapTo(mutableSetOf(), BattleTask::id)
        _state.value = _state.value.copy(
            readyTasks = readyTasks,
            selectedTaskId = _state.value.selectedTaskId?.takeIf(eligibleIds::contains),
            drafts = _state.value.drafts.filterKeys(eligibleIds::contains),
        )
    }

    suspend fun commit(): PlanningCommitOutcome = commitMutex.withLock {
        val current = _state.value
        if (!current.active || current.saving) return@withLock PlanningCommitOutcome.Ignored
        val drafts = current.drafts.values.toList()
        if (drafts.isEmpty()) {
            cancel()
            return@withLock PlanningCommitOutcome.CancelledEmptySession
        }

        _state.value = current.copy(saving = true, failure = null)
        val placements = drafts.map { draft ->
            PlanningCommitPlacement(
                date = draft.date,
                taskId = draft.taskId,
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
            )
        }
        transport.commit(placements).fold(
            onSuccess = { days ->
                val plannedTaskIds = drafts.mapTo(mutableSetOf()) { it.taskId }
                calendar = emptyMap()
                _state.value = _state.value.copy(
                    active = false,
                    readyTasks = _state.value.readyTasks.filterNot { it.id in plannedTaskIds },
                    selectedTaskId = null,
                    drafts = emptyMap(),
                    saving = false,
                    failure = null,
                )
                refreshQueue()
                PlanningCommitOutcome.Saved(days)
            },
            onFailure = { failure ->
                val message = failure.apiError.message
                _state.value = _state.value.copy(saving = false, failure = message)
                PlanningCommitOutcome.Failed(message)
            },
        )
    }

    private fun reject(reason: String): PlanningEditResult.Rejected {
        _state.value = _state.value.copy(failure = reason)
        return PlanningEditResult.Rejected(reason)
    }
}

internal fun planningRangeAvailable(
    day: Day,
    drafts: Collection<PlanningDraftPlacement>,
    taskId: Int?,
    startMinute: Int,
    endMinute: Int,
): Boolean {
    if (endMinute - startMinute < MIN_PLANNED_BLOCK_MINUTES) return false
    if (startMinute < day.visibleStart || endMinute > day.visibleEnd || startMinute >= endMinute) return false
    if (day.lane(Lane.Planned).any {
            intervalsOverlap(startMinute, endMinute, it.startMinute, it.endMinute)
        }
    ) return false
    return drafts.none {
        it.date == day.date && it.taskId != taskId &&
            intervalsOverlap(startMinute, endMinute, it.startMinute, it.endMinute)
    }
}

internal fun intervalsOverlap(start: Int, end: Int, otherStart: Int, otherEnd: Int): Boolean =
    start < otherEnd && otherStart < end

internal fun List<BattleTask>.readyToPlanTasks(): List<BattleTask> =
    flattenBattleTasks().filter(BattleTask::readyToPlan)
