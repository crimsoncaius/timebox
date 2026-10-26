package com.timebox.android.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.*
import com.timebox.android.ui.day.TaskTypePicker
import com.timebox.android.ui.day.ActivityTimeField
import com.timebox.android.ui.day.ActivityTimeValue
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

private fun JsonObject.reviewText(key: String) = get(key)?.jsonPrimitive?.contentOrNull
private fun JsonObject.reviewRecord() = get("record")?.takeUnless { it is JsonNull }?.jsonObject
private fun JsonObject.isStop() = getValue("receipt").jsonObject.reviewText("action") == "stop"
private fun JsonObject.canCorrect() = get("correction_available")?.jsonPrimitive?.booleanOrNull
    ?: (reviewRecord() != null && (!isStop() || reviewRecord()?.reviewText("end_at") != null))
private fun JsonObject.eventTime(): String? {
    val receipt = getValue("receipt").jsonObject
    val key = if (isStop()) "end_at" else "start_at"
    return if (canCorrect()) reviewRecord()?.reviewText(key)
        else receipt.reviewText("effective_at") ?: receipt.getValue("record").jsonObject.reviewText(key)
}
private fun displayReviewTime(value: String?, zone: ZoneId): String = value?.let {
    val time = Instant.parse(it).atZone(zone)
    time.format(DateTimeFormatter.ofPattern(if (time.toLocalDate() == LocalDate.now(zone)) "HH:mm" else "d MMM yyyy · HH:mm"))
} ?: "Time unavailable"

