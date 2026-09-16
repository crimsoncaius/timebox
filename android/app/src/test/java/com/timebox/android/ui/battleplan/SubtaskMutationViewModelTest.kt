package com.timebox.android.ui.battleplan

import androidx.lifecycle.viewModelScope
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubtaskMutationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `task details checks a Subtask from the saved response without another task list read`() = runTest(dispatcher) {
        val api = MutationApi()
        val details = details(api)
        details.load(10)
        details.state.first { !it.loading }
        val readsAfterLoad = api.reads.get()

        details.toggleSubtask(details.state.value.subtasks.single())
        details.state.first { it.subtasks.single().checked }

        assertTrue(details.state.value.subtasks.single().checked)
        assertFalse(details.state.value.saving)
        assertEquals(readsAfterLoad, api.reads.get())
        details.viewModelScope.cancel()
    }

    @Test fun `task details does not put the sheet into saving while checking a Subtask`() = runTest(dispatcher) {
        val gate = CompletableDeferred<SubtaskDto>()
        val api = MutationApi(checkGate = gate)
        val details = details(api)
        details.load(10)
        details.state.first { !it.loading }

        details.toggleSubtask(details.state.value.subtasks.single())
        runCurrent()
        withTimeout(1_000) { api.checkStarted.await() }
        assertFalse(details.state.value.saving)
        assertFalse(details.state.value.subtasks.single().checked)

        gate.complete(api.checkedSubtask())
        details.state.first { it.subtasks.single().checked }
        assertFalse(details.state.value.saving)
        details.viewModelScope.cancel()
    }

    @Test fun `task details trashes a Subtask from the saved response without another task list read`() = runTest(dispatcher) {
        val api = MutationApi()
        val details = details(api)
        details.load(10)
        details.state.first { !it.loading }
        val readsAfterLoad = api.reads.get()

        details.requestSubtaskTrash(details.state.value.subtasks.single())
        details.confirmSubtaskTrash()
        details.state.first { it.subtasks.isEmpty() }

        assertTrue(details.state.value.subtasks.isEmpty())
        assertFalse(details.state.value.saving)
        assertEquals(readsAfterLoad, api.reads.get())
        details.viewModelScope.cancel()
    }

    @Test fun `battle plan checks a Subtask without reloading the board`() = runTest(dispatcher) {
        val api = MutationApi()
        val model = battlePlan(api)
        model.load()
        model.state.first { !it.loading }
        val readsAfterLoad = api.reads.get()

        model.toggleSubtaskComplete(model.state.value.tasks.single().subtasks.single())
        model.state.first { it.tasks.single().subtasks.single().checked }

        assertTrue(model.state.value.tasks.single().subtasks.single().checked)
        assertFalse(model.state.value.refreshing)
        assertEquals(readsAfterLoad, api.reads.get())
        model.viewModelScope.cancel()
    }

    @Test fun `battle plan composer title keystrokes do not reload the board`() = runTest(dispatcher) {
        val api = MutationApi()
        val model = battlePlan(api)
        model.load()
        model.state.first { !it.loading }
        model.setComposerVisible(true)
        val readsAfterOpen = api.reads.get()

        model.updateComposerDraft(model.state.value.composerDraft.copy(title = "P"))
        model.updateComposerDraft(model.state.value.composerDraft.copy(title = "Pr"))
        model.updateComposerDraft(model.state.value.composerDraft.copy(title = "Prepare review"))

        assertEquals("Prepare review", model.state.value.composerDraft.title)
        assertTrue(model.state.value.showComposer)
        assertFalse(model.state.value.refreshing)
        assertEquals(readsAfterOpen, api.reads.get())
        model.viewModelScope.cancel()
    }

    @Test fun `battle plan trashes a Battle Plan Task without reloading the board`() = runTest(dispatcher) {
        val api = MutationApi()
        val model = battlePlan(api)
        model.load()
        model.state.first { !it.loading }
        val readsAfterLoad = api.reads.get()

        model.requestTrash(model.state.value.tasks.single())
        model.confirmTrash()
        model.state.first { it.tasks.isEmpty() }

        assertTrue(model.state.value.tasks.isEmpty())
        assertFalse(model.state.value.refreshing)
        assertFalse(model.state.value.saving)
        assertEquals(readsAfterLoad, api.reads.get())
        assertEquals(10, model.state.value.trashUndo?.taskId)
        model.viewModelScope.cancel()
    }

    private fun kotlinx.coroutines.test.TestScope.details(api: MutationApi): TaskDetailViewModel {
        val repository = TimeboxRepository(api.proxy())
        return TaskDetailViewModel(repository, TaskCompletion(RepositoryTaskCompletionTransport(repository)))
    }

    private fun kotlinx.coroutines.test.TestScope.battlePlan(api: MutationApi): BattlePlanViewModel {
        val repository = TimeboxRepository(api.proxy())
        return BattlePlanViewModel(
            repository,
            TaskCompletion(RepositoryTaskCompletionTransport(repository)),
            readinessCoordinator = createReadyToPlanCoordinator(repository, backgroundScope),
        )
    }
}

private class MutationApi(
    private val checkGate: CompletableDeferred<SubtaskDto>? = null,
) {
    val reads = AtomicInteger(0)
    val checkStarted = CompletableDeferred<Unit>()
    private val timestamp = "2026-09-15T00:00:00Z"
    private var parent = BattleTaskDto(
        id = 10, title = "Parent", description = "", readyToPlan = false, status = "open", position = 0,
        createdAt = timestamp, updatedAt = timestamp,
        subtasks = listOf(SubtaskDto(11, 10, "Checkpoint", false, false, 0, timestamp, timestamp)),
    )

    fun checkedSubtask() = SubtaskDto(11, 10, "Checkpoint", true, true, 0, timestamp, timestamp)

    fun proxy(): TimeboxApi = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
        when (method.name) {
            "listBattleTasks" -> {
                reads.incrementAndGet()
                BattleTaskListDto(listOf(parent), "UTC", timestamp)
            }
            "listProjects", "listTaskTypes" -> emptyList<Nothing>()
            "checkSubtask" -> {
                checkStarted.complete(Unit)
                val saved = checkGate?.let { runBlocking { it.await() } } ?: checkedSubtask()
                parent = parent.copy(subtasks = listOf(saved))
                saved
            }
            "trashBattleTask" -> {
                val id = args!![0] as Int
                if (id == parent.id) {
                    parent = parent.copy(deletedAt = timestamp)
                    parent
                } else {
                    parent = parent.copy(subtasks = emptyList())
                    BattleTaskDto(
                        id = 11, parentId = 10, title = "Checkpoint", description = "", readyToPlan = false,
                        status = "open", position = 0, createdAt = timestamp, updatedAt = timestamp,
                        deletedAt = timestamp,
                    )
                }
            }
            else -> error("Unexpected API call: ${method.name}")
        }
    } as TimeboxApi
}
