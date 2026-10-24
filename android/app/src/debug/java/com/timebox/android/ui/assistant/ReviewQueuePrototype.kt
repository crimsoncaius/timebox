package com.timebox.android.ui.assistant

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme

private data class ReviewSample(val id: Int, val name: String, val category: String, val start: String, val end: String, val said: String, val status: String = "", val reviewed: Boolean = false)
private fun reviewSamples() = listOf(
    ReviewSample(1, "Lunch", "Meal", "12:35", "Running", "I'm eating"),
    ReviewSample(2, "Project work", "Work", "09:10", "12:35", "I started working five minutes ago"),
    ReviewSample(3, "Morning walk", "Exercise", "08:30", "09:10", "I'm going for a walk", "Changed later"),
    ReviewSample(4, "Breakfast", "Meal", "08:00", "08:30", "I'm having breakfast", "Unsynced"),
)

/** Debug-only design round. All corrections and acknowledgements stay in memory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueuePrototype() {
    var samples by remember { mutableStateOf(reviewSamples()) }
    var reviewed by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf(false) }
    var scenario by remember { mutableStateOf("Today") }
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val context = LocalContext.current
    val visible = if (scenario == "Empty") emptyList() else samples.filter { it.reviewed == reviewed }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Assistant", Modifier.weight(1f), style = type.screenTitle)
            TextButton(onClick = { controls = !controls }) { Text(if (controls) "Hide study" else "Study") }
        }
        if (controls) Surface(color = colors.field) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Design study · sample data · changes stay here", style = type.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Today", "Empty", "Offline").forEach { value ->
                        FilterChip(scenario == value, { scenario = value }, label = { Text(value) })
                    }
                    TextButton(onClick = { samples = reviewSamples(); reviewed = false; scenario = "Today" }) { Text("Reset") }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Review", Modifier.semantics { heading() }, style = type.sectionTitle)
            Text("Already recorded. Check the activity and time.", style = type.body, color = colors.onVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!reviewed, { reviewed = false }, label = { Text("To review · ${samples.count { !it.reviewed }}") })
                FilterChip(reviewed, { reviewed = true }, label = { Text("Reviewed") })
            }
            if (scenario == "Offline") Text("Offline · Showing saved activity. Reconnect to save corrections or mark reviewed.", style = type.bodySmall)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            if (visible.isNotEmpty()) item {
                Text("Today · 24 October", style = type.label)
                Text("Times in Singapore", style = type.bodySmall, color = colors.onVariant)
                Spacer(Modifier.height(12.dp))
            }
            if (visible.isEmpty()) item {
                Column(Modifier.padding(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (reviewed) "Nothing reviewed yet" else "You're caught up", style = type.sectionTitle)
                    Text(if (reviewed) "Activities you mark reviewed will appear here." else "Your next recorded activity will appear here for a quick check.", style = type.body, color = colors.onVariant)
                }
            }
            items(visible, key = { it.id }) { row ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f).clickable { detail = row.id; editing = false }) {
                            Text(row.name, style = type.sectionTitle)
                            Text(row.category, style = type.bodySmall, color = colors.onVariant)
                        }
                        if (row.status.isNotEmpty()) Text(row.status, style = type.bodySmall, color = if (row.status == "Unsynced") colors.onVariant else MaterialTheme.colorScheme.primary)
                    }
                    Text("Started at ${row.start}", style = type.body)
                    Text("You said: “${row.said}”", style = type.body, color = colors.onVariant)
                    if (row.status == "Unsynced") Text("Saved on this device. Available to review after sync.", style = type.bodySmall, color = colors.onVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { detail = row.id; editing = false }) { Text("Details") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { detail = row.id; editing = true }, enabled = row.status != "Unsynced" && scenario != "Offline") { Text("Correct") }
                        if (!reviewed) TextButton(onClick = { samples = samples.map { if (it.id == row.id) it.copy(reviewed = true) else it } }, enabled = row.status != "Unsynced" && scenario != "Offline") { Text("Looks right") }
                    }
                }
                HorizontalDivider(color = colors.hairline)
            }
        }
    }
    samples.find { it.id == detail }?.let { row ->
        var name by remember(row.id) { mutableStateOf(row.name) }
        var start by remember(row.id) { mutableStateOf(row.start) }
        var category by remember(row.id) { mutableStateOf(row.category) }
        var choosingCategory by remember(row.id) { mutableStateOf(false) }
        var search by remember(row.id) { mutableStateOf("") }
        var error by remember(row.id) { mutableStateOf<String?>(null) }
        fun pickTime(value: String, onPicked: (String) -> Unit) {
            val parts = value.split(":").map { it.toInt() }
            TimePickerDialog(context, { _, hour, minute ->
                onPicked("%02d:%02d".format(hour, minute)); error = null
            }, parts[0], parts[1], true).show()
        }
        ModalBottomSheet(onDismissRequest = { detail = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (choosingCategory) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Activity category", Modifier.weight(1f), style = type.sectionTitle)
                        TextButton(onClick = { choosingCategory = false }) { Text("Back") }
                    }
                    OutlinedTextField(search, { search = it }, label = { Text("Search categories") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val matches = listOf("Work", "Meal", "Exercise", "Break", "Learning", "Travel", "Personal care", "Housework", "Social", "Sleep").filter { it.contains(search, ignoreCase = true) }
                    if (matches.isEmpty()) Text("No matching category. Try a different search.", style = type.body)
                    matches.forEach { option ->
                        Row(Modifier.fillMaxWidth().clickable { category = option; choosingCategory = false }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = category == option, onClick = { category = option; choosingCategory = false })
                            Text(option, style = type.body)
                        }
                    }
                } else {
                Text(if (editing) "Correct activity" else row.name, style = type.sectionTitle)
                if (editing) {
                    Text("You said: “${row.said}”", style = type.body, color = colors.onVariant)
                    OutlinedButton(onClick = { search = ""; choosingCategory = true }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Category", style = type.bodySmall)
                            Text(category, style = type.label)
                        }
                        Text("Change")
                    }
                    Text("Today · 24 October · Singapore time", style = type.bodySmall, color = colors.onVariant)
                        OutlinedButton(onClick = { pickTime(start) { start = it } }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Started at", style = type.bodySmall)
                                Text(start, style = type.sectionTitle)
                            }
                        }
                    OutlinedTextField(name, { name = it }, label = { Text("Activity name (optional)") }, modifier = Modifier.fillMaxWidth())
                    Text("Saving also marks this activity reviewed.", style = type.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        if (start > "12:40") error = "Choose a time at or before 12:40, the current time in this study."
                        else if (row.end != "Running" && start >= row.end) error = "Later activity ended this record at ${row.end}. Choose an earlier start."
                        else { samples = samples.map { if (it.id == row.id) it.copy(name = name.ifBlank { category }, category = category, start = start, reviewed = true) else it }; detail = null }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save correction") }
                } else {
                    Text("${row.category} · ${row.start} – ${row.end}", style = type.body)
                    Text("You said", style = type.label)
                    Text("“${row.said}”", style = type.body)
                    Text("Originally recorded", style = type.label)
                    val original = reviewSamples().first { it.id == row.id }
                    Text("${original.category} · Started at ${original.start}", style = type.body)
                    if (row.status == "Changed later") Text("A later activity changed this record. Check its current times before correcting.", style = type.body)
                    TextButton(onClick = { editing = true }, enabled = row.status != "Unsynced" && scenario != "Offline") { Text("Correct activity") }
                }
                TextButton(onClick = { detail = null }) { Text("Close") }
                }
            }
        }
    }
}
