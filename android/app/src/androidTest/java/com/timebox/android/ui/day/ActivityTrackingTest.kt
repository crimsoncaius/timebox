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

    @Test fun focusOffersSwitchRatherThanANamingPromptForUnnamedUnspecifiedActivity() {
        val at = "2026-09-11T10:00:00Z"
        val type = TaskTypeDto(1, "unspecified")
        val row = ActualBlockDto(7, 1, type, startAt = at, createdAt = at, updatedAt = at)
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = at, reportingTimezone = "UTC", current = row, records = listOf(row), taskTypes = listOf(type, TaskTypeDto(2, "Reading")))
        var journal: String? = null
        val repository = ActivityRepository(object : ActivityTransport { override suspend fun read() = snapshot; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline") }, object : ActivityStorage { override fun load() = journal; override fun save(value: String) { journal = value } })
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(emptyList(), {}, repository, focus = true) } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText("Stop").assertDoesNotExist()
        compose.onNodeWithText("What are you doing?").assertDoesNotExist()
        compose.onNodeWithText("Choose activity").assertDoesNotExist()
        compose.onNodeWithText("Switch activity").performClick()
        compose.onNodeWithText("Block Name (optional)").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Block Name (optional)").assertDoesNotExist()
        assertEquals(7, repository.state.value.snapshot!!.current!!.id)
    }

    @Test fun startWithoutCoveringPlanAsksForTaskTypeAndCancelLeavesTrackingStopped() {
        val at = "2026-09-11T10:00:00Z"
        val later = ActivityPlanDto(4, 2, null, "Later", null, "2099-01-01T10:00:00Z", "2099-01-01T11:00:00Z")
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 0, serverAt = at, reportingTimezone = "UTC", current = null, records = emptyList(),
            taskTypes = listOf(TaskTypeDto(1, "unspecified"), TaskTypeDto(2, "Reading")), plans = listOf(later))
        val commands = mutableListOf<ActivityCommandDto>()
        var journal: String? = null
        val repository = ActivityRepository(object : ActivityTransport { override suspend fun read() = snapshot; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto { commands += command; error("Offline") } }, object : ActivityStorage { override fun load() = journal; override fun save(value: String) { journal = value } })
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(emptyList(), {}, repository) } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText("Start tracking").performClick()
        compose.onNodeWithText("When did this change happen?").assertDoesNotExist()
        compose.onNode(hasText("Start") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Block Name (optional)").assertDoesNotExist()
        assertNull(repository.state.value.snapshot!!.current)
        assertTrue(commands.isEmpty())
        compose.onNodeWithText("Start tracking").performClick()
        chooseTaskType("read", "Reading")
        compose.onNodeWithText("Block Name (optional)").performTextInput("Paper")
        compose.onNode(hasText("Start") and hasClickAction()).performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current != null }
        assertEquals(2, repository.state.value.snapshot!!.current!!.taskTypeId)
        assertEquals("Paper", repository.state.value.snapshot!!.current!!.name)
        assertNull(repository.state.value.snapshot!!.current!!.plannedBlockId)
    }

    @get:Rule val compose = createComposeRule()

    /** Picks a type in the Switch sheet's picker; a partial query keeps the typed text from matching the row. */
    private fun chooseTaskType(query: String, name: String) {
        compose.onAllNodes(hasSetTextAction()).onLast().performScrollTo().performTextInput(query)
        compose.onNodeWithText(name, useUnmergedTree = true).performScrollTo().performClick()
    }

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
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(listOf(TaskType(2, "Reading", 0)), {}, repository) } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText("Start tracking").performClick()
        chooseTaskType("read", "Reading")
        compose.onNode(hasText("Start") and hasClickAction()).performClick()
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

    @Test fun planStartAndExplicitResumePreserveLinkedActuals() {
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
        chooseTaskType("writ", "Writing")
        compose.onNode(hasText("Switch activity") and hasClickAction()).performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current?.plannedBlockId == null }
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("PLANNED NOW").assertIsDisplayed()
        compose.onNodeWithText("Chapter").assertIsDisplayed()
        compose.onNodeWithText("10:00 AM – 11:00 AM", substring = true).assertIsDisplayed()
        compose.onNode(hasText("Switch") and hasClickAction()).performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.records?.count { it.plannedBlockId == 4 } == 2 }
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("2 sessions ·", substring = true).assertDoesNotExist()
        compose.onNodeWithText("on this plan", substring = true).assertDoesNotExist()
        assertEquals(7, repository.state.value.snapshot?.current?.taskId)
    }

    @Test fun focusOmitsPlanSessionSummary() = verifyPlanSessionSummary(focus = true)
    @Test fun expandedControlOmitsPlanSessionSummary() = verifyPlanSessionSummary(focus = false)

    private fun verifyPlanSessionSummary(focus: Boolean) {
        val at = "2026-09-11T10:00:00Z"
        val earlier = ActualBlockDto(1, 1, TaskTypeDto(2, "Writing"), startAt = at, endAt = "2026-09-11T10:30:00Z", createdAt = at, updatedAt = at, plannedBlockId = 4)
        val row = ActualBlockDto(2, 1, TaskTypeDto(2, "Writing"), startAt = "2026-09-11T10:40:00Z", createdAt = at, updatedAt = at, name = "Chapter", plannedBlockId = 4)
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 2, serverAt = at, reportingTimezone = "UTC", current = row, records = listOf(earlier, row))
        var journal: String? = null
        val repository = ActivityRepository(object : ActivityTransport { override suspend fun read() = snapshot; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline") }, object : ActivityStorage { override fun load() = journal; override fun save(value: String) { journal = value } })
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(emptyList(), {}, repository, focus = focus) } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        if (focus) {
            compose.onNodeWithText("elapsed").assertIsDisplayed()
            compose.onNodeWithText("on this plan", substring = true).assertDoesNotExist()
        } else {
            compose.onNodeWithText("on this plan", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Current activity").performClick()
            compose.onNodeWithText("2 sessions ·", substring = true).assertDoesNotExist()
            compose.onNodeWithText("on this plan", substring = true).assertDoesNotExist()
        }
    }

    @Test fun explicitUnspecifiedStartTypeFirstSwitchAndStop() {
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
            ActivityTracking(listOf(TaskType(1, "unspecified", 0), TaskType(2, "reading", 0)), {}, repository)
        } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText("Start tracking").performClick()
        chooseTaskType("unspec", "unspecified")
        compose.onNode(hasText("Start") and hasClickAction()).performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current != null }
        compose.onNodeWithText("unspecified").assertIsDisplayed()
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("Switch activity").performClick()
        compose.onNode(hasText("Switch activity") and hasClickAction()).assertIsNotEnabled()
        chooseTaskType("read", "reading")
        compose.onNode(hasText("Switch activity") and hasClickAction()).performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current?.taskTypeId == 2 }
        compose.onNodeWithText("Current activity").performClick()
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("Unrecorded after ", substring = true).assertExists()
        compose.onNode(hasText("Stop tracking") and hasClickAction()).assertIsDisplayed().performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current == null }
        compose.onNodeWithText("Start tracking").assertIsDisplayed()
        assertEquals(listOf(ActivityKind.Start, ActivityKind.Switch, ActivityKind.Stop), commands.map { it.kind })
        assertEquals(1, commands[0].taskTypeId)
        assertNull(commands[1].name)
    }
}
