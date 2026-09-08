package com.timebox.android.ui.battleplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.ApiErrorCode
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.RecurrencePreview
import com.timebox.android.data.RecurrenceRule
import com.timebox.android.data.RecurringTemplate
import com.timebox.android.data.RecurringPreplanningSchedule
import com.timebox.android.data.RecurringPreplanningSlot
import com.timebox.android.data.RecurringTemplateCreate
import com.timebox.android.data.RecurringTemplatePatch
import com.timebox.android.data.ServerErrorDetail
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.data.remote.PatchField
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class RecurrenceEndMode { Never, EndDate, CycleLimit }

data class RecurringEditorUiState(
    val templateId: Int? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val title: String = "",
    val description: String = "",
    val taskTypeId: Int? = null,
    val urgency: PriorityLevel? = null,
    val importance: PriorityLevel? = null,
    val mode: RecurrenceMode = RecurrenceMode.Scheduled,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.Daily,
    val interval: String = "1",
    val weekdays: Set<Int> = emptySet(),
    val monthDay: String = "1",
    val quotaCount: String = "1",
    val startDate: String = LocalDate.now().toString(),
    val endMode: RecurrenceEndMode = RecurrenceEndMode.Never,
    val endDate: String = "",
    val cycleLimit: String = "",
    val checklistText: String = "",
    val keepUnfinishedOverdue: Boolean = false,
    val preplanningEnabled: Boolean = false,
    val preplanningStart: String = "09:00",
    val preplanningEnd: String = "10:00",
    val preplanningWeekday: Int? = null,
    val taskTypes: List<TaskType> = emptyList(),
    val preview: RecurrencePreview? = null,
    val previewLoading: Boolean = false,
    val previewError: String? = null,
    val pendingBackfill: ServerErrorDetail.BackfillConfirmation? = null,
    val savedTemplateId: Int? = null,
    val dirty: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class RecurringEditorViewModel(private val repository: TimeboxRepository) : ViewModel() {
    private val _state = MutableStateFlow(RecurringEditorUiState())
    val state: StateFlow<RecurringEditorUiState> = _state.asStateFlow()
    private var previewJob: Job? = null

    fun open(templateId: Int?) {
        previewJob?.cancel()
        _state.value = RecurringEditorUiState(templateId = templateId, loading = true)
        viewModelScope.launch {
            val taskTypes = repository.listTaskTypes().getOrElse {
                _state.update { state -> state.copy(loading = false, error = it.apiError.message) }
                return@launch
            }
            if (templateId == null) {
                _state.update { it.copy(loading = false, taskTypes = taskTypes) }
            } else {
                repository.getRecurringTemplate(templateId).fold(
                    onSuccess = { template ->
                        _state.value = template.toEditorState(taskTypes)
                    },
                    onFailure = { cause ->
                        _state.update { it.copy(loading = false, error = cause.apiError.message) }
                    },
                )
            }
            schedulePreview(immediate = true)
        }
    }

    fun setTitle(value: String) = edit { copy(title = value) }
    fun setDescription(value: String) = edit { copy(description = value) }
    fun setTaskType(value: Int?) = edit { copy(taskTypeId = value) }
    fun setUrgency(value: PriorityLevel?) = edit { copy(urgency = value) }
    fun setImportance(value: PriorityLevel?) = edit { copy(importance = value) }
    fun setMode(value: RecurrenceMode) {
        if (_state.value.templateId != null) return
        edit {
            copy(
                mode = value,
                interval = if (value == RecurrenceMode.Quota) "1" else interval,
                weekdays = if (value == RecurrenceMode.Quota) emptySet() else weekdays,
                monthDay = if (value == RecurrenceMode.Quota) "1" else monthDay,
                keepUnfinishedOverdue = if (value == RecurrenceMode.Quota) false else keepUnfinishedOverdue,
            )
        }
    }
    fun setFrequency(value: RecurrenceFrequency) = edit {
        copy(
            frequency = value,
            weekdays = if (value == RecurrenceFrequency.Weekly && mode == RecurrenceMode.Scheduled) weekdays else emptySet(),
        )
    }
    fun setInterval(value: String) = edit { copy(interval = value.filter(Char::isDigit)) }
    fun toggleWeekday(value: Int) = edit {
        copy(weekdays = if (value in weekdays) weekdays - value else weekdays + value)
    }
    fun setMonthDay(value: String) = edit { copy(monthDay = value.filter(Char::isDigit)) }
    fun setQuotaCount(value: String) = edit { copy(quotaCount = value.filter(Char::isDigit)) }
    fun setStartDate(value: String) = edit { copy(startDate = value) }
    fun setEndMode(value: RecurrenceEndMode) = edit { copy(endMode = value) }
    fun setEndDate(value: String) = edit { copy(endDate = value) }
    fun setCycleLimit(value: String) = edit { copy(cycleLimit = value.filter(Char::isDigit)) }
    fun setChecklistText(value: String) = edit { copy(checklistText = value) }
    fun setKeepUnfinishedOverdue(value: Boolean) = edit { copy(keepUnfinishedOverdue = value) }
    fun setPreplanningEnabled(value: Boolean) = edit { copy(preplanningEnabled = value) }
    fun setPreplanningStart(value: String) = edit { copy(preplanningStart = value) }
    fun setPreplanningEnd(value: String) = edit { copy(preplanningEnd = value) }
    fun setPreplanningWeekday(value: Int) = edit { copy(preplanningWeekday = value) }

    fun refreshPreview() = schedulePreview(immediate = true)

    fun save(confirmBackfill: Boolean = false) {
        val current = _state.value
        val validation = validateRecurrenceDraft(current, requireTitle = true)
        if (validation != null) {
            _state.update { it.copy(message = validation) }
            return
        }
        val rule = current.toRule() ?: return
        _state.update { it.copy(saving = true, pendingBackfill = null, message = null) }
        viewModelScope.launch {
            val result = if (current.templateId == null) {
                repository.createRecurringTemplate(
                    RecurringTemplateCreate(
                        title = current.title.trim(),
                        description = current.description.trim(),
                        taskTypeId = current.taskTypeId,
                        urgency = current.urgency,
                        importance = current.importance,
                        rule = rule,
                        checklistTitles = current.checklistTitles(),
                        confirmBackfill = confirmBackfill,
                        keepUnfinishedOverdue = current.mode == RecurrenceMode.Scheduled && current.keepUnfinishedOverdue,
                        preplanningSchedule = current.toPreplanningSchedule(),
                    )
                )
            } else {
                repository.patchRecurringTemplate(
                    current.templateId,
                    RecurringTemplatePatch(
                        title = PatchField.of(current.title.trim()),
                        description = PatchField.of(current.description.trim()),
                        taskTypeId = current.taskTypeId.asPatch(),
                        urgency = current.urgency.asPatch(),
                        importance = current.importance.asPatch(),
                        frequency = PatchField.of(rule.frequency),
                        interval = PatchField.of(rule.interval),
                        weekdays = PatchField.of(rule.weekdays),
                        monthDay = rule.monthDay.asPatch(),
                        quotaCount = rule.quotaCount.asPatch(),
                        startDate = PatchField.of(rule.startDate),
                        endDate = rule.endDate.asPatch(),
                        cycleLimit = rule.cycleLimit.asPatch(),
                        checklistTitles = PatchField.of(current.checklistTitles()),
                        confirmBackfill = PatchField.of(confirmBackfill),
                        keepUnfinishedOverdue = PatchField.of(
                            current.mode == RecurrenceMode.Scheduled && current.keepUnfinishedOverdue
                        ),
                    ),
                )
            }
            result.fold(
                onSuccess = { template ->
                    _state.update {
                        it.copy(saving = false, dirty = false, savedTemplateId = template.id, message = "Recurring template saved")
                    }
                },
                onFailure = { cause ->
                    val apiError = cause.apiError
                    val backfill = apiError.detail as? ServerErrorDetail.BackfillConfirmation
                    _state.update {
                        it.copy(
                            saving = false,
                            pendingBackfill = backfill.takeIf { apiError.code == ApiErrorCode.BackfillConfirmationRequired },
                            message = if (backfill == null) apiError.message else null,
                        )
                    }
                },
            )
        }
    }

    fun dismissBackfill() = _state.update { it.copy(pendingBackfill = null) }
    fun consumeSaved() = _state.update { it.copy(savedTemplateId = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun edit(block: RecurringEditorUiState.() -> RecurringEditorUiState) {
        _state.update { it.block().copy(dirty = true, preview = null, previewError = null, savedTemplateId = null) }
        schedulePreview()
    }

    private fun schedulePreview(immediate: Boolean = false) {
        previewJob?.cancel()
        val snapshot = _state.value
        if (validateRecurrenceDraft(snapshot, requireTitle = false) != null) {
            _state.update { it.copy(previewLoading = false, preview = null) }
            return
        }
        val rule = snapshot.toRule() ?: return
        previewJob = viewModelScope.launch {
            if (!immediate) delay(350)
            _state.update { it.copy(previewLoading = true, previewError = null) }
            repository.previewRecurrence(rule).fold(
                onSuccess = { preview -> _state.update { it.copy(previewLoading = false, preview = preview) } },
                onFailure = { cause ->
                    _state.update { it.copy(previewLoading = false, previewError = cause.apiError.message) }
                },
            )
        }
    }
}

internal fun validateRecurrenceDraft(state: RecurringEditorUiState, requireTitle: Boolean): String? {
    if (requireTitle && state.title.isBlank()) return "Title is required."
    val start = runCatching { LocalDate.parse(state.startDate.trim()) }.getOrNull()
        ?: return "Use YYYY-MM-DD for the start date."
    if (state.mode == RecurrenceMode.Scheduled) {
        val interval = state.interval.toIntOrNull()
        if (interval == null || interval !in 1..365) return "Interval must be between 1 and 365."
        if (state.frequency == RecurrenceFrequency.Weekly && state.weekdays.isEmpty()) {
            return "Choose at least one weekday for a weekly schedule."
        }
        if (state.frequency == RecurrenceFrequency.Monthly && state.monthDay.toIntOrNull() !in 1..31) {
            return "Month day must be between 1 and 31."
        }
        if (state.preplanningEnabled) {
            val startMinute = parseTimeMinute(state.preplanningStart)
                ?: return "Use HH:MM for the Planned Block start."
            val endMinute = parseTimeMinute(state.preplanningEnd, endOfDay = true)
                ?: return "Use HH:MM for the Planned Block end."
            if (endMinute - startMinute < 30) {
                return "A Planned Block must end at least 30 minutes after it starts."
            }
            if (state.frequency == RecurrenceFrequency.Weekly &&
                (state.preplanningWeekday ?: state.weekdays.minOrNull()) !in state.weekdays
            ) {
                return "Choose a selected recurrence weekday for the pre-planning slot."
            }
        }
    } else if (state.quotaCount.toIntOrNull() !in 1..100) {
        return "Quota count must be between 1 and 100."
    }
    when (state.endMode) {
        RecurrenceEndMode.Never -> Unit
        RecurrenceEndMode.EndDate -> {
            val end = runCatching { LocalDate.parse(state.endDate.trim()) }.getOrNull()
                ?: return "Use YYYY-MM-DD for the end date."
            if (end < start) return "End date cannot be before the start date."
        }
        RecurrenceEndMode.CycleLimit -> if (state.cycleLimit.toIntOrNull() !in 1..10_000) {
            return "Cycle limit must be between 1 and 10000."
        }
    }
    return null
}

private fun parseTimeMinute(value: String, endOfDay: Boolean = false): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    if (endOfDay && hour == 0 && minute == 0) return 24 * 60
    return hour * 60 + minute
}

internal fun RecurringEditorUiState.toPreplanningSchedule(): RecurringPreplanningSchedule? {
    if (!preplanningEnabled || mode != RecurrenceMode.Scheduled) return null
    val startMinute = parseTimeMinute(preplanningStart) ?: return null
    val endMinute = parseTimeMinute(preplanningEnd, endOfDay = true) ?: return null
    return RecurringPreplanningSchedule(listOf(RecurringPreplanningSlot(
        weekday = if (frequency == RecurrenceFrequency.Weekly) {
            preplanningWeekday ?: weekdays.minOrNull()
        } else null,
        startMinute = startMinute,
        endMinute = endMinute,
    )))
}

internal fun RecurringEditorUiState.toRule(): RecurrenceRule? {
    if (validateRecurrenceDraft(this, requireTitle = false) != null) return null
    return RecurrenceRule(
        mode = mode,
        frequency = frequency,
        interval = if (mode == RecurrenceMode.Quota) 1 else checkNotNull(interval.toIntOrNull()),
        weekdays = if (mode == RecurrenceMode.Scheduled && frequency == RecurrenceFrequency.Weekly) weekdays.sorted() else emptyList(),
        monthDay = if (mode == RecurrenceMode.Scheduled && frequency == RecurrenceFrequency.Monthly) monthDay.toInt() else null,
        quotaCount = if (mode == RecurrenceMode.Quota) quotaCount.toInt() else null,
        startDate = LocalDate.parse(startDate.trim()),
        endDate = if (endMode == RecurrenceEndMode.EndDate) LocalDate.parse(endDate.trim()) else null,
        cycleLimit = if (endMode == RecurrenceEndMode.CycleLimit) cycleLimit.toInt() else null,
    )
}

private fun RecurringEditorUiState.checklistTitles(): List<String> = checklistText.lineSequence()
    .map(String::trim).filter(String::isNotEmpty).toList()

private fun RecurringTemplate.toEditorState(taskTypes: List<TaskType>) = RecurringEditorUiState(
    templateId = id,
    title = title,
    description = description,
    taskTypeId = taskTypeId,
    urgency = urgency,
    importance = importance,
    mode = mode,
    frequency = frequency,
    interval = interval.toString(),
    weekdays = weekdays.toSet(),
    monthDay = (monthDay ?: 1).toString(),
    quotaCount = (quotaCount ?: 1).toString(),
    startDate = startDate.toString(),
    endMode = when {
        endDate != null -> RecurrenceEndMode.EndDate
        cycleLimit != null -> RecurrenceEndMode.CycleLimit
        else -> RecurrenceEndMode.Never
    },
    endDate = endDate?.toString().orEmpty(),
    cycleLimit = cycleLimit?.toString().orEmpty(),
    checklistText = checklistItems.sortedBy { it.position }.joinToString("\n") { it.title },
    keepUnfinishedOverdue = keepUnfinishedOverdue,
    taskTypes = taskTypes,
)

private fun <T : Any> T?.asPatch(): PatchField<T> = this?.let { PatchField.of(it) } ?: PatchField.Null
