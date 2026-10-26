package com.timebox.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TimeGoalWriteDto(
    @SerialName("task_type_id") val taskTypeId: Int,
    val unit: String,
    val interval: Int,
    @SerialName("target_minutes") val targetMinutes: Int,
    @SerialName("start_date") val startDate: String? = null,
)

@Serializable
data class TimeGoalTargetDto(@SerialName("target_minutes") val targetMinutes: Int)

@Serializable
data class GoalBlockDto(
    val id: Int,
    @SerialName("task_type") val taskType: String,
    val name: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("credited_seconds") val creditedSeconds: Double,
)

@Serializable
data class GoalPeriodDto(
    val start: String,
    val end: String,
    @SerialName("target_minutes") val targetMinutes: Int,
    @SerialName("duration_seconds") val durationSeconds: Double,
    val outcome: String,
    val blocks: List<GoalBlockDto>,
)

@Serializable
data class TimeGoalDto(
    val id: Int,
    @SerialName("task_type_id") val taskTypeId: Int,
    @SerialName("task_type") val taskType: String,
    val unit: String,
    val interval: Int,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("target_minutes") val targetMinutes: Int,
    @SerialName("next_target_minutes") val nextTargetMinutes: Int? = null,
    @SerialName("next_target_date") val nextTargetDate: String,
    val period: GoalPeriodDto,
    val days: Map<String, Double>,
)

@Serializable
data class TimeGoalsWeekDto(
    val today: String,
    @SerialName("week_start") val weekStart: String,
    @SerialName("earliest_week_start") val earliestWeekStart: String,
    val timezone: String,
    @SerialName("captured_at") val capturedAt: String,
    val goals: List<TimeGoalDto>,
)
