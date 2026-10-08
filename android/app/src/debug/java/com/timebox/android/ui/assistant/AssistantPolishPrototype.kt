package com.timebox.android.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Progressive design experiment. All mutations are local; no repository or transport. */
@Composable
fun AssistantPolishPrototype(initialLayout: String, initialScenario: String) {
    var layout by rememberSaveable { mutableStateOf(initialLayout) }
    var scenario by rememberSaveable { mutableStateOf(initialScenario.takeIf { it in listOf("saved", "create", "edit", "complete", "batch") } ?: "saved") }
    var stage by rememberSaveable(scenario) { mutableStateOf("pending") }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var detailId by rememberSaveable { mutableStateOf<Int?>(null) }
    var longDescription by rememberSaveable { mutableStateOf(false) }
    val proposal = remember(scenario) { polishProposal(scenario) }
    val result = remember(scenario, stage) { polishResult(proposal, scenario, stage) }
    val savedTasks = remember(stage) { polishSavedTasks(stage) }
    val title = if (scenario == "saved") savedTasks.rows.find { it.id == detailId }?.title ?: "Sample Task"
        else if (scenario == "batch" && detailId == 47) "Prepare handover notes" else proposal.targets.single().title
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val confirm = { stage = "applied" }
    val open: (Int) -> Unit = { detailId = it }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Assistant", Modifier.weight(1f).semantics { heading() }, style = type.screenTitle)
            TextButton(onClick = { showControls = !showControls }) { Text(if (showControls) "Hide study" else "Study") }
        }
        if (showControls) {
            Surface(color = colors.field) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Design study · sample data · no changes saved", style = type.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("current" to "Production", "focused" to "Prototype").forEach { (value, label) ->
                            FilterChip(selected = layout == value, onClick = { layout = value }, label = { Text(label) })
                        }
                        TextButton(onClick = { stage = "pending" }) { Text("Reset") }
                    }
                    ScrollableTabRow(selectedTabIndex = listOf("batch", "saved", "create", "edit", "complete").indexOf(scenario).coerceAtLeast(0), edgePadding = 0.dp, containerColor = colors.field) {
                        listOf("batch" to "Multiple tasks", "saved" to "Saved Tasks", "create" to "Create", "edit" to "Edit", "complete" to "Complete").forEach { (value, label) ->
                            Tab(selected = scenario == value, onClick = { scenario = value; stage = "pending" }, text = { Text(label) })
                        }
                    }
                    if (scenario == "saved") Row {
                        TextButton(onClick = { stage = "pending" }) { Text("Matches") }
                        TextButton(onClick = { stage = "partial" }) { Text("Long / partial") }
                        TextButton(onClick = { stage = "empty" }) { Text("No matches") }
                    } else Row {
                        TextButton(onClick = { stage = "stale" }) { Text("Changed elsewhere") }
                        TextButton(onClick = { stage = "unknown" }) { Text("Lost reply") }
                    }
                    if (scenario == "batch") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = longDescription, onClick = { longDescription = !longDescription; stage = "pending" }, label = { Text("Long description") })
                        TextButton(onClick = { stage = "description-error" }) { Text("Review load fails") }
                    }
                }
            }
        }
        HorizontalDivider(color = colors.hairline)
        // Preserve the local outcome across layout switches; reset scroll for a fair starting point.
        key(layout, scenario, stage, longDescription) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(Modifier.align(Alignment.End).padding(start = 28.dp), color = colors.card, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.hairline)) {
                    Text(when (scenario) {
                        "batch" -> "Move the proposal to Friday, mark it ready to plan and update its description with the revised scope. Also create handover notes, due Saturday."
                        "saved" -> "Which client tasks are still open?"
                        "create" -> "Create a task to send the client proposal, due tomorrow."
                        "edit" -> "Move the client proposal deadline to tomorrow and mark it ready to plan."
                        else -> "I've finished the client proposal. Mark it complete."
                    }, Modifier.padding(14.dp), style = type.body)
                }
                Text("Assistant", style = type.bodySmall, color = colors.onVariant)
                if (scenario == "batch") {
                    BatchReviewStudy(layout, stage, { stage = it }, open, longDescription)
                } else if (scenario == "saved") {
                    if (layout == "current") TaskReadCard(savedTasks, open) else FocusedSavedTasks(savedTasks, open)
                } else if (layout == "current") {
                    TaskChangeCard(proposal, TaskChangeState(stage, sourceCompleted = true, result = if (stage == "applied" || stage == "undone") result else null),
                        open, { proposal }, { if (stage == "unknown") stage = "applied" }, { stage = "cancelled" }, { stage = "pending" }, onConfirm = confirm)
                    if (stage == "applied" && scenario == "complete") TextButton(onClick = { stage = "undone" }) { Text("Simulate completion Undo") }
                } else {
                    FocusedChangeCard(proposal, scenario, stage, confirm, { stage = "cancelled" }, { stage = "pending" }, { stage = "applied" }, { stage = "undone" }, { detailId = 42 })
                }
            }
        }
        HorizontalDivider(color = colors.hairline)
        Text("Sample conversation · use the study controls to explore", Modifier.fillMaxWidth().padding(16.dp), style = type.bodySmall, color = colors.onVariant)
    }
    if (detailId != null) AlertDialog(onDismissRequest = { detailId = null }, title = { Text(title) },
        text = { Text("Sample Task #$detailId. In the live app, Open Task opens its current details. This study has no saved task behind it.") },
        confirmButton = { TextButton(onClick = { detailId = null }) { Text("Back to review") } })
}

