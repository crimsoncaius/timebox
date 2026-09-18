package com.timebox.android.ui.chronicle

// PROTOTYPE (#210), throwaway. Built only with -PchroniclePrototype=true on a debug build.
// Question: what form should Chronicle's Calendar | Trends switch take on Android?
//   A  Pill segment above the month navigation; Trends follows the displayed month.
//   B  Full-width underline tabs; Trends owns its own range and drops month navigation.
//   C  No switch; Trends is a drawer over the Calendar that drills in by highlighting days.
// Every variant drills from a Task Type figure back to the Calendar.
// Trends figures are placeholders summed from archived days; the real content is #10.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

private enum class ChronicleVariant(val label: String) {
    A("Pill + shared month"),
    B("Underline tabs"),
    C("Trends drawer"),
}

// Top-level so the choice survives leaving and re-entering Chronicle.
private var prototypeVariant by mutableStateOf(ChronicleVariant.A)

@Composable
internal fun ChronicleTrendsPrototype(
    state: ChronicleUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        key(prototypeVariant) {
            when (prototypeVariant) {
                ChronicleVariant.A -> VariantA(state, onPrevMonth, onNextMonth, onThisMonth, onOpenDay)
                ChronicleVariant.B -> VariantB(state, onPrevMonth, onNextMonth, onThisMonth, onOpenDay)
                ChronicleVariant.C -> VariantC(state, onPrevMonth, onNextMonth, onThisMonth, onOpenDay)
            }
        }
        PrototypeSwitcher(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
    }
}

// A: pill segment, month navigation shared by both views.
@Composable
private fun VariantA(
    state: ChronicleUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    var trends by remember { mutableStateOf(false) }
    var drillType by remember { mutableStateOf<String?>(null) }
    val month = YearMonth.from(state.monthStart)
    Column(Modifier.fillMaxSize()) {
        PillSegment(
            trends = trends,
            onSelect = { trends = it },
            modifier = Modifier
                .padding(horizontal = TimeboxDimens.screenPadding)
                .padding(bottom = 10.dp)
                .fillMaxWidth(),
        )
        if (!trends) {
            DrillChip(drillType) { drillType = null }
            ChronicleCalendar(
                state, onPrevMonth, onNextMonth, onThisMonth, onOpenDay,
                modifier = Modifier.weight(1f),
                highlighted = drillType?.let { daysWithType(state, it, month.atDay(1)..month.atEndOfMonth()) }.orEmpty(),
            )
        } else {
            ChronicleMonthNav(onPrevMonth, onNextMonth, onThisMonth)
            TrendsScroll(Modifier.weight(1f)) {
                TrendsFigures(summarize(state, month.atDay(1)..month.atEndOfMonth())) { type ->
                    drillType = type
                    trends = false
                }
            }
        }
    }
}

// B: underline tabs; Trends has its own range and no month navigation.
@Composable
private fun VariantB(
    state: ChronicleUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var trends by remember { mutableStateOf(false) }
    var drillType by remember { mutableStateOf<String?>(null) }
    var rangeDays by remember { mutableIntStateOf(30) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            listOf(false to "Calendar", true to "Trends").forEach { (isTrends, label) ->
                val selected = trends == isTrends
                Column(
                    Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab) { trends = isTrends }
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = TimeboxTheme.type.sectionTitle, color = if (selected) colors.on else colors.onVariant)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(2.dp)
                            .background(if (selected) colors.on else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.low))
        Spacer(Modifier.height(12.dp))
        if (!trends) {
            DrillChip(drillType) { drillType = null }
            val month = YearMonth.from(state.monthStart)
            ChronicleCalendar(
                state, onPrevMonth, onNextMonth, onThisMonth, onOpenDay,
                modifier = Modifier.weight(1f),
                highlighted = drillType?.let { daysWithType(state, it, month.atDay(1)..month.atEndOfMonth()) }.orEmpty(),
            )
        } else {
            Row(
                Modifier.padding(horizontal = TimeboxDimens.screenPadding).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(7, 30, 90, 365).forEach { days ->
                    val selected = rangeDays == days
                    Box(
                        Modifier
                            .clip(TimeboxShapes.chip)
                            .background(if (selected) colors.on else colors.low)
                            .clickable { rangeDays = days }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (days == 365) "Year" else "$days days",
                            style = TimeboxTheme.type.label,
                            color = if (selected) colors.bg else colors.on,
                        )
                    }
                }
            }
            TrendsScroll(Modifier.weight(1f)) {
                TrendsFigures(summarize(state, state.today.minusDays(rangeDays - 1L)..state.today)) { type ->
                    drillType = type
                    trends = false
                }
            }
        }
    }
}

// C: no switch; Trends docks over the Calendar and drills in place.
@Composable
private fun VariantC(
    state: ChronicleUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var drillType by remember { mutableStateOf<String?>(null) }
    val month = YearMonth.from(state.monthStart)
    val range = month.atDay(1)..month.atEndOfMonth()
    val summary = summarize(state, range)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DrillChip(drillType) { drillType = null }
            ChronicleCalendar(
                state, onPrevMonth, onNextMonth, onThisMonth, onOpenDay,
                modifier = Modifier.weight(1f),
                highlighted = drillType?.let { daysWithType(state, it, range) }.orEmpty(),
            )
            Spacer(Modifier.height(120.dp))
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (expanded) Modifier.fillMaxHeight(0.8f) else Modifier)
                .shadow(12.dp, TimeboxShapes.sheet)
                .clip(TimeboxShapes.sheet)
                .background(colors.lowest)
                .padding(horizontal = 16.dp)
                .padding(bottom = 60.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(36.dp).height(4.dp).clip(TimeboxShapes.chip).background(colors.onVariant.copy(alpha = 0.4f)))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Trends · ${month.format(monthTitleFormatter)}", style = TimeboxTheme.type.sectionTitle, color = colors.on, modifier = Modifier.weight(1f))
                    Text(
                        "${summary.daysRecorded}d · ${formatMinutes(summary.trackedMinutes)}",
                        style = TimeboxTheme.type.monoSmall,
                        color = colors.onVariant,
                    )
                }
            }
            if (expanded) {
                TrendsScroll(Modifier.weight(1f), horizontalPadding = 0.dp) {
                    TrendsFigures(summary) { type ->
                        drillType = type
                        expanded = false
                    }
                }
            }
        }
    }
}

