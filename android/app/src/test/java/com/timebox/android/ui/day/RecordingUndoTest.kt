package com.timebox.android.ui.day

import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.PlannedRecordingDto
import com.timebox.android.data.remote.RecordingReplacement
import com.timebox.android.data.remote.RecordingUndoRequest
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.ui.planning.PlanningSession
import com.timebox.android.ui.planning.RepositoryPlanningSessionTransport
import com.timebox.android.ui.readiness.ReadyToPlanCoordinator
import com.timebox.android.ui.readiness.ReadyToPlanTransport
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import com.timebox.android.ui.undo.UndoLifecycle
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingUndoTest {
    @Test fun `tokenless result preserves Undo and a new recording captures its own target`() = runTest {
        val calls = mutableListOf<Int>()
        val undone = mutableListOf<Pair<Int, String>>()
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "recordPlanned" -> {
                    val id = args!![0] as Int
                    calls += id
                    PlannedRecordingDto(
                        status = if (id == 2) "already_recorded" else "recorded",
                        startAt = "2026-09-21T10:00:00Z", endAt = "2026-09-21T11:00:00Z",
                        fingerprint = "test", replacement = RecordingReplacement(),
                        undoToken = if (id == 2) null else "token-$id",
                    )
                }
                "undoRecordPlanned" -> {
                    undone += (args!![0] as Int) to (args[1] as RecordingUndoRequest).undoToken
                    Unit
                }
                else -> error("Unused ${method.name}")
            }
        } as TimeboxApi
        val repository = TimeboxRepository(api, ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val readiness = ReadyToPlanCoordinator(object : ReadyToPlanTransport {
            override suspend fun setReady(taskId: Int, ready: Boolean) = error("Unused")
        }, backgroundScope)
        val lifecycle = UndoLifecycle(backgroundScope) { testScheduler.currentTime }
        val model = DayViewModel(repository, TaskCompletion(RepositoryTaskCompletionTransport(repository)),
            PlanningSession(RepositoryPlanningSessionTransport(repository, readiness)),
            injectedScope = backgroundScope, readinessCoordinator = readiness, injectedUndoLifecycle = lifecycle)

        model.selectBlock(1); model.recordPlanned(); runCurrent()
        assertEquals(1, lifecycle.notice.value?.targetId)
        model.selectBlock(2); model.recordPlanned(); runCurrent()
        assertEquals(1, lifecycle.notice.value?.targetId)
        assertEquals(2 to "Already recorded", model.state.value.recordingNotice)
        model.selectBlock(3); model.recordPlanned()
        model.selectBlock(4); model.recordPlanned()
        runCurrent()
        assertEquals(listOf(1, 2, 3), calls)
        assertEquals(3, lifecycle.notice.value?.targetId)
        model.undoRecording(); runCurrent()
        assertEquals(listOf(3 to "token-3"), undone)
    }
}
