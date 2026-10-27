package com.timebox.android.ui.assistant

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
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

    private class ReviewTransport(val stopEvent: Boolean = false, val loseReply: Boolean = false, val rejectFirst: Boolean = false, val failLoadOnce: Boolean = false) : AssistantTransport {
        val corrections = mutableListOf<JsonObject>()
        var reviewed = false
        var loads = 0
        var loadGate: CompletableDeferred<Unit>? = null
        override suspend fun create() = "unused"
        override suspend fun delete(conversation: String) {}
        override suspend fun stop(conversation: String, run: String) {}
        override suspend fun acknowledge(conversation: String, run: String) {}
        override fun stream(conversation: String, run: String, message: String) = emptyFlow<AssistantEvent>()
        val record = Json.parseToJsonElement("""{"id":7,"name":"Project work","task_type_id":1,"task_type":{"name":"Work"},"start_at":"2026-10-23T01:00:37Z","end_at":"2026-10-23T02:00:41Z"}""").jsonObject
        override suspend fun reviewRequest(path: String, body: JsonObject?): JsonObject {
            if (path == "reviews/acknowledge") {
                reviewed = true
                return buildJsonObject { put("reviewed", true) }
            }
            if (path.endsWith("correct-event")) {
                corrections.add(requireNotNull(body))
                if (rejectFirst && corrections.size == 1) throw ReviewRejectedException(409, "Activity overlaps known time")
                reviewed = true
                if (loseReply && corrections.size == 1) throw IOException("Reply lost")
                return buildJsonObject { put("reviewed", true) }
            }
            loads++
            loadGate?.await()
            if (failLoadOnce && loads == 1) throw IOException("Load failed")
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

    @Test fun detailsKeepsHeaderPositionAndBackArrow() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val blockers = mutableStateOf(0)
        try {
            val controller = AssistantController(scope) { ReviewTransport() }
            compose.setContent {
                TimeboxTheme {
                    CompositionLocalProvider(
                        com.timebox.android.ui.components.LocalNavigationBlockers provides blockers,
                        com.timebox.android.ui.components.LocalScreenBack provides if (blockers.value == 0) ({}) else null,
                    ) {
                        Column {
                            com.timebox.android.ui.components.TimeboxTopBar("Assistant", "Review")
                            AssistantReviewScreen(controller, {}, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Details").fetchSemanticsNodes().isNotEmpty() }
            val before = compose.onNodeWithText("Review").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithText("Details").performClick()
            compose.onNodeWithText("Tracking details").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").assertExists()
            assertEquals(before, compose.onNodeWithText("Review").fetchSemanticsNode().boundsInRoot)
        } finally { scope.cancel() }
    }

    @Test fun acknowledgingRemovesRowWithoutSuccessMessage() {
        val transport = ReviewTransport()
        show(transport) {
            compose.onNodeWithText("Looks right").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("To review · 0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Marked reviewed").assertDoesNotExist()
            compose.onNodeWithText("Project work").assertDoesNotExist()
            compose.onNodeWithText("Reviewed", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Project work").assertIsDisplayed()
        }
    }

    private fun show(transport: ReviewTransport, action: () -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val controller = AssistantController(scope) { transport }
            compose.setContent { TimeboxTheme { AssistantReviewScreen(controller, {}, Modifier.fillMaxSize()) } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Adjust").fetchSemanticsNodes().isNotEmpty() }
            action()
        } finally { scope.cancel() }
    }

    @Test fun switchingTabsRetainsContentWithoutProgressBarDuringRefresh() {
        val transport = ReviewTransport()
        show(transport) {
            compose.onNodeWithText("Reviewed", useUnmergedTree = true).performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("No activities reviewed yet").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { transport.loadGate = CompletableDeferred() }
            compose.onNodeWithText("To review · 1").performClick()
            compose.onNodeWithText("Project work").assertIsDisplayed()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            // Navigation remains available even while the previous tab is refreshing.
            compose.onNodeWithText("Reviewed", useUnmergedTree = true).performClick()
            compose.onNodeWithText("No activities reviewed yet").assertIsDisplayed()
            compose.onNodeWithText("Project work").assertDoesNotExist()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            compose.runOnIdle { transport.loadGate?.complete(Unit) }
        }
    }

    @Test fun contextualRetryRecoversLoadWithoutPermanentRefresh() {
        val transport = ReviewTransport(failLoadOnce = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val controller = AssistantController(scope) { transport }
            compose.setContent { TimeboxTheme { AssistantReviewScreen(controller, {}, Modifier.fillMaxSize()) } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Refresh").assertDoesNotExist()
            compose.onNodeWithText("Retry").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Adjust").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Retry").assertDoesNotExist()
        } finally { scope.cancel() }
    }

    @Test fun returningReloadsRemoteChangesWithoutLocalCursorChange() {
        val transport = ReviewTransport()
        val visible = mutableStateOf(true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val controller = AssistantController(scope) { transport }
            compose.setContent { TimeboxTheme { if (visible.value) AssistantReviewScreen(controller, {}, Modifier.fillMaxSize()) } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Adjust").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            compose.runOnIdle { transport.reviewed = true; visible.value = true }
            compose.waitUntil(5000) { compose.onAllNodesWithText("To review · 0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("No activities to review").assertIsDisplayed()
            assertTrue(transport.loads >= 2)
        } finally { scope.cancel() }
    }

    @Test fun startCorrectsCategoryAndNameWithoutSendingEndAndMarksReviewed() {
        val transport = ReviewTransport()
        show(transport) {
            compose.onNodeWithText("You said: “I started working”").assertIsDisplayed()
            compose.onNodeWithText("Adjust").performClick()
            compose.onNodeWithText("Change").performClick()
            compose.onNodeWithTag("task-type-search").performTextInput("Learn")
            compose.onNodeWithText("Learning").performClick()
            compose.onNodeWithContentDescription("Block Name").performScrollTo().performTextReplacement("Study")
            compose.onNodeWithText("Save & mark reviewed").performClick()
            compose.waitUntil(5000) { transport.corrections.isNotEmpty() }
            compose.waitUntil(5000) { compose.onAllNodesWithText("To review · 0").fetchSemanticsNodes().isNotEmpty() }
            val body = transport.corrections.single()
            compose.onNodeWithText("Saved and marked reviewed").assertDoesNotExist()
            assertEquals(2, body.getValue("task_type_id").jsonPrimitive.int)
            assertEquals("Study", body.getValue("name").jsonPrimitive.content)
            assertEquals("2026-10-23T01:00:37Z", body.getValue("effective_at").jsonPrimitive.content)
            assertFalse(body.containsKey("end_at"))
        }
    }

    @Test fun pickerCancellationAndDiscardProtectionPreserveDraft() {
        show(ReviewTransport()) {
            compose.onNodeWithText("Adjust").performClick()
            compose.onNodeWithContentDescription("Block Name").performScrollTo().performTextReplacement("My draft")
            compose.onNodeWithText("Change").performScrollTo().performClick()
            compose.onNodeWithTag("task-type-search").performTextInput("Learning")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithContentDescription("Block Name").assertTextEquals("My draft")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Discard correction?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithContentDescription("Block Name").assertTextEquals("My draft")
        }
    }

    @Test fun stopOffersOnlyStoppedTimeAndPreservesOtherFields() {
        val transport = ReviewTransport(stopEvent = true)
        show(transport) {
            compose.onNodeWithText("Stopped at 23 Oct 2026 · 10:00").assertIsDisplayed()
            compose.onNodeWithText("Adjust").performClick()
            compose.onNodeWithText("Adjust stop").assertIsDisplayed()
            compose.onNodeWithContentDescription("Stopped at time").assertExists()
            compose.onNodeWithText("Change").assertDoesNotExist()
            compose.onNodeWithContentDescription("Block Name").assertDoesNotExist()
            compose.onNodeWithText("Save & mark reviewed").performClick()
            compose.waitUntil(5000) { transport.corrections.isNotEmpty() }
            assertEquals(setOf("operation_id", "revision", "effective_at"), transport.corrections.single().keys)
            assertEquals("2026-10-23T02:00:41Z", transport.corrections.single().getValue("effective_at").jsonPrimitive.content)
        }
    }

    @Test fun lostReplyRetriesExactOperationAndDoesNotEnableAnotherEdit() {
        val transport = ReviewTransport(loseReply = true)
        show(transport) {
            compose.onNodeWithText("Adjust").performClick()
            compose.onNodeWithText("Save & mark reviewed").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Retry save").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Block Name").assertIsNotEnabled()
            compose.onNodeWithText("Retry save").performClick()
            compose.waitUntil(5000) { transport.corrections.size == 2 }
            assertEquals(transport.corrections[0], transport.corrections[1])
        }
    }

    @Test fun rejectedSaveKeepsDraftEditableAndNextEditGetsNewIdentity() {
        val transport = ReviewTransport(rejectFirst = true)
        show(transport) {
            compose.onNodeWithText("Adjust").performClick()
            compose.onNodeWithText("Save & mark reviewed").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Activity overlaps known time").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Block Name").performScrollTo().performTextReplacement("Retained draft")
            compose.onNodeWithText("Save & mark reviewed").performClick()
            compose.waitUntil(5000) { transport.corrections.size == 2 }
            assertNotEquals(transport.corrections[0]["operation_id"], transport.corrections[1]["operation_id"])
            assertEquals("Retained draft", transport.corrections[1].getValue("name").jsonPrimitive.content)
        }
    }
}
