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
    planMinutes: Int? = null,
    onPlanMinutes: ((Int?) -> Unit)? = null,
    editCurrent: Boolean = false,
    keepCurrent: Boolean = false,
    startTracking: Boolean = false,
    currentTaskId: Int? = null,
    currentTaskTitle: String? = null,
    currentPlanId: Int? = null,
    onConfirmPlan: ((List<Int>) -> Unit)? = null,
    planEnabled: Boolean = true,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type

    val nextActivity = activityIdentityText(name, if (keepCurrent) currentTaskTitle else null, selectedType?.name)
    val selected = timing?.resolve(zone) ?: now
    val effectiveTime = switchTimeLabel(selected, zone)
    val valid = (allowHistory || selected >= start) && selected <= now
    var details by remember { mutableStateOf(false) }
    var customDuration by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("45") }
    var confirmPlan by remember { mutableStateOf(false) }
    var confirmedIds by remember { mutableStateOf(emptyList<Int>()) }
    var startExpanded by remember { mutableStateOf(timing != null) }
    val durationValid = !customDuration || customText.toIntOrNull()?.let { it in 1..90 } == true
    val plannedEnd = planMinutes?.let { now.plusSeconds(it * 60L) }
    val adjustingPlan = plans.find {
        it.taskTypeId == selectedType?.id && it.name.orEmpty() == name.trim() &&
        it.taskId == currentTaskId &&
        parseActivityInstant(it.startAt) <= now && (parseActivityInstant(it.endAt) > now || (keepCurrent && it.id == currentPlanId)) }
    val displaced = plans.filter { it.id != adjustingPlan?.id && plannedEnd != null && parseActivityInstant(it.startAt) < plannedEnd && parseActivityInstant(it.endAt) > now }
    val laterConflict = displaced.any { parseActivityInstant(it.startAt) > now }
    val affected = records.filter { (it.endAt?.let(::parseActivityInstant) ?: now) > selected }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
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
                Text(if (startTracking) "Start tracking" else if (editCurrent) "Edit current activity" else "Switch activity", style = type.screenTitle.copy(fontSize = 28.sp))
                ActivitySelectionFields(taskTypes, selectedType, onTypeChange, name, onNameChange, busy, onCreateType, typeError, onTypeQueryChange)
                if (onPlanMinutes != null) {
                    Text("How long from now?", style = type.label)
                    if (!planEnabled) Text("Connect and sync to change your plan. Open-ended tracking is still available.", style = type.bodySmall, color = colors.onVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf<Int?>(null, 15, 30, 60, 90).forEach { minutes ->
                            FilterChip(selected = !customDuration && planMinutes == minutes, onClick = { customDuration = false; onPlanMinutes(minutes) }, enabled = !busy,
                                label = { Text(minutes?.let { "$it min" } ?: if (keepCurrent) "Keep plan" else "Open-ended") })
                        }
                        FilterChip(selected = customDuration, onClick = { customDuration = true; onPlanMinutes(customText.toIntOrNull()?.takeIf { it in 1..90 }) }, enabled = !busy, label = { Text("Custom") })
                    }
                    if (customDuration) Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it; onPlanMinutes(it.toIntOrNull()?.takeIf { value -> value in 1..90 }) },
                            label = { Text("Duration") }, suffix = { Text("min") },
                            textStyle = type.sectionTitle.copy(fontSize = 22.sp),
                            singleLine = true, isError = !durationValid, enabled = !busy,
                            shape = TimeboxShapes.field,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.width(156.dp),
                        )
                        Text(
                            if (durationValid) "Choose 1–90 minutes" else "Enter 1–90 minutes",
                            style = type.bodySmall,
                            color = if (durationValid) colors.onVariant else colors.error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (!keepCurrent && planMinutes == null && !customDuration) {
                if (startTracking) {
                    StartTime(StartHistory(records, plans, now, zone, timing, onTimingChange, loadPlanTitles), taskTypes, nextActivity, startExpanded, enabled && !busy) { startExpanded = true }
                } else {
                Text("When did this change happen?", style = type.label)
                Text("Drag the line. Hold near an edge to keep scrolling.", style = type.bodySmall, color = colors.onVariant)
                SwitchActivityTimeline(
                    currentId = currentId, start = start, records = records, plans = plans, taskTypes = taskTypes,
                    nextActivity = nextActivity, selected = selected, now = now, zone = zone,
                    enabled = enabled && !busy, loadPlanTitles = loadPlanTitles,
                    allowHistory = allowHistory,
                    onSelect = { onTimingChange(it?.let { at -> ActivityTimeValue.from(at, zone) }) },
                )
                }
                } else Text(if (keepCurrent) "Recording continues from ${switchTimeLabel(start, zone)}. Running Time does not restart." else if (startTracking) "Starts when you press Start." else "Switches now, at ${switchTimeLabel(now, zone)}.", style = type.bodySmall, color = colors.onVariant)
                if (!startTracking || planMinutes != null) {
                Surface(color = colors.low, shape = TimeboxShapes.group) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("AFTER THIS CHANGE · $effectiveTime", style = type.kicker, color = colors.onVariant)
                        if (plannedEnd != null) {
                            Text("$nextActivity until ${switchTimeLabel(plannedEnd, zone)}", style = type.sectionTitle)
                            Text(if (adjustingPlan != null) "Your plan now ends here. Earlier planned time stays as it is."
                                else "${planMinutes} minutes added to your plan from now.", style = type.bodySmall, color = colors.onVariant)
                            displaced.filter { it.id != adjustingPlan?.id }.forEach { p ->
                                val title = activityIdentityText(p.name, p.taskTitle, taskTypes.find { it.id == p.taskTypeId }?.name)
                                val finish = parseActivityInstant(p.endAt)
                                Text(if (finish > plannedEnd) "$title resumes at ${switchTimeLabel(plannedEnd, zone)}, until ${switchTimeLabel(finish, zone)}."
                                    else "$title is replaced until ${switchTimeLabel(finish, zone)}.", style = type.bodySmall)
                            }
                            Text("Tracking continues when planned time ends.", style = type.bodySmall, color = colors.onVariant)
                        } else if (keepCurrent) Text("Your activity and plan stay as they are.", style = type.bodySmall)
                        else {
                        Text("$currentActivity → $nextActivity", style = type.sectionTitle)
                        Text(if (selected < start) "${affected.size} activities change. $nextActivity replaces time from ${selected.atZone(zone).toLocalDate()}, $effectiveTime through now."
                            else "Previous ends and next starts at $effectiveTime.", style = type.bodySmall, color = colors.onVariant)
                        Text(if (allowHistory) "You can Undo." else "● Recording continues without a gap.", style = type.bodySmall, color = colors.onVariant)
                        }
                    }
                }
                }
                if (!startTracking && selected < start) {
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
                Button(onClick = {
                    confirmedIds = displaced.filter { it.id != adjustingPlan?.id }.map { it.id }
                    if (laterConflict) confirmPlan = true
                    else if (planMinutes != null && onConfirmPlan != null) onConfirmPlan(confirmedIds) else onConfirm()
                }, enabled = enabled && !busy && valid && durationValid && selectedType != null && (planMinutes == null || planEnabled),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(50)) {
                    Text(if (busy) "Saving…" else if (startTracking) "Start" else if (keepCurrent) { if (planMinutes == null) "Done" else "Save plan" } else "Switch activity", style = type.button)
                }
            }
        }
    }
    if (confirmPlan) AlertDialog(onDismissRequest = { confirmPlan = false }, title = { Text("Replace this planned time?") },
        text = { Text("$nextActivity will replace the overlapping time in ${displaced.joinToString { activityIdentityText(it.name, it.taskTitle, taskTypes.find { t -> t.id == it.taskTypeId }?.name) }}. Time outside this interval stays in place.") },
        confirmButton = { TextButton(onClick = { confirmPlan = false; if (onConfirmPlan != null) onConfirmPlan(confirmedIds) else onConfirm() }) { Text("Replace this time") } },
        dismissButton = { TextButton(onClick = { confirmPlan = false }) { Text("Back") } })
}
