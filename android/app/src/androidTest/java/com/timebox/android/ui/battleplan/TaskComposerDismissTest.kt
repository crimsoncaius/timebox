package com.timebox.android.ui.battleplan

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test

class TaskComposerDismissTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun keepEditingAfterSwipePreservesDraftAndAcceptsTaps() {
        var createdDraft: TaskComposerDraft? = null
        var backgroundTapped = false
        compose.setContent {
            var state by remember {
                mutableStateOf(BattlePlanUiState(loading = false, showComposer = true))
            }
            TimeboxTheme(darkTheme = false) {
                TextButton(onClick = { backgroundTapped = true }) { Text("Battle Plan action") }
                if (state.showComposer) {
                    TaskComposerOverlay(
                        state = state,
                        notificationsAllowed = true,
                        onRequestNotificationPermission = {},
                        onDraftChange = { state = state.copy(composerDraft = it.copy(dirty = true)) },
                        onReminderEnabledChange = {},
                        onDismiss = { state = state.copy(showComposer = false) },
                        onCreate = { createdDraft = state.composerDraft },
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Task title").performTextInput("Prepare launch notes")
        repeat(2) {
            swipeComposerDown()
            compose.onNodeWithText("Discard new task?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithText("Prepare launch notes").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Task title").performTouchInput { click() }
        compose.onNodeWithContentDescription("Task title").performTextReplacement("Prepare launch notes updated")
        compose.onNodeWithText("Add task").performTouchInput { click() }
        compose.runOnIdle {
            check(createdDraft?.title == "Prepare launch notes updated")
        }
        swipeComposerDown()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithContentDescription("Task title").assertDoesNotExist()
        compose.onNodeWithText("Battle Plan action").performTouchInput { click() }
        compose.runOnIdle { check(backgroundTapped) }
    }

    @Test
    fun keepEditingAfterSystemBackLeavesDraftVisibleAndUsable() {
        var created = false
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                TaskComposerOverlay(
                    state = BattlePlanUiState(loading = false, showComposer = true,
                        composerDraft = TaskComposerDraft(title = "Keep this draft", dirty = true)),
                    notificationsAllowed = true, onRequestNotificationPermission = {},
                    onDraftChange = {}, onReminderEnabledChange = {}, onDismiss = {},
                    onCreate = { created = true },
                )
            }
        }
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Discard new task?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Keep this draft").assertIsDisplayed()
        compose.onNodeWithText("Add task").performClick()
        compose.runOnIdle { check(created) }
    }

    @Test
    fun untouchedDraftSwipesAwayWithoutConfirmation() {
        var backgroundTapped = false
        compose.setContent {
            var showComposer by remember { mutableStateOf(true) }
            TimeboxTheme(darkTheme = false) {
                TextButton(onClick = { backgroundTapped = true }) { Text("Battle Plan action") }
                if (showComposer) {
                    TaskComposerOverlay(
                        state = BattlePlanUiState(loading = false, showComposer = true),
                        notificationsAllowed = true,
                        onRequestNotificationPermission = {},
                        onDraftChange = {},
                        onReminderEnabledChange = {},
                        onDismiss = { showComposer = false },
                        onCreate = {},
                    )
                }
            }
        }
        swipeComposerDown()
        compose.onNodeWithText("Discard new task?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Task title").assertDoesNotExist()
        compose.onNodeWithText("Battle Plan action").performTouchInput { click() }
        compose.runOnIdle { check(backgroundTapped) }
    }

    private fun swipeComposerDown() {
        compose.onNodeWithContentDescription("Close task").performTouchInput {
            swipe(center, center + Offset(0f, 1400f), durationMillis = 400)
        }
    }
}
