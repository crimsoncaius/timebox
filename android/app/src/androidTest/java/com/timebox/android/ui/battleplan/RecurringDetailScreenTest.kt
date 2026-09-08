package com.timebox.android.ui.battleplan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.RecurrenceStatus
import com.timebox.android.data.RecurringTemplate
import com.timebox.android.data.RecurringPreplanningSchedule
import com.timebox.android.data.RecurringPreplanningSlot
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

    @Test
    fun detailShowsPersistedRecurringPreplanningScheduleSlots() {
        val template = activeTemplate().copy(
            frequency = RecurrenceFrequency.Weekly,
            weekdays = listOf(0, 2),
            cadence = "Every week on Mon, Wed",
            preplanningSchedule = RecurringPreplanningSchedule(listOf(
                RecurringPreplanningSlot(key = "morning", position = 0, weekday = 0, startMinute = 480, endMinute = 540),
                RecurringPreplanningSlot(key = "afternoon", position = 1, weekday = 2, startMinute = 900, endMinute = 1440),
            )),
        )
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                RecurringDetailScreen(
                    state = RecurringUiState(selectedTemplate = template),
                    onBack = {}, onRetry = {}, onEdit = {}, onOpenTask = {},
                    onPause = {}, onResume = {}, onEnd = {}, onRequestDelete = {},
                    onDismissDelete = {}, onConfirmDelete = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Recurring Pre-planning Schedule")
            .performScrollTo()
            .fetchSemanticsNode()
        compose.onNodeWithText("Mon · 08:00–09:00").performScrollTo().fetchSemanticsNode()
        compose.onNodeWithText("Wed · 15:00–00:00").performScrollTo().fetchSemanticsNode()
    }

    private fun activeTemplate() = RecurringTemplate(
        id = 7,
        title = "Daily review",
        description = "",
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
