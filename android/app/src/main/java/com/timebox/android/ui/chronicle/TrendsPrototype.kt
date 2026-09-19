package com.timebox.android.ui.chronicle

import android.app.DatePickerDialog
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.BuildConfig
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter

// THROWAWAY #10: compare ranked bars, a proportion wheel, and a daily chart inside Chronicle.
// Launch with timebox://chronicle?variant=A (B or C). No writes or persisted prototype state.
private object TrendsStudy { var variant by mutableStateOf("A") }
fun configureTrendsPrototype(intent: Intent?) {
    if (BuildConfig.TRENDS_PROTOTYPE) {
        intent?.data?.getQueryParameter("variant")?.takeIf { it in listOf("A", "B", "C") }?.let { TrendsStudy.variant = it }
    }
}

private data class SampleTime(val date: LocalDate, val path: String, val minutes: Int)
private val sampleToday = LocalDate.of(2026, 9, 19)
private val sampleTimes = (0L..89L).flatMap { offset ->
    val day = sampleToday.minusDays(offset)
    val weekend = day.dayOfWeek.value > 5
    buildList {
        if (!weekend) {
            add(SampleTime(day, "Work / Coding", 140 + (offset % 5).toInt() * 25))
            add(SampleTime(day, "Work / Meetings", 30 + (offset % 3).toInt() * 30))
            add(SampleTime(day, "Work", 20))
        }
        add(SampleTime(day, "Learning / Reading", 35 + (offset % 4).toInt() * 10))
        if (offset % 2L == 0L) add(SampleTime(day, "Exercise", 45))
        add(SampleTime(day, "Personal", if (weekend) 140 else 50))
        if (offset % 3L == 0L) add(SampleTime(day, "unspecified", 25))
    }
}
private fun duration(minutes: Int) = "${minutes / 60}h ${minutes % 60}m"
private val dateFormat = DateTimeFormatter.ofPattern("d MMM")
private val shades = listOf(Color(0xff576862), Color(0xff84918b), Color(0xffa6afa6), Color(0xffbbb8a9), Color(0xffd3d5d0))

