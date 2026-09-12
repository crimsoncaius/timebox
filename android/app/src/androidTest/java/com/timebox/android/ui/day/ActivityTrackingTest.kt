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
    @Test fun pendingQuestionDismissesToWaitingAndReopensOnlyExplicitlyInFocus() {
        val at = "2026-09-11T10:00:00Z"
        val row = ActualBlockDto(1, 1, TaskTypeDto(1, "Reading"), startAt = at, createdAt = at, updatedAt = at)
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = at, reportingTimezone = "UTC", current = row, records = listOf(row),
            checkIn = CheckInStateDto(generation = "a", rearm = 0, armedAt = at, question = CheckInQuestionDto("a:0", at)))
        var journal: String? = null
        val repository = ActivityRepository(object : ActivityTransport { override suspend fun read() = snapshot; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline") }, object : ActivityStorage { override fun load() = journal; override fun save(value: String) { journal = value } })
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(emptyList(), {}, repository, focus = true) } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText("Yes, still doing this").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Check-in waiting").assertIsDisplayed()
        compose.onNodeWithText("Yes, still doing this").assertDoesNotExist()
        compose.onNodeWithText("Check-in waiting").performClick()
        compose.onNodeWithText("Yes, still doing this").performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot!!.checkIn!!.question == null }
        compose.onNodeWithText("Check-in waiting").assertDoesNotExist()
        assertEquals(row, repository.state.value.snapshot!!.current)
    }

    @Test fun focusUnknownPromptIsTypeOnlyAndBackDismissesSwitchSheet() = verifyUnknownActivity(false)
    @Test fun focusDescribeFromNowPreservesEarlierUnspecifiedTime() = verifyUnknownActivity(true)

    private fun verifyUnknownActivity(fromNow: Boolean) {
        val at = "2026-09-11T10:00:00Z"
        val type = TaskTypeDto(1, "unspecified")
        val row = ActualBlockDto(7, 1, type, startAt = at, createdAt = at, updatedAt = at)
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = at, reportingTimezone = "UTC", current = row, records = listOf(row), taskTypes = listOf(type, TaskTypeDto(2, "Reading")))
        var journal: String? = null
        val repository = ActivityRepository(object : ActivityTransport { override suspend fun read() = snapshot; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline") }, object : ActivityStorage { override fun load() = journal; override fun save(value: String) { journal = value } })
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(emptyList(), {}, repository, focus = true) } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText("Stop").assertDoesNotExist()
        compose.onNodeWithText("What are you doing?").assertIsDisplayed()
        compose.onNodeWithText("Block Name (optional)").assertDoesNotExist()
        compose.onNodeWithText("Switch activity").performClick()
        compose.onNodeWithText("Block Name (optional)").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Block Name (optional)").assertDoesNotExist()
        assertEquals(7, repository.state.value.snapshot!!.current!!.id)
        compose.onNodeWithText("Reading").assertDoesNotExist()
        compose.onNodeWithText("From the start").assertDoesNotExist()
        compose.onNodeWithText("Choose activity").performClick()
        compose.onNodeWithText("Reading").performClick()
        if (fromNow) compose.onNodeWithText("From now").performScrollTo().performClick()
        compose.onNodeWithText("Apply activity").performScrollTo().performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current?.taskType?.name == "Reading" }
        if (fromNow) {
            val earlier = repository.state.value.snapshot!!.records.single { it.id == 7 }
            assertEquals("unspecified", earlier.taskType.name)
            assertNotNull(earlier.endAt)
            assertEquals(earlier.endAt, repository.state.value.snapshot!!.current!!.startAt)
        } else assertEquals(at, repository.state.value.snapshot!!.current!!.startAt)
    }

    @get:Rule val compose = createComposeRule()

    @Test fun newerRemoteChangeShowsCanonicalActivityAndTransientFeedback() {
        var journal: String? = null
        val storage = object : ActivityStorage {
            override fun load() = journal
            override fun save(value: String) { journal = value }
        }
        var saved = ActivitySnapshotDto(offlineReady = true, cursor = 0, serverAt = "2026-09-11T10:00:00Z",
            reportingTimezone = "UTC", current = null, records = emptyList())
        val transport = object : ActivityTransport {
            override suspend fun read() = saved
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                val reading = ActualBlockDto(9, 2, TaskTypeDto(2, "Reading"), startAt = "2026-09-11T12:10:00Z",
                    createdAt = saved.serverAt, updatedAt = saved.serverAt)
                saved = saved.copy(cursor = 2, current = reading, records = listOf(reading),
                    operationOutcomes = mapOf(command.operationId to ActivityOperationOutcomeDto(command.deviceId, ActivityOutcome.Superseded)),
                    acknowledgement = ActivityAcknowledgementDto(command.operationId, ActivityOutcome.Superseded))
                return saved
            }
        }
        val repository = ActivityRepository(transport, storage)
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(emptyList(), {}, repository) } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText("Start tracking").performClick()
        compose.waitUntil(5000) { repository.state.value.feedback != null }
        compose.onNodeWithText("Reading").assertIsDisplayed()
        compose.onNodeWithText("A newer change on another device updated this time.").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertDoesNotExist()
        compose.onNodeWithText("Synced").assertDoesNotExist()
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("Stop").assertIsEnabled()
        assertFalse(repository.state.value.pending)
        assertNull(repository.state.value.error)
    }

    @Test fun planStartAndExplicitResumeShowMultipleActuals() {
        val at = "2026-09-11T10:00:00Z"
        val plan = ActivityPlanDto(4, 2, 7, "Chapter", "Outline", at, "2026-09-11T11:00:00Z")
        var journal: String? = null
        val store = object : ActivityStorage {
            override fun load() = journal
            override fun save(value: String) { journal = value }
        }
        var saved = ActivitySnapshotDto(offlineReady = true, cursor = 0, serverAt = at, reportingTimezone = "UTC",
            current = null, records = emptyList(), taskTypes = listOf(TaskTypeDto(2, "Writing")), plans = listOf(plan))
        val transport = object : ActivityTransport {
            override suspend fun read() = saved
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                val current = if (command.kind == ActivityKind.Stop) null else ActualBlockDto(command.sequence, 2, TaskTypeDto(2, "Writing"),
                    startAt = command.actionAt, createdAt = at, updatedAt = at, name = command.name,
                    taskId = command.taskId, plannedBlockId = command.plannedBlockId)
                val records = saved.records.map { if (it.endAt == null) it.copy(endAt = command.actionAt) else it } + listOfNotNull(current)
                saved = saved.copy(cursor = command.sequence, current = current, records = records,
                    acknowledgement = ActivityAcknowledgementDto(command.operationId, ActivityOutcome.Applied))
                return saved
            }
        }
        val repository = ActivityRepository(transport, store)
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(emptyList(), {}, repository) } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText("Start tracking").performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current?.plannedBlockId == 4 }
        compose.onNodeWithText("Chapter").assertIsDisplayed()
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("Switch activity").performClick()
        compose.onNodeWithText("Task Type").performScrollTo().performClick()
        compose.onNodeWithText("Writing").performClick()
        compose.onNode(hasText("Switch activity") and hasClickAction()).performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current?.plannedBlockId == null }
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("Planned now: Chapter · Switch").performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.records?.count { it.plannedBlockId == 4 } == 2 }
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("2 linked Actual Blocks", substring = true).assertIsDisplayed()
        assertEquals(7, repository.state.value.snapshot?.current?.taskId)
    }

    @Test fun immediateStartTypeFirstSwitchAndStop() {
        var journal: String? = null
        val storage = object : ActivityStorage {
            override fun load() = journal
            override fun save(value: String) { journal = value }
        }
        var saved = ActivitySnapshotDto(offlineReady = true, cursor = 0, serverAt = "2026-09-11T10:00:00Z", reportingTimezone = "UTC", current = null, records = emptyList())
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
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("Switch activity").performClick()
        compose.onNode(hasText("Switch activity") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Task Type").performScrollTo().performClick()
        compose.onNodeWithText("reading").performClick()
        compose.onNode(hasText("Switch activity") and hasClickAction()).performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current?.taskTypeId == 2 }
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("After this change").assertExists()
        compose.onNode(hasText("Stop tracking") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current == null }
        compose.onNodeWithText("Start tracking").assertIsDisplayed()
        assertEquals(listOf(ActivityKind.Start, ActivityKind.Switch, ActivityKind.Stop), commands.map { it.kind })
        assertNull(commands[1].name)
    }
}
