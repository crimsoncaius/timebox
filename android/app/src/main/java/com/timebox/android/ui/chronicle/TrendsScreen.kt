package com.timebox.android.ui.chronicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.timebox.android.ui.components.CurrentRangeState
import com.timebox.android.ui.components.CurrentRangeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.remote.TrendNodeDto
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun trendDuration(seconds: Double): String {
    if (seconds > 0 && seconds < 60) return "<1m"
    val minutes = (seconds / 60).toLong()
    return "${minutes / 60}h ${minutes % 60}m"
}

@Composable
fun TrendsScreen(state: ChronicleUiState, viewModel: ChronicleViewModel) {
    val colors = TimeboxTheme.colors
    val report = state.trends
    var choosingDays by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { delay(60_000); viewModel.loadTrends() }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val customStart = state.customStart ?: state.today
        val customEnd = state.customEnd ?: state.today
        if (state.period == "custom") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ChooseDaysAction(onClick = { choosingDays = true })
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = trendRangeHeading("custom", customStart, customEnd, state.today),
                        color = colors.on,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(dayCountLabel(customStart, customEnd), color = colors.onVariant, fontSize = 12.sp)
                }
            }
        } else {
            // The pill keeps one width in both states so the arrows never move.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CurrentRangeAction(
                    state = if (showsCurrentTrendRange(state)) CurrentRangeState.Current else CurrentRangeState.Navigate,
                    currentLabel = if (state.period == "day") "Today" else "This ${state.period}",
                    navigateLabel = if (state.period == "day") "Go to today" else "Go to this ${state.period}",
                    onClick = viewModel::currentRange,
                    width = 156.dp,
                )
                IconButton(onClick = { viewModel.shiftRange(-1) }, enabled = report != null, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous ${state.period}", modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { viewModel.shiftRange(1) }, enabled = report != null && canAdvanceTrendRange(report, state.period), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next ${state.period}", modifier = Modifier.size(24.dp))
                }
                Text(
                    text = report?.let { trendRangeHeading(state.period, LocalDate.parse(it.start), LocalDate.parse(it.end), LocalDate.parse(it.today)) } ?: "…",
                    modifier = Modifier.padding(start = 2.dp).weight(1f),
                    color = colors.on,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxWidth().clip(TimeboxShapes.chip).background(colors.low).padding(4.dp)) {
            listOf("day", "week", "month", "custom").forEach { period ->
                val selected = state.period == period
                Box(Modifier.weight(1f).height(40.dp).clip(TimeboxShapes.chip).background(if (selected) colors.highest else Color.Transparent)
                    .selectable(selected = selected, enabled = period != "custom" || report != null || state.customStart != null, role = Role.Tab) { viewModel.setPeriod(period) }, contentAlignment = Alignment.Center) {
                    Text(period.replaceFirstChar { it.uppercase() }, color = if (selected) colors.on else colors.onVariant, fontSize = 13.sp)
                }
            }
        }
        if (choosingDays && state.period == "custom") {
            CustomRangeSheet(
                start = customStart,
                end = customEnd,
                today = state.today,
                onDismiss = { choosingDays = false },
                onShow = { start, end -> choosingDays = false; viewModel.customRange(start, end) },
            )
        }

        if (state.trendsLoading) Text("Updating recorded time…", color = colors.onVariant)
        state.customRangeError?.let { Text(it, color = colors.error) }
        state.trendsError?.let {
            Text(it, color = colors.error)
            TextButton(onClick = viewModel::loadTrends) { Text("Retry") }
        }
        if (report != null) {
            Column {
                Text(trendDuration(report.durationSeconds), color = colors.on, fontSize = 38.sp, fontWeight = FontWeight.Light)
                Text("Recorded time · ${report.timezone}", color = colors.onVariant, fontSize = 12.sp)
            }
            if (report.types.isEmpty()) Text("No recorded time in this range.", color = colors.onVariant)
            else {
                Column {
                    TrendTableHeader()
                    report.types.forEach { node -> key(node.path) {
                        TrendDivider()
                        // A Day range always has exactly one contributing day, so it offers no drill-through.
                        TrendNode(node, report.durationSeconds, 0, if (state.period == "day") null else viewModel::showContributingDays)
                    } }
                    TrendDivider()
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val shareColumn = 56.dp
private val timeColumn = 64.dp
private val percentColumn = 52.dp

@Composable
private fun TrendTableHeader() {
    val colors = TimeboxTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Task Type", color = colors.onVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("Share", color = colors.onVariant, fontSize = 12.sp, modifier = Modifier.width(shareColumn))
        Text("Time", color = colors.onVariant, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(timeColumn))
        Text("%", color = colors.onVariant, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(percentColumn))
    }
}

@Composable
private fun TrendDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TimeboxTheme.colors.low))
}

