package com.timebox.android.ui.day

import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.BattleTaskDto
import com.timebox.android.data.remote.BattleTaskListDto
import com.timebox.android.data.remote.DayDto
import com.timebox.android.data.remote.DayMetaDto
import com.timebox.android.data.remote.DayPreviewDto
import com.timebox.android.data.remote.LinkedTaskDto
import com.timebox.android.data.remote.TaskCompletionResponseDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.data.remote.TimeBlockDto
import com.timebox.android.data.remote.TimeboxApi
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayWorkModeViewModelTest {
    @Test
    fun `finish session closes Work Mode and explicitly reports Task remains open`() =
        runTest {
        val api = FakeWorkModeApi()
        val viewModel = loadedViewModel(api)

        viewModel.startSelectedWorkMode()
        viewModel.state.first { it.workMode != null || it.message != null }
        assertNotNull("calls=${api.calls}, state=${viewModel.state.value}", viewModel.state.value.workMode)

        viewModel.finishWorkMode(completeTask = false)
        viewModel.state.first { it.workMode == null || it.workMode.saving.not() }

        assertNull(viewModel.state.value.workMode)
        assertEquals("Session finished · Task remains open", viewModel.state.value.message)
        assertTrue("finishActualBlock" in api.calls)
        assertFalse("completeBattleTask" in api.calls)
    }

    @Test
    fun `finish and complete uses the one atomic completion command and exposes exact Undo`() =
        runTest {
        val api = FakeWorkModeApi(removedPlannedBlocks = listOf(41, 42))
        val viewModel = loadedViewModel(api)

        viewModel.startSelectedWorkMode()
        viewModel.state.first { it.workMode != null || it.message != null }
        viewModel.finishWorkMode(completeTask = true)
        viewModel.state.first { it.workMode == null || it.workMode.saving.not() }

        assertNull(viewModel.state.value.workMode)
        assertEquals("Task completed · 2 future Planned Blocks removed", viewModel.state.value.message)
        assertEquals("undo-10", viewModel.state.value.completionUndoToken)
        assertTrue("completeBattleTask" in api.calls)
        assertFalse("finishActualBlock" in api.calls)

        viewModel.undoLastTaskCompletion()
        viewModel.state.first { !it.saving }
        assertEquals("Task completion undone", viewModel.state.value.message)
        assertTrue("undoBattleTaskCompletion" in api.calls)
        assertEquals("2026-08-30T02:00:00Z", api.endedActual.endAt)
    }

    @Test
    fun `completion conflict keeps Work Mode open with actionable feedback`() =
        runTest {
        val api = FakeWorkModeApi(failCompletion = true)
        val viewModel = loadedViewModel(api)
        viewModel.startSelectedWorkMode()
        viewModel.state.first { it.workMode != null || it.message != null }

        viewModel.finishWorkMode(completeTask = true)
        viewModel.state.first { it.workMode?.saving == false }

        assertNotNull(viewModel.state.value.workMode)
        assertEquals(
            "Cannot reach the Timebox API. Check the server address in Settings.",
            viewModel.state.value.workMode?.error,
        )
    }

    @Test
    fun `failed Undo keeps the exact token available for retry`() = runTest {
        val api = FakeWorkModeApi(failUndo = true)
        val viewModel = loadedViewModel(api)
        viewModel.startSelectedWorkMode()
        viewModel.state.first { it.workMode != null || it.message != null }
        viewModel.finishWorkMode(completeTask = true)
        viewModel.state.first { it.workMode == null || it.workMode.saving.not() }

        viewModel.undoLastTaskCompletion()
        viewModel.state.first { !it.saving }

        assertEquals(10, viewModel.state.value.completionUndoTaskId)
        assertEquals("undo-10", viewModel.state.value.completionUndoToken)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.loadedViewModel(api: FakeWorkModeApi): DayViewModel {
        val viewModel = DayViewModel(TimeboxRepository(api.proxy()), this)
        viewModel.load()
        viewModel.state.first { it.day != null || it.error != null }
        viewModel.selectBlock(31)
        return viewModel
    }
}

private class FakeWorkModeApi(
    private val removedPlannedBlocks: List<Int> = emptyList(),
    private val failCompletion: Boolean = false,
    private val failUndo: Boolean = false,
) {
    val calls = mutableListOf<String>()
    private val taskType = TaskTypeDto(3, "coding")
    private val linkedTask = LinkedTaskDto(10, "Ship Android", "open", 3)
    private val task = BattleTaskDto(
        id = 10,
        taskTypeId = 3,
        taskType = taskType,
        title = "Ship Android",
        description = "",
        readyToPlan = false,
        status = "open",
        position = 0,
        createdAt = "2026-08-30T00:00:00Z",
        updatedAt = "2026-08-30T00:00:00Z",
    )
    private val liveActual = ActualBlockDto(
        id = 44,
        taskTypeId = 3,
        taskType = taskType,
        taskId = 10,
        task = linkedTask,
        plannedBlockId = 31,
        startAt = "2026-08-30T01:17:00Z",
        endAt = null,
        createdAt = "2026-08-30T01:17:00Z",
        updatedAt = "2026-08-30T01:17:00Z",
    )
    val endedActual = liveActual.copy(endAt = "2026-08-30T02:00:00Z")

    fun proxy(): TimeboxApi {
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            calls += method.name
            val date = args?.firstOrNull() as? String ?: "2026-08-30"
            val result = runCatching<Any?> {
                when (method.name) {
                    "getDay" -> day(date)
                    "getDayPreview" -> preview(date)
                    "listTaskTypes" -> listOf(taskType)
                    "listBattleTasks" -> BattleTaskListDto(listOf(task), "Asia/Singapore", "2026-08-30T09:17:00+08:00")
                    "startActualBlock", "getActualBlock" -> liveActual
                    "finishActualBlock", "patchActualBlock" -> endedActual
                    "completeBattleTask" -> {
                        if (failCompletion) throw IOException("conflict")
                        TaskCompletionResponseDto(task.copy(status = "completed"), "undo-10", removedPlannedBlocks)
                    }
                    "undoBattleTaskCompletion" -> {
                        if (failUndo) throw IOException("conflict")
                        task
                    }
                    else -> Unit
                }
            }
            @Suppress("UNCHECKED_CAST")
            val continuation = args?.lastOrNull() as? Continuation<Any?>
            if (continuation == null) result.getOrThrow()
            else {
                continuation.resumeWith(result)
                COROUTINE_SUSPENDED
            }
        }
        return Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java), handler) as TimeboxApi
    }

    private fun block() = TimeBlockDto(
        id = 31,
        lane = "planned",
        taskTypeId = 3,
        taskType = taskType,
        taskId = 10,
        task = linkedTask,
        startMinute = 9 * 60,
        endMinute = 10 * 60,
    )

    private fun meta(date: String) = DayMetaDto("Asia/Singapore", date, "${date}T09:17:00+08:00")

    private fun day(date: String) = DayDto(
        id = 1,
        date = date,
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        timeBlocks = listOf(block()),
        meta = meta(date),
    )

    private fun preview(date: String) = DayPreviewDto(
        date = date,
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        timeBlocks = emptyList(),
        meta = meta(date),
    )
}
