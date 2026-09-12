package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.timebox.android.data.Day
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Preserve elapsed shares and offsets when the wall-clock grid cannot show a clock change. */
@Composable
fun ReportingDayActuals(day: Day, onSelectBlock: (Int) -> Unit) {
    Column {
        Text("Actual time - ${day.timezone}. This day includes a clock change; elapsed daily shares are shown below.")
        val format = DateTimeFormatter.ofPattern("MMM d HH:mm XXX").withZone(ZoneId.of(day.timezone))
        day.actualBlocks.forEach { projection ->
            val actual = projection.actualBlock
            TextButton(onClick = { day.blocks.find { it.actualBlockId == actual.id }?.let { onSelectBlock(it.id) } }) {
                Text("${actual.name ?: actual.taskTypeName} - ${projection.durationMinutes}m on this day\n${format.format(actual.startAt)} - ${actual.endAt?.let(format::format) ?: "Running"}")
            }
        }
    }
}
