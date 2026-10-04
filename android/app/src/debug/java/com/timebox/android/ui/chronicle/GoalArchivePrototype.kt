package com.timebox.android.ui.chronicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.components.TimeboxTopBar
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate

// Issue 306 experiment. All goals, records and mutations below are local sample data.
private val studyToday = LocalDate.of(2026, 10, 4)
private val studyWeek = studyToday.minusDays(6)
private data class ArchivePeriod(val start: LocalDate, val end: LocalDate, val target: Int, val actual: Int)
private data class ArchiveGoal(
    val id: Int, val name: String, val cadence: String, val end: LocalDate?, val periods: List<ArchivePeriod>,
) {
    val start get() = periods.last().start
}
private fun sampleArchiveGoals(): List<ArchiveGoal> {
    fun week(offset: Long, target: Int, actual: Int) = ArchivePeriod(studyWeek.plusWeeks(offset), studyWeek.plusWeeks(offset).plusDays(6), target, actual)
    return listOf(
        ArchiveGoal(1, "Exercise", "Every week", null, listOf(week(0, 240, 150), week(-1, 240, 270), week(-2, 180, 190))),
        ArchiveGoal(2, "Leisure / Reading", "Every day", null, (0L..20L).map { days ->
            ArchivePeriod(studyToday.minusDays(days), studyToday.minusDays(days), 30, if (days % 3 == 0L) 20 else 35)
        }),
        ArchiveGoal(3, "Learning / Study", "Every 2 weeks", studyWeek.minusDays(1), listOf(
            ArchivePeriod(studyWeek.minusWeeks(2), studyWeek.minusDays(1), 480, 420),
            ArchivePeriod(studyWeek.minusWeeks(4), studyWeek.minusWeeks(2).minusDays(1), 360, 390),
            ArchivePeriod(studyWeek.minusWeeks(6), studyWeek.minusWeeks(4).minusDays(1), 360, 300),
        )),
        ArchiveGoal(4, "Exercise / Strength", "Every week", studyWeek.minusDays(5), listOf(
            ArchivePeriod(studyWeek.minusWeeks(1), studyWeek.minusDays(5), 120, 45), week(-2, 120, 135), week(-3, 120, 100),
        )),
        ArchiveGoal(5, "Leisure / Reading", "Every day", studyWeek.minusDays(8), (8L..24L).map { days ->
            ArchivePeriod(studyWeek.minusDays(days), studyWeek.minusDays(days), 20, if (days % 2 == 0L) 25 else 15)
        }),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalArchivePrototype(initialVariant: String) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var variant by rememberSaveable { mutableStateOf(initialVariant) }
    var goals by remember { mutableStateOf(sampleArchiveGoals()) }
    var archive by rememberSaveable { mutableStateOf(false) }
    var week by remember { mutableStateOf(studyWeek) }
    var historyId by rememberSaveable { mutableStateOf<Int?>(null) }
    var historyWeek by remember { mutableStateOf(studyWeek) }
    var selected by remember { mutableStateOf<Pair<Int, ArchivePeriod>?>(null) }
    var action by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var options by rememberSaveable { mutableStateOf(false) }
    var includePast by rememberSaveable { mutableStateOf(true) }
    var periodList by rememberSaveable { mutableStateOf(true) }
    var offline by rememberSaveable { mutableStateOf(false) }
    var emptyArchive by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val history = goals.find { it.id == historyId }
    fun openHistory(goal: ArchiveGoal) {
        historyId = goal.id
        val last = goal.end ?: studyToday
        historyWeek = last.minusDays((last.dayOfWeek.value - 1).toLong())
    }
    BackHandler(enabled = selected == null && action == null && !options && (historyId != null || archive)) {
        if (historyId != null) historyId = null else archive = false
    }
    Column(Modifier.fillMaxSize()) {
        TimeboxTopBar("Chronicle", "Time Goals")
        // The production Chronicle component supplies the surrounding tab layout.
        ChronicleScreen(
            state = ChronicleUiState(view = ChronicleView.TimeGoals, loading = false),
            onPrevMonth = {}, onNextMonth = {}, onThisMonth = {}, onOpenDay = {}, onRetry = {},
            onSelectView = { notice = "This study focuses on Time Goals. Use the bottom navigation to leave." },
            timeGoalsContent = {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(colors.low).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Prototype · sample data", Modifier.weight(1f), style = type.bodySmall)
                        TextButton({ options = true }) { Text("Compare") }
                    }
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(variant == "page", { variant = "page" }, label = { Text("A · Archive page") })
                        FilterChip(variant == "switch", { variant = "switch" }, label = { Text("B · Switch") })
                    }
                    if (offline) Text("Offline · showing sample data saved 4 Oct, 12:00", Modifier.padding(12.dp), style = type.bodySmall)
                    notice?.let {
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, Modifier.weight(1f), style = type.bodySmall)
                            TextButton({ notice = null }) { Text("Dismiss") }
                        }
                    }
                    if (history != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton({ historyId = null }) { Icon(Icons.Outlined.ArrowBack, "Back to goals") }
                            Text("Goal history", style = type.sectionTitle)
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                            Text(history.name, style = type.sectionTitle)
                            Text("${history.cadence} · ${goalRange(history.start, history.end ?: studyToday)}", style = type.bodySmall,
                                color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
                            Text("Archived ${(history.end ?: studyToday).format(goalDateFormat)}", style = type.label, modifier = Modifier.padding(top = 12.dp))
                            Text("History is retained. To start again, create a new goal.", style = type.bodySmall,
                                color = colors.onVariant, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                            if (!periodList) ArchiveWeekControls(historyWeek, history.start, history.end ?: studyToday, !offline) { historyWeek = it }
                            val periods = if (periodList) history.periods else history.periods.filter { it.end >= historyWeek && it.start <= historyWeek.plusDays(6) }
                            periods.forEach { period -> ArchiveResultRow(history, period) { selected = history.id to period } }
                            Text("Results reflect recorded time and later corrections.", style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(top = 16.dp))
                            TextButton({ action = "delete" to history.id }, enabled = !offline) { Text("Delete goal", color = colors.error) }
                            Spacer(Modifier.height(24.dp))
                        }
                    } else {
                        if (variant == "switch") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!archive, { archive = false }, label = { Text("Active") })
                                FilterChip(archive, { archive = true }, label = { Text("Archived") })
                            }
                        } else Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (archive) IconButton({ archive = false }) { Icon(Icons.Outlined.ArrowBack, "Back to Time Goals") }
                            Text(if (archive) "Archived goals" else "", Modifier.weight(1f), style = type.sectionTitle)
                            if (!archive) TextButton({ archive = true }) { Text("Archive") }
                        }
                        if (!archive) ArchiveWeekControls(week, studyWeek.minusWeeks(6), studyToday, !offline) { week = it }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                            Text(if (archive) "Past goals, with their targets and results." else "Recorded time, measured against each goal’s period",
                                style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(vertical = 8.dp))
                            val visible = if (archive) {
                                if (emptyArchive) emptyList() else goals.filter { it.end != null }.sortedByDescending { it.end }
                            } else goals.filter {
                                it.start <= week.plusDays(6) && (it.end == null || (includePast && week < studyWeek && it.end >= week))
                            }
                            if (visible.isEmpty()) {
                                Text(if (archive) "No archived goals" else "No goals for this week", style = type.sectionTitle, modifier = Modifier.padding(top = 24.dp))
                                Text(if (archive) "Archive a goal to stop future targets and keep its history here." else "You can still find past goals in the archive.",
                                    style = type.bodySmall, color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
                            }
                            visible.forEach { goal ->
                                if (archive) {
                                    Column(Modifier.fillMaxWidth().clickable { openHistory(goal) }.padding(vertical = 18.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(goal.name, Modifier.weight(1f), style = type.body)
                                            Icon(Icons.Outlined.ChevronRight, "Open ${goal.name} history")
                                        }
                                        Text("${goal.cadence} · last target ${goalDuration(goal.periods.first().target)}", style = type.bodySmall,
                                            color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
                                        Text("${goalRange(goal.start, goal.end!!)}", style = type.bodySmall, color = colors.onVariant)
                                        Text("${goal.periods.size} periods · Archived ${goal.end.format(goalDateFormat)}", style = type.bodySmall,
                                            color = colors.onVariant, modifier = Modifier.padding(top = 8.dp))
                                    }
                                    HorizontalDivider(color = colors.hairline)
                                } else {
                                    val periods = goal.periods.filter { it.end >= week && it.start <= week.plusDays(6) }
                                    val period = periods.firstOrNull()
                                    if (period != null) {
                                        Text(goal.name, style = type.body, modifier = Modifier.padding(top = 18.dp))
                                        Text(goal.cadence + if (goal.end != null) " · Archived ${goal.end.format(goalDateFormat)}" else "", style = type.bodySmall, color = colors.onVariant)
                                        ArchiveResultRow(goal, period) { selected = goal.id to period }
                                        if (goal.end != null) TextButton({ openHistory(goal) }) { Text("View goal history") }
                                    }
                                }
                            }
                            if (!archive) TextButton({
                                val nextId = (goals.maxOfOrNull { it.id } ?: 0) + 1
                                goals = goals + ArchiveGoal(nextId, "Learning / Study", "Every week", null,
                                    listOf(ArchivePeriod(studyWeek, studyToday, 120, 0)))
                                week = studyWeek
                                notice = "Added a sample goal. Creation uses the existing flow in production."
                            }, enabled = !offline) { Text("Add Time Goal") }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            },
        )
    }
    selected?.let { (id, period) -> goals.find { it.id == id }?.let { goal ->
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.sheet.copy(alpha = 1f), shape = TimeboxShapes.sheet) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text(goal.name, style = type.sectionTitle)
                Text(goalRange(period.start, period.end), style = type.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Text("${goalDuration(period.actual)} / ${goalDuration(period.target)}", style = type.display, modifier = Modifier.padding(vertical = 12.dp))
                Text(archiveOutcome(goal, period), style = type.label)
                if (goal.end != null) Text("Archived ${goal.end.format(goalDateFormat)} · history retained", style = type.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Text("Includes this Task Type and its descendants.\nReporting time zone · Asia/Singapore", style = type.bodySmall,
                    color = colors.onVariant, modifier = Modifier.padding(top = 12.dp))
                Text("Actual Blocks", style = type.sectionTitle, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
                if (period.actual == 0) Text("No recorded time in this period.", style = type.bodySmall)
                // Each sample block's credited minutes sum exactly to the period result.
                val amounts = if (period.actual == 0) emptyList() else if (period.start == period.end) listOf(period.actual)
                    else listOf(period.actual / 2, period.actual - period.actual / 2)
                amounts.forEachIndexed { index, minutes ->
                    val date = if (index == 0) period.start else period.end
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(goal.name, style = type.body)
                            Text("${date.format(goalDateFormat)} · 09:00–${java.time.LocalTime.of(9, 0).plusMinutes(minutes.toLong())}", style = type.bodySmall, color = colors.onVariant)
                        }
                        Text(goalDuration(minutes), style = type.label)
                    }
                    HorizontalDivider(color = colors.hairline)
                }
                Text("Durations show time credited within this period.", style = type.bodySmall, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    if (goal.end == null) TextButton({ action = "archive" to id }, enabled = !offline) { Text("Archive goal") }
                    TextButton({ action = "delete" to id }, enabled = !offline) { Text("Delete goal", color = colors.error) }
                }
                if (offline) Text("Connect to manage this goal.", style = type.bodySmall)
                TextButton({ selected = null }, Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    } }
    action?.let { (kind, id) ->
        AlertDialog(onDismissRequest = { action = null }, title = { Text(if (kind == "archive") "Archive this goal today?" else "Delete this goal?") },
            text = { Text(if (kind == "archive") "Stop future targets and keep this goal’s targets and results in the archive. The final period is excused if its target is unmet. Actual Blocks stay unchanged. This goal cannot be reactivated."
                else "Permanently remove this goal and all its targets and results, including its archived history. Actual Blocks stay unchanged.") },
            confirmButton = { TextButton({
                goals = if (kind == "archive") goals.map { if (it.id == id) it.copy(end = studyToday) else it } else goals.filterNot { it.id == id }
                selected = null; action = null; historyId = null
                notice = if (kind == "archive") "Goal archived. Find its history in the archive." else "Goal deleted. Actual Blocks are unchanged."
            }, enabled = !offline) { Text(if (kind == "archive") "Archive goal" else "Delete goal") } },
            dismissButton = { TextButton({ action = null }) { Text("Cancel") } })
    }
    if (options) AlertDialog(onDismissRequest = { options = false }, title = { Text("Prototype comparison") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("These controls are only for this design study. Sample date: 4 Oct 2026.", style = type.bodySmall)
            ArchiveOption("Include archived goals in past weeks", includePast) { includePast = it }
            ArchiveOption("History as a period list (off: weeks)", periodList) { periodList = it }
            ArchiveOption("Offline with retained data", offline) { offline = it }
            ArchiveOption("Empty archive", emptyArchive) { emptyArchive = it }
            TextButton({ goals = sampleArchiveGoals(); archive = false; historyId = null; selected = null; week = studyWeek; offline = false; emptyArchive = false; notice = null; options = false }) { Text("Reset sample data") }
        }
    }, confirmButton = { TextButton({ options = false }) { Text("Done") } })
}

