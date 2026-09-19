package com.timebox.android.ui.chronicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.ArchivedDay
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.timebox.android.data.remote.TrendsDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/** Chronicle's two views: one past day at a time, or Trends across many days. */
enum class ChronicleView { Calendar, Trends }

data class ChronicleUiState(
    val view: ChronicleView = ChronicleView.Calendar,
    val monthStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val archived: Map<LocalDate, ArchivedDay> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val error: String? = null,
    val trends: TrendsDto? = null,
    val trendsLoading: Boolean = false,
    val trendsError: String? = null,
    val period: String = "week",
    val anchor: LocalDate? = null,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val highlightedDays: Map<String, Double> = emptyMap(),
    val highlightedType: String? = null,
)

class ChronicleViewModel(private val repository: TimeboxRepository) : ViewModel() {

    private val _state = MutableStateFlow(ChronicleUiState())
    val state: StateFlow<ChronicleUiState> = _state.asStateFlow()
    private var trendsJob: Job? = null

    fun load() {
        if (_state.value.view == ChronicleView.Trends) loadTrends()
        _state.update { it.copy(loading = it.archived.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.listArchivedDays().fold(
                onSuccess = { days ->
                    _state.update { current ->
                        current.copy(
                            archived = days.associateBy { it.date },
                            loading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.apiError.message) }
                },
            )
        }
    }

    /** Keeps today's marker honest even if the device sat open past midnight. */
    fun setToday(today: LocalDate) = _state.update { it.copy(today = today) }

    fun shiftMonth(months: Long) = _state.update {
        it.copy(monthStart = it.monthStart.plusMonths(months))
    }

    fun selectView(view: ChronicleView) {
        _state.update { it.copy(view = view) }
        if (view == ChronicleView.Trends) loadTrends()
    }

    fun loadTrends() {
        trendsJob?.cancel()
        val selected = _state.value
        _state.update { it.copy(trendsLoading = true, trendsError = null) }
        trendsJob = viewModelScope.launch {
            val result = repository.trends(selected.period, selected.anchor, selected.customStart, selected.customEnd)
            ensureActive()
            result.fold(onSuccess = { report ->
                _state.update { it.copy(trends = report, today = LocalDate.parse(report.today), trendsLoading = false) }
            }, onFailure = { error ->
                _state.update { it.copy(trendsLoading = false, trendsError = error.apiError.message) }
            })
        }
    }

    fun setPeriod(period: String) {
        _state.update { it.copy(period = period, trends = null,
            customStart = it.customStart ?: it.trends?.start?.let(LocalDate::parse) ?: it.today,
            customEnd = it.customEnd ?: it.trends?.end?.let(LocalDate::parse) ?: it.today) }
        loadTrends()
    }

    fun shiftRange(step: Long) {
        _state.update {
            val base = it.trends?.start?.let(LocalDate::parse) ?: it.anchor ?: it.today
            val anchor = when (it.period) { "day" -> base.plusDays(step); "week" -> base.plusWeeks(step); else -> base.plusMonths(step) }
            it.copy(anchor = anchor, trends = null)
        }
        loadTrends()
    }

    fun currentRange() { _state.update { it.copy(anchor = null, trends = null) }; loadTrends() }

    fun customRange(start: LocalDate, end: LocalDate) {
        _state.update { it.copy(customStart = start, customEnd = end, trends = null) }
        loadTrends()
    }

    fun showContributingDays(path: String, days: Map<String, Double>) {
        val latest = days.keys.maxOrNull()?.let(LocalDate::parse) ?: return
        _state.update { it.copy(view = ChronicleView.Calendar, monthStart = latest.withDayOfMonth(1), highlightedDays = days, highlightedType = path) }
    }

    fun clearHighlights() = _state.update { it.copy(highlightedDays = emptyMap(), highlightedType = null) }

    fun goToThisMonth() = _state.update {
        it.copy(monthStart = it.today.withDayOfMonth(1))
    }
}
