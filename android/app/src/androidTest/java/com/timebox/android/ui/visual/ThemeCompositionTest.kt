package com.timebox.android.ui.visual

import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.ui.theme.DarkTimeboxColors
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemeCompositionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun darkThemeProvidesReadableDefaultContentColor() {
        var providedColor = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                providedColor = LocalContentColor.current
            }
        }

        compose.runOnIdle { assertEquals(DarkTimeboxColors.on, providedColor) }
    }
}
