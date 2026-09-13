package com.timebox.android.ui.day

import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.MIN_PLANNED_BLOCK_MINUTES
import com.timebox.android.ui.planning.PlanningDraftPlacement
import kotlin.math.abs

internal const val NO_NEARBY_BLOCK_SPACE = "No available space in this day"

/** Resolves a drag within the configured day without changing duration or other placements. */
internal fun nearestPlanningDragStart(
    day: Day,
    drafts: List<PlanningDraftPlacement>,
    movingTaskId: Int?,
    intendedStart: Int,
    durationMinutes: Int,
): Int? {
    val occupied = (
        day.lane(Lane.Planned).map { it.startMinute to it.endMinute } +
            drafts.filter { it.date == day.date && it.taskId != movingTaskId }
                .map { it.startMinute to it.endMinute }
        ).filter { (start, end) -> end > day.visibleStart && start < day.visibleEnd }
    return nearestAvailableBlockStart(day, occupied, intendedStart, durationMinutes)
}

/** Resolves a saved Block move without treating its committed range as an obstacle. */
internal fun nearestSavedBlockDragStart(
    day: Day,
    movingBlockId: Int,
    intendedStart: Int,
    durationMinutes: Int,
    lane: Lane = Lane.Planned,
): Int? = nearestAvailableBlockStart(
    day = day,
    occupied = day.lane(lane)
        .filterNot { it.id == movingBlockId }
        .map { it.startMinute to it.endMinute }
        .filter { (start, end) -> end > day.visibleStart && start < day.visibleEnd },
    intendedStart = intendedStart,
    durationMinutes = durationMinutes,
    minimumDuration = if (lane == Lane.Actual) 1 else MIN_PLANNED_BLOCK_MINUTES,
    visibleEnd = if (lane == Lane.Actual) actualPlacementEnd(day) else day.visibleEnd,
)

/** Validates a previewed saved Block range without selecting a replacement slot. */
internal fun savedBlockRangeAvailable(
    day: Day,
    movingBlockId: Int,
    startMinute: Int,
    endMinute: Int,
    lane: Lane = Lane.Planned,
): Boolean {
    val minimumDuration = if (lane == Lane.Actual) 1 else MIN_PLANNED_BLOCK_MINUTES
    val upper = if (lane == Lane.Actual) actualPlacementEnd(day) else day.visibleEnd
    if (endMinute - startMinute < minimumDuration ||
        startMinute < day.visibleStart || endMinute > upper || startMinute >= endMinute
    ) return false
    return day.lane(lane)
        .filterNot { it.id == movingBlockId }
        .none { startMinute < it.endMinute && it.startMinute < endMinute }
}

private fun nearestAvailableBlockStart(
    day: Day,
    occupied: List<Pair<Int, Int>>,
    intendedStart: Int,
    durationMinutes: Int,
    minimumDuration: Int = MIN_PLANNED_BLOCK_MINUTES,
    visibleEnd: Int = day.visibleEnd,
): Int? {
    if (durationMinutes < minimumDuration ||
        durationMinutes > visibleEnd - day.visibleStart
    ) return null

    val sortedOccupied = occupied.sortedBy { it.first }

    var nearest: Int? = null
    var gapStart = day.visibleStart
    fun considerGap(gapEnd: Int) {
        if (gapEnd - gapStart < durationMinutes) return
        val candidate = intendedStart.coerceIn(gapStart, gapEnd - durationMinutes)
        val previous = nearest
        if (previous == null || abs(candidate - intendedStart) < abs(previous - intendedStart) ||
            (abs(candidate - intendedStart) == abs(previous - intendedStart) && candidate > previous)
        ) nearest = candidate
    }
    for ((start, end) in sortedOccupied) {
        if (end <= day.visibleStart || start >= visibleEnd) continue
        considerGap(start)
        gapStart = maxOf(gapStart, end)
    }
    considerGap(visibleEnd)
    return nearest
}

/** Bounds of the existing gap; a resize stops at its first neighbor. */
internal fun blockResizeBounds(
    day: Day,
    start: Int,
    end: Int,
    movingBlockId: Int? = null,
    drafts: List<PlanningDraftPlacement> = emptyList(),
    movingTaskId: Int? = null,
    lane: Lane = Lane.Planned,
): Pair<Int, Int> {
    val occupied = day.lane(lane).filterNot { it.id == movingBlockId }
        .map { it.startMinute to it.endMinute } +
        drafts.filter { it.date == day.date && it.taskId != movingTaskId }
            .map { it.startMinute to it.endMinute }
    val lower = maxOf(day.visibleStart, occupied.filter { it.second <= start }.maxOfOrNull { it.second } ?: day.visibleStart)
    val upper = minOf(if (lane == Lane.Actual) actualPlacementEnd(day) else day.visibleEnd, occupied.filter { it.first >= end }.minOfOrNull { it.first } ?: day.visibleEnd)
    return lower to upper
}

/** Use the shared server clock, including elapsed time since the Day was fetched. */
internal fun actualPlacementEnd(day: Day): Int = when {
    day.date < day.today -> day.visibleEnd
    day.date > day.today -> 0
    else -> minOf(day.visibleEnd, day.nowMinuteAt(System.currentTimeMillis()) ?: 0)
}
