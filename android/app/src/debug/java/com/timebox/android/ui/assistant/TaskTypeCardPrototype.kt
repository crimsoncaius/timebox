package com.timebox.android.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.fillMaxWidth as fill
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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

// Issue 296 round 3: Task Type Card. Sample data only; nothing reaches the API.

/** How a Task Type Card shows planned and actual together. */
private enum class TypeBoth(val label: String, val arg: String) {
    PairedBars("Paired bars", "bars"),
    Marker("Actual + plan mark", "marker"),
    Numbers("Numbers", "numbers"),
}

private enum class TypeDetail(val label: String, val arg: String) { Total("Total", "total"), Day("By day", "day") }

private enum class TypeScenario(val label: String, val arg: String) {
    LastWeek("Last week", "last-week"),
    Yesterday("Yesterday", "yesterday"),
    ThisWeek("This week · so far", "this-week"),
    NextWeek("Next week", "next-week"),
    Filtered("Last week · Work", "filtered"),
}

private data class Leaf(val path: String, val planned: List<Int>, val actual: List<Int>)

private val LEAVES = listOf(
    Leaf("Work/Deep Work", listOf(180, 180, 180, 180, 120, 0, 0), listOf(150, 200, 90, 170, 60, 0, 45)),
    Leaf("Work/Admin", listOf(30, 30, 30, 30, 30, 0, 0), listOf(45, 40, 60, 35, 50, 0, 10)),
    Leaf("Work/Meetings", listOf(60, 0, 90, 0, 60, 0, 0), listOf(55, 0, 100, 0, 45, 0, 0)),
    Leaf("Leisure", listOf(60, 60, 60, 60, 120, 240, 240), listOf(110, 90, 140, 75, 160, 300, 280)),
    Leaf("Leisure/Reading", listOf(30, 30, 30, 30, 30, 60, 60), listOf(20, 0, 30, 45, 0, 90, 60)),
    Leaf("Meals", List(7) { 90 }, listOf(95, 80, 110, 85, 100, 120, 130)),
    Leaf("Health/Exercise", listOf(60, 0, 60, 0, 60, 90, 0), listOf(60, 0, 0, 0, 55, 90, 0)),
    Leaf("Sleep", List(7) { 0 }, listOf(450, 420, 480, 400, 460, 540, 510)),
)

private data class TypeNode(val path: String, val planned: List<Int>, val actual: List<Int>, val children: List<TypeNode>) {
    val name get() = path.substringAfterLast('/')
    val p get() = planned.sum()
    val a get() = actual.sum()
}

private data class TypeSample(
    val title: String, val range: String, val question: String, val answer: String,
    val days: List<String>, val roots: List<TypeNode>, val future: Boolean = false, val partial: Boolean = false, val filter: String? = null,
)

private fun tree(leaves: List<Leaf>, dayCount: Int, transform: (Leaf) -> Leaf = { it }): List<TypeNode> {
    val ls = leaves.map(transform)
    fun sum(lists: List<List<Int>>) = List(dayCount) { i -> lists.sumOf { it[i] } }
    fun node(path: String): TypeNode {
        val under = ls.filter { it.path == path || it.path.startsWith("$path/") }
        val childPaths = ls.map { it.path }.filter { it.startsWith("$path/") }.map { path + "/" + it.removePrefix("$path/").substringBefore('/') }.distinct()
        return TypeNode(path, sum(under.map { it.planned }), sum(under.map { it.actual }), childPaths.map(::node))
    }
    return ls.map { it.path.substringBefore('/') }.distinct().map(::node)
}

private val WEEK = listOf("M", "T", "W", "T", "F", "S", "S")

