package com.timebox.android.ui.day

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.TaskType
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanNowSheetTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-10-04T10:20:59.732Z")
    private val kind = TaskType(1, "Writing", 0)

    @Test fun adjustmentRetainsTaskIdentityAndConfirmsOnlyOtherPlans() {
        var saved: List<Int>? = null
        val plans = listOf(
            ActivityPlanDto(1, 1, taskId = 7, taskTitle = "Draft proposal", startAt = "2026-10-04T10:00:00Z", endAt = "2026-10-04T11:00:00Z"),
            ActivityPlanDto(2, 1, name = "Meeting", startAt = "2026-10-04T11:00:00Z", endAt = "2026-10-04T11:30:00Z"),
        )
        compose.setContent {
            var minutes by remember { mutableStateOf<Int?>(null) }
            TimeboxTheme(darkTheme = false) {
                SwitchActivitySheet(currentActivity = "Draft proposal", currentId = 5, start = now.minusSeconds(300),
                    records = emptyList(), plans = plans, taskTypes = listOf(kind), selectedType = kind,
                    onTypeChange = {}, name = "", onNameChange = {}, timing = null, onTimingChange = {},
                    now = now, zone = ZoneId.of("UTC"), enabled = true, busy = false, error = null,
                    onDismiss = {}, onConfirm = {}, planMinutes = minutes, onPlanMinutes = { minutes = it },
                    editCurrent = true, keepCurrent = true, currentTaskId = 7, currentTaskTitle = "Draft proposal", currentPlanId = 1,
                    onConfirmPlan = { saved = it })
            }
        }
        compose.onNodeWithText("Change").assertIsDisplayed()
        compose.onNodeWithText("60 min").performClick()
        compose.onNodeWithText("Draft proposal · Writing until 11:20").assertExists()
        compose.onNodeWithText("Save plan").performScrollTo().performClick()
        assertNull(saved)
        compose.onNodeWithText("Replace this planned time?").assertIsDisplayed()
        compose.onNodeWithText("Replace this time").performClick()
        assertEquals(listOf(2), saved)
    }

    @Test fun offlineDurationCannotSaveButOpenEndedCan() {
        compose.setContent {
            var minutes by remember { mutableStateOf<Int?>(null) }
            TimeboxTheme(darkTheme = false) {
                SwitchActivitySheet(currentActivity = "", currentId = 0, start = now, records = emptyList(), plans = emptyList(),
                    taskTypes = listOf(kind), selectedType = kind, onTypeChange = {}, name = "", onNameChange = {},
                    timing = null, onTimingChange = {}, now = now, zone = ZoneId.of("UTC"), enabled = true, busy = false,
                    error = null, onDismiss = {}, onConfirm = {}, planMinutes = minutes, onPlanMinutes = { minutes = it },
                    startTracking = true, planEnabled = false)
            }
        }
        compose.onNodeWithText("15 min").performClick()
        compose.onNode(hasText("Start") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Open-ended").performClick()
        compose.onNode(hasText("Start") and hasClickAction()).assertIsEnabled()
    }
}
