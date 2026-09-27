package com.timebox.android.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.durationShort
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Issue 296: debug-route experiment for Assistant Cards. Sample data only; nothing reaches the API.

/** Round 1: how a Day Card arranges both lanes. */
private enum class DayLayout(val label: String, val arg: String) {
    Timeline("One timeline", "timeline"),
    Sections("Plan, then actual", "sections"),
    Paired("Paired", "paired"),
}

private enum class DayScenario(val label: String, val arg: String) {
    Review("Yesterday", "review"),
    Today("Today · running", "today"),
    Future("Tomorrow", "future"),
    Long("Long day", "long"),
    Empty("Empty", "empty"),
}

private enum class Lane { Planned, Actual }

/** Minutes are relative to the card date's midnight and may fall outside it; a null end is running. */
private data class SampleBlock(
    val id: Int,
    val lane: Lane,
    val start: Int,
    val end: Int?,
    val title: String,
    val type: String,
    val plannedId: Int? = null,
)

private data class SampleDay(
    val date: LocalDate,
    val relative: String?,
    val question: String,
    val answer: String,
    val readMinute: Int,
    val blocks: List<SampleBlock>,
    val future: Boolean = false,
)

private val TODAY = LocalDate.of(2026, 9, 27)

private fun sample(scenario: DayScenario): SampleDay = when (scenario) {
    DayScenario.Review -> SampleDay(
        TODAY.minusDays(1), "Yesterday", "How did yesterday go against my plan?",
        "You recorded most of what you planned. Deep Work came in 25m short and started late, " +
            "Leisure took 1h 30m you hadn't planned around midday, and Gym didn't happen.",
        15 * 60 + 2 + 1440,
        listOf(
            SampleBlock(1, Lane.Planned, 450, 480, "Morning routine", "Routine"),
            SampleBlock(2, Lane.Planned, 540, 660, "Thesis chapter 3", "Work/Deep Work"),
            SampleBlock(3, Lane.Planned, 660, 690, "Email", "Work/Admin"),
            SampleBlock(4, Lane.Planned, 750, 795, "Lunch", "Meals"),
            SampleBlock(5, Lane.Planned, 840, 960, "Thesis chapter 3", "Work/Deep Work"),
            SampleBlock(6, Lane.Planned, 1140, 1200, "Gym", "Health/Exercise"),
            SampleBlock(11, Lane.Actual, -60, 435, "Sleep", "Sleep"),
            SampleBlock(12, Lane.Actual, 460, 490, "Morning routine", "Routine", 1),
            SampleBlock(13, Lane.Actual, 555, 640, "Thesis chapter 3", "Work/Deep Work", 2),
            SampleBlock(14, Lane.Actual, 640, 680, "Email", "Work/Admin", 3),
            SampleBlock(15, Lane.Actual, 680, 770, "YouTube", "Leisure"),
            SampleBlock(16, Lane.Actual, 770, 810, "Lunch", "Meals", 4),
            SampleBlock(17, Lane.Actual, 850, 900, "Thesis chapter 3", "Work/Deep Work", 5),
            SampleBlock(18, Lane.Actual, 900, 925, "Coffee", "Meals"),
            SampleBlock(19, Lane.Actual, 925, 990, "Thesis chapter 3", "Work/Deep Work", 5),
            SampleBlock(20, Lane.Actual, 1410, 1510, "Sleep", "Sleep"),
        ),
    )
    DayScenario.Today -> SampleDay(
        TODAY, "Today", "What have I done so far today?",
        "Groceries, a 1h 30m Deep Work block and lunch are done. You've been reading since 14:40, " +
            "and your Weekly review is still ahead at 20:00.",
        15 * 60 + 2,
        listOf(
            SampleBlock(1, Lane.Planned, 540, 600, "Groceries", "Errands"),
            SampleBlock(2, Lane.Planned, 630, 750, "Deep work", "Work/Deep Work"),
            SampleBlock(3, Lane.Planned, 780, 825, "Lunch", "Meals"),
            SampleBlock(4, Lane.Planned, 870, 990, "Reading", "Leisure/Reading"),
            SampleBlock(5, Lane.Planned, 1200, 1260, "Weekly review", "Planning"),
            SampleBlock(11, Lane.Actual, -45, 480, "Sleep", "Sleep"),
            SampleBlock(12, Lane.Actual, 560, 615, "Groceries", "Errands", 1),
            SampleBlock(13, Lane.Actual, 640, 730, "Deep work", "Work/Deep Work", 2),
            SampleBlock(14, Lane.Actual, 730, 800, "Lunch", "Meals", 3),
            SampleBlock(15, Lane.Actual, 880, null, "Reading", "Leisure/Reading", 4),
        ),
    )
    DayScenario.Future -> SampleDay(
        TODAY.plusDays(1), "Tomorrow", "What's my plan tomorrow?",
        "Six Planned Blocks, with Deep Work in the morning and a free stretch from 16:00. " +
            "Recurring routines you haven't placed yet won't show here.",
        15 * 60 + 2 - 1440,
        listOf(
            SampleBlock(1, Lane.Planned, 450, 480, "Morning routine", "Routine"),
            SampleBlock(2, Lane.Planned, 540, 720, "Thesis chapter 4", "Work/Deep Work"),
            SampleBlock(3, Lane.Planned, 750, 795, "Lunch", "Meals"),
            SampleBlock(4, Lane.Planned, 810, 870, "Supervisor meeting", "Work/Meetings"),
            SampleBlock(5, Lane.Planned, 870, 960, "Email", "Work/Admin"),
            SampleBlock(6, Lane.Planned, 1140, 1200, "Gym", "Health/Exercise"),
        ),
        future = true,
    )
    DayScenario.Long -> {
        val types = listOf("Work/Deep Work" to "Focus sprint", "Work/Admin" to "Inbox", "Leisure" to "Break", "Meals" to "Snack")
        val planned = (0 until 16).map { i ->
            val (type, title) = types[i % types.size]
            SampleBlock(i + 1, Lane.Planned, 480 + i * 45, 480 + i * 45 + 40, "$title ${i / 4 + 1}", type)
        }
        val actual = (0 until 20).map { i ->
            val (type, title) = types[(i + i / 5) % types.size]
            val start = 490 + i * 36
            SampleBlock(100 + i, Lane.Actual, start, start + 34, "$title ${i / 4 + 1}", type, planned.getOrNull(i * 16 / 20)?.id?.takeIf { i % 5 != 4 })
        }
        SampleDay(TODAY.minusDays(3), null, "Show Thursday.", "A fragmented day: 20 Actual Blocks against 16 planned, mostly in 30–40 minute pieces.", 15 * 60 + 2 + 3 * 1440, planned + actual)
    }
    DayScenario.Empty -> SampleDay(TODAY.minusDays(5), null, "What did I do last Tuesday?", "Nothing was planned or recorded on Tuesday 22 Sep.", 15 * 60 + 2 + 5 * 1440, emptyList())
}

