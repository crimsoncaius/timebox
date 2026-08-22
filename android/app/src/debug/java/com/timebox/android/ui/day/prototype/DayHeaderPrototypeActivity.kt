package com.timebox.android.ui.day.prototype

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PROTOTYPE — throwaway code.
 *
 * Three variants that separate Today (date navigation) from Plan (workspace mode),
 * switchable via timebox://prototype/day-header?variant=A in a debug build.
 */
class DayHeaderPrototypeActivity : ComponentActivity() {
    private var activeVariant by mutableStateOf(PrototypeVariant.A)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        activeVariant = PrototypeVariant.from(intent.data)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            TimeboxTheme(darkTheme = false) {
                DayHeaderPrototype(
                    variant = activeVariant,
                    onVariantChange = ::showVariant,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeVariant = PrototypeVariant.from(intent.data)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            showVariant(activeVariant.previous())
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            showVariant(activeVariant.next())
            true
        }
        else -> super.onKeyUp(keyCode, event)
    }

    private fun showVariant(variant: PrototypeVariant) {
        activeVariant = variant
        setIntent(
            Intent(intent).setData(
                Uri.parse("timebox://prototype/day-header?variant=${variant.key}"),
            ),
        )
    }
}

private enum class PrototypeVariant(val key: String, val label: String) {
    A("A", "Split header"),
    B("B", "Plan handoff"),
    C("C", "Workspace action");

    fun previous(): PrototypeVariant = entries[(ordinal - 1 + entries.size) % entries.size]
    fun next(): PrototypeVariant = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(uri: Uri?): PrototypeVariant {
            val requested = uri?.getQueryParameter("variant")?.uppercase(Locale.ENGLISH)
            return entries.firstOrNull { it.key == requested } ?: A
        }
    }
}

private val initialSelectedDate: LocalDate = LocalDate.of(2026, 8, 21)
private val today: LocalDate = LocalDate.of(2026, 8, 22)
private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH)
private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)

@Composable
private fun DayHeaderPrototype(
    variant: PrototypeVariant,
    onVariantChange: (PrototypeVariant) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var monthMode by rememberSaveable { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(initialSelectedDate) }
    var planningMode by rememberSaveable { mutableStateOf(false) }
    val navigateToday = { selectedDate = today }
    val togglePlanning = { planningMode = !planningMode }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 92.dp),
        ) {
            Column(Modifier.statusBarsPadding()) {
                when (variant) {
                    PrototypeVariant.A -> TitleLedHeader(
                        selectedDate = selectedDate,
                        monthMode = monthMode,
                        isPlanningMode = planningMode,
                        onMonthModeChange = { monthMode = it },
                        onNavigateToday = navigateToday,
                        onTogglePlanning = togglePlanning,
                    )
                    PrototypeVariant.B -> CenteredModesHeader(
                        selectedDate = selectedDate,
                        monthMode = monthMode,
                        onMonthModeChange = { monthMode = it },
                        onNavigateToday = navigateToday,
                    )
                    PrototypeVariant.C -> CalendarLedHeader(
                        selectedDate = selectedDate,
                        monthMode = monthMode,
                        onMonthModeChange = { monthMode = it },
                        onNavigateToday = navigateToday,
                    )
                }

                PrototypeCalendar(
                    monthMode = monthMode,
                    variant = variant,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it },
                )

                if (variant == PrototypeVariant.B) {
                    PlanHandoff(
                        isPlanningMode = planningMode,
                        onTogglePlanning = togglePlanning,
                    )
                }

                when (variant) {
                    PrototypeVariant.A -> TimelinePreview(
                        topGap = 24.dp,
                        treatment = TimelineTreatment.Rule,
                        isPlanningMode = planningMode,
                    )
                    PrototypeVariant.B -> TimelinePreview(
                        topGap = 18.dp,
                        treatment = TimelineTreatment.Open,
                        isPlanningMode = planningMode,
                    )
                    PrototypeVariant.C -> TimelinePreview(
                        topGap = 24.dp,
                        treatment = TimelineTreatment.Rule,
                        isPlanningMode = planningMode,
                        onTogglePlanning = togglePlanning,
                    )
                }
            }
        }

        PrototypeSwitcher(
            variant = variant,
            onPrevious = { onVariantChange(variant.previous()) },
            onNext = { onVariantChange(variant.next()) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TitleLedHeader(
    selectedDate: LocalDate,
    monthMode: Boolean,
    isPlanningMode: Boolean,
    onMonthModeChange: (Boolean) -> Unit,
    onNavigateToday: () -> Unit,
    onTogglePlanning: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DAY", style = TimeboxTheme.type.navLabel, color = colors.onVariant)
                Text(
                    selectedDate.format(fullDateFormatter),
                    style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp),
                    color = colors.on,
                )
            }
            PlanAction(
                isPlanningMode = isPlanningMode,
                onClick = onTogglePlanning,
                treatment = ActionTreatment.Outline,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TodayAction(
                enabled = selectedDate != today,
                onClick = onNavigateToday,
            )
            Spacer(Modifier.weight(1f))
            CapsuleModeControl(monthMode, onMonthModeChange)
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun CenteredModesHeader(
    selectedDate: LocalDate,
    monthMode: Boolean,
    onMonthModeChange: (Boolean) -> Unit,
    onNavigateToday: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Schedule", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                Text(
                    selectedDate.format(fullDateFormatter),
                    style = TimeboxTheme.type.sectionTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.on,
                )
            }
            TodayAction(enabled = selectedDate != today, onClick = onNavigateToday)
        }
        Spacer(Modifier.height(14.dp))
        UnderlineModeControl(monthMode, onMonthModeChange)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun CalendarLedHeader(
    selectedDate: LocalDate,
    monthMode: Boolean,
    onMonthModeChange: (Boolean) -> Unit,
    onNavigateToday: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    selectedDate.dayOfWeek.name.lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) },
                    style = TimeboxTheme.type.screenTitle.copy(fontSize = 27.sp),
                    color = colors.on,
                )
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)),
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                )
            }
            TodayAction(enabled = selectedDate != today, onClick = onNavigateToday)
        }
        Spacer(Modifier.height(16.dp))
        UnderlineModeControl(monthMode, onMonthModeChange)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun CapsuleModeControl(monthMode: Boolean, onMonthModeChange: (Boolean) -> Unit) {
    TwoWayControl(
        left = "Week",
        right = "Month",
        rightSelected = monthMode,
        width = 108.dp,
        onLeft = { onMonthModeChange(false) },
        onRight = { onMonthModeChange(true) },
    )
}

