package com.timebox.android.ui.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.serialization.json.*

/** Debug-only deterministic review of production composables; no provider or mutation calls. */
class AssistantTaskReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixture = Json.parseToJsonElement(assets.open("assistant-tasks.json").bufferedReader().use { it.readText() }).jsonObject
        val card = AssistantTaskCard.parse(fixture.getValue("card").jsonObject)
        val proposal = TaskChangeProposal.parse(fixture.getValue("proposal").jsonObject)
        val full = TaskChangeProposal.parse(fixture.getValue("full").jsonObject, fullDescription = true)
        val result = TaskOperationResult.parse(fixture.getValue("result").jsonObject)
        val openTask: (Int) -> Unit = { id -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("timebox://battle-plan/task/$id")).setPackage(packageName)) }
        setContent {
            var dark by remember { mutableStateOf(false) }
            var large by remember { mutableStateOf(false) }
            var tab by remember { mutableIntStateOf(0) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 1.6f else 1f)) {
                TimeboxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                            Text("Assistant · deterministic native review", Modifier.padding(12.dp))
                            Row { TextButton(onClick = { dark = !dark }) { Text("Theme") }; TextButton(onClick = { large = !large }) { Text("Text size") } }
                            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                                listOf("Saved Tasks", "Change review", "Saved result").forEachIndexed { index, label -> Tab(selected = index == tab, onClick = { tab = index }, text = { Text(label) }) }
                            }
                            key(tab) {
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Illustrative server fixtures. Open Task navigates to current details on the isolated review backend; the saved ID may be unavailable.", style = MaterialTheme.typography.bodySmall)
                                    when(tab) {
                                        0 -> TaskReadCard(card, openTask)
                                        1 -> TaskChangeCard(proposal, TaskChangeState("pending", sourceCompleted = true), openTask, { full }, {}, {}, {})
                                        else -> TaskResultCard(result, openTask)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
