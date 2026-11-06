package com.timebox.android.ui.battleplan

import androidx.lifecycle.viewModelScope
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import com.timebox.android.ui.readiness.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnavailablePreplanningActionTest {
    @Test fun explicitReadinessActionWaitsForConfirmationAndSurfacesRejection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stamp = "2026-11-06T00:00:00Z"
        val original = task(10)
        var fail = false
        val gate = CompletableDeferred<Unit>()
        val transport = object : ReadyToPlanTransport {
            override suspend fun setReady(taskId: Int, ready: Boolean): Result<com.timebox.android.data.BattleTask> {
                gate.await()
                return if (fail) Result.failure(java.io.IOException("Offline")) else Result.success(original.copy(readyToPlan = true, version = 2))
            }
            override suspend fun reconcile(taskId: Int) = Result.success(original)
        }
        val coordinator = ReadyToPlanCoordinator(transport, backgroundScope)
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, _ ->
            when (method.name) {
                "listBattleTasks" -> BattleTaskListDto(listOf(BattleTaskDto(10, title = "Task", description = "", readyToPlan = false,
                    status = "open", position = 0, createdAt = stamp, updatedAt = stamp)), "UTC", stamp)
                else -> error(method.name)
            }
        } as TimeboxApi
        val model = RecurringEditorViewModel(TimeboxRepository(api, StandardTestDispatcher(testScheduler)), coordinator)
        try {
            model.addUnavailableToReady(10); runCurrent()
            assertTrue(model.state.value.saving)
            assertNull(model.state.value.message)
            gate.complete(Unit); runCurrent()
            assertFalse(model.state.value.saving)
            assertEquals("Task added to Ready to Plan", model.state.value.message)
            // A later server snapshot and explicit retry must report failure, never an optimistic success.
            coordinator.mergeServerTasks(listOf(original.copy(version = 3)))
            model.consumeMessage(); fail = true
            model.addUnavailableToReady(10); runCurrent()
            assertFalse(model.state.value.saving)
            assertNotNull(model.state.value.saveError)
            assertNull(model.state.value.message)
        } finally { gate.complete(Unit); model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