@Composable
private fun TwoWayControl(
    left: String,
    right: String,
    rightSelected: Boolean,
    width: Dp,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .width(width)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.low)
            .padding(2.dp),
    ) {
        listOf(left to false, right to true).forEach { (label, isRight) ->
            val selected = isRight == rightSelected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            selected -> colors.lowest
                            else -> Color.Transparent
                        },
                    )
                    .clickable(onClick = if (isRight) onRight else onLeft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = TimeboxTheme.type.navLabel,
                    color = when {
                        selected -> colors.on
                        else -> colors.onVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun UnderlineModeControl(monthMode: Boolean, onMonthModeChange: (Boolean) -> Unit) {
    val colors = TimeboxTheme.colors
    Row(Modifier.fillMaxWidth().height(36.dp)) {
        listOf("Week" to false, "Month" to true).forEach { (label, mode) ->
            val selected = monthMode == mode
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable { onMonthModeChange(mode) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    label,
                    style = TimeboxTheme.type.label,
                    color = if (selected) colors.planned else colors.onVariant,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (selected) 2.dp else 1.dp)
                        .background(if (selected) colors.planned else colors.hairline),
                )
            }
        }
    }
}

@Composable
private fun TodayAction(enabled: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val contentColor = if (enabled) colors.onVariant else colors.outlineVariant.copy(alpha = 0.58f)
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (enabled) colors.low else colors.low.copy(alpha = 0.48f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Outlined.CalendarToday,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(15.dp),
        )
        Text("Today", style = TimeboxTheme.type.bodySmall, color = contentColor)
    }
}

private enum class ActionTreatment { Outline, Filled, Text }

