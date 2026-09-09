package com.timebox.android.ui.battleplan

import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.ProjectCreateDto
import com.timebox.android.data.remote.ProjectDto
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InlineProjectCreationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `creating a Project selects it and clears the inline draft`() = runTest(dispatcher) {
        val model = viewModel()
        model.startProjectCreation()
        model.setNewProjectName("  Launch  ")
        model.createProject()

        val state = model.state.first { !it.projectCreation.saving }
        assertEquals(listOf("Launch"), state.projects.map { it.name })
        assertEquals(42, state.selectedScope.projectId)
        assertEquals("Launch", state.selectedScope.label)
        assertFalse(state.projectCreation.active)
        assertEquals("", state.projectCreation.name)
    }

    @Test fun `pending creation cannot be edited cancelled or submitted twice`() = runTest(dispatcher) {
        val model = viewModel()
        model.startProjectCreation()
        model.setNewProjectName("Launch")
        model.createProject()
        model.setNewProjectName("Replacement")
        model.cancelProjectCreation()
        model.createProject()

        assertEquals("Launch", model.state.value.projectCreation.name)
        val state = model.state.first { !it.projectCreation.saving }
        assertEquals(listOf("Launch"), state.projects.map { it.name })
    }

    @Test fun `blank and overlong names stay inline without creating a Project`() = runTest(dispatcher) {
        val model = viewModel()
        model.startProjectCreation()
        for (name in listOf("   ", "a".repeat(201))) {
            model.setNewProjectName(name)
            model.createProject()
            val state = model.state.first { !it.projectCreation.saving }
            assertTrue(state.projects.isEmpty())
            assertTrue(state.projectCreation.active)
            assertEquals(name, state.projectCreation.name)
        }
    }

    @Test fun `scope changes retain a draft until explicit Cancel`() = runTest(dispatcher) {
        val model = viewModel()
        model.startProjectCreation()
        model.setNewProjectName("Launch")
        model.selectScope(BattlePlanScope.Admin)
        assertEquals("Launch", model.state.value.projectCreation.name)
        assertTrue(model.state.value.projectCreation.active)
        model.cancelProjectCreation()
        assertFalse(model.state.value.projectCreation.active)
        assertEquals("", model.state.value.projectCreation.name)
    }

    @Test fun `a failed request retains the name for a successful retry`() = runTest(dispatcher) {
        var fail = true
        val model = viewModel { if (fail) throw java.io.IOException("Offline") }
        model.startProjectCreation()
        model.setNewProjectName("Launch")
        model.createProject()
        val failed = model.state.first { !it.projectCreation.saving }
        assertEquals("Launch", failed.projectCreation.name)
        assertTrue(failed.projectCreation.active)
        assertNotNull(failed.projectCreation.error)
        assertTrue(failed.projects.isEmpty())
        fail = false
        model.createProject()
        assertEquals(42, model.state.first { !it.projectCreation.saving }.selectedScope.projectId)
    }

    private fun TestScope.viewModel(beforeCreate: () -> Unit = {}): BattlePlanViewModel {
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "createProject" -> {
                    beforeCreate()
                    ProjectDto(42, (args!![0] as ProjectCreateDto).name, "2026-09-09T00:00:00Z", "2026-09-09T00:00:00Z")
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        } as TimeboxApi
        val repository = TimeboxRepository(api)
        return BattlePlanViewModel(
            repository = repository,
            taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository)),
            readinessCoordinator = createReadyToPlanCoordinator(repository, backgroundScope),
        )
    }
}
