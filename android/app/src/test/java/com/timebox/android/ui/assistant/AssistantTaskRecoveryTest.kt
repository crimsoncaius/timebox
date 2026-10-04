package com.timebox.android.ui.assistant

import com.timebox.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

private class PointerStorage : TaskJournalStorage {
    var value: String? = null
    var fail = false
    override fun read() = value
    override fun write(value: String) { check(!fail) { "Disk full" }; this.value = value }
}
private fun recoveryFixture(key: String) = Json.parseToJsonElement(AssistantTaskRecoveryTest::class.java.getResource("/assistant-tasks.json")!!.readText()).jsonObject.getValue(key).jsonObject
private fun pointer(operation: String = UUID.randomUUID().toString(), id: Int = 1) = TaskSubmission("test", UUID.randomUUID().toString(), UUID.randomUUID().toString(), operation,
    UUID.randomUUID().toString(), affectedTaskIds = listOf(id), affectedFields = listOf("ready_to_plan"), affectsTracking = false, submittedAt = "2026-10-04T00:00:00Z")
private class RecoveryApi(val store: PointerStorage) : AssistantTransport {
    var identity = "test"
    override val serverIdentity get() = identity
    var confirms = mutableListOf<TaskSubmission>()
    var checks = 0
    var status = "not_seen"
    var loseReply = true
    var review = TaskChangeProposal.parse(recoveryFixture("full"), true)
    override suspend fun taskReview(id: String) = review
    fun result(record: TaskSubmission): TaskOperationResult {
        val base = recoveryFixture("result")
        return TaskOperationResult.parse(JsonObject(base + mapOf(
            "operation_id" to JsonPrimitive(record.operationId), "proposal_id" to JsonPrimitive(record.proposalId),
            "status" to JsonPrimitive(if (status == "applied") "applied" else "pending"),
            "receipt" to if (status == "applied") base.getValue("receipt") else JsonNull,
            "submission" to buildJsonObject { put("submission_id", record.submissionId); put("operation_id", record.operationId); put("state", status) }
        )))
    }
    override suspend fun confirmTask(record: TaskSubmission): TaskOperationResult? {
        check(store.value!!.contains(record.submissionId))
        confirms += record
        if (loseReply) throw java.io.IOException("Lost reply")
        status = "applied"
        return result(record)
    }
    override suspend fun taskStatuses(records: List<TaskSubmission>): List<TaskOperationResult> { checks++; return records.map(::result) }
    override suspend fun create() = "unused"
    override suspend fun delete(conversation: String) {}
    override suspend fun stop(conversation: String, run: String) {}
    override suspend fun acknowledge(conversation: String, run: String) {}
    override fun stream(conversation: String, run: String, message: String) = emptyFlow<AssistantEvent>()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantTaskRecoveryTest {
    @Test fun `terminal rollback permits a new explicit submission for same operation`() = runTest {
        val storage = PointerStorage(); val api = RecoveryApi(storage).also { it.status = "rolled_back" }; val journal = AssistantTaskJournal(storage)
        val engine = AssistantTaskRecovery(this, journal, { api }, {}, {}, { _, _ -> })
        engine.confirm(api.review, "test"); advanceUntilIdle()
        assertTrue(journal.records("test").single().resolved)
        val review = engine.reviewRetry(journal.records("test").single())
        api.loseReply = false
        engine.confirm(review, "test"); advanceUntilIdle()
        assertEquals(2, api.confirms.size)
        assertEquals(api.confirms.first().operationId, api.confirms.last().operationId)
        assertNotEquals(api.confirms.first().submissionId, api.confirms.last().submissionId)
    }
    @Test fun `known offline confirmation never writes pointer or contacts server`() = runTest {
        val storage = PointerStorage(); val api = RecoveryApi(storage); val journal = AssistantTaskJournal(storage)
        val engine = AssistantTaskRecovery(this, journal, { api }, {}, {}, { _, _ -> })
        engine.connectionChanged(false); engine.confirm(api.review, "test"); advanceUntilIdle()
        assertTrue(journal.records("test").isEmpty()); assertTrue(api.confirms.isEmpty()); assertEquals(0, api.checks)
    }
    @Test fun `lost reply restores scoped barriers after restart and never replays mutation`() = runTest {
        val storage = PointerStorage(); val api = RecoveryApi(storage); val journal = AssistantTaskJournal(storage)
        var reconciles = 0
        fun engine(j: AssistantTaskJournal) = AssistantTaskRecovery(this, j, { api }, {}, {}, { _, _ -> reconciles++ })
        val first = engine(journal)
        first.confirm(api.review, "test")
        advanceUntilIdle()
        assertEquals(1, api.confirms.size); assertEquals(3, api.checks)
        assertTrue(journal.blocked("test", listOf(1))); assertFalse(journal.blocked("other", listOf(1)))
        assertFalse(storage.value!!.contains("Private original")); assertFalse(storage.value!!.contains("Revised private"))
        val restored = AssistantTaskJournal(storage); val second = engine(restored)
        assertTrue(restored.blocked("test", listOf(1)))
        second.foreground(); advanceUntilIdle()
        assertEquals(1, api.confirms.size); assertEquals(6, api.checks)
        api.status = "applied"
        second.foreground(); advanceUntilIdle()
        assertEquals(1, api.confirms.size); assertEquals(1, reconciles)
        assertFalse(restored.blocked("test", listOf(1)))
        assertTrue(second.state.value.items.single().record.resolved)
    }
    @Test fun `not seen explicit retry reuses submission and operation identities`() = runTest {
        val storage = PointerStorage(); val api = RecoveryApi(storage); val journal = AssistantTaskJournal(storage)
        val engine = AssistantTaskRecovery(this, journal, { api }, {}, {}, { _, _ -> })
        engine.confirm(api.review, "test"); advanceUntilIdle()
        val original = api.confirms.single()
        api.loseReply = false
        engine.retry(original); advanceUntilIdle()
        assertEquals(listOf(original, original), api.confirms)
        assertTrue(journal.records("test").single().resolved)
    }
    @Test fun `storage failure sends no mutation and releases temporary reservation`() = runTest {
        val storage = PointerStorage().also { it.fail = true }; val api = RecoveryApi(storage); val journal = AssistantTaskJournal(storage)
        var releases = 0
        val engine = AssistantTaskRecovery(this, journal, { api }, {}, { releases++ }, { _, _ -> })
        engine.confirm(api.review, "test"); advanceUntilIdle()
        assertTrue(api.confirms.isEmpty()); assertTrue(journal.records("test").isEmpty()); assertEquals(1, releases)
        assertFalse(journal.blocked("test", listOf(1)))
    }
    @Test fun `reconciliation failure retains pointer even after authoritative success`() = runTest {
        val storage = PointerStorage(); val api = RecoveryApi(storage).also { it.loseReply = false }; val journal = AssistantTaskJournal(storage)
        val engine = AssistantTaskRecovery(this, journal, { api }, {}, {}, { _, _ -> error("Refresh failed") })
        engine.confirm(api.review, "test"); advanceUntilIdle()
        assertEquals(1, api.confirms.size); assertFalse(journal.records("test").single().resolved)
        assertTrue(journal.blocked("test", listOf(1)))
    }
    @Test fun `endpoint switch cannot send an existing proposal or pointer`() = runTest {
        val storage = PointerStorage(); val api = RecoveryApi(storage); val journal = AssistantTaskJournal(storage)
        val engine = AssistantTaskRecovery(this, journal, { api }, {}, {}, { _, _ -> })
        engine.confirm(api.review, "test"); advanceUntilIdle()
        val record = api.confirms.single(); api.identity = "other"
        engine.foreground(); advanceUntilIdle(); assertEquals(3, api.checks); assertTrue(engine.state.value.items.isEmpty())
        engine.retry(record); advanceUntilIdle(); engine.confirm(api.review, "test"); advanceUntilIdle()
        assertEquals(1, api.confirms.size)
    }
    @Test fun `unresolved cap and corrupt storage fail closed without losing pointers`() {
        val storage = PointerStorage(); val journal = AssistantTaskJournal(storage)
        repeat(20) { journal.persist(pointer(id = it + 1)) }
        assertTrue(runCatching { journal.persist(pointer(id = 21)) }.isFailure)
        assertEquals(20, AssistantTaskJournal(storage).records("test").size)
        storage.value = "invalid"
        assertTrue(AssistantTaskJournal(storage).blocked("test", listOf(1)))
    }
    @Test fun `reservation drains preexisting writes but rejects overlapping new writes`() = runTest {
        val journal = AssistantTaskJournal(PointerStorage()); val finish = CompletableDeferred<Unit>()
        val old = launch { journal.write("test", listOf(1)) { finish.await() } }; runCurrent()
        val record = pointer(); journal.reserve(record)
        val drain = async { journal.drainWrites(record) }; runCurrent()
        assertFalse(drain.isCompleted)
        assertTrue(runCatching { journal.write("test", listOf(1)) { } }.isFailure)
        assertTrue(runCatching { journal.write("test", listOf(2)) { } }.isSuccess)
        finish.complete(Unit); old.join(); advanceUntilIdle(); drain.await()
        journal.release(record.submissionId)
        assertFalse(journal.blocked("test", listOf(1)))
    }
    @Test fun `conversation closures survive restart and are endpoint scoped`() {
        val storage = PointerStorage(); val journal = AssistantTaskJournal(storage)
        journal.closeLater("test", "old")
        val restored = AssistantTaskJournal(storage)
        assertEquals(listOf("old"), restored.closures("test")); assertTrue(restored.closures("other").isEmpty())
        restored.closed("test", "old"); assertTrue(AssistantTaskJournal(storage).closures("test").isEmpty())
    }
}
