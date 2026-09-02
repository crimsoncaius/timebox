package com.timebox.android.ui.day

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.components.Hairline
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val fullCalendarDateFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH)
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH)
private val WEEK_SWIPE_THRESHOLD = 55.dp

@Composable
internal fun DayCalendarHeader(
    selectedDate: LocalDate,
    today: LocalDate?,
    isPlanningMode: Boolean,
    planningActionEnabled: Boolean = true,
    onOpenWorkMode: () -> Unit,
    onSetPlanningMode: (Boolean) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNavigateToday: (LocalDate) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var monthMode by remember { mutableStateOf(false) }
    var displayedMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    LaunchedEffect(selectedDate, monthMode) {
        if (monthMode) displayedMonth = YearMonth.from(selectedDate)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = "DAY",
                    style = TimeboxTheme.type.navLabel,
                    color = colors.onVariant,
                )
                Text(
                    text = formatCalendarHeaderDate(selectedDate),
                    style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, lineHeight = 27.sp),
                    color = colors.on,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WorkModeAction(onClick = onOpenWorkMode)
                PlanningModeAction(
                    isPlanningMode = isPlanningMode,
                    enabled = planningActionEnabled,
                    onClick = { onSetPlanningMode(!isPlanningMode) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TodayAction(
                state = when {
                    today == null -> TodayControlState.Resolving
                    selectedDate == today -> TodayControlState.Current
                    else -> TodayControlState.Navigate
                },
                onClick = { today?.let(onNavigateToday) },
            )
            Spacer(Modifier.weight(1f))
            CalendarModeControl(
                monthSelected = monthMode,
                onWeek = { monthMode = false },
                onMonth = {
                    displayedMonth = YearMonth.from(selectedDate)
                    monthMode = true
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        if (monthMode) {
            MonthPager(
                displayedMonth = displayedMonth,
                selectedDate = selectedDate,
                today = today,
                onDisplayedMonthChange = { displayedMonth = it },
                onSelectDate = { date ->
                    displayedMonth = YearMonth.from(date)
                    onSelectDate(date)
                },
            )
        } else {
            WeekRow(
                selectedDate = selectedDate,
                today = today,
                onSelectDate = onSelectDate,
            )
        }
        Spacer(Modifier.height(24.dp))
        Hairline(Modifier.testTag("day-header-divider"))
    }
}

@Composable
private fun WorkModeAction(onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag("work-mode-action")
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Work Mode" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .border(1.dp, colors.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = colors.onVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private enum class TodayControlState { Navigate, Current, Resolving }

@Composable
private fun TodayAction(state: TodayControlState, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    val interaction = when (state) {
        TodayControlState.Navigate -> Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Go to today" }
        TodayControlState.Current -> Modifier
            .semantics { contentDescription = "Viewing today" }
        TodayControlState.Resolving -> Modifier
            .clickable(enabled = false, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Today unavailable" }
    }
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(shape)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(42.dp)
                .clip(shape)
                .background(
                    when (state) {
                        TodayControlState.Navigate -> colors.planned
                        TodayControlState.Current -> colors.plannedSurface
                        TodayControlState.Resolving -> colors.disabledContainer
                    },
                )
                .then(
                    if (state == TodayControlState.Current) {
                        Modifier.border(1.dp, colors.plannedBorder, shape)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = if (state == TodayControlState.Current) {
                    Icons.Outlined.Check
                } else {
                    Icons.Outlined.CalendarToday
                },
                contentDescription = null,
                tint = when (state) {
                    TodayControlState.Navigate -> colors.lowest
                    TodayControlState.Current -> colors.planned
                    TodayControlState.Resolving -> colors.disabledContent
                },
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = if (state == TodayControlState.Navigate) "Go to today" else "Today",
                style = TimeboxTheme.type.label,
                color = when (state) {
                    TodayControlState.Navigate -> colors.lowest
                    TodayControlState.Current -> colors.on
                    TodayControlState.Resolving -> colors.disabledContent
                },
            )
        }
    }
}

@Composable
private fun PlanningModeAction(isPlanningMode: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .height(48.dp)
            .testTag("planning-mode-action")
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.62f },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(shape)
                .background(if (isPlanningMode) colors.planned else Color.Transparent)
                .then(
                    if (isPlanningMode) Modifier else Modifier.border(1.dp, colors.plannedBorder, shape),
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = if (isPlanningMode) Icons.Outlined.Check else Icons.Outlined.Checklist,
                contentDescription = null,
                tint = if (isPlanningMode) colors.lowest else colors.planned,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = if (isPlanningMode) "Done" else "Plan",
                style = TimeboxTheme.type.label,
                color = if (isPlanningMode) colors.lowest else colors.planned,
            )
        }
    }
}

@Composable
private fun CalendarModeControl(
    monthSelected: Boolean,
    onWeek: () -> Unit,
    onMonth: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .height(32.dp)
            .width(116.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.low)
            .padding(2.dp)
            .testTag("calendar-mode-control")
            .selectableGroup(),
    ) {
        CalendarModeSegment("Week", selected = !monthSelected, onClick = onWeek, Modifier.weight(1f))
        CalendarModeSegment("Month", selected = monthSelected, onClick = onMonth, Modifier.weight(1f))
    }
}

