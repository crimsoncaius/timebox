package com.timebox.android.ui.assistant

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import com.timebox.android.ui.day.TrackingHandoff
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class ProposalConfirmationTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val release = CompletableDeferred<Unit>()
    @After fun cleanup() { release.complete(Unit); scope.cancel() }

    @Test fun switchKeepsProgressUntilAppliedInsteadOfFlashingAlreadyTracking() = confirmSwitch(false)

    @Test fun failedLocalSaveRestoresConfirmationAndCanBeRetried() = confirmSwitch(true)

    private fun confirmSwitch(failFirstSave: Boolean) {
        val now = java.time.Instant.parse("2026-09-28T05:00:00Z")
        val work = TaskTypeDto(1, "Work")
        val meals = TaskTypeDto(2, "Meals")
        val current = ActualBlockDto(1, 1, work, startAt = now.minusSeconds(3600).toString(), createdAt = "", updatedAt = "")
        var snapshot = ActivitySnapshotDto(cursor = 1, serverAt = now.toString(), reportingTimezone = "UTC",
            offlineReady = true, current = current, records = listOf(current), taskTypes = listOf(work, meals), switchHistoryReady = true)
        val entered = CompletableDeferred<Unit>()
        var failSave = false
        val repository = ActivityRepository(object : ActivityTransport {
            override suspend fun read() = snapshot
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                entered.complete(Unit)
                release.await()
                val next = current.copy(id = 2, taskTypeId = meals.id, taskType = meals, startAt = command.effective.at!!)
                snapshot = snapshot.copy(cursor = 2, current = next, records = listOf(current.copy(endAt = next.startAt), next),
                    provenance = mapOf("2" to command.operationId),
                    acknowledgement = ActivityAcknowledgementDto(command.operationId, ActivityOutcome.Applied))
                return snapshot
            }
        }, object : ActivityStorage {
            override fun load(): String? = null
            override fun save(value: String) { check(!failSave) { "Disk full" } }
        }, wallTime = { now.toEpochMilli() }, monotonicTime = { 0L })
        runBlocking { repository.refresh() }
        val controller = AssistantController(scope) { object : AssistantTransport {
            override val supportsTrackingProposals = true
            override suspend fun create() = "test"
            override suspend fun delete(conversation: String) {}
            override suspend fun stop(conversation: String, run: String) {}
            override suspend fun acknowledge(conversation: String, run: String) {}
            override fun stream(conversation: String, run: String, message: String) = flow {
                emit(AssistantEvent("tracking_proposal", buildJsonObject {
                    put("run_id", run); put("sequence", 1); put("schema_version", 1); put("proposal_id", "p"); put("action", "track")
                    putJsonArray("task_types") { addJsonObject { put("id", 2); put("path", "Meals") } }
                    put("block_name", JsonNull); put("at", JsonNull)
                    put("proposed_at", now.toString()); put("expires_at", now.plusSeconds(900).toString())
                }))
                emit(AssistantEvent("completed", buildJsonObject { put("run_id", run); put("sequence", 2) }))
            }
        } }
        compose.runOnIdle { controller.send("Switch to Meals") }
        compose.waitUntil { controller.state.value.proposals.containsKey("p") }
        val proposal = controller.state.value.exchanges.single().proposal!!
        compose.setContent {
            val compositionScope = rememberCoroutineScope()
            val tracking = remember { ProposalTracking(controller, repository, TrackingHandoff(), {}, compositionScope) }
            TimeboxTheme { tracking.Card(proposal) }
        }
        if (failFirstSave) {
            failSave = true
            compose.onNodeWithText("Switch").performClick()
            compose.waitUntil { controller.state.value.proposals.getValue("p").applying == null }
            compose.onNodeWithText("Switching…").assertDoesNotExist()
            compose.onNodeWithText("Activity storage failed.", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Switch").assertIsEnabled()
            failSave = false
        }
        compose.onNodeWithText("Switch").performClick()
        compose.waitUntil { entered.isCompleted }
        compose.onNodeWithText("You're already tracking Meals.").assertDoesNotExist()
        compose.onNodeWithText("CAN'T SWITCH").assertDoesNotExist()
        compose.onNodeWithText("Switching…").assertIsDisplayed()
        compose.onNodeWithText("Switch").assertDoesNotExist()
        release.complete(Unit)
        compose.waitUntil { controller.state.value.proposals.getValue("p").status == ProposalStatus.Applied }
        compose.onNodeWithText("TRACKED").assertIsDisplayed()
        compose.onNodeWithText("View in Day").assertIsDisplayed()
        compose.onNodeWithText("You're already tracking Meals.").assertDoesNotExist()
    }
}
