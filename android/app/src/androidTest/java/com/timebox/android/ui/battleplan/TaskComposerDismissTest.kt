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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        compose.onNodeWithText("Title").performTextInput("Prepare launch notes")
        repeat(2) {
            swipeComposerDown()
            compose.onNodeWithText("Discard new task?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithText("Prepare launch notes").assertIsDisplayed()
        }
        compose.onNodeWithText("Title").performTouchInput { click() }
        compose.onNodeWithText("Title").performTextInput(" updated")
        compose.onNodeWithText("Create task").performTouchInput { click() }
        compose.runOnIdle {
            check(createdDraft?.title == "Prepare launch notes updated")
        }
        swipeComposerDown()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("New task").assertDoesNotExist()
        compose.onNodeWithText("Battle Plan action").performTouchInput { click() }
        compose.runOnIdle { check(backgroundTapped) }
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
        compose.onNodeWithText("New task").assertDoesNotExist()
        compose.onNodeWithText("Battle Plan action").performTouchInput { click() }
        compose.runOnIdle { check(backgroundTapped) }
    }

    private fun swipeComposerDown() {
        compose.onNodeWithText("New task").performTouchInput {
            swipe(center, center + Offset(0f, 1400f), durationMillis = 400)
        }
    }
}