@Composable
internal fun TrendsPrototype() {
    val colors = TimeboxTheme.colors
    val context = LocalContext.current
    var preset by remember { mutableStateOf("Week") }
    var anchor by remember { mutableStateOf(sampleToday) }
    var customStart by remember { mutableStateOf(sampleToday.minusDays(13)) }
    var customEnd by remember { mutableStateOf(sampleToday) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var drill by remember { mutableStateOf<String?>(null) }
    val variant = TrendsStudy.variant
    val start = when (preset) {
        "Day" -> anchor
        "Week" -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        "Month" -> anchor.withDayOfMonth(1)
        else -> customStart
    }
    val end = when (preset) {
        "Day" -> start
        "Week" -> start.plusDays(6)
        "Month" -> start.plusMonths(1).minusDays(1)
        else -> customEnd
    }
    val rows = sampleTimes.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    val total = rows.sumOf { it.minutes }
    val groups = rows.groupBy { it.path.substringBefore(" / ") }.entries.sortedByDescending { it.value.sumOf { row -> row.minutes } }
    fun cycle(step: Int) { TrendsStudy.variant = listOf("A", "B", "C")[(listOf("A", "B", "C").indexOf(variant) + step + 3) % 3] }
    fun shift(step: Long) { anchor = when (preset) { "Day" -> anchor.plusDays(step); "Week" -> anchor.plusWeeks(step); else -> anchor.plusMonths(step) } }
    fun pickDate(initial: LocalDate, onPick: (LocalDate) -> Unit) {
        DatePickerDialog(context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("SAMPLE DATA · 19 SEP 2026", color = colors.onVariant, fontSize = 10.sp, letterSpacing = 1.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Day", "Week", "Month", "Custom").forEach { option ->
                    Box(Modifier.weight(1f).clip(TimeboxShapes.chip).background(if (preset == option) colors.on else colors.low).clickable { preset = option }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(option, color = if (preset == option) colors.bg else colors.on, fontSize = 13.sp)
                    }
                }
            }
            if (preset == "Custom") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { pickDate(customStart) { customStart = it; if (it > customEnd) customEnd = it } }) { Text("From ${start.format(dateFormat)}") }
                    TextButton(onClick = { pickDate(customEnd) { customEnd = it; if (it < customStart) customStart = it } }) { Text("To ${end.format(dateFormat)}") }
                }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { shift(-1) }) { Text("‹", fontSize = 26.sp) }
                Column(Modifier.weight(1f).clickable { anchor = sampleToday }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (start == end) start.format(dateFormat) else "${start.format(dateFormat)} – ${end.format(dateFormat)}", color = colors.on, fontSize = 17.sp)
                    Text("${start.year} · ${if (preset == "Day") "Today" else "This ${preset.lowercase()}"}", color = colors.onVariant, fontSize = 11.sp)
                }
                TextButton(onClick = { shift(1) }) { Text("›", fontSize = 26.sp) }
            }
            if (variant != "B" || total == 0) {
                Column {
                    Text(duration(total), color = colors.on, fontSize = 38.sp, fontWeight = FontWeight.Light)
                    Text("Recorded time · Asia/Singapore", color = colors.onVariant, fontSize = 12.sp)
                }
            }
            if (total == 0) Text("No recorded time in this range.", color = colors.onVariant)
            else {
                if (variant == "B") Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(194.dp)) {
                        var angle = -90f
                        groups.forEachIndexed { index, group ->
                            val sweep = group.value.sumOf { it.minutes } * 360f / total
                            drawArc(shades[index % shades.size], angle + 1, (sweep - 2).coerceAtLeast(0f), false, topLeft = Offset(12.dp.toPx(), 12.dp.toPx()), size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()), style = Stroke(19.dp.toPx()))
                            angle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(duration(total), color = colors.on, fontSize = 27.sp, fontWeight = FontWeight.Light)
                        Text("Recorded time", color = colors.onVariant, fontSize = 12.sp)
                    }
                }
                if (variant == "C") {
                    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
                    val maximum = days.maxOf { date -> rows.filter { it.date == date }.sumOf { it.minutes } }.coerceAtLeast(1)
                    Text("DAILY RECORDED TIME", color = colors.onVariant, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text("Scale: ${duration(maximum)}", color = colors.onVariant, fontSize = 11.sp)
                    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                        val column = size.width / days.size
                        days.forEachIndexed { i, day ->
                            var bottom = size.height
                            groups.forEachIndexed { index, group ->
                                val height = group.value.filter { it.date == day }.sumOf { it.minutes }.toFloat() / maximum * size.height
                                drawRect(shades[index % shades.size], Offset(i * column + 1, bottom - height), Size((column - 2).coerceAtLeast(1f), height))
                                bottom -= height
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(start.format(dateFormat), color = colors.onVariant, fontSize = 11.sp)
                        if (start != end) Text(end.format(dateFormat), color = colors.onVariant, fontSize = 11.sp)
                    }
                }
                Text("BY TASK TYPE", color = colors.onVariant, fontSize = 11.sp, letterSpacing = 1.sp)
                groups.forEachIndexed { index, group ->
                    val minutes = group.value.sumOf { it.minutes }
                    val children = group.value.groupBy { it.path }.entries.sortedByDescending { it.value.sumOf { r -> r.minutes } }
                    val hasChildren = children.any { it.key.contains(" / ") }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (variant != "A") Box(Modifier.size(9.dp).background(shades[index % shades.size]))
                            Text((if (hasChildren) if (group.key in expanded) "▾  " else "▸  " else "") + group.key,
                                modifier = Modifier.weight(1f).clickable { if (hasChildren) expanded = if (group.key in expanded) expanded - group.key else expanded + group.key else drill = group.key }.padding(vertical = 10.dp, horizontal = if (variant == "A") 0.dp else 8.dp), color = colors.on, fontSize = 15.sp)
                            Column(Modifier.clickable { drill = group.key }.padding(vertical = 6.dp), horizontalAlignment = Alignment.End) {
                                Text(duration(minutes), color = colors.on, fontSize = 15.sp)
                                Text("${"%.1f".format(minutes * 100.0 / total)}%", color = colors.onVariant, fontSize = 11.sp)
                            }
                        }
                        if (variant == "A") Box(Modifier.fillMaxWidth().height(6.dp).clip(TimeboxShapes.chip).background(colors.low)) {
                            Box(Modifier.fillMaxWidth(minutes.toFloat() / total).fillMaxHeight().background(shades[0]))
                        }
                        if (group.key in expanded && hasChildren) children.forEach { child ->
                            val childMinutes = child.value.sumOf { it.minutes }
                            Column(Modifier.fillMaxWidth().clickable { drill = child.key }.padding(start = 22.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (child.key == group.key) "Directly under ${group.key}" else child.key.substringAfter(" / "), color = colors.onVariant, fontSize = 13.sp)
                                    Text("${duration(childMinutes)} · ${"%.1f".format(childMinutes * 100.0 / total)}%", color = colors.onVariant, fontSize = 12.sp)
                                }
                                if (variant == "A") Box(Modifier.fillMaxWidth().height(4.dp).clip(TimeboxShapes.chip).background(colors.low)) {
                                    Box(Modifier.fillMaxWidth(childMinutes.toFloat() / total).fillMaxHeight().background(shades[1]))
                                }
                            }
                        }
                        if (variant != "A") HorizontalDivider(color = colors.low)
                    }
                }
            }
            Text("Prototype state: $variant · $preset · $start to $end · Actual only · expanded: ${expanded.joinToString().ifEmpty { "none" }}", color = colors.onVariant, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
        }
        Surface(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(), color = colors.on, shape = TimeboxShapes.chip, shadowElevation = 4.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { cycle(-1) }) { Text("←", color = colors.bg) }
                Text("$variant · ${when (variant) { "A" -> "Ranked bars"; "B" -> "Proportion wheel"; else -> "Daily rhythm" }}", color = colors.bg, fontSize = 13.sp)
                TextButton(onClick = { cycle(1) }) { Text("→", color = colors.bg) }
            }
        }
    }
    drill?.let { selected ->
        val contributing = rows.filter { it.path == selected || it.path.startsWith("$selected / ") }.groupBy { it.date }.toSortedMap()
        AlertDialog(onDismissRequest = { drill = null }, title = { Text(selected) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Contributing days · sample data")
                contributing.forEach { (date, records) -> Text("${date.format(dateFormat)}   ${duration(records.sumOf { it.minutes })}") }
                Text("Calendar highlighting is a later design decision.", fontSize = 12.sp)
            }
        }, confirmButton = { TextButton(onClick = { drill = null }) { Text("Done") } })
    }
}
