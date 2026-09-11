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
}

@Serializable data class ActivityCalibrationDto(
    @SerialName("server_at") val serverAt: String,
    @SerialName("offset_ms") val offsetMs: Long,
)
@Serializable data class ActivityEffectiveDto(val mode: String)
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
)
