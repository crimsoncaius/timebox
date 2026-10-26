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
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val fullCalendarDateFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH)
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)
private val WEEK_SWIPE_THRESHOLD = 55.dp

/**
 * One title row above an optional week strip. The
 * "Back to today" pill appears only while another date is shown.
 */
@Composable
internal fun DayCalendarHeader(
    selectedDate: LocalDate,
    today: LocalDate?,
    isPlanningMode: Boolean,
    planningActionEnabled: Boolean = true,
    showCalendar: Boolean = true,
    viewAction: @Composable () -> Unit = {},
    hiddenTrackingAction: @Composable () -> Unit = {},
    onSetPlanningMode: (Boolean) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNavigateToday: (LocalDate) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.timebox.android.ui.components.ScreenBackButton()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Selected date, ${selectedDate.format(fullCalendarDateFormatter)}"
                    },
            ) {
                Text(
                    text = dayTitleKicker(selectedDate, today),
                    style = TimeboxTheme.type.bodySmall,
                    color = if (selectedDate == today) colors.planned else colors.onVariant,
                    maxLines = 1,
                )
                Text(
                    text = dayTitle(selectedDate, today),
                    style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, lineHeight = 27.sp),
                    color = colors.on,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (today != null && selectedDate != today) BackToTodayPill { onNavigateToday(today) }
                hiddenTrackingAction()
                if (!showCalendar) {
                    androidx.compose.material3.TextButton(onClick = { onSelectDate(selectedDate.minusDays(1)) }) { Text("‹", modifier = Modifier.semantics { contentDescription = "Previous day" }) }
                    androidx.compose.material3.TextButton(onClick = { onSelectDate(selectedDate.plusDays(1)) }) { Text("›", modifier = Modifier.semantics { contentDescription = "Next day" }) }
                }
                PlanningModeAction(
                    isPlanningMode = isPlanningMode,
                    enabled = planningActionEnabled,
                    onClick = { onSetPlanningMode(!isPlanningMode) },
                )
                viewAction()
            }
        }

        if (showCalendar) {
            Spacer(Modifier.height(4.dp))
            WeekRow(
                selectedDate = selectedDate,
                today = today,
                onSelectDate = onSelectDate,
            )
        }
        Spacer(Modifier.height(6.dp))
        Hairline(Modifier.testTag("day-header-divider"))
    }
}

@Composable
private fun BackToTodayPill(onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Go to today" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Back to today",
            style = TimeboxTheme.type.navLabel,
            color = colors.planned,
            modifier = Modifier
                .clip(shape)
                .border(1.dp, colors.plannedBorder, shape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
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
            .height(48.dp)
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
                .height(48.dp)
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
                        .padding(horizontal = 10.dp, vertical = 2.dp)
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
    val shape = RoundedCornerShape(12.dp)
    // Today is the filled date; another selected date is outlined so the two never look alike.
    val background = if (today) colors.planned else Color.Transparent
    val textColor = when {
        today -> colors.lowest
        selected -> colors.on
        else -> colors.onVariant
    }
    Column(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(background)
            .then(if (selected && !today) Modifier.border(1.5.dp, colors.on, shape) else Modifier)
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
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.format(weekdayFormatter),
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
