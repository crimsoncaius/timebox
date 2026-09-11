package com.timebox.android.ui.day

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.timebox.android.MainActivity
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.WorkModeSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicitly opt in only against the owned, restored local rehearsal database. */
class ActivityCutoverRehearsalTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun savedLegacyActivityRestoresFocusAndLeavesRecoveryInDay() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("activityCutoverRehearsal") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TimeboxApplication
        val preferences = AppPreferences(app)
        val before = app.repository.getActivity()
        val current = checkNotNull(before.current)
        assertFalse(preferences.legacyWorkModeRecovered())
        preferences.setWorkMode(WorkModeSnapshot(current.startAt, current.startAt, before.serverAt, activeActualId = current.id))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15000) { app.focusController.state.value.active }
            compose.onNodeWithText("Exit Focus").assertIsDisplayed().performClick()
            compose.onNodeWithText("Review old Work Mode data").assertIsDisplayed().performClick()
            compose.onNodeWithText("Saved device observations, not recorded time. Use Day add/edit for corrections.").assertIsDisplayed()
            assertTrue(preferences.legacyWorkModeRecovered())
            assertTrue(preferences.legacyWorkModeRecovery()!!.contains(current.startAt))
            val after = app.repository.getActivity()
            assertEquals(current.id, after.current!!.id)
            assertEquals(current.startAt, after.current.startAt)
            assertEquals(before.records, after.records)
        }
    }
}
