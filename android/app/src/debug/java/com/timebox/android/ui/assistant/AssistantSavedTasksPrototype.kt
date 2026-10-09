package com.timebox.android.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Round two only: ordinary saved tasks; no live lookup or writes. */
@Composable
internal fun FocusedSavedTasks(card: AssistantTaskCard, open: (Int) -> Unit) {
    var all by rememberSaveable(card.id) { mutableStateOf(false) }
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    Surface(color = colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (card.partial) "At least ${card.count} matches · ${card.rows.size} loaded" else "${card.count} saved tasks", style = type.label)
                Text("Snapshot · ${card.readAt.atZone(card.zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))} · ${card.zone.id}",
                    style = type.bodySmall, color = colors.onVariant)
            }
            if (card.partial) {
                Text("Partial results. More matches and some details are not loaded. Ask for a narrower search.", style = type.body)
                if (card.limitations.contains("unavailable")) Text("One saved task was unavailable at read time.", style = type.bodySmall)
            }
            if (card.rows.isEmpty()) Text("No saved tasks matched this request.", style = type.body)
            (if (all) card.rows else card.rows.take(3)).forEach { row ->
                HorizontalDivider(color = colors.hairline)
                key(row.id) { FocusedSavedTask(row, open) }
            }
            if (card.rows.size > 3) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer tasks" else "Show all ${card.rows.size} loaded tasks") }
            if (card.hasMore) Text("More saved matches are available. Ask for the next page.", style = type.bodySmall, color = colors.onVariant)
        }
    }
}

@Composable
private fun FocusedSavedTask(row: AssistantTaskRow, open: (Int) -> Unit) {
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    val type = TimeboxTheme.type
    val colors = TimeboxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (row.availability != "available") {
            PolishTaskTitle("Task #${row.id}")
            Text("Unavailable at read time. Its details could not be verified.", style = type.body)
        } else {
            val fields = row.values
            PolishTaskTitle(row.title)
            val project = fields["project"]?.takeUnless { it == JsonNull }?.jsonObject?.text("name") ?: "No project"
            Text(project, style = type.bodySmall, color = colors.onVariant)
            val deadline = fields["deadline"]?.takeUnless { it == JsonNull }?.jsonObject?.text("date")
            Text(if (deadline == null) "No deadline" else "Due ${LocalDate.parse(deadline).format(DateTimeFormatter.ofPattern("EEE, d MMM"))}", style = type.body)
            val blocked = fields.flag("blocked")
            Text(if (blocked) "Blocked · ${fields.text("blocking_reason")}" else if (fields.flag("ready_to_plan")) "Ready to Plan" else "Not ready to plan",
                style = type.bodySmall, color = colors.onVariant)
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text("Saved details · Task #${row.id}", style = type.label)
                Text("Status: Incomplete", style = type.bodySmall)
                Text("Task Type: ${fields.getValue("task_type").jsonObject.text("path")}", style = type.bodySmall)
                listOf("urgency", "importance", "relevance").forEach { field ->
                    fields[field]?.takeUnless { it == JsonNull }?.let { Text("${taskFieldLabel(field)}: ${taskValue(it)}", style = type.bodySmall) }
                }
                Text("Blocked: ${if (blocked) "Yes" else "No"}", style = type.bodySmall)
                Text("Ready to Plan: ${if (fields.flag("ready_to_plan")) "Yes" else "No"}", style = type.bodySmall)
                Text("${fields.integer("subtasks_count")} saved Subtasks", style = type.bodySmall)
                fields.getValue("subtasks").jsonArray.forEach { item ->
                    val subtask = item.jsonObject
                    Text("${subtask.text("title")} · ${if (subtask.flag("checked")) "Checked" else "Unchecked"}", style = type.bodySmall)
                }
                Text("Planned dates: ${taskValue(fields["planned_dates"])}", style = type.bodySmall)
                Text("No reminder set", style = type.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { open(row.id) }, modifier = Modifier.semantics { contentDescription = "Open current Task ${row.title}, ID ${row.id}" }) { Text("Open Task") }
            if (row.availability == "available") TextButton(onClick = { expanded = !expanded },
                modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
                Text(if (expanded) "Hide details" else "Saved details")
            }
        }
    }
}

internal fun polishSavedTasks(sample: String): AssistantTaskCard {
    val partial = sample == "partial"
    fun row(id: Int, title: String, project: String, deadline: String, ready: Boolean, blocked: Boolean): AssistantTaskRow =
        AssistantTaskRow.parse(Json.parseToJsonElement("""{
          "id":$id,"kind":"ordinary","availability":"available","title":"$title",
          "lifecycle":"incomplete","relevance":"current","project":{"id":$id,"name":"$project"},
          "task_type":{"id":9,"path":"Work/Clients"},"ready_to_plan":$ready,"blocked":$blocked,
          "blocking_reason":${if (blocked) "\"Waiting for revised pricing\"" else "null"},
          "urgency":null,"importance":null,"deadline":{"kind":"date","date":"$deadline"},"reminder_at":null,
          "subtasks":[{"id":${id + 100},"title":"Check the pricing appendix","checked":false}],
          "subtasks_count":1,"planned_dates":[],"planned_dates_complete":true
        }""").jsonObject)
    val rows = if (sample == "empty") emptyList() else listOf(
        row(42, if (partial) "Send the client proposal with revised scope, pricing and delivery milestones" else "Send the client proposal", "Client launch", "2026-10-09", true, false),
        row(43, "Send the client proposal", "Website relaunch", "2026-10-12", false, true),
        row(44, "Review the launch checklist", "Client launch", "2026-10-10", false, false),
    ) + if (partial) listOf(AssistantTaskRow(45, "ordinary", "unavailable", JsonObject(emptyMap()))) else emptyList()
    val readAt = Instant.parse("2026-10-08T06:30:00Z")
    return AssistantTaskCard("study-saved-$sample", readAt, ZoneId.of("Asia/Singapore"), rows,
        if (partial) 7 else rows.size, if (partial) "at_least" else "exact", partial,
        if (partial) listOf("payload_limit", "unavailable") else emptyList(), readAt, partial)
}
