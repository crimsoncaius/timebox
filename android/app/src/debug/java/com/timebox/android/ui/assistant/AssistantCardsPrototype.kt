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

/** Round 2: how a Block Card shows both lanes. Round 1's "Plan, then actual" was dropped with the combined card. */
internal enum class DayLayout(val label: String, val arg: String) {
    Timeline("List", "timeline"),
    Lanes("Two lanes", "lanes"),
    Paired("Paired", "paired"),
}

/** What the read selected; the card shows exactly that. */
internal enum class LaneMode(val label: String, val arg: String) {
    Planned("Planned", "planned"),
    Actual("Actual", "actual"),
    Both("Both", "both"),
}

internal enum class DayScenario(val label: String, val arg: String) {
    Review("Yesterday", "review"),
    Today("Today · running", "today"),
    Future("Tomorrow", "future"),
    Long("Long day", "long"),
    Empty("Empty", "empty"),
}

internal enum class Lane { Planned, Actual }

/** Minutes are relative to the card date's midnight and may fall outside it; a null end is running. */
internal data class SampleBlock(
    val id: Int,
    val lane: Lane,
    val start: Int,
    val end: Int?,
    val title: String,
    val type: String,
    val plannedId: Int? = null,
)

internal data class SampleDay(
    val date: LocalDate,
    val relative: String?,
    val question: String,
    val answer: String,
    val readMinute: Int,
    val blocks: List<SampleBlock>,
    val future: Boolean = false,
)

internal val TODAY = LocalDate.of(2026, 9, 27)

internal fun sample(scenario: DayScenario): SampleDay = when (scenario) {
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

/** Switches between the Block Card and Task Type Card studies. */
@Composable
internal fun AssistantCardsPrototypeRoot(card: String, layout: String, scenario: String, lane: String, both: String, detail: String) {
    var kind by remember { mutableStateOf(card) }
    when (kind) {
        "type" -> TaskTypeCardPrototype(both, detail, scenario, lane) { kind = "conversation" }
        "conversation" -> CardConversationPrototype(layout) { kind = "block" }
        else -> AssistantCardsPrototype(layout, scenario, lane) { kind = "type" }
    }
}

@Composable
private fun AssistantCardsPrototype(initialLayout: String, initialScenario: String, initialLane: String, onKind: () -> Unit) {
    var layout by remember { mutableStateOf(DayLayout.entries.firstOrNull { it.arg == initialLayout } ?: DayLayout.Timeline) }
    var lane by remember { mutableStateOf(LaneMode.entries.firstOrNull { it.arg == initialLane } ?: LaneMode.Both) }
    var scenario by remember { mutableStateOf(DayScenario.entries.firstOrNull { it.arg == initialScenario } ?: DayScenario.Review) }
    var opened by remember { mutableStateOf<String?>(null) }
    val colors = TimeboxTheme.colors
    val day = sample(scenario)
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        ComparisonBar(lane, { lane = it }, layout, { layout = it }, scenario, { scenario = it; opened = null }, opened, onKind)
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
            androidx.compose.runtime.key(layout, scenario, lane) {
                DayCard(day, lane, layout) { opened = "Would open Day · ${day.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}" }
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
private fun ComparisonBar(lane: LaneMode, onLane: (LaneMode) -> Unit, layout: DayLayout, onLayout: (DayLayout) -> Unit, scenario: DayScenario, onScenario: (DayScenario) -> Unit, opened: String?, onKind: () -> Unit) {
    val ink = Color(0xFF1F1A00)
    Column(Modifier.fillMaxWidth().background(Color(0xFFFFE066)).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PROTOTYPE · Block Card", Modifier.weight(1f), style = TimeboxTheme.type.kicker, color = ink)
            Text("→ Task Type Card", Modifier.clickable(onClick = onKind).padding(4.dp), style = TimeboxTheme.type.bodySmall, color = ink, fontWeight = FontWeight.Medium)
        }
        Pills(LaneMode.entries, lane, { "Read: " + it.label }, onLane, ink)
        if (lane == LaneMode.Both) Pills(DayLayout.entries, layout, { "Both as: " + it.label }, onLayout, ink)
        Pills(DayScenario.entries, scenario, { it.label }, onScenario, ink)
        opened?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = ink) }
    }
}

