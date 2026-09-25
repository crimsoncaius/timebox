package com.timebox.android.data

import com.timebox.android.data.remote.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ActivitySwitchUndoTest {
    @Test fun historicalSwitchAndUndoRestoreFullHistoryOfflineAcrossRestart() = runTest {
        val type = TaskTypeDto(1, "Work")
        val a = ActualBlockDto(1, 1, type, startAt = "2026-09-10T08:00:00Z", endAt = "2026-09-10T10:00:00Z", name = "A", note = "Keep this", createdAt = "", updatedAt = "")
        val b = a.copy(id = 2, name = "B", startAt = "2026-09-10T11:00:00Z", endAt = null)
        val initial = ActivitySnapshotDto(cursor = 1, serverAt = "2026-09-10T14:00:00Z", reportingTimezone = "UTC", current = b, records = listOf(a, b), taskTypes = listOf(type), offlineReady = true, switchHistoryReady = true)
        var durable: String? = null
        var failStorage = false
        val store = object : ActivityStorage {
            override fun load() = durable
            override fun save(value: String) { check(!failStorage) { "Disk full" }; durable = value }
        }
        val commands = mutableListOf<ActivityCommandDto>()
        val transport = object : ActivityTransport {
            override suspend fun read() = initial
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto { commands += command; throw java.io.IOException("Offline") }
        }
        val repository = ActivityRepository(transport, store)
        repository.refresh()
        assertFalse(repository.command(ActivityKind.Stop, effectiveAt = Instant.parse("2026-09-10T09:30:00Z"), observedTargetId = 2))
        assertTrue(repository.command(ActivityKind.Switch, 1, "C", effectiveAt = Instant.parse("2026-09-10T09:30:00Z"), observedTargetId = 2))
        assertEquals("2026-09-10T09:30:00Z", repository.state.value.snapshot!!.records.first().endAt)
        val operation = commands.last().operationId
        failStorage = true
        assertTrue(repository.undoSwitch(operation).isFailure)
        assertEquals("C", repository.state.value.snapshot!!.current!!.name)
        failStorage = false
        assertTrue(repository.undoSwitch(operation).isSuccess)
        assertEquals(initial.records, repository.state.value.snapshot!!.records)
        val restarted = ActivityRepository(transport, store)
        assertEquals(initial.records, restarted.state.value.snapshot!!.records)
        assertTrue(restarted.state.value.pending)
    }
}
