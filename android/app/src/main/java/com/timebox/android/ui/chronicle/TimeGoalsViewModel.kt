package com.timebox.android.ui.chronicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.TimeGoalsWeek
import com.timebox.android.data.TimeGoalArchive
import com.timebox.android.data.TimeGoalHistory
import com.timebox.android.data.TaskType
import com.timebox.android.data.remote.TimeGoalWriteDto
import com.timebox.android.data.apiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TimeGoalsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val goals: TimeGoalsWeek? = null,
    val taskTypes: List<TaskType> = emptyList(),
    val goalSaving: Boolean = false,
    val goalError: String? = null,
    val offline: Boolean = false,
    val selectingGoal: Int? = null,
    val archiveOpen: Boolean = false,
    val archive: TimeGoalArchive? = null,
    val archiveLoading: Boolean = false,
    val archiveError: String? = null,
    val historyGoalId: Int? = null,
    val history: TimeGoalHistory? = null,
    val historyLoading: Boolean = false,
    val historyError: String? = null,
)

/** Owns Time Goals browsing independently of completion-based Habits. */
class TimeGoalsViewModel(private val repository: TimeboxRepository) : ViewModel() {
    private val _state = MutableStateFlow(TimeGoalsUiState())
    val state: StateFlow<TimeGoalsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var selectionJob: Job? = null
    private var archiveJob: Job? = null
    private var historyJob: Job? = null
    private val goalAnchors = mutableMapOf<Int, LocalDate>()

    fun refresh() {
        if (_state.value.goalSaving) return
        if (_state.value.archiveOpen) {
            if (_state.value.historyGoalId != null) loadHistory() else loadArchive()
        } else load(_state.value.goals?.weekStart)
    }

    fun openArchive() {
        if (_state.value.goalSaving) return
        loadJob?.cancel(); selectionJob?.cancel()
        _state.update { it.copy(archiveOpen = true, loading = false, selectingGoal = null, goalError = null) }
        loadArchive()
    }

    fun openGoalHistory(id: Int) {
        if (_state.value.goalSaving) return
        loadJob?.cancel(); selectionJob?.cancel(); archiveJob?.cancel(); historyJob?.cancel()
        _state.update { it.copy(archiveOpen = true, archiveLoading = false, loading = false, selectingGoal = null,
            historyGoalId = id, history = if (it.history?.goal?.id == id) it.history else null, goalError = null) }
        loadHistory()
    }

    fun archiveBack() {
        if (_state.value.goalSaving) return
        historyJob?.cancel(); archiveJob?.cancel()
        if (_state.value.historyGoalId != null) {
            _state.update { it.copy(historyGoalId = null, historyLoading = false, historyError = null, goalError = null) }
            loadArchive()
        } else {
            _state.update { it.copy(archiveOpen = false, archiveLoading = false, goalError = null) }
            refresh()
        }
    }

    private fun loadArchive() {
        archiveJob?.cancel()
        _state.update { it.copy(archiveLoading = true, archiveError = null) }
        archiveJob = viewModelScope.launch {
            repository.timeGoalArchive().fold(
                onSuccess = { archive -> _state.update { it.copy(archive = archive, archiveLoading = false, offline = false) } },
                onFailure = { e -> _state.update { it.copy(archiveLoading = false, archiveError = e.apiError.message, offline = e.apiError.isNetwork) } },
            )
        }
    }

    fun loadHistory(more: Boolean = false) {
        val state = _state.value
        val id = state.historyGoalId ?: return
        if (state.goalSaving || (more && (state.historyLoading || state.history?.nextBefore == null))) return
        historyJob?.cancel()
        _state.update { it.copy(historyLoading = true, historyError = null) }
        historyJob = viewModelScope.launch {
            var before = if (more) state.history?.nextBefore else null
            var combined = if (more) state.history else null
            // Refresh all loaded pages atomically so an offline failure retains the complete prior view.
            val wanted = if (more) 0 else state.history?.periods?.size ?: 0
            do {
                val result = repository.timeGoalHistory(id, before)
                if (result.isFailure) {
                    val error = result.exceptionOrNull()!!.apiError
                    _state.update { it.copy(historyLoading = false, historyError = error.message, offline = error.isNetwork) }
                    return@launch
                }
                val page = result.getOrThrow()
                combined = page.copy(periods = ((combined?.periods ?: emptyList()) + page.periods).distinctBy { it.start })
                val next = page.nextBefore
                // A malformed/non-advancing cursor must not cause an endless refresh loop.
                before = next?.takeIf { before == null || it < before }
            } while (before != null && combined!!.periods.size < wanted)
            _state.update { it.copy(history = combined, historyLoading = false, offline = false) }
        }
    }

