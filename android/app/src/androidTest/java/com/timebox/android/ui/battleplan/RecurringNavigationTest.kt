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

    @Test fun recurringKeepsTheTaskNavigationMenu() {
        var selected: BattlePlanScope? = null
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                RecurringScreen(
                    state = RecurringUiState(), onRetry = {}, onSelectStatus = {},
                    onNew = {}, onOpen = {},
                    onSelectScope = { selected = it },
                )
            }
        }
        compose.onNodeWithText("Recurring").assertIsDisplayed().performClick()
        compose.onNodeWithText("Admin").assertIsDisplayed()
        compose.onNodeWithText("All Tasks").assertIsDisplayed()
        compose.onNodeWithText("Admin").performClick()
        compose.runOnIdle { assertEquals(BattlePlanScope.Admin, selected) }
        compose.onNodeWithText("Recurring").assertIsDisplayed()
    }
}