private fun typeSample(s: TypeScenario): TypeSample = when (s) {
    TypeScenario.LastWeek -> TypeSample("Last week", "Mon 14 – Sun 20 Sep 2026", "How did last week compare to my plan?",
        "Work came in about 1h short of plan, mostly Deep Work on Wednesday and Friday. Leisure ran 3h 25m over, and you skipped two planned workouts.",
        WEEK, tree(LEAVES, 7))
    TypeScenario.Yesterday -> TypeSample("Yesterday", "Sat 26 Sep 2026", "Where did my time go yesterday?",
        "Mostly Sleep and Leisure, as planned for a Saturday. Leisure ran an hour over.",
        listOf("S"), tree(LEAVES, 1) { Leaf(it.path, listOf(it.planned[5]), listOf(it.actual[5])) })
    TypeScenario.ThisWeek -> TypeSample("This week", "Mon 21 – Sun 27 Sep 2026", "How's this week going?",
        "With today still running, Work is 1h 20m behind plan and Leisure is already over.",
        WEEK, tree(LEAVES, 7) { Leaf(it.path, it.planned, it.actual.mapIndexed { i, v -> if (i == 6) v * 15 / 24 else v }) }, partial = true)
    TypeScenario.NextWeek -> TypeSample("Next week", "Mon 28 Sep – Sun 4 Oct 2026", "How much Deep Work have I planned next week?",
        "14h of Deep Work is planned, front-loaded Monday to Thursday.",
        WEEK, tree(LEAVES.filter { it.path != "Sleep" }, 7) { Leaf(it.path, it.planned, List(7) { 0 }) }, future = true)
    TypeScenario.Filtered -> TypeSample("Last week", "Mon 14 – Sun 20 Sep 2026", "How much Work did I do last week?",
        "12h 45m of Work against 14h planned. Deep Work was the shortfall.",
        WEEK, tree(LEAVES.filter { it.path.startsWith("Work") }, 7), filter = "Work")
}

@Composable
internal fun TaskTypeCardPrototype(initialBoth: String, initialDetail: String, initialScenario: String, initialLane: String, onKind: () -> Unit) {
    var both by remember { mutableStateOf(TypeBoth.entries.firstOrNull { it.arg == initialBoth } ?: TypeBoth.PairedBars) }
    var detail by remember { mutableStateOf(TypeDetail.entries.firstOrNull { it.arg == initialDetail } ?: TypeDetail.Total) }
    var scenario by remember { mutableStateOf(TypeScenario.entries.firstOrNull { it.arg == initialScenario } ?: TypeScenario.LastWeek) }
    var lane by remember { mutableStateOf(LaneMode.entries.firstOrNull { it.arg == initialLane } ?: LaneMode.Both) }
    var opened by remember { mutableStateOf<String?>(null) }
    val colors = TimeboxTheme.colors
    val sample = typeSample(scenario)
    val ink = Color(0xFF1F1A00)
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(Color(0xFFFFE066)).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PROTOTYPE · Task Type Card", Modifier.weight(1f), style = TimeboxTheme.type.kicker, color = ink)
                Text("→ Block Card", Modifier.clickable(onClick = onKind).padding(4.dp), style = TimeboxTheme.type.bodySmall, color = ink, fontWeight = FontWeight.Medium)
            }
            Pills(LaneMode.entries, lane, { "Read: " + it.label }, { lane = it }, ink)
            if (lane == LaneMode.Both) Pills(TypeBoth.entries, both, { "Both as: " + it.label }, { both = it }, ink)
            Pills(TypeDetail.entries, detail, { "Detail: " + it.label }, { detail = it }, ink)
            Pills(TypeScenario.entries, scenario, { it.label }, { scenario = it; opened = null }, ink)
            opened?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = ink) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Assistant", Modifier.semantics { heading() }, style = TimeboxTheme.type.screenTitle, color = colors.on)
        }
        HorizontalDivider(color = colors.hairline)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.padding(start = 36.dp).align(Alignment.End), color = colors.card, shape = RoundedCornerShape(16.dp, 16.dp, 3.dp, 16.dp), border = BorderStroke(1.dp, colors.hairline)) {
                Text(sample.question, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), style = TimeboxTheme.type.body)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = colors.onVariant)
                Text("Assistant", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            androidx.compose.runtime.key(scenario, lane) {
                TypeCard(sample, lane, both, detail) { opened = "Would open Trends · ${sample.range}" }
            }
            Text(sample.answer, style = TimeboxTheme.type.body.copy(fontSize = 15.sp, lineHeight = 25.sp))
            Spacer(Modifier.height(24.dp))
        }
        Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), color = colors.field, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, colors.hairline)) {
            Text("Ask a follow-up…", Modifier.padding(horizontal = 18.dp, vertical = 14.dp), style = TimeboxTheme.type.body, color = colors.onVariant)
        }
    }
}

