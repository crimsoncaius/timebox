package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class RecordingPiece(val title: String, val start: Instant, val end: Instant, val fresh: Boolean = false, val running: Boolean = false)

@Composable
internal fun RecordingTimeline(before: List<RecordingPiece>, after: List<RecordingPiece>, first: Instant, last: Instant,
                          replaceStart: Instant, replaceEnd: Instant, recorded: Boolean, zone: ZoneId) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    val seconds = Duration.between(first, last).toMillis().toFloat().coerceAtLeast(1f)
    fun fraction(at: Instant) = (Duration.between(first, at).toMillis() / seconds).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().padding(start = 45.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!recorded) Text("BEFORE", Modifier.weight(1f), style = type.laneLabel, color = colors.onVariant)
        Text(if (recorded) "ACTUAL" else "AFTER", Modifier.weight(1f), style = type.laneLabel, color = colors.actual)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(242.dp).testTag("recording-timeline")) {
        val height = 220.dp
        val width = maxWidth
        val lane = if (recorded) width - 47.dp else (width - 55.dp) / 2
        val boundaries = (listOf(first, last, replaceStart, replaceEnd) + before.flatMap { listOf(it.start, it.end) }).distinct().sorted()
        var previousLabel = -30f
        boundaries.forEach { at ->
            val labelY = 220f * fraction(at)
            if (labelY - previousLabel >= 20f) {
                Text(formatter.format(at), Modifier.offset(y = height * fraction(at)), style = type.gutter, color = colors.onVariant)
                previousLabel = labelY
            }
            HorizontalDivider(Modifier.offset(x = 43.dp, y = height * fraction(at)).width(width - 43.dp), color = colors.hairline)
        }
        @Composable fun piece(p: RecordingPiece, x: androidx.compose.ui.unit.Dp, isBefore: Boolean) {
            val h = height * (fraction(p.end) - fraction(p.start))
            val bg = if (p.fresh) colors.actualSurface else colors.low
            Column(Modifier.offset(x = x, y = height * fraction(p.start)).width(lane).height(h)
                .clip(TimeboxShapes.block).background(bg).border(if (p.fresh) 1.5.dp else 1.dp,
                    if (p.fresh) colors.actual else colors.outlineVariant.copy(alpha = .5f), TimeboxShapes.block)
                .semantics { contentDescription = "${p.title}, ${formatter.format(p.start)} to ${if (p.running) "continuing" else formatter.format(p.end)}${if (p.fresh) ", new Actual" else ""}" }
                .padding(horizontal = 7.dp, vertical = 4.dp)) {
                if (h > 24.dp) Text(p.title, style = type.blockTitle, maxLines = if (h > 80.dp) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (h > 50.dp) Text(if (p.fresh) "From plan" else if (p.running) "Tracking →" else "Recorded", style = type.monoSmall, color = colors.onVariant)
                if (p.fresh && h > 90.dp) Text("${formatter.format(p.start)}–${formatter.format(p.end)}", style = type.monoSmall, color = colors.actual)
            }
            if (isBefore) {
                val a = maxOf(replaceStart, p.start)
                val b = minOf(replaceEnd, p.end)
                if (b > a) Box(Modifier.offset(x = x + lane - 5.dp, y = height * fraction(a)).width(4.dp).height(height * (fraction(b) - fraction(a))).background(colors.error.copy(alpha = .65f)))
            }
        }
        if (!recorded) before.forEach { piece(it, 47.dp, true) }
        after.forEach { piece(it, if (recorded) 47.dp else 55.dp + lane, false) }
    }
}