@Composable
internal fun <T> Pills(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, ink: Color) {
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
internal fun DayCard(source: SampleDay, lane: LaneMode, layout: DayLayout, onOpenDay: () -> Unit) {
    val colors = TimeboxTheme.colors
    var expanded by remember { mutableStateOf(false) }
    // Future dates have no Actual Blocks; Both behaves like Planned there.
    val shown = if (source.future || lane == LaneMode.Planned) LaneMode.Planned else lane
    val day = source.copy(blocks = source.blocks.filter {
        shown == LaneMode.Both || (shown == LaneMode.Planned) == (it.lane == Lane.Planned)
    })
    val planned = day.blocks.filter { it.lane == Lane.Planned }.sortedBy { it.start }
    val actual = day.blocks.filter { it.lane == Lane.Actual }.sortedBy { it.start }
    val what = when (shown) { LaneMode.Planned -> "Plan"; LaneMode.Actual -> "Actual"; LaneMode.Both -> "Plan and actual" }
    Surface(color = colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${day.relative ?: day.date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))} · $what", Modifier.weight(1f).semantics { heading() }, style = TimeboxTheme.type.label)
                Text(when (shown) {
                    LaneMode.Planned -> "${planned.size} planned"
                    LaneMode.Actual -> "${actual.size} actual"
                    LaneMode.Both -> "${planned.size} · ${actual.size}"
                }, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            Text("${day.date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))} · Read 15:02 · Asia/Singapore", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            if (day.future) Text(
                if (lane == LaneMode.Planned) "Recurring work you haven’t placed isn’t included for future dates."
                else "Nothing can be recorded on a future date yet, so this shows the plan. Recurring work you haven’t placed isn’t included.",
                style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            if (day.blocks.isEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.hairline)
                Text(when (shown) {
                    LaneMode.Planned -> "Nothing planned on this date."
                    LaneMode.Actual -> "Nothing recorded on this date."
                    LaneMode.Both -> "Nothing planned or recorded on this date."
                }, style = TimeboxTheme.type.body)
            }
            val total = when {
                day.blocks.isEmpty() -> 0
                shown != LaneMode.Both || layout == DayLayout.Timeline -> TimelineRows(day, expanded, tags = shown == LaneMode.Both)
                layout == DayLayout.Lanes -> LaneView(day, planned, actual, expanded)
                else -> PairedRows(day, planned, actual, expanded)
            }
            if (total > COLLAPSED_ITEMS) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Show less" else if (shown == LaneMode.Both && layout == DayLayout.Lanes) "Show larger" else "Show all ${day.blocks.size} blocks",
                    Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                Text(if (expanded) "−" else "+", color = colors.onVariant)
            }
            HorizontalDivider(color = colors.hairline)
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenDay).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Open Day", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.on, fontWeight = FontWeight.Medium)
                Icon(Icons.Outlined.NorthEast, null, Modifier.size(16.dp), tint = colors.onVariant)
            }
        }
    }
}

