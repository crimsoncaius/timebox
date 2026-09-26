package com.timebox.android.ui.chronicle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.components.TimeboxTopBar
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.random.Random

// Issue 214: debug-route experiment for the Chronicle Habits view.
// Every tick is local sample state; nothing reaches the API.

/**
 * Round 3 comparison: how the week total looks. Round 1 chose the name column,
 * round 2 a total on every row, round 3 [Labeled]. The others stay reachable by URL only.
 */
private enum class TotalStyle(val label: String, val arg: String) {
    Fraction("Fraction", "fraction"),
    Labeled("With unit", "labeled"),
    Ring("Ring", "ring"),
    Pill("Pill", "pill"),
}

private enum class QuotaPeriod(val noun: String) { Day("day"), Week("week"), Month("month") }

private sealed interface HabitRule {
    data class Scheduled(val weekdays: Set<DayOfWeek>) : HabitRule
    data class Quota(val count: Int, val period: QuotaPeriod) : HabitRule
}

private data class SampleHabit(
    val id: String,
    val name: String,
    val rule: HabitRule,
    val start: LocalDate,
    val pausedFrom: LocalDate? = null,
)

private enum class CellKind { NotDue, Excused, Future, Open, Met, Missed, Partial, Count, Empty }

private data class CellState(val kind: CellKind, val count: Int, val label: String?)

private val ALL_DAYS = DayOfWeek.entries.toSet()
private val weekRangeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun cadence(rule: HabitRule): String = when (rule) {
    is HabitRule.Scheduled ->
        if (rule.weekdays == ALL_DAYS) "Daily"
        else rule.weekdays.sorted().joinToString(" · ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    is HabitRule.Quota -> "${rule.count}× ${rule.period.noun}"
}

private fun sampleHabits(today: LocalDate): List<SampleHabit> {
    val week = today.weekStart()
    return listOf(
        SampleHabit("gym", "Gym", HabitRule.Scheduled(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), week.minusWeeks(10)),
        SampleHabit("meditate", "Meditate", HabitRule.Scheduled(ALL_DAYS), week.minusWeeks(6)),
        SampleHabit("read", "Read before bed", HabitRule.Quota(3, QuotaPeriod.Week), week.minusWeeks(8)),
        SampleHabit("water", "Drink a full bottle of water", HabitRule.Quota(2, QuotaPeriod.Day), week.minusWeeks(4)),
        SampleHabit("parents", "Call parents", HabitRule.Quota(4, QuotaPeriod.Month), week.minusWeeks(12)),
        SampleHabit(
            "run", "Run",
            HabitRule.Scheduled(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)),
            week.minusWeeks(5),
            pausedFrom = week.plusDays(3),
        ),
        SampleHabit("review", "Weekly review", HabitRule.Scheduled(setOf(DayOfWeek.SUNDAY)), week.minusWeeks(9)),
    )
}

/** Routines that exist but are not yet Habits, offered by Add habit. */
private fun optInCandidates(today: LocalDate): List<SampleHabit> {
    val week = today.weekStart()
    return listOf(
        SampleHabit("stretch", "Stretch", HabitRule.Quota(1, QuotaPeriod.Day), week.minusWeeks(3)),
        SampleHabit("journal", "Journal", HabitRule.Quota(4, QuotaPeriod.Week), week.minusWeeks(7)),
        SampleHabit("bins", "Take out the bins", HabitRule.Scheduled(setOf(DayOfWeek.TUESDAY)), week.minusWeeks(20)),
    )
}

/** Hand-authored Monday-to-Sunday counts for the current week; later days are ignored. */
private val currentWeekCounts = mapOf(
    "gym" to listOf(1, 0, 0, 0, 1, 0, 0),
    "meditate" to listOf(1, 1, 1, 0, 1, 1, 0),
    "read" to listOf(0, 1, 0, 1, 0, 0, 0),
    "water" to listOf(2, 1, 2, 2, 2, 1, 0),
    "parents" to listOf(0, 1, 0, 0, 0, 0, 0),
    "run" to listOf(0, 1, 0, 0, 0, 0, 0),
    "review" to listOf(0, 0, 0, 0, 0, 0, 0),
)

