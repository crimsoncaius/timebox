package com.timebox.android.ui.day

import com.timebox.android.data.ActualBlockDayProjection
import com.timebox.android.data.Day
import com.timebox.android.data.TimeBlock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class PlannedRecordingKind { Record, AlreadyRecorded, Override }

internal data class LinkedRecordingRef(val id: Int, val startMinute: Int, val endMinute: Int)

internal data class PlannedRecordingState(
    val kind: PlannedRecordingKind,
    val requestedStartMinute: Int,
    val requestedEndMinute: Int,
    val available: Boolean,
    val underway: Boolean,
    val matching: LinkedRecordingRef?,
    val override: LinkedRecordingRef?,
)

internal fun clockMinute(at: Instant, date: LocalDate, zone: ZoneId): Int {
    val local = at.atZone(zone)
    return when {
        local.toLocalDate() > date -> Int.MAX_VALUE / 2
        local.toLocalDate() < date -> 0
        else -> local.hour * 60 + local.minute
    }
}

internal fun plannedRecordingState(block: TimeBlock, day: Day?, now: Instant): PlannedRecordingState {
    val zone = ZoneId.of(day?.timezone ?: "UTC")
    val date = day?.date ?: now.atZone(zone).toLocalDate()
    val nowMinute = clockMinute(now, date, zone)
    val requestedStart = block.startMinute
    val requestedEnd = minOf(block.endMinute, nowMinute).coerceAtLeast(requestedStart)
    val available = nowMinute > requestedStart
    val underway = nowMinute < block.endMinute
    val overlapping = day?.actualBlocks.orEmpty().filter { it.startMinute < requestedEnd && it.endMinute > requestedStart }
    val linked = overlapping.filter { it.actualBlock.id in block.actualBlockIds || it.actualBlock.plannedBlockId == block.id }
    val matching = linked.map { it.toRef() }.firstOrNull { it.startMinute == requestedStart && it.endMinute == requestedEnd }
    val differing = linked.map { it.toRef() }.firstOrNull { it.startMinute != requestedStart || it.endMinute != requestedEnd }
    val kind = when {
        matching != null && overlapping.size == 1 -> PlannedRecordingKind.AlreadyRecorded
        differing != null -> PlannedRecordingKind.Override
        else -> PlannedRecordingKind.Record
    }
    return PlannedRecordingState(
        kind = kind,
        requestedStartMinute = requestedStart,
        requestedEndMinute = requestedEnd,
        available = available,
        underway = underway,
        matching = matching,
        override = differing ?: matching,
    )
}

private fun ActualBlockDayProjection.toRef() = LinkedRecordingRef(actualBlock.id, startMinute, endMinute)
