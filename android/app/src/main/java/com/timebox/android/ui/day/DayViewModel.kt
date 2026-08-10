package com.timebox.android.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
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
)

data class DayUiState(
    /** The device's date until the backend's is known; see [DayViewModel.start]. */
    val date: LocalDate = LocalDate.now(),
    val day: Day? = null,
    val taskTypes: List<TaskType> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val selectedBlockId: Int? = null,
    val draft: Draft? = null,
    val noteInput: String = "",
    /** Raw text in the task type picker; cleared whenever the sheet changes what it shows. */
    val typeQuery: String = "",
) {
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
}

class DayViewModel(private val repository: TimeboxRepository) : ViewModel() {

    private val _state = MutableStateFlow(DayUiState())
    val state: StateFlow<DayUiState> = _state.asStateFlow()

    private var typesLoaded = false
    private var todayResolved = false

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
            load(today ?: _state.value.date)
        }
    }

    fun load(date: LocalDate = _state.value.date, showSpinner: Boolean = true) {
        _state.update { it.copy(date = date, loading = showSpinner && it.day == null, error = null) }
        viewModelScope.launch {
            repository.getDay(date).fold(
                onSuccess = { day ->
                    _state.update { it.copy(day = day, loading = false, error = null) }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.apiError.message) }
                },
            )
            if (!typesLoaded) loadTaskTypes()
        }
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
        _state.update { it.copy(date = date, selectedBlockId = null, draft = null) }
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
        _state.update {
            it.copy(
                draft = Draft(lane, start, start + SLOT_MINUTES),
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
            createBlock(draft.lane, draft.startMinute, draft.endMinute, taskType.id, current.noteInput)
            return
        }
        val selected = current.selectedBlock ?: return
        if (selected.taskTypeId == taskType.id) return
        mutate("Saved") { repository.patchBlock(current.date, selected.id, taskTypeId = taskType.id) }
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

    private fun createBlock(lane: Lane, start: Int, end: Int, taskTypeId: Int, note: String) {
        val date = _state.value.date
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.createBlock(date, lane, taskTypeId, start, end, note.ifBlank { null }).fold(
                onSuccess = { day ->
                    // Picking the type is the whole draft flow, so the sheet is done: leaving
                    // it open on the fresh block would just hide the timeline it landed on.
                    _state.update {
                        it.copy(
                            day = day,
                            saving = false,
                            draft = null,
                            selectedBlockId = null,
                            noteInput = "",
                            typeQuery = "",
                            message = "Block created",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.apiError.message) }
                },
            )
        }
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

        _state.update {
            it.copy(day = it.day?.withBlockTimes(blockId, startMinute, endMinute), saving = true)
        }
        viewModelScope.launch {
            repository.patchBlock(
                current.date,
                blockId,
                startMinute = startMinute,
                endMinute = endMinute,
            ).fold(
                onSuccess = { day -> _state.update { it.copy(day = day, saving = false) } },
                onFailure = { e ->
                    // Roll back this block alone rather than the whole day: another
                    // request (a note save, say) may have landed a fresh day meanwhile.
                    _state.update {
                        it.copy(
                            day = it.day?.withBlockTimes(
                                blockId,
                                block.startMinute,
                                block.endMinute,
                            ),
                            saving = false,
                            message = e.apiError.message,
                        )
                    }
                },
            )
        }
    }

    private fun saveNote(blockId: Int, note: String) {
        val date = _state.value.date
        viewModelScope.launch {
            repository.patchBlock(date, blockId, note = note).onSuccess { day ->
                _state.update { it.copy(day = day) }
            }
        }
    }

    fun deleteSelected() {
        val current = _state.value
        val selected = current.selectedBlock ?: return
        _state.update { it.copy(selectedBlockId = null) }
        mutate("Block deleted") { repository.deleteBlock(current.date, selected.id) }
    }

    fun completeSelected() {
        val current = _state.value
        val selected = current.selectedBlock ?: return
        if (selected.lane != Lane.Planned) return
        _state.update { it.copy(selectedBlockId = null) }
        mutate("Copied to actual") { repository.completeAsPlanned(current.date, selected.id) }
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

    private fun mutate(successMessage: String?, block: suspend () -> Result<Day>) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            block().fold(
                onSuccess = { day ->
                    _state.update {
                        it.copy(day = day, saving = false, message = successMessage)
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.apiError.message) }
                },
            )
        }
    }
}
