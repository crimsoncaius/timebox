package com.timebox.android

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.timebox.android.data.CheckInPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Opt-in review probes use the real OS and gated isolated server; no fabricated sensor events. */
class ActivityCheckInPlatformTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TimeboxApplication
    @Test fun initializeMinimumThreshold() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("platformReview") == "true")
        check(BuildConfig.ACTIVITY_TRACKING_DEV)
        app.activityRepository.refresh()
        assertNotNull(app.activityRepository.state.value.snapshot?.current)
        app.activityRepository.setCheckInPreferences(CheckInPreferences(true, 15))
        app.checkIns.tick()
        Log.i("ActivityCheckInProbe", "initialized ${app.checkIns.status.value}")
        Unit
    }
    @Test fun inspectRealPlatform() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("platformReview") == "true")
        check(BuildConfig.ACTIVITY_TRACKING_DEV)
        app.checkIns.tick()
        val now = System.currentTimeMillis()
        val history = app.getSystemService(UsageStatsManager::class.java).queryEvents(now - 2 * 60 * 60_000, now)
        val event = UsageEvents.Event()
        val transitions = mutableListOf<String>()
        while (history != null && history.hasNextEvent()) {
            history.getNextEvent(event)
            if (event.eventType in listOf(15, 16, 17, 18, 26, 27)) transitions += "${event.timeStamp}:${event.eventType}"
        }
        Log.i("ActivityCheckInProbe", "usage=${app.checkIns.usageGranted()} interactive=${app.getSystemService(PowerManager::class.java).isInteractive} status=${app.checkIns.status.value} transitions=$transitions question=${app.activityRepository.state.value.snapshot?.checkIn?.question}")
        Log.i("ActivityCheckInProbe", "durable=${app.getSharedPreferences("activity-screen-evidence-v1", Context.MODE_PRIVATE).all}")
        Unit
    }
}