@Composable
internal fun AssistantCardsPrototype(initialLayout: String, initialScenario: String) {
    var layout by remember { mutableStateOf(DayLayout.entries.firstOrNull { it.arg == initialLayout } ?: DayLayout.Timeline) }
    var scenario by remember { mutableStateOf(DayScenario.entries.firstOrNull { it.arg == initialScenario } ?: DayScenario.Review) }
    var opened by remember { mutableStateOf<String?>(null) }
    val colors = TimeboxTheme.colors
    val day = sample(scenario)
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        ComparisonBar(layout, { layout = it }, scenario, { scenario = it; opened = null }, opened)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Assistant", Modifier.semantics { heading() }, style = TimeboxTheme.type.screenTitle, color = colors.on)
        }
        HorizontalDivider(color = colors.hairline)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.padding(start = 36.dp).align(Alignment.End), color = colors.card, shape = RoundedCornerShape(16.dp, 16.dp, 3.dp, 16.dp), border = BorderStroke(1.dp, colors.hairline)) {
                Text(day.question, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), style = TimeboxTheme.type.body)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = colors.onVariant)
                Text("Assistant", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            androidx.compose.runtime.key(layout, scenario) {
                DayCard(day, layout) { opened = "Would open Day · ${day.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}" }
            }
            Text(day.answer, style = TimeboxTheme.type.body.copy(fontSize = 15.sp, lineHeight = 25.sp))
            Spacer(Modifier.height(24.dp))
        }
        Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), color = colors.field, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, colors.hairline)) {
            Text("Ask a follow-up…", Modifier.padding(horizontal = 18.dp, vertical = 14.dp), style = TimeboxTheme.type.body, color = colors.onVariant)
        }
    }
}

