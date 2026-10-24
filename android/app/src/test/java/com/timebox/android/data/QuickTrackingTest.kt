package com.timebox.android.data

import com.timebox.android.data.remote.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class QuickTrackingTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val type = TaskTypeDto(1, "Work")
    private val row = ActualBlockDto(1, 1, type, startAt = "2026-10-24T09:00:00Z", createdAt = "2026-10-24T09:00:00Z", updatedAt = "2026-10-24T09:00:00Z")
    private val snapshot = ActivitySnapshotDto(cursor = 0, serverAt = now.toString(), reportingTimezone = "UTC", current = row, records = listOf(row), taskTypes = listOf(type), offlineReady = true)
    private fun result(action: String = "track", at: String = "2026-10-24T09:30:00Z", timing: String = "extract") = QuickInterpretation(UUID.randomUUID().toString(), "working", "tile", action, if (action == "stop") null else 1, at, now.toString(), "UTC", timing, "No change")

    @Test fun continuationAndExplicitCorrectionHaveDifferentSemantics() {
        assertNull(quickTrackingKind(result(timing = "now"), snapshot, now))
        assertEquals(ActivityKind.Edit, quickTrackingKind(result(), snapshot, now))
        assertEquals(ActivityKind.Switch, quickTrackingKind(result(at = "2026-10-24T08:30:00Z"), snapshot, now))
        assertEquals(ActivityKind.Stop, quickTrackingKind(result("stop", "2026-10-24T08:30:00Z"), snapshot, now))
        assertTrue(runCatching { quickTrackingKind(result(at = "2026-10-23T23:59:00Z"), snapshot, now) }.isFailure)
        assertTrue(runCatching { quickTrackingKind(result(at = "2026-10-24T10:01:00Z"), snapshot, now) }.isFailure)
    }

    @Test fun offlineSaveAndReceiptSurviveRestartWithoutRepeatingOperation() = runTest {
        var durable: String? = null
        var online = true
        val transport = object : ActivityTransport {
            override suspend fun read(): ActivitySnapshotDto { if (!online) throw java.io.IOException("Offline"); return snapshot }
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = throw java.io.IOException("Offline")
        }
        val storage = object : ActivityStorage { override fun load() = durable; override fun save(value: String) { durable = value } }
        val repository = ActivityRepository(transport, storage, { now.toEpochMilli() }, { 0L })
        repository.refresh(); online = false
        val command = result()
        repository.applyQuick(command, repository.quickFingerprint())
        assertTrue(repository.state.value.pending)
        assertEquals(command.effectiveAt, repository.state.value.snapshot!!.current!!.startAt)
        val restored = ActivityRepository(transport, storage, { now.toEpochMilli() }, { 0L })
        assertTrue(restored.hasQuickOperation(command.requestId))
        assertEquals(1, restored.state.value.quickReviews.size)
        restored.applyQuick(command, "stale fingerprint")
        assertEquals(1, restored.state.value.quickReviews.size)
        assertTrue(runCatching { restored.applyQuick(result(), "stale fingerprint") }.isFailure)
    }
}
