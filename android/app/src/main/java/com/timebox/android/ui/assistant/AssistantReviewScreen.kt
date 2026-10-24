package com.timebox.android.ui.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantReviewScreen(controller: AssistantController, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as TimeboxApplication
    val local by application.activityRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<JsonObject?>(null) }
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf<String?>(null) }
    val edit = editText?.let { Json.parseToJsonElement(it).jsonObject }
    var details by remember { mutableStateOf<JsonObject?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
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
        catch (e: Exception) { error = e.message ?: "Could not load Review. Try Refresh." }
        finally { busy = false }
    }
    fun acknowledge(row: JsonObject) {
        busy = true
        scope.launch {
            try {
                controller.reviewRequest("reviews/acknowledge", buildJsonObject {
                    putJsonArray("items") { add(buildJsonObject { put("id", row.getValue("id")); put("revision", row.getValue("revision")) }) }
                })
                refresh++
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not mark reviewed. Refresh and retry." }
            finally { busy = false }
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Review", style = type.sectionTitle)
        Text("Already recorded. Compare the activity and time with what you said.", style = type.body, color = colors.onVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!reviewed, { if (!busy && reviewed) { reviewed = false; rows = emptyList(); page = null } }, label = { Text("To review${page?.reviewText("unchecked_count")?.let { " · $it" }.orEmpty()}") })
            FilterChip(reviewed, { if (!busy && !reviewed) { reviewed = true; rows = emptyList(); page = null } }, label = { Text("Reviewed") })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Times in $zone", Modifier.weight(1f), style = type.bodySmall, color = colors.onVariant)
            TextButton(enabled = !busy, onClick = { refresh++; scope.launch { application.activityRepository.refresh() } }) { Text("Refresh") }
        }
        if (local.offline) Text("Offline · Reconnect to save corrections or mark reviewed.", style = type.bodySmall)
        if (local.pending) Text("Syncing activity. Corrections are available after sync.", style = type.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = type.bodySmall) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
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
                    Text(if (reviewed) "Nothing reviewed yet" else "You're caught up", style = type.sectionTitle)
                    Text(if (reviewed) "Activities you mark reviewed will appear here." else "Your next recorded activity will appear here for a quick check.", style = type.body, color = colors.onVariant)
                }
            }
            items(rows, key = { it.getValue("id").jsonPrimitive.int }) { row ->
                val receipt = row.getValue("receipt").jsonObject
                val shown = row.reviewRecord() ?: receipt.getValue("record").jsonObject
                val category = shown.getValue("task_type").jsonObject.reviewText("name").orEmpty()
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (row.isStop()) "Stopped tracking" else shown.reviewText("name") ?: category, style = type.sectionTitle)
                    Text(if (row.isStop()) shown.reviewText("name")?.let { "$it · $category" } ?: category else category, style = type.bodySmall, color = colors.onVariant)
                    Text("${if (row.isStop()) "Stopped" else "Started"} at ${displayReviewTime(row.eventTime(), zone)}", style = type.body)
                    Text("You said: “${receipt.reviewText("question").orEmpty()}”", style = type.body, color = colors.onVariant)
                    if (!row.canCorrect()) Text("This event no longer has one editable record. Inspect it in Day.", style = type.bodySmall)
                    else if (row.reviewText("outcome") != "applied") Text("Changed by later activity", style = type.bodySmall, color = colors.onVariant)
                    if (row["changed_since_review"]?.jsonPrimitive?.boolean == true) Text("Changed since you reviewed it", style = type.bodySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { details = row }) { Text("Details") }
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = row.canCorrect() && !busy && !local.offline && !local.pending, onClick = { editText = row.toString() }) { Text("Correct") }
                        if (!reviewed) TextButton(enabled = !busy && !local.offline && !local.pending, onClick = { acknowledge(row) }) { Text("Looks right") }
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
        ModalBottomSheet(onDismissRequest = { details = null }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Tracking details", style = type.sectionTitle)
                Text("You said: “${receipt.reviewText("question").orEmpty()}”", style = type.body)
                Text("Originally recorded", style = type.label)
                Text(original.getValue("task_type").jsonObject.reviewText("name").orEmpty(), style = type.body)
                Text("${if (row.isStop()) "Stopped" else "Started"} at ${displayReviewTime(receipt.reviewText("effective_at") ?: original.reviewText(if (row.isStop()) "end_at" else "start_at"), zone)}", style = type.body)
                row.reviewRecord()?.let { current ->
                    Text("Current activity record", style = type.label)
                    Text("${displayReviewTime(current.reviewText("start_at"), zone)} → ${current.reviewText("end_at")?.let { displayReviewTime(it, zone) } ?: "Running"}", style = type.body)
                }
                TextButton(onClick = { details = null; onOpenDay(Instant.parse(original.reviewText("start_at")).atZone(zone).toLocalDate()) }) { Text("Open day") }
                TextButton(onClick = { details = null }) { Text("Close") }
            }
        }
    }
    edit?.let { row ->
        ReviewCorrectionSheet(row, page?.get("task_types")?.jsonArray.orEmpty(), zone, controller,
            available = !local.offline && !local.pending, onDismiss = { editText = null },
            onSaved = { editText = null; refresh++; scope.launch { application.activityRepository.refresh() } })
    }
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
    ModalBottomSheet(onDismissRequest = { dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || (!busy && !changed && pendingText == null) })) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (chooseCategory) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Activity category", Modifier.weight(1f), style = style.sectionTitle)
                    TextButton(onClick = { chooseCategory = false }) { Text("Back") }
                }
                OutlinedTextField(search, { search = it }, label = { Text("Search categories") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val matches = types.filter { it.jsonObject.reviewText("name").orEmpty().contains(search, ignoreCase = true) }
                if (matches.isEmpty()) Text("No matching category. Try another search.", style = style.body)
                matches.forEach { item ->
                    val id = item.jsonObject.getValue("id").jsonPrimitive.int
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { type = id; chooseCategory = false; error = null }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(type == id, onClick = null)
                        Text(item.jsonObject.reviewText("name").orEmpty(), style = style.body)
                    }
                }
            } else {
                Text(if (stop) "Correct stop" else "Correct activity", style = style.sectionTitle)
                Text("You said: “${row.getValue("receipt").jsonObject.reviewText("question").orEmpty()}”", style = style.body, color = colors.onVariant)
                if (!stop) OutlinedButton(enabled = editable, onClick = { search = ""; chooseCategory = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("Category", style = style.bodySmall)
                        Text(types.find { it.jsonObject.getValue("id").jsonPrimitive.int == type }?.jsonObject?.reviewText("name") ?: record.getValue("task_type").jsonObject.reviewText("name").orEmpty(), style = style.label)
                    }
                    Text("Change")
                } else Text("${record.reviewText("name") ?: record.getValue("task_type").jsonObject.reviewText("name")} · Started ${displayReviewTime(record.reviewText("start_at"), zone)}", style = style.body)
                Text("Times in $zone", style = style.bodySmall, color = colors.onVariant)
                ActivityTimeField(if (stop) "Stopped at" else "Started at", time, zone, compact = true, enabled = editable) {
                    timeLocal = it.local; occurrence = it.occurrence?.name; timeChanged = true; error = null
                }
                if (!stop) OutlinedTextField(name, { name = it; error = null }, label = { Text("Activity name (optional)") }, enabled = editable, modifier = Modifier.fillMaxWidth())
                Text("Saving also marks this activity reviewed.", style = style.bodySmall)
                if (!available) Text("Reconnect and finish syncing before saving.", style = style.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = style.bodySmall) }
                Button(enabled = !busy && available, modifier = Modifier.fillMaxWidth(), onClick = {
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
                            error = (e.message ?: "Could not save.") + if (pendingText != null) " Retry to check this save, or close and refresh Review." else ""
                        }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Saving…" else if (pendingText != null) "Retry save" else "Save correction") }
                TextButton(enabled = !busy, onClick = { dismiss() }) { Text("Close") }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(if (pendingText != null) "Close before checking the save?" else "Discard correction?") },
        text = { Text(if (pendingText != null) "The save may have completed. Refresh Review before making another correction." else "Your unsaved changes will be lost.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}
