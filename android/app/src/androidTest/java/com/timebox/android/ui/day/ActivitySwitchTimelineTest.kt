package com.timebox.android.ui.day

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ActivitySwitchTimelineTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-09-12T14:00:00Z")
    private val zone = ZoneId.of("Asia/Singapore")
    private val reading = TaskTypeDto(1, "Reading")
    private val meals = TaskTypeDto(2, "Meals")
    private val current = ActualBlockDto(42, 1, reading, name = "Book", startAt = "2026-09-12T12:15:00Z", createdAt = "2026-09-12T12:15:00Z", updatedAt = "2026-09-12T12:15:00Z")
    private val previous = current.copy(id = 41, name = "Walk", startAt = "2026-09-12T11:30:00Z", endAt = current.startAt)
    private val plan = ActivityPlanDto(7, 2, name = "Dinner", startAt = "2026-09-12T12:30:17Z", endAt = "2026-09-12T13:00:23Z")

    @Test fun realContextBoundaryCancelThenSwitchPreservesPlannedBlocksAndExactTime() {
        val initial = ActivitySnapshotDto(cursor = 1, serverAt = now.toString(), reportingTimezone = zone.id,
            current = current, records = listOf(previous, current), plans = listOf(plan), taskTypes = listOf(reading, meals), offlineReady = true)
        var journal: String? = null
        val storage = object : ActivityStorage { override fun load() = journal; override fun save(value: String) { journal = value } }
        val transport = object : ActivityTransport {
            override suspend fun read() = initial
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline")
        }
        val repository = ActivityRepository(transport, storage, { now.toEpochMilli() }, { 0 }).also { runBlocking { it.refresh() } }
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(emptyList(), {}, repository) } }
        fun open() { compose.onNodeWithText("Current activity").performClick(); compose.onNodeWithText("Switch activity", substring = false).performClick() }
        open()
        val left = compose.onNodeWithTag("switch-planned-header").fetchSemanticsNode().boundsInRoot.left
        val right = compose.onNodeWithTag("switch-recorded-header").fetchSemanticsNode().boundsInRoot.left
        assertTrue(left < right)
        compose.onNodeWithText("Dinner ends · 21:00").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(current, repository.state.value.snapshot!!.current)
        assertFalse(repository.state.value.pending)
        open()
        compose.onAllNodes(hasSetTextAction()).onLast().performScrollTo().performTextInput("meal")
        compose.onNodeWithText("Meals", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Dinner ends · 21:00").performScrollTo().performClick()
        compose.onNode(hasText("Switch activity") and hasClickAction()).performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current?.taskTypeId == 2 }
        val saved = repository.state.value.snapshot!!
        assertEquals(plan.endAt, saved.current!!.startAt)
        assertEquals(plan.endAt, saved.records.first { it.id == current.id }.endAt)
        assertEquals(listOf(plan), saved.plans)
        assertEquals(previous, saved.records.first { it.id == previous.id })
    }

    @Test fun darkTimelineSupportsDragPagingAndDisablesSelectionWhileSaving() {
        var timing: Instant? = now.minusSeconds(1800)
        var busy by mutableStateOf(false)
        compose.setContent {
            var selected by remember { mutableStateOf(timing!!) }
            TimeboxTheme(darkTheme = true) {
                SwitchActivityTimeline(current.id, Instant.parse(current.startAt), listOf(previous, current), listOf(plan.copy(name = null, taskId = 77)),
                    listOf(TaskType(1, "Reading", 0), TaskType(2, "Meals", 0)), "Dinner", selected, now, zone, !busy,
                    { timing = it; selected = it ?: now }, loadPlanTitles = { mapOf(plan.id to "Evening meal") })
            }
        }
        compose.onNodeWithText("Evening meal").assertExists()
        compose.onNodeWithTag("switch-timeline").performTouchInput { swipe(Offset(width * .8f, height * .35f), Offset(width * .8f, height * .55f)) }
        compose.runOnIdle { assertTrue(timing!! >= Instant.parse(current.startAt)); assertTrue(timing!! <= now); busy = true }
        val before = timing
        compose.onNodeWithTag("switch-timeline").performTouchInput { click(Offset(width * .8f, height * .1f)) }
        compose.runOnIdle { assertEquals(before, timing); busy = false }
        compose.onNodeWithContentDescription("Earlier context").performClick()
        compose.onNodeWithContentDescription("Later context").performClick()
        compose.onNodeWithText("Now", substring = false).performClick()
        compose.runOnIdle { assertNull(timing) }
    }
}
