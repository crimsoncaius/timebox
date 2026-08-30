package com.timebox.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDto(
    val id: Int,
    val name: String,
    val description: String,
    @SerialName("deadline_date") val deadlineDate: String? = null,
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ProjectCreateDto(
    val name: String,
    val description: String = "",
    @SerialName("deadline_date") val deadlineDate: String? = null,
    @SerialName("deadline_at") val deadlineAt: String? = null,
)

@Serializable
data class BattleTaskDto(
    val id: Int,
    @SerialName("parent_id") val parentId: Int? = null,
    @SerialName("parent_title") val parentTitle: String? = null,
    @SerialName("project_id") val projectId: Int? = null,
    val project: ProjectDto? = null,
    @SerialName("task_type_id") val taskTypeId: Int? = null,
    @SerialName("task_type") val taskType: TaskTypeDto? = null,
    @SerialName("recurring_template_id") val recurringTemplateId: Int? = null,
    @SerialName("recurring_template_title") val recurringTemplateTitle: String? = null,
    @SerialName("occurrence_key") val occurrenceKey: String? = null,
    @SerialName("recurrence_kind") val recurrenceKind: String? = null,
    @SerialName("quota_period_start") val quotaPeriodStart: String? = null,
    @SerialName("quota_period_end") val quotaPeriodEnd: String? = null,
    @SerialName("expected_sessions") val expectedSessions: Int? = null,
    @SerialName("session_index") val sessionIndex: Int? = null,
    @SerialName("quota_completed") val quotaCompleted: Int? = null,
    val title: String,
    val description: String,
    @SerialName("ready_to_plan") val readyToPlan: Boolean,
    @SerialName("is_blocked") val isBlocked: Boolean = false,
    @SerialName("blocking_reason") val blockingReason: String? = null,
    val status: String,
    @SerialName("completed_at") val completedAt: String? = null,
    val version: Int = 1,
    val urgency: String? = null,
    val importance: String? = null,
    @SerialName("deadline_date") val deadlineDate: String? = null,
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("reminder_at") val reminderAt: String? = null,
    @SerialName("reminder_delivered_at") val reminderDeliveredAt: String? = null,
    val position: Int,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val overdue: Boolean = false,
    @SerialName("planned_dates") val plannedDates: List<String> = emptyList(),
    val subtasks: List<SubtaskDto> = emptyList(),
    @SerialName("session_tasks") val sessionTasks: List<BattleTaskDto> = emptyList(),
)

@Serializable
data class SubtaskDto(
    val id: Int,
    @SerialName("parent_task_id") val parentTaskId: Int,
    val title: String,
    val checked: Boolean,
    @SerialName("effectively_resolved") val effectivelyResolved: Boolean,
    val position: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class BattleTaskListDto(
    val items: List<BattleTaskDto>,
    val timezone: String,
    @SerialName("server_now_iso") val serverNowIso: String,
)

@Serializable
data class BattleTaskCreateDto(
    val title: String,
    val description: String = "",
    @SerialName("ready_to_plan") val readyToPlan: Boolean = false,
    val status: String = "open",
    @SerialName("project_id") val projectId: Int? = null,
    @SerialName("parent_id") val parentId: Int? = null,
    @SerialName("task_type_id") val taskTypeId: Int? = null,
    val urgency: String? = null,
    val importance: String? = null,
    @SerialName("deadline_date") val deadlineDate: String? = null,
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("reminder_at") val reminderAt: String? = null,
    @SerialName("is_blocked") val isBlocked: Boolean = false,
    @SerialName("blocking_reason") val blockingReason: String? = null,
)

@Serializable
data class TaskPlacementDto(
    @SerialName("task_id") val taskId: Int,
    val status: String,
    val position: Int,
)

@Serializable
data class TaskReorderDto(val placements: List<TaskPlacementDto>)

@Serializable
data class TaskIdsDto(@SerialName("task_ids") val taskIds: List<Int>)

@Serializable
data class TaskCompletionUndoDto(@SerialName("undo_token") val undoToken: String)

@Serializable
data class TaskCompletionResponseDto(
    val task: BattleTaskDto,
    @SerialName("undo_token") val undoToken: String,
    @SerialName("removed_planned_block_ids") val removedPlannedBlockIds: List<Int>,
)

@Serializable
data class DueReminderDto(
    val id: Int,
    val title: String,
    @SerialName("deadline_date") val deadlineDate: String? = null,
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("reminder_at") val reminderAt: String,
)

@Serializable
data class RecurrenceRuleDto(
    val mode: String,
    val frequency: String,
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    @SerialName("month_day") val monthDay: Int? = null,
    @SerialName("quota_count") val quotaCount: Int? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("cycle_limit") val cycleLimit: Int? = null,
)

@Serializable
data class RecurrencePreviewDto(
    val upcoming: List<RecurrenceWindowDto>,
    @SerialName("past_cycles") val pastCycles: Int,
    @SerialName("past_tasks") val pastTasks: Int,
)

@Serializable
data class RecurrenceWindowDto(
    val key: String,
    val start: String,
    val end: String,
)

@Serializable
data class RecurringChecklistItemDto(
    val id: Int,
    val title: String,
    val position: Int,
)

@Serializable
data class RecurringTaskLinkDto(
    val id: Int,
    val title: String,
    @SerialName("deadline_date") val deadlineDate: String? = null,
    val overdue: Boolean,
)

@Serializable
data class RecurringTemplateDto(
    val id: Int,
    val title: String,
    val description: String,
    @SerialName("project_id") val projectId: Int? = null,
    val project: ProjectDto? = null,
    @SerialName("task_type_id") val taskTypeId: Int? = null,
    @SerialName("task_type") val taskType: TaskTypeDto? = null,
    val mode: String,
    val status: String,
    val frequency: String,
    val interval: Int,
    val weekdays: List<Int> = emptyList(),
    @SerialName("month_day") val monthDay: Int? = null,
    @SerialName("quota_count") val quotaCount: Int? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("cycle_limit") val cycleLimit: Int? = null,
    val urgency: String? = null,
    val importance: String? = null,
    @SerialName("paused_at") val pausedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("checklist_items") val checklistItems: List<RecurringChecklistItemDto> = emptyList(),
    val upcoming: List<RecurrenceWindowDto> = emptyList(),
    @SerialName("current_tasks") val currentTasks: List<RecurringTaskLinkDto> = emptyList(),
    val cadence: String,
    @SerialName("next_occurrence") val nextOccurrence: String? = null,
)

@Serializable
data class RecurringTemplateCreateDto(
    val title: String,
    val description: String = "",
    @SerialName("project_id") val projectId: Int? = null,
    @SerialName("task_type_id") val taskTypeId: Int? = null,
    val urgency: String? = null,
    val importance: String? = null,
    val mode: String,
    val frequency: String,
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    @SerialName("month_day") val monthDay: Int? = null,
    @SerialName("quota_count") val quotaCount: Int? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("cycle_limit") val cycleLimit: Int? = null,
    @SerialName("checklist_titles") val checklistTitles: List<String> = emptyList(),
    @SerialName("confirm_backfill") val confirmBackfill: Boolean = false,
)
