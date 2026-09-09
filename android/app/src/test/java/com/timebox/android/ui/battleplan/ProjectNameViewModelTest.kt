package com.timebox.android.ui.battleplan

import com.timebox.android.data.Project
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.ProjectCreateDto
import com.timebox.android.data.remote.ProjectDto
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
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
class ProjectNameViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `creating a Project selects it and clears the creation draft`() = runTest(dispatcher) {
        val model = viewModel()
        model.startProjectCreation()
        model.setProjectName("  Launch  ")
        model.saveProject()

        val state = model.state.first { it.projectEditor?.saving != true }
        assertEquals(listOf("Launch"), state.projects.map { it.name })
        assertEquals(42, state.selectedScope.projectId)
        assertEquals("Launch", state.selectedScope.label)
        assertEquals(null, state.projectEditor)
    }

    @Test fun `pending creation cannot be edited cancelled or submitted twice`() = runTest(dispatcher) {
        val model = viewModel()
        model.startProjectCreation()
        model.setProjectName("Launch")
        model.saveProject()
        model.setProjectName("Replacement")
        model.cancelProjectEditor()
        model.saveProject()

        assertEquals("Launch", model.state.value.projectEditor!!.name)
        val state = model.state.first { it.projectEditor?.saving != true }
        assertEquals(listOf("Launch"), state.projects.map { it.name })
    }

    @Test fun `blank and overlong names stay in the sheet without creating a Project`() = runTest(dispatcher) {
        val model = viewModel()
        model.startProjectCreation()
        for (name in listOf("   ", "a".repeat(201))) {
            model.setProjectName(name)
            model.saveProject()
            val state = model.state.first { it.projectEditor?.saving != true }
            assertTrue(state.projects.isEmpty())
            assertNotNull(state.projectEditor)
            assertEquals(name, state.projectEditor!!.name)
        }
    }

    @Test fun `scope changes retain a draft until explicit Cancel`() = runTest(dispatcher) {
        val model = viewModel()
        model.startProjectCreation()
        model.setProjectName("Launch")
        model.selectScope(BattlePlanScope.Admin)
        assertEquals("Launch", model.state.value.projectEditor!!.name)
        assertNotNull(model.state.value.projectEditor)
        model.cancelProjectEditor()
        assertEquals(null, model.state.value.projectEditor)
    }

    @Test fun `a failed request retains the name for a successful retry`() = runTest(dispatcher) {
        var fail = true
        val model = viewModel { if (fail) throw java.io.IOException("Offline") }
        model.startProjectCreation()
        model.setProjectName("Launch")
        model.saveProject()
        val failed = model.state.first { it.projectEditor?.saving != true }
        assertEquals("Launch", failed.projectEditor!!.name)
        assertNotNull(failed.projectEditor)
        assertNotNull(failed.projectEditor!!.error)
        assertTrue(failed.projects.isEmpty())
        fail = false
        model.saveProject()
        assertEquals(42, model.state.first { it.projectEditor?.saving != true }.selectedScope.projectId)
    }

    @Test fun `a late reorder response retains a Project created during that request`() = runTest(dispatcher) {
        val reorderStarted = CompletableDeferred<Unit>()
        val finishReorder = CompletableDeferred<Unit>()
        val model = viewModel(reorder = {
            reorderStarted.complete(Unit)
            runBlocking { finishReorder.await() }
            listOf(ProjectDto(1, "Alpha", "2026-09-09T00:00:00Z", "2026-09-09T00:00:00Z"))
        })
        model.reorderProjects(listOf(1))
        reorderStarted.await()
        model.startProjectCreation()
        model.setProjectName("Launch")
        model.saveProject()
        model.state.first { it.projectEditor?.saving != true }
        finishReorder.complete(Unit)
        val state = model.state.first { !it.projectOrderSaving }
        assertEquals(listOf("Alpha", "Launch"), state.projects.map { it.name })
        assertEquals(42, state.selectedScope.projectId)
    }

    @Test fun `dismissed creation and edits retain independent names and Cancel clears only its target`() = runTest(dispatcher) {
        val model = viewModel()
        val alpha = Project(1, "Alpha", Instant.EPOCH, Instant.EPOCH)
        val beta = Project(2, "Beta", Instant.EPOCH, Instant.EPOCH)
        model.startProjectCreation()
        model.setProjectName("New draft")
        model.dismissProjectEditor()
        assertEquals(null, model.state.value.projectEditor)
        model.editProject(alpha)
        model.setProjectName("Alpha draft")
        model.dismissProjectEditor()
        model.editProject(beta)
        model.setProjectName("Beta draft")
        model.dismissProjectEditor()
        model.editProject(alpha)
        assertEquals("Alpha draft", model.state.value.projectEditor!!.name)
        model.cancelProjectEditor()
        model.editProject(alpha)
        assertEquals("Alpha", model.state.value.projectEditor!!.name)
        model.editProject(beta)
        assertEquals("Beta draft", model.state.value.projectEditor!!.name)
        model.startProjectCreation()
        assertEquals("New draft", model.state.value.projectEditor!!.name)
    }

    @Test fun `rename refreshes the selected label and preserves other selected scopes`() = runTest(dispatcher) {
        val model = viewModel()
        val alpha = Project(1, "Alpha", Instant.EPOCH, Instant.EPOCH)
        model.selectScope(BattlePlanScope.project(alpha))
        model.editProject(alpha)
        model.setProjectName("  Renamed  ")
        model.saveProject()
        val renamed = model.state.first { it.projectEditor == null }
        assertEquals("Renamed", renamed.selectedScope.label)
        assertEquals(1, renamed.selectedScope.projectId)
        model.selectScope(BattlePlanScope.Admin)
        model.editProject(alpha.copy(name = "Renamed"))
        assertEquals("Renamed", model.state.value.projectEditor!!.name)
        model.setProjectName("Another name")
        model.saveProject()
        assertEquals(BattlePlanScope.Admin, model.state.first { it.projectEditor == null }.selectedScope)
    }

    @Test fun `pending rename blocks dismissal switching and duplicate submission and failure retains draft`() = runTest(dispatcher) {
        var requests = 0
        var fail = true
        val model = viewModel(beforePatch = { requests++; if (fail) throw java.io.IOException("Offline") })
        model.editProject(Project(1, "Alpha", Instant.EPOCH, Instant.EPOCH))
        model.setProjectName("Renamed")
        model.saveProject()
        model.dismissProjectEditor()
        model.cancelProjectEditor()
        model.startProjectCreation()
        model.setProjectName("Replacement")
        model.saveProject()
        assertEquals(1, model.state.value.projectEditor!!.projectId)
        assertEquals("Renamed", model.state.value.projectEditor!!.name)
        val failed = model.state.first { it.projectEditor?.saving != true }
        assertNotNull(failed.projectEditor!!.error)
        assertEquals(1, requests)
        fail = false
        model.saveProject()
        model.state.first { it.projectEditor == null }
        assertEquals(2, requests)
    }

    private fun TestScope.viewModel(
        reorder: () -> List<ProjectDto> = { error("Unexpected reorder") },
        beforePatch: () -> Unit = {},
        beforeCreate: () -> Unit = {},
    ): BattlePlanViewModel {
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "reorderProjects" -> reorder()
                "patchProject" -> {
                    beforePatch()
                    ProjectDto(args!![0] as Int, (args[1] as JsonObject).getValue("name").jsonPrimitive.content, "2026-09-09T00:00:00Z", "2026-09-09T00:00:00Z")
                }
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
