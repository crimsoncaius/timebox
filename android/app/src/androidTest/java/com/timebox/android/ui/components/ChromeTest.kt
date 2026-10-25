package com.timebox.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.AppRoutes
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigateButtonOpensDirectory() {
        var opened = false
        compose.setContent { TimeboxTheme { NavigationDock(emptyList(), AppRoutes.DayPattern, {}, { opened = true }) } }
        compose.onNodeWithText("Navigate").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test fun everyDestinationIsDirectlyReachableAtNarrowWidth() {
        var selected: String? = null
        compose.setContent {
            TimeboxTheme {
                Box(Modifier.width(320.dp)) {
                    NavigationDirectory(AppRoutes.DayPattern, {}, { selected = it })
                }
            }
        }
        navigationDestinations.forEach { destination ->
            compose.onNodeWithText(destination.label).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(destination.route, selected) }
        }
        compose.onNodeWithText("Pinned").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close navigation").assertIsDisplayed()
    }
}
