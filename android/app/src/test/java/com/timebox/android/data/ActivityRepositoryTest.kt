package com.timebox.android.data

import com.timebox.android.data.remote.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ActivityRepositoryTest {
    @Test fun checkInConfirmationAndDismissalAreDurableOfflineWithoutChangingActivity() = runTest {
        val at = "2026-09-11T10:00:00Z"
        val row = ActualBlockDto(1, 1, TaskTypeDto(1, "Reading"), startAt = at, endAt = null, createdAt = at, updatedAt = at)
        val initial = ActivitySnapshotDto(cursor = 1, serverAt = at, reportingTimezone = "UTC", offlineReady = true,
            current = row, records = listOf(row), checkIn = CheckInStateDto(generation = "a", rearm = 0, armedAt = at, question = CheckInQuestionDto("a:0", at)))
        var durable: String? = null
        val store = object : ActivityStorage { override fun load() = durable; override fun save(value: String) { durable = value } }
        val transport = object : ActivityTransport { override suspend fun read() = initial; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline") }
        val repository = ActivityRepository(transport, store)
        repository.refresh()
        repository.dismissCheckIn("a:0")
        val dismissed = ActivityRepository(transport, store)
        assertTrue(dismissed.checkInDismissed("a:0"))
        assertEquals("a:0", dismissed.state.value.snapshot!!.checkIn!!.question!!.id)
        assertTrue(dismissed.checkIn(CheckInEventDto("confirm", questionId = "a:0")))
        val restored = ActivityRepository(transport, store)
        assertNull(restored.state.value.snapshot!!.checkIn!!.question)
        assertEquals(row, restored.state.value.snapshot!!.current)
        assertTrue(restored.state.value.pending)
    }

    @Test fun describeUnknownRetainsIdentityAcrossOfflineRestart() = runTest {
        val at = "2026-09-11T10:00:00Z"
        val type = TaskTypeDto(1, "unspecified")
        val current = ActualBlockDto(7, 1, type, startAt = at, createdAt = at, updatedAt = at)
        val initial = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = at, reportingTimezone = "UTC", current = current, records = listOf(current), taskTypes = listOf(type, TaskTypeDto(2, "Reading")))
        var durable: String? = null
        val store = object : ActivityStorage { override fun load() = durable; override fun save(value: String) { durable = value } }
        val transport = object : ActivityTransport { override suspend fun read() = initial; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline") }
        val repository = ActivityRepository(transport, store)
        repository.refresh()
        assertTrue(repository.command(ActivityKind.Describe, 2, observedTargetId = 7))
        val restored = ActivityRepository(transport, store)
        assertEquals(1, restored.state.value.snapshot!!.records.size)
        assertEquals(7, restored.state.value.snapshot!!.current!!.id)
        assertEquals(at, restored.state.value.snapshot!!.current!!.startAt)
        assertEquals("Reading", restored.state.value.snapshot!!.current!!.taskType.name)
        assertFalse(restored.command(ActivityKind.Describe, 2, observedTargetId = 7))
    }

    @Test fun historicalCorrectionsRejectOverlapAndKeepGapsOffline() = runTest {
        val type = TaskTypeDto(1, "work")
        val writing = ActualBlockDto(1, 1, type, name = "Writing", startAt = "2026-09-10T10:00:00Z", endAt = "2026-09-10T12:00:00Z", createdAt = "2026-09-10T10:00:00Z", updatedAt = "2026-09-10T10:00:00Z")
        val current = writing.copy(id = 2, name = "Reading", startAt = "2026-09-11T10:00:00Z", endAt = null)
        val initial = ActivitySnapshotDto(cursor = 2, serverAt = "2026-09-11T13:00:00Z", reportingTimezone = "UTC", offlineReady = true, current = current, records = listOf(writing, current), taskTypes = listOf(type))
        var durable: String? = null
        val store = object : ActivityStorage { override fun load() = durable; override fun save(value: String) { durable = value } }
        val transport = object : ActivityTransport { override suspend fun read() = initial; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline") }
        val repository = ActivityRepository(transport, store)
        repository.refresh()
        assertFalse(repository.correct(ActivityKind.Add, startAt = "2026-09-10T11:00:00Z", endAt = "2026-09-10T11:30:00Z", name = "Lunch", taskTypeId = 1))
        assertTrue(repository.correct(ActivityKind.Edit, 1, endAt = "2026-09-10T11:00:00Z"))
        assertTrue(repository.correct(ActivityKind.Add, startAt = "2026-09-10T11:00:00Z", endAt = "2026-09-10T11:30:00Z", name = "Lunch", taskTypeId = 1))
        val restored = ActivityRepository(transport, store)
        assertTrue(restored.correct(ActivityKind.Delete, 1))
        assertEquals(listOf("Lunch", "Reading"), restored.state.value.snapshot!!.records.map { it.name }.sortedBy { it })
        assertEquals(current, restored.state.value.snapshot!!.current)
    }

    @Test fun planSnapshotSurvivesRestartAndExplicitSwitchClearsItsLinks() = runTest {
        val at = "2026-09-11T10:00:00Z"
        val plan = ActivityPlanDto(4, 2, 7, "Chapter", "Outline", at, "2026-09-11T11:00:00Z")
        val initial = ActivitySnapshotDto(offlineReady = true, cursor = 0, serverAt = at, reportingTimezone = "UTC",
            current = null, records = emptyList(), taskTypes = listOf(TaskTypeDto(2, "Writing")), plans = listOf(plan))
        var durable: String? = null
        var online = true
        val store = object : ActivityStorage {
            override fun load() = durable
            override fun save(value: String) { durable = value }
        }
        val transport = object : ActivityTransport {
            override suspend fun read(): ActivitySnapshotDto { check(online); return initial }
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline")
        }
        var clock = java.time.Instant.parse(at).toEpochMilli()
        val repository = ActivityRepository(transport, store, { clock }, { 0 })
        repository.refresh()
        online = false
        assertTrue(repository.command(ActivityKind.Start))
        val restored = ActivityRepository(transport, store, { clock }, { 0 })
        assertEquals(4, restored.state.value.snapshot?.current?.plannedBlockId)
        assertEquals(7, restored.state.value.snapshot?.current?.taskId)
        assertEquals("Outline", restored.state.value.snapshot?.current?.note)
        clock += 60_000
        assertTrue(restored.command(ActivityKind.Switch, 2, "Break"))
        assertNull(restored.state.value.snapshot?.current?.plannedBlockId)
        assertNull(restored.state.value.snapshot?.current?.taskId)
        assertNotNull(restored.currentPlan())
        clock += 60_000
        assertTrue(restored.command(ActivityKind.Switch, plan = plan))
        assertEquals(2, restored.state.value.snapshot?.records?.count { it.plannedBlockId == 4 })
        clock += 3_600_000
        assertNull(restored.currentPlan())
        assertEquals(4, restored.state.value.snapshot?.current?.plannedBlockId)
    }

    @Test fun lostAcknowledgementSurvivesRestartAndOldReadCannotStopRecording() = runTest {
        val initial = ActivitySnapshotDto(offlineReady = true, cursor = 0, serverAt = "2026-09-11T10:00:00Z", reportingTimezone = "UTC", current = null, records = emptyList())
        val current = ActualBlockDto(1, 1, TaskTypeDto(1, "unspecified"), startAt = initial.serverAt, createdAt = initial.serverAt, updatedAt = initial.serverAt)
        var durable: String? = null
        val store = object : ActivityStorage {
            override fun load() = durable
            override fun save(value: String) { durable = value }
        }
        val commands = mutableListOf<ActivityCommandDto>()
        val transport = object : ActivityTransport {
            override suspend fun read() = initial
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                commands += command
                if (commands.size == 1) error("Connection lost")
                return initial.copy(cursor = 1, current = current, records = listOf(current), acknowledgement = ActivityAcknowledgementDto(command.operationId, ActivityOutcome.Applied))
            }
        }
        val repository = ActivityRepository(transport, store)
        repository.refresh()
        repository.command(ActivityKind.Start)
        assertTrue(repository.state.value.pending)
        val restored = ActivityRepository(transport, store)
        restored.retry()
        assertEquals(commands[0], commands[1])
        assertEquals(1, restored.state.value.snapshot?.current?.id)
        restored.refresh()
        assertEquals(1, restored.state.value.snapshot?.current?.id)
        assertFalse(restored.state.value.pending)
    }
    @Test fun offlineSequenceRestoresProjectionAndReplaysOnce() = runTest {
        val at = java.time.Instant.now().toString()
        val initial = ActivitySnapshotDto(offlineReady = true, cursor = 0, serverAt = at, reportingTimezone = "UTC", current = null, records = emptyList(), taskTypes = listOf(TaskTypeDto(2, "reading")))
        var durable: String? = null
        var failStorage = false
        val store = object : ActivityStorage {
            override fun load() = durable
            override fun save(value: String) { check(!failStorage) { "Disk full" }; durable = value }
        }
        var connected = true
        val sent = mutableListOf<ActivityCommandDto>()
        val transport = object : ActivityTransport {
            override suspend fun read(): ActivitySnapshotDto { check(connected) { "Offline" }; return initial.copy(cursor = sent.size) }
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                check(connected) { "Offline" }
                sent += command
                return initial.copy(cursor = command.sequence, acknowledgement = ActivityAcknowledgementDto(command.operationId, ActivityOutcome.Applied))
            }
        }
        val repository = ActivityRepository(transport, store)
        repository.refresh()
        connected = false
        assertTrue(repository.command(ActivityKind.Start))
        assertTrue(repository.command(ActivityKind.Switch, 2))
        val restored = ActivityRepository(transport, store)
        assertEquals("reading", restored.state.value.snapshot?.current?.taskType?.name)
        assertEquals(2, restored.state.value.snapshot?.records?.size)
        val originalStart = restored.state.value.snapshot!!.records.first().startAt
        assertEquals(restored.state.value.snapshot!!.records.first().endAt, restored.state.value.snapshot!!.current!!.startAt)
        assertTrue(restored.command(ActivityKind.Stop))
        assertNull(restored.state.value.snapshot?.current)
        assertTrue(restored.state.value.pending)
        failStorage = true
        assertFalse(restored.command(ActivityKind.Start))
        assertNull(restored.state.value.snapshot?.current)
        assertTrue(restored.state.value.error!!.contains("storage failed"))
        failStorage = false
        connected = true
        restored.refresh()
        assertEquals(3, sent.size)
        assertEquals(originalStart, sent.first().effective.at)
        assertEquals(sent[0].operationId, sent[1].predecessorId)
        assertEquals(sent[1].operationId, sent[2].predecessorId)
        assertFalse(restored.state.value.pending)
        restored.refresh()
        assertEquals(3, sent.size)
    }

    @Test fun initialOfflineInstallationCannotGuessActivity() = runTest {
        val storage = object : ActivityStorage {
            override fun load(): String? = null
            override fun save(value: String) {}
        }
        val transport = object : ActivityTransport {
            override suspend fun read(): ActivitySnapshotDto = error("Offline")
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Must not send")
        }
        val repository = ActivityRepository(transport, storage)
        repository.refresh()
        assertFalse(repository.command(ActivityKind.Start))
        assertNull(repository.state.value.snapshot)
        assertTrue(repository.state.value.error!!.contains("Connect once"))
    }

    @Test fun competingOfflineChangesKeepCanonicalRemoteActivityAndDrainTheWholeChain() = runTest {
        val at = "2026-09-11T10:00:00Z"
        val row = ActualBlockDto(1, 1, TaskTypeDto(1, "writing"), startAt = at, createdAt = at, updatedAt = at, name = "Writing")
        val initial = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = at, reportingTimezone = "UTC", current = row, records = listOf(row))
        var saved = initial
        var durable: String? = null
        val store = object : ActivityStorage {
            override fun load() = durable
            override fun save(value: String) { durable = value }
        }
        var connected = true
        var allowStop = false
        var elapsed = 0L
        val sent = mutableListOf<ActivityCommandDto>()
        val transport = object : ActivityTransport {
            override suspend fun read(): ActivitySnapshotDto { check(connected) { "Offline" }; return saved }
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                check(connected) { "Offline" }
                sent += command
                check(command.kind != ActivityKind.Stop || allowStop) { "Connection lost" }
                val reading = row.copy(id = 3, name = "Reading", startAt = "2026-09-11T12:10:00Z")
                fun coverage(start: String, end: String?, id: Int, action: String, device: String) = ActivityCoverageDto(
                    start, end, id, listOf(kotlinx.serialization.json.JsonPrimitive(action), kotlinx.serialization.json.JsonPrimitive(device),
                        kotlinx.serialization.json.JsonPrimitive(1), kotlinx.serialization.json.JsonPrimitive("op-$id")))
                saved = initial.copy(cursor = if (command.kind == ActivityKind.Stop) 4 else 3, current = reading,
                    records = listOf(row.copy(endAt = "2026-09-11T12:00:00Z"), row.copy(id = 2, name = "Lunch", startAt = "2026-09-11T12:00:00Z", endAt = reading.startAt), reading),
                    operationOutcomes = mapOf(command.operationId to ActivityOperationOutcomeDto(command.deviceId, ActivityOutcome.Superseded)),
                    coverage = listOf(coverage(at, "2026-09-11T12:00:00Z", 1, "", ""),
                        coverage("2026-09-11T12:00:00Z", reading.startAt, 2, "2026-09-11T12:00:00Z", command.deviceId),
                        coverage(reading.startAt, null, 3, reading.startAt, "remote")),
                    acknowledgement = ActivityAcknowledgementDto(command.operationId, ActivityOutcome.Superseded))
                return saved
            }
        }
        val repository = ActivityRepository(transport, store, monotonicTime = { elapsed })
        repository.refresh()
        connected = false
        elapsed = 2L * 3600 * 1_000_000_000
        assertTrue(repository.command(ActivityKind.Switch, 1, "Lunch"))
        elapsed += 5L * 60 * 1_000_000_000
        assertTrue(repository.command(ActivityKind.Stop))
        connected = true
        val restored = ActivityRepository(transport, store)
        restored.refresh()
        assertTrue(restored.state.value.pending)
        assertEquals("Reading", restored.state.value.snapshot?.current?.name)
        assertEquals("2026-09-11T12:05:00Z", restored.state.value.snapshot?.records?.find { it.name == "Lunch" }?.endAt)
        assertEquals("A newer change on another device updated this time.", restored.state.value.feedback)
        allowStop = true
        restored.refresh()
        assertFalse(restored.state.value.pending)
        assertNull(restored.state.value.error)
        assertEquals("Reading", restored.state.value.snapshot?.current?.name)
        assertEquals(sent.filter { it.kind == ActivityKind.Stop }.first(), sent.last())
        restored.dismissFeedback()
        assertNull(restored.state.value.feedback)
    }

    @Test fun backwardWallClockAndRestartNeverRestampQueuedActions() = runTest {
        var wall = java.time.Instant.parse("2026-09-11T10:00:00Z").toEpochMilli()
        var mono = 0L
        var connected = true
        var durable: String? = null
        val sent = mutableListOf<ActivityCommandDto>()
        val initial = ActivitySnapshotDto(offlineReady = true, cursor = 0, serverAt = "2026-09-11T10:00:00Z", reportingTimezone = "UTC", current = null, records = emptyList())
        val store = object : ActivityStorage {
            override fun load() = durable
            override fun save(value: String) { durable = value }
        }
        val transport = object : ActivityTransport {
            override suspend fun read(): ActivitySnapshotDto { check(connected); return initial.copy(cursor = sent.size) }
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                check(connected); sent += command
                return initial.copy(cursor = sent.size, acknowledgement = ActivityAcknowledgementDto(command.operationId, ActivityOutcome.Applied))
            }
        }
        val repository = ActivityRepository(transport, store, { wall }, { mono })
        repository.refresh()
        connected = false
        mono += 60_000_000_000
        wall -= 3_600_000
        repository.command(ActivityKind.Start)
        val start = repository.state.value.snapshot!!.current!!.startAt
        val restored = ActivityRepository(transport, store, { wall }, { mono })
        restored.command(ActivityKind.Stop)
        connected = true
        restored.refresh()
        assertEquals(start, sent[0].actionAt)
        assertTrue(java.time.Instant.parse(sent[1].actionAt) > java.time.Instant.parse(sent[0].actionAt))
        assertEquals(sent[0].calibration, sent[1].calibration)
    }
}
