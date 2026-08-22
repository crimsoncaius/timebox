package com.timebox.android.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.Day
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Lane
import com.timebox.android.data.PlanningCommitPlacement
import com.timebox.android.data.SLOT_MINUTES
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    /** True once an actual block already mirrors the selected planned block. */
    val selectedAlreadyCompleted: Boolean
        get() {
            val sel = selectedBlock ?: return false
            if (sel.lane != Lane.Planned) return false
            return day?.blocks.orEmpty().any {
                it.lane == Lane.Actual && it.plannedBlockId == sel.id
            }
        }

    val accessibilityPlanningTask: BattleTask?
        get() = readyTasks.firstOrNull { it.id == accessibilityPlanningTaskId }

    fun planningDrafts(date: LocalDate): List<PlanningDraftPlacement> =
        planningDrafts.values.filter { it.date == date }
}

class DayViewModel(private val repository: TimeboxRepository) : ViewModel() {

    private val _state = MutableStateFlow(DayUiState())
    val state: StateFlow<DayUiState> = _state.asStateFlow()

    private var typesLoaded = false
    private var todayResolved = false
    private val pageRequestVersions = mutableMapOf<LocalDate, Int>()

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
        if (todayResolved) {
            load()
            return
        }
        viewModelScope.launch {
            val today = repository.getDaySummary(_state.value.date).getOrNull()?.today
            todayResolved = today != null
            if (today != null) _state.update { it.copy(today = today) }
            load(today ?: _state.value.date)
        }
    }

    /** Battle Plan is independent of the timeline: failure leaves Day fully usable. */
    fun refreshReadyToPlan() {
        _state.update { it.copy(readyTasksLoading = it.readyTasks.isEmpty(), readyTasksError = null) }
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch { loadTaskTypes() }
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
        val current = _state.value
        val selected = current.selectedBlock
        // The note field saves on dismiss rather than on every keystroke.
        if (selected != null && current.noteInput != selected.note.orEmpty()) {
            saveNote(selected.id, current.noteInput)
        }
        _state.update { it.copy(selectedBlockId = null, draft = null, noteInput = "", typeQuery = "") }
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
        viewModelScope.launch {
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
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.createBlock(date, lane, taskTypeId, start, end, note.ifBlank { null }, taskId).fold(
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
                            message = "Block created",
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    private fun saveNote(blockId: Int, note: String) {
        val date = _state.value.date
        viewModelScope.launch {
            repository.patchBlock(date, blockId, note = note).onSuccess { day ->
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
        mutate(current.date, "Block deleted") {
            repository.deleteBlock(current.date, selected.id)
        }
    }

    fun completeSelected() {
        val current = _state.value
        val selected = current.selectedBlock ?: return
        if (selected.lane != Lane.Planned) return
        _state.update { it.copy(selectedBlockId = null) }
        mutate(current.date, "Copied to actual") {
            repository.completeAsPlanned(current.date, selected.id)
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
        viewModelScope.launch {
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

internal fun List<BattleTask>.readyToPlanTasks(): List<BattleTask> =
    flatMap { listOf(it) + it.subtasks }.filter { it.readyToPlan }

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
