package com.timebox.android.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AssistantTaskCardsTest {
    @get:Rule val compose = createComposeRule()
    private fun fixture(name: String) = Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("assistant-tasks.json").bufferedReader().use { it.readText() }).jsonObject.getValue(name).jsonObject

    @Test fun threeRowsExpandAndNavigateBySavedIdentityWithoutChangingSnapshot() {
        val card = AssistantTaskCard.parse(fixture("card"))
        var opened = 0
        compose.setContent { TimeboxTheme(darkTheme = false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { TaskReadCard(card) { opened = it } }
        } }
        compose.onNodeWithText("Saved Tasks").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
        compose.onNodeWithText("4. Fourth saved Task").assertDoesNotExist()
        compose.onNodeWithText("Show all 4 loaded Tasks").performScrollTo().performClick()
        compose.onNodeWithText("4. Fourth saved Task").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Open current Task Fourth saved Task, ID 4").performScrollTo().performClick()
        assertEquals(4, opened)
        assertEquals(listOf(1, 2, 3, 4), card.rows.map { it.id })
        compose.onNodeWithText("Historical snapshot", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Private original", substring = true).assertCountEquals(0)
    }

    @Test fun descriptionIsPrivateUntilExplicitReviewAndBackReturnsFocusAtLargeText() {
        val proposal = TaskChangeProposal.parse(fixture("proposal"))
        val full = TaskChangeProposal.parse(fixture("full"), fullDescription = true)
        var fetches = 0
        var dismissed = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                TimeboxTheme(darkTheme = true) {
                    Column(Modifier.widthIn(max = 360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        TaskChangeCard(proposal, TaskChangeState(status = "pending", sourceCompleted = true), {}, { fetches++; full }, {}, { dismissed = true }, {})
                    }
                }
            }
        }
        compose.onAllNodesWithText("Private original", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Confirm Task changes").assertDoesNotExist()
        compose.onNodeWithText("Review description").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, fetches)
        compose.onNodeWithText("Confirm Task changes").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Before literal <script>", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("After literal **not markdown**", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("After: Review second appendix").onLast().performScrollTo().assertIsDisplayed()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Return to preview").assertDoesNotExist()
        compose.waitUntil(5000) {
            compose.onNodeWithText("Review description").fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
        }
        compose.onNodeWithText("Review description").assertIsFocused()
        assertFalse(dismissed)
        compose.onAllNodesWithText("Private original", substring = true).assertCountEquals(0)
    }

    @Test fun inlineGateRemainsDisabledAndAllTargetsAreReachable() {
        val data = fixture("proposal")
        val targets = data.getValue("review_targets").jsonArray.map { v ->
            JsonObject(v.jsonObject.mapValues { (key, field) -> if (key in setOf("before", "after") && field != JsonNull) JsonObject(field.jsonObject - "description") else field })
        }
        val proposal = TaskChangeProposal.parse(JsonObject(data + ("review_targets" to JsonArray(targets)) + ("description_review_available" to JsonPrimitive(false))))
        var opened = 0
        compose.setContent { TimeboxTheme(darkTheme = false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TaskChangeCard(proposal, TaskChangeState(status = "pending", sourceCompleted = true), { opened = it }, { error("no fetch") }, {}, {}, {})
            }
        } }
        compose.onNodeWithText("After: Yes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("After: Review second appendix").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Open Task #1").assertCountEquals(2).onLast().performScrollTo().performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("Confirm Task changes").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(TaskConfirmationGate).performScrollTo().assertIsDisplayed()
    }

    @Test fun receiptIsDistinctAndContainsNoDescriptionText() {
        val result = TaskOperationResult.parse(fixture("result"))
        var opened = 0
        compose.setContent { TimeboxTheme(darkTheme = true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { TaskResultCard(result) { opened = it } }
        } }
        compose.onNodeWithText("Task change result").assertIsDisplayed()
        compose.onNodeWithText("Saved 20", substring = true).assertExists()
        compose.onAllNodesWithText("Revised private", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Description: Description changed · separate review").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open Task #6").assertDoesNotExist()
        compose.onNodeWithText("Open Task #1").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun completionReviewShowsEveryMaterialPlanAndDateOnlyIntent() {
        val complete = TaskChangeProposal.parse(fixture("complete"))
        val dated = TaskChangeProposal.parse(fixture("dated"))
        compose.setContent { TimeboxTheme(darkTheme = false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TaskReviewContent(complete, false, {})
                TaskReviewContent(dated, false, {})
            }
        } }
        compose.onNodeWithText("Stops the linked running Actual Block.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 Planned Blocks removed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Final edit", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("10:00 – 11:00", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Record earlier completion.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("12:00", substring = true).assertCountEquals(0)
    }

    @Test fun descriptionDialogKeepsControlsReachableAfterComposerKeyboard() {
        val proposal = TaskChangeProposal.parse(fixture("proposal"))
        val full = TaskChangeProposal.parse(fixture("full"), fullDescription = true)
        compose.setContent { TimeboxTheme(darkTheme = false) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    TaskChangeCard(proposal, TaskChangeState(status = "pending", sourceCompleted = true), {}, { full }, {}, {}, {})
                }
                var draft by remember { mutableStateOf("") }
                OutlinedTextField(draft, { draft = it }, label = { androidx.compose.material3.Text("Composer") })
            }
        } }
        compose.onNodeWithText("Composer").performClick().performTextInput("keep draft")
        compose.onNodeWithText("Review description").performScrollTo().performClick()
        compose.onNodeWithText("Confirm Task changes").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Return to preview").performClick()
        compose.onNodeWithText("keep draft").assertExists()
    }
}
