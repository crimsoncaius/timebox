package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class ElapsedDurationDisplayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusShowsHoursAndSeconds() = verifyElapsed(true, "2026-09-11T13:00:07Z", "3 hours 7 secs")
    @Test fun compactShowsDaysWithoutHidingTheActivity() = verifyElapsed(false, "2026-09-12T11:01:00Z", "1 day 1 hour 1 min")

    private fun verifyElapsed(focus: Boolean, now: String, expected: String) {
        val at = "2026-09-11T10:00:00Z"
        val row = ActualBlockDto(1, 1, TaskTypeDto(1, "Writing"), startAt = at, createdAt = at, updatedAt = at)
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = now,
            reportingTimezone = "UTC", current = row, records = listOf(row))
        var journal: String? = null
        val repository = ActivityRepository(
            object : ActivityTransport {
                override suspend fun read() = snapshot
                override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Read-only fixture")
            },
            object : ActivityStorage { override fun load() = journal; override fun save(value: String) { journal = value } },
            { Instant.parse(now).toEpochMilli() }, { 0 },
        )
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                Box(Modifier.width(320.dp)) { ActivityTracking(emptyList(), {}, repository, focus = focus) }
            }
        }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        compose.onNodeWithText(expected).assertIsDisplayed()
        compose.onNodeWithText("Writing").assertIsDisplayed()
    }
}