@Composable
private fun TrendNode(node: TrendNodeDto, total: Double, depth: Int, onDays: ((String, Map<String, Double>) -> Unit)?) {
    var expanded by rememberSaveable(node.path) { mutableStateOf(false) }
    TrendRow(node.name, node.name, node.durationSeconds, total, depth,
        expandable = node.children.isNotEmpty(), expanded = expanded,
        onExpand = { expanded = !expanded }, onDays = onDays?.let { drill -> { drill(node.path, node.days) } })
    if (expanded) {
        // Direct time participates in the same ranking as immediate children.
        val direct = if (node.directSeconds > 0) listOf(TrendNodeDto("${node.path}/", "Directly under ${node.name}", node.directSeconds, node.directSeconds, node.directDays, node.directDays)) else emptyList()
        (node.children + direct).sortedWith(compareByDescending<TrendNodeDto> { it.durationSeconds }.thenBy { it.path }).forEach { child -> key(child.path) {
            if (child.path.endsWith('/')) TrendRow(child.name, "(direct)", child.durationSeconds, total, depth + 1, direct = true, onDays = onDays?.let { drill -> { drill(child.name, child.days) } })
            else TrendNode(child, total, depth + 1, onDays)
        } }
    }
}

/**
 * One ledger row: tree guides and a chevron slot for depth, then fixed Share, Time and % columns.
 * Every bar shares the range-total scale; top-level rows are taller and heavier than their children.
 */
@Composable
private fun TrendRow(name: String, label: String, seconds: Double, total: Double, depth: Int, expandable: Boolean = false, expanded: Boolean = false, direct: Boolean = false, onExpand: () -> Unit = {}, onDays: (() -> Unit)?) {
    val colors = TimeboxTheme.colors
    val top = depth == 0
    val fraction = (seconds / total).coerceIn(0.0, 1.0)
    Row(Modifier.fillMaxWidth().heightIn(min = if (top) 52.dp else 40.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .clickable(enabled = expandable || onDays != null, role = Role.Button, onClickLabel = if (expandable) if (expanded) "Collapse $name" else "Expand $name" else "Show contributing days") { if (expandable) onExpand() else onDays?.invoke() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(depth.coerceAtMost(4)) {
                Box(Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outlineVariant))
                }
            }
            Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
                if (expandable) Icon(if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(18.dp))
            }
            Text(label, modifier = Modifier.weight(1f),
                color = if (direct) colors.onVariant else colors.on,
                fontSize = if (top) 15.sp else 13.sp,
                fontWeight = if (top) FontWeight.Medium else FontWeight.Normal,
                fontStyle = if (direct) FontStyle.Italic else FontStyle.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.width(shareColumn).padding(end = 8.dp).height(if (top) 8.dp else 5.dp).clip(TimeboxShapes.chip).background(colors.low)) {
                Box(Modifier.fillMaxWidth(fraction.toFloat()).fillMaxHeight().background(if (top) colors.on else colors.onVariant))
            }
        }
        Row((if (onDays != null) Modifier.clickable(role = Role.Button, onClickLabel = "Show contributing days for $name", onClick = onDays) else Modifier).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Text(trendDuration(seconds), color = colors.on, fontSize = if (top) 14.sp else 13.sp, fontWeight = if (top) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(timeColumn))
            Text(String.format(Locale.getDefault(), "%.1f%%", fraction * 100), color = colors.onVariant, fontSize = 12.sp,
                textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(percentColumn))
        }
    }
}

/**
 * Whether Trends shows the range containing Today, decided from the anchor so it holds while a
 * report is loading: no anchor is the current range; otherwise the anchor's Monday week or month must contain Today.
 */
internal fun showsCurrentTrendRange(state: ChronicleUiState): Boolean {
    val anchor = state.anchor ?: return true
    return when (state.period) {
        "day" -> anchor == state.today
        "week" -> anchor.with(DayOfWeek.MONDAY) == state.today.with(DayOfWeek.MONDAY)
        "month" -> YearMonth.from(anchor) == YearMonth.from(state.today)
        else -> false
    }
}

/**
 * The range heading beside the arrows: "Sun 27 Sep", "21–27 Sep", "September 2026"; other years add the year.
 * A custom range spanning years names both, since it can run longer than a year.
 */
internal fun trendRangeHeading(period: String, start: LocalDate, end: LocalDate, today: LocalDate): String {
    val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    val year = if (end.year != today.year) " ${end.year}" else ""
    return when {
        period == "month" -> start.format(DateTimeFormatter.ofPattern("MMMM uuuu"))
        start == end -> start.format(DateTimeFormatter.ofPattern("EEE d MMM")) + year
        period == "custom" && start.year != end.year -> DateTimeFormatter.ofPattern("d MMM uuuu").let { "${start.format(it)} – ${end.format(it)}" }
        start.month == end.month && start.year == end.year -> "${start.dayOfMonth}–${end.format(dayMonth)}$year"
        else -> "${start.format(dayMonth)} – ${end.format(dayMonth)}$year"
    }
}

/** Compact labels leave equal space for the two fixed-size navigation targets. */
internal fun trendRangeLabel(period: String, start: LocalDate, end: LocalDate): String {
    val fullDate = DateTimeFormatter.ofPattern("d MMM uuuu")
    return when {
        period == "month" -> start.format(DateTimeFormatter.ofPattern("MMMM uuuu"))
        start == end -> start.format(fullDate)
        start.year != end.year -> "${start.format(fullDate)} – ${end.format(fullDate)}"
        start.month == end.month -> "${start.dayOfMonth}–${end.format(fullDate)}"
        else -> "${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(fullDate)}"
    }
}