private fun archiveOutcome(goal: ArchiveGoal, period: ArchivePeriod) = when {
    period.actual >= period.target -> "Met"
    goal.end == period.end -> "Excused · goal archived"
    period.end >= studyToday -> "${goalDuration(period.target - period.actual)} to go"
    else -> "Missed"
}

@Composable
private fun ArchiveResultRow(goal: ArchiveGoal, period: ArchivePeriod, onOpen: () -> Unit) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 16.dp)) {
        Text(goalRange(period.start, period.end), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${goalDuration(period.actual)} / ${goalDuration(period.target)}", Modifier.weight(1f), style = TimeboxTheme.type.label)
            Text(archiveOutcome(goal, period), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        }
        LinearProgressIndicator(progress = { (period.actual.toFloat() / period.target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(3.dp), color = colors.actual, trackColor = colors.low)
        Text("View period & blocks", style = TimeboxTheme.type.bodySmall, modifier = Modifier.padding(top = 10.dp))
    }
    HorizontalDivider(color = colors.hairline)
}

@Composable
private fun ArchiveWeekControls(week: LocalDate, earliest: LocalDate, latest: LocalDate, enabled: Boolean, onWeek: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onWeek(week.minusWeeks(1)) }, enabled = enabled && week > earliest) { Icon(Icons.Outlined.ChevronLeft, "Previous week") }
        Text(if (week == studyWeek) "This week" else goalRange(week, week.plusDays(6)), Modifier.weight(1f), style = TimeboxTheme.type.bodySmall)
        IconButton({ onWeek(week.plusWeeks(1)) }, enabled = enabled && week.plusWeeks(1) <= latest) { Icon(Icons.Outlined.ChevronRight, "Next week") }
    }
}

@Composable
private fun ArchiveOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = TimeboxTheme.type.bodySmall)
        Switch(checked, null)
    }
}
