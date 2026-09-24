package com.timebox.android.ui.battleplan

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoutinePreplanningTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destinationsSwitchBetweenQueueTimeAndNone() {
        val state = mutableStateOf(RecurringEditorUiState(title = "Review", startDate = "2026-09-24"))
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                Column { RoutinePreplanningFields(state.value) { state.value = it } }
            }
        }
        compose.onNodeWithText("No pre-planning").performClick()
        compose.onNodeWithText("Ready to Plan").performClick()
        compose.runOnIdle { assertEquals("ready_to_plan", state.value.preplanningDestination()) }
        compose.onNodeWithText("Planned Block start").assertDoesNotExist()
        compose.onNodeWithText("Ready to Plan").performClick()
        compose.onNodeWithText("Planned time").performClick()
        compose.onNodeWithText("Planned Block start").assertExists()
        compose.runOnIdle { assertEquals("planned_time", state.value.preplanningDestination()) }
        compose.onNodeWithText("Planned time").performClick()
        compose.onNodeWithText("No pre-planning").performClick()
        compose.onNodeWithText("Planned Block start").assertDoesNotExist()
        compose.runOnIdle { assertEquals("none", state.value.preplanningDestination()) }
    }
}
