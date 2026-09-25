package com.timebox.android.ui.day

import com.timebox.android.data.activityIdentityText
import com.timebox.android.data.identityText
import com.timebox.android.data.parseActivityInstant
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.TaskType
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.ZoneId

/** Presentation only: the Activity Tracking owner controls the draft and commits the handoff. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SwitchActivitySheet(
    currentActivity: String,
    currentId: Int,
    start: Instant,
    records: List<ActualBlockDto>,
    plans: List<ActivityPlanDto>,
    taskTypes: List<TaskType>,
    selectedType: TaskType?,
    onTypeChange: (TaskType) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    timing: ActivityTimeValue?,
    onTimingChange: (ActivityTimeValue?) -> Unit,
    now: Instant,
    zone: ZoneId,
    enabled: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onCreateType: (String) -> Unit = {},
    typeError: String? = null,
    onTypeQueryChange: () -> Unit = {},
    loadPlanTitles: suspend (java.time.LocalDate) -> Map<Int, String> = { emptyMap() },
    allowHistory: Boolean = false,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type

    val nextActivity = activityIdentityText(name, null, selectedType?.name)
    val selected = timing?.resolve(zone) ?: now
    val effectiveTime = switchTimeLabel(selected, zone)
    val valid = (allowHistory || selected >= start) && selected <= now
    var details by remember { mutableStateOf(false) }
    val affected = records.filter { (it.endAt?.let(::parseActivityInstant) ?: now) > selected }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp)
                .imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("ACTIVITY TRACKING", style = type.kicker, color = colors.onVariant)
                Text("Switch activity", style = type.screenTitle.copy(fontSize = 28.sp))
                ActivitySelectionFields(taskTypes, selectedType, onTypeChange, name, onNameChange, busy, onCreateType, typeError, onTypeQueryChange)
                Text("When did this change happen?", style = type.label)
                Text("Drag the line. Hold near an edge to keep scrolling.", style = type.bodySmall, color = colors.onVariant)
                SwitchActivityTimeline(
                    currentId = currentId, start = start, records = records, plans = plans, taskTypes = taskTypes,
                    nextActivity = nextActivity, selected = selected, now = now, zone = zone,
                    enabled = enabled && !busy, loadPlanTitles = loadPlanTitles,
                    allowHistory = allowHistory,
                    onSelect = { onTimingChange(it?.let { at -> ActivityTimeValue.from(at, zone) }) },
                )
                Surface(color = colors.low, shape = TimeboxShapes.group) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("AFTER THIS CHANGE · $effectiveTime", style = type.kicker, color = colors.onVariant)
                        Text("$currentActivity → $nextActivity", style = type.sectionTitle)
                        Text(if (selected < start) "${affected.size} activities change. $nextActivity replaces time from ${selected.atZone(zone).toLocalDate()}, $effectiveTime through now."
                            else "Previous ends and next starts at $effectiveTime.", style = type.bodySmall, color = colors.onVariant)
                        Text(if (allowHistory) "You can Undo." else "● Recording continues without a gap.", style = type.bodySmall, color = colors.onVariant)
                    }
                }
                if (selected < start) {
                    TextButton(onClick = { details = !details }) { Text(if (details) "Hide changes" else "See what changes") }
                    if (details) {
                        affected.forEach { record ->
                            val removed = parseActivityInstant(record.startAt) >= selected
                            Text("${record.identityText()}: ${if (removed) "replaced entirely" else "ends at $effectiveTime"}", style = type.bodySmall)
                            if (removed && !record.note.isNullOrBlank()) Text("Note also replaced: ${record.note}", style = type.bodySmall, color = colors.onVariant)
                        }
                        val covered = affected.sumOf { java.time.Duration.between(maxOf(selected, parseActivityInstant(it.startAt)), it.endAt?.let(::parseActivityInstant) ?: now).toMillis() }
                        val gap = (java.time.Duration.between(selected, now).toMillis() - covered) / 60000
                        if (gap > 0) Text("$gap minutes of unrecorded time filled.", style = type.bodySmall)
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = type.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", style = type.button) }
                Button(onClick = onConfirm, enabled = enabled && !busy && valid && selectedType != null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(50)) {
                    Text(if (busy) "Switching…" else "Switch activity", style = type.button)
                }
            }
        }
    }
}
