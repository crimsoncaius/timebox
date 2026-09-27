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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** A tick or untick shown on the grid before the server has recorded it. */
private class HabitEdit(val key: HabitCellKey, val tick: Boolean)

/**
 * Chronicle's Habits view: one Calendar Week of every Habit's Habit Periods.
 *
 * Ticks show at once: the shown week is the last week the server returned with every
 * unconfirmed edit applied on top. Edits are sent one at a time in tap order; each answer
 * becomes the new base, and a failed edit simply drops out, restoring the cell.
 */
class HabitsViewModel(private val repository: TimeboxRepository) : ViewModel() {

    private val _state = MutableStateFlow(HabitsUiState())
    val state: StateFlow<HabitsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    private var confirmed: HabitsWeek? = null
    private val edits = mutableListOf<HabitEdit>()
    private val sending = Mutex()
    private var confirmations = 0

    /** Reloads the shown week, or the current week on first open. */
    fun refresh() = load(_state.value.week?.weekStart)

    fun load(week: LocalDate?) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        val confirmationsBefore = confirmations
        loadJob = viewModelScope.launch {
            repository.habitsWeek(week).fold(
                onSuccess = { result ->
                    // An edit confirmed while this read was in flight carries the fresher week.
                    if (confirmations == confirmationsBefore || result.weekStart != confirmed?.weekStart) confirmed = result
                    publish { it.copy(loading = false, error = null) }
                },
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

    fun tick(templateId: Int, date: LocalDate) = record(HabitEdit(HabitCellKey(templateId, date), tick = true)) {
        repository.tickHabit(templateId, date)
    }

    fun untick(templateId: Int, date: LocalDate) = record(HabitEdit(HabitCellKey(templateId, date), tick = false)) {
        repository.untickHabit(templateId, date)
    }

    private fun record(edit: HabitEdit, action: suspend () -> Result<HabitsWeek>) {
        edits += edit
        publish { it.copy(actionError = null) }
        viewModelScope.launch {
            val result = sending.withLock { action() }
            edits.remove(edit)
            result.fold(
                onSuccess = { week ->
                    confirmations++
                    // Keep the week the user is looking at if they moved on meanwhile.
                    if (confirmed == null || confirmed?.weekStart == week.weekStart) confirmed = week
                    publish()
                },
                onFailure = { e -> publish { it.copy(actionError = e.apiError.message) } },
            )
        }
    }

    private fun publish(change: (HabitsUiState) -> HabitsUiState = { it }) {
        val shown = confirmed?.let { base -> edits.fold(base) { week, edit -> week.withHabitEdit(edit.key, edit.tick) } }
        _state.update { current ->
            change(current).copy(week = shown ?: current.week, pending = edits.mapTo(mutableSetOf()) { it.key })
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
