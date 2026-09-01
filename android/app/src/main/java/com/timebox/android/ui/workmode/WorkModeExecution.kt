package com.timebox.android.ui.workmode

import com.timebox.android.data.ActualBlock
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.WorkModeSnapshot
import com.timebox.android.data.apiError
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkModeSession(
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

data class WorkModeExecutionState(
    val session: WorkModeSession? = null,
    val visible: Boolean = false,
    val entryWarning: Boolean = false,
    val restorePrompt: Boolean = false,
    val day: Day? = null,
    val notice: String? = null,
) {
    val activeActualAvailable: Boolean get() = session?.activeActual != null
}

interface WorkModeTransport {
    suspend fun getDay(date: LocalDate): Result<Day>
    suspend fun getActiveActual(): Result<ActualBlock?>
    suspend fun listTasks(): Result<List<BattleTask>>
    suspend fun startActual(plannedBlockId: Int, startAt: Instant): Result<ActualBlock>
    suspend fun createActual(plannedBlockId: Int, startAt: Instant, endAt: Instant): Result<ActualBlock>
    suspend fun endActual(actualBlockId: Int, endAt: Instant): Result<ActualBlock>
    suspend fun setSubtask(subtask: Subtask, checked: Boolean): Result<Subtask>
}

class RepositoryWorkModeTransport(private val repository: TimeboxRepository) : WorkModeTransport {
    override suspend fun getDay(date: LocalDate) = repository.getDay(date)
    override suspend fun getActiveActual() = repository.getActiveActualBlock()
    override suspend fun listTasks() = repository.listBattleTasks().map { it.items }
    override suspend fun startActual(plannedBlockId: Int, startAt: Instant) =
        repository.startActualBlock(plannedBlockId = plannedBlockId, startAt = startAt)
    override suspend fun createActual(plannedBlockId: Int, startAt: Instant, endAt: Instant) =
        repository.createActualBlock(startAt = startAt, endAt = endAt, plannedBlockId = plannedBlockId)
    override suspend fun endActual(actualBlockId: Int, endAt: Instant) =
        repository.patchActualBlock(actualBlockId, endAt = endAt)
    override suspend fun setSubtask(subtask: Subtask, checked: Boolean) =
        if (checked) repository.checkSubtask(subtask.id) else repository.uncheckSubtask(subtask.id)
}

interface WorkModePersistence {
    suspend fun load(): WorkModeSnapshot?
    suspend fun save(snapshot: WorkModeSnapshot?)
}

class RepositoryWorkModePersistence(private val repository: TimeboxRepository) : WorkModePersistence {
    override suspend fun load(): WorkModeSnapshot? = repository.workMode.first()
    override suspend fun save(snapshot: WorkModeSnapshot?) = repository.setWorkMode(snapshot)
}

/**
 * Owns the complete Work Mode lifecycle. Callers provide a day at entry/recovery and
 * thereafter observe one coherent state; ticking, Actual Block coordination and durable
 * recovery remain implementation details behind this interface.
 */
class WorkModeExecution(
    private val transport: WorkModeTransport,
    private val persistence: WorkModePersistence,
    private val scope: CoroutineScope,
    private val clock: () -> Instant = Instant::now,
    private val tickMillis: Long = 1_000L,
) {
    private val _state = MutableStateFlow(WorkModeExecutionState())
    val state: StateFlow<WorkModeExecutionState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var restoreChecked = false

    suspend fun begin(day: Day, force: Boolean = false) {
        if (_state.value.session != null) {
            _state.update { it.copy(visible = true) }
            return
        }
        val now = clock()
        val active = transport.getActiveActual().getOrNull()
        if (active != null) {
            enter(day, active.startAt, active)
            return
        }
        val (current, next) = selection(day, now)
        val near = current != null || next?.let { it.startMinute - minuteOfDay(now, day.timezone) <= 10 } == true
        if (!force && !near) {
            _state.update { it.copy(day = day, entryWarning = true, notice = null) }
            return
        }
        enter(day, now)
    }

    suspend fun resume(day: Day, active: ActualBlock) = enter(day, active.startAt, active)

    suspend fun continueEntry(day: Day) {
        _state.update { it.copy(entryWarning = false) }
        enter(day, clock())
    }

    suspend fun restore(day: Day) {
        if (restoreChecked) return
        restoreChecked = true
        val snapshot = persistence.load() ?: return
        val now = clock()
        val active = transport.getActiveActual().getOrNull()
        val (clockCurrent, next) = selection(day, now)
        val current = snapshot.confirmingPlannedBlockId?.let { id ->
            day.lane(Lane.Planned).firstOrNull { it.id == id }
        } ?: clockCurrent
        val taskId = current?.taskId ?: active?.taskId
        val tasks = transport.listTasks().getOrNull()
        val session = snapshot.toSession(
            current,
            next,
            taskId?.let { tasks?.findBattleTask(it) },
            day.timezone,
            active,
        )
        val absentTooLong = now.isAfter(session.lastObservedAt.plusSeconds(10 * 60))
        _state.value = WorkModeExecutionState(session, true, restorePrompt = absentTooLong, day = day)
        if (!absentTooLong) startTicker()
    }

    fun show() = _state.update { if (it.session == null) it else it.copy(visible = true) }
    fun hide() = _state.update { it.copy(visible = false) }
    fun clearEntryWarning() = _state.update { it.copy(entryWarning = false) }
    fun consumeNotice() = _state.update { it.copy(notice = null) }

    fun toggleSubtask(subtask: Subtask) {
        val session = _state.value.session ?: return
        if (session.saving || session.task?.status == TaskStatus.Completed) return
        _state.update { it.copy(session = session.copy(saving = true, error = null)) }
        scope.launch {
            transport.setSubtask(subtask, !subtask.checked).fold(
                onSuccess = {
                    val tasks = transport.listTasks().getOrNull()
                    val task = session.task?.id?.let { tasks?.findBattleTask(it) }
                    updateSession(session.copy(task = task, saving = false))
                },
                onFailure = { updateSession(session.copy(saving = false, error = it.apiError.message)) },
            )
        }
    }

    suspend fun exit(): Boolean {
        val session = _state.value.session ?: return false
        if (session.saving) return false
        updateSession(session.copy(saving = true, error = null))
        session.activeActual?.let { actual ->
            transport.endActual(actual.id, clock()).getOrElse {
                updateSession(session.copy(saving = false, error = it.apiError.message))
                return false
            }
        }
        persistence.save(null)
        ticker?.cancel()
        _state.value = WorkModeExecutionState(notice = "Actual time preserved · Task remains open")
        return true
    }

    fun continueAfterAbsence() {
        val original = _state.value.session ?: return
        val day = _state.value.day ?: return
        updateSession(original.copy(saving = true, error = null))
        scope.launch {
            val now = clock()
            var resumed = original
            var endedActivePlannedBlockId: Int? = null
            if (resumed.activeActual != null && resumed.activePlannedEndAt != null && !now.isBefore(resumed.activePlannedEndAt)) {
                endedActivePlannedBlockId = resumed.activeActual.plannedBlockId
                transport.endActual(resumed.activeActual.id, resumed.activePlannedEndAt).getOrElse {
                    updateSession(original.copy(saving = false, error = it.apiError.message)); return@launch
                }
                resumed = resumed.copy(activeActual = null, activePlannedEndAt = null)
            }
            for (block in day.lane(Lane.Planned).sortedBy { it.startMinute }) {
                if (block.id == resumed.activeActual?.plannedBlockId || block.id == endedActivePlannedBlockId) continue
                val blockStart = blockInstant(day, block.startMinute)
                val blockEnd = blockInstant(day, block.endMinute)
                val start = maxOf(blockStart, original.lastConfirmedAt, original.entryAt)
                val end = minOf(blockEnd, now)
                if (!end.isAfter(start)) continue
                if (!blockEnd.isAfter(now)) {
                    transport.createActual(block.id, start, end).getOrElse {
                        updateSession(original.copy(saving = false, error = it.apiError.message)); return@launch
                    }
                } else {
                    val actual = transport.startActual(block.id, start).getOrElse {
                        updateSession(original.copy(saving = false, error = it.apiError.message)); return@launch
                    }
                    resumed = resumed.copy(activeActual = actual, activePlannedEndAt = blockEnd)
                }
            }
            val (current, next) = selection(day, now)
            resumed = resumed.copy(
                currentBlock = current,
                nextBlock = next,
                lastConfirmedAt = now,
                lastObservedAt = now,
                confirmingPlannedBlockId = null,
                confirmationStartedAt = null,
                saving = false,
            )
            _state.update { it.copy(session = resumed, restorePrompt = false) }
            persist(resumed)
            startTicker()
        }
    }

    fun declineAfterAbsence() {
        val session = _state.value.session ?: return
        scope.launch {
            session.activeActual?.let { transport.endActual(it.id, session.lastConfirmedAt).getOrNull() }
            persistence.save(null)
            ticker?.cancel()
            _state.value = WorkModeExecutionState()
        }
    }

    private suspend fun enter(day: Day, entryAt: Instant, active: ActualBlock? = null) {
        val now = clock()
        val (clockCurrent, clockNext) = selection(day, now)
        val current = active?.let { activeBlock(day, it, now) } ?: clockCurrent
        val next = if (active == null) clockNext else day.lane(Lane.Planned).sortedBy { it.startMinute }
            .firstOrNull { it.startMinute >= (current?.endMinute ?: minuteOfDay(now, day.timezone)) && it.id != current?.id }
        val tasks = transport.listTasks().getOrNull()
        val taskId = current?.taskId ?: active?.taskId
        val activeEnd = active?.plannedBlockId?.let { id ->
            day.lane(Lane.Planned).firstOrNull { it.id == id }?.let { blockInstant(day, it.endMinute) }
        }
        val session = WorkModeSession(
            entryAt, now, now, current, next, taskId?.let { tasks?.findBattleTask(it) },
            day.timezone, active, activePlannedEndAt = activeEnd,
        )
        _state.value = WorkModeExecutionState(session, visible = true, day = day)
        persist(session)
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (_state.value.session != null && !_state.value.restorePrompt) {
                reconcile()
                delay(tickMillis)
            }
        }
    }

    internal suspend fun reconcile() {
        var session = _state.value.session ?: return
        var day = _state.value.day ?: return
        val now = clock()

        if (session.activeActual == null && session.confirmingPlannedBlockId != null && session.confirmationStartedAt != null) {
            val confirmed = session.currentBlock?.takeIf { it.id == session.confirmingPlannedBlockId }
            if (confirmed != null) {
                val startAt = maxOf(session.entryAt, blockInstant(day, confirmed.startMinute))
                val endAt = blockInstant(day, confirmed.endMinute)
                val confirmedThrough = minOf(now, endAt)
                val fullMinute = !confirmedThrough.isBefore(session.confirmationStartedAt.plusSeconds(60))
                session = when {
                    !fullMinute && !now.isBefore(endAt) -> session.copy(currentBlock = null, confirmingPlannedBlockId = null, confirmationStartedAt = null)
                    fullMinute && !now.isBefore(endAt) -> {
                        transport.createActual(confirmed.id, startAt, endAt).getOrElse { return fail(session, it) }
                        session.copy(currentBlock = null, confirmingPlannedBlockId = null, confirmationStartedAt = null, lastConfirmedAt = endAt)
                    }
                    fullMinute -> {
                        val actual = transport.startActual(confirmed.id, startAt).getOrElse { return fail(session, it) }
                        session.copy(activeActual = actual, activePlannedEndAt = endAt, confirmingPlannedBlockId = null, confirmationStartedAt = null, lastConfirmedAt = now)
                    }
                    else -> session
                }
            }
        }

        val today = now.atZone(ZoneId.of(day.timezone)).toLocalDate()
        if (day.date != today) {
            day = transport.getDay(today).getOrElse { return fail(session, it) }
            _state.update { it.copy(day = day) }
        }
        if (session.activeActual != null && session.activePlannedEndAt != null && !now.isBefore(session.activePlannedEndAt)) {
            transport.endActual(session.activeActual.id, session.activePlannedEndAt).getOrElse { return fail(session, it) }
            session = session.copy(activeActual = null, activePlannedEndAt = null, lastConfirmedAt = session.activePlannedEndAt)
        }

        val (current, next) = selection(day, now)
        session = when {
            session.activeActual != null -> {
                val active = activeBlock(day, session.activeActual, now)
                session.copy(
                    currentBlock = active,
                    nextBlock = day.lane(Lane.Planned).sortedBy { it.startMinute }.firstOrNull { it.startMinute >= active.endMinute && it.id != active.id },
                    confirmingPlannedBlockId = null, confirmationStartedAt = null,
                    lastConfirmedAt = now, lastObservedAt = now,
                )
            }
            current == null -> session.copy(currentBlock = null, nextBlock = next, task = null, confirmingPlannedBlockId = null, confirmationStartedAt = null, lastObservedAt = now)
            session.confirmingPlannedBlockId != current.id || session.confirmationStartedAt == null -> session.copy(
                currentBlock = current, nextBlock = next, confirmingPlannedBlockId = current.id,
                confirmationStartedAt = maxOf(session.entryAt, blockInstant(day, current.startMinute)), lastObservedAt = now,
            )
            !now.isBefore(session.confirmationStartedAt.plusSeconds(60)) -> {
                val startAt = maxOf(session.entryAt, blockInstant(day, current.startMinute))
                val actual = transport.startActual(current.id, startAt).getOrElse { cause ->
                    transport.getActiveActual().getOrNull() ?: return fail(session, cause)
                }
                session.copy(currentBlock = current, nextBlock = next, activeActual = actual,
                    activePlannedEndAt = blockInstant(day, current.endMinute), confirmingPlannedBlockId = null,
                    confirmationStartedAt = null, lastConfirmedAt = now, lastObservedAt = now)
            }
            else -> session.copy(currentBlock = current, nextBlock = next, lastObservedAt = now)
        }
        val taskId = session.currentBlock?.taskId
        if (taskId != session.task?.id) {
            val tasks = transport.listTasks().getOrNull()
            session = session.copy(task = taskId?.let { tasks?.findBattleTask(it) })
        }
        _state.update { it.copy(session = session, day = day) }
        persist(session)
    }

    private fun fail(session: WorkModeSession, cause: Throwable) {
        updateSession(session.copy(saving = false, error = cause.apiError.message))
    }

    private fun updateSession(session: WorkModeSession) = _state.update { it.copy(session = session) }
    private suspend fun persist(session: WorkModeSession) = persistence.save(session.toSnapshot())

    private fun selection(day: Day, now: Instant): Pair<TimeBlock?, TimeBlock?> {
        val minute = minuteOfDay(now, day.timezone)
        val planned = day.lane(Lane.Planned).sortedBy { it.startMinute }
        return planned.firstOrNull { minute >= it.startMinute && minute < it.endMinute } to
            planned.firstOrNull { it.startMinute > minute }
    }

    private fun activeBlock(day: Day, actual: ActualBlock, now: Instant): TimeBlock {
        day.lane(Lane.Planned).firstOrNull { it.id == actual.plannedBlockId }?.let { return it }
        val start = minuteOfDay(actual.startAt, day.timezone)
        return TimeBlock(actual.id, Lane.Actual, actual.taskTypeId, actual.taskTypeName, actual.taskId,
            actual.task, actual.note, actual.plannedBlockId, actual.id, start, maxOf(start + 1, minuteOfDay(now, day.timezone)))
    }
}

