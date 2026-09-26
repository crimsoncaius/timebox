package com.timebox.android.ui.chronicle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.Habit
import com.timebox.android.data.HabitDay
import com.timebox.android.data.HabitDayState
import com.timebox.android.data.HabitTotal
import com.timebox.android.data.HabitTotalTone
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val NAME_COLUMN_WIDTH = 92.dp
private val TOTAL_COLUMN_WIDTH = 50.dp
private val CELL_GAP = 3.dp
private val HABITS_SWIPE_THRESHOLD = 55.dp
private val weekRangeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val sheetDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

/** Short cadence under a Habit's name, e.g. "Mon · Wed · Fri" or "3× week". */
internal fun habitCadence(habit: Habit): String {
    val every = if (habit.interval > 1) "Every ${habit.interval} " else ""
    return when {
        habit.mode == RecurrenceMode.Quota -> "${habit.quotaCount ?: 0}× " + when (habit.frequency) {
            RecurrenceFrequency.Daily -> "day"
            RecurrenceFrequency.Weekly -> "week"
            RecurrenceFrequency.Monthly -> "month"
        }
        habit.frequency == RecurrenceFrequency.Daily -> if (habit.interval > 1) "${every}days" else "Daily"
        habit.frequency == RecurrenceFrequency.Weekly -> {
            val days = habit.weekdays.sorted().joinToString(" · ") {
                DayOfWeek.of(it + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            }
            if (habit.interval > 1) "$days · ${habit.interval} wk" else days
        }
        else -> "Day ${habit.monthDay ?: 1}" + if (habit.interval > 1) " · ${habit.interval} mo" else " monthly"
    }
}

/** The unit caption under a week total: days, sessions, or the month for month-to-date. */
internal fun habitTotalUnit(total: HabitTotal): String = when (total.unit) {
    "sessions" -> "sessions"
    "month" -> "in " + (total.month?.month?.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) ?: "month")
    else -> "days"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(viewModel: HabitsViewModel, state: HabitsUiState, onOpenRoutine: (Int) -> Unit) {
    val colors = TimeboxTheme.colors
    LaunchedEffect(viewModel) { viewModel.refresh() }
    var sessionSheet by remember { mutableStateOf<Pair<Int, LocalDate>?>(null) }
    var addSheet by remember { mutableStateOf(false) }
    val week = state.week

    when {
        week == null && state.loading -> { LoadingState(Modifier.fillMaxSize()); return }
        week == null && state.error != null -> {
            ErrorState(message = state.error, onRetry = viewModel::refresh, modifier = Modifier.fillMaxSize())
            return
        }
        week == null -> return
    }
    checkNotNull(week)
    val currentWeek = currentWeekStart(week)
    val canPrev = week.weekStart.isAfter(week.earliestWeekStart)
    val canNext = week.weekStart.isBefore(currentWeek)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding).padding(top = 2.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundIconButton(
                icon = Icons.Outlined.ChevronLeft, contentDescription = "Previous week",
                onClick = { viewModel.shiftWeek(-1) }, enabled = canPrev,
                tint = if (canPrev) colors.on else colors.onVariant.copy(alpha = 0.4f),
                diameter = 36.dp, background = colors.low, iconSize = 19.dp,
            )
            Box(
                Modifier.weight(1f).height(36.dp).clip(TimeboxShapes.field).background(colors.low)
                    .clickable(onClick = viewModel::thisWeek),
                contentAlignment = Alignment.Center,
            ) {
                val label = if (week.weekStart == currentWeek) "This week"
                else "${week.weekStart.format(weekRangeFormatter)} – ${week.weekStart.plusDays(6).format(weekRangeFormatter)}"
                Text(label, style = TimeboxTheme.type.sectionTitle, color = colors.on)
            }
            RoundIconButton(
                icon = Icons.Outlined.ChevronRight, contentDescription = "Next week",
                onClick = { viewModel.shiftWeek(1) }, enabled = canNext,
                tint = if (canNext) colors.on else colors.onVariant.copy(alpha = 0.4f),
                diameter = 36.dp, background = colors.low, iconSize = 19.dp,
            )
        }

        if (week.habits.isEmpty() && week.weekStart == currentWeek && week.earliestWeekStart == currentWeek) {
            HabitsEmptyState(onAdd = { addSheet = true; viewModel.loadCandidates() })
        } else {
            val shiftWeek by rememberUpdatedState(viewModel::shiftWeek)
            val thresholdPx = with(LocalDensity.current) { HABITS_SWIPE_THRESHOLD.toPx() }
            var drag by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier.weight(1f)
                    .pointerInput(week.weekStart) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                when {
                                    drag > thresholdPx -> shiftWeek(-1)
                                    drag < -thresholdPx -> shiftWeek(1)
                                }
                                drag = 0f
                            },
                            onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                        )
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = TimeboxDimens.screenPadding)
                    .padding(bottom = TimeboxDimens.bottomInset)
                    .testTag("habits-week"),
            ) {
                WeekdayHeader(week.weekStart, week.today)
                if (week.habits.isEmpty()) {
                    Text(
                        "No habits were active this week.",
                        style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                week.habits.forEach { habit ->
                    HabitRow(
                        habit = habit,
                        pending = state.pending,
                        onOpenRoutine = onOpenRoutine,
                        onTap = { day ->
                            if (habit.mode == RecurrenceMode.Scheduled && day.state == HabitDayState.Met) {
                                viewModel.untick(habit.templateId, day.date)
                            } else {
                                viewModel.tick(habit.templateId, day.date)
                            }
                        },
                        onLongPress = { day ->
                            if (habit.mode == RecurrenceMode.Quota) sessionSheet = habit.templateId to day.date
                        },
                    )
                }
                state.actionError?.let { message ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, color = colors.error, style = TimeboxTheme.type.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::dismissActionError) { Text("Dismiss") }
                    }
                }
                if (state.loading) {
                    CircularProgressIndicator(
                        Modifier.padding(top = 8.dp).size(18.dp).align(Alignment.CenterHorizontally),
                        color = colors.onVariant, strokeWidth = 2.dp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { addSheet = true; viewModel.loadCandidates() }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add habit")
                }
                Text(
                    "Tap a day to record it. Long-press a quota day to remove a session.",
                    style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    sessionSheet?.let { (templateId, date) ->
        val habit = week.habits.firstOrNull { it.templateId == templateId }
        val day = habit?.days?.firstOrNull { it.date == date }
        if (habit == null || day == null) {
            sessionSheet = null
            return@let
        }
        val busy = HabitCellKey(templateId, date) in state.pending
        ModalBottomSheet(
            onDismissRequest = { sessionSheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.sheet.copy(alpha = 1f),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text(habit.title, style = TimeboxTheme.type.sectionTitle, color = colors.on)
                Text(date.format(sheetDateFormatter), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val canRemove = day.count > 0 && !busy
                    RoundIconButton(
                        icon = Icons.Outlined.Remove, contentDescription = "Remove one session",
                        onClick = { viewModel.untick(templateId, date) }, enabled = canRemove,
                        tint = if (canRemove) colors.on else colors.onVariant.copy(alpha = 0.4f),
                        diameter = 44.dp, background = colors.low, iconSize = 20.dp,
                    )
                    Text(
                        if (day.count == 1) "1 session" else "${day.count} sessions",
                        style = TimeboxTheme.type.sectionTitle, color = colors.on,
                    )
                    RoundIconButton(
                        icon = Icons.Outlined.Add, contentDescription = "Add one session",
                        onClick = { viewModel.tick(templateId, date) }, enabled = !busy && day.tickable,
                        tint = colors.on, diameter = 44.dp, background = colors.low, iconSize = 20.dp,
                    )
                }
            }
        }
    }

    if (addSheet) {
        ModalBottomSheet(
            onDismissRequest = { addSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.sheet.copy(alpha = 1f),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text("Add habit", style = TimeboxTheme.type.sectionTitle, color = colors.on)
                Text(
                    "Choose a routine to track. Its past completions count.",
                    style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                )
                Spacer(Modifier.height(12.dp))
                val candidates = state.candidates
                when {
                    state.candidatesError != null -> Text(state.candidatesError, color = colors.error)
                    candidates == null -> CircularProgressIndicator(Modifier.size(20.dp), color = colors.onVariant, strokeWidth = 2.dp)
                    candidates.isEmpty() -> Text("Every active routine is already a habit.", color = colors.onVariant)
                    else -> candidates.forEach { candidate ->
                        Row(
                            Modifier.fillMaxWidth().clip(TimeboxShapes.field)
                                .clickable { viewModel.addHabit(candidate.id); addSheet = false }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(candidate.title, color = colors.on, style = TimeboxTheme.type.body)
                                Text(candidate.cadence, color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
                            }
                            Icon(Icons.Outlined.Add, contentDescription = "Track ${candidate.title} as a habit", tint = colors.onVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitsEmptyState(onAdd: () -> Unit) {
    val colors = TimeboxTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No habits yet", style = TimeboxTheme.type.sectionTitle, color = colors.on)
        Spacer(Modifier.height(6.dp))
        Text(
            "Choose routines to track here. Their past completions are included.",
            style = TimeboxTheme.type.bodySmall, color = colors.onVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add habit")
        }
    }
}

@Composable
private fun WeekdayHeader(weekStart: LocalDate, today: LocalDate) {
    val colors = TimeboxTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        Spacer(Modifier.width(NAME_COLUMN_WIDTH - CELL_GAP))
        (0L..6L).forEach { offset ->
            val date = weekStart.plusDays(offset)
            val isToday = date == today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                    style = TimeboxTheme.type.laneLabel.copy(fontSize = 9.sp),
                    color = if (isToday) colors.planned else colors.onVariant,
                )
                Text(
                    date.dayOfMonth.toString(),
                    style = TimeboxTheme.type.label.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
                    color = if (isToday) colors.planned else colors.on,
                )
            }
        }
        Text(
            "WEEK",
            style = TimeboxTheme.type.laneLabel.copy(fontSize = 9.sp),
            color = colors.onVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(TOTAL_COLUMN_WIDTH - CELL_GAP).align(Alignment.Bottom),
        )
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    pending: Set<HabitCellKey>,
    onOpenRoutine: (Int) -> Unit,
    onTap: (HabitDay) -> Unit,
    onLongPress: (HabitDay) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        Column(
            Modifier.width(NAME_COLUMN_WIDTH - CELL_GAP).clip(TimeboxShapes.block)
                .clickable { onOpenRoutine(habit.templateId) }
                .padding(end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(habit.title, style = TimeboxTheme.type.label, color = colors.on, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                habitCadence(habit), style = TimeboxTheme.type.bodySmall.copy(fontSize = 10.sp),
                color = colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        habit.days.forEach { day ->
            HabitCell(habit, day, HabitCellKey(habit.templateId, day.date) in pending, onTap, onLongPress)
        }
        HabitTotalView(habit.total, Modifier.width(TOTAL_COLUMN_WIDTH - CELL_GAP))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.HabitCell(
    habit: Habit,
    day: HabitDay,
    pending: Boolean,
    onTap: (HabitDay) -> Unit,
    onLongPress: (HabitDay) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val onActual = if (colors.actual.luminance() > 0.5f) Color.Black else Color.White
    val daily = habit.mode == RecurrenceMode.Quota && habit.frequency == RecurrenceFrequency.Daily
    val label = if (daily) "${day.count}/${day.target ?: habit.quotaCount ?: 0}" else day.count.toString()
    val background = when (day.state) {
        HabitDayState.Met, HabitDayState.Count -> colors.actual
        HabitDayState.Partial -> colors.actual.copy(alpha = 0.3f)
        HabitDayState.Missed, HabitDayState.Empty -> colors.low
        else -> Color.Transparent
    }
    val border = when (day.state) {
        HabitDayState.Open -> 1.5.dp to colors.planned
        HabitDayState.Upcoming -> 1.dp to colors.outlineVariant
        else -> null
    }
    val description = "${habit.title}, ${day.date}, " + when (day.state) {
        HabitDayState.NotDue -> "not due"
        HabitDayState.Excused -> "excused"
        HabitDayState.Upcoming -> "upcoming"
        HabitDayState.Open -> if (daily) "open, $label" else "open"
        HabitDayState.Met -> if (daily) "met, $label" else "done"
        HabitDayState.Missed -> if (daily) "missed, $label" else "missed"
        HabitDayState.Partial -> "missed, $label"
        HabitDayState.Count -> if (day.count == 1) "1 session" else "${day.count} sessions"
        HabitDayState.Empty -> "no sessions"
    }
    Box(
        Modifier.weight(1f).height(40.dp).clip(TimeboxShapes.cell).background(background)
            .then(if (border != null) Modifier.border(border.first, border.second, TimeboxShapes.cell) else Modifier)
            .alpha(if (pending) 0.5f else 1f)
            .semantics { contentDescription = description }
            .testTag("habit-${habit.templateId}-${day.date}")
            .then(
                if (day.tickable && !pending) Modifier.combinedClickable(
                    onClick = { onTap(day) },
                    onLongClick = { onLongPress(day) },
                ) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val small = TimeboxTheme.type.label.copy(fontSize = 11.sp)
        when (day.state) {
            HabitDayState.NotDue -> Box(Modifier.size(3.dp).background(colors.outlineVariant, CircleShape))
            HabitDayState.Excused -> Box(Modifier.width(10.dp).height(1.5.dp).background(colors.outlineVariant))
            HabitDayState.Missed -> if (daily) Text(label, style = small, color = colors.onVariant)
                else Icon(Icons.Outlined.Close, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(14.dp))
            HabitDayState.Met -> if (daily) Text(label, style = small.copy(fontWeight = FontWeight.Bold), color = onActual)
                else Icon(Icons.Outlined.Check, contentDescription = null, tint = onActual, modifier = Modifier.size(18.dp))
            HabitDayState.Partial -> Text(label, style = small, color = colors.on)
            HabitDayState.Count -> Text(label, style = TimeboxTheme.type.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = onActual)
            HabitDayState.Open -> if (daily) Text(label, style = small, color = colors.on)
            HabitDayState.Upcoming, HabitDayState.Empty -> Unit
        }
    }
}

@Composable
private fun HabitTotalView(total: HabitTotal, modifier: Modifier) {
    val colors = TimeboxTheme.colors
    val met = total.tone == HabitTotalTone.Met
    val unit = habitTotalUnit(total)
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "${total.done} of ${total.target} $unit" },
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            "${total.done}/${total.target}",
            style = TimeboxTheme.type.label.copy(fontSize = 12.sp, fontWeight = if (met) FontWeight.Bold else FontWeight.Normal),
            color = when (total.tone) {
                HabitTotalTone.Met -> colors.actual
                HabitTotalTone.Missed -> colors.onVariant
                HabitTotalTone.Open -> colors.on
            },
            maxLines = 1,
        )
        Text(
            unit,
            style = TimeboxTheme.type.bodySmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
            color = colors.onVariant, maxLines = 1,
        )
    }
}