@Composable
private fun TypeCard(sample: TypeSample, lane: LaneMode, both: TypeBoth, detail: TypeDetail, onOpen: () -> Unit) {
    val colors = TimeboxTheme.colors
    val shown = if (sample.future) LaneMode.Planned else lane
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val roots = sample.roots.filter { if (shown == LaneMode.Planned) it.p > 0 else if (shown == LaneMode.Actual) it.a > 0 else true }
        .sortedByDescending { if (shown == LaneMode.Planned) it.p else it.a }
    val scaleMax = roots.maxOf { when (shown) { LaneMode.Planned -> it.p; LaneMode.Actual -> it.a; LaneMode.Both -> maxOf(it.p, it.a) } }.coerceAtLeast(1)
    val totalP = roots.sumOf { it.p }
    val totalA = roots.sumOf { it.a }
    val what = when (shown) { LaneMode.Planned -> "Plan"; LaneMode.Actual -> "Actual"; LaneMode.Both -> "Plan and actual" }
    val showDays = detail == TypeDetail.Day && sample.days.size > 1
    Surface(color = colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${sample.title} · $what", Modifier.weight(1f).semantics { heading() }, style = TimeboxTheme.type.label)
                Text(when (shown) {
                    LaneMode.Planned -> durationShort(totalP) + " planned"
                    LaneMode.Actual -> durationShort(totalA) + " actual"
                    LaneMode.Both -> "${durationShort(totalP)} · ${durationShort(totalA)}"
                }, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            Text("${sample.range} · Read 15:02 · Asia/Singapore", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            val notes = buildList {
                sample.filter?.let { add("Only $it and its Task Types.") }
                if (sample.partial && shown != LaneMode.Planned) add("Actual time counts up to 15:02 today; the plan covers the whole week.")
                if (sample.future) add(if (lane == LaneMode.Planned) "Recurring work you haven’t placed isn’t included for future dates."
                    else "Nothing can be recorded in the future yet, so this shows the plan. Recurring work you haven’t placed isn’t included.")
            }
            notes.forEach { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
            if (shown == LaneMode.Both && both != TypeBoth.Numbers) Legend(both)
            if (showDays) Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Spacer(Modifier.weight(1f))
                DayStripLabels(sample.days)
            }
            roots.forEach { node ->
                HorizontalDivider(color = colors.hairline)
                TypeRow(node, 0, shown, both, scaleMax, if (shown == LaneMode.Planned) totalP else totalA, showDays, sample.days.size, open[node.path] == true) {
                    open[node.path] = open[node.path] != true
                }
                if (open[node.path] == true) node.children.sortedByDescending { if (shown == LaneMode.Planned) it.p else it.a }.forEach { child ->
                    TypeRow(child, 1, shown, both, scaleMax, if (shown == LaneMode.Planned) totalP else totalA, showDays, sample.days.size, false) {}
                }
            }
            HorizontalDivider(color = colors.hairline)
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Open Trends", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.on, fontWeight = FontWeight.Medium)
                Icon(Icons.Outlined.NorthEast, null, Modifier.size(16.dp), tint = colors.onVariant)
            }
        }
    }
}

@Composable
private fun Legend(both: TypeBoth) {
    val colors = TimeboxTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (both == TypeBoth.Marker) Box(Modifier.width(2.dp).height(10.dp).background(colors.planned))
            else Box(Modifier.width(14.dp).height(6.dp).border(1.dp, colors.planned, RoundedCornerShape(2.dp)))
            Text("Plan", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(14.dp).height(6.dp).background(colors.actual, RoundedCornerShape(2.dp)))
            Text("Actual", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        }
    }
}

private val STRIP_CELL = 14.dp

@Composable
private fun DayStripLabels(days: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        days.forEach { Text(it, Modifier.width(STRIP_CELL), style = TimeboxTheme.type.mono.copy(fontSize = 9.sp), color = TimeboxTheme.colors.onVariant) }
    }
}

