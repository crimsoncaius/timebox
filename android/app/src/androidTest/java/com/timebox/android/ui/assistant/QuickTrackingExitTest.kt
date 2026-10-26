package com.timebox.android.ui.assistant

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.AppPreferences
import com.timebox.android.quicktracking.QuickTrackingActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickTrackingExitTest {
    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText().trim()
        }

    private fun leavingSubmits(destroy: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TimeboxApplication
        val previous = shell("settings get secure voice_recognition_service")
        val connection = runBlocking { app.repository.settings.first() }
        try {
            runBlocking { AppPreferences(app).setConnection("http://127.0.0.1:1/", "") }
            shell("settings put secure voice_recognition_service ${instrumentation.context.packageName}/com.timebox.android.ui.assistant.ExitSpeechService")
            shell("pm grant ${app.packageName} android.permission.RECORD_AUDIO")
            shell("pm grant ${instrumentation.context.packageName} android.permission.RECORD_AUDIO")
            instrumentation.runOnMainSync { app.quickTracking.edit("") }
            ActivityScenario.launch<QuickTrackingActivity>(Intent(app, QuickTrackingActivity::class.java)).use { scenario ->
                // Allow the platform to bind the deterministic recognition service.
                Thread.sleep(1500)
                if (destroy) scenario.moveToState(Lifecycle.State.DESTROYED)
                else scenario.moveToState(Lifecycle.State.CREATED)
                val deadline = System.currentTimeMillis() + 5000
                while (app.quickTracking.state.value.text.isBlank() && System.currentTimeMillis() < deadline) Thread.sleep(50)
                assertEquals("exit regression spoken activity", app.quickTracking.state.value.text)
                val finished = System.currentTimeMillis() + 5000
                while (app.quickTracking.state.value.busy && System.currentTimeMillis() < finished) Thread.sleep(50)
                org.junit.Assert.assertFalse("Isolated request should finish before restoring settings", app.quickTracking.state.value.busy)
            }
        } finally {
            runBlocking { AppPreferences(app).setConnection(connection.baseUrl, connection.apiKey) }
            if (previous == "null") shell("settings delete secure voice_recognition_service")
            else shell("settings put secure voice_recognition_service $previous")
        }
    }

    @Test fun closingBeforeFinalTranscriptionSubmitsAfterActivityDestruction() = leavingSubmits(true)
    @Test fun goingHomeBeforeFinalTranscriptionSubmitsWhileActivityStopped() = leavingSubmits(false)
}
