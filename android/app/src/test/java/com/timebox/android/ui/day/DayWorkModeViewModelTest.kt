package com.timebox.android.ui.day

import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.WorkModeSnapshot
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.ActualBlockCreateDto
import com.timebox.android.data.remote.ActualBlockDayProjectionDto
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
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import com.timebox.android.ui.planning.PlanningSession
import com.timebox.android.ui.planning.RepositoryPlanningSessionTransport
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import java.lang.reflect.Proxy
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayWorkModeViewModelTest {
    @Test
    fun `requesting Today again while already there asks the timeline to scroll to the Now Line`() = runTest {
        val viewModel = loadedViewModel(
            FakeWorkModeApi(listOf(block(31, 540, 600))),
            FakeClock("2026-08-30T01:30:00Z"),
            FakeWorkModePersistence(),
        )
        val today = java.time.LocalDate.parse("2026-08-30")
        assertEquals(today, viewModel.state.value.date)
        assertEquals(today, viewModel.state.value.today)
        assertEquals(0, viewModel.state.value.scrollToNowRequest)
        viewModel.goToDate(today)
        assertEquals(0, viewModel.state.value.scrollToNowRequest)
        viewModel.goToDate(today, scrollToNow = true)
        assertEquals(1, viewModel.state.value.scrollToNowRequest)
        assertFalse(viewModel.state.value.skipScrollToNow)
    }

    @Test
    fun `a Block deep link suppresses scroll to the Now Line until Today is requested again`() = runTest {
        val viewModel = loadedViewModel(
            FakeWorkModeApi(listOf(block(31, 540, 600))),
            FakeClock("2026-08-30T01:30:00Z"),
            FakeWorkModePersistence(),
        )
        viewModel.noteBlockLanding()
        assertTrue(viewModel.state.value.skipScrollToNow)
        viewModel.goToDate(java.time.LocalDate.parse("2026-08-30"), scrollToNow = true)
        assertFalse(viewModel.state.value.skipScrollToNow)
        assertEquals(1, viewModel.state.value.scrollToNowRequest)
    }

    @Test
    fun `recording saves text then confirms the frozen preview and undoes`() = runTest {
        val api = FakeWorkModeApi(listOf(block(31, 540, 600)))
        val viewModel = loadedViewModel(api, FakeClock("2026-08-30T01:30:00Z"), FakeWorkModePersistence())
        viewModel.selectBlock(31)
        viewModel.onNameChange("Edited name")
        viewModel.onNoteChange("Edited note")
        viewModel.recordPlanned()
        viewModel.state.first { it.recordingPreview != null && !it.saving }
        assertTrue(api.calls.indexOf("patchBlock") < api.calls.indexOf("recordPlanned"))
        assertNull(api.recordRequests[0].fingerprint)
        val preview = viewModel.state.value.recordingPreview!!.second
        viewModel.recordPlanned()
        viewModel.state.first { it.recordingUndo != null && !it.saving }
        assertEquals("Actual recorded", viewModel.state.value.recordingNotice?.second)
        assertEquals(preview.endAt, api.recordRequests[1].until)
        assertEquals(preview.fingerprint, api.recordRequests[1].fingerprint)
        assertNull(viewModel.state.value.recordingPreview)
        viewModel.undoRecording()
        viewModel.state.first { it.recordingUndo == null && !it.saving }
        assertEquals("Recording undone", viewModel.state.value.recordingNotice?.second)
        assertTrue("undoRecordPlanned" in api.calls)
    }

    @Test
    fun `recording cancel discards the preview without sending replacement`() = runTest {
        val api = FakeWorkModeApi(listOf(block(31, 540, 600)))
        val viewModel = loadedViewModel(api, FakeClock("2026-08-30T01:30:00Z"), FakeWorkModePersistence())
        viewModel.selectBlock(31)
        viewModel.recordPlanned()
        viewModel.state.first { it.recordingPreview != null && !it.saving }
        viewModel.cancelRecordingPreview()
        assertNull(viewModel.state.value.recordingPreview)
        assertEquals(1, api.recordRequests.size)
        assertNull(viewModel.state.value.recordingUndo)
    }
    @Test
    fun `Actual drop does not revert while activity correction is pending`() = runTest {
        val at = "2026-08-30T00:00:00Z"
        val type = TaskTypeDto(3, "coding")
        val row = ActualBlockDto(44, 3, type, startAt = at, endAt = "2026-08-30T01:00:00Z", createdAt = at, updatedAt = at)
        val snapshot = com.timebox.android.data.remote.ActivitySnapshotDto(cursor = 1,
            serverAt = "2026-08-30T04:00:00Z", reportingTimezone = "Asia/Singapore", offlineReady = true,
            current = null, records = listOf(row), taskTypes = listOf(type))
        val gate = CompletableDeferred<Unit>()
        val activity = com.timebox.android.data.ActivityRepository(object : com.timebox.android.data.ActivityTransport {
            override suspend fun read() = snapshot
            override suspend fun execute(command: com.timebox.android.data.remote.ActivityCommandDto): com.timebox.android.data.remote.ActivitySnapshotDto {
                gate.await()
                return snapshot.copy(cursor = 2,
                    records = listOf(row.copy(startAt = command.effective.at!!, endAt = command.effective.end)),
                    acknowledgement = com.timebox.android.data.remote.ActivityAcknowledgementDto(command.operationId,
                        com.timebox.android.data.remote.ActivityOutcome.Applied))
            }
        }, object : com.timebox.android.data.ActivityStorage {
            override fun load(): String? = null
            override fun save(value: String) {}
        })
        activity.refresh()
        // Keep the Day fetch on the test scheduler so it lands before the drop, not whenever IO finishes.
        val repo = TimeboxRepository(FakeWorkModeApi(emptyList()).proxy(), StandardTestDispatcher(testScheduler))
        val readiness = createReadyToPlanCoordinator(repo, backgroundScope)
        val vm = DayViewModel(repo, TaskCompletion(RepositoryTaskCompletionTransport(repo)),
            PlanningSession(RepositoryPlanningSessionTransport(repo, readiness)), injectedScope = backgroundScope,
            readinessCoordinator = readiness, activityRepository = activity,
            workModePersistence = FakeWorkModePersistence())
        vm.load(java.time.LocalDate.parse("2026-08-30"))
        runCurrent()
        val observed = mutableListOf<Int>()
        val collecting = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { state -> state.day?.blocks?.find { it.actualBlockId == 44 }?.let { observed += it.startMinute } }
        }
        observed.clear()
        vm.moveBlock(44, 495, 555)
        runCurrent()
        val whilePending = observed.toList()
        gate.complete(Unit)
        runCurrent()
        collecting.cancel()
        val bridge = DayViewModel::class.java.getDeclaredField("workModeBridgeScope").apply { isAccessible = true }
        (bridge.get(vm) as kotlinx.coroutines.CoroutineScope).cancel()
        assertTrue("Observed $observed", whilePending.isNotEmpty())
        assertTrue("Actual jumped during save: $observed", observed.all { it == 495 })
    }

    @Test
    fun `resizing Actual Block persists Actual timestamps`() = runTest {
        val api = FakeWorkModeApi(
            blocks = emptyList(),
            actualBlocks = listOf(actualProjection()),
        )
        val viewModel = loadedViewModel(
            api,
            FakeClock("2026-08-30T01:17:00Z"),
            FakeWorkModePersistence(),
        )
        assertEquals(listOf(-44), viewModel.state.value.day?.blocks?.map { it.id })

        // Actual time ends at the server's now (09:17), so the resize stays in the past.
        viewModel.moveBlock(-44, 8 * 60 + 30, 9 * 60 + 15)
        advanceUntilIdle()

        assertEquals(
            "calls=${api.calls}, message=${viewModel.state.value.message}",
            listOf(44),
            api.patchedActualIds,
        )
        assertEquals("2026-08-30T00:30:00Z", api.patchedActualBodies.single().startAt)
        assertEquals("2026-08-30T01:15:00Z", api.patchedActualBodies.single().endAt)
        assertFalse("patchBlock" in api.calls)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.loadedViewModel(
        api: FakeWorkModeApi,
        clock: FakeClock,
        persistence: FakeWorkModePersistence,
    ): DayViewModel {
        val repository = TimeboxRepository(api.proxy())
        val readinessCoordinator = createReadyToPlanCoordinator(repository, this)
        val viewModel = DayViewModel(
            repository = repository,
            injectedScope = this,
            clock = clock::now,
            workModeTickMillis = 1_000,
            workModePersistence = persistence,
            taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository)),
            planningSession = PlanningSession(
                RepositoryPlanningSessionTransport(repository, readinessCoordinator),
            ),
            readinessCoordinator = readinessCoordinator,
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
}

