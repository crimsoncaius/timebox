package com.timebox.android.ui.assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import android.view.WindowManager
import org.junit.Before
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test

class AssistantScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun useProductionKeyboardMode() {
        // The generic test host defaults to pan, unlike MainActivity's manifest.
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge(); it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
    }

    @Test fun historicalResponseRendersTextAndSupportedCardsWithoutRetiredPlan() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val events = Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets
            .open("assistant-historical-response.json").bufferedReader().use { it.readText() }).jsonArray
        val transport = object : AssistantTransport by Fake() {
            override fun stream(conversation: String, run: String, message: String) = flow {
                events.forEachIndexed { index, value ->
                    val event = value.jsonObject
                    emit(AssistantEvent(event.getValue("kind").jsonPrimitive.content,
                        JsonObject(event.getValue("data").jsonObject + mapOf("run_id" to JsonPrimitive(run), "sequence" to JsonPrimitive(index + 1)))))
                }
            }
        }
        try {
            val controller = AssistantController(scope) { transport }
            compose.setContent { TimeboxTheme { AssistantScreen(controller) } }
            compose.runOnIdle { controller.send("Read saved response") }
            compose.waitUntil(5000) { controller.state.value.exchanges.lastOrNull()?.status == "Complete" }
            compose.onNodeWithText("Your saved response is still readable.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Retired day-plan block").assertDoesNotExist()
            compose.onNodeWithText("Current activity block").performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                check(controller.state.value.exchanges.single().cards.size == 2)
                check(controller.state.value.exchanges.single().taskCards.size == 1)
            }
        } finally { scope.cancel() }
    }

    @Test fun sentQuestionStaysAtSameHorizontalPositionBeforeReply() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val releaseCard = CompletableDeferred<Unit>()
        val transport = object : AssistantTransport by Fake(pauseAfterCard = true) {
            override fun stream(conversation: String, run: String, message: String) = flow {
                releaseCard.await()
                Fake(pauseAfterCard = true).stream(conversation, run, message).collect { emit(it) }
            }
        }
        try {
            val controller = AssistantController(scope) { transport }
            compose.setContent { TimeboxTheme(darkTheme = true) { Box(Modifier.fillMaxSize().imePadding()) { AssistantScreen(controller) } } }
            compose.onNode(hasSetTextAction()).performTextInput("Hi")
            compose.onNodeWithContentDescription("Send").performClick()
            compose.onNodeWithText("Thinking…").assertIsDisplayed()
            val before = compose.onNodeWithContentDescription("You: Hi").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { releaseCard.complete(Unit) }
            compose.waitUntil(5000) { controller.state.value.exchanges.lastOrNull()?.cards?.isNotEmpty() == true }
            compose.onNodeWithContentDescription("You: Hi").performScrollTo()
            val after = compose.onNodeWithContentDescription("You: Hi").fetchSemanticsNode().boundsInRoot
            check(kotlin.math.abs(before.right - after.right) < 1f) {
                "User message moved horizontally before reply: before=$before after=$after"
            }
        } finally { scope.cancel() }
    }

    private class Fake(private val pauseAfterCard: Boolean = false) : AssistantTransport {
        override val supportsActivityCards = true
        override suspend fun create() = "fixture"
        override suspend fun delete(conversation: String) {}
        override suspend fun stop(conversation: String, run: String) {}
        override suspend fun acknowledge(conversation: String, run: String) {}
        override fun stream(conversation: String, run: String, message: String) = flow {
            emit(AssistantEvent("plan_card", buildJsonObject {
                put("run_id", run); put("sequence", 1); put("schema_version", 2)
                put("lane", "planned"); put("recurring_not_materialized", false)
                put("snapshot_id", "fixture"); put("date", "2026-09-21"); put("reporting_timezone", "Asia/Singapore"); put("read_at", "2026-09-21T01:41:00Z")
                putJsonArray("blocks") {
                    repeat(4) { index -> add(buildJsonObject {
                        put("start_at", java.time.Instant.parse("2026-09-21T01:00:00Z").plusSeconds(index * 3600L).toString())
                        put("end_at", java.time.Instant.parse("2026-09-21T02:00:00Z").plusSeconds(index * 3600L).toString())
                        put("lane", "planned"); put("running", false); put("duration_minutes", 60); put("minutes_in_date", 60)
                        put("name", "Review block ${index + 1}"); put("task_type", "Work / Product"); put("task_id", JsonNull); put("task_title", JsonNull)
                    }) }
                }
            }))
            if (pauseAfterCard) awaitCancellation()
            emit(AssistantEvent("completed", buildJsonObject { put("run_id", run); put("sequence", 2) }))
        }
    }

    @Test fun blankStartThenCardOnlyExpandsAndResetClears() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val controller = AssistantController(scope) { Fake() }
            compose.setContent { TimeboxTheme(darkTheme = isSystemInDarkTheme()) { Box(Modifier.fillMaxSize().imePadding()) { AssistantScreen(controller) } } }
            compose.onNodeWithText("What’s on your mind?").assertIsDisplayed()
            compose.onNodeWithText("Make room\nfor your day.").assertDoesNotExist()
            compose.onNodeWithText("Show today’s plan").assertDoesNotExist()
            compose.onNode(hasSetTextAction()).performTextInput("Show today’s plan")
            compose.onNode(hasSetTextAction()).assertTextContains("Show today’s plan")
            compose.onNodeWithContentDescription("Send").performClick()
            compose.waitUntil(5000) { controller.state.value.exchanges.lastOrNull()?.status == "Complete" }
            compose.onNodeWithText("Show all 4 blocks").performScrollTo().performClick()
            compose.onNodeWithText("Review block 4", substring = true).performScrollTo().assertIsDisplayed()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            File(directory, "assistant-native.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("No response text.").assertDoesNotExist()
            compose.onNodeWithText("Think through my morning").assertDoesNotExist()
            compose.runOnIdle { check(controller.state.value.exchanges.size == 1) }
            compose.onNode(hasText("New conversation") or hasContentDescription("New conversation")).performClick()
            compose.onNodeWithText("What’s on your mind?").assertIsDisplayed()
            compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        } finally { scope.cancel() }
    }

    @Test fun multilineDraftSurvivesStopAndResetClearsIt() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val controller = AssistantController(scope) { Fake(pauseAfterCard = true) }
            compose.setContent { TimeboxTheme(darkTheme = true) { Box(Modifier.fillMaxSize().imePadding()) { AssistantScreen(controller) } } }
            compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
            compose.onNode(hasSetTextAction()).performTextInput("Show my plan\nand find a gap")
            compose.onNodeWithContentDescription("Send").performClick()
            compose.waitUntil(5000) { controller.state.value.exchanges.lastOrNull()?.cards?.isNotEmpty() == true }
            compose.onNode(hasSetTextAction()).performTextInput("Keep this\nfor later")
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed().performClick()
            compose.waitUntil(5000) { !controller.state.value.busy }
            compose.onNode(hasSetTextAction()).assertTextContains("Keep this\nfor later")
            compose.onNodeWithContentDescription("Send").assertIsEnabled()
            compose.onNodeWithText("Retry response").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("New conversation").performClick()
            compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        } finally { scope.cancel() }
    }
}
