package com.timebox.android.ui.assistant

import android.content.Intent
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.timebox.android.TimeboxApplication
import com.timebox.android.quicktracking.QuickTrackingActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in against an isolated review backend. Exercises final recognition, not microphone acoustics. */
class QuickTrackingEntryTest {
    @Test fun assistantFinalRecognitionSubmitsWithoutConfirmationAndPersists() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveQuickTracking") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TimeboxApplication
        val intent = Intent(Intent.ACTION_ASSIST).setPackage(app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNotNull(app.packageManager.resolveActivity(intent, 0))
        ActivityScenario.launch<QuickTrackingActivity>(intent).use { scenario ->
            scenario.onActivity {
                app.quickSpeech.onResults(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("I'm eating")) })
            }
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline && (app.quickTracking.state.value.busy || app.quickTracking.state.value.text.isBlank())) Thread.sleep(100)
            assertEquals("I'm eating", app.quickTracking.state.value.text)
            assertTrue(app.quickTracking.state.value.message, app.quickTracking.state.value.saved)
            assertNotNull(app.activityRepository.state.value.snapshot?.current)
            assertFalse(app.activityRepository.state.value.pending)
        }
    }
}
