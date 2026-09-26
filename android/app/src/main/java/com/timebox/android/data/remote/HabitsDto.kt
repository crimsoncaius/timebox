package com.timebox.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HabitDayDto(
    val date: String,
    val state: String,
    val count: Int = 0,
    val target: Int? = null,
    val tickable: Boolean = false,
)

@Serializable
data class HabitTotalDto(
    val done: Int,
    val target: Int,
    val unit: String,
    val month: String? = null,
    val tone: String,
)

@Serializable
data class HabitDto(
    @SerialName("template_id") val templateId: Int,
    val title: String,
    val mode: String,
    val status: String,
    val frequency: String,
    val interval: Int,
    val weekdays: List<Int> = emptyList(),
    @SerialName("month_day") val monthDay: Int? = null,
    @SerialName("quota_count") val quotaCount: Int? = null,
    val days: List<HabitDayDto>,
    val total: HabitTotalDto,
)

@Serializable
data class HabitsWeekDto(
    val today: String,
    @SerialName("week_start") val weekStart: String,
    @SerialName("earliest_week_start") val earliestWeekStart: String,
    val habits: List<HabitDto>,
)
