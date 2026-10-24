package com.timebox.android.ui.assistant

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class AssistantReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class ReviewTransport(val stopEvent: Boolean = false, val loseReply: Boolean = false, val rejectFirst: Boolean = false) : AssistantTransport {
        val corrections = mutableListOf<JsonObject>()
        var reviewed = false
        override suspend fun create() = "unused"
        override suspend fun delete(conversation: String) {}
        override suspend fun stop(conversation: String, run: String) {}
        override suspend fun acknowledge(conversation: String, run: String) {}
        override fun stream(conversation: String, run: String, message: String) = emptyFlow<AssistantEvent>()
        val record = Json.parseToJsonElement("""{"id":7,"name":"Project work","task_type_id":1,"task_type":{"name":"Work"},"start_at":"2026-10-23T01:00:37Z","end_at":"2026-10-23T02:00:41Z"}""").jsonObject
        override suspend fun reviewRequest(path: String, body: JsonObject?): JsonObject {
            if (path.endsWith("correct-event")) {
                corrections.add(requireNotNull(body))
                if (rejectFirst && corrections.size == 1) throw ReviewRejectedException(409, "Activity overlaps known time")
                reviewed = true
                if (loseReply && corrections.size == 1) throw IOException("Reply lost")
                return buildJsonObject { put("reviewed", true) }
            }
            return buildJsonObject {
                put("timezone", "Asia/Singapore"); put("unchecked_count", if (reviewed) 0 else 1)
                putJsonArray("task_types") {
                    add(buildJsonObject { put("id", 1); put("name", "Work") })
                    add(buildJsonObject { put("id", 2); put("name", "Learning") })
                }
                putJsonArray("items") {
                    if (path.contains("reviewed=$reviewed")) add(buildJsonObject {
                        put("id", 1); put("revision", "a".repeat(64)); put("record", record)
                        put("correction_available", true); put("outcome", "applied")
                        putJsonObject("receipt") {
                            put("record", record); put("action", if (stopEvent) "stop" else "start")
                            put("question", if (stopEvent) "Stop tracking" else "I started working")
                        }
                    })
                }
            }
        }
    }

    private fun show(transport: ReviewTransport, action: () -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val controller = AssistantController(scope) { transport }
            compose.setContent { TimeboxTheme { AssistantReviewScreen(controller, {}, Modifier.fillMaxSize()) } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Correct").fetchSemanticsNodes().isNotEmpty() }
            action()
        } finally { scope.cancel() }
    }

    @Test fun startCorrectsCategoryAndNameWithoutSendingEndAndMarksReviewed() {
        val transport = ReviewTransport()
        show(transport) {
            compose.onNodeWithText("You said: “I started working”").assertIsDisplayed()
            compose.onNodeWithText("Correct").performClick()
            compose.onNodeWithText("Change").performClick()
            compose.onNodeWithText("Search categories").performTextInput("Learn")
            compose.onNodeWithText("Learning").performClick()
            compose.onNodeWithText("Activity name (optional)").performScrollTo().performTextReplacement("Study")
            compose.onNodeWithText("Save correction").performScrollTo().performClick()
            compose.waitUntil(5000) { transport.corrections.isNotEmpty() }
            compose.onNodeWithText("To review · 0").assertExists()
            val body = transport.corrections.single()
            assertEquals(2, body.getValue("task_type_id").jsonPrimitive.int)
            assertEquals("Study", body.getValue("name").jsonPrimitive.content)
            assertEquals("2026-10-23T01:00:37Z", body.getValue("effective_at").jsonPrimitive.content)
            assertFalse(body.containsKey("end_at"))
        }
    }

    @Test fun stopOffersOnlyStoppedTimeAndPreservesOtherFields() {
        val transport = ReviewTransport(stopEvent = true)
        show(transport) {
            compose.onNodeWithText("Stopped tracking").assertIsDisplayed()
            compose.onNodeWithText("Correct").performClick()
            compose.onNodeWithText("Correct stop").assertIsDisplayed()
            compose.onNodeWithContentDescription("Stopped at time").assertExists()
            compose.onNodeWithText("Change").assertDoesNotExist()
            compose.onNodeWithText("Activity name (optional)").assertDoesNotExist()
            compose.onNodeWithText("Save correction").performScrollTo().performClick()
            compose.waitUntil(5000) { transport.corrections.isNotEmpty() }
            assertEquals(setOf("operation_id", "revision", "effective_at"), transport.corrections.single().keys)
            assertEquals("2026-10-23T02:00:41Z", transport.corrections.single().getValue("effective_at").jsonPrimitive.content)
        }
    }

    @Test fun lostReplyRetriesExactOperationAndDoesNotEnableAnotherEdit() {
        val transport = ReviewTransport(loseReply = true)
        show(transport) {
            compose.onNodeWithText("Correct").performClick()
            compose.onNodeWithText("Save correction").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Retry save").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Activity name (optional)").assertIsNotEnabled()
            compose.onNodeWithText("Retry save").performScrollTo().performClick()
            compose.waitUntil(5000) { transport.corrections.size == 2 }
            assertEquals(transport.corrections[0], transport.corrections[1])
        }
    }

    @Test fun rejectedSaveKeepsDraftEditableAndNextEditGetsNewIdentity() {
        val transport = ReviewTransport(rejectFirst = true)
        show(transport) {
            compose.onNodeWithText("Correct").performClick()
            compose.onNodeWithText("Save correction").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Activity overlaps known time").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Activity name (optional)").performScrollTo().performTextReplacement("Retained draft")
            compose.onNodeWithText("Save correction").performScrollTo().performClick()
            compose.waitUntil(5000) { transport.corrections.size == 2 }
            assertNotEquals(transport.corrections[0]["operation_id"], transport.corrections[1]["operation_id"])
            assertEquals("Retained draft", transport.corrections[1].getValue("name").jsonPrimitive.content)
        }
    }
}
