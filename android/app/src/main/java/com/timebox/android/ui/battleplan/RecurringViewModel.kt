package com.timebox.android.ui.battleplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.RecurringTemplate
import com.timebox.android.data.RecurrenceStatus
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecurringUiState(
    val selectedStatus: RecurrenceStatus = RecurrenceStatus.Active,
    val templates: List<RecurringTemplate> = emptyList(),
    val loading: Boolean = false,
    val detailLoading: Boolean = false,
    val actionInProgress: Boolean = false,
    val selectedTemplate: RecurringTemplate? = null,
    val pendingDelete: RecurringTemplate? = null,
    val error: String? = null,
    val message: String? = null,
)

class RecurringViewModel(private val repository: TimeboxRepository) : ViewModel() {
    private val _state = MutableStateFlow(RecurringUiState())
    val state: StateFlow<RecurringUiState> = _state.asStateFlow()

    fun load(status: RecurrenceStatus = _state.value.selectedStatus, showSpinner: Boolean = true) {
        _state.update { it.copy(selectedStatus = status, loading = showSpinner, error = null) }
        viewModelScope.launch {
            repository.listRecurringTemplates(status).fold(
                onSuccess = { rows -> _state.update { it.copy(templates = rows, loading = false) } },
                onFailure = { cause ->
                    _state.update { it.copy(loading = false, error = cause.apiError.message) }
                },
            )
        }
    }

    fun selectStatus(status: RecurrenceStatus) {
        if (status != _state.value.selectedStatus) load(status)
    }

    fun openDetail(templateId: Int) {
        _state.update { it.copy(detailLoading = true, selectedTemplate = null, error = null) }
        viewModelScope.launch {
            repository.getRecurringTemplate(templateId).fold(
                onSuccess = { template ->
                    _state.update { it.copy(detailLoading = false, selectedTemplate = template) }
                },
                onFailure = { cause ->
                    _state.update { it.copy(detailLoading = false, error = cause.apiError.message) }
                },
            )
        }
    }

    fun pause() = lifecycle("paused") { repository.pauseRecurringTemplate(it) }
    fun resume() = lifecycle("resumed") { repository.resumeRecurringTemplate(it) }
    fun end() = lifecycle("ended") { repository.endRecurringTemplate(it) }

    fun requestDelete() {
        _state.value.selectedTemplate?.let { template ->
            _state.update { it.copy(pendingDelete = template) }
        }
    }

    fun dismissDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete(onDeleted: () -> Unit = {}) {
        val template = _state.value.pendingDelete ?: return
        _state.update { it.copy(actionInProgress = true, pendingDelete = null) }
        viewModelScope.launch {
            repository.deleteRecurringTemplate(template.id).fold(
                onSuccess = {
                    _state.update {
                        it.copy(actionInProgress = false, selectedTemplate = null, message = "Recurring template deleted")
                    }
                    load(_state.value.selectedStatus, showSpinner = false)
                    onDeleted()
                },
                onFailure = { cause ->
                    _state.update { it.copy(actionInProgress = false, message = cause.apiError.message) }
                },
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun lifecycle(
        pastTense: String,
        action: suspend (Int) -> Result<RecurringTemplate>,
    ) {
        val template = _state.value.selectedTemplate ?: return
        _state.update { it.copy(actionInProgress = true) }
        viewModelScope.launch {
            action(template.id).fold(
                onSuccess = { updated ->
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            selectedTemplate = updated,
                            message = "Recurring template $pastTense",
                        )
                    }
                    load(_state.value.selectedStatus, showSpinner = false)
                },
                onFailure = { cause ->
                    _state.update { it.copy(actionInProgress = false, message = cause.apiError.message) }
                },
            )
        }
    }
}
