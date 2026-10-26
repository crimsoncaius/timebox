package com.timebox.android.ui.chronicle

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import com.timebox.android.ui.theme.TimeboxTheme
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimeGoalDeletionTest {
    @get:Rule val compose = createComposeRule()
    @Volatile private var deleted = false

    @Test fun goalsOfferDeletionWithoutArchiveAndRequireConfirmation() {
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, _ ->
            when (method.name) {
                "timeGoals" -> TimeGoalsWeekDto("2026-09-27", "2026-09-21", "2026-09-14", "Asia/Singapore", "2026-09-27T12:00:00Z",
                    if (deleted) emptyList() else listOf(TimeGoalDto(1, 2, "exercise", "week", 1, "2026-09-14",
                        targetMinutes = 120, nextTargetDate = "2026-09-28",
                        period = GoalPeriodDto("2026-09-21", "2026-09-27", 120, 0.0, "in_progress", emptyList()), days = emptyMap())))
                "deleteTimeGoal" -> { deleted = true; Unit }
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = TimeGoalsViewModel(TimeboxRepository(api))
        compose.setContent {
            val state by vm.state.collectAsState()
            TimeboxTheme { TimeGoalsScreen(vm, state) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("exercise").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Archive").assertDoesNotExist()
        compose.onNodeWithText("exercise").performClick()
        compose.onNodeWithText("View period & blocks").performScrollTo().performClick()
        compose.onNodeWithText("Archive goal").assertDoesNotExist()
        compose.onNodeWithText("Delete goal").performScrollTo().performClick()
        compose.onNodeWithText("Delete this goal?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("Delete goal").performClick()
        compose.onNode(hasText("Delete goal") and hasAnyAncestor(
            isDialog() and hasAnyDescendant(hasText("Delete this goal?")))).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No time goals for this week").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertTrue(deleted) }
    }
}
