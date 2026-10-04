package com.timebox.android.data

import com.timebox.android.data.remote.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlanNowRepositoryTest {
    @Test fun lostSaveResponseRetriesIdenticalRequestAfterRestart() = runTest {
        val at = "2026-10-04T10:20:59.732Z"
        val initial = ActivitySnapshotDto(cursor = 0, serverAt = at, reportingTimezone = "UTC", current = null, records = emptyList(), offlineReady = true, planNowRevision = "before")
        val body = PlanNowRequestDto("save-1", "before", null, at, 15, 1, null, "Writing", emptyList())
        var durable: String? = null
        val store = object : ActivityStorage {
            override fun load() = durable
            override fun save(value: String) { durable = value }
        }
        val calls = mutableListOf<PlanNowRequestDto>()
        val saved = initial.copy(cursor = 1, planNowRevision = "after", planNowUndo = body.operationId)
        val transport = object : ActivityTransport {
            override suspend fun read() = if (calls.isEmpty()) initial else saved
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Must reconcile plan first")
            override suspend fun planNow(body: PlanNowRequestDto): ActivitySnapshotDto {
                calls += body
                if (calls.size == 1) throw java.io.IOException("Response lost after commit")
                return saved
            }
        }
        val repository = ActivityRepository(transport, store)
        repository.refresh()
        assertTrue(repository.planNow(body))
        assertTrue(repository.state.value.pending)
        assertFalse(repository.command(ActivityKind.Start, 1))
        val restarted = ActivityRepository(transport, store)
        assertTrue(restarted.state.value.pending)
        restarted.refresh()
        assertEquals(listOf(body, body), calls)
        assertFalse(restarted.state.value.pending)
        assertEquals("after", restarted.state.value.snapshot?.planNowRevision)
    }
}
