package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.elapsedDurationSeconds
import com.timebox.android.ui.runningTime
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

// Throwaway design state. Nothing here writes to Activity Tracking or Planned Blocks.
private object CountdownPreview {
    var variant by mutableIntStateOf(0)
    var scenario by mutableIntStateOf(0)
    var scenarioStartedAt by mutableStateOf(Instant.now())

    fun selectScenario(value: Int) {
        scenario = value
        scenarioStartedAt = Instant.now()
    }
}

private val variantNames = listOf("A · Inline", "B · Two clocks", "C · Plan rail")
private val scenarioNames = listOf("Live", "Near end", "Over", "No plan")

private fun clock(seconds: Long): String {
    val whole = abs(seconds)
    return if (whole >= 3600) String.format(Locale.US, "%d:%02d:%02d", whole / 3600, (whole / 60) % 60, whole % 60)
    else String.format(Locale.US, "%d:%02d", whole / 60, whole % 60)
}

@Composable
internal fun PlanCountdownPrototypeMetrics(
    focus: Boolean,
    elapsedSeconds: Long,
    now: Instant,
    linkedStart: Instant?,
    linkedEnd: Instant?,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val elapsed = if (focus) elapsedDurationSeconds(elapsedSeconds) else runningTime(elapsedSeconds)
    val previewElapsed = Duration.between(CountdownPreview.scenarioStartedAt, now).seconds.coerceAtLeast(0)
    val remaining = when (CountdownPreview.scenario) {
        0 -> linkedEnd?.let { Duration.between(now, it).seconds } ?: 1800L - (previewElapsed % 1800L)
        1 -> 90L - previewElapsed
        2 -> -90L - previewElapsed
        else -> null
    }
    if (remaining == null) {
        Text(elapsed, style = if (focus) type.display else type.bodySmall, color = colors.on)
        if (focus) Text("Running Time", style = type.bodySmall, color = colors.onVariant)
        return
    }
    val duration = if (CountdownPreview.scenario == 0 && linkedStart != null && linkedEnd != null)
        Duration.between(linkedStart, linkedEnd).seconds.coerceAtLeast(1)
    else 1800L
    val progress = ((duration - remaining).toFloat() / duration).coerceIn(0f, 1f)
    val time = clock(remaining)
    val state = if (remaining >= 0) "left" else "over"
    val sample = CountdownPreview.scenario != 0 || linkedEnd == null

    when (CountdownPreview.variant) {
        0 -> Column(verticalArrangement = Arrangement.spacedBy(if (focus) 5.dp else 1.dp)) {
            Text(elapsed, style = if (focus) type.display else type.bodySmall, color = colors.on)
            Text("Running Time", style = type.bodySmall, color = colors.onVariant)
            Text(if (sample) "Sample plan · $time $state" else "$time $state in plan",
                style = if (focus) type.sectionTitle else type.bodySmall, color = colors.planned)
        }
        1 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(if (focus) .38f else .5f)) {
                Text("RUNNING TIME", style = type.laneLabel, color = colors.onVariant)
                Text(if (focus) clock(elapsedSeconds) else elapsed,
                    style = if (focus) type.sectionTitle.copy(fontSize = 22.sp) else type.bodySmall, color = colors.on)
            }
            Column(Modifier.weight(if (focus) .62f else .5f)) {
                Text(
                    if (sample) "SAMPLE · ${if (remaining >= 0) "PLAN LEFT" else "OVER PLAN"}"
                    else if (remaining >= 0) "PLAN LEFT" else "OVER PLAN",
                    style = type.laneLabel, color = colors.planned,
                )
                Text(time, style = if (focus) type.display else type.sectionTitle.copy(fontSize = 20.sp), color = colors.planned)
            }
        }
        else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (sample) "SAMPLE · ${if (remaining >= 0) "PLAN LEFT" else "OVER PLAN"}"
                        else if (remaining >= 0) "UNTIL PLANNED END" else "PAST PLANNED END",
                        style = type.laneLabel, color = colors.planned,
                    )
                    Text(time, style = if (focus) type.display else type.sectionTitle.copy(fontSize = 20.sp), color = colors.planned)
                }
                if (!focus) Text("$elapsed running", style = type.bodySmall, color = colors.onVariant)
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.plannedSurface)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.planned))
            }
            if (focus) Text("Running Time  $elapsed", style = type.bodySmall, color = colors.onVariant)
        }
    }
}

@Composable
internal fun PlanCountdownPrototypeControls(hasLinkedPlan: Boolean) {
    val colors = TimeboxTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        shape = TimeboxShapes.cell, color = colors.low,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("COUNTDOWN PROTOTYPE", style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    if (CountdownPreview.scenario == 0 && hasLinkedPlan) "Linked plan" else "Sample state",
                    style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { CountdownPreview.variant = (CountdownPreview.variant + 2) % 3 }) { Text("Prev") }
                Text(
                    variantNames[CountdownPreview.variant], modifier = Modifier.weight(1f),
                    style = TimeboxTheme.type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.on,
                )
                TextButton(onClick = { CountdownPreview.variant = (CountdownPreview.variant + 1) % 3 }) { Text("Next") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                scenarioNames.forEachIndexed { index, label ->
                    FilterChip(
                        selected = CountdownPreview.scenario == index,
                        onClick = { CountdownPreview.selectScenario(index) },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
        }
    }
}
