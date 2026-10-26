package com.timebox.android.ui.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.components.SectionCard
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.serialization.json.*

/** Debug-only state gallery using the production settings content, with no network writes. */
class GoogleCalendarReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var dark by remember { mutableStateOf(intent.getBooleanExtra("dark", false)) }
            var scenario by remember { mutableStateOf(intent.getStringExtra("state") ?: "Connected") }
            var menu by remember { mutableStateOf(false) }
            var selected by remember(scenario) { mutableStateOf<List<String>?>(null) }
            var feedback by remember(scenario) { mutableStateOf<String?>(null) }
            SideEffect {
                val barStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            TimeboxTheme(darkTheme = dark) {
                Surface(color = TimeboxTheme.colors.bg) {
                    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Calendar settings review", style = TimeboxTheme.type.sectionTitle)
                        Text("Sample states · no account changes", style = TimeboxTheme.type.bodySmall)
                        Row {
                            Box {
                                OutlinedButton(onClick = { menu = true }) { Text(scenario) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    listOf("Connected", "Loading", "Not connected", "Disconnected", "Setup needed", "Offline", "First load failed", "Access expired", "Sync failed", "Syncing", "No calendars", "Empty selection", "Action failed", "Connecting").forEach { name ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = { scenario = name; menu = false })
                                    }
                                }
                            }
                            TextButton(onClick = { dark = !dark }) { Text(if (dark) "Light theme" else "Dark theme") }
                        }
                        SectionCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Google Calendar", style = TimeboxTheme.type.sectionTitle)
                                Text("All devices", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                                Text("Bring calendar commitments into your plan. Google events stay unchanged.", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                            }
                            key(scenario) {
                                GoogleCalendarSettings(
                                    state = if (scenario in listOf("Loading", "First load failed")) null else buildJsonObject {
                                        put("configured", scenario != "Setup needed")
                                        put("connected", scenario !in listOf("Not connected", "Disconnected", "Setup needed", "Connecting"))
                                        if (scenario !in listOf("Not connected", "Setup needed", "Connecting")) put("account", "alex.morgan@example.com")
                                        put("timezone", "Asia/Singapore")
                                        put("last_success", "2026-10-26T02:42:00Z")
                                        if (scenario == "Access expired") put("error", "Google access could not be renewed. Reconnect Google Calendar.")
                                        if (scenario == "Sync failed") put("error", "Google could not be reached.")
                                        putJsonArray("selected") { if (scenario !in listOf("No calendars", "Empty selection")) { add("work"); add("personal") } }
                                        putJsonArray("calendars") {
                                            if (scenario != "No calendars") {
                                                add(buildJsonObject { put("id", "work"); put("name", "Work · Product & Design") })
                                                add(buildJsonObject { put("id", "personal"); put("name", "Personal") })
                                                add(buildJsonObject { put("id", "family"); put("name", "Family & shared commitments") })
                                            }
                                        }
                                    },
                                    busy = if (scenario == "Syncing") "Syncing calendars…" else null,
                                    loadError = if (scenario in listOf("Offline", "First load failed")) "Cannot reach Timebox. Check your connection and try again." else null,
                                    actionError = if (scenario == "Action failed") "The calendar or plan changed. Review your changes again before confirming." else null,
                                    awaitingGoogle = scenario == "Connecting", selected = selected, onSelect = { selected = it },
                                    onConnect = { scenario = "Connecting" }, onRefresh = { scenario = "Syncing" }, onRetry = { scenario = "Connected" },
                                    onReview = { feedback = "Sample review requested. In Settings, the plan preview opens before anything is applied." },
                                    onDisconnect = { feedback = "Sample disconnect requested. In Settings, this opens a confirmation preview." },
                                    onForget = { feedback = "Sample removal requested. In Settings, this opens a confirmation preview." },
                                    onDismissError = { scenario = "Connected" },
                                )
                            }
                        }
                        feedback?.let { Text(it, style = TimeboxTheme.type.bodySmall) }
                    }
                }
            }
        }
    }
}
