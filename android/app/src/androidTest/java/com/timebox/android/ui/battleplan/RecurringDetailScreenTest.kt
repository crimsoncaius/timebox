package com.timebox.android.ui.battleplan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.RecurrenceStatus
import com.timebox.android.data.RecurringTemplate
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecurringDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun endRequiresExplicitConfirmation() {
        var endCalls = 0
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                RecurringDetailScreen(
                    state = RecurringUiState(selectedTemplate = activeTemplate()),
                    onBack = {}, onRetry = {}, onEdit = {}, onOpenTask = {},
                    onPause = {}, onResume = {}, onEnd = { endCalls += 1 },
                    onRequestDelete = {}, onDismissDelete = {}, onConfirmDelete = {},
                )
            }
        }

        compose.onNodeWithText("End").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, endCalls) }
        compose.onNodeWithText("future generated tasks", substring = true).fetchSemanticsNode()

        compose.onNodeWithText("End template").performClick()
        compose.runOnIdle { assertEquals(1, endCalls) }
    }

    private fun activeTemplate() = RecurringTemplate(
        id = 7,
        title = "Daily review",
        description = "",
        projectId = null,
        project = null,
        taskTypeId = null,
        taskType = null,
        mode = RecurrenceMode.Scheduled,
        status = RecurrenceStatus.Active,
        frequency = RecurrenceFrequency.Daily,
        interval = 1,
        weekdays = emptyList(),
        monthDay = null,
        quotaCount = null,
        startDate = LocalDate.parse("2026-08-18"),
        endDate = null,
        cycleLimit = null,
        urgency = null,
        importance = null,
        pausedAt = null,
        endedAt = null,
        createdAt = Instant.parse("2026-08-18T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-18T00:00:00Z"),
        checklistItems = emptyList(),
        upcoming = emptyList(),
        currentTasks = emptyList(),
        cadence = "Daily",
        nextOccurrence = LocalDate.parse("2026-08-18"),
    )
}