@Composable
private fun PillSegment(trends: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier) {
    val colors = TimeboxTheme.colors
    Row(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.low)
            .padding(2.dp)
            .selectableGroup(),
    ) {
        listOf(false to "Calendar", true to "Trends").forEach { (isTrends, label) ->
            val selected = trends == isTrends
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) colors.lowest else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab) { onSelect(isTrends) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = TimeboxTheme.type.navLabel, color = if (selected) colors.on else colors.onVariant)
            }
        }
    }
}

@Composable
private fun DrillChip(type: String?, onClear: () -> Unit) {
    if (type == null) return
    val colors = TimeboxTheme.colors
    Box(
        Modifier
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(bottom = 8.dp)
            .clip(TimeboxShapes.chip)
            .background(colors.low)
            .clickable(onClick = onClear)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("Days with $type  ·  Clear", style = TimeboxTheme.type.label, color = colors.on)
    }
}

@Composable
private fun TrendsScroll(
    modifier: Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = TimeboxDimens.screenPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .padding(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun TrendsFigures(summary: TrendsSummary, onType: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Tracked", formatMinutes(summary.trackedMinutes), Modifier.weight(1f))
        StatTile("Days recorded", summary.daysRecorded.toString(), Modifier.weight(1f))
        StatTile("Days opened", summary.daysOpened.toString(), Modifier.weight(1f))
    }
    Text("BY TASK TYPE", style = TimeboxTheme.type.kicker, color = colors.onVariant)
    if (summary.byType.isEmpty()) {
        Text("Nothing recorded in this range.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
    }
    val max = summary.byType.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
    summary.byType.take(6).forEach { (name, minutes) ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(TimeboxShapes.field)
                .clickable { onType(name) }
                .padding(vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(name, style = TimeboxTheme.type.body, color = colors.on, modifier = Modifier.weight(1f), maxLines = 1)
                Text(formatMinutes(minutes), style = TimeboxTheme.type.monoSmall, color = colors.onVariant)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(TimeboxShapes.chip).background(colors.low)) {
                Box(Modifier.fillMaxWidth(minutes.toFloat() / max).fillMaxHeight().background(colors.actual))
            }
        }
    }
    Text("Placeholder figures. Trends content is #10.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    val colors = TimeboxTheme.colors
    Column(modifier.clip(TimeboxShapes.card).background(colors.low).padding(12.dp)) {
        Text(value, style = TimeboxTheme.type.sectionTitle, color = colors.on)
        Spacer(Modifier.height(2.dp))
        Text(label, style = TimeboxTheme.type.bodySmall, color = colors.onVariant, maxLines = 1)
    }
}

@Composable
private fun PrototypeSwitcher(modifier: Modifier) {
    val variants = ChronicleVariant.entries
    fun step(delta: Int) {
        prototypeVariant = variants[(prototypeVariant.ordinal + delta + variants.size) % variants.size]
    }
    Row(
        modifier
            .shadow(8.dp, TimeboxShapes.chip)
            .clip(TimeboxShapes.chip)
            .background(Color(0xFF111111))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clickable { step(-1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous variant", tint = Color.White)
        }
        Text("${prototypeVariant.name} · ${prototypeVariant.label}", style = TimeboxTheme.type.label, color = Color.White)
        Box(Modifier.size(40.dp).clickable { step(1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next variant", tint = Color.White)
        }
    }
}

private data class TrendsSummary(
    val daysRecorded: Int,
    val daysOpened: Int,
    val trackedMinutes: Long,
    val byType: List<Pair<String, Long>>,
)

private fun summarize(state: ChronicleUiState, range: ClosedRange<LocalDate>): TrendsSummary {
    val days = state.archived.filterKeys { it in range }.values
    val blocks = days.flatMap { it.actualBlocks }
    fun minutes(block: com.timebox.android.data.ActualBlock) =
        block.endAt?.let { Duration.between(block.startAt, it).toMinutes() } ?: 0L
    val byType = blocks.groupBy { it.taskTypeName }
        .mapValues { (_, typed) -> typed.sumOf(::minutes) }
        .toList()
        .sortedByDescending { it.second }
    return TrendsSummary(
        daysRecorded = days.count { it.actualBlocks.isNotEmpty() },
        daysOpened = days.size,
        trackedMinutes = blocks.sumOf(::minutes),
        byType = byType,
    )
}

private fun daysWithType(state: ChronicleUiState, type: String, range: ClosedRange<LocalDate>): Set<LocalDate> =
    state.archived.filter { (date, day) -> date in range && day.actualBlocks.any { it.taskTypeName == type } }.keys

private fun formatMinutes(minutes: Long): String = "%dh %02dm".format(minutes / 60, minutes % 60)
