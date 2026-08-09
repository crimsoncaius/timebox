package com.timebox.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.DayWindowSettings
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val window: DayWindowSettings? = null,
    val timezone: String? = null,
    val baseUrlInput: String = "",
    val apiKeyInput: String = "",
    val connectionDirty: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class SettingsViewModel(private val repository: TimeboxRepository) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var connectionPrimed = false

    fun load(timezone: String?) {
        _state.update {
            it.copy(loading = it.window == null, error = null, timezone = timezone ?: it.timezone)
        }
        viewModelScope.launch {
            if (!connectionPrimed) {
                val stored = repository.settings.first()
                connectionPrimed = true
                _state.update {
                    it.copy(baseUrlInput = stored.baseUrl, apiKeyInput = stored.apiKey)
                }
            }
            repository.getWindowSettings().fold(
                onSuccess = { window ->
                    _state.update { it.copy(window = window, loading = false, error = null) }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.apiError.message) }
                },
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun onBaseUrlChange(value: String) =
        _state.update { it.copy(baseUrlInput = value, connectionDirty = true) }

    fun onApiKeyChange(value: String) =
        _state.update { it.copy(apiKeyInput = value, connectionDirty = true) }

    fun saveConnection() {
        val current = _state.value
        viewModelScope.launch {
            repository.setConnection(current.baseUrlInput, current.apiKeyInput)
            _state.update { it.copy(connectionDirty = false, message = "Connection saved") }
            load(current.timezone)
        }
    }

    fun adjustStartHour(delta: Int) {
        val window = _state.value.window ?: return
        val next = (window.startHour + delta).coerceIn(0, 23)
        if (next == window.startHour) return
        if (next >= window.endHour) {
            _state.update { it.copy(message = "Start hour must stay before the end hour") }
            return
        }
        patch(startHour = next)
    }

    fun adjustEndHour(delta: Int) {
        val window = _state.value.window ?: return
        val next = (window.endHour + delta).coerceIn(1, 24)
        if (next == window.endHour) return
        if (next <= window.startHour) {
            _state.update { it.copy(message = "End hour must stay after the start hour") }
            return
        }
        patch(endHour = next)
    }

    fun toggleFullDay() {
        val window = _state.value.window ?: return
        patch(showFullDay = !window.showFullDay)
    }

    private fun patch(
        startHour: Int? = null,
        endHour: Int? = null,
        showFullDay: Boolean? = null,
    ) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.patchWindowSettings(startHour, endHour, showFullDay).fold(
                onSuccess = { window ->
                    _state.update { it.copy(window = window, saving = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.apiError.message) }
                },
            )
        }
    }
}
