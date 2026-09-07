package com.timebox.android.ui.battleplan

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.Project
import com.timebox.android.data.remote.BattleTaskDto
import com.timebox.android.data.toModel
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class MoveProjectMenuItemTest {
    @get:Rule val compose = createComposeRule()
    private val task = BattleTaskDto(id = 1, projectId = 3, title = "Review concepts", description = "Keep notes",
        readyToPlan = true, status = "open", position = 0, createdAt = "2026-09-07T00:00:00Z", updatedAt = "2026-09-07T00:00:00Z").toModel()
    private val projects = listOf(Project(3, "Website", Instant.EPOCH, Instant.EPOCH), Project(4, "Studio", Instant.EPOCH, Instant.EPOCH))

    @Test fun selectionAndCancellationNeverMoveUntilExplicitConfirmation() {
        val moves = mutableListOf<Int?>()
        compose.setContent {
            TimeboxTheme { MoveProjectMenuItem(task, ProjectMoveActions(projects, false, null) { _, id -> moves.add(id) }, {}) }
        }
        compose.onNodeWithText("Move to project").performClick()
        compose.onNodeWithText("Current: Website").assertExists()
        compose.onNodeWithText("Move", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Studio").performClick()
        compose.onNodeWithText("Move to Studio?").assertExists()
        compose.runOnIdle { check(moves.isEmpty()) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Studio").assertDoesNotExist()
        compose.onNodeWithText("Move to project").performClick()
        compose.onNodeWithText("Move", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Admin", substring = false).performClick()
        compose.onNodeWithText("Move", substring = false).performClick()
        compose.runOnIdle { check(moves == listOf<Int?>(null)) }
    }

    @Test fun pendingMoveDisablesControlsAndFailureCanRetryBeforeClosingOnSuccess() {
        var saving by mutableStateOf(false)
        var message by mutableStateOf<String?>(null)
        var saved by mutableStateOf(task)
        var calls = 0
        var closed = false
        compose.setContent {
            TimeboxTheme { MoveProjectMenuItem(saved, ProjectMoveActions(projects, saving, message) { _, _ -> calls++; saving = true; message = null }, { closed = true }) }
        }
        compose.onNodeWithText("Move to project").performClick()
        compose.onNodeWithText("Studio").performClick()
        compose.onNodeWithText("Move", substring = false).performClick()
        compose.onNodeWithText("Moving…").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.runOnIdle { saving = false; message = "Network unavailable" }
        compose.onNodeWithText("Network unavailable").assertExists()
        compose.runOnIdle { check(!closed && saved.projectId == 3) }
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { check(calls == 2); saved = task.copy(projectId = 4); saving = false; message = "Moved" }
        compose.waitForIdle()
        compose.runOnIdle { check(closed); check(saved.description == task.description && saved.readyToPlan) }
    }
}
