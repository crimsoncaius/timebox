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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.Habit
import com.timebox.android.data.HabitDay
import com.timebox.android.data.HabitDayState
import com.timebox.android.data.HabitItem
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val NAME_COLUMN_WIDTH = 92.dp
/** Guide line plus its padding, which indents Checklist Item rows under their series. */
private val ITEM_INDENT = 12.dp
private val TOTAL_COLUMN_WIDTH = 50.dp
private const val SLOW_SAVE_MILLIS = 1_000L
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
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.refresh()
        while (true) { delay(60_000); viewModel.refresh() }
    }
    var sessionSheet by remember { mutableStateOf<Pair<Int, LocalDate>?>(null) }
    var addSheet by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var createGoal by remember { mutableStateOf(false) }
    val week = state.week
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { AppPreferences(context) }
    val collapsed by preferences.collapsedHabitGroups.collectAsState(initial = emptySet())
    val preferenceScope = rememberCoroutineScope()

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
    val earliest = minOf(week.earliestWeekStart, state.goals?.earliestWeekStart ?: week.earliestWeekStart)
    val canPrev = week.weekStart.isAfter(earliest) && !state.offline && !state.goalSaving
    val canNext = week.weekStart.isBefore(currentWeek) && !state.offline && !state.goalSaving

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
                    .clickable(enabled = !state.offline && !state.goalSaving, onClick = viewModel::thisWeek),
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
            Box {
                IconButton(onClick = { addMenu = true }, enabled = !state.offline && !state.goalSaving) {
                    Icon(Icons.Outlined.Add, "Add")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("Habit") }, onClick = { addMenu = false; addSheet = true; viewModel.loadCandidates() })
                    DropdownMenuItem(text = { Text("Time Goal") }, onClick = { addMenu = false; createGoal = true })
                }
            }
        }

        (state.error ?: if (state.offline) "Offline" else null)?.let { message ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val updated = state.goals?.let { it.capturedAt.atZone(it.timezone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) }
                Text(if (state.offline) "Offline · last updated $updated\nUnsynced tracked time is not included." else message,
                    Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                TextButton(onClick = viewModel::refresh) { Text("Retry") }
            }
        }
        run {
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
                    val open = habit.items.isNotEmpty() && habit.templateId !in collapsed
                    val onToggle = {
                        preferenceScope.launch { preferences.setHabitGroupCollapsed(habit.templateId, open) }
                        Unit
                    }
                    if (habit.tracked) {
                        HabitRow(
                            habit = habit,
                            open = open,
                            pending = state.pending,
                            onOpenRoutine = onOpenRoutine,
                            onToggle = onToggle,
                            onTap = { day ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                    } else {
                        HabitHeading(habit, open, onOpenRoutine, onToggle)
                    }
                    if (open) {
                        HabitItemRows(habit, state.pending, onOpenRoutine) { item, day ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (day.state == HabitDayState.Met) viewModel.untickItem(habit.templateId, item.itemId, day.date)
                            else viewModel.tickItem(habit.templateId, item.itemId, day.date)
                        }
                    }
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
                if (week.habits.isNotEmpty()) Text(
                    "Tap a day to record it. Long-press a quota day to remove a session.",
                    style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TimeGoalsContent(state, viewModel, createGoal, onDismissCreate = { createGoal = false })
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
                    val canRemove = day.count > 0 && day.tickable
                    RoundIconButton(
                        icon = Icons.Outlined.Remove, contentDescription = "Remove one session",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.untick(templateId, date)
                        },
                        enabled = canRemove,
                        tint = if (canRemove) colors.on else colors.onVariant.copy(alpha = 0.4f),
                        diameter = 44.dp, background = colors.low, iconSize = 20.dp,
                    )
                    Text(
                        if (day.count == 1) "1 session" else "${day.count} sessions",
                        style = TimeboxTheme.type.sectionTitle, color = colors.on,
                    )
                    RoundIconButton(
                        icon = Icons.Outlined.Add, contentDescription = "Add one session",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.tick(templateId, date)
                        },
                        enabled = day.tickable,
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
                    "Choose a routine, or one of its checklist items. Past completions and checks count.",
                    style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                )
                Spacer(Modifier.height(12.dp))
                val candidates = state.candidates
                when {
                    state.candidatesError != null -> Text(state.candidatesError, color = colors.error)
                    candidates == null -> CircularProgressIndicator(Modifier.size(20.dp), color = colors.onVariant, strokeWidth = 2.dp)
                    candidates.isEmpty() -> Text("Every active routine and checklist item is already a habit.", color = colors.onVariant)
                    else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        candidates.forEach { candidate ->
                            Row(
                                Modifier.fillMaxWidth().clip(TimeboxShapes.field)
                                    .clickable(enabled = !candidate.trackAsHabit) { viewModel.addHabit(candidate.id); addSheet = false }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.title, color = colors.on, style = TimeboxTheme.type.body)
                                    Text(
                                        candidate.cadence + if (candidate.trackAsHabit) " · tracked" else "",
                                        color = colors.onVariant, style = TimeboxTheme.type.bodySmall,
                                    )
                                }
                                if (!candidate.trackAsHabit) {
                                    Icon(Icons.Outlined.Add, contentDescription = "Track ${candidate.title} as a habit", tint = colors.onVariant)
                                }
                            }
                            candidate.checklistItems.sortedBy { it.position }.filterNot { it.trackAsHabit }.forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 16.dp).clip(TimeboxShapes.field)
                                        .clickable { viewModel.addHabitItem(candidate, item.id); addSheet = false }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        item.title, color = colors.on, style = TimeboxTheme.type.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.Outlined.Add, contentDescription = "Track ${item.title} as a habit",
                                        tint = colors.onVariant, modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
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

/** Cadence, plus the item count while a series' Checklist Items are hidden. */
private fun habitCaption(habit: Habit, open: Boolean): String {
    val count = habit.items.size
    return if (count == 0 || open) habitCadence(habit)
    else habitCadence(habit) + if (count == 1) " · 1 item" else " · $count items"
}

@Composable
private fun GroupChevron(habit: Habit, open: Boolean, onToggle: () -> Unit) {
    Icon(
        if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
        contentDescription = if (open) "Hide ${habit.title} items" else "Show ${habit.title} items",
        tint = TimeboxTheme.colors.onVariant,
        modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onToggle).padding(1.dp),
    )
}

