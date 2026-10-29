package com.timebox.android.quicktracking

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import com.timebox.android.ui.assistant.AssistantController
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun QuickFailureReview(controller: AssistantController, history: Boolean, onOpenDay: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TimeboxApplication
    val recovery = app.quickTracking
    val local by recovery.failures.collectAsState()
    val endpoint by recovery.endpoint.collectAsState()
    val capture by recovery.state.collectAsState()
    val preview by recovery.preview.collectAsState()
    val activity by app.activityRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var remote by remember(endpoint, history) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember(endpoint, history) { mutableStateOf<String?>(null) }
    var next by remember(endpoint, history) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val zone = ZoneId.of(activity.snapshot?.reportingTimezone ?: "UTC")
    fun day(at: String) = runCatching { Instant.parse(at).takeUnless { it == Instant.EPOCH }?.atZone(zone)?.toLocalDate() ?: LocalDate.now(zone) }.getOrDefault(LocalDate.now(zone))
    fun time(at: String) = runCatching { Instant.parse(at).takeUnless { it == Instant.EPOCH }?.atZone(zone)?.format(DateTimeFormatter.ofPattern("d MMM · HH:mm")) ?: "Original time unavailable" }.getOrDefault(at)
    suspend fun load(more: Boolean = false) {
        try {
            val page = controller.reviewRequest("quick-failures?history=$history" + if (more && next != null) "&before=$next" else "")
            val rows = page.getValue("items").jsonArray.map { it.jsonObject }
            remote = if (more) remote + rows else rows
            next = page["next_before"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
            error = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not load shared command history. Saved commands on this phone are still available." }
    }
    LaunchedEffect(endpoint, history, refresh) { recovery.refresh(); load() }
    val ownIds = local.filter(recovery::owns).map { it.id }.toSet()
    val sharedIds = remote.map { it.getValue("id").jsonPrimitive.content }.toSet()
    val shown = local.filter { (it.dismissed || it.status in listOf("recorded", "information")) == history }
        .filter { recovery.owns(it) || it.id !in sharedIds }
        .filter { it.history.isNotEmpty() || it.status !in listOf("recorded", "information") || it.stage == "capture" }
        .sortedByDescending { it.occurredAt }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recovery.historyUnavailable) Text("Saved command history could not be read. It has been retained on this phone. Check Day before repeating commands.")
        if (shown.isNotEmpty() || remote.isNotEmpty() || error != null) {
            Text(if (history) "Command history" else "Commands needing attention", style = TimeboxTheme.type.sectionTitle)
            TextButton(onClick = { refresh++ }) { Text("Refresh commands") }
        }
        error?.let { Text(it, style = TimeboxTheme.type.bodySmall) }
        for (record in shown) {
            val owned = recovery.owns(record)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.command?.let { "You said: “$it”" } ?: "No transcript captured", style = TimeboxTheme.type.body)
                Text(time(record.occurredAt), style = TimeboxTheme.type.bodySmall)
                Text(record.reason, style = TimeboxTheme.type.body)
                if (!owned) Text("Retry requires the originating phone and original server.", style = TimeboxTheme.type.bodySmall)
                if (record.status == "uncertain") Text("Waiting to check recording. Reconnect this phone before retrying.", style = TimeboxTheme.type.bodySmall)
                val expired = record.expired(timezone = zone.id)
                if (expired && record.command != null && record.status == "failed") Text("Too old to retry safely. Correct the activity in Day.", style = TimeboxTheme.type.bodySmall)
                if (record.history.isNotEmpty()) {
                    var expanded by remember(record.id) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide attempt history" else "Attempt history") }
                    if (expanded) for (event in record.history) Text("${time(event.at)} · ${event.command ?: "No transcript captured"}\n${event.reason}", style = TimeboxTheme.type.bodySmall)
                }
                if (!history && owned && record.status == "failed") {
                    Row {
                        if (record.retryable && !expired) TextButton(enabled = !capture.busy, onClick = { recovery.retry(record.id) }) { Text("Retry command") }
                        if (record.requestIds.none(app.activityRepository::hasQuickOperation)) TextButton(enabled = !capture.busy, onClick = {
                            try {
                                recovery.speakAgain(record.id)
                                context.startActivity(Intent(context, QuickTrackingActivity::class.java).putExtra("source", "app"))
                            } catch (_: Exception) { error = "Check this command's recording status before speaking again." }
                        }) { Text("Speak again") }
                    }
                }
                Row {
                    TextButton(enabled = record.endpoint == endpoint, onClick = { onOpenDay(day(record.result?.effectiveAt ?: record.occurredAt)) }) { Text(if (record.status == "recorded") "View recorded activity" else "Open Day") }
                    if (!history) TextButton(enabled = !capture.busy, onClick = { recovery.dismiss(record.id) }) { Text("Dismiss") }
                }
            }
            HorizontalDivider()
        }
        for (row in remote.filter { it.getValue("id").jsonPrimitive.content !in ownIds }) {
            fun field(key: String) = row[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
            val id = field("id")!!
            Text(field("command")?.let { "You said: “$it”" } ?: "No transcript captured", style = TimeboxTheme.type.body)
            Text(field("reason").orEmpty(), style = TimeboxTheme.type.bodySmall)
            if (!history) Text(if (field("command") == null) "Speak again on the originating phone." else "Retry on the originating phone. If it is unavailable, correct the activity in Day.", style = TimeboxTheme.type.bodySmall)
            row["history"]?.jsonArray?.forEach { event -> Text(event.jsonObject["reason"]!!.jsonPrimitive.content, style = TimeboxTheme.type.bodySmall) }
            Row {
                TextButton(onClick = { onOpenDay(day(field("recorded_at") ?: field("occurred_at")!!)) }) { Text("Open Day") }
                if (!history) TextButton(onClick = { scope.launch {
                    try { controller.reviewRequest("quick-failures/$id/dismiss", buildJsonObject {}); load() }
                    catch (_: Exception) { error = "Could not dismiss this command. Try again." }
                } }) { Text("Dismiss") }
            }
            HorizontalDivider()
        }
        if (next != null) TextButton(onClick = { scope.launch { load(true) } }) { Text("Earlier commands") }
    }
    preview?.let { proposed ->
        AlertDialog(onDismissRequest = { if (!capture.busy) recovery.cancelPreview() },
            title = { Text("Confirm retry") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(proposed.result.question)
                Text(proposed.title)
                Text(Instant.parse(proposed.result.effectiveAt).atZone(ZoneId.of(proposed.result.timezone)).toString())
                Text("This uses the original command time and may replace recorded activity from that time onward.")
                capture.message?.let { Text(it) }
            } },
            confirmButton = { TextButton(enabled = !capture.busy, onClick = recovery::confirmRetry) { Text("Record activity") } },
            dismissButton = { TextButton(enabled = !capture.busy, onClick = recovery::cancelPreview) { Text("Cancel") } })
    }
}