private fun isDue(habit: SampleHabit, date: LocalDate): Boolean = when (val rule = habit.rule) {
    is HabitRule.Scheduled -> date.dayOfWeek in rule.weekdays
    is HabitRule.Quota -> true
}

private fun isExcused(habit: SampleHabit, date: LocalDate): Boolean =
    date < habit.start || (habit.pausedFrom != null && date >= habit.pausedFrom)

private fun seededCount(habit: SampleHabit, date: LocalDate): Int {
    val random = Random("${habit.id}|$date".hashCode())
    val roll = random.nextFloat()
    return when (val rule = habit.rule) {
        is HabitRule.Scheduled -> if (roll < 0.76f) 1 else 0
        is HabitRule.Quota -> when (rule.period) {
            QuotaPeriod.Day -> when {
                roll < 0.62f -> rule.count
                roll < 0.86f -> (rule.count - 1).coerceAtLeast(0)
                else -> 0
            }
            QuotaPeriod.Week -> when {
                roll < 0.36f -> 1
                roll < 0.42f -> 2
                else -> 0
            }
            QuotaPeriod.Month -> if (roll < 0.13f) 1 else 0
        }
    }
}

private class HabitsSample(val today: LocalDate) {
    val habits = mutableStateListOf<SampleHabit>().apply { addAll(sampleHabits(today)) }
    val overrides = mutableStateMapOf<String, Int>()

    fun reset(empty: Boolean) {
        habits.clear()
        if (!empty) habits.addAll(sampleHabits(today))
        overrides.clear()
    }

    fun count(habit: SampleHabit, date: LocalDate): Int {
        if (date > today || isExcused(habit, date) || !isDue(habit, date)) return 0
        overrides["${habit.id}|$date"]?.let { return it }
        val week = today.weekStart()
        return when {
            date >= week -> currentWeekCounts[habit.id]?.getOrNull(date.dayOfWeek.value - 1) ?: 0
            else -> seededCount(habit, date)
        }
    }

    fun set(habit: SampleHabit, date: LocalDate, value: Int) {
        overrides["${habit.id}|$date"] = value.coerceAtLeast(0)
    }

    fun cell(habit: SampleHabit, date: LocalDate): CellState {
        val rule = habit.rule
        if (!isDue(habit, date)) return CellState(CellKind.NotDue, 0, null)
        if (isExcused(habit, date)) return CellState(CellKind.Excused, 0, null)
        if (date > today) return CellState(CellKind.Future, 0, null)
        val count = count(habit, date)
        return when (rule) {
            is HabitRule.Scheduled -> when {
                count > 0 -> CellState(CellKind.Met, count, null)
                date == today -> CellState(CellKind.Open, 0, null)
                else -> CellState(CellKind.Missed, 0, null)
            }
            is HabitRule.Quota -> when (rule.period) {
                QuotaPeriod.Day -> {
                    val label = "$count/${rule.count}"
                    when {
                        count >= rule.count -> CellState(CellKind.Met, count, label)
                        date == today -> CellState(CellKind.Open, count, label)
                        count > 0 -> CellState(CellKind.Partial, count, label)
                        else -> CellState(CellKind.Missed, 0, label)
                    }
                }
                else -> when {
                    count > 0 -> CellState(CellKind.Count, count, count.toString())
                    date == today -> CellState(CellKind.Open, 0, null)
                    else -> CellState(CellKind.Empty, 0, null)
                }
            }
        }
    }

    /** Appears in a week only when its series was active on at least one day of it. */
    fun visibleIn(habit: SampleHabit, weekStart: LocalDate): Boolean =
        (0L..6L).any { !isExcused(habit, weekStart.plusDays(it)) }

    fun earliestWeek(): LocalDate =
        (habits.minOfOrNull { it.start } ?: today).weekStart()
}

private enum class TotalTone { Met, Missed, Open }

/** [done] out of [target] [unit]; [caption] names the month for month-to-date totals. */
private data class RowTotal(val done: Int, val target: Int, val unit: String, val caption: String?, val tone: TotalTone) {
    val text: String get() = "$done/$target"
}

