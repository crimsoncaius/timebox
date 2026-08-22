package com.timebox.android.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bottomNavigationShowsFiveTabsAndSelectsSettings() {
        var selected: TimeboxTab? = null

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                TimeboxBottomNav(selected = TimeboxTab.Settings) { selected = it }
            }
        }

        listOf("Day", "Chronicle", "Battle Plan", "Types", "Settings").forEach { label ->
            compose.onNodeWithContentDescription(label).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Settings").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(TimeboxTab.Settings, selected) }
    }
}
