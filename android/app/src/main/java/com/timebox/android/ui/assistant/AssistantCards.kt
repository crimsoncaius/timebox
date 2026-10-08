package com.timebox.android.ui.assistant

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.durationShort
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun AssistantCards(cards: List<AssistantCard>, onOpenDay: (LocalDate) -> Unit, onOpenTrends: (LocalDate, LocalDate) -> Unit) {
    if (cards.isEmpty()) return
    if (cards.size == 1) { ActivityCard(cards.single(), onOpenDay, onOpenTrends); return }
    val pager = rememberPagerState(pageCount = { cards.size })
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { isTraversalGroup = true }) {
        ScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 0.dp, containerColor = TimeboxTheme.colors.bg) {
            cards.forEachIndexed { index, card ->
                Tab(selected = index == pager.currentPage, onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(card.label, style = TimeboxTheme.type.bodySmall) },
                    modifier = Modifier.semantics { contentDescription = "${if (card.types == null) "Blocks" else "Task Types"}, ${card.label}, card ${index + 1} of ${cards.size}" })
            }
        }
        HorizontalPager(state = pager, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) { index ->
            // Offscreen pages must not participate in accessibility traversal.
            Box(if (index == pager.currentPage) Modifier else Modifier.clearAndSetSemantics {}) {
                ActivityCard(cards[index], onOpenDay, onOpenTrends)
            }
        }
        Row(Modifier.align(Alignment.CenterHorizontally).clearAndSetSemantics { contentDescription = "Card ${pager.currentPage + 1} of ${cards.size}" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.indices.forEach { index -> Box(Modifier.size(6.dp).background(if (index == pager.currentPage) TimeboxTheme.colors.on else TimeboxTheme.colors.hairline, CircleShape)) }
        }
    }
}

