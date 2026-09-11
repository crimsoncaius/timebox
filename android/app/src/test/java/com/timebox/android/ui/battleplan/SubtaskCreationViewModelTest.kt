package com.timebox.android.ui.battleplan

import androidx.lifecycle.viewModelScope
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubtaskCreationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `task details creates and reloads a subtask in a project`() = verifyCreation(7, false)
    @Test fun `task details creates and reloads a subtask without a project`() = verifyCreation(null, false)
    @Test fun `battle plan creates and reloads a subtask in a project`() = verifyCreation(7, true)
    @Test fun `battle plan creates and reloads a subtask without a project`() = verifyCreation(null, true)

    private fun verifyCreation(projectId: Int?, fromBattlePlan: Boolean) = runTest(dispatcher) {
        val timestamp = "2026-09-12T00:00:00Z"
        var parent = BattleTaskDto(
            id = 10, projectId = projectId, title = "Parent", description = "",
            readyToPlan = false, status = "open", position = 0,
            createdAt = timestamp, updatedAt = timestamp,
        )
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "listBattleTasks" -> BattleTaskListDto(listOf(parent), "UTC", timestamp)
                "listProjects", "listTaskTypes" -> emptyList<Nothing>()
                "createBattleTask" -> {
                    val body = args!![0] as BattleTaskCreateDto
                    // Match the backend's rejection of independent Subtask lifecycle fields.
                    require(body.projectId == null) { "Subtasks do not have a Task lifecycle" }
                    assertEquals(10, body.parentId)
                    parent = parent.copy(subtasks = listOf(SubtaskDto(11, 10, body.title, false, false, 0, timestamp, timestamp)))
                    parent.copy(id = 11, parentId = 10, title = body.title, subtasks = emptyList())
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        } as TimeboxApi
        val repository = TimeboxRepository(api)
        val completion = TaskCompletion(RepositoryTaskCompletionTransport(repository))
        val details = TaskDetailViewModel(repository, completion)
        details.load(10)
        details.state.first { !it.loading }
        assertNotNull(details.state.value.task)
        if (fromBattlePlan) {
            val model = BattlePlanViewModel(repository, completion,
                readinessCoordinator = createReadyToPlanCoordinator(repository, backgroundScope))
            model.createSubtask(details.state.value.task!!, "  Checkpoint  ")
            model.state.first { !it.saving }
            model.viewModelScope.cancel()
        } else {
            details.addSubtask("  Checkpoint  ")
            details.state.first { !it.saving }
        }
        val reopened = TaskDetailViewModel(repository, completion)
        reopened.load(10)
        reopened.state.first { !it.loading }
        details.viewModelScope.cancel()
        reopened.viewModelScope.cancel()
        assertEquals(listOf("Checkpoint"), reopened.state.value.subtasks.map { it.title })
    }
}
