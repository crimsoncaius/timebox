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

    @Test fun confirmationNeedsCompletedSourceAndDescriptionConfirmationStaysInExplicitReview() {
        val full = TaskChangeProposal.parse(fixture("full"), fullDescription = true)
        val proposal = TaskChangeProposal.parse(fixture("proposal")).copy(expiresAt = java.time.Instant.now().plusSeconds(900))
        var confirmations = 0
        compose.setContent { TimeboxTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TaskChangeCard(proposal, TaskChangeState("pending", sourceCompleted = true), {}, { full }, {}, {}, {}, onConfirm = { confirmations++ })
            }
        } }
        compose.onNodeWithText("Confirm all changes").assertDoesNotExist()
        compose.onNodeWithText("Review description").performScrollTo().performClick()
        compose.onNodeWithText("Confirm all changes").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }
        compose.onNodeWithText("Back to preview").assertDoesNotExist()
    }
    @Test fun offlineConfirmationIsDisabledWithAccessibleReason() {
        val proposal = TaskChangeProposal.parse(fixture("complete")).copy(expiresAt = java.time.Instant.now().plusSeconds(900))
        compose.setContent { TimeboxTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TaskChangeCard(proposal, TaskChangeState("pending", sourceCompleted = true), {}, { proposal }, {}, {}, {}, onConfirm = {}, confirmationBlocked = "Connect before confirming Task changes.")
            }
        } }
        compose.onNodeWithText("Confirm completion").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Connect before confirming Task changes.").performScrollTo().assertIsDisplayed()
    }

    @Test fun threeRowsExpandAndNavigateBySavedIdentityWithoutChangingSnapshot() {
        val card = AssistantTaskCard.parse(fixture("card"))
        var opened = 0
        compose.setContent { TimeboxTheme(darkTheme = false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { TaskReadCard(card) { opened = it } }
        } }
        compose.onNodeWithText("Saved Tasks").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
        compose.onNodeWithText("Fourth saved Task").assertDoesNotExist()
        compose.onNodeWithText("Show all 4 loaded Tasks").performScrollTo().performClick()
        compose.onNodeWithText("Fourth saved Task").performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("Confirm all changes").assertDoesNotExist()
        compose.onNodeWithText("Review description").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, fetches)
        compose.onNodeWithText("Confirm all changes").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Before literal <script>", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("After literal **not markdown**", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Review second appendix").onLast().performScrollTo().assertIsDisplayed()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Back to preview").assertDoesNotExist()
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
        compose.onNodeWithText("No → Yes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review second appendix").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review details").performScrollTo().performClick()
        compose.onAllNodesWithText("Open Task #1").assertCountEquals(2).onLast().performScrollTo().performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("Confirm all changes").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(TaskConfirmationGate).performScrollTo().assertIsDisplayed()
    }

    @Test fun receiptIsDistinctAndContainsNoDescriptionText() {
        val result = TaskOperationResult.parse(fixture("result"))
        var opened = 0
        compose.setContent { TimeboxTheme(darkTheme = true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { TaskResultCard(result, onOpenTask = { opened = it }) }
        } }
        compose.onNodeWithText("2 changes saved together").assertIsDisplayed()
        compose.onNodeWithText("Saved 4 Oct 2026", substring = true).assertExists()
        compose.onAllNodesWithText("Revised private", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Description updated").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Open current Task Review second appendix, ID 6").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open current Task Task #1, ID 1").performScrollTo().performClick()
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

    @Test fun failedDescriptionLoadCannotConfirmAndRetryUsesFullReview() {
        val proposal = TaskChangeProposal.parse(fixture("proposal")).copy(expiresAt = java.time.Instant.now().plusSeconds(900))
        val full = TaskChangeProposal.parse(fixture("full"), fullDescription = true)
        var attempts = 0
        var confirmed = 0
        compose.setContent { TimeboxTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TaskChangeCard(proposal, TaskChangeState("pending", true), {}, { if (++attempts == 1) error("offline"); full }, {}, {}, {}, onConfirm = { confirmed++ })
            }
        } }
        compose.onNodeWithText("Review description").performScrollTo().performClick()
        compose.onNodeWithText("Could not load", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm all changes").assertDoesNotExist()
        compose.onNodeWithText("Retry description review").performScrollTo().performClick()
        compose.onNodeWithText("Confirm all changes").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, attempts); assertEquals(1, confirmed) }
    }

    @Test fun savedResultReplacesConfirmationAndUsesReceiptIdentityForSubtaskParent() {
        val proposal = TaskChangeProposal.parse(fixture("proposal"))
        val result = TaskOperationResult.parse(fixture("result"))
        var opened = 0
        compose.setContent { TimeboxTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TaskChangeCard(proposal, TaskChangeState("applied", true, result), { opened = it }, { error("no fetch") }, {}, {}, {}, onConfirm = { error("cannot reconfirm") })
            }
        } }
        compose.onNodeWithText(proposal.targets.first().title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm all changes").assertDoesNotExist()
        compose.onNodeWithText("Review description").assertDoesNotExist()
        compose.onNodeWithText("Subtask created").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Open current Task Review second appendix, ID 1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithText("View reviewed changes").performScrollTo().performClick()
        compose.onNodeWithText("Historical review", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun snapshotDetailsHandleUnavailableChildrenAndKeepRowsIndependent() {
        val card = AssistantTaskCard.parse(fixture("card"))
        val first = card.rows.first().let { it.copy(values = JsonObject(it.values + mapOf(
            "subtasks" to buildJsonArray { add(buildJsonObject { put("id", 91); put("availability", "unavailable") }) },
            "deadline" to buildJsonObject { put("kind", "instant"); put("instant", "2026-10-09T03:15:00Z") }
        ))) }
        compose.setContent { TimeboxTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { TaskReadCard(card.copy(rows = listOf(first, card.rows[1]), zone = java.time.ZoneId.of("Asia/Singapore"))) {} }
        } }
        compose.onNodeWithText("Due 9 Oct 2026, 11:15 · Asia/Singapore").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Subtask #91", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("Saved details").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Subtask #91 · Unavailable at read time").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Hide details").assertCountEquals(1)
        compose.onAllNodesWithText("Saved details", substring = false).assertCountEquals(1)
    }

    @Test fun confirmationWaitsForCompletedSourceAndUndoUsesExistingCallback() {
        val proposal = TaskChangeProposal.parse(fixture("complete")).copy(expiresAt = java.time.Instant.now().plusSeconds(900))
        var state by mutableStateOf(TaskChangeState("pending", sourceCompleted = false))
        var confirmed = 0
        var undone = 0
        val result = TaskOperationResult.parse(fixture("result")).copy(operationId = proposal.operationId, proposalId = proposal.id,
            undo = buildJsonObject { put("status", "available") })
        compose.setContent { TimeboxTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TaskChangeCard(proposal, state, {}, { proposal }, {}, {}, {}, onConfirm = { confirmed++ }, onUndo = { undone++ })
            }
        } }
        compose.onNodeWithText("Confirm completion").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(sourceCompleted = true, busy = true) }
        compose.onNodeWithText("Confirm completion").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = false) }
        compose.onNodeWithText("Confirm completion").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, confirmed); state = state.copy(status = "applied", result = result) }
        compose.onNodeWithText("Confirm completion").assertDoesNotExist()
        compose.onNodeWithText("Undo completion").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, undone) }
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
        compose.onNodeWithText("Confirm all changes").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Back to preview").performClick()
        compose.onNodeWithText("keep draft").assertExists()
    }
}
