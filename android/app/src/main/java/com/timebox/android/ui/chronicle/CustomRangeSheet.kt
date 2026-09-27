package com.timebox.android.ui.chronicle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val WEEKDAY_INITIALS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
private val monthTitle = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH)
private val dayDescription = DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.ENGLISH)

/** A custom range being chosen on the calendar: [end] is null until the last day is picked. */
internal data class RangeDraft(val start: LocalDate, val end: LocalDate?)

/** A tap starts a new range, unless it picks the last day of one already started. */
internal fun nextRangeDraft(draft: RangeDraft, date: LocalDate): RangeDraft =
    if (draft.end != null || date < draft.start) RangeDraft(date, null) else RangeDraft(draft.start, date)

internal fun dayCountLabel(start: LocalDate, end: LocalDate): String {
    val days = ChronoUnit.DAYS.between(start, end) + 1
    return if (days == 1L) "1 day" else "$days days"
}

/** "14 Sep", or "14 Sep 2025" outside Today's year. */
internal fun trendDayLabel(date: LocalDate, today: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM uuuu", Locale.ENGLISH))

/** The Custom range's place in the toolbar: an outlined "Choose days" pill that opens [CustomRangeSheet]. */
@Composable
internal fun ChooseDaysAction(onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Box(Modifier.height(48.dp).clip(shape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier.height(42.dp).clip(shape).border(1.dp, colors.outlineVariant, shape).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(Icons.Outlined.DateRange, contentDescription = null, tint = colors.on, modifier = Modifier.size(17.dp))
            Text("Choose days", style = TimeboxTheme.type.label, color = colors.on, maxLines = 1)
        }
    }
}

/**
 * A Monday-first calendar for choosing custom days: the first tap picks the first day, the second the last,
 * and the days between are shaded. Nothing changes until the chosen days are shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomRangeSheet(
    start: LocalDate,
    end: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onShow: (LocalDate, LocalDate) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var draftStart by rememberSaveable { mutableStateOf(start.toEpochDay()) }
    var draftEnd by rememberSaveable { mutableStateOf<Long?>(end.toEpochDay()) }
    var shownMonth by rememberSaveable { mutableStateOf(YearMonth.from(end).toString()) }
    val draft = RangeDraft(LocalDate.ofEpochDay(draftStart), draftEnd?.let(LocalDate::ofEpochDay))
    val month = YearMonth.parse(shownMonth)
    val canShowNextMonth = month < YearMonth.from(today)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet.copy(alpha = 1f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text("Choose days", style = TimeboxTheme.type.sectionTitle.copy(fontSize = 18.sp), color = colors.on)
            Spacer(Modifier.height(16.dp))
            // The underline marks the slot the next tap fills.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RangeSlot("First day", trendDayLabel(draft.start, today), next = draft.end != null)
                RangeSlot("Last day", draft.end?.let { trendDayLabel(it, today) }, next = draft.end == null)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(
                    icon = Icons.Outlined.ChevronLeft, contentDescription = "Previous month",
                    onClick = { shownMonth = month.minusMonths(1).toString() },
                    tint = colors.on, diameter = 40.dp, iconSize = 21.dp,
                )
                Text(month.format(monthTitle), style = TimeboxTheme.type.label, color = colors.on, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                RoundIconButton(
                    icon = Icons.Outlined.ChevronRight, contentDescription = "Next month",
                    onClick = { shownMonth = month.plusMonths(1).toString() }, enabled = canShowNextMonth,
                    tint = if (canShowNextMonth) colors.on else colors.onVariant.copy(alpha = 0.35f), diameter = 40.dp, iconSize = 21.dp,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
                WEEKDAY_INITIALS.forEach {
                    Text(it.uppercase(), style = TimeboxTheme.type.laneLabel.copy(fontSize = 9.5.sp), color = colors.onVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            rangeMonthCells(month).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    week.forEachIndexed { column, date ->
                        if (date == null) Spacer(Modifier.weight(1f)) else RangeDay(date, column, draft, today, month) {
                            val next = nextRangeDraft(draft, date)
                            draftStart = next.start.toEpochDay()
                            draftEnd = next.end?.toEpochDay()
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { draft.end?.let { onShow(draft.start, it) } },
                enabled = draft.end != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.on, contentColor = colors.bg,
                    disabledContainerColor = colors.disabledContainer, disabledContentColor = colors.disabledContent,
                ),
            ) { Text(draft.end?.let { "Show ${dayCountLabel(draft.start, it)}" } ?: "Choose the last day", style = TimeboxTheme.type.button) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", style = TimeboxTheme.type.button, color = colors.onVariant)
            }
        }
    }
}

@Composable
private fun RowScope.RangeSlot(label: String, value: String?, next: Boolean) {
    val colors = TimeboxTheme.colors
    Column(Modifier.weight(1f)) {
        Text(label.uppercase(), style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
        Text(
            value ?: "Tap a day",
            color = if (value == null) colors.onVariant else colors.on,
            fontSize = 20.sp, fontWeight = FontWeight.Light, fontFamily = TimeboxTheme.type.sectionTitle.fontFamily,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (next) colors.on else Color.Transparent))
    }
}

@Composable
private fun RowScope.RangeDay(date: LocalDate, column: Int, draft: RangeDraft, today: LocalDate, month: YearMonth, onTap: () -> Unit) {
    val colors = TimeboxTheme.colors
    val first = date == draft.start
    val last = date == draft.end
    val inRange = draft.end != null && date >= draft.start && date <= draft.end
    val future = date > today
    // The band runs between the endpoints and breaks at the edges of each week and of the month.
    val band = inRange && !(first && last)
    val roundStart = first || column == 0 || date.dayOfMonth == 1
    val roundEnd = last || column == 6 || date == month.atEndOfMonth()
    val bandShape = RoundedCornerShape(
        topStartPercent = if (roundStart) 50 else 0, bottomStartPercent = if (roundStart) 50 else 0,
        topEndPercent = if (roundEnd) 50 else 0, bottomEndPercent = if (roundEnd) 50 else 0,
    )
    val state = when {
        first -> ", first day"
        last -> ", last day"
        inRange -> ", in range"
        else -> ""
    }
    Box(
        Modifier.weight(1f).height(44.dp).then(if (band) Modifier.clip(bandShape).background(colors.high) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (first || last) colors.on else Color.Transparent)
                .clickable(enabled = !future, role = Role.Button, onClick = onTap)
                .semantics {
                    contentDescription = date.format(dayDescription) + (if (date == today) ", today" else "") + state
                    selected = first || last
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontFamily = TimeboxTheme.type.sectionTitle.fontFamily,
                fontWeight = if (first || last || date == today) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    first || last -> colors.bg
                    future -> colors.onVariant.copy(alpha = 0.35f)
                    date == today -> colors.planned
                    else -> colors.on
                },
            )
        }
    }
}

/** The month's days in Monday-first weeks, with nulls filling the days that belong to neighbouring months. */
internal fun rangeMonthCells(month: YearMonth): List<LocalDate?> {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List<LocalDate?>(lead) { null } + days
    return cells + List((7 - cells.size % 7) % 7) { null }
}
