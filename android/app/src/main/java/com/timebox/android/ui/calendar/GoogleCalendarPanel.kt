package com.timebox.android.ui.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.apiError
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun JsonObject.s(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.b(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull == true
internal fun JsonObject.objects(key: String) = (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
internal fun JsonObject.strings(key: String) = (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
private fun command(kind: String, id: String? = null, extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject { put("kind", kind); id?.let { put("id", it) }; extra() }
internal fun stamp(value: String?, zone: String): String = value?.let { runCatching { Instant.parse(it).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) }.getOrDefault(it) }.orEmpty()

@Composable
fun GoogleCalendarPanel(date: String? = null, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as? TimeboxApplication ?: return
    val repository = app.repository
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChanged)
    var state by remember(date) { mutableStateOf<JsonObject?>(null) }
    var error by remember(date) { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf<String?>(null) }
    val busy = operation != null
    var loadError by remember { mutableStateOf<String?>(null) }
    var awaitingGoogle by remember { mutableStateOf(false) }
    var reloadVersion by remember { mutableIntStateOf(0) }
    var opened by remember(date) { mutableStateOf(false) }
    var updatesOpen by remember(date) { mutableStateOf(false) }
    var selected by remember { mutableStateOf<List<String>?>(null) }
    var detail by remember { mutableStateOf<JsonObject?>(null) }
    var preview by remember { mutableStateOf<Pair<JsonObject, JsonObject>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun reload() {
        val version = ++reloadVersion
        val next = repository.googleCalendar(date)
        if (version != reloadVersion) return
        val oldSync = state?.s("last_success")
        if (state?.s("account") != next.s("account") || !next.b("connected")) selected = null
        state = next
        loadError = null
        if (next.b("connected") && next.s("error") == null) awaitingGoogle = false
        if (selected == next.strings("selected")) selected = null
        if (oldSync != null && oldSync != next.s("last_success")) changed()
    }
    fun action(cmd: JsonObject, fingerprint: String? = null) {
        if (operation != null) return
        scope.launch {
            operation = if (fingerprint == null) "Preparing review…" else "Applying changes…"; error = null
            reloadVersion++
            try {
                val result = repository.googleCalendarAction("action", buildJsonObject { put("command", cmd); fingerprint?.let { put("fingerprint", it) } })
                if (fingerprint == null) preview = cmd to result
                else {
                    preview = null; detail = null; selected = null
                    // Action snapshots omit server configuration; preserve the status endpoint's value.
                    val configured = state?.b("configured") == true
                    state = (result["state"] as? JsonObject ?: state)?.let { JsonObject(it + ("configured" to JsonPrimitive(configured))) }
                    changed(); message = "Calendar plan updated"
                    try { reload() } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { loadError = calendarFailure(e) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = calendarFailure(e); if (fingerprint != null) preview = null }
            finally { operation = null }
        }
    }
    LaunchedEffect(date) {
        while (true) {
            if (operation == null) {
                try { reload() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { loadError = calendarFailure(e) }
            }
            delay(30_000)
        }
    }
    fun retry(refresh: Boolean = false) {
        if (operation != null) return
        scope.launch {
            operation = if (refresh) "Syncing calendars…" else "Checking connection…"
            if (refresh) error = null
            try {
                if (refresh) repository.googleCalendarAction("refresh")
                reload()
                if (refresh) changed()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (refresh) error = calendarFailure(e) else loadError = calendarFailure(e)
            } finally { operation = null }
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, date) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) retry()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun connect() {
        if (operation != null) return
        scope.launch {
            operation = "Opening Google…"; error = null
            try {
                val result = repository.googleCalendarAction("connect")
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.s("url"))))
                awaitingGoogle = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = calendarFailure(e) }
            finally { operation = null }
        }
    }
    LaunchedEffect(message) {
        if (message != null) { delay(6_000); message = null }
    }
    val current = state
    val zone = current?.s("timezone") ?: "UTC"
    val occurrences = current?.objects("occurrences").orEmpty()
    val pending = occurrences.filter { it.s("status") == "pending" }
    val updates = occurrences.filter { it.objects("updates").isNotEmpty() }
    if (date != null && pending.isEmpty() && updates.isEmpty() && error == null && loadError == null && current?.s("error") == null) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (date == null) {
            GoogleCalendarSettings(
                state = current, busy = operation, loadError = loadError, actionError = error,
                awaitingGoogle = awaitingGoogle, selected = selected, onSelect = { selected = it },
                onConnect = ::connect, onRefresh = { retry(true) }, onRetry = { retry() },
                onReview = { action(command("select") { put("selected", JsonArray((selected ?: current?.strings("selected").orEmpty()).map(::JsonPrimitive))) }) },
                onDisconnect = { action(command("disconnect")) }, onForget = { action(command("forget")) },
                onDismissError = { error = null },
            )
        } else {
            (error ?: loadError ?: current?.s("error"))?.let { Text(it, color = TimeboxTheme.colors.error, style = TimeboxTheme.type.bodySmall) }
            if (error != null || loadError != null || current?.s("error") != null) TextButton(enabled = !busy, onClick = { retry(current?.b("connected") == true) }) { Text("Try again") }
        }
        message?.let { Text(it, style = TimeboxTheme.type.bodySmall) }
        if (pending.isNotEmpty()) TextButton(onClick = { opened = !opened }) { Text(if (opened) "Hide meetings needing attention" else "${pending.size} ${if (pending.size == 1) "meeting needs" else "meetings need"} attention") }
        if (date == null && occurrences.any { it.s("status") == "excluded" } && pending.isEmpty()) TextButton(onClick = { opened = !opened }) { Text(if (opened) "Hide meetings left out" else "Meetings left out") }
        if (opened) occurrences.filter { it.s("status") in listOf("pending", "excluded") }.forEach { occurrence ->
            TextButton(onClick = { detail = occurrence }) { Text("${occurrence.s("name")} · ${stamp(occurrence.s("start"), zone)} · ${if (occurrence.s("status") == "excluded") "Left out" else "Needs room"}") }
        }
        if (date != null && updates.isNotEmpty()) {
            TextButton(onClick = { updatesOpen = !updatesOpen }) { Text("Calendar updates") }
            if (updatesOpen) updates.forEach { occurrence ->
                Text(occurrence.s("name").orEmpty(), style = TimeboxTheme.type.body)
                occurrence.objects("updates").forEach { Text("${it.s("kind")} · ${stamp(it.s("at"), zone)}", style = TimeboxTheme.type.bodySmall) }
            }
        }
    }
    detail?.let { occurrence -> CalendarDialog(onDismiss = { if (!busy) detail = null }) {
        CalendarDetails(occurrence, current ?: JsonObject(emptyMap()), busy, onAction = { action(it) }, onClose = { detail = null })
    } }
    preview?.let { (cmd, result) -> CalendarDialog(onDismiss = { if (!busy) preview = null }) {
        error?.let { Text(it, color = TimeboxTheme.colors.error, style = TimeboxTheme.type.bodySmall) }
        Text("Review calendar changes", style = TimeboxTheme.type.sectionTitle)
        Text("Google events and recorded activity stay unchanged. Plan edits apply together.", style = TimeboxTheme.type.bodySmall)
        if (cmd.s("kind") in listOf("disconnect", "forget")) Text("Disconnect Google and remove future imported plans." + if (cmd.s("kind") == "forget") " Also forget cached details and remembered choices; historical plan identities remain protected." else "")
        if (cmd.s("kind") == "exclude") Text("Leave this occurrence out until Google changes its scheduled interval.")
        if (cmd.s("kind") == "metadata") Text("Save the selected classification, Task linkage, and Supporting Note.")
        val before = result.objects("before")
        val after = result.objects("after")
        val removed = before.filter { old -> after.find { it.s("id") == old.s("id") } != old }
        val added = after.filter { new -> before.find { it.s("id") == new.s("id") } != new }
        if (removed.isNotEmpty()) { Text("Before"); removed.forEach { Text("${it.s("name") ?: "Unnamed Planned Block"} · ${stamp(it.s("start_at"), zone)} – ${stamp(it.s("end_at"), zone)}", style = TimeboxTheme.type.bodySmall) } }
        if (added.isNotEmpty()) { Text("After"); added.forEach { Text("${it.s("name") ?: "Unnamed Planned Block"} · ${stamp(it.s("start_at"), zone)} – ${stamp(it.s("end_at"), zone)}", style = TimeboxTheme.type.bodySmall) } }
        val count = (result["state"] as? JsonObject)?.objects("occurrences")?.count { it.s("status") == "pending" } ?: 0
        Text("$count pending conflicts after this change.")
        Row { TextButton(enabled = !busy, onClick = { preview = null }) { Text("Back") }; Button(enabled = !busy, onClick = { action(cmd, result.s("fingerprint")) }) { Text(if (busy) "Applying…" else "Confirm changes") } }
    } }
}

@Composable
private fun CalendarDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) { Surface(shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    } }
}

@Composable
private fun CalendarDetails(o: JsonObject, state: JsonObject, busy: Boolean, onAction: (JsonObject) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as TimeboxApplication).repository
    val zone = state.s("timezone") ?: "UTC"
    val id = o.s("id")!!
    var note by remember(id) { mutableStateOf(o.s("note").orEmpty()) }
    var typeId by remember(id) { mutableStateOf(o.s("task_type_id")) }
    var taskId by remember(id) { mutableStateOf(o.s("task_id")) }
    var types by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var edits by remember(id) { mutableStateOf<Map<String, JsonObject>>(emptyMap()) }
    var exclusions by remember(id) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        try {
            types = repository.listTaskTypes().getOrThrow().map { it.id.toString() to it.name }
            tasks = repository.listBattleTasks().getOrThrow().items.flatMap { listOf(it) + it.sessionTasks }.map { it.id.toString() to it.title }
        } catch (e: Exception) { error = e.apiError.message }
    }
    Text(o.s("name").orEmpty(), style = TimeboxTheme.type.sectionTitle)
    Text("${stamp(o.s("start"), zone)} – ${stamp(o.s("end"), zone)}")
    o.strings("previous").takeIf { it.size == 2 }?.let { Text("Previously ${stamp(it[0], zone)} – ${stamp(it[1], zone)}", style = TimeboxTheme.type.bodySmall) }
    Text("Google Calendar · ${state.objects("calendars").find { it.s("id") == o.s("calendar_id") }?.s("name") ?: o.s("calendar_id")}", style = TimeboxTheme.type.bodySmall)
    o.s("url")?.let { url -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("Open source event") } }
    o.objects("overlaps").forEach { block ->
        val blockId = block.s("id")!!
        var operation by remember(blockId) { mutableStateOf("") }
        var start by remember(blockId) { mutableStateOf(Instant.parse(block.s("start")).atZone(ZoneId.of(zone)).toLocalDateTime().toString()) }
        var end by remember(blockId) { mutableStateOf(Instant.parse(block.s("end")).atZone(ZoneId.of(zone)).toLocalDateTime().toString()) }
        fun update() { edits = edits + (blockId to buildJsonObject { put("id", blockId.toInt()); put("action", operation); put("start", start); put("end", end) }) }
        Text(block.s("name") ?: "Unnamed Planned Block", style = TimeboxTheme.type.body)
        Text("${stamp(block.s("start"), zone)} – ${stamp(block.s("end"), zone)}", style = TimeboxTheme.type.bodySmall)
        val options = listOf("" to "Keep unchanged", "remove" to if (block.s("occurrence_id") != null) "Leave this meeting out" else "Remove Planned Block") + if (block.s("occurrence_id") == null) listOf("move" to "Move or shorten") else emptyList()
        Choice("Make room", operation, options) { operation = it.orEmpty(); update() }
        if (operation == "move") {
            OutlinedTextField(start, { start = it; update() }, label = { Text("Start in $zone") }, supportingText = { Text("YYYY-MM-DDTHH:MM") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { end = it; update() }, label = { Text("End in $zone") }, modifier = Modifier.fillMaxWidth())
        }
    }
    o.strings("competing_ids").forEach { peer -> Row {
        Checkbox(checked = peer in exclusions, onCheckedChange = { exclusions = if (it) exclusions + peer else exclusions - peer })
        Text("Leave out ${state.objects("occurrences").find { it.s("id") == peer }?.s("name") ?: "the other meeting"}", modifier = Modifier.padding(top = 12.dp))
    } }
    Button(enabled = !busy && state.b("connected"), onClick = {
        try {
            val normalized = edits.values.filter { !it.s("action").isNullOrEmpty() }.map { e -> buildJsonObject {
                put("id", e.s("id")!!.toInt()); put("action", e.s("action")!!)
                if (e.s("action") == "move") {
                    fun instant(key: String): String {
                        val local = LocalDateTime.parse(e.s(key)); val zoneId = ZoneId.of(zone)
                        require(zoneId.rules.getValidOffsets(local).isNotEmpty()) { "That time does not exist in $zone. Choose another time." }
                        return local.atZone(zoneId).toInstant().toString()
                    }
                    put("start", instant("start")); put("end", instant("end"))
                }
            } }
            onAction(command(if (o.s("status") == "excluded") "include" else "resolve", id) { put("edits", JsonArray(normalized)); put("exclude_ids", JsonArray(exclusions.map(::JsonPrimitive))) })
        } catch (e: Exception) { error = e.message ?: "Enter valid start and end times." }
    }) { Text(if (o.s("status") == "excluded") "Include again" else "Preview making room") }
    if (o.s("status") != "excluded") TextButton(enabled = !busy, onClick = { onAction(command("exclude", id)) }) { Text("Leave this meeting out") }
    TextButton(onClick = onClose) { Text("Resolve later") }
    HorizontalDivider()
    Text("Local classification and notes", style = TimeboxTheme.type.body)
    Choice("Task Type", typeId, listOf("" to "unspecified") + types) { typeId = it?.ifEmpty { null } }
    Choice("Battle Plan Task", taskId, listOf("" to "No linked Task") + tasks) { taskId = it?.ifEmpty { null } }
    OutlinedTextField(note, { note = it }, label = { Text("Supporting Note") }, modifier = Modifier.fillMaxWidth())
    TextButton(enabled = !busy, onClick = { onAction(command("metadata", id) { put("task_type_id", typeId?.toInt()?.let(::JsonPrimitive) ?: JsonNull); put("task_id", taskId?.toInt()?.let(::JsonPrimitive) ?: JsonNull); put("note", note) }) }) { Text("Preview local changes") }
    error?.let { Text(it, color = TimeboxTheme.colors.error) }
}

@Composable
private fun Choice(label: String, value: String?, options: List<Pair<String, String>>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("$label: ${options.find { it.first == value.orEmpty() }?.second ?: "Choose"}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { open = false; onSelect(id) }) } }
    }
}
