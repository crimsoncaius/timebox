package com.timebox.android.ui.day

import androidx.compose.runtime.*
import androidx.compose.material3.Text
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
import java.time.LocalDate

class ActivityCorrectionTest {
    @get:Rule val compose = createComposeRule()
    private val type = TaskTypeDto(1, "Writing")
    private val row = ActualBlockDto(42, 1, type, name = "Across midnight", startAt = "2025-11-02T03:30:12Z", endAt = "2025-11-02T06:30:34Z", createdAt = "2025-11-02T03:30:12Z", updatedAt = "2025-11-02T03:30:12Z")
    private fun repository(snapshot: ActivitySnapshotDto): ActivityRepository {
        var journal: String? = null
        val storage = object : ActivityStorage { override fun load() = journal; override fun save(value: String) { journal = value } }
        val transport = object : ActivityTransport {
            override suspend fun read() = snapshot
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline")
        }
        return ActivityRepository(transport, storage, { Instant.parse(snapshot.serverAt).toEpochMilli() }, { 0 }).also { runBlocking { it.refresh() } }
    }
    @Test fun earlierDayEditorRejectsGapAndRequiresOccurrenceBeforeSavingOffline() {
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = "2026-09-11T12:15:00Z", reportingTimezone = "America/New_York", current = null, records = listOf(row), taskTypes = listOf(type))
        val repository = repository(snapshot)
        val date = LocalDate.parse("2025-11-02")
        compose.setContent {
            val activity by repository.state.collectAsState()
            var open by remember { mutableStateOf(true) }
            val day = activity.snapshot!!.projectDay(date, null)
            TimeboxTheme(darkTheme = false) {
                Text("${day.actualBlocks.first().durationMinutes}m on this day")
                if (open) ActivityActualEditor(DayUiState(date = date, pages = mapOf(date to DayPageState(day, false)), selectedBlockId = 42), { open = false }, repository)
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Start")).assertTextContains("2025-11-01T23:30")
        compose.onNode(hasSetTextAction() and hasText("End")).assertTextContains("2025-11-02T01:30")
        compose.onNode(hasSetTextAction() and hasText("Start")).performTextReplacement("2025-03-09T02:30")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.onAllNodesWithText("That local time does not exist", substring = true).onFirst().assertExists()
        assertFalse(repository.state.value.pending)
        compose.onNode(hasSetTextAction() and hasText("Start")).performScrollTo().performTextReplacement("2025-11-02T01:40")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.onNodeWithText("That local time occurs twice", substring = true).assertExists()
        compose.onAllNodes(hasText("Earlier ·", substring = true) and hasClickAction()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.waitUntil(5000) { repository.state.value.pending }
        compose.onNodeWithText("50m on this day").assertIsDisplayed()
        assertEquals("2025-11-02T05:40:00Z", repository.state.value.snapshot!!.records.single().startAt)
        assertEquals(row.endAt, repository.state.value.snapshot!!.records.single().endAt)
    }
    @Test fun lateStopPreviewCancelAndOfflineSaveUseDifferentActionAndEffectiveTimes() {
        val current = row.copy(startAt = "2026-09-11T10:00:00Z", endAt = null)
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = "2026-09-11T12:15:00Z", reportingTimezone = "UTC", current = current, records = listOf(current), taskTypes = listOf(type))
        val repository = repository(snapshot)
        compose.setContent { TimeboxTheme(darkTheme = false) { ActivityTracking(emptyList(), {}, repository) } }
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("15 min ago").performClick()
        compose.onNodeWithText("After this change").assertExists()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertFalse(repository.state.value.pending)
        assertEquals(current, repository.state.value.snapshot!!.current)
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("15 min ago").performClick()
        compose.onNode(hasText("Stop tracking") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current == null }
        assertEquals("2026-09-11T12:00:00Z", repository.state.value.snapshot!!.records.single().endAt)
    }
}
