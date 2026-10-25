package com.timebox.android.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.SLOT_MINUTES
import com.timebox.android.ui.durationShort
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.theme.TimeboxTheme

/** Shorter stretches without an Actual Block are not worth an invitation to record time. */
internal const val MIN_UNTRACKED_GAP_MINUTES = 15

/** A stretch of a Day with no Actual Block, as minutes past midnight. */
internal data class UntrackedGap(val startMinute: Int, val endMinute: Int) {
    val minutes: Int get() = endMinute - startMinute
}

/**
 * The Day's Untracked Gaps: stretches of at least [MIN_UNTRACKED_GAP_MINUTES] after its first
 * Actual Block and before the latest Actual Block's end or, on Today, [nowMinute]. Future dates,
 * and the time before the first Actual Block, never have one.
 */
internal fun untrackedGaps(day: Day, nowMinute: Int?): List<UntrackedGap> {
    if (day.date > day.today) return emptyList()
    val blocks = day.lane(Lane.Actual)
        .map { it.startMinute to displayedBlockEndMinute(it, day, nowMinute) }
        .sortedBy { it.first }
    if (blocks.isEmpty()) return emptyList()
    val gaps = mutableListOf<UntrackedGap>()
    var cursor = blocks.first().first
    for ((start, end) in blocks) {
        if (start - cursor >= MIN_UNTRACKED_GAP_MINUTES) gaps += UntrackedGap(cursor, start)
        cursor = maxOf(cursor, end)
    }
    if (day.date == day.today && nowMinute != null && nowMinute - cursor >= MIN_UNTRACKED_GAP_MINUTES) {
        gaps += UntrackedGap(cursor, minOf(nowMinute, day.visibleEnd))
    }
    return gaps
}

/** A dashed region in the Actual lane that opens a new Actual Block prefilled with the gap. */
@Composable
internal fun UntrackedGapRegion(
    gap: UntrackedGap,
    visibleStart: Int,
    slotHeight: Dp,
    onLog: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val top = slotHeight * ((gap.startMinute - visibleStart).toFloat() / SLOT_MINUTES)
    val height = slotHeight * (gap.minutes.toFloat() / SLOT_MINUTES)
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    Row(
        modifier = Modifier
            .offset(y = top)
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height((height - 4.dp).coerceAtLeast(16.dp))
            .drawBehind {
                drawRoundRect(
                    color = colors.actual.copy(alpha = 0.55f),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                )
            }
            .clickable(role = Role.Button, onClickLabel = "Log time", onClick = onLog)
            .semantics(mergeDescendants = true) {
                contentDescription = "Untracked, ${hhmm(gap.startMinute)} to ${hhmm(gap.endMinute)}, ${durationShort(gap.minutes)}"
            }
            .testTag("untracked-gap-${gap.startMinute}")
            .padding(horizontal = 8.dp),
        verticalAlignment = if (height >= 40.dp) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Untracked · ${durationShort(gap.minutes)}",
            color = colors.onVariant, fontSize = 11.sp, lineHeight = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = if (height >= 40.dp) 5.dp else 0.dp),
        )
        Text(
            "+ Log", color = colors.actual, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = if (height >= 40.dp) 4.dp else 0.dp),
        )
    }
}
