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

class TimeGoalArchiveTest {
    @get:Rule val compose = createComposeRule()
    @Volatile private var archived = false
    @Volatile private var deleted = false
    private val latest = GoalPeriodDto("2026-09-21", "2026-09-27", 120, 3600.0, "excused", listOf(
        GoalBlockDto(9, "exercise", "Morning walk", "2026-09-22T01:00:00Z", "2026-09-22T02:00:00Z", 3600.0)))
    private val entry = ArchivedTimeGoalDto(1, 2, "exercise", "week", 1, "2026-09-14", "2026-09-27", 120)

    private fun launch(initiallyArchived: Boolean) {
        archived = initiallyArchived
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, _ ->
            when (method.name) {
                "timeGoals" -> TimeGoalsWeekDto("2026-09-27", "2026-09-21", "2026-09-14", "Asia/Singapore", "2026-09-27T12:00:00Z",
                    if (archived || deleted) emptyList() else listOf(TimeGoalDto(1, 2, "exercise", "week", 1, "2026-09-14",
                        targetMinutes = 120, nextTargetDate = "2026-09-28", period = latest.copy(outcome = "in_progress"), days = emptyMap())))
                "timeGoalArchive" -> TimeGoalArchiveDto("2026-09-27", "Asia/Singapore", "2026-09-27T12:00:00Z",
                    if (archived && !deleted) listOf(entry) else emptyList())
                "timeGoalHistory" -> TimeGoalHistoryDto(entry, "Asia/Singapore", "2026-09-27T12:00:00Z", listOf(latest,
                    latest.copy(start = "2026-09-14", end = "2026-09-20", targetMinutes = 60, outcome = "met", blocks = emptyList())))
                "endTimeGoal" -> { archived = true; Unit }
                "deleteTimeGoal" -> { deleted = true; Unit }
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = TimeGoalsViewModel(TimeboxRepository(api))
        compose.setContent {
            val state by vm.state.collectAsState()
            TimeboxTheme { TimeGoalsScreen(vm, state) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No time goals for this week").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("exercise").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun archivedHistoryShowsPastTargetsAndBlocksThenConfirmsDeletion() {
        launch(true)
        compose.onNodeWithText("Archive").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("exercise").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("exercise").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1h / 2h").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1h / 1h").assertExists()
        compose.onAllNodesWithText("View period & blocks")[0].performClick()
        compose.onNodeWithText("Morning walk").assertIsDisplayed()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithTag("time-goal-archive-list").performScrollToNode(hasText("Delete goal"))
        compose.onNodeWithText("Delete goal").performClick()
        compose.onNodeWithText("Permanently remove this goal", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("Delete goal").performClick()
        compose.onAllNodesWithText("Delete goal").filter(hasAnyAncestor(isDialog()))[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No archived goals").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertTrue(deleted) }
    }

    @Test fun archiveMovesActiveGoalIntoArchiveWithSeparateConfirmation() {
        launch(false)
        compose.onNodeWithText("exercise").performClick()
        compose.onNodeWithText("View period & blocks").performScrollTo().performClick()
        compose.onNodeWithText("Archive goal").performScrollTo().performClick()
        compose.onNodeWithText("Archive this goal today?").assertIsDisplayed()
        compose.onNodeWithText("This goal cannot be reactivated.", substring = true).assertExists()
        compose.onNode(hasText("Archive goal") and hasAnyAncestor(
            isDialog() and hasAnyDescendant(hasText("Archive this goal today?")))).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No time goals for this week").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Archive").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("exercise").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertTrue(archived); assertFalse(deleted) }
    }
}
