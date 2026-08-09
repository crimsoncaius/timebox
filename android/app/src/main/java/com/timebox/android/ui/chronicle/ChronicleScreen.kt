package com.timebox.android.ui.chronicle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.YearMonth

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

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
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(bottom = TimeboxDimens.bottomInset),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
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

        monthGrid(state.monthStart).chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == YearMonth.from(state.monthStart)
                    val archived = state.archived[date]
                    DayCell(
                        date = date,
                        inMonth = inMonth,
                        isToday = date == state.today,
                        archived = archived != null,
                        windowLabel = archived?.windowLabel,
                        onClick = { onOpenDay(date) },
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
            .clickable(onClick = onClick)
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