@Composable
private fun ComparisonBar(layout: DayLayout, onLayout: (DayLayout) -> Unit, scenario: DayScenario, onScenario: (DayScenario) -> Unit, opened: String?) {
    val ink = Color(0xFF1F1A00)
    Column(Modifier.fillMaxWidth().background(Color(0xFFFFE066)).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("PROTOTYPE · Day Card", style = TimeboxTheme.type.kicker, color = ink)
        Pills(DayLayout.entries, layout, { it.label }, onLayout, ink)
        Pills(DayScenario.entries, scenario, { it.label }, onScenario, ink)
        opened?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = ink) }
    }
}

@Composable
private fun <T> Pills(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, ink: Color) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val on = option == selected
            Surface(Modifier.clickable { onSelect(option) }, color = if (on) ink else Color.Transparent, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, ink)) {
                Text(label(option), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = TimeboxTheme.type.bodySmall, color = if (on) Color(0xFFFFE066) else ink)
            }
        }
    }
}

private const val COLLAPSED_ITEMS = 5

@Composable
private fun DayCard(day: SampleDay, layout: DayLayout, onOpenDay: () -> Unit) {
    val colors = TimeboxTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val planned = day.blocks.filter { it.lane == Lane.Planned }.sortedBy { it.start }
    val actual = day.blocks.filter { it.lane == Lane.Actual }.sortedBy { it.start }
    Surface(color = colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(day.relative ?: day.date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)), Modifier.weight(1f).semantics { heading() }, style = TimeboxTheme.type.label)
                Text(if (day.future) "${planned.size} planned" else "${planned.size} planned · ${actual.size} actual", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            Text("${day.date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))} · Read 15:02 · Asia/Singapore", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            if (day.future) Text("Recurring work you haven't placed isn't included for future dates.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            if (day.blocks.isEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.hairline)
                Text("Nothing planned or recorded on this date.", style = TimeboxTheme.type.body)
            }
            val total = when (layout) {
                DayLayout.Timeline -> TimelineRows(day, expanded)
                DayLayout.Sections -> SectionRows(day, planned, actual, expanded)
                DayLayout.Paired -> PairedRows(day, planned, actual, expanded)
            }
            if (total > COLLAPSED_ITEMS) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Show fewer" else "Show all ${day.blocks.size} blocks", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                Text(if (expanded) "−" else "+", color = colors.onVariant)
            }
            if (day.blocks.isNotEmpty()) TypeTotals(day)
            HorizontalDivider(color = colors.hairline)
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenDay).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Open Day", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.on, fontWeight = FontWeight.Medium)
                Icon(Icons.Outlined.NorthEast, null, Modifier.size(16.dp), tint = colors.onVariant)
            }
        }
    }
}

/** Returns the number of collapsible items so the card can offer expansion. */
@Composable
private fun TimelineRows(day: SampleDay, expanded: Boolean): Int {
    val rows = day.blocks.sortedWith(compareBy({ it.start }, { it.lane }))
    (if (expanded) rows else rows.take(COLLAPSED_ITEMS)).forEach { block ->
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = TimeboxTheme.colors.hairline)
        BlockRow(day, block)
    }
    return rows.size
}

@Composable
private fun SectionRows(day: SampleDay, planned: List<SampleBlock>, actual: List<SampleBlock>, expanded: Boolean): Int {
    val perSection = 3
    listOf("PLANNED" to planned, "ACTUAL" to actual).forEach { (label, rows) ->
        if (rows.isEmpty()) return@forEach
        Text("$label · ${rows.size}", Modifier.padding(top = 6.dp), style = TimeboxTheme.type.kicker, color = TimeboxTheme.colors.onVariant)
        (if (expanded) rows else rows.take(perSection)).forEach { block ->
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = TimeboxTheme.colors.hairline)
            BlockRow(day, block)
        }
    }
    return if (planned.size > perSection || actual.size > perSection) Int.MAX_VALUE else 0
}

