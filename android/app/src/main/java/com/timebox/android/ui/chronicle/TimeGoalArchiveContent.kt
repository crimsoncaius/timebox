package com.timebox.android.ui.chronicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.format.DateTimeFormatter

@Composable
internal fun TimeGoalArchiveContent(state: TimeGoalsUiState, viewModel: TimeGoalsViewModel) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val history = state.history?.takeIf { it.goal.id == state.historyGoalId }
    val showingHistory = state.historyGoalId != null
    var selectedPeriod by rememberSaveable(state.historyGoalId) { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable(state.historyGoalId) { mutableStateOf(false) }
    val savedScroll = rememberSaveableStateHolder()
    BackHandler(enabled = selectedPeriod == null && !deleting) { if (!state.goalSaving) viewModel.archiveBack() }
    Column(Modifier.fillMaxSize().testTag("time-goal-archive")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(viewModel::archiveBack, enabled = !state.goalSaving) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, if (showingHistory) "Back to archive" else "Back to Time Goals")
            }
            Text(if (showingHistory) "Goal history" else "Archived goals", style = type.sectionTitle, color = colors.on)
        }
        val loading = if (showingHistory) state.historyLoading else state.archiveLoading
        val error = if (showingHistory) state.historyError else state.archiveError
        if (error != null) {
            val captured = if (showingHistory) history?.capturedAt else state.archive?.capturedAt
            val timezone = if (showingHistory) history?.timezone else state.archive?.timezone
            val updated = if (captured != null && timezone != null) captured.atZone(timezone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) else null
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.offline && updated != null) "Offline · last updated $updated\nUnsynced tracked time is not included." else error,
                    Modifier.weight(1f), style = type.bodySmall, color = colors.onVariant)
                TextButton(viewModel::refresh, enabled = !loading && !state.goalSaving) { Text("Retry") }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        savedScroll.SaveableStateProvider(state.historyGoalId?.let { "history-$it" } ?: "archive") {
            LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = TimeboxDimens.bottomInset),
                modifier = Modifier.weight(1f).testTag("time-goal-archive-list")) {
                if (!showingHistory) {
                    item { Text("Past goals, with their targets and results.", style = type.bodySmall, color = colors.onVariant,
                        modifier = Modifier.padding(vertical = 12.dp)) }
                    if (state.archive?.goals?.isEmpty() == true) item {
                        Text("No archived goals", style = type.sectionTitle, modifier = Modifier.padding(top = 24.dp))
                        Text("Archive a goal to stop future targets and keep its history here.", style = type.bodySmall,
                            color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(state.archive?.goals ?: emptyList(), key = { it.id }) { goal ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = !state.goalSaving) { viewModel.openGoalHistory(goal.id) }.padding(vertical = 18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(goal.taskType, Modifier.weight(1f), style = type.body, color = colors.on)
                                Icon(Icons.Outlined.ChevronRight, "Open ${goal.taskType} history", tint = colors.onVariant)
                            }
                            Text("${goalCadence(goal.unit, goal.interval)} · last target ${goalDuration(goal.targetMinutes)}",
                                style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
                            Text(goalRange(goal.startDate, goal.endDate), style = type.bodySmall, color = colors.onVariant)
                            Text("Archived ${goal.endDate.format(goalDateFormat)}", style = type.bodySmall, color = colors.onVariant,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                        HorizontalDivider(color = colors.hairline)
                    }
                } else if (history != null) {
                    item {
                        Text(history.goal.taskType, style = type.sectionTitle, color = colors.on, modifier = Modifier.padding(top = 12.dp))
                        Text("${goalCadence(history.goal.unit, history.goal.interval)} · ${goalRange(history.goal.startDate, history.goal.endDate)}",
                            style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
                        Text("Archived ${history.goal.endDate.format(goalDateFormat)}", style = type.label, color = colors.on, modifier = Modifier.padding(top = 12.dp))
                        Text("History is retained. To start again, create a new goal.", style = type.bodySmall, color = colors.onVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                    }
                    items(history.periods, key = { it.start.toString() }) { period ->
                        Column(Modifier.fillMaxWidth().clickable { selectedPeriod = period.start.toString(); viewModel.clearGoalError() }.padding(vertical = 16.dp)) {
                            Text(goalRange(period.start, period.end), style = type.bodySmall, color = colors.onVariant)
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${goalDuration((period.durationSeconds / 60).toInt())} / ${goalDuration(period.targetMinutes)}",
                                    Modifier.weight(1f), style = type.label, color = colors.on)
                                Text(history.goal.inPeriod(period).outcomeLabel(), style = type.bodySmall, color = colors.onVariant)
                            }
                            LinearProgressIndicator(progress = { (period.durationSeconds / (period.targetMinutes * 60)).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(3.dp), color = colors.actual, trackColor = colors.low)
                            Text("View period & blocks", style = type.bodySmall, color = colors.on, modifier = Modifier.padding(top = 10.dp))
                        }
                        HorizontalDivider(color = colors.hairline)
                    }
                    if (history.nextBefore != null) item {
                        TextButton({ viewModel.loadHistory(more = true) }, enabled = !loading && !state.offline && !state.goalSaving) { Text("Show earlier periods") }
                    }
                    item {
                        Text("Results reflect recorded time and later corrections.", style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(top = 16.dp))
                        TextButton({ deleting = true; viewModel.clearGoalError() }, enabled = !loading && !state.offline && !state.goalSaving) { Text("Delete goal", color = colors.error) }
                    }
                }
            }
        }
    }
    history?.let { report ->
        report.periods.find { it.start.toString() == selectedPeriod }?.let { period ->
            TimeGoalDetails(report.goal.inPeriod(period), state.archive?.today ?: report.goal.endDate, report.timezone,
                state, viewModel, onDismiss = { selectedPeriod = null })
        }
        if (deleting) TimeGoalActionDialog(report.goal.id, "delete", state, viewModel,
            onCancel = { deleting = false }, onSaved = { deleting = false })
    }
}
