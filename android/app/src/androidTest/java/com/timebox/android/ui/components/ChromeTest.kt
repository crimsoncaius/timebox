package com.timebox.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChromeTest {
    @get:Rule val compose = createComposeRule()

    private val labels = listOf("Day", "Chronicle", "Battle Plan", "Assistant", "Settings")

    @Test
    fun bottomNavigationShowsFiveTabsAndSelectsSettings() {
        var selected: TimeboxTab? = null

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                TimeboxBottomNav(selected = TimeboxTab.Settings) { selected = it }
            }
        }

        labels.forEach { label ->
            compose.onNodeWithContentDescription(label).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Settings").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(TimeboxTab.Settings, selected) }
    }

    @Test
    fun noTabLabelTruncatesAtTheNarrowestSupportedWidth() {
        val naturalWidths = mutableMapOf<String, Int>()
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                Column {
                    Box(Modifier.width(360.dp)) {
                        TimeboxBottomNav(selected = TimeboxTab.Day) {}
                    }
                    // Each label laid out without a width limit, for comparison.
                    labels.forEach { label ->
                        Text(
                            label,
                            style = TimeboxTheme.type.navLabel,
                            softWrap = false,
                            modifier = Modifier
                                .wrapContentWidth(unbounded = true)
                                .clearAndSetSemantics {}
                                .onGloballyPositioned { naturalWidths[label] = it.size.width },
                        )
                    }
                }
            }
        }

        labels.forEach { label ->
            val shown = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().size.width
            val natural = compose.runOnIdle { naturalWidths.getValue(label) }
            assertTrue("$label is clipped at 360dp: $shown of $natural px", shown >= natural)
        }
    }
}
