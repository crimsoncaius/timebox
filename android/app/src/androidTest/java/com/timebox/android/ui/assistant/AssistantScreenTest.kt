package com.timebox.android.ui.assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
    @get:Rule val compose = createComposeRule()

    private class Fake(private val pauseAfterCard: Boolean = false) : AssistantTransport {
        override val supportsPlanCards = true
        override suspend fun create() = "fixture"
        override suspend fun delete(conversation: String) {}
        override suspend fun stop(conversation: String, run: String) {}
        override suspend fun acknowledge(conversation: String, run: String) {}
        override fun stream(conversation: String, run: String, message: String) = flow {
            emit(AssistantEvent("plan_card", buildJsonObject {
                put("run_id", run); put("sequence", 1); put("schema_version", 1)
                put("snapshot_id", "fixture"); put("date", "2026-09-21"); put("reporting_timezone", "Asia/Singapore"); put("read_at", "2026-09-21T01:41:00Z")
                putJsonArray("planned_blocks") {
                    repeat(4) { index -> add(buildJsonObject {
                        put("start_minute", 540 + index * 60); put("end_minute", 600 + index * 60)
                        put("name", "Review block ${index + 1}"); put("task_type", "Work / Product"); put("task_id", JsonNull); put("task_title", JsonNull)
                    }) }
                }
            }))
            if (pauseAfterCard) awaitCancellation()
            emit(AssistantEvent("completed", buildJsonObject { put("run_id", run); put("sequence", 2) }))
        }
    }

    @Test fun starterEditsThenCardOnlyExpandsAndResetClears() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val controller = AssistantController(scope) { Fake() }
            compose.setContent { TimeboxTheme(darkTheme = isSystemInDarkTheme()) { AssistantScreen(controller) } }
            compose.onNodeWithText("Show today’s plan").performScrollTo().assertIsDisplayed().performClick()
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
            compose.onNodeWithText("Think through my morning").performScrollTo().performClick()
            compose.onNode(hasSetTextAction()).assertTextContains("Help me think through my morning")
            compose.runOnIdle { check(controller.state.value.exchanges.size == 1) }
            compose.onNode(hasText("New conversation") or hasContentDescription("New conversation")).performClick()
            compose.onNodeWithText("Make room\nfor your day.").performScrollTo().assertIsDisplayed()
        } finally { scope.cancel() }
    }

    @Test fun multilineDraftSurvivesStopAndResetClearsIt() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val controller = AssistantController(scope) { Fake(pauseAfterCard = true) }
            compose.setContent { TimeboxTheme(darkTheme = true) { AssistantScreen(controller) } }
            compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
            compose.onNode(hasSetTextAction()).performTextInput("Show my plan\nand find a gap")
            compose.onNodeWithContentDescription("Send").performClick()
            compose.waitUntil(5000) { controller.state.value.exchanges.lastOrNull()?.plan != null }
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
