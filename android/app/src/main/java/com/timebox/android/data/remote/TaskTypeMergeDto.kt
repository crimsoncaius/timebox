package com.timebox.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskTypeMergeRequest(
    @SerialName("target_id") val targetId: Int,
    @SerialName("preview_token") val previewToken: String? = null,
)

@Serializable
data class TaskTypeMergeChange(
    @SerialName("source_id") val sourceId: Int,
    @SerialName("source_name") val sourceName: String,
    @SerialName("target_name") val targetName: String,
    val action: String,
)

@Serializable
data class TaskTypeMergePreview(
    @SerialName("source_id") val sourceId: Int,
    @SerialName("source_name") val sourceName: String,
    @SerialName("target_id") val targetId: Int,
    @SerialName("target_name") val targetName: String,
    @SerialName("preview_token") val previewToken: String,
    val changes: List<TaskTypeMergeChange>,
    @SerialName("task_count") val taskCount: Int,
    @SerialName("completed_task_count") val completedTaskCount: Int,
    @SerialName("archived_task_count") val archivedTaskCount: Int,
    @SerialName("trashed_task_count") val trashedTaskCount: Int,
    @SerialName("planned_block_count") val plannedBlockCount: Int,
    @SerialName("actual_block_count") val actualBlockCount: Int,
    @SerialName("recurring_series_count") val recurringSeriesCount: Int,
)
