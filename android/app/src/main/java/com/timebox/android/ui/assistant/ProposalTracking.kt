package com.timebox.android.ui.assistant

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.activityIdentityText
import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.remote.ActivityKind
import com.timebox.android.ui.day.DayLanding
import com.timebox.android.ui.day.RecordRef
import com.timebox.android.ui.day.TrackingHandoff
import com.timebox.android.ui.day.TrackingSheetRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Applies confirmed Tracking Proposals through the ordinary activity journal (ADR 0014), so confirmation works
 * offline and gets the same Undo as a manual change. The card's live view comes from local tracking state.
 */
class ProposalTracking internal constructor(
    private val controller: AssistantController,
    private val repository: ActivityRepository,
    private val handoff: TrackingHandoff,
    private val onOpenDay: (LocalDate) -> Unit,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    internal var now by mutableStateOf(repository.now())
    private var confirming by mutableStateOf<String?>(null)
    private var errors by mutableStateOf(emptyMap<String, String>())

    private fun zone() = runCatching { ZoneId.of(repository.state.value.snapshot!!.reportingTimezone) }.getOrDefault(ZoneId.systemDefault())

    private fun confirm(p: TrackingProposal, view: ProposalView) {
        val snapshot = repository.state.value.snapshot ?: return
        val state = controller.state.value.proposals[p.id] ?: return
        val typeId = state.chosen ?: p.taskTypes.singleOrNull()?.id
        val running = snapshot.current
        confirming = p.id
        errors = errors - p.id
        scope.launch {
            try {
                var operation: String? = null
                val saved = withContext(Dispatchers.IO) {
                    when (view.action) {
                        ProposalAction.Stop -> repository.command(ActivityKind.Stop, effectiveAt = p.at, observedTargetId = running?.id)
                        ProposalAction.Start -> repository.command(ActivityKind.Start, typeId, p.blockName, effectiveAt = p.at, onOperation = { operation = it })
                        ProposalAction.Switch -> repository.command(ActivityKind.Switch, typeId, p.blockName, effectiveAt = p.at,
                            observedTargetId = running?.id, onOperation = { operation = it })
                    }
                }
                val after = repository.state.value.snapshot
                if (!saved || after == null) { errors = errors + (p.id to (repository.state.value.error ?: "Could not apply this change.")); return@launch }
                val record = if (view.action == ProposalAction.Stop) running?.let { RecordRef.of(it, snapshot) }
                    else after.records.find { after.provenance[it.id.toString()] == operation }?.let { RecordRef.of(it, after) }
                    ?: operation?.let { RecordRef(it, p.at ?: repository.now()) }
                if (record != null) controller.proposalApplied(p.id, record, operation, stopped = view.action == ProposalAction.Stop)
            } finally { confirming = null }
        }
    }

    private fun viewInDay(p: TrackingProposal, record: com.timebox.android.data.remote.ActualBlockDto) {
        val snapshot = repository.state.value.snapshot ?: return
        val zone = zone()
        val start = parseActivityInstant(record.startAt)
        val name = activityIdentityText(record.name, record.task?.title, record.taskType.name)
        handoff.land(DayLanding(RecordRef.of(record, snapshot), "$name · from ${clockLabel(start, zone)}"))
        onOpenDay((if (record.endAt == null) now else start).atZone(zone).toLocalDate())
    }

    private fun change(p: TrackingProposal) {
        val state = controller.state.value.proposals[p.id] ?: return
        handoff.requestSheet(TrackingSheetRequest(state.chosen ?: p.taskTypes.singleOrNull()?.id, p.blockName, p.at, p.id))
        onOpenDay(now.atZone(zone()).toLocalDate())
    }

    private val actions = ProposalCardActions(
        onConfirm = ::confirm,
        onDismiss = { controller.dismissProposal(it.id) },
        onChange = ::change,
        onChoose = { p, id -> controller.chooseProposal(p.id, id) },
        onViewDay = ::viewInDay,
    )

    @Composable
    fun Card(p: TrackingProposal) {
        val activity by repository.state.collectAsState()
        val states by controller.state.collectAsState()
        TrackingProposalCard(p, states.proposals[p.id] ?: ProposalState(), activity.snapshot, now, zone(),
            busy = confirming == p.id || activity.busy, error = errors[p.id], actions = actions)
    }
}

/** Null where Activity Tracking is unavailable, such as isolated screen tests. */
@Composable
internal fun rememberProposalTracking(controller: AssistantController, state: AssistantState, onOpenDay: (LocalDate) -> Unit): ProposalTracking? {
    val app = LocalContext.current.applicationContext as? TimeboxApplication ?: return null
    val scope = rememberCoroutineScope()
    val open by rememberUpdatedState(onOpenDay)
    val tracking = remember(controller) { ProposalTracking(controller, app.activityRepository, app.trackingHandoff, { open(it) }, scope) }
    val cards = state.exchanges.any { it.proposal != null }
    LaunchedEffect(cards) {
        if (!cards) return@LaunchedEffect
        // Cards are live: elapsed time ticks and the impact follows tracking changes made elsewhere.
        launch { while (true) { tracking.now = app.activityRepository.now(); delay(1000) } }
        launch { while (true) { withContext(Dispatchers.IO) { app.activityRepository.refresh() }; delay(15_000) } }
    }
    return tracking
}
