package com.timebox.android.ui.day

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class RunningActualSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun elapsedMinutesAdvanceInSummaryAndWhileEditingWithoutClosingTheBlock() {
        val at = "2026-09-11T10:00:00Z"
        val type = TaskTypeDto(1, "Design")
        val row = ActualBlockDto(1, 1, type, name = "Draft", startAt = at, createdAt = at, updatedAt = at)
        val snapshot = ActivitySnapshotDto(cursor = 1, serverAt = "2026-09-11T10:45:59Z", reportingTimezone = "UTC", offlineReady = true,
            current = row, records = listOf(row), taskTypes = listOf(type))
        val monotonic = AtomicLong(0)
        var stored: String? = null
        val repository = ActivityRepository(object : ActivityTransport {
            override suspend fun read() = snapshot
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Unexpected mutation")
        }, object : ActivityStorage { override fun load() = stored; override fun save(value: String) { stored = value } },
            monotonicTime = monotonic::get)
        runBlocking { repository.refresh() }
        compose.setContent { TimeboxTheme(darkTheme = false) { RunningActualSheet(row, repository, {}, {}) } }
        compose.onNodeWithText("45 mins").assertIsDisplayed()
        monotonic.set(1_000_000_000)
        compose.waitUntil(5000) { compose.onAllNodesWithText("46 mins").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Edit details").performClick()
        compose.onNodeWithText("Recording continues").assertIsDisplayed()
        monotonic.set(61_000_000_000)
        compose.waitUntil(5000) { compose.onAllNodesWithText("47 mins").fetchSemanticsNodes().isNotEmpty() }
        assertNull(repository.state.value.snapshot!!.current!!.endAt)
        assertFalse(repository.state.value.pending)
    }

    @Test fun summaryExpandsAndSavesWithoutStopping() {
        val at = "2026-09-11T10:00:00Z"
        val type = TaskTypeDto(1, "Design")
        val row = ActualBlockDto(1, 1, type, name = "Draft", note = "Keep this note", startAt = at, endAt = null, createdAt = at, updatedAt = at)
        val initial = ActivitySnapshotDto(cursor = 1, serverAt = "2026-09-11T14:00:00Z", reportingTimezone = "UTC", offlineReady = true,
            current = row, records = listOf(row), taskTypes = listOf(type))
        var stored: String? = null
        val repository = ActivityRepository(object : ActivityTransport {
            override suspend fun read() = initial
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Offline")
        }, object : ActivityStorage { override fun load() = stored; override fun save(value: String) { stored = value } })
        runBlocking { repository.refresh() }
        compose.setContent { TimeboxTheme(darkTheme = true) { RunningActualSheet(row, repository, {}, {}) } }
        compose.onNodeWithText("Draft").assertIsDisplayed()
        compose.onNodeWithText("Keep this note").assertIsDisplayed()
        compose.onNodeWithText("Switch activity").assertIsDisplayed()
        compose.onNodeWithText("Edit details").performClick()
        compose.onNodeWithText("Block Name (optional)").performTextReplacement("Revised")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(5000) { repository.state.value.snapshot?.current?.name == "Revised" }
        assertNull(repository.state.value.snapshot!!.current!!.endAt)
        assertEquals("Keep this note", repository.state.value.snapshot!!.current!!.note)
    }
}
