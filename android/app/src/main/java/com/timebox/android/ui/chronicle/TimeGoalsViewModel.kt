package com.timebox.android.ui.chronicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.TimeGoalsWeek
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
)

/** Owns Time Goals browsing independently of completion-based Habits. */
class TimeGoalsViewModel(private val repository: TimeboxRepository) : ViewModel() {
    private val _state = MutableStateFlow(TimeGoalsUiState())
    val state: StateFlow<TimeGoalsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var selectionJob: Job? = null
    private val goalAnchors = mutableMapOf<Int, LocalDate>()

    fun refresh() { if (!_state.value.goalSaving) load(_state.value.goals?.weekStart) }

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

    fun endGoal(id: Int, onSaved: () -> Unit) = goalMutation(onSaved) { repository.endTimeGoal(id) }
    fun deleteGoal(id: Int, onSaved: () -> Unit) = goalMutation(onSaved) { repository.deleteTimeGoal(id) }
    fun clearGoalError() = _state.update { it.copy(goalError = null) }

    private fun goalMutation(onSaved: () -> Unit, action: suspend () -> Result<Unit>) {
        if (_state.value.goalSaving) return
        loadJob?.cancel()
        selectionJob?.cancel()
        _state.update { it.copy(goalSaving = true, goalError = null, selectingGoal = null, loading = false) }
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