// Destination-scoped data survives navigation, including loaded review pages.
internal class AssistantReviewState : androidx.lifecycle.ViewModel() {
    val page = mutableStateOf<JsonObject?>(null)
    val rows = mutableStateOf<List<JsonObject>>(emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantReviewScreen(controller: AssistantController, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as TimeboxApplication
    val local by application.activityRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val retained: AssistantReviewState = androidx.lifecycle.viewmodel.compose.viewModel()
    var page by retained.page
    var rows by retained.rows
    var reviewed by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val accessibility = androidx.compose.ui.platform.LocalAccessibilityManager.current
    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(accessibility?.calculateRecommendedTimeoutMillis(5000, containsText = true, containsControls = true) ?: 5000)
            notice = null
        }
    }
    var editText by rememberSaveable { mutableStateOf<String?>(null) }
    if (editText != null) com.timebox.android.ui.components.HideNavigate()
    val edit = editText?.let { Json.parseToJsonElement(it).jsonObject }
    var details by remember { mutableStateOf<JsonObject?>(null) }
    if (details != null) com.timebox.android.ui.components.HideNavigate()
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    val zone = ZoneId.of(page?.reviewText("timezone") ?: local.snapshot?.reportingTimezone ?: "UTC")
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    suspend fun fetch(more: Boolean = false) {
        val before = if (more) page?.reviewText("next_before") else null
        val data = controller.reviewRequest("reviews?reviewed=$reviewed" + (before?.let { "&before=$it" } ?: ""))
        val loaded = data.getValue("items").jsonArray.map { it.jsonObject }
        rows = if (more) (rows + loaded).distinctBy { it.getValue("id") } else loaded
        page = data
        error = null
    }
    // One cancellable load per tab/refresh. Cursor changes are folded into this same load.
    LaunchedEffect(reviewed, refresh, local.snapshot?.cursor) {
        busy = true
        try { fetch() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not load Review. Try again." }
        finally { busy = false }
    }
    fun acknowledge(row: JsonObject) {
        busy = true
        scope.launch {
            try {
                controller.reviewRequest("reviews/acknowledge", buildJsonObject {
                    putJsonArray("items") { add(buildJsonObject { put("id", row.getValue("id")); put("revision", row.getValue("revision")) }) }
                })
                notice = "Marked reviewed"
                refresh++
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not mark reviewed. Reload Review and try again." }
            finally { busy = false }
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!reviewed, { if (!busy && reviewed) { reviewed = false; rows = emptyList(); page = null } }, label = { Text("To review${page?.reviewText("unchecked_count")?.let { " · $it" }.orEmpty()}") })
            FilterChip(reviewed, { if (!busy && !reviewed) { reviewed = true; rows = emptyList(); page = null } }, label = { Text("Reviewed") })
        }
        if (local.offline) Text("Offline · Reconnect to save corrections or mark reviewed.", style = type.bodySmall)
        if (local.pending) Text("Syncing activity. Corrections are available after sync.", style = type.bodySmall)
        error?.let {
            Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = colors.error, style = type.bodySmall)
            TextButton(enabled = !busy, onClick = { refresh++; scope.launch { application.activityRepository.refresh() } }) { Text("Retry") }
        }
        notice?.let { TransientFeedback(it, onDismiss = { notice = null }) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp + LocalNavigationDockInset.current)) {
            if (!reviewed) items(local.quickReviews, key = { it.interpretation.requestId }) { pending ->
                val result = pending.interpretation
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (result.action == "stop") "Stopped tracking" else local.snapshot?.taskTypes?.find { it.id == result.taskTypeId }?.name ?: "Recorded activity", style = type.sectionTitle)
                    Text("${if (result.action == "stop") "Stopped" else "Started"} at ${displayReviewTime(result.effectiveAt, ZoneId.of(result.timezone))}", style = type.body)
                    Text("You said: “${result.question}”", style = type.body, color = colors.onVariant)
                    Text(if (pending.rejected) "Could not sync · Open Day for recovery" else "Saved on this device · Unsynced", style = type.bodySmall)
                    TextButton(onClick = { onOpenDay(Instant.parse(result.effectiveAt).atZone(ZoneId.of(result.timezone)).toLocalDate()) }) { Text("Open day") }
                }
                HorizontalDivider(color = colors.hairline)
            }
            if (rows.isEmpty() && (reviewed || local.quickReviews.isEmpty()) && !busy && error == null) item {
                Column(Modifier.padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (reviewed) "No activities reviewed yet" else "No activities to review", style = type.sectionTitle)
                    Text(if (reviewed) "Activities you mark reviewed will appear here." else "Your next recorded activity will appear here for a quick check.", style = type.body, color = colors.onVariant)
                }
            }
            items(rows, key = { it.getValue("id").jsonPrimitive.int }) { row ->
                val receipt = row.getValue("receipt").jsonObject
                val shown = row.reviewRecord() ?: receipt.getValue("record").jsonObject
                val category = shown.getValue("task_type").jsonObject.reviewText("name").orEmpty()
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val identity = shown.reviewText("name")?.takeIf { it.isNotBlank() } ?: category
                    Text(identity, style = type.sectionTitle)
                    if (identity != category) Text(category, style = type.bodySmall, color = colors.onVariant)
                    Text("${if (row.isStop()) "Stopped" else "Started"} at ${displayReviewTime(row.eventTime(), zone)}", style = type.body)
                    Text("You said: “${receipt.reviewText("question").orEmpty()}”", style = type.body, color = colors.onVariant)
                    if (!row.canCorrect()) Text("This event no longer has one editable record. Inspect it in Day.", style = type.bodySmall)
                    else if (row.reviewText("outcome") != "applied") Text("Changed by later activity", style = type.bodySmall, color = colors.onVariant)
                    if (row["changed_since_review"]?.jsonPrimitive?.boolean == true) Text("Changed since you reviewed it", style = type.bodySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { details = row }) { Text("Details") }
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = row.canCorrect() && !busy && !local.offline && !local.pending, onClick = { editText = row.toString() }) { Text("Correct") }
                        if (!reviewed) FilledTonalButton(enabled = !busy && !local.offline && !local.pending, onClick = { acknowledge(row) }) { Text("Looks right") }
                    }
                }
                HorizontalDivider(color = colors.hairline)
            }
            if (page?.reviewText("next_before") != null) item {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try { fetch(more = true) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { busy = false }
                    }
                }) { Text("Earlier activity") }
            }
        }
    }
    details?.let { row ->
        val receipt = row.getValue("receipt").jsonObject
        val original = receipt.getValue("record").jsonObject
        ModalBottomSheet(onDismissRequest = { details = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.sheet, contentColor = colors.on) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Tracking details", style = type.sectionTitle)
                Text("You said: “${receipt.reviewText("question").orEmpty()}”", style = type.body)
                Text("Originally recorded", style = type.label)
                ReviewIdentity(original)
                Text("${if (row.isStop()) "Stopped" else "Started"} at ${displayReviewTime(receipt.reviewText("effective_at") ?: original.reviewText(if (row.isStop()) "end_at" else "start_at"), zone)}", style = type.body)
                row.reviewRecord()?.let { current ->
                    Text("Current activity", style = type.label)
                    ReviewIdentity(current)
                    Text("${displayReviewTime(current.reviewText("start_at"), zone)} → ${current.reviewText("end_at")?.let { displayReviewTime(it, zone) } ?: "Running"}", style = type.body)
                }
                TextButton(onClick = { details = null; onOpenDay(Instant.parse(original.reviewText("start_at")).atZone(zone).toLocalDate()) }) { Text("Open day") }
                TextButton(onClick = { details = null }) { Text("Close") }
            }
        }
    }
    edit?.let { row ->
        ReviewCorrectionSheet(row, page?.get("task_types")?.jsonArray.orEmpty(), zone, controller,
            available = !local.offline && !local.pending, onDismiss = { editText = null; refresh++ },
            onSaved = { notice = "Saved and marked reviewed"; editText = null; refresh++; scope.launch { application.activityRepository.refresh() } })
    }
}