    fun load(week: LocalDate?) {
        loadJob?.cancel()
        selectionJob?.cancel()
        if (week != _state.value.goals?.weekStart) goalAnchors.clear()
        _state.update { it.copy(loading = true, error = null, selectingGoal = null) }
        loadJob = viewModelScope.launch {
            val goalsResult = repository.timeGoals(week)
            if (goalsResult.isFailure) {
                val error = goalsResult.exceptionOrNull()!!.apiError
                _state.update { it.copy(loading = false, error = error.message, offline = error.isNetwork) }
                return@launch
            }
            var goals = goalsResult.getOrThrow()
            for ((id, anchor) in goalAnchors.toMap()) {
                if (goals.goals.any { it.id == id }) {
                    val selected = repository.timeGoalPeriod(id, anchor, goals.weekStart)
                    if (selected.isFailure) {
                        val error = selected.exceptionOrNull()!!.apiError
                        _state.update { it.copy(loading = false, error = error.message, offline = error.isNetwork) }
                        return@launch
                    }
                    goals = goals.copy(goals = goals.goals.map { if (it.id == id) selected.getOrThrow() else it })
                }
            }
            _state.update { it.copy(loading = false, error = null, goals = goals, offline = false,
                goalError = if (it.offline) null else it.goalError, selectingGoal = null) }
        }
    }

    fun shiftWeek(weeks: Long) {
        val report = _state.value.goals ?: return
        val next = report.weekStart.plusWeeks(weeks)
        val current = report.today.minusDays((report.today.dayOfWeek.value - 1).toLong())
        if (next.isBefore(report.earliestWeekStart) || next.isAfter(current) || _state.value.offline || _state.value.goalSaving) return
        load(next)
    }

    fun thisWeek() = load(null)

    fun selectGoalDay(id: Int, date: LocalDate) {
        val week = _state.value.goals ?: return
        if (_state.value.offline || _state.value.goalSaving) return
        selectionJob?.cancel()
        _state.update { it.copy(selectingGoal = id, goalError = null) }
        selectionJob = viewModelScope.launch {
            repository.timeGoalPeriod(id, date, week.weekStart).fold(
                onSuccess = { selected ->
                    goalAnchors[id] = date
                    _state.update { state -> state.copy(selectingGoal = null, goals = state.goals?.let { current ->
                        if (current.weekStart == week.weekStart) current.copy(goals = current.goals.map {
                            if (it.id == id) selected else it
                        }) else current
                    }) }
                },
                onFailure = { e -> _state.update { it.copy(selectingGoal = null, goalError = e.apiError.message, offline = e.apiError.isNetwork) } },
            )
        }
    }

    fun loadGoalTypes() {
        _state.update { it.copy(goalError = null) }
        viewModelScope.launch {
            repository.listTaskTypes().fold(
                onSuccess = { types -> _state.update { it.copy(taskTypes = types) } },
                onFailure = { e -> _state.update { it.copy(goalError = e.apiError.message) } },
            )
        }
    }

    fun createGoalType(name: String, onCreated: (TaskType) -> Unit) {
        if (_state.value.goalSaving) return
        _state.update { it.copy(goalSaving = true, goalError = null) }
        viewModelScope.launch {
            repository.createTaskType(name).fold(
                onSuccess = { type ->
                    _state.update { it.copy(goalSaving = false, taskTypes = (it.taskTypes + type).distinctBy { t -> t.id }) }
                    onCreated(type)
                },
                onFailure = { e -> _state.update { it.copy(goalSaving = false, goalError = e.apiError.message) } },
            )
        }
    }

    fun saveGoal(id: Int?, replace: Boolean, body: TimeGoalWriteDto, onSaved: () -> Unit) = goalMutation(onSaved) {
        when {
            id == null -> repository.createTimeGoal(body).map { Unit }
            replace -> repository.replaceTimeGoal(id, body).map { Unit }
            else -> repository.changeTimeGoalTarget(id, body.targetMinutes)
        }
    }

    fun archiveGoal(id: Int, onSaved: () -> Unit) = goalMutation({
        _state.update { state -> state.copy(archive = null, goals = state.goals?.let { report ->
            val current = report.today.minusDays((report.today.dayOfWeek.value - 1).toLong())
            report.copy(goals = if (report.weekStart == current) report.goals.filterNot { it.id == id }
                else report.goals.map { if (it.id == id) it.copy(endDate = report.today) else it })
        }) }
        onSaved()
    }) { repository.endTimeGoal(id) }

    fun deleteGoal(id: Int, onSaved: () -> Unit) = goalMutation({
        _state.update { state -> state.copy(
            goals = state.goals?.let { it.copy(goals = it.goals.filterNot { goal -> goal.id == id }) },
            archive = state.archive?.let { it.copy(goals = it.goals.filterNot { goal -> goal.id == id }) },
            historyGoalId = state.historyGoalId?.takeUnless { it == id },
            history = state.history?.takeUnless { it.goal.id == id },
        ) }
        goalAnchors.remove(id)
        onSaved()
    }) { repository.deleteTimeGoal(id) }
    fun clearGoalError() = _state.update { it.copy(goalError = null) }

    private fun goalMutation(onSaved: () -> Unit, action: suspend () -> Result<Unit>) {
        if (_state.value.goalSaving) return
        loadJob?.cancel()
        selectionJob?.cancel()
        archiveJob?.cancel()
        historyJob?.cancel()
        _state.update { it.copy(goalSaving = true, goalError = null, selectingGoal = null, loading = false,
            archiveLoading = false, historyLoading = false) }
        viewModelScope.launch {
            action().fold(
                onSuccess = {
                    _state.update { it.copy(goalSaving = false) }
                    onSaved()
                    refresh()
                },
                onFailure = { e -> _state.update { it.copy(goalSaving = false, goalError = e.apiError.message, offline = e.apiError.isNetwork) } },
            )
        }
    }

}