@Composable
private fun PlanAction(
    isPlanningMode: Boolean,
    onClick: () -> Unit,
    treatment: ActionTreatment,
    expandedLabel: Boolean = false,
) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val filled = treatment == ActionTreatment.Filled || isPlanningMode
    val label = when {
        isPlanningMode -> "Done"
        expandedLabel -> "Plan this day"
        else -> "Plan"
    }
    val icon: ImageVector = if (isPlanningMode) Icons.Outlined.Check else Icons.Outlined.Checklist
    val baseModifier = Modifier
        .height(36.dp)
        .clip(shape)
        .background(if (filled) colors.planned else Color.Transparent)
        .then(
            if (treatment == ActionTreatment.Outline && !isPlanningMode) {
                Modifier.border(1.dp, colors.plannedBorder, shape)
            } else {
                Modifier
            },
        )
        .clickable(onClick = onClick)
        .padding(horizontal = if (expandedLabel) 16.dp else 12.dp)
    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) colors.lowest else colors.planned,
            modifier = Modifier.size(17.dp),
        )
        Text(
            label,
            style = TimeboxTheme.type.label,
            color = if (filled) colors.lowest else colors.planned,
        )
    }
}

@Composable
private fun PlanHandoff(isPlanningMode: Boolean, onTogglePlanning: () -> Unit) {
    val colors = TimeboxTheme.colors
    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.plannedSurface)
            .border(1.dp, colors.plannedBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (isPlanningMode) "Planning workspace" else "Ready to shape the day?",
                style = TimeboxTheme.type.label,
                color = colors.on,
            )
            Text(
                if (isPlanningMode) "Tasks are ready to place" else "Open tasks ready to schedule",
                style = TimeboxTheme.type.navLabel,
                color = colors.onVariant,
            )
        }
        PlanAction(
            isPlanningMode = isPlanningMode,
            onClick = onTogglePlanning,
            treatment = ActionTreatment.Filled,
            expandedLabel = !isPlanningMode,
        )
    }
}

@Composable
private fun PrototypeCalendar(
    monthMode: Boolean,
    variant: PrototypeVariant,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    val modifier = when (variant) {
        PrototypeVariant.C -> Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(TimeboxTheme.colors.lowest)
            .padding(horizontal = 8.dp, vertical = 6.dp)
        else -> Modifier.padding(horizontal = 12.dp)
    }
    Column(modifier.fillMaxWidth()) {
        if (monthMode) {
            MonthCalendar(selectedDate = selectedDate, onSelectDate = onSelectDate)
        } else {
            WeekCalendar(selectedDate = selectedDate, onSelectDate = onSelectDate)
        }
    }
}