@Composable
private fun HabitRow(
    habit: Habit,
    open: Boolean,
    pending: Set<HabitCellKey>,
    onOpenRoutine: (Int) -> Unit,
    onToggle: () -> Unit,
    onTap: (HabitDay) -> Unit,
    onLongPress: (HabitDay) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = if (open) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        Row(Modifier.width(NAME_COLUMN_WIDTH - CELL_GAP), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clip(TimeboxShapes.block)
                    .clickable { onOpenRoutine(habit.templateId) }
                    .padding(end = 2.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Text(habit.title, style = TimeboxTheme.type.label, color = colors.on, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    habitCaption(habit, open), style = TimeboxTheme.type.bodySmall.copy(fontSize = 10.sp),
                    color = colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (habit.items.isNotEmpty()) GroupChevron(habit, open, onToggle)
        }
        val daily = habit.mode == RecurrenceMode.Quota && habit.frequency == RecurrenceFrequency.Daily
        habit.days.forEach { day ->
            HabitCell(
                title = habit.title, day = day, daily = daily, quotaCount = habit.quotaCount,
                pending = HabitCellKey(habit.templateId, day.date) in pending,
                height = 40.dp, tag = "habit-${habit.templateId}-${day.date}",
                onTap = onTap, onLongPress = onLongPress,
            )
        }
        HabitTotalView(habit.total, Modifier.width(TOTAL_COLUMN_WIDTH - CELL_GAP))
    }
}

/** A series that is not itself a Habit, shown only to head its tracked Checklist Items. */
@Composable
private fun HabitHeading(habit: Habit, open: Boolean, onOpenRoutine: (Int) -> Unit, onToggle: () -> Unit) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = if (open) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).clip(TimeboxShapes.block)
                .clickable { onOpenRoutine(habit.templateId) }
                .padding(vertical = 2.dp),
        ) {
            Text(habit.title, style = TimeboxTheme.type.label, color = colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                habitCaption(habit, open) + " · routine not tracked",
                style = TimeboxTheme.type.bodySmall.copy(fontSize = 10.sp),
                color = colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        GroupChevron(habit, open, onToggle)
    }
}

/** A series' tracked Checklist Items, indented behind a guide line. */
@Composable
private fun HabitItemRows(
    habit: Habit,
    pending: Set<HabitCellKey>,
    onOpenRoutine: (Int) -> Unit,
    onTap: (HabitItem, HabitDay) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val rowHeight = 30.dp
    val rowGap = 4.dp
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Box(
            Modifier.padding(start = 4.dp, end = 6.dp).width(2.dp)
                .height((rowHeight + rowGap) * habit.items.size - rowGap)
                .background(colors.outlineVariant),
        )
        Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
            habit.items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                ) {
                    Text(
                        item.title,
                        style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp),
                        color = colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(NAME_COLUMN_WIDTH - ITEM_INDENT - CELL_GAP)
                            .clickable { onOpenRoutine(habit.templateId) },
                    )
                    item.days.forEach { day ->
                        HabitCell(
                            title = item.title, day = day, daily = false, quotaCount = null,
                            pending = HabitCellKey(habit.templateId, day.date, item.itemId) in pending,
                            height = rowHeight, tag = "habit-${habit.templateId}-item-${item.itemId}-${day.date}",
                            onTap = { onTap(item, it) }, onLongPress = {},
                        )
                    }
                    HabitTotalView(item.total, Modifier.width(TOTAL_COLUMN_WIDTH - CELL_GAP))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.HabitCell(
    title: String,
    day: HabitDay,
    daily: Boolean,
    quotaCount: Int?,
    pending: Boolean,
    height: Dp,
    tag: String,
    onTap: (HabitDay) -> Unit,
    onLongPress: (HabitDay) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val onActual = if (colors.actual.luminance() > 0.5f) Color.Black else Color.White
    // The tap already shows; only a save that is taking noticeably long dims the cell.
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        slow = false
        if (pending) {
            delay(SLOW_SAVE_MILLIS)
            slow = true
        }
    }
    val label = if (daily) "${day.count}/${day.target ?: quotaCount ?: 0}" else day.count.toString()
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
    val description = "$title, ${day.date}, " + when (day.state) {
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
        Modifier.weight(1f).height(height).clip(TimeboxShapes.cell).background(background)
            .then(if (border != null) Modifier.border(border.first, border.second, TimeboxShapes.cell) else Modifier)
            .alpha(if (slow) 0.6f else 1f)
            .semantics { contentDescription = description }
            .testTag(tag)
            .then(
                // The cell's own change acknowledges a tap; overlapping ripples from quick
                // repeat taps left the cell's fill undrawn.
                if (day.tickable) Modifier.combinedClickable(
                    interactionSource = null,
                    indication = null,
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