@Composable
private fun ReviewIdentity(record: JsonObject) {
    val category = record.getValue("task_type").jsonObject.reviewText("name").orEmpty()
    val identity = record.reviewText("name")?.takeIf { it.isNotBlank() } ?: category
    Text(identity, style = TimeboxTheme.type.body)
    if (identity != category) Text(category, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewCorrectionSheet(row: JsonObject, types: List<JsonElement>, zone: ZoneId, controller: AssistantController,
    available: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val record = row.getValue("record").jsonObject
    val stop = row.isStop()
    val originalInstant = Instant.parse(record.reviewText(if (stop) "end_at" else "start_at"))
    var name by rememberSaveable { mutableStateOf(record.reviewText("name").orEmpty()) }
    var type by rememberSaveable { mutableIntStateOf(record.getValue("task_type_id").jsonPrimitive.int) }
    var timeLocal by rememberSaveable { mutableStateOf(ActivityTimeValue.from(originalInstant, zone).local) }
    var timeChanged by rememberSaveable { mutableStateOf(false) }
    var occurrence by rememberSaveable { mutableStateOf<String?>(null) }
    val time = if (!timeChanged) ActivityTimeValue.from(originalInstant, zone) else ActivityTimeValue(timeLocal,
        occurrence?.let { com.timebox.android.data.ReportingTime.Occurrence.valueOf(it) })
    var chooseCategory by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingText by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val changed = name != record.reviewText("name").orEmpty() || type != record.getValue("task_type_id").jsonPrimitive.int || timeChanged
    fun dismiss() { if (!busy) { if (changed || pendingText != null) discard = true else onDismiss() } }
    val scope = rememberCoroutineScope()
    val style = TimeboxTheme.type
    val colors = TimeboxTheme.colors
    // After a lost reply, only Retry is offered until the exact operation has a known result.
    val editable = !busy && pendingText == null
    val application = LocalContext.current.applicationContext as TimeboxApplication
    var createdTypesText by rememberSaveable { mutableStateOf("[]") }
    val taskTypes = (types + Json.parseToJsonElement(createdTypesText).jsonArray).map {
        val item = it.jsonObject
        TaskType(item.getValue("id").jsonPrimitive.int, item.reviewText("name").orEmpty(), item["usage_count"]?.jsonPrimitive?.intOrNull ?: 0)
    }.distinctBy { it.id }
    val save: () -> Unit = {
        scope.launch {
            busy = true
            try {
                val body = pendingText?.let { Json.parseToJsonElement(it).jsonObject } ?: run {
                    val at = time.resolve(zone)
                    require(at <= Instant.now()) { "Choose a time no later than now." }
                    if (stop) require(at > Instant.parse(record.reviewText("start_at"))) { "Stopped time must be after this activity started." }
                    else record.reviewText("end_at")?.let { require(at < Instant.parse(it)) { "Later activity ended this record at ${displayReviewTime(it, zone)}. Choose an earlier start." } }
                    buildJsonObject {
                        put("operation_id", UUID.randomUUID().toString()); put("revision", row.getValue("revision"))
                        put("effective_at", at.toString())
                        if (!stop) { put("task_type_id", type); put("name", name.ifBlank { null }) }
                    }.also { pendingText = it.toString() }
                }
                controller.reviewRequest("reviews/${row.reviewText("id")}/correct-event", body)
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // A definitive rejection did not save this request; keep the draft editable.
                // Only an unknown outcome needs an exact, locked retry.
                if (e is ReviewRejectedException) pendingText = null
                error = (e.message ?: "Could not save.") + if (pendingText != null) " Retry to check this save. Closing does not cancel the submitted change." else ""
            }
            finally { busy = false }
        }
    }
    TimeboxSheet(
        lane = Lane.Actual, kicker = "Recorded activity",
        title = if (chooseCategory) "Task Type" else if (stop) "Correct stop" else "Correct activity",
        onDismiss = { if (chooseCategory) chooseCategory = false else dismiss() },
        dismissAllowed = !changed && pendingText == null && !chooseCategory,
        onCancel = { if (chooseCategory) chooseCategory = false else dismiss() },
        busy = busy,
        secondary = if (pendingText != null) SheetSecondary.Action("Close", { dismiss() }) else SheetSecondary.Cancel,
        primary = when {
            busy -> SheetPrimary.Disabled(if (chooseCategory) "Creating…" else "Saving…")
            chooseCategory -> SheetPrimary.Action("Back", { chooseCategory = false })
            !available -> SheetPrimary.Disabled("Waiting for sync")
            else -> SheetPrimary.Action(if (pendingText != null) "Retry save" else "Save & mark reviewed", save)
        },
    ) {
        if (chooseCategory) {
            if (!busy) TaskTypePicker(
                taskTypes = taskTypes, query = search, onQueryChange = { search = it }, selectedTypeId = type,
                onChoose = { type = it.id; chooseCategory = false; error = null },
                onCreate = { path ->
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val created = application.repository.createTaskType(path).getOrThrow()
                            createdTypesText = JsonArray(Json.parseToJsonElement(createdTypesText).jsonArray + buildJsonObject {
                                put("id", created.id); put("name", created.name); put("usage_count", created.usageCount)
                            }).toString()
                            type = created.id
                            chooseCategory = false
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "Could not create Task Type. Try again." }
                        finally { busy = false }
                    }
                },
            )
        } else {
            Text("You said: “${row.getValue("receipt").jsonObject.reviewText("question").orEmpty()}”", style = style.body, color = colors.onVariant)
            if (!stop) {
                OutlinedButton(enabled = editable, onClick = { search = ""; chooseCategory = true },
                    shape = com.timebox.android.ui.theme.TimeboxShapes.field, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("Task Type", style = style.bodySmall)
                        Text(taskTypes.find { it.id == type }?.name ?: record.getValue("task_type").jsonObject.reviewText("name").orEmpty(), style = style.label)
                    }
                    Text("Change")
                }
                SheetTextField(name, { name = it; error = null }, "Block Name", optional = true, enabled = editable,
                    fieldModifier = Modifier.semantics { contentDescription = "Block Name" })
            } else {
                ReviewIdentity(record)
                Text("Started ${displayReviewTime(record.reviewText("start_at"), zone)}", style = style.body)
            }
            Text("Times in $zone", style = style.bodySmall, color = colors.onVariant)
            ActivityTimeField(if (stop) "Stopped at" else "Started at", time, zone, compact = true, enabled = editable) {
                timeLocal = it.local; occurrence = it.occurrence?.name; timeChanged = true; error = null
            }
            if (!available) Text("Reconnect and finish syncing before saving.", style = style.bodySmall)
            if (pendingText != null) Text("The save may have completed. Closing does not cancel the submitted change.", style = style.bodySmall)
        }
        error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = colors.error, style = style.bodySmall) }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(if (pendingText != null) "Close before checking the save?" else "Discard correction?") },
        text = { Text(if (pendingText != null) "The save may have completed. Closing does not cancel it. Return to Review to reload the current result." else "Your unsaved changes will be lost.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(if (pendingText != null) "Keep checking" else "Keep editing") } })
}
