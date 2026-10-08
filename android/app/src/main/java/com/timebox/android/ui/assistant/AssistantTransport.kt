package com.timebox.android.ui.assistant

import com.timebox.android.data.assistantIdentity
import com.timebox.android.data.AppSettings
import com.timebox.android.data.remote.ApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AssistantEvent(val kind: String, val data: JsonObject)
class AssistantEndedException(message: String) : IOException(message)

interface AssistantTransport {
    val serverIdentity: String get() = "test"
    suspend fun taskStatuses(records: List<com.timebox.android.data.TaskSubmission>): List<TaskOperationResult> = error("Task recovery unavailable")
    suspend fun confirmTask(record: com.timebox.android.data.TaskSubmission): TaskOperationResult? = error("Task confirmation unavailable")
    suspend fun create(previousConversation: String?): String = create()
    val supportsActivityCards: Boolean get() = false
    val supportsTrackingProposals: Boolean get() = false
    suspend fun taskReview(id: String): TaskChangeProposal = error("Task review unavailable")
    suspend fun taskStatus(operation: String): TaskOperationResult? = error("Task status unavailable")
    suspend fun dismissTask(id: String): TaskOperationResult? = error("Task dismissal unavailable")
    suspend fun refreshTask(id: String): TaskChangeProposal = error("Task refresh unavailable")
    suspend fun create(): String
    suspend fun delete(conversation: String)
    suspend fun stop(conversation: String, run: String)
    suspend fun acknowledge(conversation: String, run: String)
    fun stream(conversation: String, run: String, message: String): Flow<AssistantEvent>
}

class HttpAssistantTransport(private val settings: AppSettings) : AssistantTransport {
    override val serverIdentity = settings.assistantIdentity()
    override var supportsTrackingProposals: Boolean = false
        private set
    override var supportsActivityCards: Boolean = false
        private set
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(130, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).build()

    private val taskClient = client.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()