@Composable
private fun ActivityCard(card: AssistantCard, onOpenDay: (LocalDate) -> Unit, onOpenTrends: (LocalDate, LocalDate) -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(color = colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (card.types == null) "Blocks · ${card.lane.replaceFirstChar { it.uppercase() }}" else "Time by Task Type · ${card.lane.replaceFirstChar { it.uppercase() }}",
                Modifier.semantics { heading() }, style = TimeboxTheme.type.body)
            Text(card.label, style = TimeboxTheme.type.body)
            Text("Read ${card.readAt.atZone(card.zone).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))} · ${card.zone.id}", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            card.filter?.let { Text("Task Type: $it", style = TimeboxTheme.type.bodySmall) }
            card.weekdays?.let { days -> Text("Days: " + days.joinToString { java.time.DayOfWeek.of(it + 1).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }, style = TimeboxTheme.type.bodySmall) }
            if (card.future) Text("Future recurring work isn’t included.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            card.actualUnavailable?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
            if (card.types != null) {
                if (card.lane != "planned" && card.date <= card.todayAtRead && card.endDate >= card.todayAtRead) Text("Actual time stops at the read time.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                TypeRows(card)
                if (card.endDate > card.todayAtRead) Text("Trends shows recorded time through Today.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                TextButton(onClick = { onOpenTrends(card.date, card.endDate) }) { Text("Open Trends") }
            } else {
                BlockRows(card)
                TextButton(onClick = { onOpenDay(card.date) }) { Text("Open Day") }
            }
        }
    }
}

private fun blockDescription(card: AssistantCard, block: AssistantBlock): String {
    val pattern = DateTimeFormatter.ofPattern("d MMM HH:mm")
    val times = "${block.start.atZone(card.zone).format(pattern)}–${block.end.atZone(card.zone).format(pattern)}"
    val duration = if (block.running) "running · ${durationShort(block.minutes)} at read" else durationShort(block.minutes)
    val crossing = if (block.minutes != block.minutesInDate) " total · ${durationShort(block.minutesInDate)} this day" else ""
    return "$times · ${block.taskType} · $duration$crossing"
}

@Composable
private fun BlockRows(card: AssistantCard) {
    var expanded by rememberSaveable(card.id) { mutableStateOf(false) }
    if (card.blocks.isEmpty()) { Text("No ${card.lane} Blocks for this date.", style = TimeboxTheme.type.bodySmall); return }
    if (card.lane == "both" && !card.future) {
        TwoLanes(card, expanded)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show smaller" else "Show larger") }
        // Full rows preserve midnight crossings and tiny blocks at every font scale.
        if (!expanded) return
    }
    val rows = if (expanded) card.blocks else card.blocks.take(3)
    rows.forEach { block ->
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(block.start.atZone(card.zone).format(DateTimeFormatter.ofPattern("HH:mm")) + if (card.lane == "both") " · ${block.lane}" else "",
                style = TimeboxTheme.type.mono, color = if (block.lane == "planned") TimeboxTheme.colors.planned else TimeboxTheme.colors.actual)
            Text(block.title, style = TimeboxTheme.type.body)
            Text(blockDescription(card, block), style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        }
    }
    if (card.lane != "both" || card.future) if (card.blocks.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show fewer" else "Show all ${card.blocks.size} blocks") }
}

@Composable
private fun TwoLanes(card: AssistantCard, expanded: Boolean) {
    val colors = TimeboxTheme.colors
    fun minute(block: AssistantBlock, end: Boolean = false): Int {
        val time = (if (end) block.end else block.start).atZone(card.zone)
        return ((time.toLocalDate().toEpochDay() - card.date.toEpochDay()) * 1440 + time.hour * 60 + time.minute).toInt()
    }
    val from = if (expanded) 0 else (card.blocks.filter { it.lane == "planned" }.minOfOrNull { minute(it) }
        ?: card.blocks.minOf { minute(it) }).coerceIn(0, 1439) / 60 * 60
    val to = if (expanded) 1440 else maxOf(from + 60, ((card.blocks.maxOf { minute(it, true) }.coerceIn(0, 1440) + 59) / 60) * 60)
    val scale = if (expanded) 1.1f else .42f
    val fontScale = LocalDensity.current.fontScale
    if (card.blocks.any { minute(it) < from }) Text("Earlier time is hidden. Show larger to see the full day.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(42.dp))
        Text("Plan", Modifier.weight(1f), color = colors.planned, style = TimeboxTheme.type.bodySmall)
        Text("Actual", Modifier.weight(1f), color = colors.actual, style = TimeboxTheme.type.bodySmall)
    }
    Row(Modifier.fillMaxWidth().height(((to - from) * scale).dp)) {
        Box(Modifier.width(42.dp).fillMaxHeight()) {
            (from until to step if (expanded) 60 else 120).forEach { time ->
                Text("%02d".format(time / 60), Modifier.offset(y = ((time - from) * scale).dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
        }
        listOf("planned", "actual").forEach { lane ->
            Box(Modifier.weight(1f).fillMaxHeight().padding(end = 4.dp).background(colors.bg).clipToBounds()) {
                card.blocks.filter { it.lane == lane && minute(it, true) > from }.forEach { block ->
                    val top = maxOf(from, minute(block)); val bottom = minOf(to, minute(block, true))
                    val height = maxOf(3f, (bottom - top) * scale)
                    Surface(Modifier.fillMaxWidth().offset(y = ((top - from) * scale).dp).height(height.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "${block.lane}, ${block.title}. ${blockDescription(card, block)}" },
                        color = if (lane == "planned") colors.plannedSurface else colors.actualSurface,
                        border = BorderStroke(1.dp, if (lane == "planned") colors.plannedBorder else colors.actualBorder), shape = RoundedCornerShape(3.dp)) {
                        if (height >= 22 * fontScale) Text(block.title + if (block.running) " · running at read" else "", Modifier.padding(3.dp).clearAndSetSemantics {}, style = TimeboxTheme.type.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeRows(card: AssistantCard) {
    val roots = card.rootTypes
    if (roots.isEmpty()) { Text("No stored time for this selection.", style = TimeboxTheme.type.bodySmall); return }
    val totalPlan = roots.sumOf { it.planned }; val totalActual = roots.sumOf { it.actual }
    val maximum = roots.maxOf { maxOf(it.planned, it.actual) }.coerceAtLeast(1.0)
    roots.forEach { TypeRow(card, it, totalPlan, totalActual, maximum, 0) }
}

@Composable
private fun TypeRow(card: AssistantCard, row: AssistantType, totalPlan: Double, totalActual: Double, maximum: Double, depth: Int) {
    val colors = TimeboxTheme.colors
    var expanded by rememberSaveable(card.id, row.path) { mutableStateOf(false) }
    val children = card.types.orEmpty().filter { it.path.substringBeforeLast('/', "") == row.path }
    fun duration(seconds: Double) = durationShort((seconds / 60).toInt())
    fun share(seconds: Double, total: Double) = if (total > 0) "${(seconds / total * 100).roundToInt()}%" else "0%"
    val summary = when (card.lane) {
        "both" -> "${duration(row.actual)} actual / ${duration(row.planned)} plan · " +
            if (row.planned == 0.0) "not planned" else "${if (row.actual >= row.planned) "+" else "−"}${duration(kotlin.math.abs(row.actual - row.planned))}"
        "planned" -> "${duration(row.planned)} · ${share(row.planned, totalPlan)} of planned time"
        else -> "${duration(row.actual)} · ${share(row.actual, totalActual)} of actual time"
    }
    Column(Modifier.fillMaxWidth().padding(start = minOf(depth, 3).times(12).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().then(if (children.isNotEmpty()) Modifier.clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse children" else "Expand children") { expanded = !expanded }.heightIn(min = 48.dp) else Modifier)
            .semantics(mergeDescendants = true) { if (children.isNotEmpty()) stateDescription = if (expanded) "Expanded" else "Collapsed" }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.path.substringAfterLast('/'), style = TimeboxTheme.type.body)
                Text(summary, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            if (children.isNotEmpty()) Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (card.lane != "actual" && row.planned > 0) Box(Modifier.fillMaxWidth((row.planned / maximum).toFloat().coerceIn(.001f, 1f)).height(6.dp).border(1.dp, colors.planned, RoundedCornerShape(2.dp)))
            if (card.lane != "planned" && row.actual > 0) Box(Modifier.fillMaxWidth((row.actual / maximum).toFloat().coerceIn(.001f, 1f)).height(6.dp).background(colors.actual, RoundedCornerShape(2.dp)))
        }
        if (card.lane == "both") Text("${share(row.actual, totalActual)} of actual · ${share(row.planned, totalPlan)} of plan", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
    }
    if (expanded) children.forEach { TypeRow(card, it, totalPlan, totalActual, maximum, depth + 1) }
}
