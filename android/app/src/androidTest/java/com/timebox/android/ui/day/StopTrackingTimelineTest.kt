package com.timebox.android.ui.day

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.TaskType
import com.timebox.android.data.remote.*
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StopTrackingTimelineTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-09-12T14:00:00Z")
    private val start = now.minusSeconds(7200)
    private val boundary = now.minusSeconds(1807)
    private val current = ActualBlockDto(42, 1, TaskTypeDto(1, "Reading"), name = "Book",
        startAt = start.toString(), createdAt = start.toString(), updatedAt = start.toString())
    private val plan = ActivityPlanDto(7, 1, name = "Read", startAt = start.toString(), endAt = boundary.toString())

    @Test fun stopPreviewSnapsDragsClampsAndKeepsActionsAccessible() {
        var timing by mutableStateOf<ActivityTimeValue?>(null)
        var busy by mutableStateOf(false)
        var cancelled = false
        var commits = 0
        val zone = ZoneId.of("UTC")
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                StopTrackingSheet(current.id, listOf(current), listOf(plan), listOf(TaskType(1, "Reading", 0)),
                    "Book", start, timing, { timing = it }, now, zone, true, busy, null,
                    { cancelled = true }, { commits++ })
            }
        }
        compose.onNodeWithText("15 min ago").assertDoesNotExist()
        compose.onNodeWithText("Choose time").assertDoesNotExist()
        compose.onNodeWithText("Next activity").assertDoesNotExist()
        compose.onNodeWithText("Unrecorded", substring = false).assertExists()
        compose.onNodeWithText("Read ends · 13:29").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(boundary, timing!!.resolve(zone)) }
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNode(hasText("Stop at 13:29") and hasClickAction()).assertIsDisplayed()
        val timeline = compose.onNodeWithTag("stop-timeline")
        timeline.performScrollTo().performTouchInput {
            swipe(Offset(width * .8f, height * .3f), Offset(width * .8f, height * .5f))
        }
        compose.runOnIdle { assertTrue(timing!!.resolve(zone) in start..now) }
        timeline.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.runOnIdle { assertEquals(start, timing!!.resolve(zone)) }
        timeline.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle { assertNull(timing) }
        compose.onNodeWithContentDescription("Earlier context").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Later context").performClick()
        compose.onNodeWithText("Now", substring = false).performClick()
        compose.runOnIdle { assertNull(timing); busy = true }
        timeline.performScrollTo().performTouchInput { click(Offset(width * .8f, height * .2f)) }
        compose.runOnIdle { assertNull(timing) }
        compose.onNode(hasText("Stopping…") and hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(cancelled); assertEquals(0, commits) }
    }
}
