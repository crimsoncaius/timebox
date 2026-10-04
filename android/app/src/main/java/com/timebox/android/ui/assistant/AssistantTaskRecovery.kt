package com.timebox.android.ui.assistant

import com.timebox.android.data.AssistantTaskJournal
import com.timebox.android.data.TaskSubmission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

data class TaskRecoveryItem(val record: TaskSubmission, val result: TaskOperationResult? = null, val error: String? = null)
data class TaskRecoveryState(val items: List<TaskRecoveryItem> = emptyList(), val busy: Boolean = false, val error: String? = null, val online: Boolean = true, val operationId: String? = null, val phase: String? = null)

/** Owns finite foreground work. Persisted pointers are queried, never replayed automatically. */
class AssistantTaskRecovery(
    private val scope: CoroutineScope,
    val journal: AssistantTaskJournal,
    private val transport: suspend () -> AssistantTransport,
    private val reserve: suspend (TaskSubmission) -> Unit,
    private val release: suspend (TaskSubmission) -> Unit,
    private val reconcile: suspend (TaskSubmission, TaskOperationResult) -> Unit,
) {
    private val mutableState = MutableStateFlow(TaskRecoveryState())
    val state = mutableState.asStateFlow()
    fun connectionChanged(online: Boolean) { mutableState.value = state.value.copy(online = online) }
    private fun publish(identity: String, results: Map<String, TaskOperationResult> = emptyMap()) {
        mutableState.value = state.value.copy(items = journal.records(identity).map { record ->
            TaskRecoveryItem(record, results[record.submissionId] ?: record.result?.let { TaskOperationResult.parse(Json.parseToJsonElement(it).jsonObject) }
                ?: state.value.items.find { it.record.submissionId == record.submissionId }?.result)
        })
    }
    private fun work(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null)
        scope.launch {
            try { action() }
            catch (error: Exception) { mutableState.value = state.value.copy(error = error.message ?: "Task changes need checking. Check your connection.") }
            finally { mutableState.value = state.value.copy(busy = false, phase = null, operationId = null) }
        }
    }
    fun foreground() = work {
        val api = transport()
        publish(api.serverIdentity)
        // Closure is durable too. A fresh local view never implies server cancellation.
        journal.closures(api.serverIdentity).forEach { old ->
            try { api.delete(old); journal.closed(api.serverIdentity, old) } catch (_: Exception) { /* Retry next foreground cycle. */ }
        }
        recover(api)
    }
    private suspend fun recover(api: AssistantTransport) {
        mutableState.value = state.value.copy(phase = "checking")
        for (wait in listOf(0L, 1_000L, 2_000L)) {
            val records = journal.records(api.serverIdentity).filterNot { it.resolved }
            if (records.isEmpty()) break
            if (wait > 0) delay(wait)
            try {
                val results = api.taskStatuses(records)
                results.forEach { result ->
                    val record = records.single { it.operationId == result.operationId && it.submissionId == result.submission?.uuid("submission_id") }
                    accept(record, result)
                }
                publish(api.serverIdentity, results.associateBy { it.submission!!.uuid("submission_id") })
            } catch (error: Exception) {
                mutableState.value = state.value.copy(error = "Task changes need checking. Connect and choose Check result.")
            }
        }
    }
    private suspend fun accept(record: TaskSubmission, result: TaskOperationResult): Boolean {
        check(result.operationId == record.operationId && result.proposalId == record.proposalId)
        val terminal = result.receipt != null || result.submission?.optionalText("state") in setOf("rolled_back", "rejected") ||
            (record.kind == "confirm" && result.status in setOf("cancelled", "expired", "stale", "replaced", "invalid"))
        if (!terminal) return false
        // Failure to refresh is still unresolved locally; the durable barrier stays in place.
        reconcile(record, result)
        journal.resolve(record, checkNotNull(result.raw).toString())
        release(record)
        if (record.kind == "undo" && result.receipt != null) {
            journal.records(record.serverIdentity).filter { it.operationId == record.originalOperationId && it.result != null }.forEach { original ->
                val saved = Json.parseToJsonElement(original.result!!).jsonObject
                val updated = JsonObject(saved + ("undo_result" to buildJsonObject {
                    put("operation_id", record.operationId); put("status", "applied"); put("receipt", result.receipt)
                }))
                TaskOperationResult.parse(updated)
                journal.resolve(original, updated.toString())
            }
        }
        return true
    }
    fun confirm(proposal: TaskChangeProposal, expectedIdentity: String, onResult: (TaskOperationResult?) -> Unit = {}) = work {
        check(state.value.online) { "Connect before confirming Task changes. Nothing was queued." }
        val api = transport()
        check(api.serverIdentity == expectedIdentity) { "Server changed. Start a new conversation on this server." }
        check(proposal.sourceCompleted && proposal.status == "pending")
        mutableState.value = state.value.copy(operationId = proposal.operationId, phase = "sync_blocked")
        check(journal.records(api.serverIdentity).none { it.operationId == proposal.operationId && (!it.resolved || it.result?.let { r -> TaskOperationResult.parse(Json.parseToJsonElement(r).jsonObject).receipt != null } == true) }) { "Check the existing Task result before confirming again." }
        val operations = proposal.raw.getValue("operations").jsonArray
        val record = TaskSubmission(api.serverIdentity, proposal.conversationId, proposal.id, proposal.operationId, UUID.randomUUID().toString(),
            affectedTaskIds = (proposal.raw["parent_ids"]?.jsonArray.orEmpty().map { it.jsonPrimitive.int } + proposal.targets.mapNotNull { it.id }).distinct().sorted(),
            affectedFields = proposal.targets.flatMap { it.after.keys + it.transitions }.distinct().sorted(),
            affectsTracking = operations.any { it.jsonObject.text("op") == "complete_now" }, submittedAt = Instant.now().toString(),
            parentTaskIds = proposal.raw["parent_ids"]?.jsonArray.orEmpty().map { it.jsonPrimitive.int })
        submit(api, record, proposal.contentHash, onResult)
    }
    private suspend fun submit(api: AssistantTransport, record: TaskSubmission, hash: String?, onResult: (TaskOperationResult?) -> Unit) {
        var persisted = false
        try {
            reserve(record)
            journal.reserve(record)
            journal.drainWrites(record)
            val reviewed = api.taskReview(record.proposalId)
            check(reviewed.id == record.proposalId && reviewed.conversationId == record.conversationId && (hash == null || reviewed.contentHash == hash)) { "The review changed. Review again before confirming." }
            if (record.kind != "undo" && (reviewed.status != "pending" || !reviewed.sourceCompleted)) {
                onResult(api.taskStatus(record.operationId))
                error("The review is ${reviewed.status}. Refresh and review again.")
            }
            check(transport().serverIdentity == api.serverIdentity) { "Server changed. Return to the original server." }
            journal.persist(record) // Must complete before sending any mutation HTTP request.
            persisted = true
            mutableState.value = state.value.copy(operationId = record.operationId, phase = "submitting")
            publish(api.serverIdentity)
            val result = try { api.confirmTask(record) } catch (_: Exception) { null }
            if (result != null) accept(record, result)
            onResult(result)
            publish(api.serverIdentity, result?.let { mapOf(record.submissionId to it) }.orEmpty())
            if (result == null || journal.records(api.serverIdentity).any { it.submissionId == record.submissionId && !it.resolved }) recover(api)
        } finally {
            if (!persisted) journal.release(record.submissionId)
            // The durable record now holds the recovery barrier; no coroutine mutex spans offline time.
            release(record)
        }
    }
    fun retry(record: TaskSubmission) = work {
        check(state.value.online) { "Connect before retrying Task changes." }
        val api = transport()
        check(record.serverIdentity == api.serverIdentity)
        check(journal.records(api.serverIdentity).any { it.submissionId == record.submissionId && !it.resolved })
        val status = api.taskStatuses(listOf(record)).singleOrNull()
        if (status != null && accept(record, status)) { publish(api.serverIdentity); return@work }
        check(status?.submission?.optionalText("state") == "not_seen") { "Check result before retrying." }
        // Explicit retry only, with the SAME submission identity and existing barrier.
        val reviewed = api.taskReview(record.proposalId)
        check(record.kind == "undo" || reviewed.status == "pending") { "This proposal cannot be retried. Check result." }
        val result = try { api.confirmTask(record) } catch (_: Exception) { null }
        if (result != null) accept(record, result)
        publish(api.serverIdentity, result?.let { mapOf(record.submissionId to it) }.orEmpty())
        if (result == null) recover(api)
    }
    fun undo(original: TaskRecoveryItem) = work {
        check(state.value.online) { "Connect before Undo. Nothing was queued." }
        val api = transport()
        check(original.record.serverIdentity == api.serverIdentity && original.record.resolved)
        val current = api.taskStatus(original.record.operationId) ?: error("Check the result before Undo.")
        val undo = checkNotNull(current.undo).also { check(it.text("status") == "available") }
        val record = original.record.copy(operationId = undo.uuid("operation_id"), submissionId = UUID.randomUUID().toString(), kind = "undo",
            originalOperationId = original.record.operationId, affectsTracking = true, submittedAt = Instant.now().toString(), resolved = false, result = null)
        submit(api, record, null) { }
    }
    fun acknowledge(record: TaskSubmission) = work {
        val api = transport(); check(api.serverIdentity == record.serverIdentity)
        journal.acknowledge(record.submissionId); publish(api.serverIdentity)
    }
    suspend fun reviewRetry(record: TaskSubmission): TaskChangeProposal {
        val api = transport()
        check(api.serverIdentity == record.serverIdentity && record.resolved)
        return api.taskReview(record.proposalId)
    }
    suspend fun refreshReview(proposal: TaskChangeProposal, identity: String): TaskChangeProposal {
        val api = transport(); check(api.serverIdentity == identity)
        return api.refreshTask(proposal.id)
    }
    suspend fun descriptionReview(proposal: TaskChangeProposal, identity: String): TaskChangeProposal {
        val api = transport(); check(api.serverIdentity == identity)
        return api.taskReview(proposal.id).also { check(it.contentHash == proposal.contentHash) }
    }
    suspend fun dismissReview(proposal: TaskChangeProposal, identity: String): TaskOperationResult {
        val api = transport(); check(api.serverIdentity == identity)
        return checkNotNull(api.dismissTask(proposal.id)) { "Dismissal needs checking. Try again." }
    }
}
