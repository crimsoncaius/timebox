package com.timebox.android.ui.battleplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.timebox.android.data.apiError
import com.timebox.android.ui.theme.TimeboxTheme
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

            },
            onFailure = { error = it.apiError.message },
        )
        loading = false
    }
    Text("Calendar", style = TimeboxTheme.type.sectionTitle)
    // A refresh after a routine edit keeps the shown month in place instead of collapsing it.
    if (loading && calendar == null) {
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
                        val foreground = if (count > 0) { if (colors.actual.luminance() > 0.5f) Color.Black else Color.White } else colors.on
                        Box(Modifier.size(44.dp)
                            .background(if (count > 0) colors.actual else Color.Transparent, CircleShape)
                            .then(if (date == data.today) Modifier.border(1.dp, colors.onVariant, CircleShape) else Modifier)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "$date${if (date == data.today) ", today" else ""}${if (count > 0) ", $count completed" else ""}${if (upcoming) ", upcoming" else ""}"
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
}
