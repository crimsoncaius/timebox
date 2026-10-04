package com.timebox.android.data

import android.content.Context
import android.util.AtomicFile
import com.timebox.android.data.remote.ApiFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** Recovery pointers only. Never a mutation queue or a saved conversation. */
@Serializable
data class TaskSubmission(
    val serverIdentity: String, val conversationId: String, val proposalId: String,
    val operationId: String, val submissionId: String, val revision: Int = 1,
    val kind: String = "confirm", val affectedTaskIds: List<Int>, val affectedFields: List<String>,
    val affectsTracking: Boolean, val submittedAt: String, val originalOperationId: String? = null,
    val resolved: Boolean = false, val result: String? = null, val parentTaskIds: List<Int> = emptyList(),
)

@Serializable
data class TaskJournalData(val submissions: List<TaskSubmission> = emptyList(), val closures: Map<String, List<String>> = emptyMap())

interface TaskJournalStorage { fun read(): String?; fun write(value: String) }

class AndroidTaskJournalStorage(context: Context) : TaskJournalStorage {
    private val file = AtomicFile(File(context.filesDir, "assistant-task-recovery.json"))
    override fun read(): String? = try { file.openRead().bufferedReader().use { it.readText() } }
        catch (missing: java.io.FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw missing
            null
        }
    override fun write(value: String) {
        val output = file.startWrite()
        try { output.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }
}

fun AppSettings.assistantIdentity(): String {
    // The API is single-account; a key change is an account boundary. No credential is stored.
    val bytes = (ApiFactory.normalizeBaseUrl(baseUrl) + "\n" + apiKey).toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

class AssistantTaskJournal(private val storage: TaskJournalStorage) {
    private val json = Json { encodeDefaults = true }
    private var loadError: Throwable? = null
    private var data = try { storage.read()?.let { json.decodeFromString<TaskJournalData>(it) } ?: TaskJournalData() }
        catch (error: Throwable) { loadError = error; TaskJournalData() }
    private val reservations = mutableMapOf<String, TaskSubmission>()
    // Task IDs are positive; key zero marks manual completion's Activity Tracking effects.
    private val active = mutableMapOf<String, Pair<String, Set<Int>>>()

    @Synchronized fun records(identity: String): List<TaskSubmission> { healthy(); return data.submissions.filter { it.serverIdentity == identity } }
    @Synchronized fun closures(identity: String): List<String> { healthy(); return data.closures[identity].orEmpty() }
    @Synchronized fun closeLater(identity: String, conversation: String) {
        healthy(); save(data.copy(closures = data.closures + (identity to (data.closures[identity].orEmpty() + conversation).distinct())))
    }
    @Synchronized fun closed(identity: String, conversation: String) {
        save(data.copy(closures = data.closures + (identity to data.closures[identity].orEmpty().filterNot { it == conversation })))
    }
    private fun healthy() { check(loadError == null) { "Task recovery storage could not be read. Restore it before changing Tasks." } }
    private fun save(next: TaskJournalData) { healthy(); storage.write(json.encodeToString(next)); data = next }
    private fun held(identity: String) = (data.submissions.filter { !it.resolved } + reservations.values).filter { it.serverIdentity == identity }
    @Synchronized fun blocked(identity: String, ids: Collection<Int>): Boolean { if (loadError != null) return true; return held(identity).any { item -> item.affectedTaskIds.any { it in ids } || (0 in ids && item.affectsTracking) } }
    @Synchronized fun trackingBlocked(identity: String): Boolean { if (loadError != null) return true; return held(identity).any { it.affectsTracking } }
    @Synchronized fun reserve(record: TaskSubmission) {
        healthy()
        check(!blocked(record.serverIdentity, record.affectedTaskIds)) { RecoveryMessage }
        reservations[record.submissionId] = record
    }
    suspend fun drainWrites(record: TaskSubmission) = withTimeout(30_000) {
        while (synchronized(this@AssistantTaskJournal) { active.values.any { (identity, ids) -> identity == record.serverIdentity && ids.any { it in record.affectedTaskIds } || (identity == record.serverIdentity && record.affectsTracking && 0 in ids) } }) delay(25)
    }
    @Synchronized fun release(submissionId: String) { reservations.remove(submissionId) }
    @Synchronized fun persist(record: TaskSubmission) {
        check(data.submissions.count { !it.resolved } < 20) { "Check unresolved Task changes before confirming another change." }
        save(data.copy(submissions = data.submissions.filterNot { it.submissionId == record.submissionId } + record))
    }
    @Synchronized fun resolve(record: TaskSubmission, result: String) {
        check(result.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        save(data.copy(submissions = data.submissions.map { if (it.submissionId == record.submissionId) it.copy(resolved = true, result = result) else it }))
        release(record.submissionId)
    }
    @Synchronized fun acknowledge(submissionId: String) {
        save(data.copy(submissions = data.submissions.filterNot { it.submissionId == submissionId && it.resolved }))
    }
    suspend fun <T> write(identity: String, ids: Collection<Int>, block: suspend () -> T): T {
        val token = java.util.UUID.randomUUID().toString()
        synchronized(this) { check(!blocked(identity, ids)) { RecoveryMessage }; active[token] = identity to ids.toSet() }
        try { return block() } finally { synchronized(this) { active.remove(token) } }
    }
    companion object {
        const val RecoveryMessage = "Task changes need checking in Assistant before changing this Task."
    }
}
