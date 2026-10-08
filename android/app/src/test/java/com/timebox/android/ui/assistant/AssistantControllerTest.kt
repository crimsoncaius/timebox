package com.timebox.android.ui.assistant

import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import com.timebox.android.ui.day.TrackingHandoff
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantControllerTest {
    @Test fun `retired payloads preserve text and all three supported cards in a completed response`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Read saved response"); runCurrent()
        val events = Json.parseToJsonElement(javaClass.getResource("/assistant-historical-response.json")!!.readText()).jsonArray
        for (value in events) {
            val event = value.jsonObject
            api.events.emit(AssistantEvent(event.getValue("kind").jsonPrimitive.content,
                JsonObject(event.getValue("data").jsonObject + mapOf("run_id" to JsonPrimitive(api.run), "sequence" to JsonPrimitive(++api.sequence)))))
        }
        api.emit("eof"); runCurrent()
        val exchange = controller.state.value.exchanges.single()
        assertEquals("Complete", exchange.status)
        assertNull(exchange.error)
        assertEquals("Your saved response is still readable.", exchange.answer)
        assertEquals(listOf("blocks", "types"), exchange.cards.map { it.id })
        assertEquals(1, exchange.taskCards.size)
        assertEquals(listOf("blocks", "types", exchange.taskCards.single().id), exchange.cardOrder)
        controller.send("Follow up"); runCurrent()
        assertEquals(1, api.acks)
    }

    @Test fun `retired payload needs no capability and leaves a text only conversation readable`() = runTest {
        val api = Fake().apply { supportsActivityCards = false }
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Read old plan"); runCurrent()
        api.events.emit(AssistantEvent("plan_card", buildJsonObject {
            put("run_id", api.run); put("sequence", ++api.sequence); put("schema_version", 1)
        }))
        api.emit("text_delta", "Historical answer"); api.emit("completed"); api.emit("eof"); runCurrent()
        val exchange = controller.state.value.exchanges.single()
        assertEquals("Complete", exchange.status)
        assertEquals("Historical answer", exchange.answer)
        assertTrue(exchange.cards.isEmpty())
        assertTrue(exchange.cardOrder.isEmpty())
        controller.send("New card without capability"); runCurrent(); api.card(); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.last().status)
    }

    @Test fun `retired card only response completes while a truly empty response is still rejected`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Old card only"); runCurrent()
        api.events.emit(AssistantEvent("plan_card", buildJsonObject {
            put("run_id", api.run); put("sequence", ++api.sequence); put("schema_version", 1)
        }))
        api.emit("completed"); api.emit("eof"); runCurrent()
        assertEquals("Complete", controller.state.value.exchanges.single().status)
        assertTrue(controller.state.value.exchanges.single().cards.isEmpty())
        controller.send("Empty response"); runCurrent()
        api.emit("completed"); runCurrent()
        assertEquals(1, api.acks)
        assertEquals("Interrupted", controller.state.value.exchanges.last().status)
    }

    @Test fun `confirmed switch retains its valid view while the repository publishes the new activity`() = runTest {
        val now = java.time.Instant.parse("2026-09-25T12:50:00Z")
        val zone = java.time.ZoneId.of("UTC")
        val work = TaskTypeDto(1, "Work")
        val running = TaskTypeDto(3, "Exercise/Running")
        val current = ActualBlockDto(1, 1, work,
            startAt = now.minusSeconds(3600).toString(), createdAt = "", updatedAt = "")
        var snapshot = ActivitySnapshotDto(cursor = 1, serverAt = now.toString(),
            reportingTimezone = "UTC", offlineReady = true, current = current, records = listOf(current),
            taskTypes = listOf(work, running), switchHistoryReady = true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var commands = 0
        val repository = ActivityRepository(object : ActivityTransport {
            override suspend fun read() = snapshot
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                commands++
                entered.complete(Unit)
                release.await()
                val next = current.copy(id = 2, taskTypeId = running.id, taskType = running, startAt = command.effective.at!!)
                snapshot = snapshot.copy(cursor = 2, current = next, records = listOf(current.copy(endAt = next.startAt), next),
                    provenance = mapOf("2" to command.operationId), acknowledgement = ActivityAcknowledgementDto(
                        command.operationId, ActivityOutcome.Applied))
                return snapshot
            }
        }, object : ActivityStorage {
            override fun load(): String? = null
            override fun save(value: String) {}
        }, wallTime = { now.toEpochMilli() }, monotonicTime = { 0L })
        repository.refresh()
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Switch to running"); runCurrent()
        api.proposal(); api.emit("completed"); api.emit("eof"); runCurrent()
        controller.chooseProposal("p1", 3)
        val proposal = controller.state.value.exchanges.single().proposal!!
        val before = deriveProposal(proposal, controller.state.value.proposals.getValue("p1"), snapshot, now, zone)
        val tracking = ProposalTracking(controller, repository, TrackingHandoff(), {}, this)
        tracking.confirm(proposal, before)
        tracking.confirm(proposal, before) // A second tap must not queue another switch.
        runCurrent()
        try {
            withContext(Dispatchers.Default) { withTimeout(5000) { entered.await() } }
            val during = controller.state.value.proposals.getValue("p1")
            val after = repository.state.value.snapshot!!
            assertEquals(3, after.current!!.taskTypeId)
            assertNotNull("The live view would show the spurious warning", deriveProposal(proposal, during, after, now, zone).invalid)
            assertEquals("Confirmation must retain its original valid view", before, during.applying)
            assertEquals(1, commands)
        } finally { release.complete(Unit) }
        val applied = controller.state.first { it.proposals["p1"]?.status == ProposalStatus.Applied }.proposals.getValue("p1")
        assertNull(applied.applying)
        assertNotNull(applied.record)
    }

    private class Fake : AssistantTransport {
        override var supportsActivityCards = true
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

    private suspend fun Fake.card(id: String = "snapshot-1") {
        events.emit(AssistantEvent("plan_card", buildJsonObject {
            put("run_id", run); put("sequence", ++sequence); put("schema_version", 2)
            put("lane", "planned"); put("recurring_not_materialized", false)
            put("snapshot_id", id); put("date", "2026-09-21")
            put("reporting_timezone", "Asia/Singapore"); put("read_at", "2026-09-21T01:41:00Z")
            putJsonArray("blocks") {}
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

    @Test fun `negotiated cards retain order after proposal and reject a fourth card`() = runTest {
        val api = Fake().apply { supportsActivityCards = true }
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Compare"); runCurrent()
        api.proposal(); api.card("b"); api.card("a"); api.card("c"); runCurrent()
        assertEquals(listOf("b", "a", "c"), controller.state.value.exchanges.single().cards.map { it.id })
        api.card("d"); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.single().status)
        assertEquals(3, controller.state.value.exchanges.single().cards.size)
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
        assertEquals(1, controller.state.value.exchanges.single().cards.size)
        assertEquals("", controller.state.value.exchanges.single().status)
        api.emit("completed"); api.emit("eof"); runCurrent()
        assertEquals("Complete", controller.state.value.exchanges.single().status)
        controller.send("Earlier plan?"); runCurrent()
        assertEquals(1, api.acks)
    }

    @Test fun `duplicate card interrupts and later completion cannot repair it`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Show plan"); runCurrent()
        api.card(); api.card(); runCurrent()
        api.emit("completed"); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.single().status)
        assertEquals(1, controller.state.value.exchanges.single().cards.size)
        controller.send("Next"); runCurrent()
        assertEquals(0, api.acks)
    }

    @Test fun `card after text is rejected and stop retains validated card`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Show plan"); runCurrent()
        api.emit("text_delta", "answer"); api.card(); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.single().status)
        assertTrue(controller.state.value.exchanges.single().cards.isEmpty())
        controller.retry(); runCurrent(); api.card(); runCurrent()
        controller.stop(); runCurrent()
        assertEquals(1, controller.state.value.exchanges.last().cards.size)
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
