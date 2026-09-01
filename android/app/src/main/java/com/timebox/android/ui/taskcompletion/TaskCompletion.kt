package com.timebox.android.ui.taskcompletion

import com.timebox.android.data.BattleTask
import com.timebox.android.data.TaskCompletionResult
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.data.BattleTaskPatch
import com.timebox.android.data.remote.PatchField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single user-facing notice for a Battle Plan Task lifecycle operation.
 *
 * [id] is deliberately opaque. Callers pass it back when dismissing or undoing so a
 * stale snackbar can never consume a newer completion's undo opportunity.
 */
data class TaskCompletionNotice(
    val id: Long,
    val message: String,
    val canUndo: Boolean,
)

/** Internal seam between task-completion policy and its transport. */
internal interface TaskCompletionTransport {
    suspend fun complete(taskId: Int): Result<TaskCompletionResult>
    suspend fun reopen(taskId: Int): Result<BattleTask>
    suspend fun setStatus(taskId: Int, status: TaskStatus): Result<BattleTask>
    suspend fun undo(taskId: Int, token: String): Result<BattleTask>
}

/** Production adapter; tests use an in-memory adapter at the same seam. */
internal class RepositoryTaskCompletionTransport(
    private val repository: TimeboxRepository,
) : TaskCompletionTransport {
    override suspend fun complete(taskId: Int) = repository.completeBattleTask(taskId)
    override suspend fun reopen(taskId: Int) = repository.reopenBattleTask(taskId)
    override suspend fun setStatus(taskId: Int, status: TaskStatus) =
        repository.patchBattleTask(taskId, BattleTaskPatch(status = PatchField.of(status)))
    override suspend fun undo(taskId: Int, token: String) =
        repository.undoBattleTaskCompletion(taskId, token)
}

/**
 * Owns the complete/reopen/undo lifecycle for Battle Plan Tasks.
 *
 * Operations are serialized. A new lifecycle operation supersedes any older pending
 * undo, while a failed undo restores that exact opportunity with a fresh notice id.
 * The transport token never crosses this module's external interface.
 *
 * Task Completion remains independent of Actual Block recording and Subtask checks;
 * neither capability exists at this module's transport seam.
 */
class TaskCompletion internal constructor(
    private val transport: TaskCompletionTransport,
) {
    private data class PendingUndo(val taskId: Int, val token: String)

    private val operationMutex = Mutex()
    private val _notice = MutableStateFlow<TaskCompletionNotice?>(null)
    val notice: StateFlow<TaskCompletionNotice?> = _notice.asStateFlow()

    private var nextNoticeId = 1L
    private var pendingUndo: PendingUndo? = null

    suspend fun transition(
        taskId: Int,
        from: TaskStatus,
        to: TaskStatus,
    ): Result<BattleTask> = operationMutex.withLock {
        if (from == to || (from != TaskStatus.Completed && to != TaskStatus.Completed)) {
            return@withLock Result.failure(
                IllegalArgumentException("Task Completion only changes transitions into or out of Completed."),
            )
        }
        clearNoticeAndUndo()
        if (to == TaskStatus.Completed) complete(taskId) else reopen(taskId, to)
    }

    private suspend fun complete(taskId: Int): Result<BattleTask> =
        transport.complete(taskId).fold(
            onSuccess = { result ->
                pendingUndo = PendingUndo(taskId, result.undoToken)
                publish(
                    message = completionMessage(result.removedPlannedBlockIds.size),
                    canUndo = true,
                )
                Result.success(result.task)
            },
            onFailure = { failure ->
                publish(failure.apiError.message, canUndo = false)
                Result.failure(failure)
            },
        )

    private suspend fun reopen(taskId: Int, target: TaskStatus): Result<BattleTask> =
        transport.reopen(taskId).fold(
            onSuccess = { reopened ->
                val final = if (target == TaskStatus.Open) {
                    Result.success(reopened)
                } else {
                    transport.setStatus(taskId, target)
                }
                final.onSuccess { publish("Task reopened", canUndo = false) }
                    .onFailure { publish(it.apiError.message, canUndo = false) }
            },
            onFailure = { failure ->
                publish(failure.apiError.message, canUndo = false)
                Result.failure(failure)
            },
        )

    suspend fun undo(noticeId: Long): Result<BattleTask> = operationMutex.withLock {
        val currentNotice = _notice.value
        val undo = pendingUndo
        if (currentNotice?.id != noticeId || !currentNotice.canUndo || undo == null) {
            return@withLock Result.failure(IllegalStateException("Task completion is no longer available to undo."))
        }

        clearNoticeAndUndo()
        transport.undo(undo.taskId, undo.token).fold(
            onSuccess = { task ->
                publish("Task completion undone", canUndo = false)
                Result.success(task)
            },
            onFailure = { failure ->
                pendingUndo = undo
                publish(failure.apiError.message, canUndo = true)
                Result.failure(failure)
            },
        )
    }

    suspend fun dismiss(noticeId: Long) = operationMutex.withLock {
        if (_notice.value?.id == noticeId) clearNoticeAndUndo()
    }

    private fun publish(message: String, canUndo: Boolean) {
        _notice.value = TaskCompletionNotice(
            id = nextNoticeId++,
            message = message,
            canUndo = canUndo,
        )
    }

    private fun clearNoticeAndUndo() {
        _notice.value = null
        pendingUndo = null
    }
}

private fun completionMessage(removed: Int): String =
    if (removed == 0) {
        "Task completed"
    } else {
        "Task completed · $removed future Planned ${if (removed == 1) "Block" else "Blocks"} removed"
    }
