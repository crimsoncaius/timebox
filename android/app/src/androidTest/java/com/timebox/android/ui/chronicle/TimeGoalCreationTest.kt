package com.timebox.android.ui.chronicle

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import com.timebox.android.ui.theme.TimeboxTheme
import java.lang.reflect.Proxy
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimeGoalCreationTest {
    @get:Rule val compose = createComposeRule()
    private var saved: TimeGoalWriteDto? = null
    private var dismissed = false

    private fun openEditor() {
        val goal = TimeGoalDto(1, 2, "exercise", "week", 1, "2026-09-21",
            targetMinutes = 120, nextTargetDate = "2026-09-28",
            period = GoalPeriodDto("2026-09-21", "2026-09-27", 120, 0.0, "in_progress", emptyList()),
            days = emptyMap())
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "listTaskTypes" -> listOf(TaskTypeDto(2, "exercise"))
                "createTimeGoal" -> { saved = args!![0] as TimeGoalWriteDto; goal }
                "timeGoals" -> TimeGoalsWeekDto("2026-09-23", "2026-09-21", "2026-09-21", "Asia/Singapore",
                    "2026-09-23T12:00:00Z", listOf(goal))
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = TimeGoalsViewModel(TimeboxRepository(api))
        compose.setContent {
            val state by vm.state.collectAsState()
            TimeboxTheme {
                TimeGoalEditor(null, LocalDate.parse("2026-09-23"), "Asia/Singapore", state, vm) { dismissed = true }
            }
        }
    }

    @Test fun fieldSheetsKeepDraftAndCreateCalendarGoal() {
        openEditor()
        compose.onNodeWithText("Create Time Goal").assertIsNotEnabled()
        compose.onNodeWithTag("goal-task-type").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("exercise").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("exercise").performClick()
        compose.onNodeWithTag("goal-duration").performClick()
        compose.onNodeWithTag("goal-minutes").performTextReplacement("99")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Create Time Goal").assertIsNotEnabled()
        compose.onNodeWithTag("goal-duration").performClick()
        compose.onNodeWithText("2h").performClick()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithTag("goal-period").performClick()
        compose.onNodeWithText("Month", substring = false).performClick()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Create Time Goal").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { dismissed }
        compose.runOnIdle {
            assertEquals(TimeGoalWriteDto(2, "month", 1, 120, "2026-09-01"), saved)
        }
    }

    @Test fun cancellingChangedDraftRequiresExplicitDiscard() {
        openEditor()
        compose.onNodeWithTag("goal-duration").performClick()
        compose.onNodeWithText("2h").performClick()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithTag("goal-period").performClick()
        pressBack()
        compose.onNodeWithTag("goal-duration").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.runOnIdle { assertFalse(dismissed); assertNull(saved) }
        compose.onNodeWithTag("goal-duration").assertTextContains("2h")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard", substring = false).performClick()
        compose.runOnIdle { assertTrue(dismissed); assertNull(saved) }
    }
}
