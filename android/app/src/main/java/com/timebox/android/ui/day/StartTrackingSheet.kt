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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.TaskType
import com.timebox.android.data.activityIdentityText
import com.timebox.android.data.identityText
import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Earlier-start support for [StartTrackingSheet]; absent when the server cannot replace history on Start. */
internal class StartHistory(
    val records: List<ActualBlockDto>,
    val plans: List<ActivityPlanDto>,
    val now: Instant,
    val zone: ZoneId,
    val timing: ActivityTimeValue?,
    val onTimingChange: (ActivityTimeValue?) -> Unit,
    val loadPlanTitles: suspend (java.time.LocalDate) -> Map<Int, String> = { emptyMap() },
)

/**
 * Presentation only: asks for the activity when no Planned Block covers the start. Recording begins on confirm.
 * With [history], "Started earlier?" reveals the switch timeline; a prefilled earlier start opens it expanded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartTrackingSheet(
    taskTypes: List<TaskType>,
    selectedType: TaskType?,
    onTypeChange: (TaskType) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    enabled: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onCreateType: (String) -> Unit = {},
    typeError: String? = null,
    onTypeQueryChange: () -> Unit = {},
    history: StartHistory? = null,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var expanded by remember { mutableStateOf(history?.timing != null) }
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
                Text("Start tracking", style = type.screenTitle.copy(fontSize = 28.sp))
                ActivitySelectionFields(taskTypes, selectedType, onTypeChange, name, onNameChange, busy, onCreateType, typeError, onTypeQueryChange)
                if (history != null) StartTime(history, taskTypes, activityIdentityText(name, null, selectedType?.name), expanded, enabled && !busy) { expanded = true }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = type.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", style = type.button) }
                Button(onClick = onConfirm, enabled = enabled && !busy && selectedType != null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(50)) {
                    Text(if (busy) "Starting…" else "Start", style = type.button)
                }
            }
        }
    }
}

@Composable
private fun StartTime(history: StartHistory, taskTypes: List<TaskType>, next: String, expanded: Boolean, enabled: Boolean, onExpand: () -> Unit) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val selected = minOf(history.timing?.resolve(history.zone) ?: history.now, history.now)
    val label = switchTimeLabel(selected, history.zone)
    if (expanded) {
        Text("When did this start?", style = type.label)
        Text("Drag the line. Hold near an edge to keep scrolling.", style = type.bodySmall, color = colors.onVariant)
        SwitchActivityTimeline(
            currentId = -1, start = history.now, records = history.records, plans = history.plans, taskTypes = taskTypes,
            nextActivity = next, selected = selected, now = history.now, zone = history.zone, enabled = enabled,
            onSelect = { at -> history.onTimingChange(at?.takeIf { it < history.now }?.let { ActivityTimeValue.from(it, history.zone) }) },
            loadPlanTitles = history.loadPlanTitles, allowHistory = true, afterLabel = "AFTER START",
        )
    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Starts now · $label", Modifier.weight(1f), style = type.body)
        TextButton(onClick = onExpand, enabled = enabled) { Text("Started earlier?") }
    }
    if (selected < history.now) {
        val affected = history.records.filter { (it.endAt?.let(::parseActivityInstant) ?: history.now) > selected }
        val covered = affected.sumOf { Duration.between(maxOf(selected, parseActivityInstant(it.startAt)), it.endAt?.let(::parseActivityInstant) ?: history.now).toMinutes() }
        val gap = Duration.between(selected, history.now).toMinutes() - covered
        Surface(color = colors.low, shape = TimeboxShapes.group) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AFTER THIS CHANGE · $label", style = type.kicker, color = colors.onVariant)
                Text(next, style = type.sectionTitle)
                Text(startImpact(affected, selected, label, gap), style = type.bodySmall, color = colors.onVariant)
                Text("You can Undo.", style = type.bodySmall, color = colors.onVariant)
            }
        }
    }
}

internal fun startImpact(affected: List<ActualBlockDto>, selected: Instant, label: String, gapMinutes: Long): String {
    val changes = affected.joinToString(" ") { record ->
        if (parseActivityInstant(record.startAt) >= selected) "${record.identityText()} is replaced entirely." else "${record.identityText()} ends at $label."
    }
    val filled = if (gapMinutes > 0) "$gapMinutes min of unrecorded time filled." else ""
    return listOf(changes, filled).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "Starts at $label." }
}
