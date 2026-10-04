package com.timebox.android.ui.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.timebox.android.TimeboxApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
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

/** Debug-only fixture review, or explicit isolated-server confirmation/recovery; never calls a model. */
class AssistantTaskReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("recovery", false)) {
            setContent { TimeboxTheme { AssistantScreen(onOpenTask = ::openCurrentTask) } }
            return
        }
        intent.getStringExtra("proposal_id")?.let { id -> liveReview(id); return }
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
    private fun openCurrentTask(id: Int) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("timebox://battle-plan/task/$id")).setPackage(packageName))

    /** Explicit debug-only review mode against the isolated server. No model invocation. */
    private fun liveReview(id: String) {
        val app = application as TimeboxApplication
        val hold = intent.getBooleanExtra("hold_after_commit", false)
        val recovery = app.createTaskRecovery {
            val api = HttpAssistantTransport(app.repository.settings.first())
            object : AssistantTransport by api {
                override suspend fun confirmTask(record: com.timebox.android.data.TaskSubmission): TaskOperationResult? {
                    try { return api.confirmTask(record) }
                    catch (error: Exception) { android.util.Log.e("TimeboxReview", "Confirmation response failed", error); throw error }
                    finally { if (hold) delay(60_000) } // Force-stop during this lost-response window.
                }
            }
        }
        setContent { TimeboxTheme {
            val state by recovery.state.collectAsState()
            var proposal by remember { mutableStateOf<TaskChangeProposal?>(null) }
            var identity by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(id) {
                try {
                    val api = HttpAssistantTransport(app.repository.settings.first())
                    identity = api.serverIdentity; proposal = api.taskReview(id)
                    if (proposal?.status == "applied") recovery.foreground()
                } catch (failure: Exception) { error = failure.message }
            }
            Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Assistant · isolated server review")
                Text(if (hold) "Process-death check: response held after commit." else "Real confirmation against isolated saved review data.")
                error?.let { Text(it) }; state.error?.let { Text(it) }
                proposal?.let { p ->
                    val result = state.items.lastOrNull()?.result
                    TaskChangeCard(p, TaskChangeState(result?.status ?: p.status, p.sourceCompleted, result, state.busy), ::openCurrentTask,
                        { HttpAssistantTransport(app.repository.settings.first()).taskReview(p.id) }, recovery::foreground, {}, {},
                        onConfirm = { recovery.confirm(p, identity) })
                }
                state.items.forEach { item -> item.result?.let { result -> TaskResultCard(result, ::openCurrentTask,
                    onUndo = if (!state.busy && item.record.resolved) ({ recovery.undo(item) }) else null) } }
            } }
        } }
    }

}