@Composable
private fun TypeRow(node: TypeNode, depth: Int, lane: LaneMode, both: TypeBoth, scaleMax: Int, laneTotal: Int, showDays: Boolean, dayCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = TimeboxTheme.colors
    val p = node.p
    val a = node.a
    val expandable = node.children.isNotEmpty()
    val diff = a - p
    val diffText = if (diff == 0) "on plan" else if (diff > 0) "+${durationShort(diff)}" else "−${durationShort(-diff)}"
    Column(Modifier.fillMaxWidth().then(if (expandable) Modifier.clickable(onClick = onToggle) else Modifier).padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp)
        .semantics(mergeDescendants = true) { contentDescription = "${node.path}: planned ${durationShort(p)}, actual ${durationShort(a)}" },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((if (expandable) (if (expanded) "▾ " else "▸ ") else "") + node.name, Modifier.weight(1f),
                style = if (depth == 0) TimeboxTheme.type.body else TimeboxTheme.type.bodySmall, fontWeight = if (depth == 0) FontWeight.Medium else FontWeight.Normal, color = colors.on)
            when (lane) {
                LaneMode.Planned -> Text("${durationShort(p)} · ${p * 100 / laneTotal.coerceAtLeast(1)}%", style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.planned)
                LaneMode.Actual -> Text("${durationShort(a)} · ${a * 100 / laneTotal.coerceAtLeast(1)}%", style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.actual)
                LaneMode.Both -> if (both == TypeBoth.Numbers) Row {
                    Text(if (p == 0) "—" else durationShort(p), Modifier.width(56.dp), style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.planned)
                    Text(if (a == 0) "—" else durationShort(a), Modifier.width(56.dp), style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.actual)
                    Text(diffText, Modifier.width(56.dp), style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.onVariant)
                } else Text("${durationShort(a)} / ${durationShort(p)} · $diffText", style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.onVariant)
            }
        }
        if (!(lane == LaneMode.Both && both == TypeBoth.Numbers)) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    lane == LaneMode.Planned -> Bar(p, scaleMax, colors.planned, filled = true)
                    lane == LaneMode.Actual -> Bar(a, scaleMax, colors.actual, filled = true)
                    both == TypeBoth.PairedBars -> { Bar(p, scaleMax, colors.planned, filled = false); Bar(a, scaleMax, colors.actual, filled = true) }
                    else -> MarkerBar(a, p, scaleMax)
                }
            }
            if (showDays) DayStrip(node, lane, dayCount)
        }
        else if (showDays) Row(Modifier.fillMaxWidth()) { Spacer(Modifier.weight(1f)); DayStrip(node, lane, dayCount) }
    }
}

@Composable
private fun Bar(value: Int, max: Int, color: Color, filled: Boolean) {
    Box(Modifier.fillMaxWidth().height(6.dp)) {
        if (value > 0) Box(Modifier.fill(value.toFloat() / max).fillMaxHeight().then(
            if (filled) Modifier.background(color, RoundedCornerShape(2.dp)) else Modifier.border(1.dp, color, RoundedCornerShape(2.dp))))
    }
}

@Composable
private fun MarkerBar(actual: Int, planned: Int, max: Int) {
    val colors = TimeboxTheme.colors
    Box(Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(colors.hairline.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
        if (actual > 0) Box(Modifier.fill(actual.toFloat() / max).height(6.dp).background(colors.actual, RoundedCornerShape(2.dp)))
        if (planned > 0) Row(Modifier.fillMaxWidth()) {
            val f = planned.toFloat() / max
            if (f < 1f) Spacer(Modifier.weight(f.coerceAtLeast(0.001f)))
            Box(Modifier.width(2.dp).height(10.dp).background(colors.planned))
            if (f < 1f) Spacer(Modifier.weight((1f - f).coerceAtLeast(0.001f)))
        }
    }
}

/** Per-day detail: tiny columns scaled to the row's busiest day. */
@Composable
private fun DayStrip(node: TypeNode, lane: LaneMode, dayCount: Int) {
    val colors = TimeboxTheme.colors
    val max = (0 until dayCount).maxOf { maxOf(node.planned[it], node.actual[it]) }.coerceAtLeast(1)
    Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        (0 until dayCount).forEach { i ->
            Row(Modifier.width(STRIP_CELL).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                if (lane != LaneMode.Actual) Box(Modifier.weight(1f).fillMaxHeight(maxOf(node.planned[i].toFloat() / max, 0.04f))
                    .then(if (lane == LaneMode.Both) Modifier.border(1.dp, colors.planned) else Modifier.background(colors.planned)))
                if (lane != LaneMode.Planned) Box(Modifier.weight(1f).fillMaxHeight(maxOf(node.actual[i].toFloat() / max, 0.04f)).background(colors.actual))
            }
        }
    }
}
