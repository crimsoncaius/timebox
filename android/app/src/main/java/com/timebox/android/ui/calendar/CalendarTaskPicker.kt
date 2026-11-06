package com.timebox.android.ui.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timebox.android.data.BattleTask
import com.timebox.android.data.TaskStatus
import com.timebox.android.ui.theme.TimeboxTheme

internal fun BattleTask.calendarContext(): String = project?.name ?: recurringTemplateTitle ?: "No Project"

internal fun calendarTaskMatches(tasks: List<BattleTask>, query: String): List<BattleTask> {
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return tasks.filter { task -> task.status != TaskStatus.Completed && task.recurrenceKind != "quota_parent" &&
        (task.parentId == null || task.recurrenceKind == "quota_session") && words.all {
            task.title.contains(it, ignoreCase = true) || task.calendarContext().contains(it, ignoreCase = true)
        }
    }
}

@Composable
internal fun CalendarTaskPicker(tasks: List<BattleTask>, selected: Int?, enabled: Boolean, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val chosen = tasks.find { it.id == selected }
    TextButton(enabled = enabled, onClick = { open = true }) {
        Text("Battle Plan Task: " + (chosen?.let { "${it.title} · ${it.calendarContext()}" } ?: if (selected == null) "No linked Task" else "Task $selected"))
    }
    if (open) {
        var query by remember { mutableStateOf("") }
        val matches = remember(tasks, query) { calendarTaskMatches(tasks, query) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text("Link a Task") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search tasks or Projects") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onSelect(null); open = false }) { Text("No linked Task") }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(matches, key = { it.id }) { task ->
                        TextButton(onClick = { onSelect(task.id); open = false }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(task.title, style = TimeboxTheme.type.body)
                                Text(task.calendarContext(), style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                            }
                        }
                    }
                    if (matches.isEmpty()) item { Text("No matching tasks", style = TimeboxTheme.type.bodySmall, modifier = Modifier.padding(12.dp)) }
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
    }
}