private fun HabitsSample.total(habit: SampleHabit, weekStart: LocalDate): RowTotal {
    val days = (0L..6L).map { weekStart.plusDays(it) }
    val weekEnded = weekStart.plusDays(6) < today
    fun tone(done: Int, target: Int, ended: Boolean) = when {
        target > 0 && done >= target -> TotalTone.Met
        ended -> TotalTone.Missed
        else -> TotalTone.Open
    }
    val rule = habit.rule
    return when {
        rule is HabitRule.Quota && rule.period == QuotaPeriod.Week -> {
            val sum = days.sumOf { count(habit, it) }
            RowTotal(sum, rule.count, "sessions", null, tone(sum, rule.count, weekEnded))
        }
        rule is HabitRule.Quota && rule.period == QuotaPeriod.Month -> {
            val anchor = days.lastOrNull { it <= today } ?: weekStart
            val month = YearMonth.from(anchor)
            var cursor = month.atDay(1)
            var sum = 0
            val through = minOf(month.atEndOfMonth(), weekStart.plusDays(6), today)
            while (cursor <= through) {
                sum += count(habit, cursor)
                cursor = cursor.plusDays(1)
            }
            val name = month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            RowTotal(sum, rule.count, "in $name", name, tone(sum, rule.count, month.atEndOfMonth() < today))
        }
        else -> {
            val periods = days.filter { isDue(habit, it) && !isExcused(habit, it) }
            val met = periods.count { cell(habit, it).kind == CellKind.Met }
            RowTotal(met, periods.size, "days", null, tone(met, periods.size, weekEnded))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsPrototype(initialTotal: String, initialScenario: String) {
    val today = remember { LocalDate.now() }
    val sample = remember { HabitsSample(today).also { if (initialScenario == "empty") it.reset(empty = true) } }
    var totalStyle by remember { mutableStateOf(TotalStyle.entries.firstOrNull { it.arg == initialTotal } ?: TotalStyle.Labeled) }
    var weekStart by remember { mutableStateOf(today.weekStart()) }
    var sessionSheet by remember { mutableStateOf<Pair<SampleHabit, LocalDate>?>(null) }
    var addSheet by remember { mutableStateOf(false) }
    val colors = TimeboxTheme.colors

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        TimeboxTopBar(kicker = "Chronicle", title = "Habits")
        PrototypeSwitcher(
            totalStyle = totalStyle,
            onTotalStyle = { totalStyle = it },
            onSample = { sample.reset(empty = false); weekStart = today.weekStart() },
            onEmpty = { sample.reset(empty = true); weekStart = today.weekStart() },
        )
        ChronicleTabsWithHabits()

        val canPrev = weekStart > sample.earliestWeek()
        val canNext = weekStart < today.weekStart()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding).padding(top = 2.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundIconButton(
                icon = Icons.Outlined.ChevronLeft, contentDescription = "Previous week",
                onClick = { weekStart = weekStart.minusWeeks(1) }, enabled = canPrev,
                tint = if (canPrev) colors.on else colors.onVariant.copy(alpha = 0.4f),
                diameter = 36.dp, background = colors.low, iconSize = 19.dp,
            )
            Box(
                Modifier.weight(1f).height(36.dp).clip(TimeboxShapes.field).background(colors.low)
                    .clickable { weekStart = today.weekStart() },
                contentAlignment = Alignment.Center,
            ) {
                val label = if (weekStart == today.weekStart()) "This week"
                else "${weekStart.format(weekRangeFormatter)} – ${weekStart.plusDays(6).format(weekRangeFormatter)}"
                Text(label, style = TimeboxTheme.type.sectionTitle, color = colors.on)
            }
            RoundIconButton(
                icon = Icons.Outlined.ChevronRight, contentDescription = "Next week",
                onClick = { weekStart = weekStart.plusWeeks(1) }, enabled = canNext,
                tint = if (canNext) colors.on else colors.onVariant.copy(alpha = 0.4f),
                diameter = 36.dp, background = colors.low, iconSize = 19.dp,
            )
        }

        val visible = sample.habits.filter { sample.visibleIn(it, weekStart) }
        if (sample.habits.isEmpty()) {
            HabitsEmptyState(onAdd = { addSheet = true })
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = TimeboxDimens.screenPadding)
                    .padding(bottom = TimeboxDimens.bottomInset),
            ) {
                val onTap: (SampleHabit, LocalDate) -> Unit = { habit, date ->
                    when (habit.rule) {
                        is HabitRule.Scheduled -> sample.set(habit, date, if (sample.count(habit, date) > 0) 0 else 1)
                        is HabitRule.Quota -> sample.set(habit, date, sample.count(habit, date) + 1)
                    }
                }
                val onLongPress: (SampleHabit, LocalDate) -> Unit = { habit, date ->
                    if (habit.rule is HabitRule.Quota) sessionSheet = habit to date
                }
                NameColumnGrid(sample, visible, weekStart, totalStyle, onTap, onLongPress)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { addSheet = true }) {
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

    sessionSheet?.let { (habit, date) ->
        ModalBottomSheet(
            onDismissRequest = { sessionSheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.sheet.copy(alpha = 1f),
        ) {
            val count = sample.count(habit, date)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text(habit.name, style = TimeboxTheme.type.sectionTitle, color = colors.on)
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)),
                    style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundIconButton(
                        icon = Icons.Outlined.Remove, contentDescription = "Remove one session",
                        onClick = { sample.set(habit, date, count - 1) }, enabled = count > 0,
                        tint = if (count > 0) colors.on else colors.onVariant.copy(alpha = 0.4f),
                        diameter = 44.dp, background = colors.low, iconSize = 20.dp,
                    )
                    Text(
                        if (count == 1) "1 session" else "$count sessions",
                        style = TimeboxTheme.type.sectionTitle, color = colors.on,
                    )
                    RoundIconButton(
                        icon = Icons.Outlined.Add, contentDescription = "Add one session",
                        onClick = { sample.set(habit, date, count + 1) },
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
            val candidates = optInCandidates(today).filter { c -> sample.habits.none { it.id == c.id } } +
                sampleHabits(today).filter { c -> sample.habits.none { it.id == c.id } }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text("Add habit", style = TimeboxTheme.type.sectionTitle, color = colors.on)
                Text(
                    "Choose a routine to track. Its past completions count.",
                    style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (candidates.isEmpty()) {
                    Text("Every routine is already a habit.", color = colors.onVariant)
                }
                candidates.forEach { candidate ->
                    Row(
                        Modifier.fillMaxWidth().clip(TimeboxShapes.field)
                            .clickable { sample.habits.add(candidate); addSheet = false }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.name, color = colors.on, style = TimeboxTheme.type.body)
                            Text(cadence(candidate.rule), color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
                        }
                        Icon(Icons.Outlined.Add, contentDescription = "Track ${candidate.name} as a habit", tint = colors.onVariant)
                    }
                }
            }
        }
    }
}

/** Comparison controls for the design round; not proposed product UI. */
@Composable
private fun PrototypeSwitcher(
    totalStyle: TotalStyle,
    onTotalStyle: (TotalStyle) -> Unit,
    onSample: () -> Unit,
    onEmpty: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding).padding(bottom = 8.dp)
            .clip(TimeboxShapes.field).border(1.dp, colors.tertiary, TimeboxShapes.field)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("#214", style = TimeboxTheme.type.kicker, color = colors.tertiary)
        SwitchChip("Sample", selected = false, onClick = onSample)
        SwitchChip("Empty", selected = false, onClick = onEmpty)
    }
}

