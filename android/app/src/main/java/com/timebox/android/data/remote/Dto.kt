package com.timebox.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskTypeDto(
    val id: Int,
    val name: String,
    @SerialName("usage_count") val usageCount: Int = 0,
    @SerialName("task_usage_count") val taskUsageCount: Int = 0,
    @SerialName("recurring_template_usage_count") val recurringTemplateUsageCount: Int = 0,
)

@Serializable
data class LinkedTaskDto(
    val id: Int,
    val title: String,
    val status: String,
    @SerialName("task_type_id") val taskTypeId: Int? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class TimeBlockDto(
    val id: Int,
    val lane: String,
    @SerialName("task_type_id") val taskTypeId: Int,
    @SerialName("task_type") val taskType: TaskTypeDto,
    @SerialName("task_id") val taskId: Int? = null,
    val task: LinkedTaskDto? = null,
    val note: String? = null,
    @SerialName("planned_block_id") val plannedBlockId: Int? = null,
    @SerialName("start_minute") val startMinute: Int,
    @SerialName("end_minute") val endMinute: Int,
)

@Serializable
data class DayMetaDto(
    val timezone: String,
    val today: String,
    @SerialName("server_now_iso") val serverNowIso: String,
)

@Serializable
data class DayDto(
    val id: Int,
    val date: String,
    @SerialName("start_hour") val startHour: Int,
    @SerialName("end_hour") val endHour: Int,
    @SerialName("show_full_day") val showFullDay: Boolean,
    @SerialName("time_blocks") val timeBlocks: List<TimeBlockDto>,
    val meta: DayMetaDto,
)

@Serializable
data class DayPreviewDto(
    val date: String,
    @SerialName("start_hour") val startHour: Int,
    @SerialName("end_hour") val endHour: Int,
    @SerialName("show_full_day") val showFullDay: Boolean,
    @SerialName("time_blocks") val timeBlocks: List<TimeBlockDto>,
    val meta: DayMetaDto,
)

@Serializable
data class DayListItemDto(
    val id: Int,
    val date: String,
    @SerialName("start_hour") val startHour: Int,
    @SerialName("end_hour") val endHour: Int,
    @SerialName("show_full_day") val showFullDay: Boolean,
    @SerialName("block_count") val blockCount: Int,
)

@Serializable
data class DaySummaryRowDto(
    @SerialName("task_type_id") val taskTypeId: Int,
    @SerialName("task_type_name") val taskTypeName: String,
    @SerialName("planned_minutes") val plannedMinutes: Int,
    @SerialName("actual_minutes") val actualMinutes: Int,
)

@Serializable
data class DaySummaryDto(
    val date: String,
    @SerialName("planned_minutes") val plannedMinutes: Int,
    @SerialName("actual_minutes") val actualMinutes: Int,
    val rows: List<DaySummaryRowDto>,
    val meta: DayMetaDto,
)

@Serializable
data class SettingsDto(
    @SerialName("start_hour") val startHour: Int,
    @SerialName("end_hour") val endHour: Int,
    @SerialName("show_full_day") val showFullDay: Boolean,
)

@Serializable
data class SettingsPatchDto(
    @SerialName("start_hour") val startHour: Int? = null,
    @SerialName("end_hour") val endHour: Int? = null,
    @SerialName("show_full_day") val showFullDay: Boolean? = null,
)

@Serializable
data class TimeBlockCreateDto(
    val lane: String,
    @SerialName("task_type_id") val taskTypeId: Int,
    @SerialName("task_id") val taskId: Int? = null,
    val note: String? = null,
    @SerialName("start_minute") val startMinute: Int,
    @SerialName("end_minute") val endMinute: Int,
)

@Serializable
data class PlanningPlacementDto(
    val date: String,
    @SerialName("task_id") val taskId: Int,
    @SerialName("task_type_id") val taskTypeId: Int,
    @SerialName("start_minute") val startMinute: Int,
    @SerialName("end_minute") val endMinute: Int,
)

@Serializable
data class PlanningCommitDto(val placements: List<PlanningPlacementDto>)

@Serializable
data class PlanningCommitResponseDto(val days: List<DayDto>)

@Serializable
data class TaskTypeCreateDto(val name: String)
