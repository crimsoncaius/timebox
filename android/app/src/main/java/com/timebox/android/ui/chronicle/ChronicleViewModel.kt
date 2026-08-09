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

data class ChronicleUiState(
    val monthStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val archived: Map<LocalDate, ArchivedDay> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val error: String? = null,
)

class ChronicleViewModel(private val repository: TimeboxRepository) : ViewModel() {

    private val _state = MutableStateFlow(ChronicleUiState())
    val state: StateFlow<ChronicleUiState> = _state.asStateFlow()

    fun load() {
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

    fun goToThisMonth() = _state.update {
        it.copy(monthStart = it.today.withDayOfMonth(1))
    }
}
