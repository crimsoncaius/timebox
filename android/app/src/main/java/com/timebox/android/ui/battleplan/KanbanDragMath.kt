package com.timebox.android.ui.battleplan

import androidx.compose.ui.geometry.Rect
import com.timebox.android.data.TaskStatus

/**
 * Pointer maths for the mobile Battle Plan board drag.
 *
 * Pure functions over measured bounds, kept apart from the composables so the
 * drop, hysteresis and edge-paging rules can be read and tested on their own.
 */

internal fun insertionIndexForPointer(
    pointerY: Float,
    itemCount: Int,
    measuredBounds: List<IndexedValue<Rect>>,
): Int {
    if (itemCount == 0 || measuredBounds.isEmpty()) return 0
    val ordered = measuredBounds.sortedBy { it.index }
    val next = ordered.firstOrNull { pointerY < it.value.center.y }
    return (next?.index ?: (ordered.last().index + 1)).coerceIn(0, itemCount)
}

internal fun insertionIndexWithHysteresis(
    pointerY: Float,
    itemCount: Int,
    measuredBounds: List<IndexedValue<Rect>>,
    currentIndex: Int,
    hysteresis: Float,
): Int {
    val raw = insertionIndexForPointer(pointerY, itemCount, measuredBounds)
    val current = currentIndex.coerceIn(0, itemCount)
    if (raw == current || hysteresis <= 0f) return raw
    val boundaryIndex = if (raw > current) current else current - 1
    val boundary = measuredBounds.firstOrNull { it.index == boundaryIndex }?.value?.center?.y ?: return raw
    return when {
        raw > current && pointerY <= boundary + hysteresis -> current
        raw < current && pointerY >= boundary - hysteresis -> current
        else -> raw
    }
}

internal fun verticalAutoScrollStep(
    pointerY: Float,
    laneBounds: Rect,
    edgeSize: Float,
    maximumStep: Float,
): Float = when {
    edgeSize <= 0f || maximumStep <= 0f -> 0f
    pointerY < laneBounds.top + edgeSize -> {
        val strength = ((laneBounds.top + edgeSize - pointerY) / edgeSize).coerceIn(0f, 1f)
        -maximumStep * strength
    }
    pointerY > laneBounds.bottom - edgeSize -> {
        val strength = ((pointerY - (laneBounds.bottom - edgeSize)) / edgeSize).coerceIn(0f, 1f)
        maximumStep * strength
    }
    else -> 0f
}

internal fun isUnchangedDrop(
    sourceStatus: TaskStatus,
    targetStatus: TaskStatus,
    sourceIndex: Int,
    targetIndex: Int,
): Boolean = sourceStatus == targetStatus && sourceIndex == targetIndex

internal fun edgePageDirection(
    pointerX: Float,
    viewportWidth: Float,
    edgeWidth: Float,
    currentPage: Int,
    pageCount: Int,
): Int = when {
    pointerX <= edgeWidth && currentPage > 0 -> -1
    pointerX >= viewportWidth - edgeWidth && currentPage < pageCount - 1 -> 1
    else -> 0
}
