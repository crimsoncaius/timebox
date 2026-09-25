package com.timebox.android.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.TimeboxApplication
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.theme.TimeboxShapes
import java.time.LocalDate
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@Composable
fun AssistantScreen(controller: AssistantController = (LocalContext.current.applicationContext as TimeboxApplication).assistant) {
    val state by controller.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val composerFocus = remember { FocusRequester() }
    val colors = TimeboxTheme.colors
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Assistant", Modifier.semantics { heading() }, style = TimeboxTheme.type.screenTitle, color = colors.on)
            }
            OutlinedIconButton(onClick = ::reset, shape = CircleShape, colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = colors.card), border = BorderStroke(1.dp, colors.hairline)) {
                Icon(Icons.Outlined.Add, contentDescription = "New conversation")
            }
        }
        HorizontalDivider(color = colors.hairline)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (state.exchanges.isEmpty()) item {
                AssistantWelcome { draft = it; composerFocus.requestFocus() }
            }
            itemsIndexed(state.exchanges) { index, exchange ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.padding(start = 36.dp).align(Alignment.End), color = colors.card, shape = RoundedCornerShape(16.dp, 16.dp, 3.dp, 16.dp), border = BorderStroke(1.dp, colors.hairline)) {
                        Text(exchange.question, Modifier.padding(horizontal = 14.dp, vertical = 11.dp).semantics { contentDescription = "You: ${exchange.question}" }, style = TimeboxTheme.type.body)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = colors.onVariant)
                        Text("Assistant", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    }
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
                    if (index == state.exchanges.lastIndex && exchange.status == "Complete" && !state.busy && state.ended == null) {
                        OutlinedButton(onClick = { draft = "Help me think through my morning"; composerFocus.requestFocus() }, border = BorderStroke(1.dp, colors.hairline)) {
                            Text("Think through my morning", Modifier.weight(1f, fill = false), style = TimeboxTheme.type.bodySmall)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Outlined.NorthEast, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
            state.ended?.let { reason -> item { Text(reason, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
            item { Spacer(Modifier.height(1.dp)) }
        }
        if (state.exchanges.isNotEmpty() && list.canScrollForward) TextButton(onClick = { follow = true; scope.launch { list.animateScrollToItem(list.layoutInfo.totalItemsCount - 1) } }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("↓ Jump to latest") }
        if (state.ended != null) Button(onClick = ::reset, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("New conversation") }
        else AssistantComposer(draft, { if (it.length <= 4000) draft = it }, composerFocus, state.busy,
            if (state.exchanges.isEmpty()) "What’s on your mind?" else "Ask a follow-up…",
            controller::stop, { follow = true; controller.send(draft); draft = "" })
    }
}

@Composable
private fun AssistantWelcome(onPrompt: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Surface(shape = CircleShape, border = BorderStroke(1.dp, colors.hairline), color = colors.bg) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(12.dp).size(24.dp), tint = colors.onVariant)
        }
        Text("A LITTLE CLARITY", style = TimeboxTheme.type.kicker, color = colors.onVariant)
        Text("Make room\nfor your day.", style = TimeboxTheme.type.display, color = colors.on)
        Text("Think through your Planned Blocks.\nAssistant can read your plan, but can’t change it.", style = TimeboxTheme.type.body, color = colors.onVariant)
        Column(Modifier.padding(top = 8.dp)) {
            listOf("Show today’s plan", "Do I have a 30-minute gap?", "Help me think through my morning").forEachIndexed { index, prompt ->
                HorizontalDivider(color = colors.hairline)
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Use prompt", onClick = { onPrompt(prompt) }).heightIn(min = 56.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("0${index + 1}", style = TimeboxTheme.type.mono, color = colors.onVariant)
                    Text(prompt, Modifier.weight(1f), style = TimeboxTheme.type.body, color = colors.on)
                    Icon(Icons.Outlined.NorthEast, null, Modifier.size(18.dp), tint = colors.onVariant)
                }
            }
        }
    }
}

@Composable
private fun AssistantComposer(draft: String, onDraft: (String) -> Unit, focus: FocusRequester, busy: Boolean, placeholder: String, onStop: () -> Unit, onSend: () -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), color = colors.field, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, colors.hairline)) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                BasicTextField(value = draft, onValueChange = onDraft,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).focusRequester(focus).semantics { contentDescription = "Message Assistant" },
                    textStyle = TimeboxTheme.type.body.copy(color = colors.on), cursorBrush = SolidColor(colors.on), maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { field -> Box { if (draft.isEmpty()) Text(placeholder, style = TimeboxTheme.type.body, color = colors.onVariant); field() } })
                if (draft.length >= 3600) Text("${draft.length}/4000", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            FilledIconButton(onClick = if (busy) onStop else onSend, enabled = busy || draft.isNotBlank(), modifier = Modifier.size(48.dp), shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.on, contentColor = colors.bg, disabledContainerColor = colors.disabledContainer, disabledContentColor = colors.disabledContent)) {
                Icon(if (busy) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward, contentDescription = if (busy) "Stop" else "Send")
            }
        }
    }
}

@Composable
private fun PlanCard(plan: AssistantPlan) {
    var expanded by rememberSaveable(plan.id) { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale > 1.3f
    val colors = TimeboxTheme.colors
    Surface(color = colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val title = if (plan.date == LocalDate.now(plan.zone)) "Today’s plan" else "Plan"
            if (largeText) {
                Text(title, Modifier.semantics { heading() }, style = TimeboxTheme.type.label)
                Text("${plan.rows.size} Planned Blocks", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).semantics { heading() }, style = TimeboxTheme.type.label)
                Text("${plan.rows.size} blocks", Modifier.semantics { contentDescription = "${plan.rows.size} Planned Blocks" }, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            Text("${plan.date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · Read ${plan.readAt.atZone(plan.zone).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${plan.zone.id}", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            if (plan.rows.isEmpty()) Text("No Planned Blocks for this date.", style = TimeboxTheme.type.body)
            (if (expanded) plan.rows else plan.rows.take(3)).forEach { row ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.hairline)
                if (largeText) Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${clock(row.start)}–${clock(row.end)}", style = TimeboxTheme.type.mono.copy(fontSize = 12.sp), color = colors.planned)
                    PlanRowTitle(row)
                } else Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(48.dp)) {
                        Text(clock(row.start), style = TimeboxTheme.type.mono.copy(fontSize = 12.sp), color = colors.planned)
                        Text(clock(row.end), style = TimeboxTheme.type.mono.copy(fontSize = 11.sp), color = colors.onVariant)
                    }
                    Column(Modifier.weight(1f)) { PlanRowTitle(row) }
                }
            }
            if (plan.rows.size > 3) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Show fewer" else "Show all ${plan.rows.size} blocks", Modifier.weight(1f), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                Text(if (expanded) "−" else "+", color = colors.onVariant)
            }
        }
    }
}

@Composable
private fun PlanRowTitle(row: AssistantPlanRow) {
    Text(row.title, style = TimeboxTheme.type.body, fontWeight = FontWeight.Medium)
    Text(row.taskType, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
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
    Text(annotated, style = TimeboxTheme.type.body.copy(fontSize = 15.sp, lineHeight = 25.sp))
}
