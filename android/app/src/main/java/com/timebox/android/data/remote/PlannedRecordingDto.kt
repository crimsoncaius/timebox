package com.timebox.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlannedRecordingRequest(val until: String? = null, val fingerprint: String? = null)

@Serializable
data class RecordingReplacement(val name: String? = null, val note: String? = null)

@Serializable
data class PlannedRecordingDto(
    val status: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    val fingerprint: String,
    val stale: Boolean = false,
    val conflicts: List<ActualBlockDto> = emptyList(),
    val replacement: RecordingReplacement,
    @SerialName("actual_block") val actualBlock: ActualBlockDto? = null,
    @SerialName("undo_token") val undoToken: String? = null,
)

@Serializable
data class RecordingUndoRequest(@SerialName("undo_token") val undoToken: String)
