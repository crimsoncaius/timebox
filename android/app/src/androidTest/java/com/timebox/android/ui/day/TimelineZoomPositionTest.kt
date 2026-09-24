package com.timebox.android.ui.day

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimelineZoomPositionTest {
    @get:Rule val compose = createComposeRule()

    private fun checkReset(scale: Float, atEnd: Boolean = false) {
        val zoom = TimelineZoom(scale)
        lateinit var scroll: ScrollState
        compose.setContent {
            scroll = rememberScrollState()
            CompositionLocalProvider(LocalTimelineZoom provides zoom) {
                Box(Modifier.size(200.dp).timelinePinch(scroll).verticalScroll(scroll)) {
                    Box(Modifier.height(2000.dp * zoom.scale))
                }
            }
        }
        compose.runOnIdle {
            runBlocking { scroll.scrollTo(if (atEnd) scroll.maxValue else scroll.viewportSize * 2) }
        }
        var expected = 0
        compose.runOnIdle {
            expected = zoomScrollOffset(scroll.value, scroll.viewportSize / 2f, scale, 1f)
            zoom.reset()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1f, zoom.scale)
            assertEquals(expected.coerceAtMost(scroll.maxValue), scroll.value)
        }
    }

    @Test fun resetFromLargerScalePreservesCentre() = checkReset(3f)
    @Test fun resetFromSmallerScalePreservesCentre() = checkReset(0.5f)
    @Test fun resetNearEndClampsToNewBounds() = checkReset(3f, atEnd = true)
}
