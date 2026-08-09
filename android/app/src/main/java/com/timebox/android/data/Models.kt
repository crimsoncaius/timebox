package com.timebox.android.data

import com.timebox.android.data.remote.DayDto
import com.timebox.android.data.remote.DayListItemDto
import com.timebox.android.data.remote.DaySummaryDto
import com.timebox.android.data.remote.SettingsDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.data.remote.TimeBlockDto
import java.time.LocalDate

const val SLOT_MINUTES = 30
const val DAY_END_MINUTES = 24 * 60

enum class Lane(val wire: String) {
    Planned("planned"),
    Actual("actual"),
    ;

    companion object {
        fun fromWire(value: String): Lane = if (value == "actual") Actual else Planned
    }
}

data class TaskType(
    val id: Int,
    val name: String,
    val usageCount: Int,
) {
    val root: String get() = name.substringBefore('/')
    val leaf: String get() = name.substringAfterLast('/')
    val depth: Int get() = name.count { it == '/' }
}

data class TimeBlock(
    val id: Int,
    val lane: Lane,
    val taskTypeId: Int,
    val taskTypeName: String,
    val note: String?,
    val plannedBlockId: Int?,
    val startMinute: Int,
    val endMinute: Int,
) {
    val durationMinutes: Int get() = endMinute - startMinute
}

data class Day(
    val date: LocalDate,
    val startHour: Int,
    val endHour: Int,
    val showFullDay: Boolean,
    val blocks: List<TimeBlock>,
    val timezone: String,
    val today: LocalDate,
    /** Minutes past midnight in the app timezone, or null when the clock is unknown. */
    val serverNowMinute: Int?,
) {
    val visibleStart: Int get() = if (showFullDay) 0 else startHour * 60
    val visibleEnd: Int get() = if (showFullDay) DAY_END_MINUTES else endHour * 60
    val slotCount: Int get() = (visibleEnd - visibleStart) / SLOT_MINUTES

    fun lane(lane: Lane): List<TimeBlock> = blocks.filter { it.lane == lane }
}

data class ArchivedDay(
    val date: LocalDate,
    val startHour: Int,
    val endHour: Int,
    val showFullDay: Boolean,
    val blockCount: Int,
) {
    /** The day's window, or null for a date that was opened but never filled in. */
    val windowLabel: String?
        get() = when {
            blockCount == 0 -> null
            showFullDay -> "24h"
            else -> "$startHour–$endHour"
        }
}

data class DaySummaryRow(
    val taskTypeName: String,
    val plannedMinutes: Int,
    val actualMinutes: Int,
)

data class DaySummary(
    val date: LocalDate,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val rows: List<DaySummaryRow>,
)

data class DayWindowSettings(
    val startHour: Int,
    val endHour: Int,
    val showFullDay: Boolean,
)

fun TaskTypeDto.toModel() = TaskType(id = id, name = name, usageCount = usageCount)

fun TimeBlockDto.toModel() = TimeBlock(
    id = id,
    lane = Lane.fromWire(lane),
    taskTypeId = taskTypeId,
    taskTypeName = taskType.name,
    note = note,
    plannedBlockId = plannedBlockId,
    startMinute = startMinute,
    endMinute = endMinute,
)

fun DayDto.toModel() = Day(
    date = LocalDate.parse(date),
    startHour = startHour,
    endHour = endHour,
    showFullDay = showFullDay,
    blocks = timeBlocks.map { it.toModel() },
    timezone = meta.timezone,
    today = LocalDate.parse(meta.today),
    serverNowMinute = parseMinuteOfDay(meta.serverNowIso),
)

fun DayListItemDto.toModel() = ArchivedDay(
    date = LocalDate.parse(date),
    startHour = startHour,
    endHour = endHour,
    showFullDay = showFullDay,
    blockCount = blockCount,
)

fun DaySummaryDto.toModel() = DaySummary(
    date = LocalDate.parse(date),
    plannedMinutes = plannedMinutes,
    actualMinutes = actualMinutes,
    rows = rows.map {
        DaySummaryRow(
            taskTypeName = it.taskTypeName,
            plannedMinutes = it.plannedMinutes,
            actualMinutes = it.actualMinutes,
        )
    },
)

fun SettingsDto.toModel() = DayWindowSettings(
    startHour = startHour,
    endHour = endHour,
    showFullDay = showFullDay,
)

/**
 * Pull the wall-clock minute out of the server's ISO timestamp.
 *
 * The backend already renders it in the app timezone, so the local part is what the
 * timeline needs; the offset suffix is irrelevant here.
 */
private fun parseMinuteOfDay(iso: String): Int? {
    val timePart = iso.substringAfter('T', "").ifEmpty { return null }
    val hhmm = timePart.take(5).split(':')
    if (hhmm.size != 2) return null
    val hours = hhmm[0].toIntOrNull() ?: return null
    val minutes = hhmm[1].toIntOrNull() ?: return null
    return hours * 60 + minutes
}
