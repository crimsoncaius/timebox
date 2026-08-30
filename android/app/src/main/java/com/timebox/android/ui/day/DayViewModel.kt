package com.timebox.android.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.ActualBlock
import com.timebox.android.data.Day
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Lane
import com.timebox.android.data.PlanningCommitPlacement
import com.timebox.android.data.SLOT_MINUTES
import com.timebox.android.data.TaskType
import com.timebox.android.data.Subtask
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.WorkModeSnapshot
import com.timebox.android.data.apiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

data class PlanningDraftPlacement(
    val date: LocalDate,
    val task: BattleTask,
    val startMinute: Int,
    val endMinute: Int,
) {
    val taskId: Int get() = task.id
    val taskTitle: String get() = task.title
}

data class DayPageState(
    val day: Day? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** False for a read-only preview; true once the normal day endpoint has opened it. */
    val materialized: Boolean = false,
)

data class WorkModeUiState(
    val entryAt: Instant,
    val lastConfirmedAt: Instant,
    val lastObservedAt: Instant,
    val currentBlock: TimeBlock? = null,
    val nextBlock: TimeBlock? = null,
    val task: BattleTask?,
    val timezone: String,
    val activeActual: ActualBlock? = null,
    val confirmingPlannedBlockId: Int? = null,
    val confirmationStartedAt: Instant? = null,
    val activePlannedEndAt: Instant? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val isRecording: Boolean get() = activeActual != null
}

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
    val readyTasks: List<BattleTask> = emptyList(),
    val readyTasksLoading: Boolean = false,
    val readyTasksError: String? = null,
    val isPlanningMode: Boolean = false,
    val accessibilityPlanningTaskId: Int? = null,
    val planningDrafts: Map<Int, PlanningDraftPlacement> = emptyMap(),
    val completionUndoTaskId: Int? = null,
    val completionUndoToken: String? = null,
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

    val accessibilityPlanningTask: BattleTask?
        get() = readyTasks.firstOrNull { it.id == accessibilityPlanningTaskId }

    fun planningDrafts(date: LocalDate): List<PlanningDraftPlacement> =
        planningDrafts.values.filter { it.date == date }
}

interface WorkModePersistence {
    suspend fun load(): WorkModeSnapshot?
    suspend fun save(snapshot: WorkModeSnapshot?)
}

private class RepositoryWorkModePersistence(
    private val repository: TimeboxRepository,
) : WorkModePersistence {
    override suspend fun load(): WorkModeSnapshot? = repository.workMode.first()
    override suspend fun save(snapshot: WorkModeSnapshot?) = repository.setWorkMode(snapshot)
}

