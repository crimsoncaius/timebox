package com.timebox.android.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.ActualBlock
import com.timebox.android.data.Day
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Lane
import com.timebox.android.data.SLOT_MINUTES
import com.timebox.android.data.TaskType
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.Subtask
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.ui.planning.PlanningCommitOutcome
import com.timebox.android.ui.planning.PlanningDraftPlacement
import com.timebox.android.ui.planning.PlanningEditResult
import com.timebox.android.ui.planning.PlanningSession
import com.timebox.android.ui.planning.PlanningSessionState
import com.timebox.android.ui.taskcompletion.TaskCompletion
import com.timebox.android.ui.workmode.RepositoryWorkModePersistence
import com.timebox.android.ui.workmode.RepositoryWorkModeTransport
import com.timebox.android.ui.workmode.WorkModeExecution
import com.timebox.android.ui.workmode.WorkModePersistence as WorkModePersistencePort
import com.timebox.android.ui.workmode.WorkModeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** A pending block the user sketched by tapping an empty slot. */
data class Draft(
    val lane: Lane,
    val startMinute: Int,
    val endMinute: Int,
    val taskId: Int? = null,
    val taskTypeId: Int? = null,
)

data class DayPageState(
    val day: Day? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** False for a read-only preview; true once the normal day endpoint has opened it. */
    val materialized: Boolean = false,
)

typealias WorkModeUiState = WorkModeSession
typealias WorkModePersistence = WorkModePersistencePort

data class DayUiState(
    /** The device's date until the backend's is known; see [DayViewModel.start]. */
    val date: LocalDate = LocalDate.now(),
    /** Backend-resolved today, kept independently of whichever day page is visible. */
    val today: LocalDate? = null,
    val pages: Map<LocalDate, DayPageState> = emptyMap(),
    val taskTypes: List<TaskType> = emptyList(),
    val saving: Boolean = false,
    val message: String? = null,
    val selectedBlockId: Int? = null,
    val draft: Draft? = null,
    val noteInput: String = "",
    /** Raw text in the task type picker; cleared whenever the sheet changes what it shows. */
    val typeQuery: String = "",
    val planning: PlanningSessionState = PlanningSessionState(),
    val workMode: WorkModeUiState? = null,
    val workModeVisible: Boolean = false,
    val activeActualAvailable: Boolean = false,
    val workModeEntryWarning: Boolean = false,
    val workModeRestorePrompt: Boolean = false,
) {
    fun page(date: LocalDate): DayPageState = pages[date] ?: DayPageState()

    val currentPage: DayPageState get() = page(date)
    val day: Day? get() = currentPage.day
    val loading: Boolean get() = currentPage.loading
    val error: String? get() = currentPage.error

    val selectedBlock: TimeBlock?
        get() = day?.blocks?.firstOrNull { it.id == selectedBlockId }

    val sheetOpen: Boolean get() = selectedBlock != null || draft != null

    val sheetLane: Lane
        get() = selectedBlock?.lane ?: draft?.lane ?: Lane.Planned

    val sheetStart: Int get() = selectedBlock?.startMinute ?: draft?.startMinute ?: 0
    val sheetEnd: Int get() = selectedBlock?.endMinute ?: draft?.endMinute ?: 0

    val readyTasks: List<BattleTask> get() = planning.readyTasks
    val readyTasksLoading: Boolean get() = planning.queueLoading
    val readyTasksError: String? get() = planning.queueError
    val isPlanningMode: Boolean get() = planning.active
    val accessibilityPlanningTaskId: Int? get() = planning.selectedTaskId
    val accessibilityPlanningTask: BattleTask? get() = planning.selectedTask
    val planningDrafts: Map<Int, PlanningDraftPlacement> get() = planning.drafts

    fun planningDrafts(date: LocalDate): List<PlanningDraftPlacement> =
        planning.drafts(date)
}

