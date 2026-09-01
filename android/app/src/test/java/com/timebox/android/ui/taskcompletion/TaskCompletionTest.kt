package com.timebox.android.ui.taskcompletion

import com.timebox.android.data.ApiError
import com.timebox.android.data.ApiErrorException
import com.timebox.android.data.BattleTask
import com.timebox.android.data.TaskCompletionResult
import com.timebox.android.data.TaskStatus
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCompletionTest {
    @Test
    fun `complete hides transport token and describes removed Planned Blocks`() = runTest {
        val transport = InMemoryTaskCompletionTransport(
            removedPlannedBlockIds = listOf(41, 42),
        )
        val completion = TaskCompletion(transport)

        val task = completion.transition(7, TaskStatus.Open, TaskStatus.Completed).getOrThrow()

        assertEquals(TaskStatus.Completed, task.status)
        assertEquals(
            TaskCompletionNotice(
                id = 1,
                message = "Task completed · 2 future Planned Blocks removed",
                canUndo = true,
            ),
            completion.notice.value,
        )
        assertEquals(listOf("complete:7"), transport.calls)
    }

    @Test
    fun `dismiss consumes the pending undo identity`() = runTest {
        val transport = InMemoryTaskCompletionTransport()
        val completion = TaskCompletion(transport)
        completion.transition(7, TaskStatus.Open, TaskStatus.Completed).getOrThrow()
        val noticeId = completion.notice.value!!.id

        completion.dismiss(noticeId)

        assertNull(completion.notice.value)
        assertTrue(completion.undo(noticeId).isFailure)
        assertEquals(listOf("complete:7"), transport.calls)
    }

    @Test
    fun `undo success uses the private token once and publishes completion`() = runTest {
        val transport = InMemoryTaskCompletionTransport()
        val completion = TaskCompletion(transport)
        completion.transition(7, TaskStatus.Open, TaskStatus.Completed).getOrThrow()
        val noticeId = completion.notice.value!!.id

        val task = completion.undo(noticeId).getOrThrow()

        assertEquals(TaskStatus.Open, task.status)
        assertEquals("Task completion undone", completion.notice.value?.message)
        assertFalse(completion.notice.value!!.canUndo)
        assertEquals(listOf("complete:7", "undo:7:token-7"), transport.calls)
    }

    @Test
    fun `undo failure restores availability under a fresh notice identity`() = runTest {
        val transport = InMemoryTaskCompletionTransport().apply {
            undoFailure = ApiErrorException(ApiError("Undo is temporarily unavailable"))
        }
        val completion = TaskCompletion(transport)
        completion.transition(7, TaskStatus.Open, TaskStatus.Completed).getOrThrow()
        val firstNoticeId = completion.notice.value!!.id

        assertTrue(completion.undo(firstNoticeId).isFailure)

        val retryNotice = completion.notice.value!!
        assertNotEquals(firstNoticeId, retryNotice.id)
        assertEquals("Undo is temporarily unavailable", retryNotice.message)
        assertTrue(retryNotice.canUndo)

        transport.undoFailure = null
        completion.undo(retryNotice.id).getOrThrow()
        assertEquals(
            listOf("complete:7", "undo:7:token-7", "undo:7:token-7"),
            transport.calls,
        )
    }

    @Test
    fun `reopen supersedes pending undo and publishes one lifecycle notice`() = runTest {
        val transport = InMemoryTaskCompletionTransport()
        val completion = TaskCompletion(transport)
        completion.transition(7, TaskStatus.Open, TaskStatus.Completed).getOrThrow()
        val completionNoticeId = completion.notice.value!!.id

        val task = completion.transition(7, TaskStatus.Completed, TaskStatus.InProgress).getOrThrow()

        assertEquals(TaskStatus.InProgress, task.status)
        assertEquals("Task reopened", completion.notice.value?.message)
        assertFalse(completion.notice.value!!.canUndo)
        assertTrue(completion.undo(completionNoticeId).isFailure)
        assertEquals(listOf("complete:7", "reopen:7", "status:7:in_progress"), transport.calls)
    }

    @Test
    fun `reopen reports a later status failure without exposing its ordering`() = runTest {
        val transport = InMemoryTaskCompletionTransport().apply {
            setStatusFailure = ApiErrorException(ApiError("Could not move reopened Task"))
        }
        val completion = TaskCompletion(transport)

        val result = completion.transition(7, TaskStatus.Completed, TaskStatus.InProgress)

        assertTrue(result.isFailure)
        assertEquals("Could not move reopened Task", completion.notice.value?.message)
        assertFalse(completion.notice.value!!.canUndo)
        assertEquals(listOf("reopen:7", "status:7:in_progress"), transport.calls)
    }
}

private class InMemoryTaskCompletionTransport(
    private val removedPlannedBlockIds: List<Int> = emptyList(),
) : TaskCompletionTransport {
    val calls = mutableListOf<String>()
    var undoFailure: Throwable? = null
    var setStatusFailure: Throwable? = null

    override suspend fun complete(taskId: Int): Result<TaskCompletionResult> {
        calls += "complete:$taskId"
        return Result.success(
            TaskCompletionResult(
                task = task(taskId, TaskStatus.Completed),
                undoToken = "token-$taskId",
                removedPlannedBlockIds = removedPlannedBlockIds,
            )
        )
    }

    override suspend fun reopen(taskId: Int): Result<BattleTask> {
        calls += "reopen:$taskId"
        return Result.success(task(taskId, TaskStatus.Open))
    }

    override suspend fun setStatus(taskId: Int, status: TaskStatus): Result<BattleTask> {
        calls += "status:$taskId:${status.wire}"
        return setStatusFailure?.let(Result.Companion::failure)
            ?: Result.success(task(taskId, status))
    }

    override suspend fun undo(taskId: Int, token: String): Result<BattleTask> {
        calls += "undo:$taskId:$token"
        return undoFailure?.let(Result.Companion::failure)
            ?: Result.success(task(taskId, TaskStatus.Open))
    }
}

private fun task(id: Int, status: TaskStatus) = BattleTask(
    id = id,
    parentId = null,
    parentTitle = null,
    projectId = null,
    project = null,
    taskTypeId = null,
    taskType = null,
    recurringTemplateId = null,
    recurringTemplateTitle = null,
    occurrenceKey = null,
    recurrenceKind = null,
    quotaPeriodStart = null,
    quotaPeriodEnd = null,
    expectedSessions = null,
    sessionIndex = null,
    quotaCompleted = null,
    title = "Task $id",
    description = "",
    readyToPlan = false,
    status = status,
    urgency = null,
    importance = null,
    deadlineDate = null,
    deadlineAt = null,
    reminderAt = null,
    reminderDeliveredAt = null,
    position = 0,
    archivedAt = null,
    deletedAt = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    overdue = false,
    subtasks = emptyList(),
)
