package com.timebox.android.ui.day

import com.timebox.android.ui.components.HelperText
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxShapes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.showMondayDatePicker
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalContext
import com.timebox.android.data.ReportingTime
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class ActivityTimeValue(val local: String, val occurrence: ReportingTime.Occurrence? = null, val original: Instant? = null) {
    fun resolve(zone: ZoneId): Instant = original ?: ReportingTime.resolve(LocalDateTime.parse(local), zone, occurrence)
    companion object {
        fun from(instant: Instant, zone: ZoneId): ActivityTimeValue {
            val local = instant.atZone(zone).toLocalDateTime().withSecond(0).withNano(0)
            val candidates = ReportingTime.candidates(local, zone)
            return ActivityTimeValue(local.toString(), if (candidates.size > 1) {
                if (instant >= candidates.last()) ReportingTime.Occurrence.Later else ReportingTime.Occurrence.Earlier
            } else null, instant)
        }
    }
}

@Composable
fun ActivityTimeField(label: String, value: ActivityTimeValue, zone: ZoneId, compact: Boolean = false, enabled: Boolean = true, onChange: (ActivityTimeValue) -> Unit) {
    val context = LocalContext.current
    val local = runCatching { LocalDateTime.parse(value.local) }.getOrNull()
    val candidates = local?.let { ReportingTime.candidates(it, zone) }.orEmpty()
    Column {
        val chooseDate = {
            val selected = local ?: LocalDateTime.now(zone)
            showMondayDatePicker(context, selected.toLocalDate()) { date ->
                onChange(ActivityTimeValue(date.atTime(selected.toLocalTime()).toString()))
            }
        }
        val chooseTime = {
            val selected = local ?: LocalDateTime.now(zone)
            TimePickerDialog(context, { _, hour, minute -> onChange(ActivityTimeValue(selected.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toString())) }, selected.hour, selected.minute, true).show()
        }
        if (compact) {
            val colors = TimeboxTheme.colors
            // One outlined field: the caption carries the date, the large figure the time.
            Column(
                Modifier.fillMaxWidth().clip(TimeboxShapes.field).border(1.dp, colors.outlineVariant, TimeboxShapes.field)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    "$label · " + (local?.format(DateTimeFormatter.ofPattern("d MMM")) ?: "Choose date"),
                    Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button) { chooseDate() }
                        .semantics { contentDescription = "$label date" }.padding(horizontal = 13.dp, vertical = 4.dp),
                    style = TimeboxTheme.type.bodySmall, color = colors.outline, maxLines = 1,
                )
                Text(
                    local?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Choose time",
                    Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button) { chooseTime() }
                        .semantics { contentDescription = "$label time" }.heightIn(min = 40.dp).padding(horizontal = 13.dp, vertical = 4.dp),
                    style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, fontWeight = FontWeight.Light),
                    color = if (enabled) colors.on else colors.onVariant,
                )
            }
        } else {
            OutlinedTextField(value.local, { onChange(ActivityTimeValue(it)) }, enabled = enabled, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row {
                TextButton(enabled = enabled, onClick = { chooseDate() }) { Text("$label date") }
                TextButton(enabled = enabled, onClick = { chooseTime() }) { Text("$label time") }
            }
        }
        if (candidates.size > 1) {
            HelperText("This time occurs twice. Choose an occurrence.")
            ReportingTime.Occurrence.entries.forEach { occurrence ->
                Row {
                    RadioButton(value.occurrence == occurrence, enabled = enabled, onClick = { onChange(value.copy(occurrence = occurrence, original = null)) })
                    TextButton(enabled = enabled, onClick = { onChange(value.copy(occurrence = occurrence, original = null)) }) { Text("${occurrence.name} · ${if (occurrence == ReportingTime.Occurrence.Earlier) candidates.first() else candidates.last()}") }
                }
            }
        } else if (local != null && candidates.isEmpty()) HelperText("That local time does not exist in $zone.", error = true)
    }
}