/** A mini Day: planned and actual side by side on one time scale, clipped to the date. */
@Composable
private fun LaneView(day: SampleDay, planned: List<SampleBlock>, actual: List<SampleBlock>, expanded: Boolean): Int {
    val colors = TimeboxTheme.colors
    val scale = if (expanded) 1.1f else 0.42f
    fun endOf(b: SampleBlock) = b.end ?: day.readMinute
    val earliest = if (!expanded && planned.isNotEmpty()) planned.minOf { it.start } else day.blocks.minOf { it.start }
    val from = (maxOf(earliest, 0) / 60) * 60
    val to = minOf(1440, ((day.blocks.maxOf { minOf(endOf(it), 1440) } + 59) / 60) * 60)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Spacer(Modifier.width(40.dp))
        Text("PLAN", Modifier.weight(1f), style = TimeboxTheme.type.kicker.copy(fontSize = 9.sp), color = colors.planned)
        Spacer(Modifier.width(6.dp))
        Text("ACTUAL", Modifier.weight(1f), style = TimeboxTheme.type.kicker.copy(fontSize = 9.sp), color = colors.actual)
    }
    Row(Modifier.fillMaxWidth().height(((to - from) * scale).dp)) {
        Box(Modifier.width(40.dp).fillMaxHeight()) {
            var hour = from
            while (hour < to) {
                if (hour % (if (expanded) 60 else 120) == 0) Text(clockOf(hour), Modifier.padding(top = ((hour - from) * scale).dp),
                    style = TimeboxTheme.type.mono.copy(fontSize = 9.sp), color = colors.onVariant)
                hour += 60
            }
        }
        listOf(planned, actual).forEachIndexed { index, blocks ->
            if (index == 1) Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f).fillMaxHeight().background(colors.bg.copy(alpha = 0.5f), RoundedCornerShape(4.dp))) {
                blocks.filter { minOf(endOf(it), 1440) > from }.forEach { b ->
                    val top = maxOf(b.start, from)
                    val bottom = minOf(endOf(b), 1440)
                    val h = ((bottom - top) * scale).dp
                    val isPlanned = b.lane == Lane.Planned
                    Surface(Modifier.fillMaxWidth().padding(top = ((top - from) * scale).dp).height(maxOf(h, 3.dp)).padding(vertical = 0.5.dp),
                        color = if (isPlanned) colors.plannedSurface else colors.actualSurface, shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(1.dp, if (isPlanned) colors.plannedBorder else colors.actualBorder)) {
                        if (h >= 16.dp) Column(Modifier.padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text((if (b.start < from) "↑ " else "") + b.title + if (b.end == null) " · now" else "",
                                style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp, lineHeight = 13.sp), maxLines = 1, color = colors.on)
                            if (h >= 32.dp) Text(durationShort(endOf(b) - b.start),
                                style = TimeboxTheme.type.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp), maxLines = 1, color = colors.onVariant)
                        }
                    }
                }
            }
        }
    }
    return if (to - from > 5 * 60 || (planned.isNotEmpty() && day.blocks.any { it.start < from })) Int.MAX_VALUE else 0
}

/** Returns the number of collapsible items so the card can offer expansion. */
@Composable
private fun TimelineRows(day: SampleDay, expanded: Boolean, tags: Boolean): Int {
    val rows = day.blocks.sortedWith(compareBy({ it.start }, { it.lane }))
    (if (expanded) rows else rows.take(COLLAPSED_ITEMS)).forEach { block ->
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = TimeboxTheme.colors.hairline)
        BlockRow(day, block, tags)
    }
    return rows.size
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
                Text(if (ahead) "Not yet" else "Not recorded", Modifier.padding(start = 90.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
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
private fun BlockRow(day: SampleDay, block: SampleBlock, tag: Boolean = true) {
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
        Column(Modifier.width(64.dp)) {
            Text(clockOf(block.start) + if (block.start < 0) " ${dayName(-1)}" else "", style = TimeboxTheme.type.mono.copy(fontSize = 12.sp), color = tone)
            Text(if (block.end == null) "now" else clockOf(end) + if (end > 1440) " ${dayName(1)}" else "", style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.onVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(block.title, style = TimeboxTheme.type.body, fontWeight = FontWeight.Medium)
            Text(detail, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        }
        if (tag) Text(if (isPlanned) "PLAN" else "DID", style = TimeboxTheme.type.kicker.copy(fontSize = 9.sp), color = tone)
    }
}