class DayViewModel(
    private val repository: TimeboxRepository,
    private val injectedScope: CoroutineScope? = null,
    private val clock: () -> Instant = Instant::now,
    private val workModeTickMillis: Long = 1_000L,
    private val workModePersistence: WorkModePersistence = RepositoryWorkModePersistence(repository),
) : ViewModel() {

    private val launchScope: CoroutineScope get() = injectedScope ?: viewModelScope

    private val _state = MutableStateFlow(DayUiState())
    val state: StateFlow<DayUiState> = _state.asStateFlow()

    private var typesLoaded = false
    private var todayResolved = false
    private val pageRequestVersions = mutableMapOf<LocalDate, Int>()
    private var workModeJob: Job? = null
    private var workModeRestoreChecked = false
    private var planThenWork = false

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
        _state.update { it.copy(readyTasksLoading = it.readyTasks.isEmpty(), readyTasksError = null) }
        launchScope.launch {
            repository.listBattleTasks().fold(
                onSuccess = { result ->
                    val ready = result.items.readyToPlanTasks()
                    _state.update { state ->
                        state.copy(
                            readyTasks = ready,
                            readyTasksLoading = false,
                            accessibilityPlanningTaskId = state.accessibilityPlanningTaskId
                                ?.takeIf { id -> ready.any { it.id == id } },
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(readyTasksLoading = false, readyTasksError = error.apiError.message) }
                },
            )
        }
    }

    fun setPlanningMode(enabled: Boolean) {
        if (!enabled) {
            cancelPlanningSession()
            return
        }
        _state.update {
            it.copy(
                isPlanningMode = true,
                accessibilityPlanningTaskId = null,
                planningDrafts = emptyMap(),
                draft = null,
                selectedBlockId = null,
            )
        }
    }

    fun cancelPlanningSession() {
        planThenWork = false
        _state.update {
            it.copy(
                isPlanningMode = false,
                accessibilityPlanningTaskId = null,
                planningDrafts = emptyMap(),
                draft = null,
                selectedBlockId = null,
            )
        }
    }

    fun armAccessiblePlanningTask(taskId: Int?) {
        _state.update { state ->
            state.copy(
                accessibilityPlanningTaskId = taskId?.takeUnless {
                    it == state.accessibilityPlanningTaskId
                },
                draft = null,
                selectedBlockId = null,
            )
        }
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
                        restoreStoredWorkMode(day)
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
        _state.update {
            it.copy(
                date = date,
                selectedBlockId = null,
                draft = null,
                noteInput = "",
                typeQuery = "",
                accessibilityPlanningTaskId = null,
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
                    _state.value.day?.let { enterWorkMode(it, actual.startAt, actual) }
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
                            accessibilityPlanningTaskId = if (state.date == date && taskId != null) {
                                null
                            } else {
                                state.accessibilityPlanningTaskId
                            },
                        ).withPage(date) {
                            DayPageState(day = day, loading = false, materialized = true)
                        }
                    }
                    if (taskId != null) refreshReadyToPlan()
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
        val task = current.readyTasks.firstOrNull { it.id == taskId } ?: return
        val start = startMinute.coerceIn(day.visibleStart, day.visibleEnd - SLOT_MINUTES)
        val end = start + SLOT_MINUTES
        if (!planningRangeAvailable(current, current.date, taskId, start, end)) {
            _state.update { it.copy(message = "That time is already planned") }
            return
        }
        _state.update {
            it.copy(
                planningDrafts = it.planningDrafts + (
                    task.id to PlanningDraftPlacement(current.date, task, start, end)
                ),
                accessibilityPlanningTaskId = null,
            )
        }
    }

    fun updatePlanningDraft(taskId: Int, startMinute: Int, endMinute: Int) {
        val current = _state.value
        val draft = current.planningDrafts[taskId] ?: return
        val day = current.page(draft.date).day ?: return
        if (endMinute - startMinute < SLOT_MINUTES) return
        if (startMinute < day.visibleStart || endMinute > day.visibleEnd) return
        if (!planningRangeAvailable(current, draft.date, taskId, startMinute, endMinute)) return
        _state.update { state ->
            state.copy(
                planningDrafts = state.planningDrafts + (
                    taskId to draft.copy(startMinute = startMinute, endMinute = endMinute)
                )
            )
        }
    }

    fun returnPlanningDraft(taskId: Int) {
        _state.update { state ->
            state.copy(planningDrafts = state.planningDrafts - taskId)
        }
    }

    fun commitPlanningSession() {
        val current = _state.value
        if (!current.isPlanningMode || current.saving) return
        val drafts = current.planningDrafts.values.toList()
        if (drafts.isEmpty()) {
            cancelPlanningSession()
            return
        }
        _state.update { it.copy(saving = true, message = null) }
        launchScope.launch {
            val placements = mutableListOf<PlanningCommitPlacement>()
            for (draft in drafts) {
                val taskTypeId = resolvePlanningTaskType(draft.task)
                if (taskTypeId == null) {
                    _state.update {
                        it.copy(saving = false, message = "Could not create the unspecified task type")
                    }
                    return@launch
                }
                placements += PlanningCommitPlacement(
                    date = draft.date,
                    taskId = draft.taskId,
                    taskTypeId = taskTypeId,
                    startMinute = draft.startMinute,
                    endMinute = draft.endMinute,
                )
            }
            repository.commitPlan(placements).fold(
                onSuccess = { updatedDays ->
                    val plannedTaskIds = drafts.mapTo(mutableSetOf()) { it.taskId }
                    _state.update { state ->
                        updatedDays.fold(
                            state.copy(
                                saving = false,
                                isPlanningMode = false,
                                planningDrafts = emptyMap(),
                                readyTasks = state.readyTasks.filterNot { it.id in plannedTaskIds },
                                accessibilityPlanningTaskId = null,
                                message = "Plan saved",
                            )
                        ) { next, day ->
                            next.withPage(day.date) {
                                DayPageState(day = day, loading = false, materialized = true)
                            }
                        }
                    }
                    refreshReadyToPlan()
                },
                onFailure = { error ->
                    _state.update { it.copy(saving = false, message = error.apiError.message) }
                },
            )
        }
    }

    private suspend fun resolvePlanningTaskType(task: BattleTask): Int? {
        task.taskTypeId?.let { return it }
        _state.value.taskTypes.unspecifiedTypeId()?.let { return it }

        val refreshed = repository.listTaskTypes().getOrNull().orEmpty()
        if (refreshed.isNotEmpty()) _state.update { it.copy(taskTypes = refreshed) }
        refreshed.unspecifiedTypeId()?.let { return it }

        repository.createTaskType("unspecified").getOrNull()?.let { created ->
            _state.update { state -> state.copy(taskTypes = (state.taskTypes + created).distinctBy { it.id }) }
            return created.id
        }

        val afterConflict = repository.listTaskTypes().getOrNull().orEmpty()
        if (afterConflict.isNotEmpty()) _state.update { it.copy(taskTypes = afterConflict) }
        return afterConflict.unspecifiedTypeId()
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
            repository.completeBattleTask(task.id).fold(
                onSuccess = { result ->
                    repository.getDay(current.date).fold(
                        onSuccess = { day ->
                            val removed = result.removedPlannedBlockIds.size
                            _state.update { state ->
                                state.copy(
                                    saving = false,
                                    message = completionMessage(removed),
                                    completionUndoTaskId = task.id,
                                    completionUndoToken = result.undoToken,
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
                onFailure = { error ->
                    _state.update { it.copy(saving = false, message = error.apiError.message) }
                },
            )
        }
    }

    /** App-level entry: present time and today's plan always win over navigation context. */
    fun startWorkMode() {
        if (_state.value.workMode != null) {
            _state.update { it.copy(workModeVisible = true) }
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
            val active = repository.getActiveActualBlock().getOrNull()
            if (active != null) {
                enterWorkMode(day, active.startAt, active)
                return@launch
            }
            val (current, next) = workSelection(day, now)
            val nowMinute = minuteOfDay(now, day.timezone)
            if (current != null || (next != null && next.startMinute - nowMinute <= 10)) {
                enterWorkMode(day, now)
            } else {
                _state.update { it.copy(saving = false, workModeEntryWarning = true) }
            }
        }
    }

    fun continueWorkModeEntry() {
        val day = _state.value.day ?: return
        _state.update { it.copy(workModeEntryWarning = false) }
        launchScope.launch { enterWorkMode(day, clock()) }
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
        val completedAt = clock()
        val (current, next) = workSelection(day, completedAt)
        val nowMinute = minuteOfDay(completedAt, day.timezone)
        if (current != null || (next != null && next.startMinute - nowMinute <= 10)) {
            enterWorkMode(day, completedAt)
        }
    }

    private suspend fun enterWorkMode(day: Day, entryAt: Instant, active: ActualBlock? = null) {
        val now = clock()
        val (clockCurrent, clockNext) = workSelection(day, now)
        val current = active?.let { activeWorkBlock(day, it, now) } ?: clockCurrent
        val next = if (active == null) clockNext else day.lane(Lane.Planned)
            .sortedBy { it.startMinute }
            .firstOrNull { it.startMinute >= (current?.endMinute ?: minuteOfDay(now, day.timezone)) && it.id != current?.id }
        val list = repository.listBattleTasks().getOrNull()
        val taskId = current?.taskId ?: active?.taskId
        val task = taskId?.let { list?.items?.findBattleTask(it) }
        val activeEnd = active?.plannedBlockId?.let { plannedId ->
            day.lane(Lane.Planned).firstOrNull { it.id == plannedId }
                ?.let { blockInstant(day, it.endMinute) }
        }
        val work = WorkModeUiState(
            entryAt = entryAt,
            lastConfirmedAt = now,
            lastObservedAt = now,
            currentBlock = current,
            nextBlock = next,
            task = task,
            timezone = day.timezone,
            activeActual = active,
            activePlannedEndAt = activeEnd,
        )
        _state.update {
            it.copy(
                saving = false,
                selectedBlockId = null,
                draft = null,
                workMode = work,
                workModeVisible = true,
                activeActualAvailable = active != null,
                workModeEntryWarning = false,
                workModeRestorePrompt = false,
            )
        }
        persistWorkMode(work)
        startWorkModeTicker()
    }

    private fun startWorkModeTicker() {
        workModeJob?.cancel()
        workModeJob = launchScope.launch {
            while (_state.value.workMode != null && !_state.value.workModeRestorePrompt) {
                reconcileWorkMode()
                delay(workModeTickMillis)
            }
        }
    }

    private suspend fun reconcileWorkMode() {
        var work = _state.value.workMode ?: return
        val now = clock()
        var day = _state.value.day ?: return
        val today = now.atZone(ZoneId.of(day.timezone)).toLocalDate()
        if (
            work.activeActual == null &&
            work.confirmingPlannedBlockId != null &&
            work.confirmationStartedAt != null
        ) {
            val confirmed = work.currentBlock?.takeIf { it.id == work.confirmingPlannedBlockId }
            if (confirmed != null) {
                val startAt = maxOf(work.entryAt, blockInstant(day, confirmed.startMinute))
                val endAt = blockInstant(day, confirmed.endMinute)
                val confirmedThrough = minOf(now, endAt)
                val minuteConfirmed = !confirmedThrough.isBefore(work.confirmationStartedAt.plusSeconds(60))
                work = if (!minuteConfirmed && !now.isBefore(endAt)) {
                    work.copy(
                        currentBlock = null,
                        confirmingPlannedBlockId = null,
                        confirmationStartedAt = null,
                    )
                } else if (minuteConfirmed && !now.isBefore(endAt)) {
                    repository.createActualBlock(
                        startAt = startAt,
                        endAt = endAt,
                        plannedBlockId = confirmed.id,
                    ).getOrElse { error ->
                        _state.update { it.copy(workMode = work.copy(error = error.apiError.message)) }
                        return
                    }
                    work.copy(
                        currentBlock = null,
                        confirmingPlannedBlockId = null,
                        confirmationStartedAt = null,
                        lastConfirmedAt = endAt,
                    )
                } else if (minuteConfirmed) {
                    val actual = repository.startActualBlock(
                        plannedBlockId = confirmed.id,
                        startAt = startAt,
                    ).getOrElse { error ->
                        _state.update { it.copy(workMode = work.copy(error = error.apiError.message)) }
                        return
                    }
                    work.copy(
                        activeActual = actual,
                        activePlannedEndAt = endAt,
                        confirmingPlannedBlockId = null,
                        confirmationStartedAt = null,
                        lastConfirmedAt = now,
                    )
                } else work
            }
        }
        if (day.date != today) {
            day = repository.getDay(today).getOrElse { error ->
                _state.update { it.copy(workMode = work.copy(error = error.apiError.message)) }
                return
            }
            _state.update { state -> state.copy(date = today).withPage(today) { DayPageState(day = day, loading = false, materialized = true) } }
        }

        if (work.activeActual != null && work.activePlannedEndAt != null && !now.isBefore(work.activePlannedEndAt)) {
            repository.patchActualBlock(work.activeActual.id, endAt = work.activePlannedEndAt).getOrElse { error ->
                _state.update { it.copy(workMode = work.copy(error = error.apiError.message)) }
                return
            }
            work = work.copy(activeActual = null, activePlannedEndAt = null, lastConfirmedAt = work.activePlannedEndAt)
        }

        val (current, next) = workSelection(day, now)
        work = when {
            work.activeActual != null -> {
                val activeBlock = activeWorkBlock(day, work.activeActual, now)
                val activeNext = day.lane(Lane.Planned).sortedBy { it.startMinute }
                    .firstOrNull { it.startMinute >= activeBlock.endMinute && it.id != activeBlock.id }
                work.copy(
                    currentBlock = activeBlock,
                    nextBlock = activeNext,
                    confirmingPlannedBlockId = null,
                    confirmationStartedAt = null,
                    lastConfirmedAt = now,
                    lastObservedAt = now,
                )
            }
            current == null -> work.copy(
                currentBlock = null,
                nextBlock = next,
                task = null,
                confirmingPlannedBlockId = null,
                confirmationStartedAt = null,
                lastObservedAt = now,
            )
            work.activeActual?.plannedBlockId == current.id -> work.copy(
                currentBlock = current,
                nextBlock = next,
                lastConfirmedAt = now,
                lastObservedAt = now,
            )
            work.confirmingPlannedBlockId != current.id || work.confirmationStartedAt == null -> {
                val started = maxOf(work.entryAt, blockInstant(day, current.startMinute))
                work.copy(
                    currentBlock = current,
                    nextBlock = next,
                    confirmingPlannedBlockId = current.id,
                    confirmationStartedAt = started,
                    lastObservedAt = now,
                )
            }
            !now.isBefore(work.confirmationStartedAt.plusSeconds(60)) -> {
                val startAt = maxOf(work.entryAt, blockInstant(day, current.startMinute))
                val actual = repository.startActualBlock(plannedBlockId = current.id, startAt = startAt).getOrElse { error ->
                    val active = repository.getActiveActualBlock().getOrNull()
                    if (active != null) active else {
                        _state.update { it.copy(workMode = work.copy(error = error.apiError.message)) }
                        return
                    }
                }
                work.copy(
                    currentBlock = current,
                    nextBlock = next,
                    activeActual = actual,
                    activePlannedEndAt = blockInstant(day, current.endMinute),
                    confirmingPlannedBlockId = null,
                    confirmationStartedAt = null,
                    lastConfirmedAt = now,
                    lastObservedAt = now,
                )
            }
            else -> work.copy(currentBlock = current, nextBlock = next, lastObservedAt = now)
        }

        val taskId = work.currentBlock?.taskId
        if (taskId != work.task?.id) {
            val list = repository.listBattleTasks().getOrNull()
            work = work.copy(task = taskId?.let { list?.items?.findBattleTask(it) })
        }
        _state.update { it.copy(workMode = work, activeActualAvailable = work.activeActual != null) }
        persistWorkMode(work)
    }

    fun toggleWorkModeSubtask(subtask: Subtask) {
        val work = _state.value.workMode ?: return
        if (work.saving || work.task?.status == com.timebox.android.data.TaskStatus.Completed) return
        _state.update { it.copy(workMode = work.copy(saving = true, error = null)) }
        launchScope.launch {
            val result = if (subtask.checked) repository.uncheckSubtask(subtask.id) else repository.checkSubtask(subtask.id)
            result.fold(
                onSuccess = {
                    val tasks = repository.listBattleTasks().getOrNull()
                    val task = work.task?.id?.let { tasks?.items?.findBattleTask(it) }
                    _state.update { it.copy(workMode = work.copy(task = task, saving = false)) }
                },
                onFailure = { error -> _state.update { it.copy(workMode = work.copy(saving = false, error = error.apiError.message)) } },
            )
        }
    }

    fun exitWorkMode() {
        val work = _state.value.workMode ?: return
        if (work.saving) return
        _state.update { it.copy(workMode = work.copy(saving = true, error = null)) }
        launchScope.launch {
            val active = work.activeActual
            if (active != null) {
                repository.patchActualBlock(active.id, endAt = clock()).getOrElse { error ->
                    _state.update { it.copy(workMode = work.copy(saving = false, error = error.apiError.message)) }
                    return@launch
                }
            }
            workModePersistence.save(null)
            workModeJob?.cancel()
            _state.update { it.copy(workMode = null, workModeVisible = false, activeActualAvailable = false, message = "Actual time preserved · Task remains open") }
            refreshCurrentDay()
        }
    }

    fun confirmWorkContinued() {
        val work = _state.value.workMode ?: return
        _state.update { it.copy(workMode = work.copy(saving = true, error = null)) }
        launchScope.launch {
            val now = clock()
            val day = _state.value.day ?: return@launch
            var resumed = work
            var endedActivePlannedBlockId: Int? = null
            if (resumed.activeActual != null && resumed.activePlannedEndAt != null && !now.isBefore(resumed.activePlannedEndAt)) {
                endedActivePlannedBlockId = resumed.activeActual.plannedBlockId
                repository.patchActualBlock(resumed.activeActual.id, endAt = resumed.activePlannedEndAt).getOrElse { error ->
                    _state.update { it.copy(workMode = work.copy(saving = false, error = error.apiError.message)) }
                    return@launch
                }
                resumed = resumed.copy(activeActual = null, activePlannedEndAt = null)
            }
            day.lane(Lane.Planned).sortedBy { it.startMinute }.forEach { block ->
                if (block.id == resumed.activeActual?.plannedBlockId || block.id == endedActivePlannedBlockId) return@forEach
                val blockStart = blockInstant(day, block.startMinute)
                val blockEnd = blockInstant(day, block.endMinute)
                val start = maxOf(blockStart, work.lastConfirmedAt, work.entryAt)
                val end = minOf(blockEnd, now)
                if (!end.isAfter(start)) return@forEach
                if (!blockEnd.isAfter(now)) {
                    repository.createActualBlock(startAt = start, endAt = end, plannedBlockId = block.id).getOrElse { error ->
                        _state.update { it.copy(workMode = work.copy(saving = false, error = error.apiError.message)) }
                        return@launch
                    }
                } else {
                    val actual = repository.startActualBlock(plannedBlockId = block.id, startAt = start).getOrElse { error ->
                        _state.update { it.copy(workMode = work.copy(saving = false, error = error.apiError.message)) }
                        return@launch
                    }
                    resumed = resumed.copy(activeActual = actual, activePlannedEndAt = blockEnd)
                }
            }
            val (current, next) = workSelection(day, now)
            resumed = resumed.copy(
                currentBlock = current,
                nextBlock = next,
                lastConfirmedAt = now,
                lastObservedAt = now,
                confirmingPlannedBlockId = null,
                confirmationStartedAt = null,
                saving = false,
            )
            _state.update {
                it.copy(
                    workMode = resumed,
                    workModeRestorePrompt = false,
                    activeActualAvailable = resumed.activeActual != null,
                )
            }
            persistWorkMode(resumed)
            startWorkModeTicker()
        }
    }

    fun declineWorkContinued() {
        val work = _state.value.workMode ?: return
        launchScope.launch {
            work.activeActual?.let { repository.patchActualBlock(it.id, endAt = work.lastConfirmedAt).getOrNull() }
            workModePersistence.save(null)
            _state.update { it.copy(workMode = null, workModeVisible = false, workModeRestorePrompt = false) }
        }
    }

    private suspend fun restoreStoredWorkMode(day: Day) {
        if (workModeRestoreChecked) return
        workModeRestoreChecked = true
        val snapshot = workModePersistence.load() ?: return
        val now = clock()
        val active = repository.getActiveActualBlock().getOrNull()
        val (clockCurrent, next) = workSelection(day, now)
        val current = snapshot.confirmingPlannedBlockId?.let { confirmingId ->
            day.lane(Lane.Planned).firstOrNull { it.id == confirmingId }
        } ?: clockCurrent
        val taskId = current?.taskId ?: active?.taskId
        val tasks = repository.listBattleTasks().getOrNull()
        val work = snapshot.toUiState(
            currentBlock = current,
            nextBlock = next,
            task = taskId?.let { tasks?.items?.findBattleTask(it) },
            timezone = day.timezone,
            activeActual = active,
        )
        val absentTooLong = now.isAfter(work.lastObservedAt.plusSeconds(10 * 60))
        _state.update { it.copy(workMode = work, workModeVisible = true, activeActualAvailable = active != null, workModeRestorePrompt = absentTooLong) }
        if (!absentTooLong) startWorkModeTicker()
    }

    private suspend fun persistWorkMode(work: WorkModeUiState) {
        workModePersistence.save(work.toSnapshot())
    }

    private fun workSelection(day: Day, now: Instant): Pair<TimeBlock?, TimeBlock?> {
        val minute = minuteOfDay(now, day.timezone)
        val planned = day.lane(Lane.Planned).sortedBy { it.startMinute }
        return planned.firstOrNull { minute >= it.startMinute && minute < it.endMinute } to
            planned.firstOrNull { it.startMinute > minute }
    }

    private fun activeWorkBlock(day: Day, active: ActualBlock, now: Instant): TimeBlock {
        val linked = active.plannedBlockId?.let { plannedId ->
            day.lane(Lane.Planned).firstOrNull { it.id == plannedId }
        }
        if (linked != null) return linked
        val start = minuteOfDay(active.startAt, day.timezone)
        val end = maxOf(start + 1, minuteOfDay(now, day.timezone))
        return TimeBlock(
            id = active.id,
            lane = Lane.Actual,
            taskTypeId = active.taskTypeId,
            taskTypeName = active.taskTypeName,
            taskId = active.taskId,
            task = active.task,
            note = active.note,
            plannedBlockId = null,
            actualBlockId = active.id,
            startMinute = start,
            endMinute = end,
        )
    }

    private fun minuteOfDay(now: Instant, timezone: String): Int {
        val local = now.atZone(runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC")))
        return local.hour * 60 + local.minute
    }

    private fun blockInstant(day: Day, minute: Int): Instant =
        day.date.atStartOfDay(runCatching { ZoneId.of(day.timezone) }.getOrDefault(ZoneId.of("UTC")))
            .plusMinutes(minute.toLong()).toInstant()

    fun leaveWorkModeVisible() = _state.update { it.copy(workModeVisible = false) }

    fun reopenSelectedTask() {
        val current = _state.value
        val task = current.selectedBlock?.task ?: return
        if (task.isReadOnly) return
        _state.update { it.copy(saving = true, message = null) }
        launchScope.launch {
            repository.reopenBattleTask(task.id).fold(
                onSuccess = {
                    repository.getDay(current.date).fold(
                        onSuccess = { day ->
                            _state.update { state ->
                                state.copy(
                                    saving = false,
                                    message = "Task reopened",
                                    completionUndoTaskId = null,
                                    completionUndoToken = null,
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
                onFailure = { error ->
                    _state.update { it.copy(saving = false, message = error.apiError.message) }
                },
            )
        }
    }

    fun undoLastTaskCompletion() {
        val current = _state.value
        val taskId = current.completionUndoTaskId ?: return
        val token = current.completionUndoToken ?: return
        _state.update { it.copy(saving = true, completionUndoTaskId = null, completionUndoToken = null) }
        launchScope.launch {
            repository.undoBattleTaskCompletion(taskId, token).fold(
                onSuccess = {
                    repository.getDay(current.date).fold(
                        onSuccess = { day ->
                            _state.update { state ->
                                state.copy(saving = false, message = "Task completion undone").withPage(current.date) {
                                    DayPageState(day = day, loading = false, materialized = true)
                                }
                            }
                            refreshReadyToPlan()
                        },
                        onFailure = { error -> _state.update { it.copy(saving = false, message = error.apiError.message) } },
                    )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            saving = false,
                            message = error.apiError.message,
                            completionUndoTaskId = taskId,
                            completionUndoToken = token,
                        )
                    }
                },
            )
        }
    }

    fun dismissCompletionUndo() = _state.update {
        it.copy(completionUndoTaskId = null, completionUndoToken = null)
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

private fun WorkModeSnapshot.toUiState(
    currentBlock: TimeBlock?,
    nextBlock: TimeBlock?,
    task: BattleTask?,
    timezone: String,
    activeActual: ActualBlock?,
) = WorkModeUiState(
    entryAt = Instant.parse(entryAt),
    lastConfirmedAt = Instant.parse(lastConfirmedAt),
    lastObservedAt = Instant.parse(lastObservedAt),
    currentBlock = currentBlock,
    nextBlock = nextBlock,
    task = task,
    timezone = timezone,
    activeActual = activeActual,
    confirmingPlannedBlockId = confirmingPlannedBlockId,
    confirmationStartedAt = confirmationStartedAt?.let(Instant::parse),
    activePlannedEndAt = activePlannedEndAt?.let(Instant::parse),
)

private fun WorkModeUiState.toSnapshot() = WorkModeSnapshot(
    entryAt = entryAt.toString(),
    lastConfirmedAt = lastConfirmedAt.toString(),
    lastObservedAt = lastObservedAt.toString(),
    confirmingPlannedBlockId = confirmingPlannedBlockId,
    confirmationStartedAt = confirmationStartedAt?.toString(),
    activeActualId = activeActual?.id,
    activePlannedBlockId = activeActual?.plannedBlockId,
    activePlannedEndAt = activePlannedEndAt?.toString(),
)

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

private fun List<BattleTask>.findBattleTask(id: Int): BattleTask? =
    firstNotNullOfOrNull { task -> task.takeIf { it.id == id } ?: task.sessionTasks.findBattleTask(id) }

private fun completionMessage(removed: Int): String =
    if (removed == 0) "Task completed" else "Task completed · $removed future Planned ${if (removed == 1) "Block" else "Blocks"} removed"

internal fun List<BattleTask>.readyToPlanTasks(): List<BattleTask> =
    flatMap { task -> listOf(task) + task.sessionTasks.readyToPlanTasks() }.filter { it.readyToPlan }

internal fun List<TaskType>.unspecifiedTypeId(): Int? =
    firstOrNull { it.name.equals("unspecified", ignoreCase = true) }?.id

internal fun blocksOverlap(start: Int, end: Int, otherStart: Int, otherEnd: Int): Boolean =
    start < otherEnd && otherStart < end

internal fun planningRangeAvailable(
    state: DayUiState,
    date: LocalDate,
    taskId: Int,
    startMinute: Int,
    endMinute: Int,
): Boolean {
    val day = state.page(date).day ?: return false
    if (startMinute < day.visibleStart || endMinute > day.visibleEnd || startMinute >= endMinute) return false
    if (day.lane(Lane.Planned).any {
            blocksOverlap(startMinute, endMinute, it.startMinute, it.endMinute)
        }
    ) return false
    return state.planningDrafts.values.none {
        it.date == date && it.taskId != taskId &&
            blocksOverlap(startMinute, endMinute, it.startMinute, it.endMinute)
    }
}

private inline fun DayUiState.withPage(
    date: LocalDate,
    transform: (DayPageState) -> DayPageState,
): DayUiState = copy(pages = pages + (date to transform(page(date))))
