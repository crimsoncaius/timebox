package com.timebox.android.ui.day

import com.timebox.android.data.activityIdentityText
import com.timebox.android.data.identityText
import com.timebox.android.data.parseActivityInstant
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.SheetField
import com.timebox.android.ui.components.SheetHelp
import com.timebox.android.ui.components.SheetPrimary
import com.timebox.android.ui.components.SheetSegmented
import com.timebox.android.ui.components.TimeboxDialog
import com.timebox.android.ui.components.TimeboxSheet
import com.timebox.android.ui.components.sheetFieldColors
import com.timebox.android.ui.elapsedDuration
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.ZoneId

/** Presentation only: the Activity Tracking owner controls the draft and commits the handoff. */
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

    val sheetTitle = if (startTracking) "Start tracking" else if (editCurrent) "Edit current activity" else "Switch activity"
    val running = elapsedDuration(java.time.Duration.between(start, now).toMinutes().coerceAtLeast(0))
    val subtitle = if (!startTracking && !editCurrent && currentActivity.isNotBlank()) "From $currentActivity · running $running" else null
    val action: () -> Unit = {
        confirmedIds = displaced.filter { it.id != adjustingPlan?.id }.map { it.id }
        if (laterConflict) confirmPlan = true
        else if (planMinutes != null && onConfirmPlan != null) onConfirmPlan(confirmedIds) else onConfirm()
    }
    val primary = when {
        busy -> SheetPrimary.Disabled("Saving…")
        selectedType == null -> SheetPrimary.Disabled(if (startTracking) "Choose a type to start" else "Choose a type to switch")
        !durationValid -> SheetPrimary.Disabled("Enter 1–90 minutes")
        planMinutes != null && !planEnabled -> SheetPrimary.Disabled("Connect to change your plan")
        !valid -> SheetPrimary.Disabled("Choose a time up to now")
        !enabled -> SheetPrimary.Disabled("Not available right now")
        keepCurrent -> SheetPrimary.Action(if (planMinutes == null) "Done" else "Save plan", action)
        startTracking -> SheetPrimary.Action(
            plannedEnd?.let { "Start until ${switchTimeLabel(it, zone)}" }
                ?: timing?.resolve(zone)?.takeIf { it < now }?.let { "Start at ${switchTimeLabel(it, zone)}" } ?: "Start now", action)
        else -> SheetPrimary.Action(
            plannedEnd?.let { "Switch until ${switchTimeLabel(it, zone)}" }
                ?: timing?.let { "Switch at $effectiveTime" } ?: "Switch now", action)
    }
    TimeboxSheet(
        lane = Lane.Actual,
        kicker = "Activity tracking",
        title = sheetTitle,
        subtitle = subtitle,
        onDismiss = onDismiss,
        busy = busy,
        primary = primary,
    ) {
        ActivitySelectionFields(taskTypes, selectedType, onTypeChange, name, onNameChange, busy, onCreateType, typeError, onTypeQueryChange)
        if (onPlanMinutes != null) SheetField("How long from now?") {
            if (!planEnabled) SheetHelp("Connect and sync to change your plan. Open-ended tracking is still available.")
            val lengths = listOf<Int?>(null, 15, 30, 60, 90)
            SheetSegmented(
                options = listOf(if (keepCurrent) "Keep plan" else "Open", "15m", "30m", "1h", "1h 30m", "Custom"),
                selectedIndex = if (customDuration) lengths.size else lengths.indexOf(planMinutes).takeIf { it >= 0 },
                onSelect = { index ->
                    if (index == lengths.size) { customDuration = true; onPlanMinutes(customText.toIntOrNull()?.takeIf { it in 1..90 }) }
                    else { customDuration = false; onPlanMinutes(lengths[index]) }
                },
                enabled = !busy,
            )
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
                    shape = TimeboxShapes.field, colors = sheetFieldColors(),
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
            } else SheetField("When did this change happen?") {
                SheetHelp("Drag the line. Hold near an edge to keep scrolling.")
                SwitchActivityTimeline(
                    currentId = currentId, start = start, records = records, plans = plans, taskTypes = taskTypes,
                    nextActivity = nextActivity, selected = selected, now = now, zone = zone,
                    enabled = enabled && !busy, loadPlanTitles = loadPlanTitles,
                    allowHistory = allowHistory,
                    onSelect = { onTimingChange(it?.let { at -> ActivityTimeValue.from(at, zone) }) },
                )
            }
        } else SheetHelp(if (keepCurrent) "Recording continues from ${switchTimeLabel(start, zone)}. Running Time does not restart." else if (startTracking) "Starts when you press Start." else "Switches now, at ${switchTimeLabel(now, zone)}.")
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
            Text(it, color = colors.error, style = type.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (confirmPlan && plannedEnd != null) {
        val replaced = displaced.filter { it.id != adjustingPlan?.id }
        TimeboxDialog(
            lane = Lane.Planned,
            kicker = "Plan change",
            title = "Replace planned time?",
            body = replacePlanBody(nextActivity, now, plannedEnd, replaced, zone) { activityIdentityText(it.name, it.taskTitle, taskTypes.find { t -> t.id == it.taskTypeId }?.name) },
            dismissLabel = "Back",
            onDismiss = { confirmPlan = false },
            primary = SheetPrimary.Action("Replace ${elapsedDuration(replacedPlanMinutes(now, plannedEnd, replaced))} of plan") {
                confirmPlan = false; if (onConfirmPlan != null) onConfirmPlan(confirmedIds) else onConfirm()
            },
        )
    }
}

/** Minutes of other plans that a new plan from [from] until [until] takes over. */
internal fun replacedPlanMinutes(from: Instant, until: Instant, plans: List<ActivityPlanDto>): Long = plans.sumOf { plan ->
    val a = maxOf(from, parseActivityInstant(plan.startAt))
    val b = minOf(until, parseActivityInstant(plan.endAt))
    java.time.Duration.between(a, b).toMinutes().coerceAtLeast(0)
}

/** "Writing until 12:41 takes 11:55 – 12:25 from Sync." Names each exact overlap. */
internal fun replacePlanBody(next: String, from: Instant, until: Instant, plans: List<ActivityPlanDto>, zone: ZoneId, title: (ActivityPlanDto) -> String): String {
    val parts = plans.map { plan ->
        val a = maxOf(from, parseActivityInstant(plan.startAt))
        val b = minOf(until, parseActivityInstant(plan.endAt))
        "${switchTimeLabel(a, zone)} – ${switchTimeLabel(b, zone)} from ${title(plan)}"
    }
    return "$next until ${switchTimeLabel(until, zone)} takes ${parts.joinToString(" and ")}. The rest of your plan stays in place."
}
