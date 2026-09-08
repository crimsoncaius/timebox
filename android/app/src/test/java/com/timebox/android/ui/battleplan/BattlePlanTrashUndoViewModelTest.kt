package com.timebox.android.ui.battleplan

import androidx.lifecycle.SavedStateHandle
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BattlePlanTrashUndoViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `eligible exposure uses the accessibility timeout and pauses in background`() = runTest(dispatcher) {
        val model = viewModel(elapsedRealtime = { testScheduler.currentTime })

        model.offerUndo(7, "Draft launch brief")
        model.setUndoExposureActive(active = true, recommendedTimeoutMillis = 15_000)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(TrashUndoPhase.Ready, model.state.value.trashUndo?.phase)
        model.setUndoExposureActive(active = false, recommendedTimeoutMillis = 15_000)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(TrashUndoPhase.Ready, model.state.value.trashUndo?.phase)

        model.setUndoExposureActive(active = true, recommendedTimeoutMillis = 15_000)
        advanceTimeBy(5_000)
        runCurrent()

        val expiring = model.state.value.trashUndo
        assertNotNull(expiring)
        assertEquals(TrashUndoPhase.Expiring, expiring?.phase)
        assertEquals("Draft launch brief", expiring?.title)
        assertEquals(7, expiring?.taskId)
    }

    @Test
    fun `Undo is exactly once and a failed restore stays available for Retry`() = runTest(dispatcher) {
        val transport = FakeTrashRestoreTransport().apply { failRestore = true }
        val model = viewModel({ testScheduler.currentTime }, transport)
        model.offerUndo(7, "Draft launch brief")
        val noticeId = model.state.value.trashUndo!!.noticeId

        model.undoTrash(noticeId)
        model.undoTrash(noticeId)
        assertEquals(TrashUndoPhase.Restoring, model.state.value.trashUndo?.phase)
        runCurrent()

        assertEquals(listOf(7), transport.restoreCalls)
        assertEquals(TrashUndoPhase.Failed, model.state.value.trashUndo?.phase)
        assertTrue(model.state.value.trashUndo?.error?.isNotBlank() == true)

        transport.failRestore = false
        model.undoTrash(noticeId)
        runCurrent()

        assertEquals(listOf(7, 7), transport.restoreCalls)
        assertNull(model.state.value.trashUndo)
        assertEquals(7, model.state.value.restoredTrashTaskId)
    }

    @Test
    fun `an older Undo completion cannot clear a replacement notice`() = runTest(dispatcher) {
        val transport = FakeTrashRestoreTransport()
        val model = viewModel({ testScheduler.currentTime }, transport)
        model.offerUndo(7, "First Task")
        val firstNoticeId = model.state.value.trashUndo!!.noticeId
        model.undoTrash(firstNoticeId)

        model.offerUndo(8, "Second Task")
        runCurrent()

        assertEquals("Second Task", model.state.value.trashUndo?.title)
        assertEquals(TrashUndoPhase.Ready, model.state.value.trashUndo?.phase)
        assertEquals(listOf(7), transport.restoreCalls)
    }

    @Test
    fun `external lifecycle action invalidates only its matching notice`() = runTest(dispatcher) {
        val model = viewModel(elapsedRealtime = { testScheduler.currentTime })
        model.offerUndo(7, "Draft launch brief")

        model.invalidateUndo(8)
        assertNotNull(model.state.value.trashUndo)

        model.invalidateUndo(7)
        assertNull(model.state.value.trashUndo)
    }

    @Test
    fun `Dismiss removes the notice without restoring the Task`() = runTest(dispatcher) {
        val transport = FakeTrashRestoreTransport()
        val model = viewModel({ testScheduler.currentTime }, transport)
        model.offerUndo(7, "Draft launch brief")
        val noticeId = model.state.value.trashUndo!!.noticeId

        model.dismissUndo(noticeId)
        runCurrent()

        assertNull(model.state.value.trashUndo)
        assertTrue(transport.restoreCalls.isEmpty())
    }

    @Test
    fun `a replacement gets a fresh interval and ignores stale expiry and dismissal`() = runTest(dispatcher) {
        val model = viewModel(elapsedRealtime = { testScheduler.currentTime })
        model.setUndoExposureActive(active = true, recommendedTimeoutMillis = 10_000)
        model.offerUndo(7, "First Task")
        val firstNoticeId = model.state.value.trashUndo!!.noticeId
        advanceTimeBy(5_000)

        model.offerUndo(8, "Second Task")
        val secondNoticeId = model.state.value.trashUndo!!.noticeId
        model.dismissUndo(firstNoticeId)
        model.finishUndoExpiry(firstNoticeId)
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(secondNoticeId, model.state.value.trashUndo?.noticeId)
        assertEquals(TrashUndoPhase.Ready, model.state.value.trashUndo?.phase)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(TrashUndoPhase.Expiring, model.state.value.trashUndo?.phase)
    }

    @Test
    fun `transient Undo state is not restored into a new ViewModel`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val current = viewModel({ testScheduler.currentTime }, savedStateHandle = savedState)
        current.offerUndo(7, "Draft launch brief")
        assertNotNull(current.state.value.trashUndo)

        val recreatedAfterProcessDeath = viewModel({ testScheduler.currentTime }, savedStateHandle = savedState)

        assertNull(recreatedAfterProcessDeath.state.value.trashUndo)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(
        elapsedRealtime: () -> Long,
        transport: TrashRestoreTransport = FakeTrashRestoreTransport(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): BattlePlanViewModel {
        val repository = TimeboxRepository(fakeApi())
        return BattlePlanViewModel(
            repository = repository,
            taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository)),
            savedStateHandle = savedStateHandle,
            trashRestoreTransport = transport,
            elapsedRealtime = elapsedRealtime,
            readinessCoordinator = createReadyToPlanCoordinator(repository, this),
        )
    }

    private class FakeTrashRestoreTransport : TrashRestoreTransport {
        val restoreCalls = mutableListOf<Int>()
        var failRestore = false

        override suspend fun restore(taskId: Int): Result<Unit> {
            restoreCalls += taskId
            return if (failRestore) Result.failure(java.io.IOException("Restore unavailable")) else Result.success(Unit)
        }
    }

    private fun fakeApi(): TimeboxApi = Proxy.newProxyInstance(
        TimeboxApi::class.java.classLoader,
        arrayOf(TimeboxApi::class.java),
    ) { _, _, _ -> Unit } as TimeboxApi
}
