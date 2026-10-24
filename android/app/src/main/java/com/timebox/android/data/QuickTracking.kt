package com.timebox.android.data

import com.timebox.android.data.remote.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

@Serializable data class QuickInterpretation(
    @SerialName("request_id") val requestId: String,
    val question: String,
    val source: String,
    val action: String?,
    @SerialName("task_type_id") val taskTypeId: Int? = null,
    @SerialName("effective_at") val effectiveAt: String? = null,
    @SerialName("received_at") val receivedAt: String,
    val timezone: String,
    val timing: String? = null,
    val message: String,
)

data class QuickLocalReview(val interpretation: QuickInterpretation, val rejected: Boolean)

/** Mirrors the lab's reducer while leaving interval reconciliation to the journal. */
fun quickTrackingKind(result: QuickInterpretation, snapshot: ActivitySnapshotDto, now: Instant): ActivityKind? {
    require(result.action in listOf("track", "stop")) { result.message }
    val at = Instant.parse(result.effectiveAt)
    val received = Instant.parse(result.receivedAt)
    val zone = ZoneId.of(result.timezone)
    require(snapshot.reportingTimezone == result.timezone) { "Reporting Time Zone changed. Retry the command." }
    require(at <= received && at.atZone(zone).toLocalDate() == received.atZone(zone).toLocalDate()) { "Choose a time today, no later than now." }
    require(now >= received.minusSeconds(5) && now <= received.plusSeconds(900) && now.atZone(zone).toLocalDate() == received.atZone(zone).toLocalDate()) { "This command expired. Retry it." }
    val current = snapshot.current
    if (result.action == "stop") return if (snapshot.records.any { it.endAt == null || Instant.parse(it.endAt) > at }) ActivityKind.Stop else null
    require(snapshot.taskTypes.any { it.id == result.taskTypeId }) { "Task Types changed. Retry the command." }
    if (current != null && current.taskTypeId == result.taskTypeId && current.name == null && at >= Instant.parse(current.startAt)) {
        return if (result.timing == "extract" && at > Instant.parse(current.startAt)) ActivityKind.Edit else null
    }
    return if (current == null) ActivityKind.Start else ActivityKind.Switch
}
