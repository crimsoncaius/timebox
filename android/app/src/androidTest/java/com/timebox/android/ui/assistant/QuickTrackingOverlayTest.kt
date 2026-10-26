package com.timebox.android.ui.assistant

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.timebox.android.quicktracking.QuickCaptureState
import com.timebox.android.quicktracking.QuickTrackingOverlay
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuickTrackingOverlayTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun captureNeverShowsTranscriptionAndFailureOffersRecovery() {
        val capture = mutableStateOf(QuickCaptureState(text = "private spoken activity", busy = true))
        var retries = 0
        var settings = 0
        var closes = 0
        var speaks = 0
        val hasSettings = mutableStateOf(true)
        compose.setContent {
            TimeboxTheme {
                QuickTrackingOverlay(capture.value, false, null, hasSettings.value,
                    { closes++ }, { retries++ }, { settings++ }, { speaks++ })
            }
        }
        compose.onNodeWithContentDescription("Recording activity").assertIsDisplayed()
        compose.onNodeWithText("private spoken activity").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.runOnIdle { capture.value = capture.value.copy(busy = false, message = "Microphone unavailable") }
        compose.onNodeWithText("Microphone unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.runOnIdle { hasSettings.value = false }
        compose.onNodeWithText("Speak again").performClick()
        compose.onNodeWithContentDescription("Close voice tracking").performClick()
        compose.runOnIdle {
            assertEquals(1, retries); assertEquals(1, settings); assertEquals(1, closes)
            assertEquals(1, speaks)
            capture.value = capture.value.copy(saved = true)
        }
        compose.onNodeWithContentDescription("Activity recorded").assertIsDisplayed()
        compose.onNodeWithText("Microphone unavailable").assertDoesNotExist()
        compose.onNodeWithText("private spoken activity").assertDoesNotExist()
    }
}
