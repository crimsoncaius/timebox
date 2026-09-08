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

    @Test
    fun `readiness projection preserves fresher unrelated caller fields`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val cached = task(12, ready = false).copy(
            title = "Cached title",
            description = "Cached description",
            position = 1,
            version = 1,
        )
        coordinator.mergeServerTasks(listOf(cached))
        coordinator.setReady(cached, true)
        runCurrent()

        val fresherCallerTask = cached.copy(
            title = "Fresh title",
            description = "Fresh description",
            position = 9,
            version = 2,
        )

        val projected = coordinator.projectTasks(listOf(fresherCallerTask)).single()

        assertEquals("Fresh title", projected.title)
        assertEquals("Fresh description", projected.description)
        assertEquals(9, projected.position)
        assertEquals(2, projected.version)
        assertTrue(projected.readyToPlan)
        assertTrue(projected.readinessPending)

        transport.completeNext(cached.copy(readyToPlan = true, version = 2))
        runCurrent()

        val settled = coordinator.projectTasks(listOf(fresherCallerTask)).single()
        assertEquals("Fresh title", settled.title)
        assertEquals("Fresh description", settled.description)
        assertEquals(9, settled.position)
        assertEquals(2, settled.version)
        assertTrue(settled.readyToPlan)
        assertFalse(settled.readinessPending)
    }

    @Test
    fun `failed latest choice reconciles and only retries manually`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val original = task(13, ready = false).copy(version = 1)
        coordinator.mergeServerTasks(listOf(original))

        coordinator.setReady(original, true)
        runCurrent()
        transport.failNext()
        runCurrent()

        assertEquals(listOf(13), transport.reconciliationCalls)
        assertEquals(listOf(13 to true), transport.calls)

        transport.completeReconciliation(original.copy(version = 2))
        runCurrent()

        val failed = coordinator.projectedTask(13)!!
        assertFalse(failed.readyToPlan)
        assertFalse(failed.readinessPending)
        assertEquals("Ready to Plan was not saved. Retry your latest choice.", failed.readinessFailureMessage)
        assertEquals(listOf(13 to true), transport.calls)

        coordinator.retry(13)
        runCurrent()

        assertEquals(listOf(13 to true, 13 to true), transport.calls)
        assertEquals(null, coordinator.projectedTask(13)!!.readinessFailureMessage)
        assertTrue(coordinator.projectedTask(13)!!.readinessPending)

        transport.completeNext(original.copy(readyToPlan = true, version = 3))
        runCurrent()
    }

    @Test
    fun `Task Detail draft does not supersede a newer explicit choice`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val original = task(14, ready = false).copy(version = 1)
        coordinator.mergeServerTasks(listOf(original))
        val saveBoundary = coordinator.intentVersion(14)

        coordinator.setReady(original, true)
        runCurrent()

        assertFalse(coordinator.setReadyFromDraft(original, false, saveBoundary))
        assertTrue(coordinator.projectedTask(14)!!.readyToPlan)
        assertEquals(listOf(14 to true), transport.calls)

        transport.completeNext(original.copy(readyToPlan = true, version = 2))
        runCurrent()
    }

    @Test
    fun `obsolete failure is silent when newer intent matches reconciled state`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val original = task(15, ready = false).copy(version = 1)
        coordinator.mergeServerTasks(listOf(original))

        coordinator.setReady(original, true)
        runCurrent()
        coordinator.setReady(coordinator.projectedTask(15)!!, false)
        transport.failNext()
        runCurrent()
        transport.completeReconciliation(original.copy(version = 2))
        runCurrent()

        val settled = coordinator.projectedTask(15)!!
        assertFalse(settled.readyToPlan)
        assertFalse(settled.readinessPending)
        assertEquals(null, settled.readinessFailureMessage)
        assertEquals(listOf(15 to true), transport.calls)
    }

    @Test
    fun `failed response settles silently when reconciliation already matches latest intent`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val original = task(16, ready = false).copy(version = 1)
        coordinator.mergeServerTasks(listOf(original))

        coordinator.setReady(original, true)
        runCurrent()
        transport.failNext()
        runCurrent()
        transport.completeReconciliation(original.copy(readyToPlan = true, version = 2))
        runCurrent()

        val settled = coordinator.projectedTask(16)!!
        assertTrue(settled.readyToPlan)
        assertFalse(settled.readinessPending)
        assertEquals(null, settled.readinessFailureMessage)
    }

    @Test
    fun `unavailable reconciliation falls back to confirmed value with retry`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val original = task(17, ready = false).copy(version = 1)
        coordinator.mergeServerTasks(listOf(original))

        coordinator.setReady(original, true)
        runCurrent()
        transport.failNext()
        runCurrent()
        transport.failReconciliation()
        runCurrent()

        val failed = coordinator.projectedTask(17)!!
        assertFalse(failed.readyToPlan)
        assertFalse(failed.readinessPending)
        assertEquals("Ready to Plan could not be confirmed. Retry your latest choice.", failed.readinessFailureMessage)
    }

    @Test
    fun `newer server snapshot and stale write response never regress task projection`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val original = task(18, ready = false).copy(title = "Original", version = 1)
        coordinator.mergeServerTasks(listOf(original))
        coordinator.setReady(original, true)
        runCurrent()

        coordinator.mergeServerTasks(listOf(original.copy(title = "Newest", readyToPlan = true, version = 7)))
        transport.completeNext(original.copy(title = "Stale", readyToPlan = false, version = 6))
        runCurrent()

        val projected = coordinator.projectTasks(listOf(original.copy(version = 5))).single()
        assertEquals("Newest", projected.title)
        assertEquals(7, projected.version)
        assertTrue(projected.readyToPlan)
        assertFalse(projected.readinessPending)
    }

    @Test
    fun `different Tasks retain independent failures and retries`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val first = task(19, ready = false).copy(version = 1)
        val second = task(20, ready = false).copy(version = 1)
        coordinator.mergeServerTasks(listOf(first, second))

        coordinator.setReady(first, true)
        coordinator.setReady(second, true)
        runCurrent()
        transport.failCall(19)
        transport.failCall(20)
        runCurrent()
        transport.completeReconciliationFor(19, first.copy(version = 2))
        transport.completeReconciliationFor(20, second.copy(version = 2))
        runCurrent()

        assertTrue(coordinator.projectedTask(19)!!.readinessFailureMessage != null)
        assertTrue(coordinator.projectedTask(20)!!.readinessFailureMessage != null)

        coordinator.retry(19)
        coordinator.retry(20)
        runCurrent()
        assertEquals(2, transport.calls.count { it == 19 to true })
        assertEquals(2, transport.calls.count { it == 20 to true })
        transport.completeNext(first.copy(readyToPlan = true, version = 3))
        transport.completeNext(second.copy(readyToPlan = true, version = 3))
        runCurrent()
    }

    @Test
    fun `new explicit choice supersedes failed retry state`() = runTest {
        val transport = DeferredReadyToPlanTransport()
        val coordinator = ReadyToPlanCoordinator(transport, this)
        val original = task(21, ready = false).copy(version = 1)
        coordinator.mergeServerTasks(listOf(original))
        coordinator.setReady(original, true)
        runCurrent()
        transport.failNext()
        runCurrent()
        transport.completeReconciliation(original.copy(version = 2))
        runCurrent()

        coordinator.setReady(coordinator.projectedTask(21)!!, true)
        runCurrent()

        assertEquals(null, coordinator.projectedTask(21)!!.readinessFailureMessage)
        assertTrue(coordinator.projectedTask(21)!!.readinessPending)
        assertEquals(2, transport.calls.count { it == 21 to true })
        transport.completeNext(original.copy(readyToPlan = true, version = 3))
        runCurrent()
    }
}

