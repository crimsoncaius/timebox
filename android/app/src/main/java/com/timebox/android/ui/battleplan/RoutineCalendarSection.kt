package com.timebox.android.ui.battleplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.data.RecurringTemplate
import com.timebox.android.data.RoutineCalendar
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.apiError
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.DayOfWeek
import java.util.Locale

@Composable
fun RoutineCalendarSection(
    template: RecurringTemplate,
    load: suspend (Int, YearMonth?) -> Result<RoutineCalendar>,
) {
    val colors = TimeboxTheme.colors
    var requested by rememberSaveable(template.id) { mutableStateOf<String?>(null) }
    var selected by rememberSaveable(template.id) { mutableStateOf<String?>(null) }
    var calendar by remember(template.id) { mutableStateOf<RoutineCalendar?>(null) }
    var error by remember(template.id) { mutableStateOf<String?>(null) }
    var loading by remember(template.id) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(template, requested, retry) {
        loading = true
        error = null
        load(template.id, requested?.let(YearMonth::parse)).fold(
            onSuccess = { result ->
                calendar = result
                if (selected == null || YearMonth.from(LocalDate.parse(selected)) != result.month) {
                    selected = (if (YearMonth.from(result.today) == result.month) result.today
                        else result.completed.minByOrNull { it.date }?.date
                            ?: result.upcoming.minOrNull() ?: result.month.atDay(1)).toString()
                }
            },
            onFailure = { error = it.apiError.message },
        )
        loading = false
    }
    Text("Calendar", style = TimeboxTheme.type.sectionTitle)
    if (loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    if (error != null) {
        Text(error!!, color = colors.error)
        TextButton({ retry++ }) { Text("Retry calendar") }
        return
    }
    val data = calendar ?: return
    if (!data.hasDates) {
        Text("No completed tasks yet", color = colors.onVariant)
        return
    }
    val month = data.month
    val completions = data.completed.groupBy { it.date }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton({ requested = month.minusMonths(1).toString() }, enabled = data.firstMonth?.let { month > it } == true,
            modifier = Modifier.semantics { contentDescription = "Previous month" }) { Text("‹") }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = TimeboxTheme.type.label)
        TextButton({ requested = month.plusMonths(1).toString() }, enabled = data.lastMonth?.let { month < it } ?: (month.year < 9998),
            modifier = Modifier.semantics { contentDescription = "Next month" }) { Text("›") }
    }
    Row {
        DayOfWeek.entries.forEach { day ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = colors.onVariant)
            }
        }
    }
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    repeat(rows) { row ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { column ->
                val number = row * 7 + column - offset + 1
                Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                    if (number in 1..month.lengthOfMonth()) {
                        val date = month.atDay(number)
                        val count = completions[date]?.size ?: 0
                        val upcoming = date in data.upcoming
                        val isSelected = selected == date.toString()
                        val foreground = if (count > 0) { if (colors.actual.luminance() > 0.5f) Color.Black else Color.White } else colors.on
                        Box(Modifier.size(44.dp)
                            .background(if (count > 0) colors.actual else if (isSelected) colors.selected else Color.Transparent, CircleShape)
                            .then(if (date == data.today || isSelected) Modifier.border(if (isSelected) 2.dp else 1.dp, colors.onVariant, CircleShape) else Modifier)
                            .clickable { selected = date.toString() }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "$date${if (date == data.today) ", today" else ""}${if (count > 0) ", $count completed" else ""}${if (upcoming) ", upcoming" else ""}${if (isSelected) ", selected" else ""}"
                            }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(number.toString(), color = foreground)
                                if (count > 1 || upcoming) Text(if (count > 1) "×$count" else "•", color = foreground, style = TimeboxTheme.type.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    Text(if (template.mode == RecurrenceMode.Quota) "Green circles: completed sessions" else "Green circles: completed · Dots: upcoming", color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
    selected?.let { value ->
        val date = LocalDate.parse(value)
        Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")), style = TimeboxTheme.type.label)
        completions[date].orEmpty().forEach { task ->
            Text("✓ ${task.title}", color = colors.onVariant)
        }
        if (date in data.upcoming) Text("${template.title} · Upcoming", color = colors.onVariant)
        if (completions[date].isNullOrEmpty() && date !in data.upcoming) Text("No tasks on this date", color = colors.onVariant)
    }
}
