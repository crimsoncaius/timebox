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
import com.timebox.android.ui.formatMinuteLabel24
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class RecurrenceEndMode { Never, EndDate, CycleLimit }

data class RecurringPreplanningSlotDraft(
    val key: String? = null,
    val weekday: Int? = null,
    val start: String = "09:00",
    val end: String = "10:00",
)

data class RecurringEditorUiState(
    val templateId: Int? = null,
    val template: RecurringTemplate? = null,
    val saveError: String? = null,
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
    val preplanningSlots: List<RecurringPreplanningSlotDraft> = emptyList(),
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
    suspend fun calendar(templateId: Int, month: java.time.YearMonth?) = repository.getRoutineCalendar(templateId, month)

    private val _state = MutableStateFlow(RecurringEditorUiState())
    val state: StateFlow<RecurringEditorUiState> = _state.asStateFlow()
    private var previewJob: Job? = null
    private var baseline: RecurringEditorUiState? = null

    fun applyDraft(draft: RecurringEditorUiState) {
        if (_state.value.saving) return
        edit { draft.copy(templateId = templateId, template = template, taskTypes = taskTypes, mode = if (templateId == null) draft.mode else mode, saveError = null) }
    }

    fun acceptTemplate(template: RecurringTemplate) {
        val current = _state.value
        if (current.templateId != template.id || current.saving || current.dirty || current.loading) return
        val updated = template.toEditorState(current.taskTypes)
        baseline = updated
        _state.value = updated
    }

    fun discardDraft() {
        previewJob?.cancel()
        baseline?.let { _state.value = it.copy(savedTemplateId = null, message = null) }
    }

    fun open(templateId: Int?) {
        previewJob?.cancel()
        baseline = null
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
            baseline = _state.value
            schedulePreview(immediate = true)
        }
    }

    fun setTitle(value: String) = edit { copy(title = value) }
    fun setDescription(value: String) = edit { copy(description = value) }
    fun setTaskType(value: Int?) = edit { copy(taskTypeId = value) }
    fun createTaskTypeAndChoose(path: String) {
        if (_state.value.saving) return
        viewModelScope.launch {
            repository.createTaskType(path).fold(
                onSuccess = { created ->
                    val types = repository.listTaskTypes().getOrElse { _state.value.taskTypes + created }
                    edit { copy(taskTypes = types, taskTypeId = created.id) }
                },
                onFailure = { cause ->
                    _state.update { it.copy(error = cause.apiError.message) }
                },
            )
        }
    }
    fun createRoutineTaskType(path: String, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            repository.createTaskType(path).fold(
                onSuccess = { created ->
                    val types = repository.listTaskTypes().getOrElse { _state.value.taskTypes + created }
                    _state.update { it.copy(taskTypes = types) }
                    baseline = baseline?.copy(taskTypes = types)
                    onCreated(created.id)
                },
                onFailure = { cause -> _state.update { it.copy(saveError = cause.apiError.message) } },
            )
        }
    }

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
    fun setPreplanningEnabled(value: Boolean) = edit {
        copy(
            preplanningSlots = if (value) {
                preplanningSlots.ifEmpty { listOf(RecurringPreplanningSlotDraft()) }
            } else emptyList(),
        )
    }
    fun addPreplanningSlot() = edit {
        copy(
            preplanningSlots = preplanningSlots + RecurringPreplanningSlotDraft(
                weekday = if (frequency == RecurrenceFrequency.Weekly) weekdays.minOrNull() else null,
            ),
        )
    }
    fun removePreplanningSlot(index: Int) = edit {
        copy(preplanningSlots = preplanningSlots.filterIndexed { slotIndex, _ -> slotIndex != index })
    }
    fun setPreplanningStart(index: Int, value: String) = updatePreplanningSlot(index) { copy(start = value) }
    fun setPreplanningEnd(index: Int, value: String) = updatePreplanningSlot(index) { copy(end = value) }
    fun setPreplanningWeekday(index: Int, value: Int) = updatePreplanningSlot(index) { copy(weekday = value) }

    fun refreshPreview() = schedulePreview(immediate = true)

    fun save(confirmBackfill: Boolean = false) {
        val current = _state.value
        if (current.saving) return
        val validation = validateRecurrenceDraft(current, requireTitle = true)
        if (validation != null) {
            _state.update { it.copy(message = validation, saveError = validation) }
            return
        }
        val rule = current.toRule() ?: return
        _state.update { it.copy(saving = true, pendingBackfill = null, message = null, saveError = null) }
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
                    recurringDraftPatch(baseline ?: current, current, confirmBackfill),
                )
            }
            result.fold(
                onSuccess = { template ->
                    val saved = template.toEditorState(_state.value.taskTypes)
                    baseline = saved
                    _state.value = saved.copy(savedTemplateId = template.id)
                },
                onFailure = { cause ->
                    val apiError = cause.apiError
                    val backfill = apiError.detail as? ServerErrorDetail.BackfillConfirmation
                    _state.update {
                        it.copy(
                            saving = false,
                            pendingBackfill = backfill.takeIf { apiError.code == ApiErrorCode.BackfillConfirmationRequired },
                            message = if (backfill == null) apiError.message else null,
                            saveError = if (backfill == null) apiError.message else null,
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
        if (_state.value.saving) return
        _state.update { it.block().copy(dirty = true, preview = null, previewError = null, savedTemplateId = null) }
        schedulePreview()
    }

    private fun updatePreplanningSlot(
        index: Int,
        block: RecurringPreplanningSlotDraft.() -> RecurringPreplanningSlotDraft,
    ) = edit {
        copy(preplanningSlots = preplanningSlots.mapIndexed { slotIndex, slot ->
            if (slotIndex == index) slot.block() else slot
        })
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
        val schedule = state.preplanningSlots
        schedule.forEach { slot ->
            val startMinute = parseTimeMinute(slot.start)
                ?: return "Use HH:MM for the Planned Block start."
            val endMinute = parseTimeMinute(slot.end, endOfDay = true)
                ?: return "Use HH:MM for the Planned Block end."
            if (endMinute - startMinute < 30) {
                return "A Planned Block must end at least 30 minutes after it starts."
            }
            if (state.frequency == RecurrenceFrequency.Weekly &&
                (slot.weekday ?: state.weekdays.minOrNull()) !in state.weekdays
            ) {
                return "Choose a selected recurrence weekday for the pre-planning slot."
            }
        }
        schedule.forEachIndexed { index, slot ->
            val slotStart = parseTimeMinute(slot.start) ?: return@forEachIndexed
            val slotEnd = parseTimeMinute(slot.end, endOfDay = true) ?: return@forEachIndexed
            schedule.drop(index + 1).forEach { other ->
                val otherStart = parseTimeMinute(other.start) ?: return@forEach
                val otherEnd = parseTimeMinute(other.end, endOfDay = true) ?: return@forEach
                val sameDate = state.frequency != RecurrenceFrequency.Weekly ||
                    (slot.weekday ?: state.weekdays.minOrNull()) ==
                    (other.weekday ?: state.weekdays.minOrNull())
                if (sameDate && slotStart < otherEnd && otherStart < slotEnd) {
                    return "Recurring Pre-planning Schedule slots cannot overlap."
                }
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
    if (mode != RecurrenceMode.Scheduled) return null
    if (preplanningSlots.isEmpty()) return null
    return RecurringPreplanningSchedule(preplanningSlots.mapIndexed { position, slot ->
        RecurringPreplanningSlot(
            key = slot.key,
            position = position,
            weekday = if (frequency == RecurrenceFrequency.Weekly) {
                slot.weekday ?: weekdays.minOrNull()
            } else null,
            startMinute = parseTimeMinute(slot.start) ?: return null,
            endMinute = parseTimeMinute(slot.end, endOfDay = true) ?: return null,
        )
    })
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

internal fun RecurringTemplate.toEditorState(taskTypes: List<TaskType>) = RecurringEditorUiState(
    templateId = id,
    template = this,
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
    preplanningSlots = preplanningSchedule?.slots?.sortedBy { it.position }?.map { slot ->
        RecurringPreplanningSlotDraft(
            key = slot.key,
            weekday = slot.weekday,
            start = formatMinuteLabel24(slot.startMinute),
            end = formatMinuteLabel24(slot.endMinute),
        )
    }.orEmpty(),
    taskTypes = taskTypes,
)

private fun <T : Any> T?.asPatch(): PatchField<T> = this?.let { PatchField.of(it) } ?: PatchField.Null


internal fun recurringDraftPatch(before: RecurringEditorUiState, after: RecurringEditorUiState, confirmBackfill: Boolean): RecurringTemplatePatch {
    fun <T : Any> changed(old: T?, value: T?): PatchField<T> = if (old == value) PatchField.Absent else value.asPatch()
    val oldRule = checkNotNull(before.toRule())
    val rule = checkNotNull(after.toRule())
    return RecurringTemplatePatch(
        title = changed(before.title.trim(), after.title.trim()),
        description = changed(before.description.trim(), after.description.trim()),
        taskTypeId = changed(before.taskTypeId, after.taskTypeId),
        importance = changed(before.importance, after.importance), urgency = changed(before.urgency, after.urgency),
        frequency = changed(oldRule.frequency, rule.frequency), interval = changed(oldRule.interval, rule.interval),
        weekdays = changed(oldRule.weekdays, rule.weekdays), monthDay = changed(oldRule.monthDay, rule.monthDay),
        quotaCount = changed(oldRule.quotaCount, rule.quotaCount), startDate = changed(oldRule.startDate, rule.startDate),
        endDate = changed(oldRule.endDate, rule.endDate), cycleLimit = changed(oldRule.cycleLimit, rule.cycleLimit),
        checklistTitles = changed(before.checklistTitles(), after.checklistTitles()),
        keepUnfinishedOverdue = changed(before.keepUnfinishedOverdue, after.keepUnfinishedOverdue),
        preplanningSchedule = changed(before.toPreplanningSchedule(), after.toPreplanningSchedule()),
        confirmBackfill = PatchField.of(confirmBackfill),
    )
}
