package com.timebox.android.ui.battleplan

import androidx.lifecycle.SavedStateHandle
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import com.timebox.android.ui.undo.UndoLifecycle
import com.timebox.android.ui.undo.UndoPhase
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BattlePlanTrashUndoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `restore retries once per request and consumes only its own notice`() = runTest(dispatcher) {
        val transport = FakeRestore().apply { fail = true }
        val lifecycle = UndoLifecycle(backgroundScope) { testScheduler.currentTime }
        lifecycle.setExposure("battle-plan", true, 10_000)
        val model = viewModel(transport, lifecycle)
        model.offerUndo(7, "Draft launch brief")
        val id = lifecycle.notice.value!!.id
        lifecycle.undo(id)
        lifecycle.undo(id)
        runCurrent()
        assertEquals(listOf(7), transport.calls)
        assertEquals(UndoPhase.Failed, lifecycle.notice.value?.phase)

        transport.fail = false
        lifecycle.undo(id)
        runCurrent()
        assertEquals(listOf(7, 7), transport.calls)
        assertNull(lifecycle.notice.value)
        assertEquals(7, model.state.value.restoredTrashTaskId)
    }

    @Test fun `matching external restore invalidates only that target`() = runTest(dispatcher) {
        val lifecycle = UndoLifecycle(backgroundScope) { testScheduler.currentTime }
        val model = viewModel(FakeRestore(), lifecycle)
        model.offerUndo(7, "Draft launch brief")
        model.invalidateUndo(8)
        assertEquals(7, lifecycle.notice.value?.targetId)
        model.invalidateUndo(7)
        assertNull(lifecycle.notice.value)
    }

    @Test fun `transient opportunity is not saved with the ViewModel`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = UndoLifecycle(backgroundScope) { testScheduler.currentTime }
        viewModel(FakeRestore(), first, saved).offerUndo(7, "Draft launch brief")
        assertEquals(7, first.notice.value?.targetId)

        val restarted = UndoLifecycle(backgroundScope) { testScheduler.currentTime }
        viewModel(FakeRestore(), restarted, saved)
        assertNull(restarted.notice.value)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(
        transport: FakeRestore,
        lifecycle: UndoLifecycle,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): BattlePlanViewModel {
        val repository = TimeboxRepository(fakeApi())
        return BattlePlanViewModel(
            repository = repository,
            taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository)),
            savedStateHandle = savedState,
            trashRestoreTransport = transport,
            readinessCoordinator = createReadyToPlanCoordinator(repository, this),
            injectedUndoLifecycle = lifecycle,
        )
    }

    private class FakeRestore : TrashRestoreTransport {
        val calls = mutableListOf<Int>()
        var fail = false
        override suspend fun restore(taskId: Int): Result<Unit> {
            calls += taskId
            return if (fail) Result.failure(java.io.IOException("Restore unavailable")) else Result.success(Unit)
        }
    }

    private fun fakeApi(): TimeboxApi = Proxy.newProxyInstance(
        TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java),
    ) { _, _, _ -> Unit } as TimeboxApi
}
