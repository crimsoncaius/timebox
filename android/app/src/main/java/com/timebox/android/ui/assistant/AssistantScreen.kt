package com.timebox.android.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@Composable
fun AssistantScreen(controller: AssistantController = (LocalContext.current.applicationContext as TimeboxApplication).assistant) {
    val state by controller.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    var contextOpen by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val largeText = LocalDensity.current.fontScale > 1.3f
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (scrolling, more) ->
            if (scrolling) follow = !more
        }
    }
    LaunchedEffect(state.exchanges.size, state.exchanges.lastOrNull(), state.readingPlan) {
        if (state.exchanges.isEmpty()) list.scrollToItem(0)
        else if (follow && list.layoutInfo.totalItemsCount > 0) list.scrollToItem(list.layoutInfo.totalItemsCount - 1)
    }
    fun reset() { controller.newConversation(); draft = ""; follow = true }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Assistant", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            if (largeText) IconButton(onClick = ::reset) { Icon(Icons.Outlined.Add, contentDescription = "New conversation") }
            else OutlinedButton(onClick = ::reset) { Text("New conversation") }
        }
        TextButton(onClick = { contextOpen = !contextOpen }, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(if (contextOpen) "▾ Temporary conversation" else "▸ Temporary conversation", style = MaterialTheme.typography.labelMedium)
        }
        if (contextOpen) Text("60 minutes of inactivity · up to 20 completed exchanges. New conversation or an app restart clears this view and Assistant memory.", Modifier.padding(horizontal = 18.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (state.exchanges.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Text("A little clarity for today", style = MaterialTheme.typography.headlineMedium)
                    Text("Ask about your Planned Blocks. Assistant can read your plan, but can’t change it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("Show today’s plan", "Do I have a 30-minute gap?", "Help me think through my morning").forEach { prompt ->
                        FilledTonalButton(onClick = { draft = prompt }, modifier = Modifier.fillMaxWidth()) { Text(prompt) }
                    }
                }
            }
            itemsIndexed(state.exchanges) { index, exchange ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.padding(start = 36.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                        Text(exchange.question, Modifier.padding(14.dp).semantics { contentDescription = "You: ${exchange.question}" })
                    }
                    Text("ASSISTANT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    exchange.plan?.let { PlanCard(it) }
                    if (exchange.answer.isNotEmpty()) SelectionContainer { AnswerText(exchange.answer) }
                    if (index == state.exchanges.lastIndex && state.busy) Text(
                        if (state.readingPlan) "Reading today’s plan…" else if (exchange.answer.isEmpty() && exchange.plan == null) "Thinking…" else "Writing response…",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (exchange.status in listOf("Stopped", "Interrupted")) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(exchange.status, style = MaterialTheme.typography.labelLarge)
                            exchange.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text("This attempt won’t be used in later answers.", style = MaterialTheme.typography.bodySmall)
                            if (index == state.exchanges.lastIndex && !state.busy && state.ended == null) TextButton(onClick = { follow = true; controller.retry() }) { Text("Retry response") }
                        }
                    }
                }
            }
            state.ended?.let { reason -> item { Text(reason, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
            item { Spacer(Modifier.height(1.dp)) }
        }
        if (state.exchanges.isNotEmpty() && list.canScrollForward) TextButton(onClick = { follow = true; scope.launch { list.animateScrollToItem(list.layoutInfo.totalItemsCount - 1) } }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("↓ Jump to latest") }
        if (state.ended != null) Button(onClick = ::reset, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("New conversation") }
        else Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 4000) draft = it },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Message Assistant" }, placeholder = { Text("Ask Assistant") }, label = { Text("Message") }, maxLines = 5,
                shape = MaterialTheme.shapes.large, supportingText = if (draft.length >= 3600) ({ Text("${draft.length}/4000") }) else null)
            if (state.busy) FilledTonalButton(onClick = controller::stop) { Text("Stop") }
            else Button(enabled = draft.isNotBlank(), onClick = { follow = true; controller.send(draft); draft = "" }) { Text("Send") }
        }
    }
}

@Composable
private fun PlanCard(plan: AssistantPlan) {
    var expanded by rememberSaveable(plan.id) { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale > 1.3f
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Plan · ${plan.date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            Text("${plan.rows.size} Planned Blocks", style = MaterialTheme.typography.bodySmall)
            Text("Read at ${plan.readAt.atZone(plan.zone).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${plan.zone.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (plan.rows.isEmpty()) Text("No Planned Blocks for this date.")
            (if (expanded) plan.rows else plan.rows.take(3)).forEach { row ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                if (largeText) Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${clock(row.start)}–${clock(row.end)}", style = MaterialTheme.typography.labelMedium)
                    PlanRowTitle(row)
                } else Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${clock(row.start)}–${clock(row.end)}", Modifier.width(92.dp), style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.weight(1f)) { PlanRowTitle(row) }
                }
            }
            if (plan.rows.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show fewer" else "Show all ${plan.rows.size} blocks") }
        }
    }
}

@Composable
private fun PlanRowTitle(row: AssistantPlanRow) {
    Text(row.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Text(row.taskType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun clock(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

/** Deliberately small formatting vocabulary: no HTML, links, images or remote content. */
@Composable
private fun AnswerText(text: String) {
    val annotated = remember(text) { buildAnnotatedString {
        val pattern = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*")
        text.lines().forEachIndexed { lineIndex, raw ->
            if (lineIndex > 0) append("\n")
            val line = if (raw.startsWith("- ")) "• " + raw.drop(2) else raw
            var offset = 0
            pattern.findAll(line).forEach { match ->
                append(line.substring(offset, match.range.first))
                val bold = match.groups[1] != null
                withStyle(if (bold) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[if (bold) 1 else 2]) }
                offset = match.range.last + 1
            }
            append(line.substring(offset))
        }
    } }
    Text(annotated, style = MaterialTheme.typography.bodyLarge)
}
