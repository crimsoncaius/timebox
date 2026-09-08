package com.timebox.android.ui.planning

import com.timebox.android.data.BattleTask
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.BattleTaskDto
import com.timebox.android.data.remote.BattleTaskListDto
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.ui.battleplan.task
import com.timebox.android.ui.day.DayViewModel
import com.timebox.android.ui.readiness.ReadyToPlanCoordinator
import com.timebox.android.ui.readiness.ReadyToPlanTransport
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryPlanningSessionTransportTest {
    @Test
    fun `Day queue excludes a recurring occurrence cached by another surface`() = runTest {
        val coordinator = ReadyToPlanCoordinator(UnusedReadyToPlanTransport, this)
        coordinator.mergeServerTasks(
            listOf(
                task(99, ready = true).copy(
                    recurringTemplateId = 7,
                    occurrenceKey = "2026-09-09",
                ),
            ),
        )
        val scopedSessionTask = taskDto(id = 2, ready = true)
        val scopedParent = taskDto(
            id = 1,
            ready = false,
            sessionTasks = listOf(scopedSessionTask),
        )
        val repository = TimeboxRepository(ScopedTasksApi(listOf(scopedParent)).proxy())
        val transport = RepositoryPlanningSessionTransport(repository, coordinator)
        val session = PlanningSession(transport)

        session.refreshQueue(LocalDate.parse("2026-09-08"))

        assertEquals(listOf(2), session.state.value.readyTasks.map(BattleTask::id))
    }

    @Test
    fun `later coordinator projection stays date scoped and includes its pending addition`() = runTest {
        val readinessTransport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(readinessTransport, this)
        coordinator.mergeServerTasks(
            listOf(
                task(99, ready = true).copy(
                    recurringTemplateId = 7,
                    occurrenceKey = "2026-09-09",
                ),
            ),
        )
        val scopedTasks = listOf(
            taskDto(
                id = 1,
                ready = false,
                sessionTasks = listOf(taskDto(id = 2, ready = true)),
            ),
            taskDto(id = 3, ready = false),
        )
        val repository = TimeboxRepository(ScopedTasksApi(scopedTasks).proxy())
        val planningSession = PlanningSession(RepositoryPlanningSessionTransport(repository, coordinator))
        val viewModel = DayViewModel(
            repository = repository,
            taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository)),
            planningSession = planningSession,
            injectedScope = this,
            readinessCoordinator = coordinator,
        )

        viewModel.refreshReadyToPlan()
        planningSession.state.first { !it.queueLoading }
        runCurrent()
        assertEquals(listOf(2), planningSession.state.value.readyTasks.map(BattleTask::id))
        assertEquals(listOf(2), viewModel.state.value.readyTasks.map(BattleTask::id))

        coordinator.setReady(task(3, ready = false), true)
        runCurrent()

        assertEquals(listOf(2, 3), viewModel.state.value.readyTasks.map(BattleTask::id))
        assertTrue(viewModel.state.value.readyTasks.single { it.id == 3 }.readinessPending)
        planningSession.begin()
        planningSession.toggleSelection(3)
        assertNull(planningSession.state.value.selectedTaskId)

        readinessTransport.complete(task(3, ready = true).copy(version = 2))
        runCurrent()
    }
}

private data object UnusedReadyToPlanTransport : ReadyToPlanTransport {
    override suspend fun setReady(taskId: Int, ready: Boolean): Result<BattleTask> =
        error("Readiness persistence is not used by this projection test")
}

private class DeferredReadyToPlanTransport : ReadyToPlanTransport {
    private val response = CompletableDeferred<Result<BattleTask>>()

    override suspend fun setReady(taskId: Int, ready: Boolean): Result<BattleTask> = response.await()

    fun complete(task: BattleTask) {
        response.complete(Result.success(task))
    }
}

private class ScopedTasksApi(private val items: List<BattleTaskDto>) {
    fun proxy(): TimeboxApi {
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            val result = when (method.name) {
                "listBattleTasks" -> BattleTaskListDto(
                    items = items,
                    timezone = "Asia/Singapore",
                    serverNowIso = "2026-09-08T12:00:00+08:00",
                )
                else -> error("Unexpected API call: ${method.name}")
            }
            @Suppress("UNCHECKED_CAST")
            val continuation = args?.lastOrNull() as? Continuation<Any?>
            if (continuation == null) result else {
                continuation.resumeWith(Result.success(result))
                COROUTINE_SUSPENDED
            }
        }
        return Proxy.newProxyInstance(
            TimeboxApi::class.java.classLoader,
            arrayOf(TimeboxApi::class.java),
            handler,
        ) as TimeboxApi
    }
}

private fun taskDto(
    id: Int,
    ready: Boolean,
    sessionTasks: List<BattleTaskDto> = emptyList(),
) = BattleTaskDto(
    id = id,
    title = "Task $id",
    description = "",
    readyToPlan = ready,
    status = "open",
    position = id,
    createdAt = "2026-09-08T00:00:00Z",
    updatedAt = "2026-09-08T00:00:00Z",
    sessionTasks = sessionTasks,
)