private class FakeWorkModePersistence(initial: WorkModeSnapshot? = null, private val loadGate: CompletableDeferred<WorkModeSnapshot?>? = null) : WorkModePersistence {
    var snapshot: WorkModeSnapshot? = initial
    override suspend fun load(): WorkModeSnapshot? = loadGate?.await() ?: snapshot
    override suspend fun save(snapshot: WorkModeSnapshot?) { this.snapshot = snapshot }
}

private class FakeWorkModeApi(
    private val blocks: List<TimeBlockDto>,
    private val active: ActualBlockDto? = null,
    private val actualBlocks: List<ActualBlockDayProjectionDto> = emptyList(),
) {
    val calls = mutableListOf<String>()
    val startedBodies = mutableListOf<ActualBlockStartDto>()
    val createdBodies = mutableListOf<ActualBlockCreateDto>()
    val patchedActualIds = mutableListOf<Int>()
    val patchedActualBodies = mutableListOf<ActualBlockPatchDto>()
    val recordRequests = mutableListOf<com.timebox.android.data.remote.PlannedRecordingRequest>()
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
                    "patchBlock" -> day(date)
                    "recordPlanned" -> {
                        val body = args!![1] as com.timebox.android.data.remote.PlannedRecordingRequest
                        recordRequests += body
                        com.timebox.android.data.remote.PlannedRecordingDto(
                            status = if (body.fingerprint == null) "confirmation_required" else "recorded",
                            startAt = "2026-08-30T01:00:00Z", endAt = "2026-08-30T01:30:00Z", fingerprint = "observed",
                            replacement = com.timebox.android.data.remote.RecordingReplacement("Edited name", "Edited note"),
                            undoToken = if (body.fingerprint == null) null else "undo-token",
                        )
                    }
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
                        patchedActualIds += args?.get(0) as Int
                        val body = args?.get(1) as ActualBlockPatchDto
                        patchedActualBodies += body
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
        timeBlocks = blocks, actualBlocks = actualBlocks, meta = meta(date),
    )
    private fun preview(date: String) = DayPreviewDto(
        date = date, startHour = 8, endHour = 20, showFullDay = false,
        // Browsing adjacent days does not change the server's present date.
        timeBlocks = emptyList(), meta = meta("2026-08-30"),
    )
}

private fun block(id: Int, start: Int, end: Int) = TimeBlockDto(
    id = id, lane = "planned", taskTypeId = 3, taskType = TaskTypeDto(3, "coding"),
    taskId = 10, task = LinkedTaskDto(10, "Ship Android", "open", 3),
    startMinute = start, endMinute = end,
)

private fun actualProjection() = ActualBlockDayProjectionDto(
    actualBlock = ActualBlockDto(
        id = 44,
        taskTypeId = 3,
        taskType = TaskTypeDto(3, "coding"),
        taskId = 10,
        task = LinkedTaskDto(10, "Existing work", "open", 3),
        plannedBlockId = null,
        startAt = "2026-08-30T01:00:00Z",
        endAt = "2026-08-30T02:00:00Z",
        createdAt = "2026-08-30T01:00:00Z",
        updatedAt = "2026-08-30T02:00:00Z",
    ),
    date = "2026-08-30",
    startMinute = 9 * 60,
    endMinute = 10 * 60,
    durationMinutes = 60,
)
