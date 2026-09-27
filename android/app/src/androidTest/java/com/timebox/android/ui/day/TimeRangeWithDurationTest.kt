package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimeRangeWithDurationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsDurationBesideRangeWhenItFits() {
        show(320.dp)
        compose.onNodeWithText("09:00 – 10:30").assertIsDisplayed()
        compose.onNodeWithText(" · 1h 30m").assertIsDisplayed()
    }

    @Test fun wrapsDurationBeneathTheRangeWhenNarrowAndTall() {
        show(90.dp, 40.dp)
        compose.onNodeWithText("09:00 – 10:30").assertIsDisplayed()
        compose.onNodeWithText(" · 1h 30m").assertIsDisplayed()
        val range = compose.onNodeWithText("09:00 – 10:30").getUnclippedBoundsInRoot()
        val duration = compose.onNodeWithText(" · 1h 30m").getUnclippedBoundsInRoot()
        assertTrue(duration.top >= range.bottom)
    }

    @Test fun dropsDurationBeforeTheRangeWhenNarrowAndShort() {
        show(90.dp, 18.dp)
        compose.onNodeWithText("09:00 – 10:30").assertIsDisplayed()
        compose.onNodeWithText(" · 1h 30m").assertIsNotDisplayed()
    }

    private fun show(width: Dp, height: Dp = 40.dp) = compose.setContent {
        TimeboxTheme(darkTheme = false) {
            Box(Modifier.width(width).heightIn(max = height)) {
                TimeRangeWithDuration("09:00 – 10:30", "1h 30m", TimeboxTheme.type.monoSmall, TimeboxTheme.colors.onVariant)
            }
        }
    }
}
