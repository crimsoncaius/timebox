package com.timebox.android.ui.day

import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.MIN_PLANNED_BLOCK_MINUTES
import com.timebox.android.ui.planning.PlanningDraftPlacement
import kotlin.math.abs

/** Resolves a drag within the configured day without changing duration or other placements. */
internal fun nearestPlanningDragStart(
    day: Day,
    drafts: List<PlanningDraftPlacement>,
    movingTaskId: Int?,
    intendedStart: Int,
    durationMinutes: Int,
): Int? {
    if (durationMinutes < MIN_PLANNED_BLOCK_MINUTES ||
        durationMinutes > day.visibleEnd - day.visibleStart
    ) return null

    val occupied = (
        day.lane(Lane.Planned).map { it.startMinute to it.endMinute } +
            drafts.filter { it.date == day.date && it.taskId != movingTaskId }
                .map { it.startMinute to it.endMinute }
        ).filter { (start, end) -> end > day.visibleStart && start < day.visibleEnd }
        .sortedBy { it.first }

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
    for ((start, end) in occupied) {
        considerGap(start)
        gapStart = maxOf(gapStart, end)
    }
    considerGap(day.visibleEnd)
    return nearest
}
