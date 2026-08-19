package com.timebox.android.data

import com.timebox.android.data.remote.BattleTaskDto
import com.timebox.android.data.remote.BattleTaskListDto
import com.timebox.android.data.remote.DueReminderDto
import com.timebox.android.data.remote.PatchField
import com.timebox.android.data.remote.ProjectDto
import com.timebox.android.data.remote.RecurrencePreviewDto
import com.timebox.android.data.remote.RecurrenceWindowDto
import com.timebox.android.data.remote.RecurringChecklistItemDto
import com.timebox.android.data.remote.RecurringTaskLinkDto
import com.timebox.android.data.remote.RecurringTemplateDto
import java.time.Instant
import java.time.LocalDate

enum class TaskStatus(val wire: String) {
    Open("open"),
    InProgress("in_progress"),
    Blocked("blocked"),
    Completed("completed");

    companion object {
        fun fromWire(value: String): TaskStatus = entries.firstOrNull { it.wire == value }
            ?: throw IllegalArgumentException("Unknown task status: $value")
    }
}

enum class PriorityLevel(val wire: String) {
    Low("low"), Medium("medium"), High("high");

    companion object {
        fun fromWire(value: String): PriorityLevel = entries.firstOrNull { it.wire == value }
            ?: throw IllegalArgumentException("Unknown priority: $value")
    }
}

enum class TaskCollection(val wire: String) {
    Active("active"), Archived("archived"), Trash("trash")
}

enum class RecurrenceMode(val wire: String) {
    Scheduled("scheduled"), Quota("quota");

    companion object {
        fun fromWire(value: String): RecurrenceMode = entries.first { it.wire == value }
    }
}

enum class RecurrenceStatus(val wire: String) {
    Active("active"), Paused("paused"), Ended("ended");

    companion object {
        fun fromWire(value: String): RecurrenceStatus = entries.first { it.wire == value }
    }
}

enum class RecurrenceFrequency(val wire: String) {
    Daily("daily"), Weekly("weekly"), Monthly("monthly");

    companion object {
        fun fromWire(value: String): RecurrenceFrequency = entries.first { it.wire == value }
    }
}

