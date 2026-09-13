package com.timebox.android.ui.day

import com.timebox.android.ui.elapsedDuration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.TaskType
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Presentation only. The tracking owner keeps the observed target and submits the stop. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StopTrackingSheet(
    currentId: Int,
    records: List<ActualBlockDto>,
    plans: List<ActivityPlanDto>,
    taskTypes: List<TaskType>,
    activity: String,
    start: Instant,
    timing: ActivityTimeValue?,
    onTimingChange: (ActivityTimeValue?) -> Unit,
    now: Instant,
    zone: ZoneId,
    enabled: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    loadPlanTitles: suspend (java.time.LocalDate) -> Map<Int, String> = { emptyMap() },
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val selected = runCatching { timing?.resolve(zone) }.getOrNull() ?: now
    val preview = runCatching { stopTrackingPreview(start, timing, now, zone) }
    val value = preview.getOrNull()
    val validation = preview.exceptionOrNull()?.message
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg, contentColor = colors.on,
        scrimColor = colors.scrim, shape = TimeboxShapes.sheet,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 18.dp).size(34.dp, 4.dp)
                .clip(CircleShape).background(colors.outlineVariant.copy(alpha = .6f)))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp)
                .imePadding().padding(horizontal = 22.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("ACTIVITY TRACKING", style = type.kicker, color = colors.onVariant)
                Text("Stop tracking", style = type.screenTitle.copy(fontSize = 28.sp))
                Text("When did you stop?", style = type.label)
                Text("Drag the line or tap a time. Nearby block boundaries snap into place.", style = type.bodySmall, color = colors.onVariant)
                SwitchActivityTimeline(
                    currentId = currentId, start = start, records = records, plans = plans, taskTypes = taskTypes,
                    nextActivity = null, selected = selected, now = now, zone = zone,
                    enabled = enabled && !busy, loadPlanTitles = loadPlanTitles,
                    onSelect = { onTimingChange(it?.let { at -> ActivityTimeValue.from(at, zone) }) },
                )
                Column(
                    Modifier.fillMaxWidth().clip(TimeboxShapes.group).background(colors.low).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.clip(TimeboxShapes.chip).background(colors.actualSurface)
                                .border(1.dp, colors.actualBorder, TimeboxShapes.chip)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(colors.actual))
                            Text("ACTUAL", style = type.laneLabel, color = colors.actual)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(value?.let { elapsedDuration(it.minutes) } ?: "—", style = type.mono, color = colors.onVariant)
                    }
                    Text(value?.range ?: "—", style = type.display)
                    value?.dateContext?.let { Text(it, style = type.bodySmall, color = colors.onVariant) }
                    Text(activity, style = type.label)
                }
                Text(zone.id, style = type.monoSmall, color = colors.onVariant)
                Text("Unrecorded after ${value?.endLabel ?: "—"}.", style = type.bodySmall, color = colors.onVariant)
                (validation ?: error)?.let {
                    Text(it, style = type.bodySmall, color = colors.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            HorizontalDivider(color = colors.hairline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", style = type.button, color = colors.onVariant) }
                Button(
                    onClick = onConfirm, enabled = enabled && !busy && value != null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = TimeboxShapes.chip,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.action, contentColor = colors.onAction),
                ) {
                    Text(if (busy) "Stopping…" else if (timing == null) "Stop tracking" else "Stop at ${value?.endLabel ?: "—"}", style = type.button)
                }
            }
        }
    }
}

internal data class StopTrackingPreview(val range: String, val minutes: Long, val dateContext: String?, val endLabel: String)

internal fun stopTrackingPreview(start: Instant, timing: ActivityTimeValue?, now: Instant, zone: ZoneId): StopTrackingPreview {
    val end = timing?.resolve(zone) ?: now
    require(end >= start) { "End time must be at or after the activity started." }
    require(end <= now) { "End time cannot be in the future." }
    val localStart = start.atZone(zone)
    val localEnd = end.atZone(zone)
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val date = DateTimeFormatter.ofPattern("d MMM uuuu")
    val crossDate = localStart.toLocalDate() != localEnd.toLocalDate()
    val sameOffset = localStart.offset == localEnd.offset
    val range = if (sameOffset) "${clock.format(localStart)} – ${clock.format(localEnd)}"
        else "${clock.format(localStart)} ${localStart.offset} – ${clock.format(localEnd)} ${localEnd.offset}"
    val dates = if (crossDate) "${date.format(localStart)} – ${date.format(localEnd)}"
        else if (localEnd.toLocalDate() != now.atZone(zone).toLocalDate()) date.format(localEnd) else null
    return StopTrackingPreview(range, Duration.between(start, end).toMinutes(), dates, clock.format(localEnd))
}
