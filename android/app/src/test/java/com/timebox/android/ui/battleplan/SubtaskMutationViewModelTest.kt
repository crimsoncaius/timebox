package com.timebox.android.ui.battleplan

import androidx.lifecycle.viewModelScope
import com.timebox.android.data.TaskStatus
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun `task details renames a checked Subtask with only its trimmed title`() = runTest(dispatcher) {
        val api = MutationApi(checked = true)
        val details = details(api)
        details.load(10)
        details.state.first { !it.loading }
        val readsAfterLoad = api.reads.get()
        val subtask = details.state.value.subtasks.single()

        details.startSubtaskRename(subtask)
        assertEquals(SubtaskRename(11), details.state.value.subtaskRename)
        details.renameSubtask(subtask, "  Draft outline  ")
        assertTrue(details.state.value.subtaskRename!!.saving)
        details.state.first { it.subtaskRename == null }

        assertEquals(listOf(JsonObject(mapOf("title" to JsonPrimitive("Draft outline")))), api.patches)
        assertEquals("Draft outline", details.state.value.subtasks.single().title)
        assertTrue(details.state.value.subtasks.single().checked)
        assertEquals(readsAfterLoad, api.reads.get())
        details.viewModelScope.cancel()
    }

    @Test fun `task details ignores blank and unchanged Subtask names`() = runTest(dispatcher) {
        val api = MutationApi()
        val details = details(api)
        details.load(10)
        details.state.first { !it.loading }
        val subtask = details.state.value.subtasks.single()

        details.startSubtaskRename(subtask)
        details.renameSubtask(subtask, "   ")
        details.renameSubtask(subtask, " Checkpoint ")
        details.renameSubtask(subtask, "x".repeat(501))
        runCurrent()

        assertTrue(api.patches.isEmpty())
        assertEquals(SubtaskRename(11), details.state.value.subtaskRename)
        details.viewModelScope.cancel()
    }

    @Test fun `task details keeps the Subtask editor open when a rename fails`() = runTest(dispatcher) {
        val api = MutationApi(patchFailure = { httpError(503, "unavailable") })
        val details = details(api)
        details.load(10)
        details.state.first { !it.loading }
        val subtask = details.state.value.subtasks.single()

        details.startSubtaskRename(subtask)
        details.renameSubtask(subtask, "Draft outline")
        details.state.first { it.subtaskRename?.error != null }

        assertFalse(details.state.value.subtaskRename!!.saving)
        assertEquals("Checkpoint", details.state.value.subtasks.single().title)
        details.viewModelScope.cancel()
    }

    @Test fun `task details reloads when a rename is rejected because the Parent Task completed elsewhere`() = runTest(dispatcher) {
        lateinit var api: MutationApi
        api = MutationApi(patchFailure = {
            api.completeParentElsewhere()
            httpError(422, """{"detail":"Completed Tasks and their Subtasks are read-only until reopen"}""")
        })
        val details = details(api)
        details.load(10)
        details.state.first { !it.loading }
        val subtask = details.state.value.subtasks.single()

        details.startSubtaskRename(subtask)
        details.renameSubtask(subtask, "Draft outline")
        details.state.first { it.subtaskRename?.error != null }

        assertEquals("Completed Tasks and their Subtasks are read-only until reopen", details.state.value.subtaskRename!!.error)
        assertEquals(TaskStatus.Completed, details.state.value.task!!.status)
        details.viewModelScope.cancel()
    }

    @Test fun `task details closes the Subtask editor when the Subtask was trashed elsewhere`() = runTest(dispatcher) {
        lateinit var api: MutationApi
        api = MutationApi(patchFailure = {
            api.trashSubtaskElsewhere()
            httpError(422, """{"detail":"Inactive tasks are read-only"}""")
        })
        val details = details(api)
        details.load(10)
        details.state.first { !it.loading }
        val subtask = details.state.value.subtasks.single()

        details.startSubtaskRename(subtask)
        details.renameSubtask(subtask, "Draft outline")
        details.state.first { it.saveError != null }

        assertNull(details.state.value.subtaskRename)
        assertTrue(details.state.value.subtasks.isEmpty())
        assertEquals("Inactive tasks are read-only", details.state.value.saveError)
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

private fun httpError(status: Int, body: String) =
    retrofit2.HttpException(retrofit2.Response.error<BattleTaskDto>(status, body.toResponseBody()))

private class MutationApi(
    private val checkGate: CompletableDeferred<SubtaskDto>? = null,
    checked: Boolean = false,
    private val patchFailure: (() -> Throwable)? = null,
) {
    val reads = AtomicInteger(0)
    val patches = mutableListOf<JsonObject>()
    val checkStarted = CompletableDeferred<Unit>()
    private val timestamp = "2026-09-15T00:00:00Z"
    private var parent = BattleTaskDto(
        id = 10, title = "Parent", description = "", readyToPlan = false, status = "open", position = 0,
        createdAt = timestamp, updatedAt = timestamp,
        subtasks = listOf(SubtaskDto(11, 10, "Checkpoint", checked, checked, 0, timestamp, timestamp)),
    )

    fun completeParentElsewhere() { parent = parent.copy(status = "completed", completedAt = timestamp) }
    fun trashSubtaskElsewhere() { parent = parent.copy(subtasks = emptyList()) }

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
            "patchBattleTask" -> {
                val body = args!![1] as JsonObject
                patches += body
                patchFailure?.let { throw it() }
                val title = (body["title"] as JsonPrimitive).content
                val subtask = parent.subtasks.single()
                parent = parent.copy(subtasks = listOf(subtask.copy(title = title)))
                BattleTaskDto(
                    id = 11, parentId = 10, title = title, description = "", readyToPlan = false,
                    status = "open", position = 0, createdAt = timestamp, updatedAt = timestamp,
                )
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