data class Project(
    val id: Int,
    val name: String,
    val description: String,
    val deadlineDate: LocalDate?,
    val deadlineAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BattleTask(
    val id: Int,
    val parentId: Int?,
    val parentTitle: String?,
    val projectId: Int?,
    val project: Project?,
    val taskTypeId: Int?,
    val taskType: TaskType?,
    val recurringTemplateId: Int?,
    val recurringTemplateTitle: String?,
    val occurrenceKey: String?,
    val recurrenceKind: String?,
    val quotaPeriodStart: LocalDate?,
    val quotaPeriodEnd: LocalDate?,
    val expectedSessions: Int?,
    val sessionIndex: Int?,
    val quotaCompleted: Int?,
    val title: String,
    val description: String,
    val readyToPlan: Boolean,
    val status: TaskStatus,
    val urgency: PriorityLevel?,
    val importance: PriorityLevel?,
    val deadlineDate: LocalDate?,
    val deadlineAt: Instant?,
    val reminderAt: Instant?,
    val reminderDeliveredAt: Instant?,
    val position: Int,
    val archivedAt: Instant?,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val overdue: Boolean,
    val subtasks: List<BattleTask>,
    val isBlocked: Boolean = false,
    val blockingReason: String? = null,
)

data class BattleTaskList(
    val items: List<BattleTask>,
    val timezone: String,
    val serverNow: Instant,
)

data class DueReminder(
    val id: Int,
    val title: String,
    val deadlineDate: LocalDate?,
    val deadlineAt: Instant?,
    val reminderAt: Instant,
)

data class RecurringChecklistItem(val id: Int, val title: String, val position: Int)
data class RecurringTaskLink(val id: Int, val title: String, val deadlineDate: LocalDate?, val overdue: Boolean)
data class RecurrenceWindow(val key: String, val start: LocalDate, val end: LocalDate)
data class RecurrencePreview(
    val upcoming: List<RecurrenceWindow>,
    val pastCycles: Int,
    val pastTasks: Int,
)

data class RecurringTemplate(
    val id: Int,
    val title: String,
    val description: String,
    val projectId: Int?,
    val project: Project?,
    val taskTypeId: Int?,
    val taskType: TaskType?,
    val mode: RecurrenceMode,
    val status: RecurrenceStatus,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val weekdays: List<Int>,
    val monthDay: Int?,
    val quotaCount: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val cycleLimit: Int?,
    val urgency: PriorityLevel?,
    val importance: PriorityLevel?,
    val pausedAt: Instant?,
    val endedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val checklistItems: List<RecurringChecklistItem>,
    val upcoming: List<RecurrenceWindow>,
    val currentTasks: List<RecurringTaskLink>,
    val cadence: String,
    val nextOccurrence: LocalDate?,
)

data class ProjectCreate(
    val name: String,
    val description: String = "",
    val deadlineDate: LocalDate? = null,
    val deadlineAt: Instant? = null,
)

data class ProjectPatch(
    val name: PatchField<String> = PatchField.Absent,
    val description: PatchField<String> = PatchField.Absent,
    val deadlineDate: PatchField<LocalDate> = PatchField.Absent,
    val deadlineAt: PatchField<Instant> = PatchField.Absent,
)

data class BattleTaskCreate(
    val title: String,
    val description: String = "",
    val readyToPlan: Boolean = false,
    val status: TaskStatus = TaskStatus.Open,
    val projectId: Int? = null,
    val parentId: Int? = null,
    val taskTypeId: Int? = null,
    val urgency: PriorityLevel? = null,
    val importance: PriorityLevel? = null,
    val deadlineDate: LocalDate? = null,
    val deadlineAt: Instant? = null,
    val reminderAt: Instant? = null,
    val isBlocked: Boolean = false,
    val blockingReason: String? = null,
)

data class BattleTaskPatch(
    val title: PatchField<String> = PatchField.Absent,
    val description: PatchField<String> = PatchField.Absent,
    val readyToPlan: PatchField<Boolean> = PatchField.Absent,
    val status: PatchField<TaskStatus> = PatchField.Absent,
    val projectId: PatchField<Int> = PatchField.Absent,
    val taskTypeId: PatchField<Int> = PatchField.Absent,
    val urgency: PatchField<PriorityLevel> = PatchField.Absent,
    val importance: PatchField<PriorityLevel> = PatchField.Absent,
    val deadlineDate: PatchField<LocalDate> = PatchField.Absent,
    val deadlineAt: PatchField<Instant> = PatchField.Absent,
    val reminderAt: PatchField<Instant> = PatchField.Absent,
    val isBlocked: PatchField<Boolean> = PatchField.Absent,
    val blockingReason: PatchField<String> = PatchField.Absent,
)

data class TaskPlacement(val taskId: Int, val status: TaskStatus, val position: Int)

data class RecurrenceRule(
    val mode: RecurrenceMode,
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    val monthDay: Int? = null,
    val quotaCount: Int? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val cycleLimit: Int? = null,
)

data class RecurringTemplateCreate(
    val title: String,
    val description: String = "",
    val projectId: Int? = null,
    val taskTypeId: Int? = null,
    val urgency: PriorityLevel? = null,
    val importance: PriorityLevel? = null,
    val rule: RecurrenceRule,
    val checklistTitles: List<String> = emptyList(),
    val confirmBackfill: Boolean = false,
)

data class RecurringTemplatePatch(
    val title: PatchField<String> = PatchField.Absent,
    val description: PatchField<String> = PatchField.Absent,
    val projectId: PatchField<Int> = PatchField.Absent,
    val taskTypeId: PatchField<Int> = PatchField.Absent,
    val urgency: PatchField<PriorityLevel> = PatchField.Absent,
    val importance: PatchField<PriorityLevel> = PatchField.Absent,
    val frequency: PatchField<RecurrenceFrequency> = PatchField.Absent,
    val interval: PatchField<Int> = PatchField.Absent,
    val weekdays: PatchField<List<Int>> = PatchField.Absent,
    val monthDay: PatchField<Int> = PatchField.Absent,
    val quotaCount: PatchField<Int> = PatchField.Absent,
    val startDate: PatchField<LocalDate> = PatchField.Absent,
    val endDate: PatchField<LocalDate> = PatchField.Absent,
    val cycleLimit: PatchField<Int> = PatchField.Absent,
    val checklistTitles: PatchField<List<String>> = PatchField.Absent,
    val confirmBackfill: PatchField<Boolean> = PatchField.Absent,
)

internal fun ProjectDto.toModel() = Project(
    id, name, description, deadlineDate?.let(LocalDate::parse), deadlineAt?.let(::parseInstant),
    parseInstant(createdAt), parseInstant(updatedAt),
)

internal fun BattleTaskDto.toModel(): BattleTask {
    val legacyStatus = TaskStatus.fromWire(status)
    return BattleTask(
        id = id, parentId = parentId, parentTitle = parentTitle, projectId = projectId,
        project = project?.toModel(), taskTypeId = taskTypeId, taskType = taskType?.toModel(),
        recurringTemplateId = recurringTemplateId, recurringTemplateTitle = recurringTemplateTitle,
        occurrenceKey = occurrenceKey, recurrenceKind = recurrenceKind,
        quotaPeriodStart = quotaPeriodStart?.let(LocalDate::parse),
        quotaPeriodEnd = quotaPeriodEnd?.let(LocalDate::parse), expectedSessions = expectedSessions,
        sessionIndex = sessionIndex, quotaCompleted = quotaCompleted, title = title,
        description = description, readyToPlan = readyToPlan,
        status = if (legacyStatus == TaskStatus.Blocked) TaskStatus.Open else legacyStatus,
        urgency = urgency?.let(PriorityLevel::fromWire), importance = importance?.let(PriorityLevel::fromWire),
        deadlineDate = deadlineDate?.let(LocalDate::parse), deadlineAt = deadlineAt?.let(::parseInstant),
        reminderAt = reminderAt?.let(::parseInstant), reminderDeliveredAt = reminderDeliveredAt?.let(::parseInstant),
        position = position, archivedAt = archivedAt?.let(::parseInstant), deletedAt = deletedAt?.let(::parseInstant),
        createdAt = parseInstant(createdAt), updatedAt = parseInstant(updatedAt), overdue = overdue,
        subtasks = subtasks.map { it.toModel() },
        isBlocked = isBlocked || legacyStatus == TaskStatus.Blocked,
        blockingReason = blockingReason,
    )
}

internal fun BattleTaskListDto.toModel() = BattleTaskList(items.map { it.toModel() }, timezone, parseInstant(serverNowIso))
internal fun DueReminderDto.toModel() = DueReminder(id, title, deadlineDate?.let(LocalDate::parse), deadlineAt?.let(::parseInstant), parseInstant(reminderAt))
internal fun RecurrenceWindowDto.toModel() = RecurrenceWindow(key, LocalDate.parse(start), LocalDate.parse(end))
internal fun RecurrencePreviewDto.toModel() = RecurrencePreview(upcoming.map { it.toModel() }, pastCycles, pastTasks)
internal fun RecurringChecklistItemDto.toModel() = RecurringChecklistItem(id, title, position)
internal fun RecurringTaskLinkDto.toModel() = RecurringTaskLink(id, title, deadlineDate?.let(LocalDate::parse), overdue)
internal fun RecurringTemplateDto.toModel() = RecurringTemplate(
    id, title, description, projectId, project?.toModel(), taskTypeId, taskType?.toModel(),
    RecurrenceMode.fromWire(mode), RecurrenceStatus.fromWire(status), RecurrenceFrequency.fromWire(frequency),
    interval, weekdays, monthDay, quotaCount, LocalDate.parse(startDate), endDate?.let(LocalDate::parse),
    cycleLimit, urgency?.let(PriorityLevel::fromWire), importance?.let(PriorityLevel::fromWire),
    pausedAt?.let(::parseInstant), endedAt?.let(::parseInstant), parseInstant(createdAt), parseInstant(updatedAt),
    checklistItems.map { it.toModel() }, upcoming.map { it.toModel() }, currentTasks.map { it.toModel() },
    cadence, nextOccurrence?.let(LocalDate::parse),
)