class DayViewModel(
    private val repository: TimeboxRepository,
    private val taskCompletion: TaskCompletion,
    private val planningSession: PlanningSession,
    private val injectedScope: CoroutineScope? = null,
    private val clock: () -> Instant = Instant::now,
    private val workModeTickMillis: Long = 1_000L,
    private val workModePersistence: WorkModePersistence = RepositoryWorkModePersistence(repository),
    workModeExecution: WorkModeExecution? = null,
) : ViewModel() {

    private val launchScope: CoroutineScope get() = injectedScope ?: viewModelScope
    private val workModeBridgeScope: CoroutineScope =
        if (injectedScope == null) viewModelScope else CoroutineScope(injectedScope.coroutineContext + Job())

    private val _state = MutableStateFlow(DayUiState())
    val state: StateFlow<DayUiState> = _state.asStateFlow()
    private val workMode = workModeExecution ?: WorkModeExecution(
        RepositoryWorkModeTransport(repository),
        workModePersistence,
        launchScope,
        clock,
        workModeTickMillis,
    )

    private var typesLoaded = false
    private var todayResolved = false
    private val pageRequestVersions = mutableMapOf<LocalDate, Int>()
    private var planThenWork = false

    init {
        workModeBridgeScope.launch {
            workMode.state.collect { work ->
                _state.update { state ->
                    var next = state.copy(
                        workMode = work.session,
                        workModeVisible = work.visible,
                        activeActualAvailable = work.activeActualAvailable,
                        workModeEntryWarning = work.entryWarning,
                        workModeRestorePrompt = work.restorePrompt,
                        message = work.notice ?: state.message,
                    )
                    val day = work.day
                    if (day != null && (state.day?.date != day.date || state.day != day)) {
                        next = next.copy(date = day.date).withPage(day.date) {
                            DayPageState(day = day, loading = false, materialized = true)
                        }
                    }
                    next
                }
            }
        }
    }

    /**
     * Open the Day tab on the backend's today rather than the device's.
     *
     * `APP_TIMEZONE` is what decides which date a block belongs to, and a phone in
     * another zone can be a day out from it — Singapore's small hours are still
     * yesterday in UTC. The summary endpoint carries the same `meta` as a day fetch but
     * does not create the date it is asked about, so probing with it cannot leave a
     * stray empty day in the archive.
     *
     * Resolved once per process. Afterwards this is an ordinary reload, which keeps the
     * date the user navigated to instead of yanking them back to today.
     */
    fun start() {
        launchScope.launch {
            val active = repository.getActiveActualBlock().getOrNull()
            _state.update { it.copy(activeActualAvailable = active != null) }
        }
        if (todayResolved) {
            load()
            return
        }
        launchScope.launch {
            val today = repository.getDaySummary(_state.value.date).getOrNull()?.today
            todayResolved = today != null
            if (today != null) _state.update { it.copy(today = today) }
            load(today ?: _state.value.date)
        }
    }

    /** Battle Plan is independent of the timeline: failure leaves Day fully usable. */
    fun refreshReadyToPlan() {
        launchScope.launch(start = CoroutineStart.UNDISPATCHED) {
            planningSession.refreshQueue()
            syncPlanningState()
        }
        syncPlanningState()
    }

    fun setPlanningMode(enabled: Boolean) {
        if (!enabled) {
            cancelPlanningSession()
            return
        }
        planningSession.begin()
        syncPlanningState()
        _state.update { it.copy(draft = null, selectedBlockId = null) }
    }

    fun cancelPlanningSession() {
        planThenWork = false
        planningSession.cancel()
        syncPlanningState()
        _state.update { it.copy(draft = null, selectedBlockId = null) }
    }

    fun armAccessiblePlanningTask(taskId: Int?) {
        planningSession.toggleSelection(taskId)
        syncPlanningState()
        _state.update { it.copy(draft = null, selectedBlockId = null) }
    }

    fun load(date: LocalDate = _state.value.date, showSpinner: Boolean = true) {
        val requestVersion = nextRequestVersion(date)
        _state.update { state ->
            state.copy(date = date).withPage(date) { page ->
                page.copy(loading = showSpinner && page.day == null, error = null)
            }
        }
        launchScope.launch {
            repository.getDay(date).fold(
                onSuccess = { day ->
                    if (isLatest(date, requestVersion) && isInActiveWindow(date)) {
                        _state.update { state ->
                            state.copy(today = state.today ?: day.today).withPage(date) {
                                DayPageState(
                                    day = day,
                                    loading = false,
                                    error = null,
                                    materialized = true,
                                )
                            }
                        }
                        workMode.restore(day)
                        if (_state.value.date == date) prefetchAdjacent(date)
                    }
                },
                onFailure = { e ->
                    if (isLatest(date, requestVersion) && isInActiveWindow(date)) {
                        _state.update { state ->
                            state.withPage(date) { page ->
                                page.copy(loading = false, error = e.apiError.message)
                            }
                        }
                    }
                },
            )
            if (!typesLoaded) loadTaskTypes()
        }
    }

    /** Retry whichever kind of read a page currently needs. */
    fun retryPage(date: LocalDate) {
        if (date == _state.value.date) load(date) else loadPreview(date)
    }

    private fun prefetchAdjacent(center: LocalDate) {
        _state.update { state ->
            val keep = setOf(center.minusDays(1), center, center.plusDays(1))
            state.copy(pages = state.pages.filterKeys { it in keep })
        }
        loadPreview(center.minusDays(1))
        loadPreview(center.plusDays(1))
    }

    private fun loadPreview(date: LocalDate) {
        val existing = _state.value.pages[date]
        if (existing?.loading == true) return
        val requestVersion = nextRequestVersion(date)
        _state.update { state ->
            state.withPage(date) { it.copy(loading = true, error = null) }
        }
        launchScope.launch {
            repository.getDayPreview(date).fold(
                onSuccess = { day ->
                    if (isLatest(date, requestVersion) && isInActiveWindow(date)) {
                        _state.update { state ->
                            state.copy(today = state.today ?: day.today).withPage(date) {
                                DayPageState(day = day, loading = false, materialized = false)
                            }
                        }
                    }
                },
                onFailure = { error ->
                    if (isLatest(date, requestVersion) && isInActiveWindow(date)) {
                        _state.update { state ->
                            state.withPage(date) {
                                it.copy(loading = false, error = error.apiError.message)
                            }
                        }
                    }
                },
            )
        }
    }

    private fun nextRequestVersion(date: LocalDate): Int {
        val next = (pageRequestVersions[date] ?: 0) + 1
        pageRequestVersions[date] = next
        return next
    }

    private fun isLatest(date: LocalDate, version: Int): Boolean =
        pageRequestVersions[date] == version

    private fun isInActiveWindow(date: LocalDate): Boolean {
        val selected = _state.value.date
        return date >= selected.minusDays(1) && date <= selected.plusDays(1)
    }

    /** Re-read types after the Types screen may have changed them. */
    fun refreshTaskTypes() {
        launchScope.launch { loadTaskTypes() }
    }

    private suspend fun loadTaskTypes() {
        repository.listTaskTypes().onSuccess { types ->
            typesLoaded = true
            _state.update { it.copy(taskTypes = types) }
        }
    }

    fun goToDate(date: LocalDate) {
        if (date == _state.value.date) return
        planningSession.toggleSelection(null)
        syncPlanningState()
        _state.update {
            it.copy(
                date = date,
                selectedBlockId = null,
                draft = null,
                noteInput = "",
                typeQuery = "",
            )
        }
        load(date)
    }

    fun shiftDay(days: Long) = goToDate(_state.value.date.plusDays(days))

    fun selectBlock(blockId: Int) {
        val block = _state.value.day?.blocks?.firstOrNull { it.id == blockId }
        if (block?.lane == Lane.Actual) {
            launchScope.launch {
                val actual = repository.getActualBlock(block.actualBlockId ?: block.id).getOrNull()
                if (actual != null && actual.endAt == null) {
                    _state.value.day?.let { workMode.resume(it, actual) }
                } else {
                    _state.update { it.copy(selectedBlockId = blockId, draft = null, noteInput = block.note.orEmpty(), typeQuery = "") }
                }
            }
            return
        }
        _state.update {
            it.copy(
                selectedBlockId = blockId,
                draft = null,
                noteInput = block?.note.orEmpty(),
                typeQuery = "",
            )
        }
    }

    fun startDraft(lane: Lane, startMinute: Int) {
        planThenWork = false
        val day = _state.value.day ?: return
        val start = startMinute.coerceIn(day.visibleStart, day.visibleEnd - SLOT_MINUTES)
        _state.update { state ->
            state.copy(
                draft = Draft(
                    lane = lane,
                    startMinute = start,
                    endMinute = start + SLOT_MINUTES,
                ),
                selectedBlockId = null,
                noteInput = "",
                typeQuery = "",
            )
        }
    }

    fun closeSheet() {
        planThenWork = false
        val current = _state.value
        val selected = current.selectedBlock
        // The note field saves on dismiss rather than on every keystroke.
        if (selected != null && current.noteInput != selected.note.orEmpty()) {
            saveNote(selected, current.noteInput)
        }
        _state.update {
            it.copy(
                selectedBlockId = null,
                draft = null,
                noteInput = "",
                typeQuery = "",
            )
        }
    }

    fun onNoteChange(value: String) = _state.update { it.copy(noteInput = value) }

    fun onTypeQueryChange(value: String) = _state.update { it.copy(typeQuery = value) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun chooseTaskType(taskType: TaskType) {
        val current = _state.value
        val draft = current.draft
        if (draft != null) {
            createBlock(
                draft.lane,
                draft.startMinute,
                draft.endMinute,
                taskType.id,
                current.noteInput,
                draft.taskId,
            )
            return
        }
        val selected = current.selectedBlock ?: return
        if (selected.taskTypeId == taskType.id) return
        if (selected.lane == Lane.Actual) {
            launchScope.launch {
                repository.patchActualBlock(selected.actualBlockId ?: selected.id, taskTypeId = taskType.id).fold(
                    onSuccess = { refreshCurrentDay() },
                    onFailure = { error -> _state.update { it.copy(message = error.apiError.message) } },
                )
            }
            return
        }
        mutate(current.date, "Saved") {
            repository.patchBlock(current.date, selected.id, taskTypeId = taskType.id)
        }
    }

    /**
     * Create the path the picker offered, then assign it — one gesture from the sheet.
     *
     * The backend materialises any missing ancestors in the same transaction, so the type
     * list is re-read rather than having the new leaf appended to it on its own.
     */
    fun createTaskTypeAndChoose(path: String) {
        _state.update { it.copy(saving = true) }
        launchScope.launch {
            repository.createTaskType(path).fold(
                onSuccess = { created ->
                    loadTaskTypes()
                    _state.update { it.copy(saving = false) }
                    chooseTaskType(created)
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.apiError.message) }
                },
            )
        }
    }

    private fun createBlock(
        lane: Lane,
        start: Int,
        end: Int,
        taskTypeId: Int,
        note: String,
        taskId: Int?,
    ) {
        val date = _state.value.date
        val timezone = _state.value.day?.timezone ?: "UTC"
        _state.update { it.copy(saving = true) }
        launchScope.launch {
            val operation = if (lane == Lane.Actual) {
                val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
                val startAt = resolveActualMinute(date, start, zone)
                val endAt = resolveActualMinute(date, end, zone)
                if (startAt == null || endAt == null) {
                    _state.update {
                        it.copy(saving = false, message = "That local time does not exist in $timezone.")
                    }
                    return@launch
                }
                repository.createActualBlock(
                    startAt = startAt,
                    endAt = endAt,
                    taskTypeId = taskTypeId,
                    taskId = taskId,
                    note = note.ifBlank { null },
                ).map {
                    repository.getDay(date).getOrThrow()
                }
            } else {
                repository.createBlock(date, lane, taskTypeId, start, end, note.ifBlank { null }, taskId)
            }
            operation.fold(
                onSuccess = { day ->
                    // Picking the type is the whole draft flow, so the sheet is done: leaving
                    // it open on the fresh block would just hide the timeline it landed on.
                    _state.update { state ->
                        state.copy(
                            saving = false,
                            draft = if (state.date == date) null else state.draft,
                            selectedBlockId = if (state.date == date) null else state.selectedBlockId,
                            noteInput = if (state.date == date) "" else state.noteInput,
                            typeQuery = if (state.date == date) "" else state.typeQuery,
                            message = if (lane == Lane.Actual) null else "Block created",
                        ).withPage(date) {
                            DayPageState(day = day, loading = false, materialized = true)
                        }
                    }
                    if (taskId != null) {
                        planningSession.toggleSelection(null)
                        syncPlanningState()
                        refreshReadyToPlan()
                    }
                    if (lane == Lane.Planned && planThenWork) finishPlanningIntoWorkMode(day)
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.apiError.message) }
                },
            )
        }
    }

    fun planTaskAt(taskId: Int, startMinute: Int) {
        val current = _state.value
        val day = current.day ?: return
        when (val result = planningSession.place(taskId, day, startMinute)) {
            PlanningEditResult.Accepted -> Unit
            is PlanningEditResult.Rejected -> _state.update { it.copy(message = result.reason) }
        }
        syncPlanningState()
    }

    fun updatePlanningDraft(taskId: Int, startMinute: Int, endMinute: Int) {
        when (val result = planningSession.update(taskId, startMinute, endMinute)) {
            PlanningEditResult.Accepted -> Unit
            is PlanningEditResult.Rejected -> _state.update { it.copy(message = result.reason) }
        }
        syncPlanningState()
    }

    fun returnPlanningDraft(taskId: Int) {
        planningSession.returnTask(taskId)
        syncPlanningState()
    }

    fun commitPlanningSession() {
        _state.update { it.copy(message = null) }
        launchScope.launch(start = CoroutineStart.UNDISPATCHED) {
            when (val outcome = planningSession.commit()) {
                is PlanningCommitOutcome.Saved -> {
                    _state.update { state ->
                        outcome.days.fold(state.copy(message = "Plan saved")) { next, day ->
                            next.withPage(day.date) {
                                DayPageState(day = day, loading = false, materialized = true)
                            }
                        }
                    }
                }
                is PlanningCommitOutcome.Failed ->
                    _state.update { it.copy(message = outcome.reason) }
                PlanningCommitOutcome.CancelledEmptySession -> planThenWork = false
                PlanningCommitOutcome.Ignored -> Unit
            }
            syncPlanningState()
        }
        syncPlanningState()
    }

    private fun syncPlanningState() {
        val planning = planningSession.state.value
        _state.update { it.copy(planning = planning) }
    }

    /**
     * Move or resize a block, painting the new times before the server confirms them.
     *
     * The timeline drops its local drag state the instant the finger lifts, so without
     * the optimistic write the block would snap back to where it started and only jump
     * forward when the PATCH returned — a visible flicker on every drag. If the server
     * refuses the new slot the block's times are put back and the reason is surfaced.
     */
    fun moveBlock(blockId: Int, startMinute: Int, endMinute: Int) {
        val current = _state.value
        val day = current.day ?: return
        val block = day.blocks.firstOrNull { it.id == blockId } ?: return
        if (block.startMinute == startMinute && block.endMinute == endMinute) return

        _state.update { state ->
            state.copy(saving = true).withPage(current.date) { page ->
                page.copy(day = page.day?.withBlockTimes(blockId, startMinute, endMinute))
            }
        }
        launchScope.launch {
            repository.patchBlock(
                current.date,
                blockId,
                startMinute = startMinute,
                endMinute = endMinute,
            ).fold(
                onSuccess = { day ->
                    _state.update { state ->
                        state.copy(saving = false).withPage(current.date) {
                            DayPageState(day = day, loading = false, materialized = true)
                        }
                    }
                },
                onFailure = { e ->
                    // Roll back this block alone rather than the whole day: another
                    // request (a note save, say) may have landed a fresh day meanwhile.
                    _state.update { state ->
                        state.copy(
                            saving = false,
                            message = e.apiError.message,
                        ).withPage(current.date) { page ->
                            page.copy(day = page.day?.withBlockTimes(
                                blockId,
                                block.startMinute,
                                block.endMinute,
                            ))
                        }
                    }
                },
            )
        }
    }

    private fun saveNote(block: TimeBlock, note: String) {
        val date = _state.value.date
        launchScope.launch {
            if (block.lane == Lane.Actual) {
                repository.patchActualBlock(block.actualBlockId ?: block.id, note = note).fold(
                    onSuccess = { refreshCurrentDay() },
                    onFailure = { error -> _state.update { it.copy(message = error.apiError.message) } },
                )
                return@launch
            }
            repository.patchBlock(date, block.id, note = note).onSuccess { day ->
                _state.update { state ->
                    state.withPage(date) {
                        DayPageState(day = day, loading = false, materialized = true)
                    }
                }
            }
        }
    }

    fun deleteSelected() {
        val current = _state.value
        val selected = current.selectedBlock ?: return
        _state.update { it.copy(selectedBlockId = null) }
        if (selected.lane == Lane.Actual) {
            launchScope.launch {
                repository.deleteActualBlock(selected.actualBlockId ?: selected.id).fold(
                    onSuccess = { _state.update { it.copy(message = "Actual Block deleted") }; refreshCurrentDay() },
                    onFailure = { error -> _state.update { it.copy(message = error.apiError.message) } },
                )
            }
            return
        }
        mutate(current.date, "Block deleted") {
            repository.deleteBlock(current.date, selected.id)
        }
    }

    fun completeSelectedTask() {
        val current = _state.value
        val task = current.selectedBlock?.task ?: return
        if (task.isReadOnly) return
        _state.update { it.copy(saving = true, message = null) }
        launchScope.launch {
            taskCompletion.transition(task.id, task.status, TaskStatus.Completed).fold(
                onSuccess = {
                    repository.getDay(current.date).fold(
                        onSuccess = { day ->
                            _state.update { state ->
                                state.copy(
                                    saving = false,
                                ).withPage(current.date) {
                                    DayPageState(day = day, loading = false, materialized = true)
                                }
                            }
                            refreshReadyToPlan()
                        },
                        onFailure = { error ->
                            _state.update { it.copy(saving = false, message = error.apiError.message) }
                        },
                    )
                },
                onFailure = {
                    _state.update { it.copy(saving = false) }
                    refreshAfterTaskCompletion()
                },
            )
        }
    }

    /** App-level entry: present time and today's plan always win over navigation context. */
    fun startWorkMode() {
        if (workMode.state.value.session != null) {
            workMode.show()
            return
        }
        _state.update { it.copy(saving = true, message = null, selectedBlockId = null, draft = null) }
        launchScope.launch {
            val now = clock()
            val targetDate = _state.value.today ?: _state.value.day?.today ?: _state.value.date
            val day = _state.value.page(targetDate).day ?: repository.getDay(targetDate).getOrElse {
                _state.update { state -> state.copy(saving = false, message = it.apiError.message) }
                return@launch
            }
            _state.update { state ->
                state.copy(date = targetDate).withPage(targetDate) {
                    DayPageState(day = day, loading = false, materialized = true)
                }
            }
            workMode.begin(day)
            _state.update { it.copy(saving = false) }
        }
    }

    fun continueWorkModeEntry() {
        val day = _state.value.day ?: return
        launchScope.launch { workMode.continueEntry(day) }
    }

    fun planSomethingBeforeWorkMode() {
        val day = _state.value.day ?: return
        val nowMinute = minuteOfDay(clock(), day.timezone)
        val start = (nowMinute / SLOT_MINUTES * SLOT_MINUTES)
            .coerceIn(day.visibleStart, day.visibleEnd - SLOT_MINUTES)
        planThenWork = true
        _state.update {
            it.copy(
                workModeEntryWarning = false,
                draft = Draft(Lane.Planned, start, start + SLOT_MINUTES),
                selectedBlockId = null,
            )
        }
    }

    private suspend fun finishPlanningIntoWorkMode(day: Day) {
        planThenWork = false
        workMode.begin(day)
    }

    fun toggleWorkModeSubtask(subtask: Subtask) {
        workMode.toggleSubtask(subtask)
    }

    fun exitWorkMode() {
        launchScope.launch {
            if (workMode.exit()) refreshCurrentDay()
        }
    }

    fun confirmWorkContinued() {
        workMode.continueAfterAbsence()
    }

    fun declineWorkContinued() {
        workMode.declineAfterAbsence()
    }

    fun leaveWorkModeVisible() = workMode.hide()

    fun reopenSelectedTask() {
        val current = _state.value
        val task = current.selectedBlock?.task ?: return
        if (task.isReadOnly) return
        _state.update { it.copy(saving = true, message = null) }
        launchScope.launch {
            taskCompletion.transition(task.id, task.status, TaskStatus.Open).fold(
                onSuccess = {
                    repository.getDay(current.date).fold(
                        onSuccess = { day ->
                            _state.update { state ->
                                state.copy(
                                    saving = false,
                                ).withPage(current.date) {
                                    DayPageState(day = day, loading = false, materialized = true)
                                }
                            }
                            refreshReadyToPlan()
                        },
                        onFailure = { error ->
                            _state.update { it.copy(saving = false, message = error.apiError.message) }
                        },
                    )
                },
                onFailure = {
                    _state.update { it.copy(saving = false) }
                    refreshAfterTaskCompletion()
                },
            )
        }
    }

    fun refreshAfterTaskCompletion() {
        val date = _state.value.date
        launchScope.launch {
            repository.getDay(date).fold(
                onSuccess = { day ->
                    _state.update { state ->
                        state.withPage(date) {
                            DayPageState(day = day, loading = false, materialized = true)
                        }
                    }
                    refreshReadyToPlan()
                },
                onFailure = { error -> _state.update { it.copy(message = error.apiError.message) } },
            )
        }
    }

    private suspend fun refreshCurrentDay() {
        val date = _state.value.date
        repository.getDay(date).onSuccess { day ->
            _state.update { state ->
                state.withPage(date) { DayPageState(day = day, loading = false, materialized = true) }
            }
        }
    }

    /** A copy of the day with one block re-timed; unchanged if that block has gone. */
    private fun Day.withBlockTimes(blockId: Int, startMinute: Int, endMinute: Int): Day = copy(
        blocks = blocks.map {
            if (it.id == blockId) {
                it.copy(startMinute = startMinute, endMinute = endMinute)
            } else {
                it
            }
        },
    )

    private fun mutate(
        date: LocalDate,
        successMessage: String?,
        block: suspend () -> Result<Day>,
    ) {
        _state.update { it.copy(saving = true) }
        launchScope.launch {
            block().fold(
                onSuccess = { day ->
                    _state.update { state ->
                        state.copy(saving = false, message = successMessage).withPage(date) {
                            DayPageState(day = day, loading = false, materialized = true)
                        }
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.apiError.message) }
                },
            )
        }
    }
}

private val ACTUAL_INPUT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)

internal fun parseActualInput(value: String, zone: ZoneId): Instant? = runCatching {
    val local = LocalDateTime.parse(value.trim(), ACTUAL_INPUT_FORMAT)
    local.atZone(zone).takeIf { it.toLocalDateTime() == local }?.toInstant()
}.getOrNull()

internal fun resolveActualMinute(date: LocalDate, minute: Int, zone: ZoneId): Instant? {
    val local = date.atStartOfDay().plusMinutes(minute.toLong())
    return local.atZone(zone).takeIf { it.toLocalDateTime() == local }?.toInstant()
}

private fun minuteOfDay(instant: Instant, timezone: String): Int {
    val local = instant.atZone(ZoneId.of(timezone))
    return local.hour * 60 + local.minute
}


private inline fun DayUiState.withPage(
    date: LocalDate,
    transform: (DayPageState) -> DayPageState,
): DayUiState = copy(pages = pages + (date to transform(page(date))))
