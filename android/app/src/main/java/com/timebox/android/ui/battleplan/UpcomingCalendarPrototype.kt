package com.timebox.android.ui.battleplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// THROWAWAY #229: month, fortnight and agenda in the routine host, selected by ?variant=A/B/C.
// Fixed sample: today Sep 19, 2026; Mondays from Sep 21, or three sessions per week.
private val sampleToday = LocalDate.of(2026, 9, 19)
private val firstUpcoming = LocalDate.of(2026, 9, 21)
private val dateLabel = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun UpcomingCalendarPrototype(variant: String, mode: String, navigate: (String, String) -> Unit) {
    val colors = TimeboxTheme.colors
    val quota = mode == "quota"
    val index = listOf("A", "B", "C").indexOf(variant).coerceAtLeast(0)
    var offset by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(firstUpcoming.toString()) }
    fun cycle(delta: Int) = navigate(listOf("A", "B", "C")[(index + delta + 3) % 3], mode)
    Column(Modifier.fillMaxSize().background(colors.sheet).statusBarsPadding().navigationBarsPadding()
        .onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) { cycle(1); true }
            else if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { cycle(-1); true } else false
        }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recurring routine", Modifier.weight(1f), color = colors.onVariant)
            TextButton({ navigate(variant, if (quota) "scheduled" else "quota") }) { Text(if (quota) "Try scheduled" else "Try quota") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (quota) "Practice sessions" else "Weekly review", style = TimeboxTheme.type.screenTitle)
            Text(if (quota) "Make room for three focused practice sessions." else "Look back over the week and choose what deserves attention next.", color = colors.onVariant)
            HorizontalDivider(color = colors.hairline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Repeat", color = colors.onVariant)
                Text(if (quota) "3 times per week" else "Every Monday")
            }
            if (!quota) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Pre-planning", color = colors.onVariant); Text("Not set") }
            HorizontalDivider(color = colors.hairline)
            Text("Current work", style = TimeboxTheme.type.label)
            Text(if (quota) "This week · 1 of 3 complete" else "Mon 14 Sep · Completed", color = colors.onVariant)
            HorizontalDivider(color = colors.hairline)
            Text("Upcoming", style = TimeboxTheme.type.sectionTitle)
            when (index) {
                0 -> {
                    val month = YearMonth.of(2026, 9).plusMonths(offset.toLong())
                    PeriodNavigation(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), { offset-- }, { offset++ })
                    CalendarGrid(month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong()),
                        ((month.lengthOfMonth() + month.atDay(1).dayOfWeek.value - 2) / 7 + 1), month, quota, selected) { selected = it.toString() }
                }
                1 -> {
                    val start = LocalDate.of(2026, 9, 14).plusWeeks(offset * 2L)
                    PeriodNavigation("${start.format(dateLabel)} – ${start.plusDays(13).format(dateLabel)}", { offset-- }, { offset++ })
                    CalendarGrid(start, 2, null, quota, selected) { selected = it.toString() }
                    Text(if (quota) "Choose any days within each shaded week." else "A short look ahead, one fortnight at a time.", color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
                }
                else -> {
                    Text(if (quota) "Next five weeks" else "Next five occurrences", color = colors.onVariant)
                    repeat(5) { i ->
                        val date = firstUpcoming.plusWeeks(i.toLong())
                        Surface(shape = RoundedCornerShape(12.dp), color = if (selected == date.toString()) colors.selected else colors.card,
                            modifier = Modifier.fillMaxWidth().clickable { selected = date.toString() }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.width(58.dp)) { Text(date.dayOfMonth.toString(), style = TimeboxTheme.type.sectionTitle); Text(date.format(DateTimeFormatter.ofPattern("MMM")), style = TimeboxTheme.type.bodySmall) }
                                Column { Text(if (quota) "${date.format(dateLabel)} – ${date.plusDays(6).format(dateLabel)}" else date.format(DateTimeFormatter.ofPattern("EEEE")))
                                    Text(if (quota) "3 sessions · Any days" else if (i == 0) "In 2 days · Not planned" else "In ${2 + i * 7} days · Not planned", color = colors.onVariant, style = TimeboxTheme.type.bodySmall) }
                            }
                        }
                    }
                }
            }
            val date = LocalDate.parse(selected)
            val eligible = !date.isBefore(firstUpcoming)
            val week = date.minusDays((date.dayOfWeek.value - 1).toLong())
            Surface(color = colors.card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (quota && eligible) "${week.format(dateLabel)} – ${week.plusDays(6).format(dateLabel)}" else date.format(dateLabel), style = TimeboxTheme.type.label)
                    Text(if (quota && eligible) "3 sessions during this week. No fixed days." else if (eligible && date.dayOfWeek.value == 1) "Weekly review · Not planned" else "No upcoming occurrence", color = colors.onVariant)
                }
            }
            Text(if (quota) "Shading marks a quota period, not chosen task dates." else "Filled dates repeat. Outline marks today. Tap a date to inspect.", color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
            Text("SAMPLE · Today 19 Sep 2026\n${if (quota) "Weekly quota: 3" else "Scheduled: Mondays"} · From 21 Sep · No end\nSelected: $selected · Page: $offset · No changes saved", color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
            Spacer(Modifier.height(12.dp))
        }
        Surface(color = colors.inverseSurface, shape = RoundedCornerShape(28.dp), modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ cycle(-1) }) { Text("←", color = colors.inverseOnSurface) }
                Text("$variant · ${listOf("Month", "Two weeks", "Agenda")[index]}", color = colors.inverseOnSurface)
                TextButton({ cycle(1) }) { Text("→", color = colors.inverseOnSurface) }
            }
        }
    }
}

@Composable
private fun PeriodNavigation(label: String, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(previous) { Text("‹") }
        Text(label, Modifier.weight(1f), style = TimeboxTheme.type.label)
        TextButton(next) { Text("›") }
    }
}

@Composable
private fun CalendarGrid(start: LocalDate, weeks: Int, month: YearMonth?, quota: Boolean, selected: String, select: (LocalDate) -> Unit) {
    val colors = TimeboxTheme.colors
    Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, color = colors.onVariant, style = TimeboxTheme.type.bodySmall) } } }
    repeat(weeks) { row ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { col ->
                val date = start.plusDays((row * 7 + col).toLong())
                val inMonth = month == null || YearMonth.from(date) == month
                val future = !date.isBefore(firstUpcoming)
                val occurrence = inMonth && future && !quota && date.dayOfWeek.value == 1
                Box(Modifier.weight(1f).height(44.dp).background(if (inMonth && quota && future) colors.primaryContainer.copy(alpha = 0.4f) else colors.sheet), contentAlignment = Alignment.Center) {
                    if (inMonth) Box(Modifier.size(38.dp).then(if (date == sampleToday || selected == date.toString()) Modifier.border(1.dp, colors.onVariant, CircleShape) else Modifier)
                        .background(if (occurrence) colors.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                        .clickable { select(date) }, contentAlignment = Alignment.Center) {
                        Text(date.dayOfMonth.toString(), color = if (occurrence) colors.onPrimary else colors.on)
                    }
                }
            }
        }
    }
}
