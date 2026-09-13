package com.timebox.android.ui.day

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timebox.android.data.*
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.theme.TimeboxDimens
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// Debug-only entry from MainActivity; all sample edits stay in memory.
internal val LocalTimelineExperiment = staticCompositionLocalOf<Dp?> { null }

private fun sampleBlocks(): List<TimeBlock> = Lane.entries.flatMap { lane ->
    listOf(600 to 605, 605 to 610, 610 to 615, 615 to 645, 660 to 665, 690 to 705,
        650 to 651, 651 to 653)
        .mapIndexed { index, (start, end) ->
            TimeBlock(lane.ordinal * 100 + index, lane, 1, "Work", null, null, null, null,
                startMinute = start, endMinute = end,
                name = listOf("Reply to message", "Make coffee", "Review notes", "Write proposal", "A five-minute block with a very long name", "Resize this block", "One-minute reply", "Two-minute check")[index])
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelinePrototype() {
    var blocks by remember { mutableStateOf(sampleBlocks()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var elapsed by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); elapsed += 1 }
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val running = TimeBlock(999, Lane.Actual, 1, "Work", null, null, null, null,
        startMinute = 720, endMinute = 720 + elapsed, name = "Current Activity")
    val displayed = blocks + running
    val day = Day(LocalDate.of(2026, 9, 13), 10, 14, false, displayed,
        timezone = "Asia/Singapore", today = LocalDate.of(2026, 9, 13), serverNowMinute = 720 + elapsed)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Short blocks · local prototype", Modifier.padding(horizontal = 16.dp))
        Text("Current Activity · ${elapsed}m elapsed", Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Zoom ${"%.1f".format(zoom)}×")
            TextButton(onClick = { blocks = sampleBlocks(); zoom = 1f; scope.launch { scroll.scrollTo(0) } }) { Text("Reset") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("Planned"); Text("Actual")
        }
        Box(Modifier.weight(1f).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        val factor = event.calculateZoom()
                        val anchor = event.calculateCentroid(useCurrent = false).y
                        val before = zoom
                        zoom = (zoom * factor).coerceIn(0.5f, 12f)
                        val target = ((scroll.value + anchor) * (zoom / before) - anchor).toInt()
                        scope.launch { scroll.scrollTo(target.coerceAtLeast(0)) }
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }.verticalScroll(scroll)) {
            CompositionLocalProvider(LocalTimelineExperiment provides TimeboxDimens.slotHeight * zoom) {
                DayTimeline(day, selected, null, onTapSlot = { _, _ -> },
                    onSelectBlock = { selected = it },
                    onCommitMove = { id, start, end ->
                        blocks = blocks.map { if (it.id == id) it.copy(startMinute = start, endMinute = end) else it }
                    })
            }
        }
    }
    displayed.firstOrNull { it.id == selected }?.let { block ->
        var startText by remember(block.id) { mutableStateOf(hhmm(block.startMinute)) }
        var endText by remember(block.id) { mutableStateOf(hhmm(block.endMinute)) }
        val start = prototypeMinute(startText)
        val end = prototypeMinute(endText)
        val error = when {
            start == null || end == null -> "Enter times as HH:mm."
            end <= start -> "End must be after start."
            start < day.visibleStart || end > day.visibleEnd -> "Keep this sample between 10:00 and 14:00."
            displayed.any { it.id != block.id && it.lane == block.lane && start < it.endMinute && end > it.startMinute } -> "This time overlaps another block."
            else -> null
        }
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(block.primaryIdentity(), style = MaterialTheme.typography.titleLarge)
                Text("${hhmm(block.startMinute)}–${hhmm(block.endMinute)} · ${block.durationMinutes} minutes")
                Text(block.taskTypeName)
                if (block.id != 999) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(startText, { startText = it }, label = { Text("Start") },
                            singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(endText, { endText = it }, label = { Text("End") },
                            singleLine = true, modifier = Modifier.weight(1f))
                    }
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                    else Text("${end!! - start!!} minutes")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { selected = null }) { Text("Cancel") }
                        Button(enabled = error == null, onClick = {
                            if (start != null && end != null && error == null) {
                                blocks = blocks.map { if (it.id == block.id) it.copy(startMinute = start, endMinute = end) else it }
                                selected = null
                            }
                        }) { Text("Save") }
                    }
                } else Text("Running · ${elapsed}m elapsed")
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun prototypeMinute(value: String): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}
