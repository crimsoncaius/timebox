package com.timebox.android.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Day
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.theme.TimeboxTheme

internal fun parseBlockMinute(value: String): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return when {
        hour == 24 && minute == 0 -> 1440
        hour in 0..23 && minute in 0..59 -> hour * 60 + minute
        else -> null
    }
}

@Composable
internal fun BlockTimeFields(block: TimeBlock, day: Day?, saving: Boolean, onSave: (Int, Int) -> Unit) {
    var startText by remember(block.id, block.startMinute) { mutableStateOf(hhmm(block.startMinute)) }
    var endText by remember(block.id, block.endMinute) { mutableStateOf(hhmm(block.endMinute)) }
    val start = parseBlockMinute(startText)
    val end = parseBlockMinute(endText)
    val error = when {
        start == null || end == null -> "Enter times as HH:mm."
        end <= start -> "End must be after start."
        day != null && (start < day.visibleStart || end > day.visibleEnd) -> "Choose times within the visible day."
        day?.lane(block.lane)?.any { it.id != block.id && start < it.endMinute && end > it.startMinute } == true -> "This time overlaps another block."
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(startText, { startText = it }, enabled = !saving, singleLine = true,
                label = { Text("Start") }, modifier = Modifier.weight(1f))
            OutlinedTextField(endText, { endText = it }, enabled = !saving, singleLine = true,
                label = { Text("End") }, modifier = Modifier.weight(1f))
        }
        error?.let { Text(it, color = TimeboxTheme.colors.error) }
        TextButton(enabled = !saving && error == null && (start != block.startMinute || end != block.endMinute),
            onClick = { if (start != null && end != null && error == null) onSave(start, end) }) { Text("Save times") }
    }
}
