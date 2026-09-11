package com.timebox.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable enum class ActivityKind {
    @SerialName("start") Start,
    @SerialName("switch") Switch,
    @SerialName("stop") Stop,
}
@Serializable enum class ActivityOutcome {
    @SerialName("applied") Applied,
    @SerialName("conflict") Conflict,
    @SerialName("superseded") Superseded,
}

@Serializable data class ActivityCalibrationDto(
    @SerialName("server_at") val serverAt: String,
    @SerialName("offset_ms") val offsetMs: Long,
)
@Serializable data class ActivityEffectiveDto(val mode: String, val at: String? = null)
@Serializable data class ActivityCommandDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("device_id") val deviceId: String,
    val sequence: Int,
    @SerialName("action_at") val actionAt: String,
    val calibration: ActivityCalibrationDto,
    @SerialName("base_cursor") val baseCursor: Int,
    val effective: ActivityEffectiveDto,
    @SerialName("target_id") val targetId: Int?,
    val kind: ActivityKind,
    @SerialName("task_type_id") val taskTypeId: Int? = null,
    val name: String? = null,
    @SerialName("task_id") val taskId: Int? = null,
    @SerialName("planned_block_id") val plannedBlockId: Int? = null,
    val note: String? = null,
    @SerialName("selection_snapshot") val selectionSnapshot: Boolean = false,
    @SerialName("predecessor_id") val predecessorId: String? = null,
)
@Serializable data class ActivityAcknowledgementDto(
    @SerialName("operation_id") val operationId: String,
    val outcome: ActivityOutcome,
)
@Serializable data class ActivitySnapshotDto(
    val protocol: String = "activity-online-v1",
    val cursor: Int,
    @SerialName("server_at") val serverAt: String,
    @SerialName("reporting_timezone") val reportingTimezone: String,
    val current: ActualBlockDto?,
    val records: List<ActualBlockDto>,
    val acknowledgement: ActivityAcknowledgementDto? = null,
    @SerialName("task_types") val taskTypes: List<TaskTypeDto> = emptyList(),
    @SerialName("offline_ready") val offlineReady: Boolean = false,
    @SerialName("operation_outcomes") val operationOutcomes: Map<String, ActivityOperationOutcomeDto> = emptyMap(),
    val plans: List<ActivityPlanDto> = emptyList(),
    val coverage: List<ActivityCoverageDto> = emptyList(),
)
@Serializable data class ActivityCoverageDto(
    val start: String, val end: String?,
    @SerialName("record_id") val recordId: Int?,
    val order: List<kotlinx.serialization.json.JsonPrimitive>,
)
@Serializable data class ActivityOperationOutcomeDto(
    @SerialName("device_id") val deviceId: String,
    val outcome: ActivityOutcome,
)

@Serializable data class ActivityPlanDto(
    val id: Int,
    @SerialName("task_type_id") val taskTypeId: Int,
    @SerialName("task_id") val taskId: Int? = null,
    val name: String? = null, val note: String? = null,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
)
