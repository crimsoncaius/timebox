package com.timebox.android.ui.battleplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.ApiErrorCode
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.Project
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.RecurrencePreview
import com.timebox.android.data.RecurrenceRule
import com.timebox.android.data.RecurringTemplate
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
    val projectId: Int? = null,
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
    val projects: List<Project> = emptyList(),
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
            val projects = repository.listProjects().getOrElse {
                _state.update { state -> state.copy(loading = false, error = it.apiError.message) }
                return@launch
            }
            val taskTypes = repository.listTaskTypes().getOrElse {
                _state.update { state -> state.copy(loading = false, error = it.apiError.message) }
                return@launch
            }
            if (templateId == null) {
                _state.update { it.copy(loading = false, projects = projects, taskTypes = taskTypes) }
            } else {
                repository.getRecurringTemplate(templateId).fold(
                    onSuccess = { template ->
                        _state.value = template.toEditorState(projects, taskTypes)
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
    fun setProject(value: Int?) = edit { copy(projectId = value) }
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
                        projectId = current.projectId,
                        taskTypeId = current.taskTypeId,
                        urgency = current.urgency,
                        importance = current.importance,
                        rule = rule,
                        checklistTitles = current.checklistTitles(),
                        confirmBackfill = confirmBackfill,
                        keepUnfinishedOverdue = current.mode == RecurrenceMode.Scheduled && current.keepUnfinishedOverdue,
                    )
                )
            } else {
                repository.patchRecurringTemplate(
                    current.templateId,
                    RecurringTemplatePatch(
                        title = PatchField.of(current.title.trim()),
                        description = PatchField.of(current.description.trim()),
                        projectId = current.projectId.asPatch(),
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

private fun RecurringTemplate.toEditorState(projects: List<Project>, taskTypes: List<TaskType>) = RecurringEditorUiState(
    templateId = id,
    title = title,
    description = description,
    projectId = projectId,
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
    projects = projects,
    taskTypes = taskTypes,
)

private fun <T : Any> T?.asPatch(): PatchField<T> = this?.let { PatchField.of(it) } ?: PatchField.Null
