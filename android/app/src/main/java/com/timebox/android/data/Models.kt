package com.timebox.android.data

import com.timebox.android.data.remote.DayDto
import com.timebox.android.data.remote.DayListItemDto
import com.timebox.android.data.remote.DayPreviewDto
import com.timebox.android.data.remote.DaySummaryDto
import com.timebox.android.data.remote.SettingsDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.data.remote.TimeBlockDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

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
    val taskUsageCount: Int = 0,
    val recurringTemplateUsageCount: Int = 0,
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
    val taskId: Int?,
    val task: LinkedTask?,
    val note: String?,
    val plannedBlockId: Int?,
    val startMinute: Int,
    val endMinute: Int,
) {
    val durationMinutes: Int get() = endMinute - startMinute
}

data class LinkedTask(
    val id: Int,
    val title: String,
    val status: TaskStatus,
    val taskTypeId: Int?,
)

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
    /** When [serverNowMinute] was read, so the now line can advance between fetches. */
    val capturedAtMillis: Long = System.currentTimeMillis(),
) {
    val visibleStart: Int get() = if (showFullDay) 0 else startHour * 60
    val visibleEnd: Int get() = if (showFullDay) DAY_END_MINUTES else endHour * 60
    val slotCount: Int get() = (visibleEnd - visibleStart) / SLOT_MINUTES

    fun lane(lane: Lane): List<TimeBlock> = blocks.filter { it.lane == lane }

    /**
     * [serverNowMinute] advanced by the time elapsed since this day was fetched.
     *
     * Only the *interval* is taken from the device, never its wall-clock reading: the
     * phone may sit in a different zone from `APP_TIMEZONE`, and the whole point of the
     * server clock is that every device agrees on the same time of day.
     */
    fun nowMinuteAt(millis: Long): Int? {
        val base = serverNowMinute ?: return null
        val elapsed = ((millis - capturedAtMillis) / 60_000L).coerceAtLeast(0L)
        return base + elapsed.toInt()
    }
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
    /** The backend's today in the app timezone. Read-only, so it costs no stray day row. */
    val today: LocalDate,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val rows: List<DaySummaryRow>,
)

data class DayWindowSettings(
    val startHour: Int,
    val endHour: Int,
    val showFullDay: Boolean,
)

fun TaskTypeDto.toModel() = TaskType(
    id = id,
    name = name,
    usageCount = usageCount,
    taskUsageCount = taskUsageCount,
    recurringTemplateUsageCount = recurringTemplateUsageCount,
)

data class PlanningCommitPlacement(
    val date: LocalDate,
    val taskId: Int,
    val taskTypeId: Int,
    val startMinute: Int,
    val endMinute: Int,
)

fun TimeBlockDto.toModel() = TimeBlock(
    id = id,
    lane = Lane.fromWire(lane),
    taskTypeId = taskTypeId,
    taskTypeName = taskType.name,
    taskId = taskId,
    task = task?.let {
        LinkedTask(
            id = it.id,
            title = it.title,
            status = TaskStatus.fromWire(it.status),
            taskTypeId = it.taskTypeId,
        )
    },
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

fun DayPreviewDto.toModel() = Day(
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
    today = LocalDate.parse(meta.today),
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

internal fun parseInstant(value: String): Instant =
    runCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrElse { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
