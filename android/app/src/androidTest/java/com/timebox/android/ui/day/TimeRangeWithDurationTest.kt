package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test

class TimeRangeWithDurationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsDurationBesideRangeWhenItFits() {
        show(320.dp)
        compose.onNodeWithText("09:00 – 10:30").assertIsDisplayed()
        compose.onNodeWithText(" · 1h 30m").assertIsDisplayed()
    }

    @Test fun dropsDurationBeforeTheRangeWhenNarrow() {
        show(90.dp)
        compose.onNodeWithText("09:00 – 10:30").assertIsDisplayed()
        compose.onNodeWithText(" · 1h 30m").assertIsNotDisplayed()
    }

    private fun show(width: Dp) = compose.setContent {
        TimeboxTheme(darkTheme = false) {
            Box(Modifier.width(width)) {
                TimeRangeWithDuration("09:00 – 10:30", "1h 30m", TimeboxTheme.type.monoSmall, TimeboxTheme.colors.onVariant)
            }
        }
    }
}
