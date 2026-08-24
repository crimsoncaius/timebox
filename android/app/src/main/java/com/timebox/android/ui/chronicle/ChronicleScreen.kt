package com.timebox.android.ui.chronicle

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val CHRONICLE_SWIPE_THRESHOLD = 55.dp
private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH)

@Composable
fun ChronicleScreen(
    state: ChronicleUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = TimeboxTheme.colors

    when {
        state.loading && state.archived.isEmpty() -> {
            LoadingState(Modifier.fillMaxSize())
            return
        }
        state.error != null && state.archived.isEmpty() -> {
            ErrorState(message = state.error, onRetry = onRetry, modifier = Modifier.fillMaxSize())
            return
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TimeboxDimens.screenPadding)
                .padding(top = 2.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundIconButton(
                icon = Icons.Outlined.ChevronLeft,
                contentDescription = "Previous month",
                onClick = onPrevMonth,
                tint = colors.on,
                diameter = 36.dp,
                background = colors.low,
                iconSize = 19.dp,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(TimeboxShapes.field)
                    .background(colors.low)
                    .clickable(onClick = onThisMonth),
                contentAlignment = Alignment.Center,
            ) {
                Text("This month", style = TimeboxTheme.type.sectionTitle, color = colors.on)
            }
            RoundIconButton(
                icon = Icons.Outlined.ChevronRight,
                contentDescription = "Next month",
                onClick = onNextMonth,
                tint = colors.on,
                diameter = 36.dp,
                background = colors.low,
                iconSize = 19.dp,
            )
        }

        ChronicleMonthPager(
            state = state,
            onPrevMonth = onPrevMonth,
            onNextMonth = onNextMonth,
            onOpenDay = onOpenDay,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A bounded three-page track that keeps the displayed month owned by [ChronicleUiState]. */
@Composable
private fun ChronicleMonthPager(
    state: ChronicleUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val isSettling = rememberUpdatedState(settling)
    val previousMonth = rememberUpdatedState(onPrevMonth)
    val nextMonth = rememberUpdatedState(onNextMonth)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val displayedMonth = YearMonth.from(state.monthStart)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .semantics { contentDescription = "Chronicle month content" },
    ) {
        val pageWidthPx = constraints.maxWidth.toFloat()
        val thresholdPx = with(density) { CHRONICLE_SWIPE_THRESHOLD.toPx() }

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
                when (monthDelta) {
                    -1L -> previousMonth.value()
                    1L -> nextMonth.value()
                }
                dragOffsetPx = 0f
                settling = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(displayedMonth, pageWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (!isSettling.value) dragOffsetPx = 0f
                        },
                        onDragCancel = { settle(null) },
                        onDragEnd = {
                            val monthDelta = when {
                                dragOffsetPx < -thresholdPx -> 1L
                                dragOffsetPx > thresholdPx -> -1L
                                else -> null
                            }
                            settle(monthDelta)
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
                key(pageMonth) {
                    ChronicleMonthPage(
                        month = pageMonth,
                        state = state,
                        interactive = interactive,
                        onOpenDay = onOpenDay,
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
}

@Composable
private fun ChronicleMonthPage(
    month: YearMonth,
    state: ChronicleUiState,
    interactive: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Chronicle ${month.format(monthTitleFormatter)}"
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(bottom = TimeboxDimens.bottomInset),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WEEKDAYS.forEach { day ->
                Text(
                    text = day.uppercase(),
                    style = TimeboxTheme.type.laneLabel.copy(fontSize = 9.sp),
                    color = colors.onVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                )
            }
        }

        monthGrid(month.atDay(1)).chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == month
                    val archived = state.archived[date]
                    DayCell(
                        date = date,
                        inMonth = inMonth,
                        isToday = date == state.today,
                        archived = archived != null,
                        windowLabel = archived?.windowLabel,
                        onClick = { onOpenDay(date) },
                        enabled = interactive,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Days you have opened appear in the archive. Any day opens in Day.",
            style = TimeboxTheme.type.bodySmall,
            color = colors.onVariant,
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    archived: Boolean,
    /** Null for a day with nothing in it, so empty cells stay bare. */
    windowLabel: String?,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val background = when {
        !inMonth -> Color.Transparent
        archived -> colors.low
        else -> Color(0x0F808080)
    }
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(TimeboxShapes.cell)
            .background(background)
            .then(
                if (isToday && inMonth) {
                    Modifier.border(1.5.dp, colors.planned, TimeboxShapes.cell)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 5.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = TimeboxTheme.type.sectionTitle.copy(fontSize = 14.sp),
            color = when {
                isToday && inMonth -> colors.planned
                inMonth -> colors.on
                else -> colors.outlineVariant
            },
        )
        if (windowLabel != null && inMonth) {
            Spacer(Modifier.weight(1f))
            Text(
                text = windowLabel,
                style = TimeboxTheme.type.monoSmall.copy(fontSize = 7.5.sp),
                color = colors.onVariant,
            )
        }
    }
}

/** Six-week grid starting Monday, trimmed to five weeks when the sixth is empty. */
private fun monthGrid(monthStart: LocalDate): List<LocalDate> {
    val month = YearMonth.from(monthStart)
    val first = month.atDay(1)
    // DayOfWeek.MONDAY.value == 1, so this lands Monday in column zero.
    val lead = first.dayOfWeek.value - 1
    val cells = (0 until 42).map { first.minusDays(lead.toLong()).plusDays(it.toLong()) }
    val lastWeek = cells.drop(35)
    return if (lastWeek.none { YearMonth.from(it) == month }) cells.take(35) else cells
}
