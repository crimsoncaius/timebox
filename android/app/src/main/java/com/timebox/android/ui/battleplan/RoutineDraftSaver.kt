package com.timebox.android.ui.battleplan

import androidx.compose.runtime.saveable.listSaver
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode

/** Retain a focused editor through Activity recreation, including temporarily invalid input. */
internal fun routineDraftSaver(base: RecurringEditorUiState) = listSaver<RecurringEditorUiState, String>(
    save = { draft -> listOf(
        draft.title, draft.description, draft.taskTypeId?.toString().orEmpty(),
        draft.importance?.name.orEmpty(), draft.urgency?.name.orEmpty(), draft.mode.name,
        draft.frequency.name, draft.interval, draft.weekdays.sorted().joinToString(","), draft.monthDay,
        draft.quotaCount, draft.startDate, draft.endMode.name, draft.endDate, draft.cycleLimit,
        draft.checklistText, draft.keepUnfinishedOverdue.toString(), draft.queuePreplanning.toString(),
    ) + draft.preplanningSlots.flatMap { listOf(it.key.orEmpty(), it.weekday?.toString().orEmpty(), it.start, it.end) } },
    restore = { values -> base.copy(
        title = values[0], description = values[1], taskTypeId = values[2].toIntOrNull(),
        importance = values[3].takeIf(String::isNotEmpty)?.let(PriorityLevel::valueOf),
        urgency = values[4].takeIf(String::isNotEmpty)?.let(PriorityLevel::valueOf),
        mode = RecurrenceMode.valueOf(values[5]), frequency = RecurrenceFrequency.valueOf(values[6]),
        interval = values[7], weekdays = values[8].split(",").mapNotNull(String::toIntOrNull).toSet(),
        monthDay = values[9], quotaCount = values[10], startDate = values[11],
        endMode = RecurrenceEndMode.valueOf(values[12]), endDate = values[13], cycleLimit = values[14],
        checklistText = values[15], keepUnfinishedOverdue = values[16].toBoolean(),
        queuePreplanning = values[17].toBoolean(),
        preplanningSlots = values.drop(18).chunked(4).map { RecurringPreplanningSlotDraft(it[0].ifEmpty { null }, it[1].toIntOrNull(), it[2], it[3]) },
    ) },
)
