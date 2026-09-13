package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.timebox.android.data.*
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ShortTimelineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pinchEnlargesBothLanesWithoutEditingTheirTimes() {
        val zoom = TimelineZoom()
        val date = LocalDate.of(2026, 9, 13)
        val blocks = Lane.entries.mapIndexed { index, lane ->
            TimeBlock(index + 1, lane, 1, "Work", null, null, null, null,
                startMinute = 600, endMinute = 605, name = "Five minutes")
        }
        val day = Day(date, 10, 14, false, blocks, timezone = "UTC", today = date.plusDays(1), serverNowMinute = null)
        var writes = 0
        compose.setContent {
            TimeboxTheme {
                val scroll = rememberScrollState()
                CompositionLocalProvider(LocalTimelineZoom provides zoom) {
                    Box(Modifier.fillMaxSize().testTag("zoom-surface").timelinePinch(scroll).verticalScroll(scroll)) {
                        DayTimeline(day, null, null, onTapSlot = { _, _ -> }, onSelectBlock = {},
                            onCommitMove = { _, _, _ -> writes++ })
                    }
                }
            }
        }
        val before = compose.onNodeWithTag("day-block-1").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        compose.onNodeWithTag("zoom-surface").performTouchInput {
            down(0, Offset(100f, 100f))
            down(1, Offset(120f, 120f))
            moveTo(0, Offset(40f, 40f))
            moveTo(1, Offset(240f, 240f))
            up(0)
            up(1)
        }
        compose.waitForIdle()
        assertTrue(zoom.scale > 1f)
        val planned = compose.onNodeWithTag("day-block-1").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        val actual = compose.onNodeWithTag("day-block-2").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        assertTrue(planned > before)
        assertEquals(planned, actual, 1f)
        assertEquals(0, writes)
        assertTrue(blocks.all { it.startMinute == 600 && it.endMinute == 605 })
    }
}
