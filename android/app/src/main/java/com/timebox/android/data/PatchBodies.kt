package com.timebox.android.data

import com.timebox.android.data.remote.PatchField
import com.timebox.android.data.remote.patchBody
import kotlinx.serialization.json.JsonObject

internal fun ProjectPatch.toJson(): JsonObject = patchBody {
    string("name", name)
    string("description", description)
    string("deadline_date", deadlineDate.map(LocalDateEncoder))
    string("deadline_at", deadlineAt.map(InstantEncoder))
}

internal fun BattleTaskPatch.toJson(): JsonObject = patchBody {
    string("title", title)
    string("description", description)
    boolean("ready_to_plan", readyToPlan)
    string("status", status.map { it.wire })
    int("project_id", projectId)
    int("task_type_id", taskTypeId)
    string("urgency", urgency.map { it.wire })
    string("importance", importance.map { it.wire })
    string("deadline_date", deadlineDate.map(LocalDateEncoder))
    string("deadline_at", deadlineAt.map(InstantEncoder))
    string("reminder_at", reminderAt.map(InstantEncoder))
    boolean("is_blocked", isBlocked)
    string("blocking_reason", blockingReason)
}

internal fun RecurringTemplatePatch.toJson(): JsonObject = patchBody {
    string("title", title)
    string("description", description)
    int("project_id", projectId)
    int("task_type_id", taskTypeId)
    string("urgency", urgency.map { it.wire })
    string("importance", importance.map { it.wire })
    string("frequency", frequency.map { it.wire })
    int("interval", interval)
    ints("weekdays", weekdays)
    int("month_day", monthDay)
    int("quota_count", quotaCount)
    string("start_date", startDate.map(LocalDateEncoder))
    string("end_date", endDate.map(LocalDateEncoder))
    int("cycle_limit", cycleLimit)
    strings("checklist_titles", checklistTitles)
    boolean("confirm_backfill", confirmBackfill)
}

internal fun timeBlockPatchBody(
    taskTypeId: Int?,
    taskId: PatchField<Int>,
    note: String?,
    startMinute: Int?,
    endMinute: Int?,
): JsonObject = patchBody {
    if (taskTypeId != null) int("task_type_id", PatchField.of(taskTypeId))
    int("task_id", taskId)
    if (note != null) string("note", PatchField.of(note))
    if (startMinute != null) int("start_minute", PatchField.of(startMinute))
    if (endMinute != null) int("end_minute", PatchField.of(endMinute))
}

private val LocalDateEncoder: (java.time.LocalDate) -> String = { it.toString() }
private val InstantEncoder: (java.time.Instant) -> String = { it.toString() }

private fun <T, R> PatchField<T>.map(transform: (T) -> R): PatchField<R> = when (this) {
    PatchField.Absent -> PatchField.Absent
    PatchField.Null -> PatchField.Null
    is PatchField.Value -> PatchField.Value(transform(value))
}
