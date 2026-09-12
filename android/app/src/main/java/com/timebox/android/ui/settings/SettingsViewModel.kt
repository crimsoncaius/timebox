package com.timebox.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.DayWindowSettings
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.reminders.DailyReminderSettings
import com.timebox.android.reminders.DailyReminder
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val window: DayWindowSettings? = null,
    val timezone: String? = null,
    val reportingZoneInput: String = "",
    val baseUrlInput: String = "",
    val apiKeyInput: String = "",
    val connectionDirty: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val dailyReminders: DailyReminderSettings = DailyReminderSettings(),
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
                val reminders = repository.dailyReminders.first()
                connectionPrimed = true
                _state.update {
                    it.copy(baseUrlInput = stored.baseUrl, apiKeyInput = stored.apiKey, dailyReminders = reminders)
                }
            }
            if (com.timebox.android.BuildConfig.ACTIVITY_TRACKING_DEV) runCatching { repository.getActivity() }.fold(
                onSuccess = { snapshot -> _state.update { it.copy(timezone = snapshot.reportingTimezone, reportingZoneInput = snapshot.reportingTimezone) } },
                onFailure = { error -> _state.update { it.copy(message = error.apiError.message) } },
            )
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

    fun changeReportingZone(value: String) = _state.update { it.copy(reportingZoneInput = value) }

    fun saveReportingZone(onSaved: () -> Unit) {
        val zone = _state.value.reportingZoneInput.trim()
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repository.setReportingTimezone(zone) }.fold(
                onSuccess = { snapshot ->
                    _state.update { it.copy(saving = false, timezone = snapshot.reportingTimezone, reportingZoneInput = snapshot.reportingTimezone, message = "Reporting Time Zone saved") }
                    onSaved()
                },
                onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
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

    fun updateDailyReminder(planning: Boolean, enabled: Boolean? = null, time: LocalTime? = null) {
        val current = _state.value.dailyReminders
        val existing = if (planning) current.planning else current.review
        val nextReminder = existing.copy(enabled = enabled ?: existing.enabled, time = time ?: existing.time)
        val next = if (planning) current.copy(planning = nextReminder) else current.copy(review = nextReminder)
        _state.update { it.copy(dailyReminders = next) }
        viewModelScope.launch { repository.setDailyReminders(next) }
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
