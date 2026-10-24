package com.timebox.android.quicktracking

import android.content.Context
import com.timebox.android.data.*
import com.timebox.android.ui.assistant.HttpAssistantTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID

data class QuickCaptureState(val text: String = "", val busy: Boolean = false, val message: String? = null, val saved: Boolean = false, val recorded: Boolean = false)

/** Process-owned execution survives capture dismissal; raw drafts never execute on reconnect. */
class QuickTrackingController(context: Context, private val repository: TimeboxRepository, private val activity: ActivityRepository) {
    private val preferences = context.getSharedPreferences("quick-tracking-draft", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(QuickCaptureState(preferences.getString("text", "").orEmpty(),
        message = if (preferences.contains("text")) "A previous command was interrupted. Check Review or retry." else null))
    val state = mutableState.asStateFlow()

    fun edit(text: String) { if (!state.value.busy) mutableState.value = QuickCaptureState(text) }

    fun submit(text: String, source: String) {
        if (state.value.busy || text.isBlank()) return
        mutableState.value = QuickCaptureState(text, busy = true)
        scope.launch {
            try {
                val settings = repository.settings.first()
                val identity = settings.assistantIdentity()
                val previous = preferences.getString("request", null)
                val sameDraft = preferences.getString("text", null) == text && preferences.getString("endpoint", null) == identity
                if (sameDraft && previous != null && activity.hasQuickOperation(previous)) {
                    activity.refresh()
                    check(activity.state.value.quickReviews.none { it.interpretation.requestId == previous && it.rejected }) { "This change could not sync. Check Assistant Review; repeating it will not fix the conflict." }
                    mutableState.value = QuickCaptureState(text, message = "Already saved. Check Assistant Review.", saved = true, recorded = true)
                    preferences.edit().clear().commit()
                    return@launch
                }
                val id = UUID.randomUUID().toString()
                check(preferences.edit().putString("text", text).putString("request", id).putString("endpoint", identity).commit()) { "Could not retain your command on this device." }
                if (!activity.bootstrappedThisRun) activity.refresh()
                checkNotNull(activity.state.value.snapshot) { "Connect once to initialize Activity Tracking, then retry." }
                val fingerprint = activity.quickFingerprint()
                val current = activity.state.value.snapshot?.current
                val result = HttpAssistantTransport(settings).interpretQuick(buildJsonObject {
                    put("request_id", id); put("message", text); put("source", source)
                    if (current != null) putJsonObject("current") { put("task_type_id", current.taskTypeId); put("name", current.name) }
                })
                check(repository.settings.first().assistantIdentity() == identity) { "Server changed. Return to the original server before retrying." }
                check(result.requestId == id) { "The interpretation did not match this command." }
                check(result.action != null) { result.message }
                val message = activity.applyQuick(result, fingerprint)
                check(preferences.edit().clear().commit()) { "Saved. Check Review before repeating this command." }
                mutableState.value = QuickCaptureState(text, message = message, saved = true, recorded = activity.hasQuickOperation(result.requestId))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.value = QuickCaptureState(text, message = error.message ?: "Could not interpret. Retry or edit your command.")
            }
        }
    }
}
