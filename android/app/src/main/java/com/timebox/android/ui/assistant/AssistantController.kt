package com.timebox.android.ui.assistant

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

data class AssistantExchange(val question: String, val answer: String = "", val status: String = "", val error: String? = null, val plan: AssistantPlan? = null,
                             val proposal: TrackingProposal? = null, val cards: List<AssistantCard> = emptyList(),
                             val taskProposal: TaskChangeProposal? = null, val taskCards: List<AssistantTaskCard> = emptyList(),
                             val cardOrder: List<String> = emptyList())
data class AssistantState(val exchanges: List<AssistantExchange> = emptyList(), val busy: Boolean = false, val readingPlan: Boolean = false, val ended: String? = null,
                          /** Tracking Proposal card states by proposal id, retained with the conversation. */
                          val proposals: Map<String, ProposalState> = emptyMap(),
                          val taskChanges: Map<String, TaskChangeState> = emptyMap(),
                          val refreshedTaskProposals: List<TaskChangeProposal> = emptyList())

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
        if (state.value.busy || state.value.ended != null || message.isBlank() || message.length > 4000) return
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
                completedRun?.let { api.acknowledge(id, it); completedRun = null }
                var sequence = 0
                api.stream(id, runId, message).collect { event ->
                    if (epoch != generation || run != runId) return@collect
                    if (event.data["run_id"]?.jsonPrimitive?.content != runId) return@collect
                    val next = event.data.integer("sequence")
                    check(next == sequence + 1) { "Interrupted stream" }
                    sequence = next
                    check(!completed) { "Event after completion" }
                    when (event.kind) {
                        "started" -> check(sequence == 1) { "Invalid stream start" }
                        "plan_card" -> {
                            check(api.supportsPlanCards || api.supportsActivityCards) { "Unnegotiated plan card" }
                            check(state.value.exchanges.last().let { (it.cards.size + it.taskCards.size) < (if (api.supportsActivityCards) 3 else 1) && it.answer.isEmpty() }) { "Invalid card order" }
                            val card = AssistantCard.parse(event.data)
                            check(api.supportsActivityCards || card.legacy != null) { "Unnegotiated card version" }
                            check(state.value.exchanges.last().let { e -> e.cards.none { it.id == card.id } && e.taskCards.none { it.id == card.id } }) { "Duplicate card" }
                            updateLast { it.copy(plan = it.plan ?: card.legacy, cards = it.cards + card, cardOrder = it.cardOrder + card.id) }
                        }
                        "task_card" -> {
                            check(state.value.exchanges.last().let { it.cards.size + it.taskCards.size < 3 && it.answer.isEmpty() }) { "Invalid card order" }
                            val card = AssistantTaskCard.parse(event.data)
                            check(state.value.exchanges.last().let { e -> e.cards.none { it.id == card.id } && e.taskCards.none { it.id == card.id } }) { "Duplicate card" }
                            updateLast { it.copy(taskCards = it.taskCards + card, cardOrder = it.cardOrder + card.id) }
                        }
                        "task_proposal" -> {
                            check(state.value.exchanges.last().let { it.cards.isEmpty() && it.taskCards.isEmpty() && it.proposal == null && it.taskProposal == null && it.answer.isEmpty() }) { "Invalid proposal order" }
                            val proposal = TaskChangeProposal.parse(event.data)
                            check(proposal.conversationId == id && proposal.runId == runId && proposal.status == "draft") { "Invalid proposal identity" }
                            updateLast { it.copy(taskProposal = proposal) }
                            updateTask(proposal.operationId, TaskChangeState())
                        }
                        "tracking_proposal" -> {
                            check(api.supportsTrackingProposals) { "Unnegotiated tracking proposal" }
                            check(state.value.exchanges.last().let { it.cards.isEmpty() && it.taskCards.isEmpty() && it.proposal == null && it.taskProposal == null && it.answer.isEmpty() }) { "Invalid card order" }
                            val proposal = TrackingProposal.parse(event.data)
                            updateLast { it.copy(proposal = proposal) }
                            mutableState.value = state.value.let { it.copy(proposals = it.proposals + (proposal.id to ProposalState())) }
                        }
                        "text_delta" -> updateLast { it.copy(answer = it.answer + event.data.getValue("text").jsonPrimitive.content) }
                        "tool_started" -> mutableState.value = state.value.copy(readingPlan = true)
                        "tool_completed" -> mutableState.value = state.value.copy(readingPlan = false)
                        "completed" -> {
                            check(state.value.exchanges.last().let { it.answer.isNotBlank() || it.cards.isNotEmpty() || it.taskCards.isNotEmpty() || it.proposal != null || it.taskProposal != null }) { "Empty response" }
                            completed = true
                        }
                        "failed" -> throw java.io.IOException(event.data.getValue("message").jsonPrimitive.content)
                        "stopped" -> { state.value.exchanges.last().taskProposal?.let { updateTask(it.operationId, TaskChangeState(status = "invalid")) }; updateLast { it.copy(status = "Stopped") }; throw CancellationException("Stopped") }
                        else -> error("Unsupported Assistant event")
                    }
                }
                if (!completed) throw java.io.IOException("Connection lost before the response finished. Please retry.")
                completedRun = runId
                updateLast { it.copy(status = "Complete") }
                state.value.exchanges.last().taskProposal?.let { proposal ->
                    // Only a saved terminal response activates a draft. HTTP owns later execution state.
                    mutableState.value = state.value.copy(taskChanges = state.value.taskChanges.mapValues { (_, value) ->
                        if (value.status == "pending") value.copy(status = "replaced") else value
                    })
                    updateTask(proposal.operationId, TaskChangeState(status = "pending", sourceCompleted = true))
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (epoch == generation && run == runId) {
                    state.value.exchanges.last().taskProposal?.let { updateTask(it.operationId, TaskChangeState(status = "invalid")) }
                    if (error is AssistantEndedException) mutableState.value = state.value.copy(ended = error.message)
                    updateLast {
                    it.copy(status = "Interrupted", error = if (error is java.io.IOException) error.message else "Response interrupted. Please retry.")
                    }
                }
            } finally {
                if (epoch == generation && run == runId) {
                    mutableState.value = state.value.copy(busy = false, readingPlan = false)
                    run = null
                }
            }
        }
    }

    private fun updateTask(operation: String, value: TaskChangeState) {
        mutableState.value = state.value.copy(taskChanges = state.value.taskChanges + (operation to value))
    }

    suspend fun descriptionReview(proposal: TaskChangeProposal): TaskChangeProposal {
        check(proposal.descriptionReview)
        val full = (transport ?: error("Assistant unavailable")).taskReview(proposal.id)
        check(full.id == proposal.id && full.operationId == proposal.operationId && full.contentHash == proposal.contentHash && full.conversationId == proposal.conversationId && full.runId == proposal.runId)
        return full
    }

    fun checkTask(proposal: TaskChangeProposal) = taskAction(proposal) { api -> api.taskStatus(proposal.operationId) }
    fun dismissTask(proposal: TaskChangeProposal) = taskAction(proposal) { api -> api.dismissTask(proposal.id) }

    private fun taskAction(proposal: TaskChangeProposal, action: suspend (AssistantTransport) -> TaskOperationResult?) {
        val current = state.value.taskChanges[proposal.operationId] ?: return
        if (current.busy) return
        val epoch = generation
        updateTask(proposal.operationId, current.copy(busy = true, error = null))
        scope.launch {
            try {
                val result = action(transport ?: error("Assistant unavailable"))
                if (epoch != generation) return@launch
                check(result == null || (result.operationId == proposal.operationId && result.proposalId == proposal.id))
                updateTask(proposal.operationId, current.copy(status = result?.status ?: "checking", result = result ?: current.result))
            } catch (error: Exception) {
                if (epoch == generation) updateTask(proposal.operationId, current.copy(error = "Could not check task changes. Check your connection and try again."))
            }
        }
    }

    fun refreshTask(proposal: TaskChangeProposal) {
        val current = state.value.taskChanges[proposal.operationId] ?: return
        if (current.busy) return
        val epoch = generation
        updateTask(proposal.operationId, current.copy(busy = true, error = null))
        scope.launch {
            try {
                val next = (transport ?: error("Assistant unavailable")).refreshTask(proposal.id)
                if (epoch != generation) return@launch
                check(next.id != proposal.id && next.operationId != proposal.operationId && next.conversationId == proposal.conversationId && next.sourceCompleted && next.status == "pending")
                updateTask(proposal.operationId, current.copy(status = "replaced"))
                mutableState.value = state.value.copy(refreshedTaskProposals = state.value.refreshedTaskProposals + next)
                mutableState.value = state.value.copy(taskChanges = state.value.taskChanges.mapValues { (_, value) ->
                    if (value.status == "pending") value.copy(status = "replaced") else value
                })
                updateTask(next.operationId, TaskChangeState(status = "pending", sourceCompleted = true))
            } catch (error: Exception) {
                if (epoch == generation) updateTask(proposal.operationId, current.copy(error = "Could not refresh review. Check the result or make a new request."))
            }
        }
    }

    private fun updateProposal(id: String, change: (ProposalState) -> ProposalState) {
        val current = state.value.proposals[id] ?: return
        mutableState.value = state.value.copy(proposals = state.value.proposals + (id to change(current)))
    }

    /** A choice chip fills the card; confirming it is still a separate tap. */
    fun chooseProposal(id: String, taskTypeId: Int) = updateProposal(id) { if (it.status == ProposalStatus.Pending) it.copy(chosen = taskTypeId) else it }
    fun dismissProposal(id: String) = updateProposal(id) { if (it.status == ProposalStatus.Pending) it.copy(status = ProposalStatus.Dismissed) else it }
    fun proposalApplying(id: String, view: ProposalView?) = updateProposal(id) { it.copy(applying = view) }
    fun proposalApplied(id: String, record: com.timebox.android.ui.day.RecordRef, operationId: String?, stopped: Boolean = false) =
        updateProposal(id) { it.copy(status = ProposalStatus.Applied, record = record, operationId = operationId, stopped = stopped, applying = null) }
    /** Undo means the change never happened: the card may be confirmed again until it expires. */
    fun operationUndone(operationId: String) = state.value.proposals.filterValues { it.operationId == operationId }.keys
        .forEach { id -> updateProposal(id) { it.copy(status = ProposalStatus.Pending, record = null, operationId = null) } }

    fun stop() {
        if (!state.value.busy || state.value.exchanges.lastOrNull()?.status == "Complete") return
        val id = conversation
        val runId = run
        val api = transport
        run = null
        job?.cancel()
        state.value.exchanges.last().taskProposal?.let { updateTask(it.operationId, TaskChangeState(status = "invalid")) }
        updateLast { it.copy(status = "Stopped") }
        mutableState.value = state.value.copy(busy = false, readingPlan = false)
        if (id != null && runId != null && api != null) scope.launch { runCatching { api.stop(id, runId) } }
    }

    fun retry() {
        if (state.value.busy || state.value.ended != null) return
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
