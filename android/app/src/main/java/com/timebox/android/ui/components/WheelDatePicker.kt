package com.timebox.android.ui.components

import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Date-only draft: scrolling never commits, and Cancel leaves the caller unchanged. */
@Composable
fun WheelDatePicker(
    title: String,
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minimumDate: LocalDate? = null,
) {
    var epochDay by rememberSaveable(initialDate) { mutableLongStateOf(initialDate.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(epochDay)
    val valid = minimumDate == null || selected >= minimumDate
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val months = remember { Month.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }.toTypedArray() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = TimeboxTheme.type.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(selected.format(formatter), style = TimeboxTheme.type.body)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DateWheel("Day", selected.dayOfMonth, 1, selected.lengthOfMonth(), Modifier.weight(1f)) { epochDay = selected.withDayOfMonth(it).toEpochDay() }
                    DateWheel("Month", selected.monthValue, 1, 12, Modifier.weight(1f), months) { epochDay = selected.withMonth(it).toEpochDay() }
                    DateWheel("Year", selected.year, 1, 9999, Modifier.weight(1.2f), wrap = false) { epochDay = selected.withYear(it).toEpochDay() }
                }
                if (!valid) Text("Choose a date on or after ${minimumDate?.format(formatter)}.", color = TimeboxTheme.colors.error)
            }
        },
        confirmButton = { TextButton({ onConfirm(selected) }, enabled = valid) { Text("Set date") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DateWheel(label: String, value: Int, minimum: Int, maximum: Int, modifier: Modifier, labels: Array<String>? = null, wrap: Boolean = true, onChange: (Int) -> Unit) {
    val color = TimeboxTheme.colors.on.toArgb()
    val currentOnChange by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = TimeboxTheme.type.label)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(156.dp),
            factory = { context -> NumberPicker(context).apply {
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                contentDescription = label
            } },
            update = { picker ->
                picker.setOnValueChangedListener(null)
                picker.displayedValues = null
                picker.minValue = minimum
                picker.maxValue = maximum
                picker.displayedValues = labels
                picker.wrapSelectorWheel = wrap
                picker.value = value
                if (android.os.Build.VERSION.SDK_INT >= 29) picker.textColor = color
                picker.setOnValueChangedListener { _, _, next -> currentOnChange(next) }
            },
        )
    }
}