private fun minuteOfDay(instant: Instant, timezone: String): Int {
    val local = instant.atZone(ZoneId.of(timezone))
    return local.hour * 60 + local.minute
}

private fun blockInstant(day: Day, minute: Int): Instant =
    day.date.atStartOfDay(ZoneId.of(day.timezone)).plusMinutes(minute.toLong()).toInstant()

private fun List<BattleTask>.findBattleTask(id: Int): BattleTask? =
    firstNotNullOfOrNull { task -> task.takeIf { it.id == id } ?: task.sessionTasks.findBattleTask(id) }

private fun WorkModeSnapshot.toSession(current: TimeBlock?, next: TimeBlock?, task: BattleTask?, timezone: String, actual: ActualBlock?) =
    WorkModeSession(
        Instant.parse(entryAt), Instant.parse(lastConfirmedAt), Instant.parse(lastObservedAt),
        current, next, task, timezone, actual, confirmingPlannedBlockId,
        confirmationStartedAt?.let(Instant::parse), activePlannedEndAt?.let(Instant::parse),
    )

private fun WorkModeSession.toSnapshot() = WorkModeSnapshot(
    entryAt.toString(), lastConfirmedAt.toString(), lastObservedAt.toString(), confirmingPlannedBlockId,
    confirmationStartedAt?.toString(), activeActual?.id, activeActual?.plannedBlockId, activePlannedEndAt?.toString(),
)