    private fun request(path: String, method: String = "POST", body: JsonObject = JsonObject(emptyMap())): Request =
        Request.Builder().url(ApiFactory.normalizeBaseUrl(settings.baseUrl) + "assistant/" + path)
            .header("X-API-Key", settings.apiKey)
            .header("X-Timebox-Protocol", "activity-online-v1")
            .method(method, if (method in setOf("DELETE", "GET")) null else body.toString().toRequestBody("application/json".toMediaType()))
            .build()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun execute(request: Request, httpClient: OkHttpClient = client): Response = suspendCancellableCoroutine { continuation ->
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Cannot reach Assistant. Check your connection."))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { response.close() }
            }
        })
    }

    private fun check(response: Response) {
        if (response.isSuccessful) return
        val detail = runCatching {
            ApiFactory.json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull()
        if (response.code == 410 || (response.code == 409 && detail?.contains("20 exchanges") == true)) {
            throw AssistantEndedException(detail ?: "Conversation expired. Start a new conversation.")
        }
        throw IOException(detail ?: when (response.code) {
            401, 403 -> "Check the Timebox API key in Settings."
            410 -> "Conversation expired. Start a new conversation."
            else -> "Assistant could not respond (${response.code}). Please retry."
        })
    }

    override suspend fun create(): String = create(null)
    override suspend fun create(previousConversation: String?): String = kotlinx.coroutines.withContext(Dispatchers.IO) { execute(request("conversations", body = buildJsonObject {
        previousConversation?.let { put("previous_conversation_id", it) }
        putJsonArray("capabilities") { add("activity_cards_v1"); add("tracking_proposal_v1") }
    })).use {
        check(it)
        val result = ApiFactory.json.parseToJsonElement(it.body!!.string()).jsonObject
        val granted = result["capabilities"]?.jsonArray?.map { value -> value.jsonPrimitive.content }.orEmpty()
        supportsActivityCards = "activity_cards_v1" in granted
        supportsTrackingProposals = "tracking_proposal_v1" in granted
        result.getValue("conversation_id").jsonPrimitive.content
    } }
    override suspend fun delete(conversation: String) = kotlinx.coroutines.withContext(Dispatchers.IO) { execute(request("conversations/$conversation", "DELETE")).use(::check) }
    override suspend fun stop(conversation: String, run: String) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        execute(request("conversations/$conversation/runs/$run/stop")).use(::check)
    }
    override suspend fun acknowledge(conversation: String, run: String) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        execute(request("conversations/$conversation/runs/$run/ack")).use(::check)
    }

    // Task reads use the bounded request deadline, independently of the SSE lifetime.
    private suspend fun taskRequest(path: String, method: String = "POST", body: JsonObject = JsonObject(emptyMap()), maxBytes: Int = 64 * 1024): JsonObject =
        kotlinx.coroutines.withContext(Dispatchers.IO) { kotlinx.coroutines.withTimeout(30_000) {
            execute(request(path, method, body), taskClient).use { response ->
                val source = response.body?.source() ?: throw IOException("Empty task response")
                if (source.request(maxBytes.toLong() + 1)) throw IOException("Task response exceeds its bound")
                val data = ApiFactory.json.parseToJsonElement(source.readUtf8()).jsonObject
                if (!response.isSuccessful && "operation_id" !in data) throw IOException("Could not establish Task result (${response.code}). Check result.")
                data
            }
        } }

    override suspend fun taskStatuses(records: List<com.timebox.android.data.TaskSubmission>): List<TaskOperationResult> {
        require(records.size in 1..20)
        val data = taskRequest("task-operations/status", maxBytes = records.size * 64 * 1024 + 1024, body = buildJsonObject {
            putJsonArray("operations") { records.forEach { record ->
                check(record.serverIdentity == serverIdentity)
                add(buildJsonObject { put("operation_id", record.operationId); put("submission_id", record.submissionId) })
            } }
        })
        if (data["status"]?.jsonPrimitive?.content == "checking") return emptyList()
        return data.getValue("operations").jsonArray.map { TaskOperationResult.parse(it.jsonObject) }.also { results ->
            check(results.size == records.size)
            results.zip(records).forEach { (result, record) -> check(result.operationId == record.operationId && result.proposalId == record.proposalId && result.submission?.uuid("submission_id") == record.submissionId) }
        }
    }
    override suspend fun confirmTask(record: com.timebox.android.data.TaskSubmission): TaskOperationResult? {
        check(record.serverIdentity == serverIdentity)
        val path = if (record.kind == "undo") "task-operations/${record.originalOperationId}/undo" else "task-proposals/${record.proposalId}/confirm"
        val data = taskRequest(path, body = buildJsonObject {
            if (record.kind == "undo") put("undo_operation_id", record.operationId)
            else { put("revision", record.revision); put("operation_id", record.operationId) }
            put("submission_id", record.submissionId)
        })
        if (data["status"]?.jsonPrimitive?.content == "checking") return null
        return TaskOperationResult.parse(data).also { check(it.operationId == record.operationId && it.proposalId == record.proposalId) }
    }

    override suspend fun taskReview(id: String) = TaskChangeProposal.parse(taskRequest("task-proposals/$id", "GET"), fullDescription = true)
    override suspend fun taskStatus(operation: String): TaskOperationResult? {
        val data = taskRequest("task-operations/status", body = buildJsonObject {
            putJsonArray("operations") { add(buildJsonObject { put("operation_id", operation) }) }
        })
        if (data["status"]?.jsonPrimitive?.content == "checking") return null
        return TaskOperationResult.parse(data.getValue("operations").jsonArray.single().jsonObject).also { check(it.operationId == operation) }
    }
    override suspend fun dismissTask(id: String): TaskOperationResult? {
        val data = taskRequest("task-proposals/$id/dismiss")
        if (data["status"]?.jsonPrimitive?.content == "checking") return null
        return TaskOperationResult.parse(data).also { check(it.proposalId == id) }
    }
    override suspend fun refreshTask(id: String) = TaskChangeProposal.parse(taskRequest("task-proposals/$id/refresh"))

    override fun stream(conversation: String, run: String, message: String): Flow<AssistantEvent> = callbackFlow {
        val call = client.newCall(request("conversations/$conversation/messages", body = buildJsonObject {
            put("run_id", run); put("message", message)
        }))
        val reader = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    check(response)
                    val source = response.body?.source() ?: throw IOException("Empty response")
                    var kind = ""
                    val data = StringBuilder()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("event:") -> kind = line.substringAfter(':').trim()
                            line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.substringAfter(':').trimStart()) }
                            line.isEmpty() -> {
                                if (data.isNotEmpty()) send(AssistantEvent(kind, ApiFactory.json.parseToJsonElement(data.toString()).jsonObject))
                                kind = ""; data.clear()
                            }
                        }
                    }
                }
                close()
            } catch (error: Exception) { close(error) }
        }
        awaitClose { call.cancel(); reader.cancel() }
    }
}
