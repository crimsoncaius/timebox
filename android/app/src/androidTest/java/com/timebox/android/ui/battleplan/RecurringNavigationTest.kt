package com.timebox.android.ui.battleplan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class RecurringNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recurringIsIndependentAndRetainsItsCreationAction() {
        var created = false
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                RecurringScreen(
                    state = RecurringUiState(), onRetry = {}, onSelectStatus = {},
                    onNew = { created = true }, onOpen = {},
                )
            }
        }
        compose.onNodeWithText("All Tasks").assertDoesNotExist()
        compose.onNodeWithText("Task Types").assertDoesNotExist()
        compose.onNodeWithText("New recurrence").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, created) }
    }
}
