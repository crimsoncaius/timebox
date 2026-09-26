package com.timebox.android.ui.chronicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.HabitsWeek
import com.timebox.android.data.RecurrenceStatus
import com.timebox.android.data.RecurringTemplate
import com.timebox.android.data.RecurringTemplatePatch
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.data.remote.PatchField
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A day of one Habit whose tick or untick is still in flight. */
data class HabitCellKey(val templateId: Int, val date: LocalDate)

data class HabitsUiState(
    val week: HabitsWeek? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val actionError: String? = null,
    val pending: Set<HabitCellKey> = emptySet(),
    val candidates: List<RecurringTemplate>? = null,
    val candidatesError: String? = null,
)

/** Chronicle's Habits view: one Calendar Week of every Habit's Habit Periods. */
class HabitsViewModel(private val repository: TimeboxRepository) : ViewModel() {

    private val _state = MutableStateFlow(HabitsUiState())
    val state: StateFlow<HabitsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    /** Reloads the shown week, or the current week on first open. */
    fun refresh() = load(_state.value.week?.weekStart)

    fun load(week: LocalDate?) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            repository.habitsWeek(week).fold(
                onSuccess = { result -> _state.update { it.copy(week = result, loading = false, error = null) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.apiError.message) } },
            )
        }
    }

    fun shiftWeek(weeks: Long) {
        val week = _state.value.week ?: return
        val next = week.weekStart.plusWeeks(weeks)
        if (next.isBefore(week.earliestWeekStart) || next.isAfter(currentWeekStart(week))) return
        load(next)
    }

    fun thisWeek() = load(null)

    fun tick(templateId: Int, date: LocalDate) = record(HabitCellKey(templateId, date)) {
        repository.tickHabit(templateId, date)
    }

    fun untick(templateId: Int, date: LocalDate) = record(HabitCellKey(templateId, date)) {
        repository.untickHabit(templateId, date)
    }

    private fun record(key: HabitCellKey, action: suspend () -> Result<HabitsWeek>) {
        if (key in _state.value.pending) return
        _state.update { it.copy(pending = it.pending + key, actionError = null) }
        viewModelScope.launch {
            action().fold(
                onSuccess = { result ->
                    _state.update { current ->
                        // Keep the week the user is looking at if they moved on meanwhile.
                        val shown = current.week?.weekStart
                        current.copy(
                            week = if (shown == null || shown == result.weekStart) result else current.week,
                            pending = current.pending - key,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(pending = it.pending - key, actionError = e.apiError.message) }
                },
            )
        }
    }

    fun dismissActionError() = _state.update { it.copy(actionError = null) }

    fun loadCandidates() {
        _state.update { it.copy(candidates = null, candidatesError = null) }
        viewModelScope.launch {
            repository.listRecurringTemplates(RecurrenceStatus.Active).fold(
                onSuccess = { templates ->
                    _state.update { state -> state.copy(candidates = templates.filterNot { it.trackAsHabit }) }
                },
                onFailure = { e -> _state.update { it.copy(candidatesError = e.apiError.message) } },
            )
        }
    }

    fun addHabit(templateId: Int) {
        viewModelScope.launch {
            repository.patchRecurringTemplate(templateId, RecurringTemplatePatch(trackAsHabit = PatchField.of(true))).fold(
                onSuccess = { refresh() },
                onFailure = { e -> _state.update { it.copy(actionError = e.apiError.message) } },
            )
        }
    }
}

internal fun currentWeekStart(week: HabitsWeek): LocalDate =
    week.today.minusDays((week.today.dayOfWeek.value - 1).toLong())
