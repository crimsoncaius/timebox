package com.timebox.android.ui.assistant

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

data class AssistantExchange(val question: String, val answer: String = "", val status: String = "", val error: String? = null)
data class AssistantState(val exchanges: List<AssistantExchange> = emptyList(), val busy: Boolean = false, val readingPlan: Boolean = false)

/** Process-owned: tab navigation and activity recreation do not cancel a response. */
class AssistantController(
    private val scope: CoroutineScope,
    private val transportFactory: suspend () -> AssistantTransport,
) {
    private val mutableState = MutableStateFlow(AssistantState())
    val state = mutableState.asStateFlow()
    private var conversation: String? = null
    private var transport: AssistantTransport? = null
    private var run: String? = null
    private var completedRun: String? = null
    private var job: Job? = null
    private var generation = 0

    private fun updateLast(change: (AssistantExchange) -> AssistantExchange) {
        val current = mutableState.value
        mutableState.value = current.copy(exchanges = current.exchanges.dropLast(1) + change(current.exchanges.last()))
    }

    fun send(message: String) {
        if (state.value.busy || message.isBlank() || message.length > 4000) return
        val epoch = generation
        val runId = UUID.randomUUID().toString()
        run = runId
        mutableState.value = state.value.copy(exchanges = state.value.exchanges + AssistantExchange(message), busy = true)
        job = scope.launch {
            var completed = false
            try {
                val api = transport ?: transportFactory().also { transport = it }
                val id = conversation ?: api.create().also {
                    if (epoch != generation) { api.delete(it); return@launch }
                    conversation = it
                }
                // Confirm the previous terminal event before starting another turn.
                // A lost acknowledgement is safe to repeat; generation is never retried.
                completedRun?.let { api.acknowledge(id, it) }
                var sequence = 0
                api.stream(id, runId, message).collect { event ->
                    if (epoch != generation || run != runId) return@collect
                    if (event.data["run_id"]?.jsonPrimitive?.content != runId) return@collect
                    val next = event.data["sequence"]?.jsonPrimitive?.content?.toIntOrNull() ?: error("Invalid stream")
                    check(next == sequence + 1) { "Interrupted stream" }
                    sequence = next
                    when (event.kind) {
                        "text_delta" -> updateLast { it.copy(answer = it.answer + event.data.getValue("text").jsonPrimitive.content) }
                        "tool_started" -> mutableState.value = state.value.copy(readingPlan = true)
                        "tool_completed" -> mutableState.value = state.value.copy(readingPlan = false)
                        "completed" -> {
                            completedRun = runId
                            completed = true
                            updateLast { it.copy(status = "Complete") }
                        }
                        "failed" -> throw java.io.IOException(event.data.getValue("message").jsonPrimitive.content)
                        "stopped" -> { updateLast { it.copy(status = "Stopped") }; throw CancellationException("Stopped") }
                    }
                }
                if (!completed) throw java.io.IOException("Connection lost before the response finished. Please retry.")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (!completed && epoch == generation && run == runId) updateLast {
                    it.copy(status = "Interrupted", error = if (error is java.io.IOException) error.message else "Response interrupted. Please retry.")
                }
            } finally {
                if (epoch == generation && run == runId) {
                    mutableState.value = state.value.copy(busy = false, readingPlan = false)
                    run = null
                }
            }
        }
    }

    fun stop() {
        if (!state.value.busy || state.value.exchanges.lastOrNull()?.status == "Complete") return
        val id = conversation
        val runId = run
        val api = transport
        run = null
        job?.cancel()
        updateLast { it.copy(status = "Stopped") }
        mutableState.value = state.value.copy(busy = false, readingPlan = false)
        if (id != null && runId != null && api != null) scope.launch { runCatching { api.stop(id, runId) } }
    }

    fun retry() {
        if (state.value.busy) return
        val last = state.value.exchanges.lastOrNull() ?: return
        if (last.status !in listOf("Stopped", "Interrupted")) return
        send(last.question)
    }

    fun newConversation() {
        generation++
        job?.cancel()
        val old = conversation
        val api = transport
        conversation = null; transport = null; run = null; completedRun = null
        mutableState.value = AssistantState()
        if (old != null && api != null) scope.launch { runCatching { api.delete(old) } }
    }
}
