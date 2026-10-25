package com.timebox.android.ui.day

import com.timebox.android.ui.elapsedDuration

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.ui.components.RelatedCard
import com.timebox.android.ui.components.SheetField
import com.timebox.android.ui.components.SheetHelp
import com.timebox.android.ui.components.SheetPrimary
import com.timebox.android.ui.components.TimeboxSheet
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Presentation only. The tracking owner keeps the observed target and submits the stop. */
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
    TimeboxSheet(
        lane = Lane.Actual,
        kicker = "Activity tracking",
        title = "Stop tracking",
        subtitle = "$activity · started ${switchTimeLabel(start, zone)}",
        onDismiss = onDismiss,
        busy = busy,
        primary = when {
            busy -> SheetPrimary.Disabled("Stopping…")
            value == null -> SheetPrimary.Disabled("Choose a time up to now")
            !enabled -> SheetPrimary.Disabled("Not available right now")
            else -> SheetPrimary.Action("Stop at ${value.endLabel}", onConfirm)
        },
    ) {
        SheetField("When did you stop?") {
            SheetHelp("Drag the line or tap a time. Nearby block boundaries snap into place.")
            SwitchActivityTimeline(
                currentId = currentId, start = start, records = records, plans = plans, taskTypes = taskTypes,
                nextActivity = null, selected = selected, now = now, zone = zone,
                enabled = enabled && !busy, loadPlanTitles = loadPlanTitles,
                onSelect = { onTimingChange(it?.let { at -> ActivityTimeValue.from(at, zone) }) },
            )
        }
        RelatedCard(
            lane = Lane.Actual,
            caption = listOfNotNull("Recorded", value?.dateContext).joinToString(" · "),
            trailing = value?.let { elapsedDuration(it.minutes) },
            title = value?.range ?: "—",
            text = activity,
        )
        (validation ?: error)?.let {
            Text(it, style = type.bodySmall, color = colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
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
