package com.timebox.android.ui.assistant

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

private fun fixture(name: String) = Json.parseToJsonElement(AssistantTasksTest::class.java.getResource("/assistant-tasks.json")!!.readText()).jsonObject.getValue(name).jsonObject
private fun JsonObject.changed(vararg values: Pair<String, JsonElement>) = JsonObject(this + values)

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantTasksTest {
    @Test fun `HTTP review and status use authenticated current endpoints without fetching prose`() = kotlinx.coroutines.runBlocking {
        val server = java.net.ServerSocket(0)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val requests = executor.submit<List<String>> {
            listOf(fixture("full"), buildJsonObject { put("operations", JsonArray(listOf(fixture("result")))) }).map { response ->
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) { val line = reader.readLine(); if (line.isEmpty()) break; lines += line }
                    val count = lines.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                    val body = CharArray(count)
                    var read = 0
                    while (read < count) read += reader.read(body, read, count - read)
                    val bytes = response.toString().toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() }
                    lines.joinToString("\n") + "\n" + body.concatToString()
                }
            }
        }
        try {
            val api = HttpAssistantTransport(com.timebox.android.data.AppSettings("http://127.0.0.1:${server.localPort}/", "fixture-key", null))
            assertEquals(fixture("full").text("proposal_id"), api.taskReview(fixture("full").text("proposal_id")).id)
            val operation = fixture("result").text("operation_id")
            assertEquals(operation, api.taskStatus(operation)!!.operationId)
            val captured = requests.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(captured[0].startsWith("GET /assistant/task-proposals/"))
            assertTrue(captured[0].contains("X-API-Key: fixture-key"))
            assertTrue(captured[0].contains("X-Timebox-Protocol: activity-online-v1"))
            assertTrue(captured[1].startsWith("POST /assistant/task-operations/status"))
            assertTrue(captured[1].contains(operation))
        } finally { server.close(); executor.shutdownNow() }
    }

    @Test fun `current server fixtures parse without losing rows reviews or private boundaries`() {
        val card = AssistantTaskCard.parse(fixture("card"))
        assertEquals(4, card.rows.size)
        assertEquals(listOf(1, 2, 3, 4), card.rows.map { it.id })
        assertEquals(card.rows[1].title, card.rows[2].title)
        assertFalse(card.toString().contains("Private original"))
        val proposal = TaskChangeProposal.parse(fixture("proposal"))
        assertEquals(2, proposal.targets.size)
        assertTrue(proposal.descriptionReview)
        assertFalse(proposal.toString().contains("Private original"))
        val full = TaskChangeProposal.parse(fixture("full"), fullDescription = true)
        assertTrue(full.targets.first().before!!.text("description").contains("<script>"))
        assertEquals(proposal.contentHash, full.contentHash)
        val result = TaskOperationResult.parse(fixture("result"))
        assertEquals("applied", result.status)
        assertFalse(result.toString().contains("Private original"))
        assertFalse(result.toString().contains("Revised private"))
    }

    @Test fun `server completion dated reopen and undo contracts stay distinct`() {
        listOf("complete", "dated", "reopen").forEach { TaskChangeProposal.parse(fixture(it)) }
        listOf("complete_result", "dated_result", "undo_result", "original_after_undo").forEach { TaskOperationResult.parse(fixture(it)) }
        val complete = TaskChangeProposal.parse(fixture("complete"))
        assertTrue(complete.effects.values.single().jsonObject.getValue("removed_plans").jsonArray.isNotEmpty())
        assertFalse(complete.toString().contains("PRIVATE SUPPORTING NOTE"))
        val dated = TaskChangeProposal.parse(fixture("dated"))
        assertFalse(taskValue(dated.targets.single().after.getValue("completion")).contains("12:00"))
        val original = TaskOperationResult.parse(fixture("original_after_undo"))
        assertEquals(fixture("complete_result").getValue("receipt"), original.receipt)
        assertEquals("applied", original.undo!!.text("status"))
    }

    @Test fun `card rejects private text malformed identities enums and bounds`() {
        val data = fixture("card")
        val row = data.getValue("rows").jsonArray.first().jsonObject
        val changes = listOf(
            data.changed("rows" to JsonArray(listOf(row.changed("description" to JsonPrimitive("private"))))),
            data.changed("rows" to JsonArray(listOf(row.changed("availability" to JsonPrimitive("unverified"))))),
            data.changed("rows" to JsonArray(listOf(row.changed("id" to JsonPrimitive("1"))))),
            data.changed("rows" to JsonArray(listOf(row, row))),
            data.changed("schema_version" to JsonPrimitive(5)),
            data.changed("count_relation" to JsonPrimitive("all")),
            data.changed("snapshot_id" to JsonPrimitive("invented")),
            data.changed("rows" to JsonArray(listOf(row.changed("title" to JsonPrimitive("猫".repeat(12000))))))
        )
        changes.forEach { assertTrue(runCatching { AssistantTaskCard.parse(it) }.isFailure) }
    }

    @Test fun `unavailable children and lower bound keep their meaning`() {
        val card = fixture("card")
        val row = card.getValue("rows").jsonArray.first().jsonObject.changed("subtasks" to JsonArray(listOf(buildJsonObject { put("id", 5); put("availability", "unavailable") })))
        val parsed = AssistantTaskCard.parse(card.changed("rows" to JsonArray(listOf(row)), "matching_count" to JsonPrimitive(1001), "count_relation" to JsonPrimitive("at_least"), "completeness" to JsonPrimitive("partial")))
        assertEquals(1001, parsed.count)
        assertTrue(parsed.partial)
        assertEquals("unavailable", parsed.rows.first().values.getValue("subtasks").jsonArray.first().jsonObject.text("availability"))
    }

    @Test fun `public proposal refuses full descriptions malformed targets and oversized reviews`() {
        assertTrue(runCatching { TaskChangeProposal.parse(fixture("full")) }.isFailure)
        val proposal = fixture("proposal")
        listOf(proposal.changed("revision" to JsonPrimitive(2)), proposal.changed("review_targets" to JsonArray(emptyList())),
            proposal.changed("description_review_available" to JsonPrimitive(false)),
            proposal.changed("content_hash" to JsonPrimitive("猫".repeat(23000)))).forEach {
            assertTrue(runCatching { TaskChangeProposal.parse(it) }.isFailure)
        }
    }

    @Test fun `date precision never prints artificial noon and description never formats private text`() {
        val date = Json.parseToJsonElement("""{"precision":"date","local_date":"2026-09-30","instant":"2026-09-30T12:00:00Z","reporting_timezone":"UTC"}""")
        assertEquals("2026-09-30 · UTC", taskValue(date))
        assertEquals("Description changed · separate review", taskValue(JsonPrimitive("PRIVATE"), "description"))
    }

    @Test fun `receipt requires evidence and rejects description text`() {
        assertTrue(runCatching { TaskOperationResult.parse(fixture("result").changed("receipt" to JsonNull)) }.isFailure)
        val result = fixture("result")
        val receipt = result.getValue("receipt").jsonObject
        val changes = receipt.getValue("changes").jsonArray.toMutableList()
        val first = changes.first().jsonObject
        changes[0] = first.changed("approved_after_values" to first.getValue("approved_after_values").jsonObject.changed("description" to JsonPrimitive("private")))
        assertTrue(runCatching { TaskOperationResult.parse(result.changed("receipt" to receipt.changed("changes" to JsonArray(changes)))) }.isFailure)
    }

    private class Fake(val kinds: List<String>, val alter: (JsonObject) -> JsonObject = { it }) : AssistantTransport {
        override val supportsTrackingProposals = true
        val conversation = fixture("proposal").text("conversation_id")
        override suspend fun create() = conversation
        override suspend fun delete(conversation: String) {}
        override suspend fun acknowledge(conversation: String, run: String) {}
        override suspend fun stop(conversation: String, run: String) {}
        override fun stream(conversation: String, run: String, message: String): Flow<AssistantEvent> = flow {
            kinds.forEachIndexed { index, kind ->
                var data = when(kind) {
                    "task_card" -> fixture("card")
                    "task_proposal" -> alter(fixture("proposal").changed("originating_run_id" to JsonPrimitive(run)))
                    "text_delta" -> buildJsonObject { put("text", "Visible answer") }
                    "failed" -> buildJsonObject { put("message", "Save failed") }
                    else -> buildJsonObject {}
                }
                data = data.changed("run_id" to JsonPrimitive(run), "sequence" to JsonPrimitive(index + 1))
                emit(AssistantEvent(kind, data))
            }
        }
        override suspend fun taskStatus(operation: String) = TaskOperationResult.parse(fixture("result"))
        override suspend fun taskReview(id: String) = error("Full text must never be fetched automatically")
    }

    @Test fun `card only and proposal only completed responses are accepted with saved eligibility`() = runTest {
        for (kind in listOf("task_card", "task_proposal")) {
            val api = Fake(listOf("started", kind, "completed"))
            val controller = AssistantController(backgroundScope) { api }
            controller.send("Read tasks"); runCurrent()
            assertEquals("Complete", controller.state.value.exchanges.single().status)
            if (kind == "task_proposal") {
                assertTrue(controller.state.value.taskChanges.values.single().sourceCompleted)
                assertEquals("pending", controller.state.value.taskChanges.values.single().status)
            }
        }
    }

    @Test fun `broken stream preserves valid partial output without eligibility`() = runTest {
        val streams = listOf(
            listOf("task_proposal", "task_proposal", "completed"),
            listOf("task_proposal", "tracking_proposal", "completed"),
            listOf("task_proposal", "text_delta", "task_card", "completed"),
            listOf("task_proposal", "text_delta", "failed"),
            listOf("task_proposal", "unknown_required", "completed"),
            listOf("task_proposal"),
            listOf("task_proposal", "completed", "text_delta"))
        for (events in streams) {
            val controller = AssistantController(backgroundScope) { Fake(listOf("started") + events) }
            controller.send("Edit task"); runCurrent()
            assertEquals(events.toString(), "Interrupted", controller.state.value.exchanges.single().status)
            assertNotNull(controller.state.value.exchanges.single().taskProposal)
            assertFalse(controller.state.value.taskChanges.values.single().sourceCompleted)
            assertEquals("invalid", controller.state.value.taskChanges.values.single().status)
        }
    }

    @Test fun `proposal rejects wrong association and status produces independent receipt`() = runTest {
        val wrong = AssistantController(backgroundScope) { Fake(listOf("task_proposal", "completed")) { it.changed("conversation_id" to JsonPrimitive(UUID.randomUUID().toString())) } }
        wrong.send("Change task"); runCurrent()
        assertEquals("Interrupted", wrong.state.value.exchanges.single().status)
        assertTrue(wrong.state.value.taskChanges.isEmpty())
        val controller = AssistantController(backgroundScope) { Fake(listOf("task_proposal", "completed")) }
        controller.send("Change task"); runCurrent()
        val proposal = controller.state.value.exchanges.single().taskProposal!!
        controller.checkTask(proposal); runCurrent()
        assertEquals("applied", controller.state.value.taskChanges[proposal.operationId]!!.result!!.status)
        assertEquals(proposal, controller.state.value.exchanges.single().taskProposal)
    }
}