@Composable
private fun PairedRows(day: SampleDay, planned: List<SampleBlock>, actual: List<SampleBlock>, expanded: Boolean): Int {
    val colors = TimeboxTheme.colors
    val unplanned = actual.filter { it.plannedId == null }
    val groups = planned.map { it to actual.filter { a -> a.plannedId == it.id } }
    val items = groups.size + if (unplanned.isEmpty()) 0 else 1
    (if (expanded) groups else groups.take(COLLAPSED_ITEMS)).forEach { (plan, done) ->
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.hairline)
        BlockRow(day, plan)
        if (!day.future) {
            if (done.isEmpty()) {
                val ahead = plan.start > day.readMinute
                Text(if (ahead) "Not yet" else "Not recorded", Modifier.padding(start = 72.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            done.forEach { Box(Modifier.padding(start = 24.dp)) { BlockRow(day, it) } }
        }
    }
    if (unplanned.isNotEmpty() && (expanded || groups.size < COLLAPSED_ITEMS)) {
        Text("NOT PLANNED · ${unplanned.size}", Modifier.padding(top = 6.dp), style = TimeboxTheme.type.kicker, color = colors.onVariant)
        unplanned.forEach { block ->
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.hairline)
            BlockRow(day, block)
        }
    }
    return items
}

private fun clockOf(minute: Int): String = Math.floorMod(minute, 1440).let { "%02d:%02d".format(it / 60, it % 60) }

@Composable
private fun BlockRow(day: SampleDay, block: SampleBlock) {
    val colors = TimeboxTheme.colors
    val isPlanned = block.lane == Lane.Planned
    val tone = if (isPlanned) colors.planned else colors.actual
    val end = block.end ?: day.readMinute
    val whole = end - block.start
    val inside = minOf(end, 1440) - maxOf(block.start, 0)
    val dayName = { offset: Int -> day.date.plusDays(offset.toLong()).format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)) }
    val detail = buildList {
        add(block.type)
        when {
            block.end == null -> add("running · ${durationShort(whole)} at read")
            inside < whole -> add("${durationShort(whole)} total · ${durationShort(inside)} this day")
            else -> add(durationShort(whole))
        }
    }.joinToString(" · ")
    val lane = if (isPlanned) "Planned" else "Actual"
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) { contentDescription = "$lane: ${block.title}, ${clockOf(block.start)} to ${if (block.end == null) "now" else clockOf(end)}, $detail" }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (isPlanned) Color.Transparent else tone, RoundedCornerShape(2.dp))
            .then(if (isPlanned) Modifier.background(colors.plannedBorder.copy(alpha = 0.6f), RoundedCornerShape(2.dp)) else Modifier))
        Column(Modifier.width(52.dp)) {
            Text(clockOf(block.start) + if (block.start < 0) " ${dayName(-1)}" else "", style = TimeboxTheme.type.mono.copy(fontSize = 12.sp), color = tone)
            Text(if (block.end == null) "now" else clockOf(end) + if (end > 1440) " ${dayName(1)}" else "", style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.onVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(block.title, style = TimeboxTheme.type.body, fontWeight = FontWeight.Medium)
            Text(detail, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        }
        Text(if (isPlanned) "PLAN" else "DID", style = TimeboxTheme.type.kicker.copy(fontSize = 9.sp), color = tone)
    }
}

@Composable
private fun TypeTotals(day: SampleDay) {
    val colors = TimeboxTheme.colors
    var open by remember { mutableStateOf(false) }
    fun clipped(block: SampleBlock) = (minOf(block.end ?: day.readMinute, 1440) - maxOf(block.start, 0)).coerceAtLeast(0)
    val types = day.blocks.map { it.type }.distinct().map { type ->
        val p = day.blocks.filter { it.type == type && it.lane == Lane.Planned }.sumOf(::clipped)
        val a = day.blocks.filter { it.type == type && it.lane == Lane.Actual }.sumOf(::clipped)
        Triple(type, p, a)
    }.sortedByDescending { maxOf(it.second, it.third) }
    HorizontalDivider(color = colors.hairline)
    Row(Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("By Task Type", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.on, fontWeight = FontWeight.Medium)
        Text(if (open) "−" else "+", color = colors.onVariant)
    }
    if (open) {
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.weight(1f))
            listOf("Plan", "Actual", "Diff").forEach { Text(it, Modifier.width(58.dp), style = TimeboxTheme.type.kicker.copy(fontSize = 9.sp), color = colors.onVariant) }
        }
        types.forEach { (type, p, a) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(type, Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.on)
                Text(if (p == 0) "—" else durationShort(p), Modifier.width(58.dp), style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.planned)
                Text(if (a == 0) "—" else durationShort(a), Modifier.width(58.dp), style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.actual)
                val diff = a - p
                Text(when { day.future -> ""; diff == 0 -> "0"; diff > 0 -> "+" + durationShort(diff); else -> "−" + durationShort(-diff) },
                    Modifier.width(58.dp), style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.onVariant)
            }
        }
    }
}
