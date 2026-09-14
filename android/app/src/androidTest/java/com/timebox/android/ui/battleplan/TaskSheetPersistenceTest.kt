package com.timebox.android.ui.battleplan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

class TaskSheetPersistenceTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun cleanup() { compose.runOnIdle { store.clear(); scope.cancel() } }

    @Test fun recoveredFieldRemainsDirtyAndCanBeRetried() {
        val api = SheetApi()
        val repository = TimeboxRepository(api.proxy())
        val handle = SavedStateHandle()
        persistTaskDetailDraft(handle, 10, 1, TaskComposerDraft(title = "Task", description = "Recovered notes").toDetailDraft())
        lateinit var model: TaskDetailViewModel
        compose.runOnIdle {
            model = TaskDetailViewModel(repository, TaskCompletion(RepositoryTaskCompletionTransport(repository)), handle)
            store.put("detail", model); model.load(10)
        }
        compose.waitUntil(5_000) { !model.state.value.loading }
        compose.runOnIdle {
            assertTrue(model.state.value.dirty)
            assertEquals("Recovered notes", model.state.value.description)
            model.save()
        }
        compose.waitUntil(5_000) { !model.state.value.dirty }
        compose.runOnIdle { assertEquals("Recovered notes", model.state.value.task?.description) }
    }

    @Test fun addingSubtaskUsesSavedResponseWithoutAnotherTaskListRead() {
        val api = SheetApi()
        val repository = TimeboxRepository(api.proxy())
        lateinit var model: TaskDetailViewModel
        compose.runOnIdle {
            model = TaskDetailViewModel(repository, TaskCompletion(RepositoryTaskCompletionTransport(repository)))
            store.put("detail", model); model.load(10)
        }
        compose.waitUntil(5_000) { !model.state.value.loading }
        compose.runOnIdle { model.addSubtask("New checkpoint") }
        compose.waitUntil(5_000) { model.state.value.subtasks.size == 1 }
        compose.runOnIdle {
            assertEquals("New checkpoint", model.state.value.subtasks.single().title)
            assertFalse(model.state.value.saving)
            assertEquals(1, api.reads)
        }
    }

    @Test fun fieldFailureKeepsDraftAndRetryDoesNotReloadOrOverwriteOtherFields() {
        val api = SheetApi()
        val repository = TimeboxRepository(api.proxy())
        var reminderSyncs = 0
        repository.onTaskChanged = { reminderSyncs++ }
        lateinit var model: TaskDetailViewModel
        compose.runOnIdle {
            model = TaskDetailViewModel(repository, TaskCompletion(RepositoryTaskCompletionTransport(repository)))
            store.put("detail", model)
            model.load(10)
        }
        compose.waitUntil(5_000) { !model.state.value.loading }
        compose.runOnIdle { model.saveField(model.state.value.toTaskDetailDraft().copy(description = "Saved notes")) }
        compose.waitUntil(5_000) { !model.state.value.saving }
        compose.runOnIdle {
            assertEquals("Saved notes", model.state.value.description)
            api.failPatch = true
            model.saveField(model.state.value.toTaskDetailDraft().copy(taskTypeId = 1))
        }
        compose.waitUntil(5_000) { model.state.value.saveError != null }
        compose.runOnIdle {
            assertTrue(model.state.value.dirty)
            assertEquals("Saved notes", model.state.value.description)
            assertEquals(1, model.state.value.taskTypeId)
            assertEquals(setOf("task_type_id"), api.patches.last().keys)
            api.failPatch = false
            model.save()
        }
        compose.waitUntil(5_000) { !model.state.value.dirty }
        compose.runOnIdle {
            assertEquals(1, api.reads)
            assertFalse(model.state.value.loading)
            assertEquals("Saved notes", model.state.value.description)
            model.saveField(model.state.value.toTaskDetailDraft().copy(taskTypeId = null))
        }
        compose.waitUntil(5_000) { !model.state.value.saving }
        compose.runOnIdle {
            assertEquals(JsonNull, api.patches.last()["task_type_id"])
            assertEquals("Saved notes", model.state.value.description)
            assertFalse(model.state.value.dirty)
            assertEquals(3, reminderSyncs)
        }
    }

    @Test fun retryAfterPartialCreationResumesAfterSuccessfulSubtasks() {
        val api = SheetApi().apply { failSecondSubtask = true }
        val repository = TimeboxRepository(api.proxy())
        val handle = SavedStateHandle()
        lateinit var model: BattlePlanViewModel
        fun model() = BattlePlanViewModel(repository, TaskCompletion(RepositoryTaskCompletionTransport(repository)), handle,
            readinessCoordinator = createReadyToPlanCoordinator(repository, scope))
        compose.runOnIdle {
            model = model(); store.put("composer", model)
            model.setComposerVisible(true)
            model.updateComposerDraft(TaskComposerDraft(title = "Parent", subtasks = listOf("First", "Second")))
            model.createTask()
        }
        compose.waitUntil(5_000) { model.state.value.composerError != null }
        compose.runOnIdle {
            assertEquals(10, model.state.value.composerCreatedTaskId)
            assertEquals(1, model.state.value.composerNextSubtask)
            assertEquals(listOf("Parent", "First", "Second"), api.creates.map { it.title })
            api.failSecondSubtask = false
            store.clear()
            model = model(); store.put("composer", model)
            model.createTask()
        }
        compose.waitUntil(5_000) { !model.state.value.showComposer }
        compose.runOnIdle {
            assertEquals(listOf("Parent", "First", "Second", "Second"), api.creates.map { it.title })
            assertEquals(1, api.creates.count { it.parentId == null })
        }
    }
}

private class SheetApi {
    @Volatile var reads = 0
    @Volatile var failPatch = false
    @Volatile var failSecondSubtask = false
    val patches = CopyOnWriteArrayList<JsonObject>()
    val creates = CopyOnWriteArrayList<BattleTaskCreateDto>()
    private var task = BattleTaskDto(id = 10, title = "Task", description = "", readyToPlan = false, status = "open", position = 0,
        createdAt = "2026-09-14T00:00:00Z", updatedAt = "2026-09-14T00:00:00Z")
    fun proxy(): TimeboxApi = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
        when (method.name) {
            "listBattleTasks" -> { reads++; BattleTaskListDto(listOf(task), "Asia/Singapore", "2026-09-14T00:00:00Z") }
            "listProjects" -> emptyList<ProjectDto>()
            "listTaskTypes" -> listOf(TaskTypeDto(1, "Work"))
            "patchBattleTask" -> {
                val body = args!![1] as JsonObject
                patches += body
                if (failPatch) throw java.io.IOException("Offline")
                task = task.copy(description = body["description"]?.jsonPrimitive?.content ?: task.description,
                    taskTypeId = if ("task_type_id" in body) body["task_type_id"]?.jsonPrimitive?.intOrNull else task.taskTypeId,
                    version = task.version + 1)
                task
            }
            "createBattleTask" -> {
                val request = args!![0] as BattleTaskCreateDto
                creates += request
                if (request.title == "Second" && failSecondSubtask) throw java.io.IOException("Offline")
                task.copy(title = request.title, parentId = request.parentId)
            }
            else -> error("Unexpected API: ${method.name}")
        }
    } as TimeboxApi
}