private class DeferredReadyToPlanTransport : ReadyToPlanTransport {
    val calls = mutableListOf<Pair<Int, Boolean>>()
    val reconciliationCalls = mutableListOf<Int>()
    private val responses = ArrayDeque<CompletableDeferred<Result<BattleTask>>>()
    private val reconciliations = ArrayDeque<CompletableDeferred<Result<BattleTask?>>>()
    private val responseTaskIds = ArrayDeque<Int>()
    private val reconciliationTaskIds = ArrayDeque<Int>()

    override suspend fun setReady(taskId: Int, ready: Boolean): Result<BattleTask> {
        calls += taskId to ready
        responseTaskIds.addLast(taskId)
        return CompletableDeferred<Result<BattleTask>>().also(responses::addLast).await()
    }

    override suspend fun reconcile(taskId: Int): Result<BattleTask?> {
        reconciliationCalls += taskId
        reconciliationTaskIds.addLast(taskId)
        return CompletableDeferred<Result<BattleTask?>>().also(reconciliations::addLast).await()
    }

    fun completeNext(task: BattleTask) {
        responseTaskIds.removeFirst()
        responses.removeFirst().complete(Result.success(task))
    }

    fun failNext() {
        responseTaskIds.removeFirst()
        responses.removeFirst().complete(Result.failure(IllegalStateException("write failed")))
    }

    fun failCall(taskId: Int) {
        val index = responseTaskIds.indexOf(taskId)
        check(index >= 0)
        responseTaskIds.removeAt(index)
        responses.removeAt(index).complete(Result.failure(IllegalStateException("write failed")))
    }

    fun completeReconciliation(task: BattleTask?) {
        reconciliationTaskIds.removeFirst()
        reconciliations.removeFirst().complete(Result.success(task))
    }

    fun completeReconciliationFor(taskId: Int, task: BattleTask?) {
        val index = reconciliationTaskIds.indexOf(taskId)
        check(index >= 0)
        reconciliationTaskIds.removeAt(index)
        reconciliations.removeAt(index).complete(Result.success(task))
    }

    fun failReconciliation() {
        reconciliationTaskIds.removeFirst()
        reconciliations.removeFirst().complete(Result.failure(IllegalStateException("read failed")))
    }
}
