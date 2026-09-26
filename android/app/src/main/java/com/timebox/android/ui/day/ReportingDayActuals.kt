package com.timebox.android.ui.day

import com.timebox.android.ui.components.HelperText
import com.timebox.android.data.activityIdentityText
import com.timebox.android.ui.elapsedDuration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Day
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Preserve elapsed shares and offsets when the wall-clock grid cannot show a clock change. */
@Composable
fun ReportingDayActuals(day: Day, onSelectBlock: (Int) -> Unit) {
    val colors = TimeboxTheme.colors
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HelperText("Actual time · ${day.timezone}. This day includes a clock change; elapsed daily shares are shown below.")
        val format = DateTimeFormatter.ofPattern("d MMM, HH:mm O").withZone(ZoneId.of(day.timezone))
        day.actualBlocks.forEach { projection ->
            val actual = projection.actualBlock
            Column(
                Modifier.fillMaxWidth()
                    .clip(TimeboxShapes.cell)
                    .background(colors.actualSurface)
                    .clickable { day.blocks.find { it.actualBlockId == actual.id }?.let { onSelectBlock(it.id) } }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("${activityIdentityText(actual.name, actual.task?.title, actual.taskTypeName)} · ${elapsedDuration(projection.durationMinutes.toLong())} on this day",
                    color = colors.on, style = TimeboxTheme.type.label)
                Text("${format.format(actual.startAt)} – ${actual.endAt?.let(format::format) ?: "Running"}",
                    color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
            }
        }
    }
}
