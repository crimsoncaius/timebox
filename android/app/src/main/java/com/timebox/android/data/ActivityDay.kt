package com.timebox.android.data

import com.timebox.android.data.remote.ActivitySnapshotDto
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The journal supplies Actuals to the same Day model online and offline. */
fun ActivitySnapshotDto.projectDay(date: LocalDate, previous: Day?): Day {
    val zone = ZoneId.of(reportingTimezone)
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val now = parseActivityInstant(serverAt)
    val actuals = records.mapNotNull { dto ->
        val row = dto.toModel()
        val a = maxOf(start, row.startAt)
        val b = minOf(end, row.endAt ?: now)
        if (a >= b) null else ActualBlockDayProjection(row, date,
            if (a == start) 0 else a.atZone(zone).let { it.hour * 60 + it.minute },
            if (b == end) 1440 else b.atZone(zone).let { it.hour * 60 + it.minute },
            Duration.between(a, b).toMinutes().toInt(), Duration.between(start, end).toMinutes().toInt())
    }
    val blocks = previous?.blocks.orEmpty().filter { it.lane == Lane.Planned } + actuals.map { p ->
        val r = p.actualBlock
        TimeBlock(r.id, Lane.Actual, r.taskTypeId, r.taskTypeName, r.taskId, r.task, r.note, r.plannedBlockId,
            actualBlockId = r.id, startMinute = p.startMinute, endMinute = p.endMinute, name = r.name)
    }
    return (previous ?: Day(date, 0, 24, true, emptyList(), timezone = reportingTimezone,
        today = now.atZone(zone).toLocalDate(), serverNowMinute = null, capturedAtMillis = now.toEpochMilli()))
        .copy(blocks = blocks, actualBlocks = actuals, timezone = reportingTimezone)
}