@Composable
private fun CalendarModeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = TimeboxTheme.colors
    Box(
        modifier = modifier
            .fillMaxHeight()
            .testTag("calendar-mode-${label.lowercase(Locale.ENGLISH)}")
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) colors.lowest else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TimeboxTheme.type.navLabel,
            color = if (selected) colors.on else colors.onVariant,
        )
    }
}

@Composable
private fun MonthPager(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate?,
    onDisplayedMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val isSettling = rememberUpdatedState(settling)
    val changeDisplayedMonth = rememberUpdatedState(onDisplayedMonthChange)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(292.dp)
            .clipToBounds()
            .semantics { contentDescription = "Month dates" },
    ) {
        val pageWidthPx = constraints.maxWidth.toFloat()
        val thresholdPx = with(density) { WEEK_SWIPE_THRESHOLD.toPx() }

        fun settle(monthDelta: Long?) {
            if (settling || pageWidthPx <= 0f) return
            settling = true
            scope.launch {
                val target = monthDelta?.let { -it * pageWidthPx } ?: 0f
                animate(
                    initialValue = dragOffsetPx,
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 520f),
                ) { value, _ ->
                    dragOffsetPx = value
                }
                if (monthDelta != null) {
                    changeDisplayedMonth.value(displayedMonth.plusMonths(monthDelta))
                }
                dragOffsetPx = 0f
                settling = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(292.dp)
                .pointerInput(displayedMonth, pageWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (!isSettling.value) dragOffsetPx = 0f
                        },
                        onDragCancel = { settle(null) },
                        onDragEnd = {
                            settle(committedMonthDelta(dragOffsetPx, thresholdPx))
                        },
                        onHorizontalDrag = { change, amount ->
                            if (!isSettling.value) {
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + amount)
                                    .coerceIn(-pageWidthPx, pageWidthPx)
                            }
                        },
                    )
                },
        ) {
            (-1..1).forEach { pagePosition ->
                val pageMonth = displayedMonth.plusMonths(pagePosition.toLong())
                val interactive = pagePosition == 0 && !settling
                MonthPage(
                    displayedMonth = pageMonth,
                    selectedDate = selectedDate,
                    today = today,
                    enabled = interactive,
                    onSelectDate = { date ->
                        if (YearMonth.from(date) != displayedMonth) {
                            changeDisplayedMonth.value(YearMonth.from(date))
                        }
                        onSelectDate(date)
                    },
                    modifier = Modifier.offset {
                        IntOffset(
                            x = (pagePosition * pageWidthPx + dragOffsetPx).roundToInt(),
                            y = 0,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthPage(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate?,
    enabled: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(292.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            text = displayedMonth.atDay(1).format(monthFormatter),
            style = TimeboxTheme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.on,
            modifier = Modifier
                .height(28.dp)
                .padding(start = 8.dp, top = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEach { weekday ->
                Text(
                    text = weekday,
                    style = TimeboxTheme.type.navLabel,
                    color = colors.onVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        monthDates(displayedMonth).chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(39.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                week.forEach { date ->
                    MonthDateCell(
                        date = date,
                        inDisplayedMonth = YearMonth.from(date) == displayedMonth,
                        selected = date == selectedDate,
                        today = date == today,
                        enabled = enabled,
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDateCell(
    date: LocalDate,
    inDisplayedMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val background = when {
        selected -> colors.selected
        today -> colors.plannedSurface
        else -> Color.Transparent
    }
    val textColor = monthDateTextColor(
        colors = colors,
        selected = selected,
        today = today,
        inDisplayedMonth = inDisplayedMonth,
    )
    val borderModifier = when {
        selected -> Modifier.border(1.dp, colors.plannedBorder, shape)
        today -> Modifier.border(1.dp, colors.planned, shape)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .height(37.dp)
            .then(borderModifier)
            .clip(shape)
            .background(background)
            .semantics(mergeDescendants = true) {
                contentDescription = date.format(fullCalendarDateFormatter)
                stateDescription = buildList {
                    if (selected) add("Selected")
                    if (today) add("Today")
                    if (!inDisplayedMonth) add("Outside displayed month")
                }.joinToString(", ")
            }
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = TimeboxTheme.type.bodySmall.copy(
                fontWeight = if (inDisplayedMonth) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun monthDateTextColor(
    colors: com.timebox.android.ui.theme.TimeboxColors,
    selected: Boolean,
    today: Boolean,
    inDisplayedMonth: Boolean,
): Color = when {
        selected -> colors.onSelected
        today -> colors.planned
        inDisplayedMonth -> colors.onVariant
        else -> colors.onVariant
    }

@Composable
private fun WeekRow(
    selectedDate: LocalDate,
    today: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val isSettling = rememberUpdatedState(settling)
    val selectDate = rememberUpdatedState(onSelectDate)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clipToBounds()
            .semantics { contentDescription = "Week dates" }
    ) {
        val pageWidthPx = constraints.maxWidth.toFloat()
        val thresholdPx = with(density) { WEEK_SWIPE_THRESHOLD.toPx() }

        fun settle(dayDelta: Long?) {
            if (settling || pageWidthPx <= 0f) return
            settling = true
            scope.launch {
                val target = dayDelta?.let { -(it / 7f) * pageWidthPx } ?: 0f
                animate(
                    initialValue = dragOffsetPx,
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 520f),
                ) { value, _ ->
                    dragOffsetPx = value
                }
                if (dayDelta != null) selectDate.value(selectedDate.plusDays(dayDelta))
                dragOffsetPx = 0f
                settling = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .pointerInput(selectedDate, pageWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (!isSettling.value) dragOffsetPx = 0f
                    },
                    onDragCancel = { settle(null) },
                    onDragEnd = { settle(committedWeekDelta(dragOffsetPx, thresholdPx)) },
                    onHorizontalDrag = { change, amount ->
                        if (!isSettling.value) {
                            change.consume()
                            dragOffsetPx = (dragOffsetPx + amount)
                                .coerceIn(-pageWidthPx, pageWidthPx)
                        }
                    },
                )
                },
        ) {
            (-1..1).forEach { pagePosition ->
                val pageSelectedDate = selectedDate.plusDays(pagePosition * 7L)
                val interactive = pagePosition == 0 && !settling
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset {
                            IntOffset(
                                x = (pagePosition * pageWidthPx + dragOffsetPx).roundToInt(),
                                y = 0,
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    weekDates(pageSelectedDate).forEach { date ->
                        WeekDateCell(
                            date = date,
                            selected = date == pageSelectedDate,
                            today = date == today,
                            enabled = interactive,
                            onClick = { selectDate.value(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekDateCell(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val background = when {
        selected -> colors.selected
        today -> colors.plannedSurface
        else -> Color.Transparent
    }
    val textColor = when {
        selected -> colors.onSelected
        today -> colors.planned
        else -> colors.onVariant
    }
    val borderModifier = when {
        selected -> Modifier.border(1.dp, colors.plannedBorder, shape)
        today -> Modifier.border(1.dp, colors.planned, shape)
        else -> Modifier
    }

    Column(
        modifier = modifier
            .height(54.dp)
            .then(borderModifier)
            .clip(shape)
            .background(background)
            .semantics(mergeDescendants = true) {
                contentDescription = date.format(fullCalendarDateFormatter)
                stateDescription = when {
                    selected && today -> "Selected, Today"
                    selected -> "Selected"
                    today -> "Today"
                    else -> ""
                }
            }
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.format(weekdayFormatter).uppercase(Locale.ENGLISH),
            style = TimeboxTheme.type.navLabel,
            color = textColor,
            textAlign = TextAlign.Center,
        )
        Text(
            date.dayOfMonth.toString(),
            style = TimeboxTheme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}
