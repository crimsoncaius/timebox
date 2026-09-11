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
    @Test fun dismissThenOpenOriginalNotificationIntent() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("platformReview") == "true")
        check(BuildConfig.ACTIVITY_TRACKING_DEV)
        app.checkIns.tick()
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        val notification = manager.activeNotifications.single { it.id == 15501 }
        val question = app.activityRepository.state.value.snapshot!!.checkIn!!.question!!
        Log.i("ActivityCheckInProbe", "DISMISS READY: swipe the real notification within 60 seconds")
        val deadline = android.os.SystemClock.elapsedRealtime() + 60_000
        while (manager.activeNotifications.any { it.id == 15501 } && android.os.SystemClock.elapsedRealtime() < deadline) kotlinx.coroutines.delay(250)
        assertFalse(manager.activeNotifications.any { it.id == 15501 })
        assertEquals(question.id, app.activityRepository.state.value.snapshot!!.checkIn!!.question!!.id)
        notification.notification.contentIntent.send()
        kotlinx.coroutines.delay(2000)
        assertEquals(question.id, app.activityRepository.state.value.snapshot!!.checkIn!!.question!!.id)
        Log.i("ActivityCheckInProbe", "Dismissal kept pending; original OS PendingIntent sent for ${question.id}")
        Unit
    }
    @Test fun originalNotificationIntentOpensVisibleQuestion() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("platformReview") == "true")
        check(BuildConfig.ACTIVITY_TRACKING_DEV)
        val pending = android.app.PendingIntent.getActivity(app, 15501, android.content.Intent(app, MainActivity::class.java),
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE)
        assertNotNull("Original notification PendingIntent must already exist", pending)
        pending.send()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun texts(node: android.view.accessibility.AccessibilityNodeInfo?): List<String> =
            if (node == null) emptyList() else listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { texts(node.getChild(it)) }
        val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
        while (android.os.SystemClock.elapsedRealtime() < deadline && "Yes, still doing this" !in texts(automation.rootInActiveWindow)) kotlinx.coroutines.delay(250)
        val visible = texts(automation.rootInActiveWindow)
        Log.i("ActivityCheckInProbe", "Visible UI: $visible")
        assertTrue("Yes, still doing this" in visible)
        Log.i("ActivityCheckInProbe", "Original OS PendingIntent opened the visible current question")
        Unit
    }
}
