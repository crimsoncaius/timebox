package com.timebox.android.ui.day

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
fun ActivityTimeField(label: String, value: ActivityTimeValue, zone: ZoneId, onChange: (ActivityTimeValue) -> Unit) {
    val context = LocalContext.current
    val local = runCatching { LocalDateTime.parse(value.local) }.getOrNull()
    val candidates = local?.let { ReportingTime.candidates(it, zone) }.orEmpty()
    Column {
        OutlinedTextField(value.local, { onChange(ActivityTimeValue(it)) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row {
            TextButton(onClick = {
                val selected = local ?: LocalDateTime.now(zone)
                DatePickerDialog(context, { _, year, month, day -> onChange(ActivityTimeValue(java.time.LocalDate.of(year, month + 1, day).atTime(selected.toLocalTime()).toString())) }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
            }) { Text("$label date") }
            TextButton(onClick = {
                val selected = local ?: LocalDateTime.now(zone)
                TimePickerDialog(context, { _, hour, minute -> onChange(ActivityTimeValue(selected.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toString())) }, selected.hour, selected.minute, true).show()
            }) { Text("$label time") }
        }
        if (candidates.size > 1) {
            Text("This time occurs twice. Choose an occurrence.")
            ReportingTime.Occurrence.entries.forEach { occurrence ->
                Row {
                    RadioButton(value.occurrence == occurrence, { onChange(value.copy(occurrence = occurrence, original = null)) })
                    TextButton(onClick = { onChange(value.copy(occurrence = occurrence, original = null)) }) { Text("${occurrence.name} · ${if (occurrence == ReportingTime.Occurrence.Earlier) candidates.first() else candidates.last()}") }
                }
            }
        } else if (local != null && candidates.isEmpty()) Text("That local time does not exist in $zone.")
    }
}
