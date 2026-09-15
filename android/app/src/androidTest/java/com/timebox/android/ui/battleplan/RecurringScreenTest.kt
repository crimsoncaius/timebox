package com.timebox.android.ui.battleplan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.RecurrenceStatus
import com.timebox.android.data.RecurringTemplate
import com.timebox.android.data.TaskType
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecurringScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun listCardsShowEqualChipsAndOpenOnTap() {
        var opened = 0
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                RecurringScreen(
                    state = RecurringUiState(
                        templates = listOf(
                            series(
                                id = 1,
                                title = "Weekly review",
                                mode = RecurrenceMode.Scheduled,
                                cadence = "Every 1 week · Mon",
                                next = LocalDate.of(2026, 9, 21),
                                taskType = TaskType(1, "Planning", 0),
                            ),
                            series(
                                id = 2,
                                title = "Gym",
                                mode = RecurrenceMode.Quota,
                                cadence = "3 times per week",
                                next = LocalDate.of(2026, 9, 21),
                                taskType = TaskType(2, "Health", 0),
                            ),
                        ),
                    ),
                    onRetry = {},
                    onSelectStatus = {},
                    onNew = {},
                    onOpen = { opened = it },
                )
            }
        }

        compose.onNodeWithText("Weekly review").assertIsDisplayed()
        compose.onNodeWithText("Planning").assertIsDisplayed()
        compose.onNodeWithText("Every 1 week · Mon").assertIsDisplayed()
        compose.onNodeWithText("Next 21 Sep").assertIsDisplayed()
        compose.onNodeWithText("Period ends 21 Sep").assertIsDisplayed()
        compose.onNodeWithText("2026-09-21").assertDoesNotExist()
        compose.onNodeWithText("Weekly review").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun pausedAndEndedRowsKeepAStatusChip() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                RecurringScreen(
                    state = RecurringUiState(
                        selectedStatus = RecurrenceStatus.Paused,
                        templates = listOf(
                            series(
                                id = 5,
                                title = "Language practice",
                                status = RecurrenceStatus.Paused,
                                cadence = "Every 1 week · Tue, Thu",
                                next = LocalDate.of(2026, 9, 17),
                            ),
                        ),
                    ),
                    onRetry = {},
                    onSelectStatus = {},
                    onNew = {},
                    onOpen = {},
                )
            }
        }
        compose.onNodeWithText("Language practice").assertIsDisplayed()
        compose.onAllNodesWithText("Paused")[1].assertIsDisplayed()
        compose.onNodeWithText("Next 17 Sep").assertIsDisplayed()
    }

    @Test
    fun overflowDeleteIsReservedForEndedSeries() {
        var requested: RecurringTemplate? = null
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                RecurringScreen(
                    state = RecurringUiState(
                        templates = listOf(
                            series(id = 1, title = "Weekly review", status = RecurrenceStatus.Active),
                        ),
                    ),
                    onRetry = {},
                    onSelectStatus = {},
                    onNew = {},
                    onOpen = {},
                    onRequestDelete = { requested = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Actions for Weekly review").performClick()
        compose.onNodeWithText("Delete").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(null, requested) }
    }

    @Test
    fun overflowDeleteOnEndedSeriesRequestsConfirmation() {
        var requested: RecurringTemplate? = null
        val ended = series(
            id = 7,
            title = "Thesis check-in",
            status = RecurrenceStatus.Ended,
            next = null,
        )
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                RecurringScreen(
                    state = RecurringUiState(
                        selectedStatus = RecurrenceStatus.Ended,
                        templates = listOf(ended),
                    ),
                    onRetry = {},
                    onSelectStatus = {},
                    onNew = {},
                    onOpen = {},
                    onRequestDelete = { requested = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Actions for Thesis check-in").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(7, requested?.id) }
    }

    private fun series(
        id: Int,
        title: String,
        mode: RecurrenceMode = RecurrenceMode.Scheduled,
        status: RecurrenceStatus = RecurrenceStatus.Active,
        cadence: String = "Daily",
        next: LocalDate? = LocalDate.of(2026, 9, 21),
        taskType: TaskType? = null,
    ) = RecurringTemplate(
        id = id,
        title = title,
        description = "",
        taskTypeId = taskType?.id,
        taskType = taskType,
        mode = mode,
        status = status,
        frequency = RecurrenceFrequency.Weekly,
        interval = 1,
        weekdays = emptyList(),
        monthDay = null,
        quotaCount = if (mode == RecurrenceMode.Quota) 3 else null,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = if (status == RecurrenceStatus.Ended) LocalDate.of(2026, 9, 1) else null,
        cycleLimit = null,
        urgency = null,
        importance = null,
        pausedAt = if (status == RecurrenceStatus.Paused) Instant.parse("2026-09-15T02:00:00Z") else null,
        endedAt = if (status == RecurrenceStatus.Ended) Instant.parse("2026-09-15T02:00:00Z") else null,
        createdAt = Instant.parse("2026-09-15T02:00:00Z"),
        updatedAt = Instant.parse("2026-09-15T02:00:00Z"),
        checklistItems = emptyList(),
        upcoming = emptyList(),
        currentTasks = emptyList(),
        cadence = cadence,
        nextOccurrence = next,
    )
}
