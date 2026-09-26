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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    onSelectView: (ChronicleView) -> Unit = {},
    onClearHighlights: () -> Unit = {},
    trendsContent: @Composable () -> Unit = {},
) {
    val colors = TimeboxTheme.colors
    val canNextMonth = YearMonth.from(state.monthStart) < YearMonth.from(state.today)

    when {
        state.view == ChronicleView.Calendar && state.loading && state.archived.isEmpty() && state.highlightedDays.isEmpty() -> {
            LoadingState(Modifier.fillMaxSize())
            return
        }
        state.view == ChronicleView.Calendar && state.error != null && state.archived.isEmpty() && state.highlightedDays.isEmpty() -> {
            ErrorState(message = state.error, onRetry = onRetry, modifier = Modifier.fillMaxSize())
            return
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        ChronicleViewTabs(state.view, onSelectView)

        if (state.view == ChronicleView.Trends) {
            trendsContent()
            return@Column
        }

        state.highlightedType?.let { name ->
            ChronicleHighlight(
                name = name,
                days = state.highlightedDays,
                range = state.highlightedRange,
                onBack = { onSelectView(ChronicleView.Trends) },
                onClear = onClearHighlights,
            )
        }

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
                enabled = canNextMonth,
                tint = if (canNextMonth) colors.on else colors.onVariant.copy(alpha = 0.4f),
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

/** Calendar header while Trends contributing days are highlighted: a breadcrumb back to the range and a one-line summary. */
@Composable
private fun ChronicleHighlight(
    name: String,
    days: Map<String, Double>,
    range: TrendHighlightRange?,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val segments = name.split('/').filter { it.isNotEmpty() }
    val leaf = segments.lastOrNull() ?: name
    val summary = buildAnnotatedString {
        segments.dropLast(1).forEach { parent -> withStyle(SpanStyle(color = colors.onVariant)) { append("$parent / ") } }
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(leaf) }
        append(chronicleHighlightSummary(days, range))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(bottom = 12.dp)
            .height(IntrinsicSize.Min)
            .testTag("chronicle-highlight"),
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(colors.actual))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Trends",
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClickLabel = "Back to Trends", onClick = onBack)
                        .padding(vertical = 10.dp),
                )
                Text(
                    range?.let { "  ›  ${chronicleHighlightRangeLabel(it)}" } ?: "",
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Show all", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
            }
            Text(summary, color = colors.on, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Light)
        }
    }
}

/** "Week · 21–27 Sep 2026", using the same date label Trends shows for the range. */
internal fun chronicleHighlightRangeLabel(range: TrendHighlightRange): String {
    val kind = if (range.period == "custom") "Custom range" else range.period.replaceFirstChar { it.uppercase() }
    return "$kind · ${trendRangeLabel(range.period, range.start, range.end)}"
}

/** The sentence after the Task Type name: " on 2 of 7 days, 2h 5m in total." */
internal fun chronicleHighlightSummary(days: Map<String, Double>, range: TrendHighlightRange?): String {
    val total = trendDuration(days.values.sum())
    val count = days.size
    if (range == null) return " on $count ${if (count == 1) "day" else "days"}, $total in total."
    val rangeDays = java.time.temporal.ChronoUnit.DAYS.between(range.start, range.end) + 1
    return " on $count of $rangeDays ${if (rangeDays == 1L) "day" else "days"}, $total in total."
}

/** Full-width underline tabs switching between Chronicle's Calendar and Trends views. */
@Composable
private fun ChronicleViewTabs(selected: ChronicleView, onSelect: (ChronicleView) -> Unit) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            ChronicleView.entries.forEach { view ->
                val active = view == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chronicle-view-${view.name.lowercase(Locale.ENGLISH)}")
                        .selectable(selected = active, role = Role.Tab) { onSelect(view) }
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        view.name,
                        style = TimeboxTheme.type.sectionTitle,
                        color = if (active) colors.on else colors.onVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(2.dp)
                            .background(if (active) colors.on else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.low))
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
    val canNextMonth = displayedMonth < YearMonth.from(state.today)

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
                                canNextMonth && dragOffsetPx < -thresholdPx -> 1L
                                dragOffsetPx > thresholdPx -> -1L
                                else -> null
                            }
                            settle(monthDelta)
                        },
                        onHorizontalDrag = { change, amount ->
                            if (!isSettling.value) {
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + amount)
                                    .coerceIn(if (canNextMonth) -pageWidthPx else 0f, pageWidthPx)
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
                        recorded = (archived?.actualCount ?: 0) > 0 ||
                            (state.highlightedDays[date.toString()] ?: 0.0) > 0.0,
                        highlighted = date.toString() in state.highlightedDays,
                        onClick = { onOpenDay(date) },
                        enabled = interactive,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "A dot means time was recorded. Tap any date to open Day.",
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
    recorded: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val background = when {
        highlighted -> colors.primaryContainer
        !inMonth -> Color.Transparent
        archived -> colors.low
        else -> Color(0x0F808080)
    }
    Column(
        modifier = modifier
            .height(52.dp)
            .clip(TimeboxShapes.cell)
            .background(background)
            .then(
                if (isToday && inMonth) {
                    Modifier.border(1.5.dp, colors.planned, TimeboxShapes.cell)
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = "$date${if (isToday) ", today" else ""}, ${if (recorded) "time recorded" else "no time recorded"}"
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 5.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = TimeboxTheme.type.sectionTitle.copy(
                fontSize = 14.sp,
                fontWeight = if (inMonth) TimeboxTheme.type.sectionTitle.fontWeight else FontWeight.Normal,
            ),
            color = chronicleDateTextColor(colors, isToday, inMonth),
        )
        if (recorded && inMonth) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(5.dp)
                    .background(colors.on, CircleShape)
                    .testTag("chronicle-recorded-$date"),
            )
        }
    }
}

internal fun chronicleDateTextColor(
    colors: com.timebox.android.ui.theme.TimeboxColors,
    isToday: Boolean,
    inMonth: Boolean,
): Color = when {
    isToday && inMonth -> colors.planned
    inMonth -> colors.on
    else -> colors.onVariant
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
