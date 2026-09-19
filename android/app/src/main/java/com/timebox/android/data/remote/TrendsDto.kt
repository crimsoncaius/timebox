package com.timebox.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrendNodeDto(
    val path: String,
    val name: String,
    @SerialName("duration_seconds") val durationSeconds: Double,
    @SerialName("direct_seconds") val directSeconds: Double,
    val days: Map<String, Double>,
    @SerialName("direct_days") val directDays: Map<String, Double>,
    val children: List<TrendNodeDto> = emptyList(),
)

@Serializable
data class TrendsDto(
    val start: String,
    val end: String,
    val today: String,
    val timezone: String,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("duration_seconds") val durationSeconds: Double,
    val types: List<TrendNodeDto>,
)
