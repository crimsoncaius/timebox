package com.timebox.android.ui.day

import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import com.timebox.android.ui.planning.*
import com.timebox.android.ui.readiness.*
import com.timebox.android.ui.taskcompletion.*
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActualResizeTest {
    private val date = LocalDate.parse("2026-09-24")
    private fun projection(id: Int, start: String, end: String, a: Int, b: Int) = ActualBlockDayProjection(
        ActualBlock(id, 1, "Activity", null, null, null, null, Instant.parse(start), Instant.parse(end)),
        date, a, b, b - a,
    )
    private val previous = projection(1, "2026-09-24T17:01:00Z", "2026-09-24T18:17:32.123Z", 1021, 1097)
    private val meal = projection(2, "2026-09-24T18:17:32.123Z", "2026-09-24T21:13:47.456Z", 1097, 1273)
    private val next = projection(3, "2026-09-24T22:00:32.123Z", "2026-09-24T23:00:00Z", 1320, 1380)

    private fun resize(target: ActualBlockDayProjection, start: Int, end: Int, expectedStart: String, expectedEnd: String) = runTest {
        var patch: ActualBlockPatchDto? = null
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "patchActualBlock" -> { patch = args!![1] as ActualBlockPatchDto; error("Captured request") }
                else -> error("Unused ${method.name}")
            }
        } as TimeboxApi
        val repository = TimeboxRepository(api, ioDispatcher = StandardTestDispatcher(testScheduler))
        val readiness = ReadyToPlanCoordinator(object : ReadyToPlanTransport {
            override suspend fun setReady(taskId: Int, ready: Boolean) = error("Unused")
        }, backgroundScope)
        val model = DayViewModel(repository, TaskCompletion(RepositoryTaskCompletionTransport(repository)),
            PlanningSession(RepositoryPlanningSessionTransport(repository, readiness)),
            injectedScope = backgroundScope, readinessCoordinator = readiness)
        val records = listOf(previous, target, next).filter { it.actualBlock.id == target.actualBlock.id ||
            it.actualBlock.endAt!! <= target.actualBlock.startAt || it.actualBlock.startAt >= target.actualBlock.endAt!! }
        val day = Day(date, 0, 24, true, records.map {
            TimeBlock(it.actualBlock.id, Lane.Actual, 1, "Activity", null, null, null, null,
                actualBlockId = it.actualBlock.id, startMinute = it.startMinute, endMinute = it.endMinute)
        }, actualBlocks = records, timezone = "UTC", today = date.plusDays(1), serverNowMinute = 1440, elapsedRealtime = { 0L })
        @Suppress("UNCHECKED_CAST")
        val state = DayViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(model) as MutableStateFlow<DayUiState>
        state.value = DayUiState(date = date, pages = mapOf(date to DayPageState(day = day)))
        model.moveBlock(target.actualBlock.id, start, end)
        runCurrent()
        assertNotNull("Correction must reach persistence", patch)
        assertEquals(Instant.parse(expectedStart), Instant.parse(patch!!.startAt))
        assertEquals(Instant.parse(expectedEnd), Instant.parse(patch!!.endAt))
        assertEquals(records, day.actualBlocks)
    }

    @Test fun endResizePreservesSharedStart() = resize(meal, 1097, 1318, "2026-09-24T18:17:32.123Z", "2026-09-24T21:58:00Z")
    @Test fun startResizePreservesEnd() = resize(meal, 1110, 1273, "2026-09-24T18:30:00Z", "2026-09-24T21:13:47.456Z")
    @Test fun endContactUsesExactNeighborStart() = resize(meal, 1097, 1320, "2026-09-24T18:17:32.123Z", "2026-09-24T22:00:32.123Z")
    @Test fun startContactUsesExactNeighborEnd() = resize(meal.copy(startMinute = 1110, actualBlock = meal.actualBlock.copy(startAt = Instant.parse("2026-09-24T18:30:00Z"))), 1097, 1273, "2026-09-24T18:17:32.123Z", "2026-09-24T21:13:47.456Z")
    @Test fun endResizePreservesPreviousDate() = resize(meal.copy(startMinute = 0, actualBlock = meal.actualBlock.copy(startAt = Instant.parse("2026-09-23T23:30:32.123Z"))), 0, 1318, "2026-09-23T23:30:32.123Z", "2026-09-24T21:58:00Z")
}
