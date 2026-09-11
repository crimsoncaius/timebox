package com.timebox.android.data

import com.timebox.android.data.remote.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ActivityRepositoryTest {
    @Test fun lostAcknowledgementSurvivesRestartAndOldReadCannotStopRecording() = runTest {
        val initial = ActivitySnapshotDto(cursor = 0, serverAt = "2026-09-11T10:00:00Z", reportingTimezone = "UTC", current = null, records = emptyList())
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
}
