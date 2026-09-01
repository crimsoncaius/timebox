package com.timebox.android.ui.day

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import kotlinx.coroutines.withTimeoutOrNull

private enum class PreArmResult { Released, Canceled }

/**
 * Detects direct manipulation that touch and stylus input normally arm with a long press.
 * Callers may make explicit press regions immediate, such as an existing Block's resize
 * grooves. Mouse input remains immediate. Movement before arming is left unconsumed so a
 * parent scroll or pager can claim it.
 */
internal suspend fun PointerInputScope.detectLongPressArmedDragGestures(
    onLongPress: () -> Unit,
    onTap: ((Offset) -> Unit)? = null,
    gestureEnabled: (Offset) -> Boolean = { true },
    armImmediately: (Offset) -> Boolean = { false },
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()

        if (!gestureEnabled(down.position)) {
            if (awaitArmOrCancellation(down.id) == PreArmResult.Released) {
                onTap?.invoke(down.position)
            }
            return@awaitEachGesture
        }

        val immediate = down.type == PointerType.Mouse || armImmediately(down.position)
        if (!immediate) {
            val preArmResult = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                awaitArmOrCancellation(down.id)
            }
            when (preArmResult) {
                PreArmResult.Released -> {
                    onTap?.invoke(down.position)
                    return@awaitEachGesture
                }
                PreArmResult.Canceled -> return@awaitEachGesture
                null -> onLongPress()
            }
        }

        var dragging = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            if (event.changes.count { it.pressed } > 1) {
                if (dragging) onDragCancel()
                return@awaitEachGesture
            }
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (dragging) onDragCancel()
                return@awaitEachGesture
            }
            if (!change.pressed) {
                when {
                    dragging && event.type == PointerEventType.Release && !change.isConsumed -> onDragEnd()
                    dragging -> onDragCancel()
                    immediate && event.type == PointerEventType.Release && !change.isConsumed -> {
                        onTap?.invoke(down.position)
                    }
                }
                return@awaitEachGesture
            }
            if (change.isConsumed) {
                if (dragging) onDragCancel()
                return@awaitEachGesture
            }

            val amount = change.positionChangeIgnoreConsumed()
            if (amount == Offset.Zero) continue
            if (!dragging) {
                dragging = true
                onDragStart(down.position)
            }
            change.consume()
            onDrag(change, amount)
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitArmOrCancellation(
    pointerId: PointerId,
): PreArmResult {
    var cumulativeMovement = Offset.Zero
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.count { it.pressed } > 1) return PreArmResult.Canceled
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return PreArmResult.Canceled
        if (!change.pressed) {
            return if (event.type == PointerEventType.Release) {
                PreArmResult.Released
            } else {
                PreArmResult.Canceled
            }
        }

        cumulativeMovement += change.positionChangeIgnoreConsumed()
        if (cumulativeMovement.getDistance() > viewConfiguration.touchSlop) {
            return PreArmResult.Canceled
        }

        val finalEvent = awaitPointerEvent(PointerEventPass.Final)
        val finalChange = finalEvent.changes.firstOrNull { it.id == pointerId }
            ?: return PreArmResult.Canceled
        if (finalChange.isConsumed) return PreArmResult.Canceled
    }
}
