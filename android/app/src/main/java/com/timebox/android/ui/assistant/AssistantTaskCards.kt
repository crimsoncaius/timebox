package com.timebox.android.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun planMinute(value: Int) = "%02d:%02d".format(value / 60, value % 60)

private val taskReadTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@Composable
internal fun AssistantReadCards(exchange: AssistantExchange, onOpenTask: (Int) -> Unit, onOpenDay: (LocalDate) -> Unit, onOpenTrends: (LocalDate, LocalDate) -> Unit) {
    if (exchange.taskCards.isEmpty()) { AssistantCards(exchange.cards, onOpenDay, onOpenTrends); return }
    val order = exchange.cardOrder
    @Composable fun Content(id: String) {
        exchange.taskCards.find { it.id == id }?.let { TaskReadCard(it, onOpenTask) }
            ?: exchange.cards.find { it.id == id }?.let { AssistantCards(listOf(it), onOpenDay, onOpenTrends) }
    }
    if (order.size == 1) { Content(order.single()); return }
    val pager = rememberPagerState(pageCount = { order.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.semantics { isTraversalGroup = true }) {
        ScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 0.dp, containerColor = TimeboxTheme.colors.bg) {
            order.forEachIndexed { index, id ->
                val label = exchange.taskCards.find { it.id == id }?.let { "Tasks · ${it.readAt.atZone(it.zone).toLocalDate()}" }
                    ?: exchange.cards.first { it.id == id }.label
                Tab(selected = pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = "$label, card ${index + 1} of ${order.size}" })
            }
        }
        HorizontalPager(state = pager, verticalAlignment = androidx.compose.ui.Alignment.Top) { index ->
            Box(if (index == pager.currentPage) Modifier else Modifier.clearAndSetSemantics {}) { Content(order[index]) }
        }
    }
}

