package com.timebox.android.ui.day

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
    loadPlanTitles: suspend (java.time.LocalDate) -> Map<Int, String> = { emptyMap() },
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type

    val nextActivity = name.trim().ifBlank { selectedType?.name ?: "Next activity" }
    val selected = timing?.resolve(zone) ?: now
    val effectiveTime = switchTimeLabel(selected, zone)
    val valid = selected >= start && selected <= now

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp)
                .imePadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("ACTIVITY TRACKING", style = type.kicker, color = colors.onVariant)
                Text("Switch activity", style = type.screenTitle.copy(fontSize = 28.sp))
                ActivitySelectionFields(taskTypes, selectedType, onTypeChange, name, onNameChange, busy, onCreateType)
                Text("When did this change happen?", style = type.label)
                Text("Drag the line or tap a time. Nearby block boundaries snap into place.", style = type.bodySmall, color = colors.onVariant)
                SwitchActivityTimeline(
                    currentId = currentId, start = start, records = records, plans = plans, taskTypes = taskTypes,
                    nextActivity = nextActivity, selected = selected, now = now, zone = zone,
                    enabled = enabled && !busy, loadPlanTitles = loadPlanTitles,
                    onSelect = { onTimingChange(it?.let { at -> ActivityTimeValue.from(at, zone) }) },
                )
                Surface(color = colors.low, shape = TimeboxShapes.group) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("AFTER THIS CHANGE · $effectiveTime", style = type.kicker, color = colors.onVariant)
                        Text("$currentActivity → $nextActivity", style = type.sectionTitle)
                        Text("Previous ends and next starts at $effectiveTime.", style = type.bodySmall, color = colors.onVariant)
                        Text("● Recording continues without a gap.", style = type.bodySmall, color = colors.onVariant)
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
