package com.timebox.android.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class TaskTypeRecommendationRequest(val name: String)

@Serializable
data class TaskTypeRecommendationDto(
    @SerialName("task_type_id") val taskTypeId: Int? = null,
    val confidence: Double? = null,
    val reason: String,
)
