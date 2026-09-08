package com.timebox.android.ui.readiness

import com.timebox.android.data.BattleTask
import com.timebox.android.data.TaskStatus
import com.timebox.android.ui.battleplan.task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadyToPlanCoordinatorTest {
    @Test
    fun `latest choice projects immediately while writes remain serialized per Task`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val original = task(10, ready = false).copy(version = 1)
        coordinator.mergeServerTasks(listOf(original))

        coordinator.setReady(original, true)
        runCurrent()

        assertEquals(listOf(10 to true), transport.calls)
        assertTrue(coordinator.projectedTask(10)!!.readyToPlan)
        assertTrue(coordinator.projectedTask(10)!!.readinessPending)

        coordinator.setReady(coordinator.projectedTask(10)!!, false)
        runCurrent()

        assertEquals(listOf(10 to true), transport.calls)
        assertFalse(coordinator.projectedTask(10)!!.readyToPlan)
        assertTrue(coordinator.projectedTask(10)!!.readinessPending)

        transport.completeNext(original.copy(readyToPlan = true, version = 2))
        runCurrent()

        assertEquals(listOf(10 to true, 10 to false), transport.calls)
        assertFalse(coordinator.projectedTask(10)!!.readyToPlan)
        assertTrue(coordinator.projectedTask(10)!!.readinessPending)

        transport.completeNext(original.copy(readyToPlan = false, version = 3))
        runCurrent()

        assertFalse(coordinator.projectedTask(10)!!.readinessPending)
        assertEquals(3, coordinator.projectedTask(10)!!.version)
    }

    @Test
    fun `server lifecycle result remains authoritative`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val original = task(11, ready = false).copy(version = 1)
        coordinator.mergeServerTasks(listOf(original))

        coordinator.setReady(original, true)
        runCurrent()
        transport.completeNext(
            original.copy(
                readyToPlan = false,
                status = TaskStatus.Completed,
                version = 2,
            ),
        )
        runCurrent()

        val projected = coordinator.projectedTask(11)!!
        assertEquals(TaskStatus.Completed, projected.status)
        assertFalse(projected.readyToPlan)
        assertFalse(projected.readinessPending)
    }
}

private class DeferredReadyToPlanTransport : ReadyToPlanTransport {
    val calls = mutableListOf<Pair<Int, Boolean>>()
    private val responses = ArrayDeque<CompletableDeferred<Result<BattleTask>>>()

    override suspend fun setReady(taskId: Int, ready: Boolean): Result<BattleTask> {
        calls += taskId to ready
        return CompletableDeferred<Result<BattleTask>>().also(responses::addLast).await()
    }

    fun completeNext(task: BattleTask) {
        responses.removeFirst().complete(Result.success(task))
    }
}
