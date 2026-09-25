package com.timebox.android.ui.assistant

import com.timebox.android.data.activityIdentityText
import com.timebox.android.data.identityText
import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.remote.ActivitySnapshotDto
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.ui.day.RecordRef
import kotlinx.serialization.json.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ProposalTaskType(val id: Int, val path: String)

/** An Assistant-suggested tracking change. It takes effect only when the user confirms it (ADR 0014). */
data class TrackingProposal(
    val id: String, val stop: Boolean, val taskTypes: List<ProposalTaskType>, val blockName: String?,
    /** Start (track) or end (stop); null means the confirmation instant. */
    val at: Instant?, val proposedAt: Instant, val expiresAt: Instant,
) {
    companion object {
        fun parse(data: JsonObject): TrackingProposal {
            fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.let { check(it.isString); it.content }
            fun JsonObject.optional(key: String): String? = if (getValue(key) == JsonNull) null else text(key)
            check(data.getValue("schema_version").jsonPrimitive.let { !it.isString && it.int == 1 })
            val action = data.text("action")
            check(action == "track" || action == "stop")
            val types = data.getValue("task_types").jsonArray.map { value ->
                val row = value.jsonObject
                ProposalTaskType(row.getValue("id").jsonPrimitive.let { check(!it.isString); it.int }, row.text("path"))
            }
            check(if (action == "track") types.size in 1..4 else types.isEmpty())
            return TrackingProposal(data.text("proposal_id").also { check(it.isNotBlank()) }, action == "stop", types,
                data.optional("block_name"), data.optional("at")?.let(Instant::parse),
                Instant.parse(data.text("proposed_at")), Instant.parse(data.text("expires_at")))
        }
    }
}

enum class ProposalStatus { Pending, Applied, Dismissed }

/** The card's own state. Applied cards follow [record]; Undo of [operationId] returns the card to Pending. */
data class ProposalState(
    val chosen: Int? = null, val status: ProposalStatus = ProposalStatus.Pending,
    val record: RecordRef? = null, val operationId: String? = null, val stopped: Boolean = false,
)

enum class ProposalAction(val label: String, val progress: String) { Start("Start", "Starting…"), Switch("Switch", "Switching…"), Stop("Stop", "Stopping…") }

/** What a pending card shows, computed from local tracking state each time it changes. */
data class ProposalView(
    val action: ProposalAction, val title: String?, val invalid: String?, val impact: List<String>,
    /** Earlier recorded time is replaced or unrecorded time filled: show the Now/After strip. */
    val reachesBack: Boolean,
)

private val clock = DateTimeFormatter.ofPattern("HH:mm")
internal fun clockLabel(at: Instant, zone: ZoneId): String = at.atZone(zone).format(clock)
internal fun spanLabel(duration: Duration): String = duration.toMinutes().coerceAtLeast(0).let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }

fun deriveProposal(p: TrackingProposal, state: ProposalState, snapshot: ActivitySnapshotDto, now: Instant, zone: ZoneId): ProposalView {
    val running = snapshot.current
    if (p.stop) {
        val at = p.at ?: now
        return when {
            running == null -> ProposalView(ProposalAction.Stop, "Stop tracking", "Nothing is being tracked.", emptyList(), false)
            at < parseActivityInstant(running.startAt) -> ProposalView(ProposalAction.Stop, "Stop ${running.identityText()}",
                "${running.identityText()} started at ${clockLabel(parseActivityInstant(running.startAt), zone)}, after ${clockLabel(at, zone)}.", emptyList(), false)
            else -> ProposalView(ProposalAction.Stop, "Stop ${running.identityText()}", null,
                listOf("${running.identityText()} ends at ${clockLabel(at, zone)} · ${spanLabel(Duration.between(parseActivityInstant(running.startAt), at))}"), false)
        }
    }
    val action = if (running == null) ProposalAction.Start else ProposalAction.Switch
    val type = p.taskTypes.find { it.id == (state.chosen ?: p.taskTypes.singleOrNull()?.id) }
        ?: return ProposalView(action, null, null, emptyList(), false)
    val title = activityIdentityText(p.blockName, null, type.path)
    val sameActivity = running != null && running.taskTypeId == type.id && (p.blockName == null || p.blockName == running.name)
    if (sameActivity && (p.at == null || p.at >= parseActivityInstant(running!!.startAt)))
        return ProposalView(action, title, "You're already tracking ${running!!.identityText()}.", emptyList(), false)
    if (p.at != null && p.at > now) return ProposalView(action, title, "That time is in the future.", emptyList(), false)
    if (p.at != null && (if (running == null) !snapshot.startHistoryReady else p.at < parseActivityInstant(running.startAt) && !snapshot.switchHistoryReady))
        return ProposalView(action, title, "This server can't ${action.label.lowercase()} from an earlier time.", emptyList(), false)
    val at = p.at ?: now
    fun end(record: ActualBlockDto) = record.endAt?.let(::parseActivityInstant) ?: now
    // A running record is affected even by a switch "from now": it ends at that instant.
    val affected = snapshot.records.filter { end(it) > at || (it.endAt == null && at >= now) }
    val impact = affected.map { r ->
        if (parseActivityInstant(r.startAt) >= at) "${r.identityText()} replaced entirely" else "${r.identityText()} ends at ${clockLabel(at, zone)}"
    }.toMutableList()
    val covered = affected.sumOf { Duration.between(maxOf(at, parseActivityInstant(it.startAt)), maxOf(at, end(it))).toMinutes() }
    val gap = Duration.between(at, now).toMinutes() - covered
    if (p.at != null && gap > 0) impact += "$gap min of unrecorded time filled"
    if (impact.isEmpty()) impact += "Starts at ${clockLabel(at, zone)}"
    val reachesBack = p.at != null && p.at < now &&
        (running == null || p.at < parseActivityInstant(running.startAt) || affected.any { it.id != running.id } || gap > 0)
    return ProposalView(action, title, null, impact, reachesBack)
}

/** What an applied card shows: the followed block while it runs, then its final range. */
fun appliedLine(state: ProposalState, snapshot: ActivitySnapshotDto?, now: Instant, zone: ZoneId): Pair<String, ActualBlockDto?> {
    val record = snapshot?.let { state.record?.find(it) } ?: return "Applied · since replaced" to null
    val start = parseActivityInstant(record.startAt)
    val end = record.endAt?.let(::parseActivityInstant)
    return when {
        state.stopped -> "Stopped at ${clockLabel(end ?: now, zone)}"
        end == null -> "Tracking since ${clockLabel(start, zone)} · ${spanLabel(Duration.between(start, now))}"
        else -> "${clockLabel(start, zone)}–${clockLabel(end, zone)} · ${spanLabel(Duration.between(start, end))}"
    } to record
}
