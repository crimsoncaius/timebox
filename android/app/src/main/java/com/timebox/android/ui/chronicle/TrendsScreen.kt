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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.remote.TrendNodeDto
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.showMondayDatePicker
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
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
    val context = LocalContext.current
    val report = state.trends
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM uuuu") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { delay(60_000); viewModel.loadTrends() }
        }
    }
    fun pick(initial: LocalDate, selected: (LocalDate) -> Unit) {
        showMondayDatePicker(context, initial, state.today, selected)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("day", "week", "month", "custom").forEach { period ->
                Box(Modifier.weight(1f).clip(TimeboxShapes.chip).background(if (state.period == period) colors.on else colors.low)
                    .selectable(selected = state.period == period, enabled = period != "custom" || report != null || state.customStart != null, role = Role.Tab) { viewModel.setPeriod(period) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(period.replaceFirstChar { it.uppercase() }, color = if (state.period == period) colors.bg else colors.on, fontSize = 13.sp)
                }
            }
        }
        if (state.period == "custom") {
            val start = state.customStart ?: state.today
            val end = state.customEnd ?: state.today
            Column {
                TextButton(onClick = { pick(start) { viewModel.customRange(it, end) } }) { Text("From ${start.format(formatter)}") }
                TextButton(onClick = { pick(end) { viewModel.customRange(start, it) } }) { Text("To ${end.format(formatter)} (inclusive)") }
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.shiftRange(-1) }, enabled = report != null, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous ${state.period}", modifier = Modifier.size(24.dp))
                    }
                    Text(
                        text = report?.let { trendRangeLabel(state.period, LocalDate.parse(it.start), LocalDate.parse(it.end)) } ?: "…",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = colors.on,
                        fontSize = 15.sp,
                    )
                    IconButton(onClick = { viewModel.shiftRange(1) }, enabled = report != null && canAdvanceTrendRange(report, state.period), modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next ${state.period}", modifier = Modifier.size(24.dp))
                    }
                }
                TextButton(onClick = viewModel::currentRange) { Text(if (state.period == "day") "Today" else "This ${state.period}") }
            }
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
                Text("BY TASK TYPE", color = colors.onVariant, fontSize = 11.sp, letterSpacing = 1.sp)
                report.types.forEach { node -> key(node.path) {
                    // A Day range always has exactly one contributing day, so it offers no drill-through.
                    TrendNode(node, report.durationSeconds, 0, if (state.period == "day") null else viewModel::showContributingDays)
                } }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TrendNode(node: TrendNodeDto, total: Double, depth: Int, onDays: ((String, Map<String, Double>) -> Unit)?) {
    var expanded by rememberSaveable(node.path) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TrendRow(node.name, node.durationSeconds, total, depth,
            expandable = node.children.isNotEmpty(), expanded = expanded,
            onExpand = { expanded = !expanded }, onDays = onDays?.let { drill -> { drill(node.path, node.days) } })
        if (expanded) {
            // Direct time participates in the same ranking as immediate children.
            val direct = if (node.directSeconds > 0) listOf(TrendNodeDto("${node.path}/", "Directly under ${node.name}", node.directSeconds, node.directSeconds, node.directDays, node.directDays)) else emptyList()
            (node.children + direct).sortedWith(compareByDescending<TrendNodeDto> { it.durationSeconds }.thenBy { it.path }).forEach { child -> key(child.path) {
                if (child.path.endsWith('/')) TrendRow(child.name, child.durationSeconds, total, depth + 1, onDays = onDays?.let { drill -> { drill(child.name, child.days) } })
                else TrendNode(child, total, depth + 1, onDays)
            } }
        }
    }
}

@Composable
private fun TrendRow(name: String, seconds: Double, total: Double, depth: Int, expandable: Boolean = false, expanded: Boolean = false, onExpand: () -> Unit = {}, onDays: (() -> Unit)?) {
    val colors = TimeboxTheme.colors
    val fraction = (seconds / total).coerceIn(0.0, 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text((if (expandable) if (expanded) "▾  " else "▸  " else "") + name,
                color = if (depth == 0) colors.on else colors.onVariant, fontSize = if (depth == 0) 15.sp else 13.sp,
                modifier = Modifier.weight(1f).clickable(enabled = expandable || onDays != null, role = Role.Button, onClickLabel = if (expandable) if (expanded) "Collapse $name" else "Expand $name" else "Show contributing days") { if (expandable) onExpand() else onDays?.invoke() }.padding(start = (depth.coerceAtMost(4) * 18).dp, top = 12.dp, bottom = 12.dp))
            Column((if (onDays != null) Modifier.clickable(role = Role.Button, onClickLabel = "Show contributing days for $name", onClick = onDays) else Modifier).padding(start = 8.dp, top = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.End) {
                Text(trendDuration(seconds), color = colors.on, fontSize = 14.sp)
                Text(String.format(Locale.getDefault(), "%.1f%%", fraction * 100), color = colors.onVariant, fontSize = 11.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(if (depth == 0) 6.dp else 4.dp).clip(TimeboxShapes.chip).background(colors.low)) {
            Box(Modifier.fillMaxWidth(fraction.toFloat()).fillMaxHeight().background(if (depth == 0) colors.onVariant else colors.outline))
        }
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
