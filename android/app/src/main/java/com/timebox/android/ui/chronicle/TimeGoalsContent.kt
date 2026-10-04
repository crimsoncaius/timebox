package com.timebox.android.ui.chronicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.data.TimeGoal
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val goalDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
internal fun goalRange(start: LocalDate, end: LocalDate): String =
    if (start == end) start.format(goalDateFormat) else "${start.format(goalDateFormat)} – ${end.format(goalDateFormat)}"

internal fun goalDuration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

internal fun goalCadence(unit: String, interval: Int): String =
    if (interval == 1) "Every $unit" else "Every $interval ${unit}s"

internal fun TimeGoal.outcomeLabel() = when (period.outcome) {
    "met" -> "Met"
    "missed" -> "Missed"
    "excused" -> "Excused · goal archived"
    "upcoming" -> "Upcoming"
    else -> "${goalDuration(kotlin.math.ceil((period.targetMinutes * 60 - period.durationSeconds) / 60).toInt())} to go"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeGoalsContent(state: TimeGoalsUiState, viewModel: TimeGoalsViewModel) {
    val report = state.goals ?: return
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editorId by rememberSaveable { mutableStateOf<Int?>(null) }
    val canManage = !state.offline && !state.goalSaving && !state.loading
    Text("Recorded time, measured against each goal’s period", style = type.bodySmall, color = colors.onVariant)
    state.goalError?.let { message ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = type.bodySmall, color = colors.error)
            TextButton(onClick = viewModel::clearGoalError) { Text("Dismiss") }
        }
    }
    if (report.goals.isEmpty()) {
        Text("No time goals for this week", style = type.body, color = colors.on, modifier = Modifier.padding(top = 24.dp))
        Text("Choose a Task Type and a repeating time target. Your Actual Blocks supply the progress.",
            style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = { editorId = 0 }, enabled = canManage) { Text("Add Time Goal") }
    }
    report.goals.forEach { goal ->
        val open = expanded == goal.id
        Column(Modifier.fillMaxWidth().clickable { expanded = if (open) null else goal.id }.padding(vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(goal.taskType, style = type.body, color = colors.on)
                    Text("${goalRange(goal.period.start, goal.period.end)} · ${goalCadence(goal.unit, goal.interval)}",
                        style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    if (open) "Collapse goal" else "Expand goal", tint = colors.onVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${goalDuration((goal.period.durationSeconds / 60).toInt())} / ${goalDuration(goal.period.targetMinutes)}",
                    Modifier.weight(1f), style = type.label, color = colors.on)
                Text(goal.outcomeLabel(), style = type.bodySmall, color = colors.onVariant)
            }
            LinearProgressIndicator(progress = { (goal.period.durationSeconds / (goal.period.targetMinutes * 60)).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp), color = colors.actual, trackColor = colors.low)
            if (goal.endDate != null) Text("Archived ${goal.endDate.format(goalDateFormat)}", style = type.bodySmall,
                color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
            else goal.nextTargetMinutes?.let {
                Text("${goalDuration(it)} from ${goal.nextTargetDate.format(goalDateFormat)}", style = type.bodySmall,
                    color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (open) {
            Text("Select a day to view its period", style = type.bodySmall, color = colors.onVariant,
                modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(7) { index ->
                    val date = report.weekStart.plusDays(index.toLong())
                    val active = date >= goal.startDate && (goal.endDate == null || date <= goal.endDate) && date <= report.today
                    val selected = date >= goal.period.start && date <= goal.period.end
                    val minutes = ((goal.days[date] ?: 0.0) / 60).toInt()
                    val foreground = if (selected) colors.onSelected else colors.onVariant
                    Column(Modifier.widthIn(min = 48.dp).clip(TimeboxShapes.cell)
                        .background(if (selected) colors.selected else colors.low)
                        .clickable(enabled = active && canManage) { viewModel.selectGoalDay(goal.id, date) }
                        .semantics { contentDescription = "${date.format(goalDateFormat)}, ${goalDuration(minutes)}, select period" }
                        .padding(horizontal = 5.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[index], style = type.bodySmall, color = foreground)
                        Text(if (active) goalDuration(minutes) else "–", style = type.label, color = foreground,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (state.selectingGoal == goal.id) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            TextButton(onClick = { detailId = goal.id; viewModel.clearGoalError() }) { Text("View period & blocks") }
        }
        HorizontalDivider(color = colors.hairline)
    }
    Spacer(Modifier.height(24.dp))

    if (report.goals.isNotEmpty()) TextButton(onClick = { editorId = 0 }, enabled = canManage) { Text("Add Time Goal") }

    report.goals.find { it.id == detailId }?.let { goal ->
        TimeGoalDetails(goal, report.today, report.timezone, state, viewModel,
            onDismiss = { detailId = null },
            onEdit = { editorId = goal.id; detailId = null },
            onHistory = { detailId = null; viewModel.openGoalHistory(goal.id) })
    }
    editorId?.let { id ->
        TimeGoalEditor(report.goals.find { it.id == id }, report.today, report.timezone.id, state, viewModel) {
            editorId = null
        }
    }
}