@Composable
internal fun PolishTaskTitle(title: String) {
    Text(title, Modifier.semantics { heading() },
        style = TimeboxTheme.type.screenTitle.copy(fontSize = 22.sp, lineHeight = 28.sp,
            fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em))
}

@Composable
private fun FocusedChangeCard(p: TaskChangeProposal, scenario: String, stage: String, confirm: () -> Unit,
    dismiss: () -> Unit, refresh: () -> Unit, check: () -> Unit, undo: () -> Unit, open: () -> Unit) {
    var details by rememberSaveable(p.id, stage) { mutableStateOf(false) }
    val type = TimeboxTheme.type
    val colors = TimeboxTheme.colors
    val target = p.targets.single()
    val saved = stage in listOf("applied", "undone")
    Surface(color = colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PolishTaskTitle(target.title)
                Text(when (stage) {
                "applied" -> when (scenario) { "create" -> "Task created"; "edit" -> "Task updated"; else -> "Task completed" }
                "undone" -> "Completion undone"
                "stale" -> "Review needs refreshing"
                "unknown" -> "Checking what was saved"
                "cancelled" -> "Proposal dismissed"
                else -> when (scenario) { "create" -> "Create task"; "edit" -> "Update task"; else -> "Complete task" }
                }, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = type.body, color = colors.onVariant)
                if (!saved && stage != "cancelled") Text("${if (scenario == "create") "New task" else "Task #42"} · Client launch", style = type.bodySmall, color = colors.onVariant)
            }
            when {
                saved -> {
                    Text(when {
                        stage == "undone" -> "Task reopened. Tracking stays stopped."
                        scenario == "create" -> "Due Fri, 9 Oct · Ready to Plan"
                        scenario == "edit" -> "Deadline moved to Fri, 9 Oct. Ready to Plan."
                        else -> "Tracking stopped. 1 future Planned Block removed."
                    }, style = type.body)
                    Text("${if (stage == "undone") "Undo saved" else "Saved"} in this sample", style = type.bodySmall, color = colors.onVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = open) { Text("Open Task") }
                        if (scenario == "complete" && stage == "applied") OutlinedButton(onClick = undo) { Text("Undo completion") }
                    }
                    if (scenario == "complete" && stage == "applied") Text("Undo reopens the task. Tracking will stay stopped.", style = type.bodySmall, color = colors.onVariant)
                    TextButton(onClick = { details = !details }) { Text(if (details) "Hide saved details" else "Saved details") }
                    if (details) {
                        Text("Task #42 · Client launch", style = type.bodySmall)
                        Text(if (scenario == "complete") "Removed plan: Fri, 9 Oct, 09:00–10:00. No linked Actual Blocks to detach. Subtask checks were unchanged." else "Task Type: Work/Clients\nReady to Plan: Yes\nDeadline: Fri, 9 Oct 2026", style = type.bodySmall)
                    }
                }
                stage == "cancelled" -> Text("No task changes were saved.", style = type.body)
                stage == "unknown" -> {
                    Text("The reply was lost. Your changes may already be saved.", style = type.body)
                    Text("Check the result before trying again.", style = type.bodySmall)
                    Button(onClick = check, modifier = Modifier.fillMaxWidth()) { Text("Check result") }
                }
                else -> {
                    HorizontalDivider(color = colors.hairline)
                    if (scenario == "complete") {
                        Text("Also changes", style = type.bodySmall, color = colors.onVariant)
                        Text("Stops the running Actual Block", style = type.body)
                        Text("Client proposal · started at 14:00", style = type.bodySmall, color = colors.onVariant)
                        Text("Removes 1 future Planned Block", style = type.body)
                        Text("Fri, 9 Oct · 09:00–10:00 · Work/Clients", style = type.bodySmall, color = colors.onVariant)
                        Text("Clears Ready to Plan. Subtask checks stay unchanged.", style = type.bodySmall)
                    } else {
                        ChangeLine("Deadline", if (scenario == "edit") "Mon, 12 Oct" else null, "Fri, 9 Oct")
                        ChangeLine("Ready to Plan", if (scenario == "edit") "No" else null, "Yes")
                        if (scenario == "create") ChangeLine("Task Type", null, "Work/Clients")
                    }
                    TextButton(onClick = { details = !details }) { Text(if (details) "Hide review details" else "Review details") }
                    if (details) {
                        Text("Review expires ${p.expiresAt.atZone(p.zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))} · ${p.zone.id}", style = type.bodySmall)
                        if (scenario == "complete") Text("Running Actual #71. Planned Block #81. No Actual Blocks to detach from the removed plan.", style = type.bodySmall)
                        if (scenario != "create") TextButton(onClick = open) { Text("Open current Task #42") }
                    }
                    if (stage == "stale") {
                        Text("This task changed after the preview. Refresh and review before confirming.", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = type.body)
                        Button(onClick = refresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh review") }
                    } else {
                        Text("Nothing saved yet", style = type.bodySmall, color = colors.onVariant)
                        Button(onClick = confirm, modifier = Modifier.fillMaxWidth()) {
                            Text(when (scenario) { "create" -> "Confirm creation"; "edit" -> "Confirm changes"; else -> "Confirm completion" })
                        }
                    }
                    TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
internal fun ChangeLine(label: String, before: String?, after: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        Text(if (before == null) after else "$before → $after", style = TimeboxTheme.type.body)
    }
}

private fun obj(value: String) = Json.parseToJsonElement(value).jsonObject

internal fun polishProposal(scenario: String): TaskChangeProposal {
    val title = "Send the client proposal"
    val before = when (scenario) {
        "create" -> null
        "edit" -> obj("""{"deadline":{"kind":"date","date":"2026-10-12"},"ready_to_plan":false}""")
        else -> obj("""{"status":"incomplete","ready_to_plan":true}""")
    }
    val after = when (scenario) {
        "create" -> obj("""{"title":"$title","project_id":8,"task_type_id":9,"deadline":{"kind":"date","date":"2026-10-09"},"ready_to_plan":true}""")
        "edit" -> obj("""{"deadline":{"kind":"date","date":"2026-10-09"},"ready_to_plan":true}""")
        else -> obj("""{"status":"completed","ready_to_plan":false}""")
    }
    return TaskChangeProposal("study-$scenario", "study-operation", "study-conversation", "study-run", "", Instant.now().plusSeconds(1800), ZoneId.of("Asia/Singapore"),
        listOf(TaskReviewTarget(if (scenario == "create") obj("""{"ref":"proposal"}""") else obj("""{"id":42}"""), "ordinary", title, null, before, after,
            if (scenario == "complete") listOf("complete_now") else emptyList())),
        if (scenario == "complete") obj("""{"42":{"tracking":"stop","running_actual":{"id":71,"start_at":"2026-10-08T06:00:00Z"},"removed_plans":[{"id":81,"name":"Send the client proposal","day_date":"2026-10-09","start_minute":540,"end_minute":600,"task_type_id":9}],"detached_actual_ids":[],"cleared_fields":["ready_to_plan"]}}""") else obj("{}"),
        obj("""{"project_id:8":{"name":"Client launch"},"task_type_id:9":{"path":"Work/Clients"}}"""), false, "pending", true, obj("{}"))
}

private fun polishResult(p: TaskChangeProposal, scenario: String, stage: String): TaskOperationResult = TaskOperationResult(
    p.operationId, p.id, "applied",
    buildJsonObject {
        put("committed_at", "2026-10-08T06:30:00Z")
        if (scenario == "create") put("created", obj("""{"proposal":42}"""))
        put("changes", buildJsonArray { add(buildJsonObject {
            put("target_id", 42); put("kind", "ordinary"); put("approved_after_values", p.targets.single().after)
        }) })
        if (scenario == "complete") put("effects", obj("""{"stopped_actual_ids":[71],"removed_plan_ids":[81],"cleared_fields":["ready_to_plan"]}"""))
    }, null,
    if (scenario == "complete") obj(if (stage == "undone") """{"status":"applied","receipt":{"committed_at":"2026-10-08T06:31:00Z"}}""" else """{"status":"available"}""") else null,
    JsonArray(emptyList()),
)
