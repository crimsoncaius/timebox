package com.timebox.android.ui.assistant

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import com.timebox.android.quicktracking.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.UUID

/** Exercises the real controller, preferences and activity journal without an LLM or network. */
class QuickFailureRecoveryTest {
    @Test fun prepareIsolatedReviewExamples() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("prepareFailureReview") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TimeboxApplication
        withContext(Dispatchers.Main) { app.quickTracking.submit("Review example: started work ten minutes ago", "app") }
        withTimeout(130_000) { while (app.quickTracking.state.value.busy) delay(100) }
        assertFalse(app.quickTracking.state.value.saved)
        withContext(Dispatchers.Main) { app.quickTracking.captureFailed("No speech heard. Try again.", "app", SpeechCaptureException(7)) }
        delay(100)
        withContext(Dispatchers.Main) { app.quickTracking.refresh() }
        delay(1000)
        withContext(Dispatchers.Main) {
            app.reviewRequested.value = true
            app.startActivity(android.content.Intent(app, com.timebox.android.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private class Api : AssistantTransport {
        var fail = true
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun interpretQuick(body: JsonObject): QuickInterpretation {
            calls++; gate?.await()
            if (fail) throw IOException("Cannot reach Assistant", IOException("private transport detail"))
            val reference = body.getValue("reference_at").jsonPrimitive.content
            return QuickInterpretation(body.getValue("request_id").jsonPrimitive.content, body.getValue("message").jsonPrimitive.content,
                "app", "track", 1, reference, reference, "UTC", "now", "Ready to record.")
        }
        override suspend fun reviewRequest(path: String, body: JsonObject?) = buildJsonObject { put("status", "failed"); put("dismissed", false) }
        override suspend fun create() = "unused"
        override suspend fun delete(conversation: String) {}
        override suspend fun stop(conversation: String, run: String) {}
        override suspend fun acknowledge(conversation: String, run: String) {}
        override fun stream(conversation: String, run: String, message: String) = emptyFlow<AssistantEvent>()
    }

    @Test fun failedCommandSurvivesRestartAndRetryRequiresConfirmationWithoutDuplicatingLocalOperation() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TimeboxApplication
        val prefix = "failure-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("$prefix-$name", mode)
        }
        val now = Instant.now()
        var sends = 0
        var snapshot = ActivitySnapshotDto(cursor = 0, serverAt = now.toString(), reportingTimezone = "UTC", current = null,
            taskTypes = listOf(TaskTypeDto(1, "Work"), TaskTypeDto(2, "Meals")), records = emptyList(), offlineReady = true)
        val activityTransport = object : ActivityTransport {
            override suspend fun read() = snapshot
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto { sends++; throw IOException("Offline after save") }
        }
        var durable: String? = null
        val storage = object : ActivityStorage { override fun load() = durable; override fun save(value: String) { durable = value } }
        val activity = ActivityRepository(activityTransport, storage)
        val api = Api()
        var controller: QuickTrackingController? = null
        suspend fun idle() { withTimeout(10_000) { while (controller!!.state.value.busy) delay(20) } }
        try {
            withContext(Dispatchers.Main) {
                controller = QuickTrackingController(context, app.repository, activity) { api }
                controller!!.submit("Track work", "app")
            }
            idle()
            val failed = controller!!.failures.value.single()
            assertEquals("failed", failed.status)
            assertEquals("Track work", failed.command)
            assertFalse(failed.diagnostics.toString().contains("private transport detail"))
            withContext(Dispatchers.Main) {
                controller!!.close()
                controller = QuickTrackingController(context, app.repository, activity) { api }
            }
            assertEquals(failed.requestIds, controller!!.failures.value.single().requestIds)
            api.fail = false
            withContext(Dispatchers.Main) { controller!!.retry(failed.id); controller!!.retry(failed.id) }
            idle()
            assertNotNull(controller!!.preview.value)
            assertEquals(2, api.calls)
            assertEquals(0, sends)
            assertEquals(failed.occurredAt, controller!!.preview.value!!.result.effectiveAt)
            val retriedRequest = controller!!.preview.value!!.result.requestId
            assertNotEquals(failed.requestIds.single(), retriedRequest)
            val changed = ActualBlockDto(8, 2, TaskTypeDto(2, "Meals"), startAt = now.minusSeconds(60).toString(),
                createdAt = now.toString(), updatedAt = now.toString())
            snapshot = snapshot.copy(cursor = 1, current = changed, records = listOf(changed))
            withContext(Dispatchers.Main) { controller!!.confirmRetry() }
            idle()
            assertNull(controller!!.preview.value)
            assertEquals(0, sends)
            assertTrue(controller!!.state.value.message!!.contains("Activity changed"))
            withContext(Dispatchers.Main) { controller!!.retry(failed.id) }
            idle()
            assertNotNull(controller!!.preview.value)
            withContext(Dispatchers.Main) { controller!!.confirmRetry(); controller!!.confirmRetry() }
            idle()
            assertTrue(activity.hasQuickOperation(retriedRequest))
            val savedJournal = durable!!
            assertTrue(savedJournal.contains(retriedRequest))
            val callsBefore = api.calls
            withContext(Dispatchers.Main) { controller!!.retry(failed.id) }
            idle()
            assertEquals(callsBefore, api.calls)
            // Network delivery may retry, but the durable operation identity never changes.
            assertEquals(1, activity.state.value.quickReviews.size)
        } finally {
            withContext(Dispatchers.Main) { controller?.close() }
            context.getSharedPreferences("quick-tracking-failures", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
