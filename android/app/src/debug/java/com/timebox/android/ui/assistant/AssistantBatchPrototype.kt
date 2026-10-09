package com.timebox.android.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Two-task and description-review study. Confirmation only changes local sample state. */
@Composable
internal fun BatchReviewStudy(layout: String, stage: String, setStage: (String) -> Unit, open: (Int) -> Unit, longDescription: Boolean = false) {
    val full = remember(longDescription) { batchProposal(longDescription) }
    val preview = remember(full) { full.copy(targets = full.targets.map { target ->
        fun redact(fields: JsonObject?) = fields?.let { JsonObject(it.mapValues { (name, value) ->
            if (name == "description") buildJsonObject { put("characters", value.jsonPrimitive.content.length); put("separate_review", true) } else value
        }) }
        target.copy(before = redact(target.before), after = redact(target.after)!!)
    }) }
    val result = remember(preview) { batchResult(preview) }
    var fullReview by rememberSaveable(stage) { mutableStateOf(false) }
    var details by rememberSaveable(stage) { mutableStateOf(false) }
    var failNextLoad by rememberSaveable(stage) { mutableStateOf(stage == "description-error") }
    var loadError by rememberSaveable(stage) { mutableStateOf(false) }
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    if (layout == "current") {
        TaskChangeCard(preview, TaskChangeState(if (stage == "description-error") "pending" else stage, true, if (stage == "applied") result else null), open, {
            if (failNextLoad) { failNextLoad = false; error("Sample description load failure") }
            full
        },
            { if (stage == "unknown") setStage("applied") }, { setStage("cancelled") }, { setStage("pending") }, onConfirm = { setStage("applied") })
        return
    }
    Surface(color = colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(when (stage) {
                "applied" -> "2 tasks saved together"
                "stale" -> "Review needs refreshing"
                "unknown" -> "Checking both tasks"
                "cancelled" -> "Proposal dismissed"
                else -> "2 tasks to review"
            }, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = type.label)
            when (stage) {
                "cancelled" -> Text("No changes were saved.", style = type.body)
                "unknown" -> {
                    Text("The reply was lost. Both task changes may already be saved. Check before trying again.", style = type.body)
                    Button(onClick = { setStage("applied") }, modifier = Modifier.fillMaxWidth()) { Text("Check result") }
                }
                else -> {
                    preview.targets.forEachIndexed { index, target ->
                        HorizontalDivider(color = colors.hairline)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PolishTaskTitle(target.title)
                            Text("${if (target.before == null) if (stage == "applied") "Created" else "Create task" else if (stage == "applied") "Updated" else "Update task"} · Client launch",
                                style = type.bodySmall, color = colors.onVariant)
                        }
                        if (stage == "applied") {
                            Text(if (index == 0) "Deadline moved to Fri, 9 Oct. Ready to Plan and description updated." else "Due Sat, 10 Oct. Not ready to plan.", style = type.body)
                            if (details) BatchFields(target, preview, saved = true)
                            TextButton(onClick = { open(if (index == 0) 42 else 47) }) { Text("Open Task") }
                        } else BatchFields(target, preview)
                    }
                    if (stage == "applied") {
                        Text("Saved together in this sample.", style = type.bodySmall, color = colors.onVariant)
                        TextButton(onClick = { details = !details }) { Text(if (details) "Hide saved details" else "Saved details") }
                    } else if (stage == "stale") {
                        Text("One task changed after this preview. Refresh and review the whole proposal before confirming.", style = type.body)
                        Button(onClick = { setStage("pending") }, modifier = Modifier.fillMaxWidth()) { Text("Refresh review") }
                    } else {
                        Text("Both tasks will be saved together. Nothing saved yet.", style = type.bodySmall, color = colors.onVariant)
                        if (loadError) Text("Could not load the full description. Nothing saved. Try again online.",
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = type.body, color = MaterialTheme.colorScheme.error)
                        Button(onClick = {
                            if (failNextLoad) { failNextLoad = false; loadError = true }
                            else { loadError = false; fullReview = true }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (loadError) "Retry description review" else "Review description") }
                        Text("Review the full description before confirming both tasks.", style = type.bodySmall, color = colors.onVariant)
                    }
                    if (stage != "applied") TextButton(onClick = { setStage("cancelled") }) { Text("Dismiss proposal") }
                }
            }
        }
    }
    if (fullReview) Dialog(onDismissRequest = { fullReview = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.bg) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TextButton(onClick = { fullReview = false }) { Text("Back to preview") }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Review both tasks", Modifier.semantics { heading() }, style = type.screenTitle)
                    Text("Nothing saved yet. Confirming saves both task changes together.", style = type.bodySmall)
                    full.targets.forEach { target ->
                        HorizontalDivider(color = colors.hairline)
                        PolishTaskTitle(target.title)
                        Text(if (target.before == null) "Create task · Client launch" else "Update task · Client launch", style = type.bodySmall, color = colors.onVariant)
                        BatchFields(target, full, descriptions = true)
                    }
                }
                Button(onClick = { fullReview = false; setStage("applied") }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Confirm both tasks") }
            }
        }
    }
}

