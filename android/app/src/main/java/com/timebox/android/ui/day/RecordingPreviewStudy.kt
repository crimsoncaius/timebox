package com.timebox.android.ui.day

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.BuildConfig
import com.timebox.android.data.remote.PlannedRecordingDto
import com.timebox.android.ui.components.TransientFeedback
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Review-only state. The two presentations never submit a recording command. */
private object RecordingStudy {
    var variant by mutableStateOf("timeline")
}

fun configureRecordingStudy(intent: Intent?) {
    if (!BuildConfig.RECORDING_PROTOTYPE) return
    intent?.getStringExtra("recording_preview_variant")?.takeIf { it in listOf("summary", "timeline") }?.let {
        RecordingStudy.variant = it
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecordingPreviewStudy(source: PlannedRecordingDto, timezone: String, onDismiss: () -> Unit) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val zone = ZoneId.of(timezone)
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    fun time(at: Instant) = timeFormat.format(at)
    val start = Instant.parse(source.startAt)
    val end = Instant.parse(source.endAt)
    var scenario by remember { mutableStateOf("trim") }
    var recorded by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val base = source.conflicts.firstOrNull()
    val oldStart = start.minusSeconds(900)
    val oldEnd = when (scenario) { "split" -> end.plusSeconds(900); "tracking" -> null; else -> start.plusSeconds(900) }
    val displayEnd = maxOf(end.plusSeconds(900), oldEnd ?: end.plusSeconds(900))
    val title = source.replacement.name ?: "Unnamed activity"
    val oldTitle = base?.name ?: base?.taskType?.name ?: "Earlier draft"
    val before = listOf(RecordingPiece(oldTitle, oldStart, oldEnd ?: displayEnd, running = oldEnd == null))
    val after = buildList {
        if (oldStart < start) add(RecordingPiece(oldTitle, oldStart, start))
        add(RecordingPiece(title, start, end, fresh = true))
        if (oldEnd == null || oldEnd > end) add(RecordingPiece(oldTitle, end, oldEnd ?: displayEnd, running = oldEnd == null))
    }
    val replacedMinutes = Duration.between(maxOf(start, oldStart), minOf(end, oldEnd ?: end)).toMinutes()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg, contentColor = colors.on, scrimColor = colors.scrim, shape = TimeboxShapes.sheet,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp)
            .padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Deliberately separated review controls; absent from ordinary builds.
            Column(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.low).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PROTOTYPE · NO SAVED CHANGES", style = type.kicker, color = colors.onVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("summary" to "Summary", "timeline" to "Before / after").forEach { (key, label) ->
                        FilterChip(selected = RecordingStudy.variant == key, onClick = { RecordingStudy.variant = key },
                            label = { Text(label, style = type.bodySmall) }, modifier = Modifier.weight(1f).testTag("recording-study-$key"))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("trim" to "Trim", "split" to "Split", "tracking" to "Tracking").forEach { (key, label) ->
                        TextButton(onClick = { scenario = key; recorded = false; details = false }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text(if (scenario == key) "• $label" else label, style = type.bodySmall)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { recorded = false; details = false }, contentPadding = PaddingValues(0.dp)) { Text("Reset", style = type.bodySmall) }
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("RECORD ACTUAL", style = type.kicker, color = colors.actual)
                    Text(if (recorded) "Actual recorded" else "Replace overlapping time", style = type.screenTitle.copy(fontSize = 26.sp))
                    Text("${DateTimeFormatter.ofPattern("EEE, d MMM").withZone(zone).format(start)} · $timezone", style = type.bodySmall, color = colors.onVariant)
                }
                Column(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.actualSurface)
                    .border(1.dp, colors.actualBorder, TimeboxShapes.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${time(start)} – ${time(end)}", style = type.display.copy(fontSize = 28.sp))
                        Spacer(Modifier.weight(1f))
                        Text("${Duration.between(start, end).toMinutes()} min", style = type.monoSmall, color = colors.onVariant)
                    }
                    Text(title, style = type.label)
                    Text("From your Planned Block", style = type.bodySmall, color = colors.onVariant)
                }
                if (RecordingStudy.variant == "timeline") {
                    RecordingTimeline(before, after, oldStart, displayEnd, start, end, recorded, zone)
                    Text(if (recorded) "The plan is kept. Task completion is unchanged."
                        else "$replacedMinutes min of $oldTitle will be replaced.", style = type.bodySmall, color = colors.onVariant)
                } else {
                    Text(if (recorded) "Your Actual time" else "What changes", style = type.sectionTitle)
                    if (!recorded) SummaryLine("Replace", "${time(maxOf(start, oldStart))} – ${time(minOf(end, oldEnd ?: end))}", oldTitle, true)
                    after.filter { !it.fresh }.forEach {
                        SummaryLine(if (it.running) "Continue" else "Keep", "${time(it.start)} – ${if (it.running) "onward" else time(it.end)}", it.title, false)
                    }
                    if (recorded) Text("The plan is kept. Task completion is unchanged.", style = type.bodySmall, color = colors.onVariant)
                }
                if (oldEnd == null) {
                    Text("$oldTitle keeps tracking from ${time(end)}. Tracking stays on.", style = type.bodySmall, color = colors.actual)
                }
                if (!recorded) {
                    Column(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.low).padding(12.dp)) {
                        Text("Name and note will change for the replaced time.", style = type.bodySmall)
                        TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (details) "Hide details" else "See name and note changes", style = type.bodySmall)
                        }
                        if (details) {
                            Text("Existing: $oldTitle", style = type.label)
                            Text(base?.note ?: "No note", style = type.bodySmall, color = colors.onVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("After: $title", style = type.label)
                            Text(source.replacement.note ?: "No note", style = type.bodySmall, color = colors.onVariant)
                            Text("Kept time retains its original details.", style = type.bodySmall, color = colors.onVariant)
                        }
                    }
                }
                if (recorded) TransientFeedback("Actual recorded", actionLabel = "Undo", onAction = { recorded = false })
            }
            HorizontalDivider(color = colors.hairline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text(if (recorded) "Close" else "Cancel", style = type.button, color = colors.onVariant) }
                Button(onClick = { recorded = !recorded }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = TimeboxShapes.chip,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.action, contentColor = colors.onAction)) {
                    Text(if (recorded) "Try again" else "Replace overlapping time", style = type.button)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(action: String, range: String, title: String, replacing: Boolean) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    Row(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.low).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(action, Modifier.width(66.dp), style = type.label, color = if (replacing) colors.error else colors.actual)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(range, style = type.mono)
            Text(title, style = type.bodySmall, color = colors.onVariant)
        }
    }
}

