package com.timebox.android.ui.day

import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.WorkModeSnapshot
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.ActualBlockCreateDto
import com.timebox.android.data.remote.ActualBlockPatchDto
import com.timebox.android.data.remote.ActualBlockStartDto
import com.timebox.android.data.remote.BattleTaskDto
import com.timebox.android.data.remote.BattleTaskListDto
import com.timebox.android.data.remote.DayDto
import com.timebox.android.data.remote.DayMetaDto
import com.timebox.android.data.remote.DayPreviewDto
import com.timebox.android.data.remote.LinkedTaskDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.data.remote.TimeBlockDto
import com.timebox.android.data.remote.TimeboxApi
import java.lang.reflect.Proxy
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayWorkModeViewModelTest {
    @Test
    fun `current planned work confirms for one minute then Exit preserves Task state`() = runTest {
        val clock = FakeClock("2026-08-30T01:17:00Z")
        val api = FakeWorkModeApi(blocks = listOf(block(31, 9 * 60, 10 * 60)))
        val persistence = FakeWorkModePersistence()
        val viewModel = loadedViewModel(api, clock, persistence)

        viewModel.startWorkMode()
        viewModel.state.first { it.workMode != null || it.workModeEntryWarning || it.message != null }
        assertNotNull(viewModel.state.value.workMode)
        assertFalse("startActualBlock" in api.calls)

        clock.advanceSeconds(59)
        advanceTimeBy(59_000)
        runCurrent()
        assertFalse("startActualBlock" in api.calls)

        clock.advanceSeconds(1)
        advanceTimeBy(1_001)
        viewModel.state.first { it.workMode?.activeActual != null || it.workMode?.error != null }
        assertEquals("2026-08-30T01:17:00Z", api.startedBodies.single().startAt)
        assertEquals(31, api.startedBodies.single().plannedBlockId)
        assertNotNull(viewModel.state.value.workMode?.activeActual)

        viewModel.exitWorkMode()
        viewModel.state.first { it.workMode == null }
        assertNull(viewModel.state.value.workMode)
        assertTrue("patchActualBlock" in api.calls)
        assertFalse("completeBattleTask" in api.calls)
        assertNull(persistence.snapshot)
    }

    @Test
    fun `entry guard includes exactly ten minutes and warns above it`() = runTest {
        val clock = FakeClock("2026-08-30T01:17:00Z")
        val exact = loadedViewModel(
            FakeWorkModeApi(blocks = listOf(block(31, 9 * 60 + 27, 10 * 60))),
            clock,
            FakeWorkModePersistence(),
        )
        exact.startWorkMode()
        exact.state.first { it.workMode != null || it.workModeEntryWarning || it.message != null }
        assertNotNull(exact.state.value.workMode)
        assertEquals(31, exact.state.value.workMode?.nextBlock?.id)
        exact.exitWorkMode()
        exact.state.first { it.workMode == null }

        val later = loadedViewModel(
            FakeWorkModeApi(blocks = listOf(block(41, 9 * 60 + 28, 10 * 60))),
            clock,
            FakeWorkModePersistence(),
        )
        later.startWorkMode()
        later.state.first { it.workMode != null || it.workModeEntryWarning || it.message != null }
        assertNull(later.state.value.workMode)
        assertTrue(later.state.value.workModeEntryWarning)
        later.continueWorkModeEntry()
        later.state.first { it.workMode != null || it.message != null }
        assertNotNull(later.state.value.workMode)
        later.exitWorkMode()
        later.state.first { it.workMode == null }
    }

    @Test
    fun `short restart restores original entry while a long absence asks first`() = runTest {
        val shortSnapshot = WorkModeSnapshot(
            entryAt = "2026-08-30T01:00:00Z",
            lastConfirmedAt = "2026-08-30T01:16:00Z",
            lastObservedAt = "2026-08-30T01:16:00Z",
        )
        val short = loadedViewModel(
            FakeWorkModeApi(blocks = listOf(block(31, 9 * 60, 10 * 60))),
            FakeClock("2026-08-30T01:17:00Z"),
            FakeWorkModePersistence(shortSnapshot),
        )
        assertEquals(Instant.parse(shortSnapshot.entryAt), short.state.value.workMode?.entryAt)
        assertFalse(short.state.value.workModeRestorePrompt)
        short.exitWorkMode()
        runCurrent()

        val longStore = FakeWorkModePersistence(shortSnapshot.copy(lastObservedAt = "2026-08-30T01:00:00Z"))
        val long = loadedViewModel(
            FakeWorkModeApi(blocks = listOf(block(31, 9 * 60, 10 * 60))),
            FakeClock("2026-08-30T01:17:00Z"),
            longStore,
        )
        assertTrue(long.state.value.workModeRestorePrompt)
        long.declineWorkContinued()
        long.state.first { it.workMode == null }
        assertNull(long.state.value.workMode)
        assertNull(longStore.snapshot)
    }

    @Test
    fun `existing active Actual owns Work Mode without starting a conflicting Actual`() = runTest {
        val clock = FakeClock("2026-08-30T01:17:00Z")
        val active = activeActual(plannedBlockId = 41)
        val api = FakeWorkModeApi(
            blocks = listOf(block(31, 9 * 60, 10 * 60), block(41, 10 * 60, 11 * 60)),
            active = active,
        )
        val viewModel = loadedViewModel(api, clock, FakeWorkModePersistence())

        viewModel.startWorkMode()
        viewModel.state.first { it.workMode != null || it.message != null }
        assertEquals(41, viewModel.state.value.workMode?.currentBlock?.id)
        assertEquals(active.id, viewModel.state.value.workMode?.activeActual?.id)

        clock.advanceSeconds(61)
        advanceTimeBy(61_001)
        runCurrent()
        assertTrue(api.startedBodies.isEmpty())
        viewModel.exitWorkMode()
        viewModel.state.first { it.workMode == null }
    }

    @Test
    fun `midnight records the confirmed final block and follows the new day`() = runTest {
        val clock = FakeClock("2026-08-30T15:59:00Z")
        val api = FakeWorkModeApi(blocks = listOf(block(31, 23 * 60 + 59, 24 * 60)))
        val viewModel = loadedViewModel(api, clock, FakeWorkModePersistence())

        viewModel.startWorkMode()
        viewModel.state.first { it.workMode != null || it.message != null }
        runCurrent()
        clock.advanceSeconds(61)
        advanceTimeBy(61_001)
        viewModel.state.first { it.date.toString() == "2026-08-31" || it.workMode?.error != null }

        assertEquals("2026-08-30T16:00:00Z", api.createdBodies.single().endAt)
        assertEquals("2026-08-31", viewModel.state.value.date.toString())
        viewModel.exitWorkMode()
        viewModel.state.first { it.workMode == null }
    }

    @Test
    fun `restart catch-up requires a full uninterrupted minute before the boundary`() = runTest {
        val block = block(31, 9 * 60 + 17, 9 * 60 + 18)
        val shortApi = FakeWorkModeApi(blocks = listOf(block))
        val short = loadedViewModel(
            shortApi,
            FakeClock("2026-08-30T01:18:10Z"),
            FakeWorkModePersistence(
                WorkModeSnapshot(
                    entryAt = "2026-08-30T01:17:50Z",
                    lastConfirmedAt = "2026-08-30T01:17:50Z",
                    lastObservedAt = "2026-08-30T01:18:10Z",
                    confirmingPlannedBlockId = 31,
                    confirmationStartedAt = "2026-08-30T01:17:50Z",
                ),
            ),
        )
        runCurrent()
        assertTrue(shortApi.createdBodies.isEmpty())
        short.exitWorkMode()
        short.state.first { it.workMode == null }

        val fullApi = FakeWorkModeApi(blocks = listOf(block))
        val full = loadedViewModel(
            fullApi,
            FakeClock("2026-08-30T01:18:10Z"),
            FakeWorkModePersistence(
                WorkModeSnapshot(
                    entryAt = "2026-08-30T01:17:00Z",
                    lastConfirmedAt = "2026-08-30T01:17:00Z",
                    lastObservedAt = "2026-08-30T01:18:10Z",
                    confirmingPlannedBlockId = 31,
                    confirmationStartedAt = "2026-08-30T01:17:00Z",
                ),
            ),
        )
        runCurrent()
        assertEquals("2026-08-30T01:18:00Z", fullApi.createdBodies.single().endAt)
        full.exitWorkMode()
        full.state.first { it.workMode == null }
    }

    @Test
    fun `long absence recovery does not duplicate the active block ended at its boundary`() = runTest {
        val active = activeActual(plannedBlockId = 31)
        val api = FakeWorkModeApi(
            blocks = listOf(block(31, 9 * 60, 9 * 60 + 30)),
            active = active,
        )
        val viewModel = loadedViewModel(
            api,
            FakeClock("2026-08-30T01:40:00Z"),
            FakeWorkModePersistence(
                WorkModeSnapshot(
                    entryAt = "2026-08-30T01:00:00Z",
                    lastConfirmedAt = "2026-08-30T01:10:00Z",
                    lastObservedAt = "2026-08-30T01:20:00Z",
                    activeActualId = active.id,
                    activePlannedBlockId = 31,
                    activePlannedEndAt = "2026-08-30T01:30:00Z",
                ),
            ),
        )
        assertTrue(viewModel.state.value.workModeRestorePrompt)

        viewModel.confirmWorkContinued()
        viewModel.state.first { !it.workModeRestorePrompt || it.workMode?.error != null }
        assertTrue(api.createdBodies.isEmpty())
        assertTrue("patchActualBlock" in api.calls)
        assertNull(viewModel.state.value.workMode?.error)
        viewModel.exitWorkMode()
        viewModel.state.first { it.workMode == null }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.loadedViewModel(
        api: FakeWorkModeApi,
        clock: FakeClock,
        persistence: FakeWorkModePersistence,
    ): DayViewModel {
        val viewModel = DayViewModel(
            repository = TimeboxRepository(api.proxy()),
            injectedScope = this,
            clock = clock::now,
            workModeTickMillis = 1_000,
            workModePersistence = persistence,
        )
        viewModel.load(java.time.LocalDate.parse("2026-08-30"))
        viewModel.state.first { it.day != null || it.error != null }
        if (persistence.snapshot != null) viewModel.state.first { it.workMode != null }
        return viewModel
    }
}

private class FakeClock(initial: String) {
    private var instant = Instant.parse(initial)
    fun now(): Instant = instant
    fun advanceSeconds(seconds: Long) { instant = instant.plusSeconds(seconds) }
}

private class FakeWorkModePersistence(initial: WorkModeSnapshot? = null) : WorkModePersistence {
    var snapshot: WorkModeSnapshot? = initial
    override suspend fun load(): WorkModeSnapshot? = snapshot
    override suspend fun save(snapshot: WorkModeSnapshot?) { this.snapshot = snapshot }
}

private class FakeWorkModeApi(
    private val blocks: List<TimeBlockDto>,
    private val active: ActualBlockDto? = null,
) {
    val calls = mutableListOf<String>()
    val startedBodies = mutableListOf<ActualBlockStartDto>()
    val createdBodies = mutableListOf<ActualBlockCreateDto>()
    private val taskType = TaskTypeDto(3, "coding")
    private val linkedTask = LinkedTaskDto(10, "Ship Android", "open", 3)
    private val task = BattleTaskDto(
        id = 10, taskTypeId = 3, taskType = taskType, title = "Ship Android",
        description = "Keep the release small.", readyToPlan = false, status = "open", position = 0,
        createdAt = "2026-08-30T00:00:00Z", updatedAt = "2026-08-30T00:00:00Z",
    )

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
                    "getActiveActualBlock" -> active
                    "startActualBlock" -> {
                        val body = args?.first() as ActualBlockStartDto
                        startedBodies += body
                        actual(body)
                    }
                    "createActualBlock" -> {
                        val body = args?.first() as ActualBlockCreateDto
                        createdBodies += body
                        actual(ActualBlockStartDto(plannedBlockId = body.plannedBlockId, startAt = body.startAt))
                            .copy(endAt = body.endAt)
                    }
                    "patchActualBlock" -> {
                        val body = args?.get(1) as ActualBlockPatchDto
                        actual(startedBodies.lastOrNull() ?: ActualBlockStartDto(plannedBlockId = 31)).copy(endAt = body.endAt)
                    }
                    else -> Unit
                }
            }
            @Suppress("UNCHECKED_CAST")
            val continuation = args?.lastOrNull() as? Continuation<Any?>
            if (continuation == null) result.getOrThrow() else {
                continuation.resumeWith(result)
                COROUTINE_SUSPENDED
            }
        }
        return Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java), handler) as TimeboxApi
    }

    private fun actual(body: ActualBlockStartDto) = ActualBlockDto(
        id = 44, taskTypeId = 3, taskType = taskType, taskId = 10, task = linkedTask,
        plannedBlockId = body.plannedBlockId, startAt = body.startAt ?: "2026-08-30T01:17:00Z",
        endAt = null, createdAt = "2026-08-30T01:17:00Z", updatedAt = "2026-08-30T01:17:00Z",
    )

    private fun meta(date: String) = DayMetaDto("Asia/Singapore", date, "${date}T09:17:00+08:00")
    private fun day(date: String) = DayDto(
        id = 1, date = date, startHour = 8, endHour = 20, showFullDay = false,
        timeBlocks = blocks, meta = meta(date),
    )
    private fun preview(date: String) = DayPreviewDto(
        date = date, startHour = 8, endHour = 20, showFullDay = false,
        timeBlocks = emptyList(), meta = meta(date),
    )
}

private fun block(id: Int, start: Int, end: Int) = TimeBlockDto(
    id = id, lane = "planned", taskTypeId = 3, taskType = TaskTypeDto(3, "coding"),
    taskId = 10, task = LinkedTaskDto(10, "Ship Android", "open", 3),
    startMinute = start, endMinute = end,
)

private fun activeActual(plannedBlockId: Int?) = ActualBlockDto(
    id = 88,
    taskTypeId = 3,
    taskType = TaskTypeDto(3, "coding"),
    taskId = 10,
    task = LinkedTaskDto(10, "Existing active work", "open", 3),
    plannedBlockId = plannedBlockId,
    startAt = "2026-08-30T01:00:00Z",
    endAt = null,
    createdAt = "2026-08-30T01:00:00Z",
    updatedAt = "2026-08-30T01:00:00Z",
)