@Composable
private fun BatchFields(target: TaskReviewTarget, proposal: TaskChangeProposal, descriptions: Boolean = false, saved: Boolean = false) {
    target.after.forEach { (field, value) ->
        // A new task's Project is already shown with its title.
        if (field == "title" || field == "project_id" && target.before == null) return@forEach
        if (field == "description") {
            if (descriptions) {
                Text("Current description", style = TimeboxTheme.type.label)
                SelectionContainer { Text(target.before!!.text("description"), style = TimeboxTheme.type.body) }
                Text("Proposed description", style = TimeboxTheme.type.label)
                SelectionContainer { Text(value.jsonPrimitive.content, style = TimeboxTheme.type.body) }
            } else Text(if (saved) "Description updated" else "Description changed · separate review", style = TimeboxTheme.type.body)
        } else {
            fun display(v: JsonElement?): String = if (field == "deadline" && v is JsonObject)
                LocalDate.parse(v.text("date")).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
                else taskValue(v, field, proposal.references).substringBefore(" (#")
            ChangeLine(taskFieldLabel(field), target.before?.get(field)?.let(::display), display(value))
        }
    }
}

private fun batchProposal(longDescription: Boolean): TaskChangeProposal {
    val base = polishProposal("edit")
    val target = base.targets.single()
    val description = if (longDescription) listOf(
        "Confirm the revised scope, pricing and delivery milestones with the client. Include the updated pricing appendix before sending.",
        "Scope\nDescribe the discovery workshop, agreed deliverables and final handover. Explain which supporting materials the client will provide and when we need them. Keep the optional reporting dashboard outside the initial scope unless the client explicitly adds it.",
        "Pricing\nShow the discovery, delivery and handover fees separately. Include the assumptions behind each estimate, the payment schedule and how additional requests will be assessed. Check the totals against the pricing appendix before sharing the proposal.",
        "Delivery milestones\nSchedule discovery first, then a review of the initial direction, delivery of the agreed work and a final handover. Explain that milestone dates depend on timely access to the required materials and feedback from the client.",
        "Client review\nAsk the client to name one person who can collect feedback and approve each milestone. Allow time for one consolidated feedback round at each agreed review point. Record unresolved questions in the handover notes so they are visible to the delivery team.",
        "Final checks\nVerify the recipient, attachments, dates and pricing. Remove internal notes from the client copy. Confirm that the final proposal and pricing appendix use the same scope and revision date.",
        "After sending\nSave the sent version with the project and record any follow-up questions. Prepare the handover notes as a separate task. Do not treat sending the proposal as client approval of the work.",
    ).joinToString("\n\n") else "Confirm the revised scope, pricing and delivery milestones with the client.\nInclude the updated pricing appendix before sending."
    return base.copy(id = "study-batch", descriptionReview = true, targets = listOf(
        target.copy(before = JsonObject(target.before!! + ("description" to JsonPrimitive("Confirm the original scope with the client."))),
            after = JsonObject(target.after + ("description" to JsonPrimitive(description)))),
        TaskReviewTarget(buildJsonObject { put("ref", "handover") }, "ordinary", "Prepare handover notes", null, null,
            buildJsonObject {
                put("title", "Prepare handover notes"); put("project_id", 8)
                put("deadline", buildJsonObject { put("kind", "date"); put("date", "2026-10-10") }); put("ready_to_plan", false)
            }, emptyList()),
    ))
}

private fun batchResult(p: TaskChangeProposal) = TaskOperationResult(p.operationId, p.id, "applied", buildJsonObject {
    put("committed_at", "2026-10-08T06:30:00Z")
    put("created", buildJsonObject { put("handover", 47) })
    put("changes", buildJsonArray { p.targets.forEachIndexed { index, target -> add(buildJsonObject {
        put("target_id", if (index == 0) 42 else 47); put("kind", "ordinary"); put("approved_after_values", target.after)
    }) } })
}, null, null, JsonArray(emptyList()))
