package com.timebox.android.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

private fun JsonObject.reviewText(key: String) = get(key)?.jsonPrimitive?.contentOrNull
private val reviewTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun AssistantReviewScreen(controller: AssistantController, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.timebox.android.TimeboxApplication
    val local by application.activityRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<JsonObject?>(null) }
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var reviewed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<JsonObject?>(null) }
    val zone = ZoneId.of(page?.reviewText("timezone") ?: "UTC")
    fun load(more: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val before = if (more) page?.reviewText("next_before") else null
                val data = controller.reviewRequest("reviews?reviewed=$reviewed" + (before?.let { "&before=$it" } ?: ""))
                rows = data.getValue("items").jsonArray.map { it.jsonObject }
                page = data; selected = emptySet(); error = null
            } catch (e: Exception) { error = e.message ?: "Could not load Review. Try again." }
            finally { busy = false }
        }
    }
    LaunchedEffect(reviewed) { page = null; rows = emptyList(); load() }
    LaunchedEffect(local.snapshot?.cursor) { if (!local.pending) load() }
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Review", style = MaterialTheme.typography.titleLarge)
        Text("Already recorded. Check the details when you have a moment.", style = TimeboxTheme.type.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !reviewed, onClick = { if (!busy) reviewed = false }, label = { Text("To review${page?.reviewText("unchecked_count")?.let { " · $it" }.orEmpty()}") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = reviewed, onClick = { if (!busy) reviewed = true }, label = { Text("Reviewed") })
            TextButton(onClick = { load() }, enabled = !busy) { Text("Refresh") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!reviewed && rows.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected.size == rows.size, onCheckedChange = { checked -> selected = if (checked) rows.map { it.getValue("id").jsonPrimitive.int }.toSet() else emptySet() }, enabled = !busy)
            Text("Select loaded", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall)
            TextButton(enabled = selected.isNotEmpty() && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        controller.reviewRequest("reviews/acknowledge", buildJsonObject {
                            putJsonArray("items") { rows.filter { it.getValue("id").jsonPrimitive.int in selected }.forEach { row -> add(buildJsonObject { put("id", row.getValue("id")); put("revision", row.getValue("revision")) }) } }
                        })
                        busy = false; load()
                    } catch (e: Exception) { error = e.message; busy = false }
                }
            }) { Text("Mark reviewed (${selected.size})") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (!reviewed) items(local.quickReviews, key = { it.interpretation.requestId }) { pending ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pending.interpretation.question)
                    Text(if (pending.rejected) "Could not sync · Check Activity Tracking recovery in Day" else "Saved on this device · Unsynced", style = TimeboxTheme.type.bodySmall)
                    Text("${pending.interpretation.effectiveAt} · ${pending.interpretation.timezone}", style = TimeboxTheme.type.bodySmall)
                    TextButton(onClick = { scope.launch { application.activityRepository.refresh(); load() } }) { Text("Check sync") }
                    TextButton(onClick = { onOpenDay(Instant.parse(pending.interpretation.effectiveAt).atZone(ZoneId.of(pending.interpretation.timezone)).toLocalDate()) }) { Text("Open day") }
                    HorizontalDivider(color = TimeboxTheme.colors.hairline)
                }
            }
            if (rows.isEmpty() && !busy && error == null) item { Text(if (reviewed) "No reviewed activity yet." else "All caught up. Activity recorded with Review later will appear here.") }
            items(rows, key = { it.getValue("id").jsonPrimitive.int }) { row ->
                val receipt = row.getValue("receipt").jsonObject
                val record = row["record"]?.takeUnless { it is JsonNull }?.jsonObject
                val original = receipt.getValue("record").jsonObject
                val shown = record ?: original
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!reviewed) Checkbox(checked = row.getValue("id").jsonPrimitive.int in selected, enabled = !busy, onCheckedChange = { checked -> val id = row.getValue("id").jsonPrimitive.int; selected = if (checked) selected + id else selected - id })
                        Text(receipt.reviewText("question").orEmpty(), style = TimeboxTheme.type.body)
                    }
                    Text("${receipt.reviewText("action")?.replaceFirstChar { it.uppercase() }} · ${shown.reviewText("name") ?: shown.getValue("task_type").jsonObject.reviewText("name")}", style = TimeboxTheme.type.body)
                    Text(shown.getValue("task_type").jsonObject.reviewText("name").orEmpty(), style = TimeboxTheme.type.bodySmall)
                    fun time(key: String) = shown.reviewText(key)?.let { Instant.parse(it).atZone(zone).format(reviewTime) } ?: "Still running"
                    Text("${time("start_at")} → ${time("end_at")} · $zone", style = TimeboxTheme.type.bodySmall)
                    receipt.reviewText("effective_at")?.let { Text("Took effect ${Instant.parse(it).atZone(zone).format(reviewTime)}", style = TimeboxTheme.type.bodySmall) }
                    if (row.reviewText("outcome") != "applied") Text("Changed by later activity · Inspect the current record before correcting.")
                    var showOriginal by remember(row.getValue("id")) { mutableStateOf(false) }
                    TextButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "Hide original" else "Originally recorded") }
                    if (showOriginal) Text("${original.reviewText("name") ?: original.getValue("task_type").jsonObject.reviewText("name")} · ${Instant.parse(original.reviewText("start_at")).atZone(zone).format(reviewTime)} → ${original.reviewText("end_at")?.let { Instant.parse(it).atZone(zone).format(reviewTime) } ?: "Running at the time"}", style = TimeboxTheme.type.bodySmall)
                    if (record == null) Text("This activity was removed or split. Inspect it in Day.")
                    if (row["changed_since_review"]?.jsonPrimitive?.boolean == true) Text("Changed since you reviewed it.")
                    Row {
                        TextButton(enabled = record != null && !busy, onClick = { edit = row }) { Text("Correct") }
                        TextButton(onClick = { onOpenDay(Instant.parse(shown.reviewText("start_at")).atZone(zone).toLocalDate()) }) { Text("Open day") }
                    }
                    HorizontalDivider(color = TimeboxTheme.colors.hairline)
                }
            }
            if (page?.reviewText("next_before") != null) item { TextButton(onClick = { load(true) }, enabled = !busy) { Text("Earlier activity") } }
        }
    }
    edit?.let { row -> ReviewCorrectionDialog(row, page?.get("task_types")?.jsonArray.orEmpty(), zone, controller, onDismiss = { edit = null }, onSaved = { edit = null; load(); scope.launch { application.activityRepository.refresh() } }) }
}

