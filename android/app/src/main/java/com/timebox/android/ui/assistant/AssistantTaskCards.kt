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
import androidx.compose.ui.unit.dp
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
        Column(Modifier.fillMaxWidth().padding(14.dp).semantics { isTraversalGroup = true }, verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun TaskReadCard(card: AssistantTaskCard, onOpenTask: (Int) -> Unit) {
    var expanded by rememberSaveable(card.id) { mutableStateOf(false) }
    TaskSurface {
        Text("Saved Tasks", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
        Text("Historical snapshot · Read ${card.readAt.atZone(card.zone).format(taskReadTime)} · ${card.zone.id}", style = TimeboxTheme.type.bodySmall)
        card.queryAt?.let { Text("Membership as of ${it.atZone(card.zone).format(taskReadTime)}", style = TimeboxTheme.type.bodySmall) }
        Text(when (card.countRelation) {
            "at_least" -> "At least ${card.count} matches · ${card.rows.size} loaded"
            "exact" -> "${card.count} matches · ${card.rows.size} loaded"
            else -> "Total unknown · ${card.rows.size} loaded"
        }, style = TimeboxTheme.type.bodySmall)
        if (card.partial) Text("Partial results. This card does not show all matching saved work.", style = TimeboxTheme.type.bodySmall)
        if (card.hasMore) Text("More saved results are available. Ask for the next page.", style = TimeboxTheme.type.bodySmall)
        if (card.rows.isEmpty()) Text(if (card.partial || card.countRelation != "exact") "No rows loaded; the result is incomplete." else "No saved Tasks matched this read.")
        (if (expanded) card.rows else card.rows.take(3)).forEachIndexed { index, row ->
            HorizontalDivider(color = TimeboxTheme.colors.hairline)
            TaskSnapshotRow(row, index + 1, card.zone, onOpenTask)
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
private fun TaskSnapshotRow(row: AssistantTaskRow, ordinal: Int, zone: ZoneId, onOpenTask: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$ordinal. ${row.title}", Modifier.semantics { heading() }, style = TimeboxTheme.type.body)
        Text("${when (row.kind) { "occurrence" -> "Saved occurrence"; "quota_tracker" -> "Quota Tracker"; "session" -> "Session Task"; else -> "Task" }} #${row.id}", style = TimeboxTheme.type.bodySmall)
        if (row.availability != "available") Text("${row.availability.replaceFirstChar { it.titlecase() }} at read time", style = TimeboxTheme.type.bodySmall)
        val fields = row.values
        fields.optionalText("lifecycle")?.let { Text(it.replaceFirstChar { c -> c.titlecase() }, style = TimeboxTheme.type.bodySmall) }
        listOf("project", "task_type", "ready_to_plan", "blocked", "blocking_reason", "urgency", "importance", "deadline", "reminder_at", "period", "quota", "parent_id", "series_id", "relevance").forEach { field ->
            fields[field]?.takeUnless { it == JsonNull }?.let { Text("${taskFieldLabel(field)}: ${taskValue(it, field)}", style = TimeboxTheme.type.bodySmall) }
        }
        fields.optionalText("completed_at")?.let {
            val dateOnly = fields.optionalText("completion_precision") == "date"
            Text("Completed: " + if (dateOnly) fields.text("completion_local_date") else Instant.parse(it).atZone(zone).format(taskReadTime), style = TimeboxTheme.type.bodySmall)
        }
        fields["subtasks"]?.jsonArray?.forEach { child ->
            Text("Subtask #${child.jsonObject.integer("id")}: ${child.jsonObject.text("title")} · ${if (child.jsonObject.flag("checked")) "Checked" else "Unchecked"}", style = TimeboxTheme.type.bodySmall)
        }
        fields["subtasks_count"]?.let { Text("${fields["subtasks"]?.jsonArray?.size ?: 0} of ${it.jsonPrimitive.int} Subtasks loaded", style = TimeboxTheme.type.bodySmall) }
        fields["matched_subtask_ids"]?.let { Text("Matched Subtask IDs: ${taskValue(it)}", style = TimeboxTheme.type.bodySmall) }
        fields["planned_dates"]?.let { Text("Planned dates: ${taskValue(it)}", style = TimeboxTheme.type.bodySmall) }
        if (fields["planned_dates_complete"]?.jsonPrimitive?.booleanOrNull == false) Text("Planned dates are incomplete.", style = TimeboxTheme.type.bodySmall)
        fields["sessions"]?.jsonArray?.forEachIndexed { index, item -> TaskSnapshotRow(AssistantTaskRow.parse(item.jsonObject), index + 1, zone, onOpenTask) }
        if (fields.optionalText("subtasks_next_cursor") != null || fields.optionalText("sessions_next_cursor") != null) Text("More saved children are available; ask for another page.", style = TimeboxTheme.type.bodySmall)
        TextButton(onClick = { onOpenTask(row.id) }, modifier = Modifier.semantics { contentDescription = "Open current Task ${row.title}, ID ${row.id}" }) { Text("Open Task") }
    }
}

@Composable
internal fun TaskChangeCard(proposal: TaskChangeProposal, state: TaskChangeState, onOpenTask: (Int) -> Unit,
    onDescription: suspend () -> TaskChangeProposal, onCheck: () -> Unit, onDismiss: () -> Unit, onRefresh: () -> Unit) {
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
    TaskSurface {
        Text("Proposed Task changes", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
        Text("Preview · Nothing is saved by this proposal", style = TimeboxTheme.type.bodySmall)
        Text("Review expires ${proposal.expiresAt.atZone(proposal.zone).format(taskReadTime)} · ${proposal.zone.id}", style = TimeboxTheme.type.bodySmall)
        TaskReviewContent(proposal, false, onOpenTask)
        val status = if (state.status == "pending" && Instant.now() >= proposal.expiresAt) "expired" else state.status
        Text(taskStatusLabel(status, state.result), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = TimeboxTheme.type.bodySmall)
        state.error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (proposal.descriptionReview) {
            TextButton(onClick = {
                keyboard?.hide(); loading = true; error = null
                scope.launch {
                    try { description = onDescription() }
                    catch (_: Exception) { error = "Could not load the complete description review. Try again online." }
                    finally { loading = false }
                }
            }, enabled = !loading, modifier = Modifier.focusRequester(reviewFocus).focusProperties { canFocus = true }.onFocusChanged { if (it.isFocused) restoreReviewFocus = false }) { Text(if (loading) "Loading description…" else "Review description") }
        } else {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Confirm Task changes") }
            Text(TaskConfirmationGate, style = TimeboxTheme.type.bodySmall)
        }
        if (status in setOf("stale", "expired")) TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Refresh review") }
        TextButton(onClick = onCheck, enabled = !state.busy && state.sourceCompleted) { Text(if (state.busy) "Checking…" else "Check result") }
        if (status in setOf("draft", "pending", "stale", "expired")) TextButton(onClick = onDismiss, enabled = !state.busy && state.sourceCompleted) { Text("Dismiss proposal") }
    }
    state.result?.let { TaskResultCard(it, onOpenTask) }
    description?.let { full -> DescriptionReview(full, onOpenTask, onClose = { description = null; restoreReviewFocus = true }) }
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
internal fun TaskReviewContent(proposal: TaskChangeProposal, descriptions: Boolean, onOpenTask: (Int) -> Unit) {
    proposal.targets.forEach { target ->
        HorizontalDivider(color = TimeboxTheme.colors.hairline)
        Text("${target.title} · ${target.identity}", Modifier.semantics { heading() }, style = TimeboxTheme.type.body)
        if (target.kind == "subtask") Text("Subtask · Parent ${taskValue(target.parent)}", style = TimeboxTheme.type.bodySmall)
        if (target.before == null) Text("Create new ${if (target.kind == "subtask") "Subtask" else "Task"}", style = TimeboxTheme.type.bodySmall)
        (target.before.orEmpty().keys + target.after.keys).distinct().forEach { field ->
            // Precision metadata accompanies its date; never display the stored noon.
            if (field in setOf("completion_precision", "completion_local_date", "completion_timezone")) return@forEach
            if (field == "completed_at" && target.before?.optionalText("completion_precision") == "date") {
                Text("Before · Completed: ${target.before.text("completion_local_date")}", style = TimeboxTheme.type.bodySmall)
                if (field !in target.after) return@forEach
            }
            if (field == "description") {
                Text("Description · separate review", style = TimeboxTheme.type.bodySmall)
                if (descriptions) {
                    Text("Before description", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
                    SelectionContainer { Text(target.before?.get("description")?.takeUnless { it == JsonNull }?.jsonPrimitive?.content ?: "(Empty)", style = TimeboxTheme.type.body) }
                    Text("After description", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
                    SelectionContainer { Text(target.after["description"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content?.ifEmpty { "(Empty)" } ?: "(Empty)", style = TimeboxTheme.type.body) }
                }
            } else {
                Text(taskFieldLabel(field), style = TimeboxTheme.type.label)
                if (target.before != null && !(field == "completed_at" && target.before.optionalText("completion_precision") == "date")) Text("Before: ${taskValue(target.before[field], field, proposal.references)}", style = TimeboxTheme.type.bodySmall)
                if (field in target.after) Text("After: ${taskValue(target.after[field], field, proposal.references)}", style = TimeboxTheme.type.bodySmall)
            }
        }
        target.transitions.forEach { transition -> Text(when (transition) {
            "complete_now" -> "Complete now. Child checks stay unchanged; eligible future plans and linked tracking are reviewed below."
            "complete_at" -> "Record earlier completion. Tracking and all Planned Blocks stay unchanged. Child checks stay unchanged."
            else -> "Reopen. Previous plans, tracking, readiness and reminders are not restored."
        }, style = TimeboxTheme.type.bodySmall) }
        proposal.effects[target.target["id"]?.jsonPrimitive?.content ?: target.target.text("ref")]?.jsonObject?.let { effects ->
            Text("Material effects", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
            effects.optionalText("tracking")?.let { tracking ->
                Text(if (tracking == "stop") "Stops the linked running Actual Block." else "Tracking stays unchanged.", style = TimeboxTheme.type.bodySmall)
            }
            effects["running_actual"]?.takeUnless { it == JsonNull }?.jsonObject?.let { running ->
                Text("Linked Actual #${running.integer("id")} · Started ${running.text("start_at")}", style = TimeboxTheme.type.bodySmall)
            }
            effects["removed_plans"]?.jsonArray?.let { plans ->
                Text("${plans.size} Planned Blocks removed", style = TimeboxTheme.type.bodySmall)
                plans.forEach { value ->
                    val plan = value.jsonObject
                    Text("Planned Block #${plan.integer("id")} · ${plan.optionalText("name") ?: "Task plan"}", style = TimeboxTheme.type.bodySmall)
                    Text("${plan.text("day_date")} · ${plan.optionalText("start_at") ?: plan.getValue("start_minute").jsonPrimitive.int.let(::planMinute)} – ${plan.optionalText("end_at") ?: plan.getValue("end_minute").jsonPrimitive.int.let(::planMinute)} · Task Type #${plan.integer("task_type_id")}", style = TimeboxTheme.type.bodySmall)
                }
            }
            effects["detached_actual_ids"]?.jsonArray?.takeIf { it.isNotEmpty() }?.let { Text("Detach plan links from Actual Blocks: ${it.joinToString { id -> "#${id.jsonPrimitive.content}" }}", style = TimeboxTheme.type.bodySmall) }
            effects["cleared_fields"]?.jsonArray?.let { Text("Clears: ${it.joinToString { field -> taskFieldLabel(field.jsonPrimitive.content) }}", style = TimeboxTheme.type.bodySmall) }
        }
        val detailId = if (target.kind == "subtask") target.parent?.jsonPrimitive?.intOrNull else target.id
        detailId?.let { id -> TextButton(onClick = { onOpenTask(id) }) { Text("Open Task #$id") } }
    }
}

@Composable
internal fun DescriptionReview(proposal: TaskChangeProposal, onOpenTask: (Int) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = TimeboxTheme.colors.bg) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                TextButton(onClick = onClose) { Text("Return to preview") }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Review description", Modifier.semantics { heading() }, style = TimeboxTheme.type.screenTitle)
                    Text("Full saved before-text and proposed after-text. Text is shown literally.", style = TimeboxTheme.type.bodySmall)
                    TaskReviewContent(proposal, true, onOpenTask)
                    Text(TaskConfirmationGate, style = TimeboxTheme.type.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("Confirm Task changes") }
            }
        }
    }
}

@Composable
internal fun TaskResultCard(result: TaskOperationResult, onOpenTask: (Int) -> Unit) {
    TaskSurface {
        Text("Task change result", Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
        Text(taskStatusLabel(result.status, result), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = TimeboxTheme.type.bodySmall)
        result.submission?.optionalText("reason")?.let { Text(taskFieldLabel(it), style = TimeboxTheme.type.bodySmall) }
        result.receipt?.let { receipt ->
            Text("Saved ${receipt.text("committed_at")}", style = TimeboxTheme.type.bodySmall)
            receipt["changes"]?.jsonArray?.forEach { item ->
                val change = item.jsonObject
                val id = change.integer("target_id")
                Text("${taskFieldLabel(change.text("kind"))} #$id", Modifier.semantics { heading() }, style = TimeboxTheme.type.body)
                change.getValue("approved_after_values").jsonObject.forEach { (field, value) ->
                    if (field !in setOf("completion_precision", "completion_local_date", "completion_timezone")) Text("${taskFieldLabel(field)}: ${taskValue(value, field)}", style = TimeboxTheme.type.bodySmall)
                }
                // A Subtask ID is not a Task ID. Receipts do not carry its parent identity.
                if (change.text("kind") == "ordinary") TextButton(onClick = { onOpenTask(id) }) { Text("Open Task #$id") }
            }
            receipt["effects"]?.jsonObject?.forEach { (field, value) -> Text("${taskFieldLabel(field)}: ${taskValue(value, field)}", style = TimeboxTheme.type.bodySmall) }
            receipt["target_id"]?.jsonPrimitive?.int?.let { id ->
                Text("Completion undone · Tracking stayed stopped.", style = TimeboxTheme.type.bodySmall)
                TextButton(onClick = { onOpenTask(id) }) { Text("Open Task #$id") }
            }
            result.undo?.let { undo ->
                undo["receipt"]?.takeUnless { it == JsonNull }?.jsonObject?.optionalText("committed_at")?.let { Text("Undo saved $it", style = TimeboxTheme.type.bodySmall) }
                Text(when (undo.text("status")) { "applied" -> "Completion subsequently undone. Tracking stayed stopped. The original saved receipt remains historical."; "conflict" -> "Undo unavailable because the saved state changed."; else -> "Completion Undo available after recovery integration. Tracking stays stopped." }, style = TimeboxTheme.type.bodySmall)
                if (undo.text("status") == "available") Button(onClick = {}, enabled = false) { Text("Undo completion") }
            }
        }
    }
}
