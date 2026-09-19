package com.timebox.android.ui.assistant

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

interface AssistantTransport {
    suspend fun create(): String
    suspend fun delete(conversation: String)
    suspend fun stop(conversation: String, run: String)
    suspend fun acknowledge(conversation: String, run: String)
    fun stream(conversation: String, run: String, message: String): Flow<AssistantEvent>
}

class HttpAssistantTransport(private val settings: AppSettings) : AssistantTransport {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(130, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).build()

    private fun request(path: String, method: String = "POST", body: JsonObject = JsonObject(emptyMap())): Request =
        Request.Builder().url(ApiFactory.normalizeBaseUrl(settings.baseUrl) + "assistant/" + path)
            .header("X-API-Key", settings.apiKey)
            .header("X-Timebox-Protocol", "activity-online-v1")
            .method(method, if (method == "DELETE") null else body.toString().toRequestBody("application/json".toMediaType()))
            .build()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
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
        throw IOException(detail ?: when (response.code) {
            401, 403 -> "Check the Timebox API key in Settings."
            410 -> "Conversation expired. Start a new conversation."
            else -> "Assistant could not respond (${response.code}). Please retry."
        })
    }

    override suspend fun create(): String = execute(request("conversations")).use {
        check(it)
        ApiFactory.json.parseToJsonElement(it.body!!.string()).jsonObject.getValue("conversation_id").jsonPrimitive.content
    }
    override suspend fun delete(conversation: String) { execute(request("conversations/$conversation", "DELETE")).use(::check) }
    override suspend fun stop(conversation: String, run: String) {
        execute(request("conversations/$conversation/runs/$run/stop")).use(::check)
    }
    override suspend fun acknowledge(conversation: String, run: String) {
        execute(request("conversations/$conversation/runs/$run/ack")).use(::check)
    }

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