@Composable
private fun ReviewCorrectionDialog(row: JsonObject, types: List<JsonElement>, zone: ZoneId, controller: AssistantController, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val record = row.getValue("record").jsonObject
    var name by remember { mutableStateOf(record.reviewText("name").orEmpty()) }
    var type by remember { mutableIntStateOf(record.getValue("task_type_id").jsonPrimitive.int) }
    var start by remember { mutableStateOf(Instant.parse(record.reviewText("start_at")).atZone(zone).format(reviewTime)) }
    var end by remember { mutableStateOf(record.reviewText("end_at")?.let { Instant.parse(it).atZone(zone).format(reviewTime) }) }
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Retain the exact request for network retries. Changed fields get a new operation ID.
    var pending by remember { mutableStateOf<JsonObject?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Correct activity") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Times in $zone · YYYY-MM-DD HH:MM", style = TimeboxTheme.type.bodySmall)
            OutlinedTextField(name, { name = it; pending = null }, label = { Text("Name") }, enabled = !busy)
            Box {
                TextButton(onClick = { menu = true }, enabled = !busy) { Text(types.find { it.jsonObject.getValue("id").jsonPrimitive.int == type }?.jsonObject?.reviewText("name") ?: record.getValue("task_type").jsonObject.reviewText("name").orEmpty()) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { types.forEach { item -> DropdownMenuItem(text = { Text(item.jsonObject.reviewText("name").orEmpty()) }, onClick = { type = item.jsonObject.getValue("id").jsonPrimitive.int; pending = null; menu = false }) } }
            }
            OutlinedTextField(start, { start = it; pending = null }, label = { Text("Start") }, enabled = !busy)
            end?.let { value -> OutlinedTextField(value, { end = it; pending = null }, label = { Text("End") }, enabled = !busy) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        scope.launch {
            busy = true
            try {
                val body = pending ?: buildJsonObject {
                    put("operation_id", UUID.randomUUID().toString()); put("revision", row.getValue("revision"))
                    put("name", name.ifBlank { null }); put("task_type_id", type)
                    put("start_at", if (start == Instant.parse(record.reviewText("start_at")).atZone(zone).format(reviewTime)) record.reviewText("start_at") else LocalDateTime.parse(start, reviewTime).atZone(zone).toInstant().toString())
                    put("end_at", end?.let { if (it == Instant.parse(record.reviewText("end_at")).atZone(zone).format(reviewTime)) record.reviewText("end_at") else LocalDateTime.parse(it, reviewTime).atZone(zone).toInstant().toString() })
                }.also { pending = it }
                controller.reviewRequest("reviews/${row.reviewText("id")}/correct", body); onSaved()
            } catch (e: Exception) { error = e.message ?: "Could not save. Check times and retry." }
            finally { busy = false }
        }
    }) { Text(if (busy) "Saving…" else "Save correction") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}
