package com.timebox.android.data

import com.timebox.android.data.remote.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ActivityStartHistoryTest {
    private val type = TaskTypeDto(1, "Work")
    private val a = ActualBlockDto(1, 1, type, startAt = "2026-09-10T08:00:00Z", endAt = "2026-09-10T10:00:00Z", name = "A", createdAt = "", updatedAt = "")
    private val b = a.copy(id = 2, name = "B", startAt = "2026-09-10T11:00:00Z", endAt = "2026-09-10T12:00:00Z")

    private fun repository(ready: Boolean, commands: MutableList<ActivityCommandDto>): ActivityRepository {
        val initial = ActivitySnapshotDto(cursor = 1, serverAt = "2026-09-10T14:00:00Z", reportingTimezone = "UTC", current = null,
            records = listOf(a, b), taskTypes = listOf(type), offlineReady = true, switchHistoryReady = true, startHistoryReady = ready)
        var durable: String? = null
        val store = object : ActivityStorage { override fun load() = durable; override fun save(value: String) { durable = value } }
        val transport = object : ActivityTransport {
            override suspend fun read() = initial
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto { commands += command; throw java.io.IOException("Offline") }
        }
        // The fixed clock sits after every sample record, as the server's would.
        return ActivityRepository(transport, store, wallTime = { Instant.parse("2026-09-10T14:00:00Z").toEpochMilli() }, monotonicTime = { 0L })
    }

    @Test fun earlierStartReplacesHistoryOfflineAndOffersUndo() = runTest {
        val commands = mutableListOf<ActivityCommandDto>()
        val repository = repository(ready = true, commands)
        repository.refresh()
        val offer = backgroundScope.launch { repository.switchUndoOffers.first() }
        testScheduler.runCurrent()
        var operation: String? = null
        assertTrue(repository.command(ActivityKind.Start, 1, "R", effectiveAt = Instant.parse("2026-09-10T09:30:00Z"), onOperation = { operation = it }))
        val projected = repository.state.value.snapshot!!
        assertEquals(listOf("A" to "2026-09-10T09:30:00Z", "R" to null), projected.records.map { it.name to it.endAt })
        assertEquals(operation, projected.provenance[projected.current!!.id.toString()])
        assertEquals("2026-09-10T09:30:00Z", commands.last().effective.at)
        testScheduler.runCurrent()
        assertTrue(offer.isCompleted)
        assertTrue(repository.undoSwitch(operation!!).isSuccess)
        assertEquals(listOf(a, b), repository.state.value.snapshot!!.records)
        assertNull(repository.state.value.snapshot!!.current)
    }

    @Test fun olderServerKeepsStartAtTheTapInstant() = runTest {
        val repository = repository(ready = false, mutableListOf())
        repository.refresh()
        assertFalse(repository.command(ActivityKind.Start, 1, "R", effectiveAt = Instant.parse("2026-09-10T09:30:00Z")))
        assertEquals("Update the server to start from an earlier time.", repository.state.value.error)
    }
}
