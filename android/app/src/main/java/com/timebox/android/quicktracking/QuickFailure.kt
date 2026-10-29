package com.timebox.android.quicktracking

import com.timebox.android.data.QuickInterpretation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Serializable data class QuickFailureEvent(val at: String, val command: String? = null, val reason: String)
@Serializable data class QuickFailure(
    val id: String = UUID.randomUUID().toString(), val endpoint: String, val deviceId: String,
    val occurredAt: String = Instant.now().toString(), val command: String? = null, val source: String = "app",
    val revision: Int = 1, val uploadedRevision: Int = 0, val status: String = "uncertain",
    val reason: String = "Waiting to check recording", val stage: String = "interpretation", val retryable: Boolean = false,
    val requestIds: List<String> = emptyList(), val history: List<QuickFailureEvent> = emptyList(),
    val request: JsonObject? = null, val result: QuickInterpretation? = null,
    val dismissed: Boolean = false, val dismissalUploaded: Boolean = false,
    val diagnostics: List<JsonObject> = emptyList(), val reviewId: Int? = null,
) {
    fun expired(now: Instant = Instant.now(), timezone: String = "UTC"): Boolean {
        val original = Instant.parse(occurredAt)
        return now > original.plusSeconds(900) || now.atZone(ZoneId.of(timezone)).toLocalDate() != original.atZone(ZoneId.of(timezone)).toLocalDate()
    }
    fun report(): JsonObject = buildJsonObject {
        put("id", id); put("device_id", deviceId); put("revision", revision); put("occurred_at", occurredAt)
        put("command", command); put("status", status); put("reason", reason.take(1000)); put("stage", stage); put("retryable", retryable)
        putJsonArray("request_ids") { requestIds.forEach { add(it) } }
        putJsonArray("history") { history.forEach { event -> add(buildJsonObject {
            put("at", event.at); put("command", event.command); put("reason", event.reason.take(1000))
        }) } }
    }
}

data class QuickRetryPreview(val failureId: String, val result: QuickInterpretation, val fingerprint: String, val title: String)
class SpeechCaptureException(val code: Int) : Exception("Speech recognition failed")

/** No exception messages/URLs/headers: only class names and source locations leave the device. */
fun quickDiagnostic(error: Throwable, stage: String, requestId: String?, appVersion: String, androidVersion: String): JsonObject = buildJsonObject {
    put("id", UUID.randomUUID().toString()); put("occurred_at", Instant.now().toString()); put("stage", stage)
    put("request_id", requestId); put("exception_type", (error.cause ?: error).javaClass.name)
    put("app_version", appVersion); put("android_version", androidVersion)
    if (error is SpeechCaptureException) put("error_code", error.code)
    if (error is com.timebox.android.ui.assistant.AssistantHttpException) put("http_status", error.statusCode)
    putJsonArray("frames") { (error.cause ?: error).stackTrace.take(40).forEach { add("${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})".take(300)) } }
}
