package com.timebox.android.ui.day

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.timebox.android.ui.theme.TimeboxDimens
import kotlinx.coroutines.launch

internal const val MIN_TIMELINE_ZOOM = 0.5f
internal const val MAX_TIMELINE_ZOOM = 12f

@Stable
internal class TimelineZoom(initial: Float = 1f) {
    var scale by mutableFloatStateOf(initial)
        private set
    fun set(value: Float) { if (value.isFinite()) scale = value.coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM) }
    companion object {
        val Saver = Saver<TimelineZoom, Float>(save = { it.scale }, restore = { TimelineZoom(it) })
    }
}

internal val LocalTimelineZoom = staticCompositionLocalOf<TimelineZoom?> { null }

@Composable
internal fun timelineSlotHeight() = TimeboxDimens.slotHeight * (LocalTimelineZoom.current?.scale ?: 1f)

internal fun zoomScrollOffset(scroll: Int, anchor: Float, before: Float, after: Float): Int =
    ((scroll + anchor) * after / before - anchor).toInt().coerceAtLeast(0)

/** Two fingers claim the gesture before block/pager handlers; one finger keeps normal behavior. */
internal fun Modifier.timelinePinch(scroll: ScrollState): Modifier = composed {
    val zoom = LocalTimelineZoom.current
    val scope = rememberCoroutineScope()
    if (zoom == null) this else pointerInput(zoom, scroll) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val before = zoom.scale
                    zoom.set(before * event.calculateZoom())
                    val target = zoomScrollOffset(scroll.value, event.calculateCentroid(false).y, before, zoom.scale)
                    scope.launch {
                        // Let the enlarged content update its scroll bounds before clamping.
                        withFrameNanos { }
                        scroll.scrollTo(target)
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
