package com.timebox.android.ui.assistant

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantControllerTest {
    private class Fake : AssistantTransport {
        override val supportsPlanCards = true
        override var supportsTrackingProposals = true
        val events = MutableSharedFlow<AssistantEvent>()
        var run = ""
        var sequence = 0
        var creates = 0
        var stops = 0
        var deletes = 0
        var acks = 0
        var ackFailures = 0
        var streams = 0
        override suspend fun create() = "conversation-${++creates}"
        override suspend fun delete(conversation: String) { deletes++ }
        override suspend fun stop(conversation: String, run: String) { stops++ }
        override suspend fun acknowledge(conversation: String, run: String) {
            acks++
            if (ackFailures > 0) { ackFailures--; throw java.io.IOException("Connection lost") }
        }
        override fun stream(conversation: String, run: String, message: String): Flow<AssistantEvent> {
            this.run = run; sequence = 0
            streams++
            return events.takeWhile { it.kind != "eof" }
        }
        suspend fun emit(kind: String, text: String = "") {
            events.emit(AssistantEvent(kind, buildJsonObject {
                put("run_id", run); put("sequence", ++sequence); put("text", text); put("message", text)
            }))
        }
    }

    private suspend fun Fake.card() {
        events.emit(AssistantEvent("plan_card", buildJsonObject {
            put("run_id", run); put("sequence", ++sequence); put("schema_version", 1)
            put("snapshot_id", "snapshot-1"); put("date", "2026-09-21")
            put("reporting_timezone", "Asia/Singapore"); put("read_at", "2026-09-21T01:41:00Z")
            putJsonArray("planned_blocks") {}
        }))
    }

    private suspend fun Fake.proposal(id: String = "p1") {
        events.emit(AssistantEvent("tracking_proposal", buildJsonObject {
            put("run_id", run); put("sequence", ++sequence); put("schema_version", 1); put("proposal_id", id); put("action", "track")
            putJsonArray("task_types") { addJsonObject { put("id", 2); put("path", "Exercise/Gym") }; addJsonObject { put("id", 3); put("path", "Exercise/Running") } }
            put("block_name", JsonNull); put("at", JsonNull); put("reporting_timezone", "UTC")
            put("proposed_at", "2026-09-25T12:50:00Z"); put("expires_at", "2026-09-25T13:05:00Z")
        }))
    }

    @Test fun `tracking proposal precedes text and its card state is retained`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("switch to exercise"); runCurrent()
        api.proposal(); api.emit("text_delta", "Which kind?"); api.emit("completed"); api.emit("eof"); runCurrent()
        val exchange = controller.state.value.exchanges.single()
        assertEquals("Complete", exchange.status)
        assertEquals(2, exchange.proposal!!.taskTypes.size)
        assertEquals(ProposalStatus.Pending, controller.state.value.proposals.getValue("p1").status)
        controller.chooseProposal("p1", 3)
        assertEquals(3, controller.state.value.proposals.getValue("p1").chosen)
        val record = com.timebox.android.ui.day.RecordRef("op-1", java.time.Instant.parse("2026-09-25T12:50:00Z"))
        controller.proposalApplied("p1", record, "op-1")
        controller.chooseProposal("p1", 2)
        assertEquals("an applied card no longer changes its choice", 3, controller.state.value.proposals.getValue("p1").chosen)
        controller.operationUndone("op-1")
        assertEquals(ProposalStatus.Pending, controller.state.value.proposals.getValue("p1").status)
        controller.dismissProposal("p1")
        assertEquals(ProposalStatus.Dismissed, controller.state.value.proposals.getValue("p1").status)
    }

    @Test fun `unnegotiated or late proposal interrupts the response`() = runTest {
        val api = Fake().apply { supportsTrackingProposals = false }
        val controller = AssistantController(backgroundScope) { api }
        controller.send("eating"); runCurrent()
        api.proposal(); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.single().status)
        api.supportsTrackingProposals = true
        controller.retry(); runCurrent()
        api.emit("text_delta", "Sure"); api.proposal("p2"); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.last().status)
    }

    @Test fun `card only completes and acknowledgement is atomic`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Show plan"); runCurrent()
        api.card(); runCurrent()
        assertNotNull(controller.state.value.exchanges.single().plan)
        assertEquals("", controller.state.value.exchanges.single().status)
        api.emit("completed"); api.emit("eof"); runCurrent()
        assertEquals("Complete", controller.state.value.exchanges.single().status)
        controller.send("Earlier plan?"); runCurrent()
        assertEquals(1, api.acks)
    }

    @Test fun `second card interrupts and later completion cannot repair it`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Show plan"); runCurrent()
        api.card(); api.card(); runCurrent()
        api.emit("completed"); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.single().status)
        assertNotNull(controller.state.value.exchanges.single().plan)
        controller.send("Next"); runCurrent()
        assertEquals(0, api.acks)
    }

    @Test fun `card after text is rejected and stop retains validated card`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Show plan"); runCurrent()
        api.emit("text_delta", "answer"); api.card(); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.single().status)
        assertNull(controller.state.value.exchanges.single().plan)
        controller.retry(); runCurrent(); api.card(); runCurrent()
        controller.stop(); runCurrent()
        assertNotNull(controller.state.value.exchanges.last().plan)
        assertEquals("Stopped", controller.state.value.exchanges.last().status)
    }

    @Test fun `unknown event after completion prevents acknowledgement`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Show plan"); runCurrent(); api.card(); api.emit("completed"); api.emit("unknown"); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.single().status)
        controller.retry(); runCurrent(); assertEquals(0, api.acks)
    }

    @Test fun `stop retains partial text and retry is explicit`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Today?"); runCurrent()
        api.emit("text_delta", "Partial"); runCurrent()
        controller.stop(); runCurrent()
        assertEquals("Partial", controller.state.value.exchanges.last().answer)
        assertEquals("Stopped", controller.state.value.exchanges.last().status)
        assertEquals(1, api.stops)
        assertEquals(0, api.acks)
        controller.retry(); runCurrent()
        assertEquals(2, controller.state.value.exchanges.size)
        assertTrue(controller.state.value.busy)
    }

    @Test fun `reset cancels old run and next conversation is fresh`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("First"); runCurrent()
        controller.newConversation(); runCurrent()
        api.emit("text_delta", "Late old output"); runCurrent()
        assertTrue(controller.state.value.exchanges.isEmpty())
        assertEquals(1, api.deletes)
        controller.send("Second"); runCurrent()
        assertEquals(2, api.creates)
        assertEquals("Second", controller.state.value.exchanges.single().question)
    }

    @Test fun `EOF is interrupted and cannot acknowledge partial context`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Today?"); runCurrent()
        api.emit("text_delta", "Partial"); runCurrent()
        api.emit("eof"); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.single().status)
        assertFalse(controller.state.value.busy)
        assertEquals(0, api.acks)
        assertEquals(1, controller.state.value.exchanges.size)
    }

    @Test fun `completed response is acknowledged and duplicate send is blocked`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Today?"); controller.send("Duplicate"); runCurrent()
        api.emit("text_delta", "Answer"); runCurrent()
        api.emit("completed"); runCurrent()
        api.emit("eof"); runCurrent()
        assertEquals("Complete", controller.state.value.exchanges.single().status)
        assertEquals(0, api.acks)
        assertFalse(controller.state.value.busy)
        controller.send("Follow up"); runCurrent()
        assertEquals(1, api.acks)
    }

    @Test fun `lost acknowledgement does not replay generation or discard completed answer`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("First"); runCurrent()
        api.emit("text_delta", "Complete answer"); runCurrent()
        api.emit("completed"); runCurrent()
        api.emit("eof"); runCurrent()
        api.ackFailures = 1
        controller.send("Follow up"); runCurrent()
        assertEquals("Complete", controller.state.value.exchanges.first().status)
        assertEquals(1, api.streams)
        assertEquals("Interrupted", controller.state.value.exchanges.last().status)
        controller.retry(); runCurrent()
        assertEquals(2, api.acks)
        assertEquals(2, api.streams)
    }

    @Test fun `save failure retains visible answer and never acknowledges it`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("First"); runCurrent()
        api.emit("text_delta", "Visible answer"); runCurrent()
        api.emit("failed", "The response could not be saved. Please retry."); runCurrent()
        val exchange = controller.state.value.exchanges.single()
        assertEquals("Visible answer", exchange.answer)
        assertEquals("Interrupted", exchange.status)
        assertEquals("The response could not be saved. Please retry.", exchange.error)
        controller.send("Next"); runCurrent()
        assertEquals(0, api.acks)
    }

    @Test fun `new process controller starts fresh without restoring previous conversation`() = runTest {
        val api = Fake()
        val first = AssistantController(backgroundScope) { api }
        first.send("First"); runCurrent()
        api.emit("text_delta", "Answer"); api.emit("completed"); api.emit("eof"); runCurrent()
        val restarted = AssistantController(backgroundScope) { api }
        assertTrue(restarted.state.value.exchanges.isEmpty())
        restarted.send("Fresh"); runCurrent()
        assertEquals(2, api.creates)
        assertEquals(0, api.acks)
    }
}