@Composable
private fun WeekCalendar(selectedDate: LocalDate, onSelectDate: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val monday = selectedDate.with(DayOfWeek.MONDAY)
        repeat(7) { index ->
            val date = monday.plusDays(index.toLong())
            WeekDate(
                date = date,
                selectedDate = selectedDate,
                onSelectDate = onSelectDate,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekDate(
    date: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val selected = date == selectedDate
    val isToday = date == today
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .height(58.dp)
            .then(if (isToday && !selected) Modifier.border(1.dp, colors.plannedBorder, shape) else Modifier)
            .clip(shape)
            .background(if (selected) colors.planned else if (isToday) colors.plannedSurface else Color.Transparent)
            .clickable { onSelectDate(date) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfWeek.name.take(3),
            style = TimeboxTheme.type.navLabel,
            color = if (selected) colors.lowest else colors.onVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            date.dayOfMonth.toString(),
            style = TimeboxTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.lowest else if (isToday) colors.planned else colors.on,
        )
    }
}

@Composable
private fun MonthCalendar(selectedDate: LocalDate, onSelectDate: (LocalDate) -> Unit) {
    val colors = TimeboxTheme.colors
    val month = YearMonth.from(selectedDate)
    val first = month.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    Text(
        month.atDay(1).format(monthTitleFormatter),
        style = TimeboxTheme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = colors.on,
        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 8.dp),
    )
    Row(Modifier.fillMaxWidth()) {
        listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEach { label ->
            Text(
                label,
                style = TimeboxTheme.type.navLabel,
                color = colors.onVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    repeat(6) { row ->
        Row(Modifier.fillMaxWidth().height(38.dp)) {
            repeat(7) { column ->
                val date = gridStart.plusDays((row * 7L) + column)
                MonthDate(
                    date = date,
                    month = month,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MonthDate(
    date: LocalDate,
    month: YearMonth,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val selected = date == selectedDate
    val isToday = date == today
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .height(34.dp)
            .padding(horizontal = 2.dp)
            .then(if (isToday && !selected) Modifier.border(1.dp, colors.plannedBorder, shape) else Modifier)
            .clip(shape)
            .background(if (selected) colors.planned else if (isToday) colors.plannedSurface else Color.Transparent)
            .clickable { onSelectDate(date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = TimeboxTheme.type.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = when {
                selected -> colors.lowest
                isToday -> colors.planned
                YearMonth.from(date) != month -> colors.outlineVariant.copy(alpha = 0.48f)
                else -> colors.onVariant
            },
        )
    }
}

private enum class TimelineTreatment { Rule, Open }

@Composable
private fun TimelinePreview(
    topGap: Dp,
    treatment: TimelineTreatment,
    isPlanningMode: Boolean,
    onTogglePlanning: (() -> Unit)? = null,
) {
    val colors = TimeboxTheme.colors
    Spacer(Modifier.height(topGap))
    val shell = when (treatment) {
        TimelineTreatment.Rule -> Modifier.fillMaxWidth().border(width = 0.dp, color = Color.Transparent)
        TimelineTreatment.Open -> Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    }
    Column(shell) {
        if (treatment == TimelineTreatment.Rule) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(12.dp))
        }
        if (onTogglePlanning != null) {
            WorkspaceActionRow(
                isPlanningMode = isPlanningMode,
                onTogglePlanning = onTogglePlanning,
            )
            Spacer(Modifier.height(12.dp))
        }
        TimelineHeader(isPlanningMode = isPlanningMode)
        Spacer(Modifier.height(8.dp))
        TimelineGrid(isPlanningMode = isPlanningMode)
    }
}

@Composable
private fun WorkspaceActionRow(isPlanningMode: Boolean, onTogglePlanning: () -> Unit) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (isPlanningMode) "Planning workspace" else "Day timeline",
                style = TimeboxTheme.type.sectionTitle,
                color = colors.on,
            )
            Text(
                if (isPlanningMode) "Place ready tasks into Planned" else "Planned beside what happened",
                style = TimeboxTheme.type.navLabel,
                color = colors.onVariant,
            )
        }
        PlanAction(
            isPlanningMode = isPlanningMode,
            onClick = onTogglePlanning,
            treatment = ActionTreatment.Text,
        )
    }
}

@Composable
private fun TimelineHeader(isPlanningMode: Boolean) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
    ) {
        Spacer(Modifier.width(TimeboxDimens.gutterWidth))
        Text(
            "PLANNED",
            style = TimeboxTheme.type.laneLabel,
            color = colors.planned,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (isPlanningMode) "READY TO PLAN" else "ACTUAL",
            style = TimeboxTheme.type.laneLabel,
            color = if (isPlanningMode) colors.onVariant else colors.actual,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TimelineGrid(isPlanningMode: Boolean) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(152.dp).padding(horizontal = TimeboxDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
    ) {
        Column(Modifier.width(TimeboxDimens.gutterWidth), horizontalAlignment = Alignment.End) {
            listOf("8 AM", "9 AM", "10 AM").forEach { label ->
                Text(
                    label,
                    style = TimeboxTheme.type.gutter,
                    color = colors.timelineLabel,
                    modifier = Modifier.height(50.dp).padding(end = 6.dp),
                )
            }
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().background(colors.plannedSurface)
                .border(1.dp, colors.plannedBorder),
        )
        if (isPlanningMode) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(colors.low)
                    .border(1.dp, colors.hairline)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                listOf("Write brief", "Review notes", "Call Jordan").forEach { task ->
                    Text(
                        task,
                        style = TimeboxTheme.type.bodySmall,
                        color = colors.onVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.lowest)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                    )
                }
            }
        } else {
            Box(
                Modifier.weight(1f).fillMaxHeight().background(colors.actualSurface)
                    .border(1.dp, colors.actualBorder),
            )
        }
    }
}

@Composable
private fun PrototypeSwitcher(
    variant: PrototypeVariant,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF202526)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clickable(onClick = onPrevious), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous variant", tint = Color.White)
        }
        Text(
            "${variant.key} — ${variant.label}",
            style = TimeboxTheme.type.label,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(170.dp),
        )
        Box(Modifier.size(48.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next variant", tint = Color.White)
        }
    }
}
