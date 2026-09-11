package com.timebox.android.ui.day

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class ActivityTrackingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun immediateStartTypeFirstSwitchAndStop() {
        var journal: String? = null
        val storage = object : ActivityStorage {
            override fun load() = journal
            override fun save(value: String) { journal = value }
        }
        var saved = ActivitySnapshotDto(cursor = 0, serverAt = "2026-09-11T10:00:00Z", reportingTimezone = "UTC", current = null, records = emptyList())
        val commands = mutableListOf<ActivityCommandDto>()
        val transport = object : ActivityTransport {
            override suspend fun read() = saved
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                commands += command
                val current = if (command.kind == ActivityKind.Stop) null else ActualBlockDto(
                    id = commands.size, taskTypeId = command.taskTypeId ?: 1,
                    taskType = TaskTypeDto(command.taskTypeId ?: 1, if (command.kind == ActivityKind.Start) "unspecified" else "reading"),
                    startAt = saved.serverAt, createdAt = saved.serverAt, updatedAt = saved.serverAt,
                )
                saved = saved.copy(cursor = commands.size, current = current,
                    acknowledgement = ActivityAcknowledgementDto(command.operationId, ActivityOutcome.Applied))
                return saved
            }
        }
        val repository = ActivityRepository(transport, storage)
        compose.setContent { TimeboxTheme(darkTheme = false) {
            ActivityTracking(listOf(TaskType(2, "reading", 0)), {}, repository)
        } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText("Start tracking").performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current != null }
        compose.onNodeWithText("unspecified").assertIsDisplayed()
        compose.onNodeWithText("Switch").performClick()
        compose.onNode(hasText("Switch activity") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("reading").performClick()
        compose.onNode(hasText("Switch activity") and hasClickAction()).performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current?.taskTypeId == 2 }
        compose.onNodeWithText("Stop").performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current == null }
        compose.onNodeWithText("Start tracking").assertIsDisplayed()
        assertEquals(listOf(ActivityKind.Start, ActivityKind.Switch, ActivityKind.Stop), commands.map { it.kind })
        assertNull(commands[1].name)
    }
}
