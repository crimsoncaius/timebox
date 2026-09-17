package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.ui.elapsedDuration
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Planned Block's allocated times and what remains of it, in the reporting timezone. */
internal fun plannedSuggestionTiming(plan: ActivityPlanDto, now: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val format = DateTimeFormatter.ofPattern("h:mm a", locale)
    val start = parseActivityInstant(plan.startAt).atZone(zone)
    val end = parseActivityInstant(plan.endAt)
    val left = Duration.between(now, end).toMinutes().coerceAtLeast(0)
    return "${format.format(start)} – ${format.format(end.atZone(zone))} · ${elapsedDuration(left)} left"
}

/** Suggests switching the Current Activity onto the Planned Block that covers now. */
@Composable
internal fun PlannedBlockSuggestion(name: String, timing: String, enabled: Boolean, focus: Boolean, onSwitch: () -> Unit) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = if (focus) 20.dp else 8.dp, bottom = 4.dp).padding(horizontal = if (focus) 0.dp else 6.dp)
            .clip(shape).background(colors.plannedSurface).border(1.dp, colors.plannedBorder, shape)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(colors.planned))
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)) {
            Text("PLANNED NOW", style = TimeboxTheme.type.kicker, color = colors.planned)
            Text(name, color = colors.on, fontSize = if (focus) 16.sp else 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(timing, style = TimeboxTheme.type.monoSmall, color = colors.onVariant, modifier = Modifier.padding(top = 2.dp))
        }
        FilledTonalButton(
            enabled = enabled, onClick = onSwitch, modifier = Modifier.padding(end = 10.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.planned, contentColor = colors.plannedSurface),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) { Text("Switch", style = TimeboxTheme.type.button) }
    }
}