@Composable
private fun TaskSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = TimeboxTheme.colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, TimeboxTheme.colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).semantics { isTraversalGroup = true }, verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun TaskReadCard(card: AssistantTaskCard, onOpenTask: (Int) -> Unit) {
    var expanded by rememberSaveable(card.id) { mutableStateOf(false) }
    TaskSurface {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Saved Tasks", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
            Text("Historical snapshot · Read ${card.readAt.atZone(card.zone).format(taskReadTime)} · ${card.zone.id}", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            card.queryAt?.takeIf { it != card.readAt }?.let { Text("Membership as of ${it.atZone(card.zone).format(taskReadTime)}", style = TimeboxTheme.type.bodySmall) }
            Text(when (card.countRelation) {
                "at_least" -> "At least ${card.count} matches · ${card.rows.size} loaded"
                "exact" -> "${card.count} matches · ${card.rows.size} loaded"
                else -> "Total unknown · ${card.rows.size} loaded"
            }, style = TimeboxTheme.type.bodySmall)
        }
        if (card.partial) Text("Partial results. This card does not show all matching saved work.", style = TimeboxTheme.type.bodySmall)
        if (card.hasMore) Text("More saved results are available. Ask for the next page.", style = TimeboxTheme.type.bodySmall)
        if (card.rows.isEmpty()) Text(if (card.partial || card.countRelation != "exact") "No rows loaded; the result is incomplete." else "No saved Tasks matched this read.")
        (if (expanded) card.rows else card.rows.take(3)).forEach { row ->
            HorizontalDivider(color = TimeboxTheme.colors.hairline)
            key(card.id, row.id) { TaskSnapshotRow(row, card.zone, onOpenTask) }
        }
        if (card.rows.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show fewer Tasks" else "Show all ${card.rows.size} loaded Tasks") }
        card.limitations.forEach { limitation ->
            Text(when (limitation) {
                "saved_only" -> "Saved recurring records only. Missing occurrences or Sessions do not mean nothing is due."
                "unverified_readiness" -> "Ready to Plan is unverified for this read."
                "query_limit" -> "Search limit reached; narrow the request for complete coverage."
                "payload_limit" -> "Some details could not fit. Ask for a narrower read."
                else -> "Some saved identities are unavailable."
            }, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        }
        Text("Open Task shows current details; this snapshot stays unchanged.", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TaskSnapshotRow(row: AssistantTaskRow, zone: ZoneId, onOpenTask: (Int) -> Unit) {
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistantTaskTitle(row.title)
        val kind = when (row.kind) { "occurrence" -> "Saved occurrence"; "quota_tracker" -> "Quota Tracker"; "session" -> "Session Task"; else -> "Task" }
        val fields = row.values
        if (row.availability != "available") Text("${row.availability.replaceFirstChar { it.titlecase() }} at read time. Details could not be verified.", style = TimeboxTheme.type.body)
        else {
            Text(fields["project"]?.takeUnless { it == JsonNull }?.jsonObject?.text("name") ?: if ("project" in fields) "No project" else kind,
                style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            if (row.kind != "ordinary" && "project" in fields) Text(kind, style = TimeboxTheme.type.bodySmall)
            fields.optionalText("lifecycle")?.takeIf { it != "incomplete" }?.let { Text(it.replaceFirstChar { c -> c.titlecase() }, style = TimeboxTheme.type.bodySmall) }
            if ("deadline" in fields) Text(fields["deadline"]?.takeUnless { it == JsonNull }?.let { "Due ${taskDisplayValue(it, "deadline", zone)}" } ?: "No deadline", style = TimeboxTheme.type.body)
            if (fields["blocked"]?.jsonPrimitive?.booleanOrNull == true) Text("Blocked" + (fields.optionalText("blocking_reason")?.let { " · $it" } ?: ""), style = TimeboxTheme.type.body)
            if (fields["blocked"]?.jsonPrimitive?.booleanOrNull != true) fields["ready_to_plan"]?.jsonPrimitive?.booleanOrNull?.let { Text(if (it) "Ready to Plan" else "Not ready to plan", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant) }
            listOf("period", "quota").forEach { field -> fields[field]?.let { Text("${taskFieldLabel(field)}: ${taskValue(it, field)}", style = TimeboxTheme.type.bodySmall) } }
            if (expanded) {
                Text("Saved details · $kind #${row.id}", style = TimeboxTheme.type.label)
                listOf("lifecycle", "task_type", "blocked", "ready_to_plan", "urgency", "importance", "reminder_at", "parent_id", "series_id", "relevance").forEach { field ->
                    fields[field]?.takeUnless { it == JsonNull }?.let { Text("${taskFieldLabel(field)}: ${taskDisplayValue(it, field, zone)}", style = TimeboxTheme.type.bodySmall) }
                }
                fields.optionalText("completed_at")?.let {
                    val dateOnly = fields.optionalText("completion_precision") == "date"
                    Text("Completed: " + if (dateOnly) fields.text("completion_local_date") else Instant.parse(it).atZone(zone).format(taskReadTime), style = TimeboxTheme.type.bodySmall)
                }
                fields["subtasks"]?.jsonArray?.forEach { child ->
                    val subtask = child.jsonObject
                    Text(if (subtask.optionalText("availability") == "unavailable") "Subtask #${subtask.integer("id")} · Unavailable at read time"
                        else "${subtask.text("title")} · ${if (subtask.flag("checked")) "Checked" else "Unchecked"}", style = TimeboxTheme.type.bodySmall)
                }
                fields["subtasks_count"]?.let { Text("${fields["subtasks"]?.jsonArray?.size ?: 0} of ${it.jsonPrimitive.int} Subtasks loaded", style = TimeboxTheme.type.bodySmall) }
                fields["matched_subtask_ids"]?.let { Text("Matched Subtask IDs: ${taskValue(it)}", style = TimeboxTheme.type.bodySmall) }
                fields["planned_dates"]?.let { Text("Planned dates: ${taskValue(it)}", style = TimeboxTheme.type.bodySmall) }
                if (fields["planned_dates_complete"]?.jsonPrimitive?.booleanOrNull == false) Text("Planned dates are incomplete.", style = TimeboxTheme.type.bodySmall)
                fields["sessions"]?.jsonArray?.forEach { item ->
                    val session = AssistantTaskRow.parse(item.jsonObject)
                    key(session.id) { TaskSnapshotRow(session, zone, onOpenTask) }
                }
                if (fields.optionalText("subtasks_next_cursor") != null || fields.optionalText("sessions_next_cursor") != null) Text("More saved children are available; ask for another page.", style = TimeboxTheme.type.bodySmall)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onOpenTask(row.id) }, modifier = Modifier.semantics { contentDescription = "Open current Task ${row.title}, ID ${row.id}" }) { Text("Open Task") }
            if (row.availability == "available") TextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) { Text(if (expanded) "Hide details" else "Saved details") }
        }

    }
}

@Composable
internal fun AssistantTaskTitle(title: String) {
    Text(title, Modifier.semantics { heading() }, style = TimeboxTheme.type.screenTitle.copy(
        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em))
}

internal fun taskDisplayValue(value: JsonElement?, field: String, zone: ZoneId, references: JsonObject = JsonObject(emptyMap())): String {
    if (value == null || value == JsonNull) return "None"
    if (field == "deadline" && value is JsonObject) return when (value.text("kind")) {
        "date" -> LocalDate.parse(value.text("date")).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
        else -> Instant.parse(value.text("instant")).atZone(zone).format(taskReadTime) + " · ${zone.id}"
    }
    if (field in setOf("reminder_at", "completed_at") && value is JsonPrimitive)
        return Instant.parse(value.content).atZone(zone).format(taskReadTime) + " · ${zone.id}"
    if (field == "project" && value is JsonObject) return value.text("name")
    if (field == "task_type" && value is JsonObject) return value.text("path")
    if (field in setOf("project_id", "task_type_id")) {
        references["$field:${value.jsonPrimitive.content}"]?.jsonObject?.optionalText(if (field == "project_id") "name" else "path")?.let { return it }
    }
    return taskValue(value, field, references)
}

@Composable
internal fun TaskChangeCard(proposal: TaskChangeProposal, state: TaskChangeState, onOpenTask: (Int) -> Unit,
    onDescription: suspend () -> TaskChangeProposal, onCheck: () -> Unit, onDismiss: () -> Unit, onRefresh: () -> Unit, onConfirm: (() -> Unit)? = null, confirmationBlocked: String? = null, onUndo: (() -> Unit)? = null) {
    var details by rememberSaveable(proposal.id) { mutableStateOf(false) }
    if (state.result?.receipt != null) {
        TaskResultCard(state.result, onOpenTask, onUndo, proposal)
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide reviewed changes" else "View reviewed changes") }
        if (details) TaskSurface {
            Text("Historical review · ${proposal.zone.id}", style = TimeboxTheme.type.bodySmall)
            TaskReviewContent(proposal, false, onOpenTask, true)
        }
        return
    }
    var description by remember(proposal.id) { mutableStateOf<TaskChangeProposal?>(null) }
    var loading by remember(proposal.id) { mutableStateOf(false) }
    var error by remember(proposal.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val reviewFocus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    var restoreReviewFocus by remember { mutableStateOf(false) }
    LaunchedEffect(restoreReviewFocus, window.isWindowFocused) {
        if (restoreReviewFocus && window.isWindowFocused) {
            // A transient window-focus event can race dialog removal. Losing
            // window focus cancels this effect; the next gain retries restoration.
            withFrameNanos { }
            reviewFocus.requestFocus()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val status = if (state.status == "pending" && Instant.now() >= proposal.expiresAt) "expired" else state.status
    TaskSurface {
        if (proposal.targets.size > 1) Text("${proposal.targets.size} changes to review together", style = TimeboxTheme.type.label)
        TaskReviewContent(proposal, false, onOpenTask, details)
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide review details" else "Review details") }
        if (details) Text("Review expires ${proposal.expiresAt.atZone(proposal.zone).format(taskReadTime)} · ${proposal.zone.id}", style = TimeboxTheme.type.bodySmall)
        if (proposal.targets.size > 1 && status == "pending") Text("All changes will be saved together. Nothing saved yet.", style = TimeboxTheme.type.bodySmall)
        Text(taskStatusLabel(status, state.result), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = TimeboxTheme.type.bodySmall)
        state.error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (proposal.descriptionReview) {
            Button(onClick = {
                keyboard?.hide(); loading = true; error = null
                scope.launch {
                    try { description = onDescription() }
                    catch (_: Exception) { error = "Could not load the complete description review. Try again online." }
                    finally { loading = false }
                }
            }, enabled = !loading, modifier = Modifier.fillMaxWidth().focusRequester(reviewFocus).focusProperties { canFocus = true }.onFocusChanged { if (it.isFocused) restoreReviewFocus = false }) { Text(if (loading) "Loading description…" else if (error != null) "Retry description review" else "Review description") }
        } else {
            Button(onClick = { onConfirm?.invoke() }, enabled = onConfirm != null && confirmationBlocked == null && status == "pending" && state.sourceCompleted && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(taskConfirmationLabel(proposal)) }
            if (onConfirm == null) Text(TaskConfirmationGate, style = TimeboxTheme.type.bodySmall)
        }
        confirmationBlocked?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = TimeboxTheme.type.bodySmall) }
        if (status in setOf("stale", "expired")) TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Refresh review") }
        if (details || status !in setOf("draft", "pending")) TextButton(onClick = onCheck, enabled = !state.busy && state.sourceCompleted) { Text(if (state.busy) "Checking…" else "Check result") }
        if (status in setOf("draft", "pending", "stale", "expired")) TextButton(onClick = onDismiss, enabled = !state.busy && state.sourceCompleted) { Text("Dismiss proposal") }
    }
    state.result?.let { TaskResultCard(it, onOpenTask, proposal = proposal) }
    description?.let { full -> DescriptionReview(full, onOpenTask, onClose = { description = null; restoreReviewFocus = true }, onConfirm = if (onConfirm != null && confirmationBlocked == null && status == "pending" && state.sourceCompleted && !state.busy) ({ onConfirm(); description = null }) else null) }
}

internal fun taskStatusLabel(status: String, result: TaskOperationResult?): String = when {
    result?.submission?.optionalText("state") == "not_seen" -> "Outcome unknown. The request may still arrive; check the result."
    result?.submission?.optionalText("state") == "rolled_back" -> "Rolled back. No changes from this submission were saved."
    result?.submission?.optionalText("state") == "rejected" -> "Submission rejected. No changes from this submission were saved."
    else -> when (status) {
        "draft" -> "Waiting for the response to finish and be saved."
        "pending" -> "Pending review. No task changes applied."
        "applied" -> "Saved result available below."
        "stale" -> "The reviewed state changed. Refresh and review again."
        "cancelled" -> "Proposal dismissed."
        "replaced" -> "Replaced by a newer review."
        "expired" -> "Review expired. Refresh before confirming."
        "invalid" -> "This response did not finish successfully. Confirmation unavailable."
        "submitting" -> "Submitting Task changes…"
        "sync_blocked" -> "Waiting for local changes to synchronize."
        else -> "Outcome unknown. Check result before making overlapping changes."
    }
}

@Composable
internal fun TaskReviewContent(proposal: TaskChangeProposal, descriptions: Boolean, onOpenTask: (Int) -> Unit, details: Boolean = false) {
    proposal.targets.forEachIndexed { index, target ->
        if (index > 0) HorizontalDivider(color = TimeboxTheme.colors.hairline)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistantTaskTitle(target.title)
            val project = (target.after["project_id"] ?: target.before?.get("project_id"))?.let { if (it == JsonNull) "No project" else taskDisplayValue(it, "project_id", proposal.zone, proposal.references) }
            Text(taskReviewAction(target) + (project?.let { " · $it" } ?: ""), style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        }
        if (proposal.targets.size == 1) HorizontalDivider(color = TimeboxTheme.colors.hairline)
        if (details) Text(target.identity, style = TimeboxTheme.type.bodySmall)
        if (target.kind == "subtask") Text("Subtask · Parent ${taskValue(target.parent)}", style = TimeboxTheme.type.bodySmall)
        (target.before.orEmpty().keys + target.after.keys).distinct().sortedBy { when (it) { "title" -> 0; "deadline" -> 1; "ready_to_plan" -> 2; else -> 3 } }.forEach { field ->
            // Precision metadata accompanies its date; never display the stored noon.
            if (field in setOf("completion_precision", "completion_local_date", "completion_timezone")) return@forEach
            if (target.before == null && field in setOf("title", "project_id")) return@forEach
            if (field == "completed_at" && target.before?.optionalText("completion_precision") == "date") {
                Text("Before · Completed: ${target.before.text("completion_local_date")}", style = TimeboxTheme.type.bodySmall)
                if (field !in target.after) return@forEach
            }
            if (field == "description") {
                if (!descriptions) Text("Description changed · separate review", style = TimeboxTheme.type.body)
                if (descriptions) {
                    Text("Current description", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
                    SelectionContainer { Text(target.before?.get("description")?.takeUnless { it == JsonNull }?.jsonPrimitive?.content ?: "(Empty)", style = TimeboxTheme.type.body) }
                    Text("Proposed description", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
                    SelectionContainer { Text(target.after["description"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content?.ifEmpty { "(Empty)" } ?: "(Empty)", style = TimeboxTheme.type.body) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(taskFieldLabel(field), style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                    val before = target.before?.takeUnless { field == "completed_at" && it.optionalText("completion_precision") == "date" }?.let { taskDisplayValue(it[field], field, proposal.zone, proposal.references) }
                    val after = if (field in target.after) taskDisplayValue(target.after[field], field, proposal.zone, proposal.references) else null
                    Text(if (before != null && after != null) "$before → $after" else after ?: before ?: "None", style = TimeboxTheme.type.body)
                }
            }
        }
        target.transitions.forEach { transition -> Text(when (transition) {
            "complete_now" -> "Complete now. Child checks stay unchanged; eligible future plans and linked tracking are reviewed below."
            "complete_at" -> "Record earlier completion. Tracking and all Planned Blocks stay unchanged. Child checks stay unchanged."
            else -> "Reopen. Previous plans, tracking, readiness and reminders are not restored."
        }, style = TimeboxTheme.type.bodySmall) }
        proposal.effects[target.target["id"]?.jsonPrimitive?.content ?: target.target.text("ref")]?.jsonObject?.let { effects ->
            Text("Also changes", Modifier.semantics { heading() }, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            effects.optionalText("tracking")?.let { tracking ->
                Text(if (tracking == "stop") "Stops the linked running Actual Block." else "Tracking stays unchanged.", style = TimeboxTheme.type.body)
            }
            effects["running_actual"]?.takeUnless { it == JsonNull }?.jsonObject?.let { running ->
                Text("Linked Actual #${running.integer("id")} · Started ${running.text("start_at")}", style = TimeboxTheme.type.bodySmall)
            }
            effects["removed_plans"]?.jsonArray?.let { plans ->
                Text("${plans.size} Planned Blocks removed", style = TimeboxTheme.type.body)
                plans.forEach { value ->
                    val plan = value.jsonObject
                    Text("Planned Block #${plan.integer("id")} · ${plan.optionalText("name") ?: "Task plan"}", style = TimeboxTheme.type.bodySmall)
                    Text("${plan.text("day_date")} · ${plan.optionalText("start_at") ?: plan.getValue("start_minute").jsonPrimitive.int.let(::planMinute)} – ${plan.optionalText("end_at") ?: plan.getValue("end_minute").jsonPrimitive.int.let(::planMinute)} · Task Type #${plan.integer("task_type_id")}", style = TimeboxTheme.type.bodySmall)
                }
            }
            effects["detached_actual_ids"]?.jsonArray?.takeIf { it.isNotEmpty() }?.let { Text("Detach plan links from Actual Blocks: ${it.joinToString { id -> "#${id.jsonPrimitive.content}" }}", style = TimeboxTheme.type.bodySmall) }
            effects["cleared_fields"]?.jsonArray?.let { Text("Clears: ${it.joinToString { field -> taskFieldLabel(field.jsonPrimitive.content) }}", style = TimeboxTheme.type.bodySmall) }
        }
        val detailId = if (target.kind == "subtask") when (val parent = target.parent) {
            is JsonPrimitive -> parent.intOrNull
            is JsonObject -> parent["id"]?.jsonPrimitive?.intOrNull
            else -> null
        } else target.id
        if (details) detailId?.let { id -> TextButton(onClick = { onOpenTask(id) }) { Text("Open Task #$id") } }
    }
}

internal fun taskReviewAction(target: TaskReviewTarget): String = when {
    target.before == null -> if (target.kind == "subtask") "Create Subtask" else "Create task"
    "complete_now" in target.transitions -> "Complete task"
    "complete_at" in target.transitions -> "Record earlier completion"
    "reopen_task" in target.transitions -> "Reopen task"
    else -> if (target.kind == "subtask") "Update Subtask" else "Update task"
}

internal fun taskConfirmationLabel(proposal: TaskChangeProposal): String {
    if (proposal.targets.size > 1) return if (proposal.targets.all { it.kind == "ordinary" }) {
        if (proposal.targets.size == 2) "Confirm both tasks" else "Confirm all ${proposal.targets.size} tasks"
    } else "Confirm all changes"
    val target = proposal.targets.single()
    return when {
        target.before == null -> "Confirm creation"
        target.transitions.any { it in setOf("complete_now", "complete_at") } -> "Confirm completion"
        "reopen_task" in target.transitions -> "Confirm reopening"
        else -> "Confirm changes"
    }
}

@Composable
internal fun DescriptionReview(proposal: TaskChangeProposal, onOpenTask: (Int) -> Unit, onClose: () -> Unit, onConfirm: (() -> Unit)? = null) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = TimeboxTheme.colors.bg) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                TextButton(onClick = onClose) { Text("Back to preview") }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (proposal.targets.size > 1) "Review all changes" else "Review description", Modifier.semantics { heading() }, style = TimeboxTheme.type.screenTitle)
                    Text("Nothing saved yet. Confirming saves all reviewed changes together. Full descriptions are shown literally.", style = TimeboxTheme.type.bodySmall)
                    TaskReviewContent(proposal, true, onOpenTask)
                    if (onConfirm == null) Text(TaskConfirmationGate, style = TimeboxTheme.type.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
                Button(onClick = { onConfirm?.invoke() }, enabled = onConfirm != null, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(taskConfirmationLabel(proposal)) }
            }
        }
    }
}

/** Match by authoritative ID/ref mapping, never by receipt order or current mutable Task data. */
internal fun receiptTarget(result: TaskOperationResult, change: JsonObject, proposal: TaskChangeProposal?): TaskReviewTarget? {
    if (proposal?.id != result.proposalId || proposal.operationId != result.operationId) return null
    val created = result.receipt?.get("created") as? JsonObject
    return proposal.targets.singleOrNull { target ->
        target.kind == change.text("kind") && (target.id ?: created?.get(target.target.text("ref"))?.jsonPrimitive?.intOrNull) == change.integer("target_id")
    }
}

@Composable
internal fun TaskResultCard(result: TaskOperationResult, onOpenTask: (Int) -> Unit, onUndo: (() -> Unit)? = null, proposal: TaskChangeProposal? = null) {
    var details by rememberSaveable(result.operationId) { mutableStateOf(false) }
    val receipt = result.receipt
    val matchedProposal = proposal?.takeIf { it.id == result.proposalId && it.operationId == result.operationId }
    val zone = matchedProposal?.zone ?: ZoneId.systemDefault()
    val references = matchedProposal?.references ?: JsonObject(emptyMap())
    val changes = receipt?.get("changes")?.jsonArray.orEmpty()
    TaskSurface {
        if (receipt == null) {
            Text("Task change result", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
            Text(taskStatusLabel(result.status, result), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = TimeboxTheme.type.body)
            result.submission?.optionalText("reason")?.let { Text(taskFieldLabel(it), style = TimeboxTheme.type.bodySmall) }
        } else {
            if (changes.size > 1) Text("${changes.size} changes saved together", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = TimeboxTheme.type.label)
            changes.forEachIndexed { index, item ->
                val change = item.jsonObject
                val id = change.integer("target_id")
                val target = receiptTarget(result, change, matchedProposal)
                val fields = change.getValue("approved_after_values").jsonObject
                val title = fields.optionalText("title") ?: target?.title ?: "${if (change.text("kind") == "subtask") "Subtask" else "Task"} #$id"
                val created = (receipt["created"] as? JsonObject)?.values?.any { it.jsonPrimitive.intOrNull == id } == true
                if (index > 0) HorizontalDivider(color = TimeboxTheme.colors.hairline)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistantTaskTitle(title)
                    Text(when {
                        created -> if (change.text("kind") == "subtask") "Subtask created" else "Task created"
                        fields.optionalText("status") == "completed" || "completion" in fields -> "Task completed"
                        fields.optionalText("status") in setOf("incomplete", "open") -> "Task reopened"
                        else -> if (change.text("kind") == "subtask") "Subtask updated" else "Task updated"
                    }, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                }
                // Receipt values are authoritative; no live task lookup and no private description text.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fields.forEach { (field, value) ->
                        if (field in setOf("title", "status", "completion_precision", "completion_local_date", "completion_timezone")) return@forEach
                        if (details || field in setOf("deadline", "ready_to_plan", "description", "checked", "completion", "completed_at")) {
                            val display = if (field == "completed_at" && fields.optionalText("completion_precision") == "date") fields.text("completion_local_date")
                                else taskDisplayValue(value, field, zone, references)
                            Text(if (field == "description") "Description updated" else "${taskFieldLabel(field)}: $display", style = TimeboxTheme.type.body)
                        }
                    }
                    val otherFields = fields.keys - setOf("title", "status", "completion_precision", "completion_local_date", "completion_timezone", "deadline", "ready_to_plan", "description", "checked", "completion", "completed_at")
                    if (!details && otherFields.isNotEmpty()) Text("Also saved: ${otherFields.joinToString { taskFieldLabel(it) }}", style = TimeboxTheme.type.bodySmall)
                }
                if (details) Text("${taskFieldLabel(change.text("kind"))} #$id", style = TimeboxTheme.type.bodySmall)
                // Subtask IDs are not Task IDs. Only a matched proposal can supply the parent.
                val openId = if (change.text("kind") == "ordinary") id else when (val parent = target?.parent) {
                    is JsonPrimitive -> parent.intOrNull
                    is JsonObject -> parent["id"]?.jsonPrimitive?.intOrNull ?: (receipt["created"] as? JsonObject)?.get(parent.optionalText("ref"))?.jsonPrimitive?.intOrNull
                    else -> null
                }
                openId?.let { taskId -> TextButton(onClick = { onOpenTask(taskId) }, modifier = Modifier.semantics { contentDescription = "Open current Task $title, ID $taskId" }) { Text("Open Task") } }
            }
            receipt["effects"]?.jsonObject?.let { effects ->
                val stopped = effects["stopped_actual_ids"]?.jsonArray.orEmpty()
                val removed = effects["removed_plan_ids"]?.jsonArray.orEmpty()
                if (stopped.isNotEmpty()) Text("${stopped.size} running Actual Blocks stopped.", style = TimeboxTheme.type.body)
                if (removed.isNotEmpty()) Text("${removed.size} future Planned Blocks removed.", style = TimeboxTheme.type.body)
                if (details) effects.forEach { (field, value) -> Text("${taskFieldLabel(field)}: ${taskValue(value, field)}", style = TimeboxTheme.type.bodySmall) }
            }
            receipt["target_id"]?.jsonPrimitive?.int?.let { id ->
                AssistantTaskTitle("Task #$id")
                Text("Completion undone · Tracking stayed stopped.", style = TimeboxTheme.type.body)
                TextButton(onClick = { onOpenTask(id) }) { Text("Open Task") }
            }
            Text("Saved ${Instant.parse(receipt.text("committed_at")).atZone(zone).format(taskReadTime)} · ${zone.id}", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            result.undo?.let { undo ->
                undo["receipt"]?.takeUnless { it == JsonNull }?.jsonObject?.optionalText("committed_at")?.let { Text("Undo saved $it", style = TimeboxTheme.type.bodySmall) }
                Text(when (undo.text("status")) { "applied" -> "Completion subsequently undone. Tracking stayed stopped. The original saved receipt remains historical."; "conflict" -> "Undo unavailable because the saved state changed."; else -> "Completion Undo available. Tracking stays stopped." }, style = TimeboxTheme.type.bodySmall)
                if (undo.text("status") == "available") OutlinedButton(onClick = { onUndo?.invoke() }, enabled = onUndo != null) { Text("Undo completion") }
            }
            TextButton(onClick = { details = !details }, modifier = Modifier.semantics { stateDescription = if (details) "Expanded" else "Collapsed" }) { Text(if (details) "Hide saved details" else "Saved details") }
        }
    }
}
