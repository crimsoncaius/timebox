package com.timebox.android.ui.day

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeBlock
import java.time.Duration
import java.time.ZoneId

/**
 * Block Duration in whole minutes for a card showing [startMinute]–[endMinute].
 *
 * An Actual Block's duration covers its whole record, including any part on another Day;
 * a running one counts up with its displayed end. A drag shows the previewed span.
 */
internal fun blockDurationMinutes(block: TimeBlock, day: Day, startMinute: Int, endMinute: Int, dragging: Boolean): Int {
    val span = (endMinute - startMinute).coerceAtLeast(0)
    if (dragging || block.lane != Lane.Actual) return span
    val record = day.actualBlocks.firstOrNull { it.actualBlock.id == block.actualBlockId }?.actualBlock ?: return span
    record.endAt?.let { return Duration.between(record.startAt, it).toMinutes().toInt().coerceAtLeast(0) }
    val dayStart = day.date.atStartOfDay(ZoneId.of(day.timezone)).toInstant()
    return span + Duration.between(record.startAt, dayStart).toMinutes().toInt().coerceAtLeast(0)
}

/** A time range followed by `· duration`, dropping the duration when the line is too narrow for it. */
@Composable
internal fun TimeRangeWithDuration(range: String, duration: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Layout(
        content = {
            Text(range, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(" · $duration", style = style, color = color, maxLines = 1, softWrap = false)
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0)
        val rangePlaceable = measurables[0].measure(loose)
        val durationPlaceable = measurables[1].measure(loose.copy(maxWidth = Constraints.Infinity))
        val fits = rangePlaceable.width + durationPlaceable.width <= constraints.maxWidth
        val width = rangePlaceable.width + if (fits) durationPlaceable.width else 0
        val height = maxOf(rangePlaceable.height, if (fits) durationPlaceable.height else 0)
        layout(width.coerceAtLeast(constraints.minWidth), height) {
            rangePlaceable.place(0, 0)
            if (fits) durationPlaceable.place(rangePlaceable.width, 0)
        }
    }
}
