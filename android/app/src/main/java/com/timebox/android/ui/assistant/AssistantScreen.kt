package com.timebox.android.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.components.TimeboxTopBar

@Composable
fun AssistantScreen(controller: AssistantController = (LocalContext.current.applicationContext as TimeboxApplication).assistant) {
    val state by controller.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(state.exchanges.lastOrNull()?.answer, state.exchanges.size, state.readingPlan) {
        if (list.layoutInfo.totalItemsCount > 0) list.scrollToItem(list.layoutInfo.totalItemsCount - 1)
    }
    Column(Modifier.fillMaxSize()) {
        TimeboxTopBar(kicker = "Assistant", title = "Conversations", primaryActionLabel = "New conversation",
            onPrimaryAction = { controller.newConversation(); draft = "" })
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding), state = list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.exchanges.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("A little clarity for today", style = MaterialTheme.typography.titleLarge)
                    Text("Ask about your Planned Blocks. This conversation lasts while the app is open.")
                    OutlinedButton(onClick = { controller.send("What have I planned for today?") }) { Text("What have I planned for today?") }
                }
            }
            itemsIndexed(state.exchanges) { index, exchange ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Text(exchange.question, Modifier.fillMaxWidth().padding(12.dp))
                    }
                    SelectionContainer { Text(exchange.answer.ifEmpty { if (exchange.status.isEmpty()) "Thinking…" else "No response text." }) }
                    if (exchange.status in listOf("Stopped", "Interrupted")) {
                        Text(exchange.status, style = MaterialTheme.typography.labelMedium)
                        exchange.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (index == state.exchanges.lastIndex && !state.busy) TextButton(onClick = controller::retry) { Text("Retry") }
                    }
                }
            }
            if (state.readingPlan) item { Text("Reading today’s plan…", style = MaterialTheme.typography.labelLarge) }
            item { Spacer(Modifier.height(4.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = TimeboxDimens.screenPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 4000) draft = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Ask Assistant") }, maxLines = 5,
                supportingText = { Text("${draft.length}/4000") })
            if (state.busy) TextButton(onClick = controller::stop) { Text("Stop") }
            else Button(enabled = draft.isNotBlank(), onClick = { controller.send(draft); draft = "" }) { Text("Send") }
        }
    }
}