@Composable
private fun SwitchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    Text(
        label,
        style = TimeboxTheme.type.label,
        color = if (selected) colors.onTertiary else colors.on,
        modifier = Modifier.clip(TimeboxShapes.chip)
            .background(if (selected) colors.tertiary else colors.low)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Chronicle's view tabs with the proposed third view selected. Calendar and Trends are inert here. */
@Composable
private fun ChronicleTabsWithHabits() {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            listOf("Calendar", "Trends", "Habits").forEach { name ->
                val active = name == "Habits"
                Column(
                    Modifier.weight(1f).selectable(selected = active, role = Role.Tab) {}.padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(name, style = TimeboxTheme.type.sectionTitle, color = if (active) colors.on else colors.onVariant)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.4f).height(2.dp).background(if (active) colors.on else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.low))
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

private val NAME_COLUMN_WIDTH = 92.dp
private val TOTAL_COLUMN_WIDTH = 46.dp
private val CELL_GAP = 3.dp

@Composable
private fun WeekdayHeader(weekStart: LocalDate, today: LocalDate, leading: Dp, trailing: Dp, trailingLabel: String?) {
    val colors = TimeboxTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        if (leading > 0.dp) Spacer(Modifier.width(leading - CELL_GAP))
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
        if (trailing > 0.dp) {
            Text(
                trailingLabel.orEmpty(),
                style = TimeboxTheme.type.laneLabel.copy(fontSize = 9.sp),
                color = colors.onVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(trailing - CELL_GAP).align(Alignment.Bottom),
            )
        }
    }
}

@Composable
private fun NameColumnGrid(
    sample: HabitsSample,
    habits: List<SampleHabit>,
    weekStart: LocalDate,
    totalStyle: TotalStyle,
    onTap: (SampleHabit, LocalDate) -> Unit,
    onLongPress: (SampleHabit, LocalDate) -> Unit,
) {
    val colors = TimeboxTheme.colors
    WeekdayHeader(weekStart, sample.today, NAME_COLUMN_WIDTH, TOTAL_COLUMN_WIDTH, "WEEK")
    habits.forEach { habit ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            Column(Modifier.width(NAME_COLUMN_WIDTH - CELL_GAP).padding(end = 4.dp)) {
                Text(
                    habit.name, style = TimeboxTheme.type.label, color = colors.on,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    cadence(habit.rule), style = TimeboxTheme.type.bodySmall.copy(fontSize = 10.sp),
                    color = colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            WeekCells(sample, habit, weekStart, height = 40.dp, onTap = onTap, onLongPress = onLongPress)
            TotalView(sample.total(habit, weekStart), totalStyle, Modifier.width(TOTAL_COLUMN_WIDTH - CELL_GAP))
        }
    }
}

@Composable
private fun TotalCaption(text: String) {
    Text(
        text, style = TimeboxTheme.type.bodySmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
        color = TimeboxTheme.colors.onVariant, maxLines = 1,
    )
}

@Composable
private fun TotalView(total: RowTotal, style: TotalStyle, modifier: Modifier) {
    val colors = TimeboxTheme.colors
    val onActual = if (colors.actual.luminance() > 0.5f) Color.Black else Color.White
    val toneColor = when (total.tone) {
        TotalTone.Met -> colors.actual
        TotalTone.Missed -> colors.onVariant
        TotalTone.Open -> colors.on
    }
    val weight = if (total.tone == TotalTone.Met) FontWeight.Bold else FontWeight.Normal
    val description = "${total.text} ${total.unit}"
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = if (style == TotalStyle.Fraction || style == TotalStyle.Labeled) Alignment.End else Alignment.CenterHorizontally,
    ) {
        when (style) {
            TotalStyle.Fraction -> {
                Text(total.text, style = TimeboxTheme.type.label.copy(fontSize = 12.sp, fontWeight = weight), color = toneColor, maxLines = 1)
                total.caption?.let { TotalCaption(it) }
            }
            TotalStyle.Labeled -> {
                Text(total.text, style = TimeboxTheme.type.label.copy(fontSize = 12.sp, fontWeight = weight), color = toneColor, maxLines = 1)
                TotalCaption(total.unit)
            }
            TotalStyle.Ring -> {
                val progress = if (total.target > 0) (total.done.toFloat() / total.target).coerceIn(0f, 1f) else 0f
                val track = colors.low
                val fill = if (total.tone == TotalTone.Missed) colors.onVariant else colors.actual
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(32.dp)) {
                        val stroke = 3.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                        if (progress > 0f) {
                            drawArc(fill, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
                    Text(
                        total.done.toString(),
                        style = TimeboxTheme.type.label.copy(fontSize = 11.sp, fontWeight = weight),
                        color = toneColor,
                    )
                }
                total.caption?.let { TotalCaption(it) }
            }
            TotalStyle.Pill -> {
                val (background, foreground) = when (total.tone) {
                    TotalTone.Met -> colors.actual to onActual
                    TotalTone.Missed -> Color.Transparent to colors.onVariant
                    TotalTone.Open -> colors.low to colors.on
                }
                Text(
                    total.text,
                    style = TimeboxTheme.type.label.copy(fontSize = 11.sp, fontWeight = weight),
                    color = foreground, maxLines = 1,
                    modifier = Modifier.clip(TimeboxShapes.chip).background(background)
                        .then(if (total.tone == TotalTone.Missed) Modifier.border(1.dp, colors.outlineVariant, TimeboxShapes.chip) else Modifier)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                total.caption?.let { TotalCaption(it) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.WeekCells(
    sample: HabitsSample,
    habit: SampleHabit,
    weekStart: LocalDate,
    height: Dp,
    onTap: (SampleHabit, LocalDate) -> Unit,
    onLongPress: (SampleHabit, LocalDate) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val onActual = if (colors.actual.luminance() > 0.5f) Color.Black else Color.White
    (0L..6L).forEach { offset ->
        val date = weekStart.plusDays(offset)
        val state = sample.cell(habit, date)
        val tappable = state.kind !in setOf(CellKind.NotDue, CellKind.Excused, CellKind.Future)
        val background = when (state.kind) {
            CellKind.Met, CellKind.Count -> colors.actual
            CellKind.Partial -> colors.actual.copy(alpha = 0.3f)
            CellKind.Missed, CellKind.Empty -> colors.low
            else -> Color.Transparent
        }
        val border = when (state.kind) {
            CellKind.Open -> 1.5.dp to colors.planned
            CellKind.Future -> 1.dp to colors.outlineVariant
            else -> null
        }
        val description = buildString {
            append("${habit.name}, $date, ")
            append(
                when (state.kind) {
                    CellKind.NotDue -> "not due"
                    CellKind.Excused -> "excused"
                    CellKind.Future -> "upcoming"
                    CellKind.Open -> if (state.label != null) "open, ${state.label}" else "open"
                    CellKind.Met -> if (state.label != null) "met, ${state.label}" else "met"
                    CellKind.Missed -> "missed"
                    CellKind.Partial -> "missed, ${state.label}"
                    CellKind.Count -> "${state.count} sessions"
                    CellKind.Empty -> "no sessions"
                },
            )
        }
        Box(
            Modifier.weight(1f).height(height).clip(TimeboxShapes.cell).background(background)
                .then(if (border != null) Modifier.border(border.first, border.second, TimeboxShapes.cell) else Modifier)
                .semantics { contentDescription = description }
                .then(
                    if (tappable) Modifier.combinedClickable(
                        onClick = { onTap(habit, date) },
                        onLongClick = { onLongPress(habit, date) },
                    ) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (state.kind) {
                CellKind.NotDue -> Box(Modifier.size(3.dp).background(colors.outlineVariant, CircleShape))
                CellKind.Excused -> Box(Modifier.width(10.dp).height(1.5.dp).background(colors.outlineVariant))
                CellKind.Missed -> if (state.label != null) {
                    Text(state.label, style = TimeboxTheme.type.label.copy(fontSize = 11.sp), color = colors.onVariant)
                } else {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(14.dp))
                }
                CellKind.Met -> if (state.label != null) {
                    Text(state.label, style = TimeboxTheme.type.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = onActual)
                } else {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = onActual, modifier = Modifier.size(18.dp))
                }
                CellKind.Partial -> Text(state.label.orEmpty(), style = TimeboxTheme.type.label.copy(fontSize = 11.sp), color = colors.on)
                CellKind.Count -> Text(
                    state.label.orEmpty(),
                    style = TimeboxTheme.type.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = onActual,
                )
                CellKind.Open -> state.label?.let {
                    Text(it, style = TimeboxTheme.type.label.copy(fontSize = 11.sp), color = colors.on)
                }
                CellKind.Future, CellKind.Empty -> Unit
            }
        }
    }
}
