package com.timebox.android.ui.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException
import java.io.IOException

/** Transport failures must not be presented as a disconnected Google account. */
internal fun calendarFailure(cause: Throwable): String = when (cause) {
    is IOException -> "Cannot reach Timebox. Check your connection and try again."
    is HttpException -> when (cause.code()) {
        401, 403 -> "Timebox could not verify this device. Check the API key under Advanced in Settings."
        409 -> "The calendar or plan changed. Review your changes again before confirming."
        422 -> "These changes could not be applied. Check your selection and review it again."
        else -> "Timebox could not complete the request. Try again shortly."
    }
    else -> "Calendar information is unavailable. Try again shortly."
}

@Composable
internal fun GoogleCalendarSettings(
    state: JsonObject?,
    busy: String?,
    loadError: String?,
    actionError: String?,
    awaitingGoogle: Boolean,
    selected: List<String>?,
    onSelect: (List<String>) -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onReview: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onDismissError: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val connected = state?.b("connected") == true
    val configured = state?.b("configured") == true
    val account = state?.s("account")
    val syncError = state?.s("error").takeIf { connected && configured }
    val reconnect = syncError?.contains("reconnect", ignoreCase = true) == true
    val enabled = busy == null
    var manage by remember { mutableStateOf(false) }
    var accountOptions by remember { mutableStateOf(false) }
    val order = selected ?: state?.strings("selected").orEmpty()
    val calendars = state?.objects("calendars").orEmpty()
    val title = when {
        state == null && loadError == null -> "Checking connection…"
        loadError != null -> "Connection unavailable"
        !configured -> "Setup needed"
        awaitingGoogle -> "Finish connecting in Google"
        !connected -> if (account == null) "Not connected" else "Disconnected"
        reconnect -> "Reconnect to continue"
        syncError != null -> "Sync needs attention"
        state.s("last_success") == null -> "Waiting for first sync"
        else -> "Connected"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            if (state == null && loadError == null) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else Icon(
                when {
                    loadError != null -> Icons.Outlined.CloudOff
                    syncError != null || !configured -> Icons.Outlined.Info
                    connected -> Icons.Outlined.CheckCircle
                    else -> Icons.Outlined.Event
                }, contentDescription = null, tint = if (loadError != null || syncError != null) colors.error else colors.onVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = TimeboxTheme.type.body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                account?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
                if (connected) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        state?.s("last_success")?.let { "Last synced ${stamp(it, state.s("timezone") ?: "UTC")}" } ?: "No successful sync yet",
                        style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.weight(1f),
                    )
                    if (configured && syncError == null && loadError == null && !awaitingGoogle) {
                        TextButton(onClick = onRefresh, enabled = enabled) { Text("Sync now", style = TimeboxTheme.type.bodySmall) }
                    }
                }
            }
        }
        when {
            loadError != null -> {
                CalendarHelp(loadError)
                if (state != null) CalendarHelp("Showing the last known connection. Check again before making changes.")
                OutlinedButton(onClick = onRetry, enabled = enabled) { Text(if (busy == "Checking connection…") busy else "Try again") }
            }
            state != null && !configured -> {
                CalendarHelp("Google Calendar is not available on this Timebox server yet. Ask the person who manages it to complete setup.")
                TextButton(onClick = onRetry, enabled = enabled) { Text("Check again") }
            }
            awaitingGoogle -> {
                CalendarHelp("Complete the sign-in in your browser, then return here. You can check the connection if you closed the browser.")
                OutlinedButton(onClick = onRetry, enabled = enabled) { Text("Check connection") }
                TextButton(onClick = onConnect, enabled = enabled) { Text("Open Google again") }
            }
            state != null && !connected -> {
                CalendarHelp(if (account == null) "Connect your account, then choose which calendars to import." else "New calendar changes are no longer imported. Connect again to choose calendars.")
                Button(onClick = onConnect, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (busy == "Opening Google…") busy else if (account == null) "Connect Google Calendar" else "Connect again")
                }
            }
            connected && syncError != null -> {
                CalendarHelp(if (reconnect) "Google access needs to be renewed. Reconnect your account to resume importing." else "Google Calendar could not be synced. Existing plans are kept; new changes may be missing.")
                Button(onClick = if (reconnect) onConnect else onRefresh, enabled = enabled) { Text(if (reconnect) "Reconnect Google" else "Retry sync") }
            }
        }
        if (busy != null) Text(busy, style = TimeboxTheme.type.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (actionError != null) {
            Surface(color = colors.errorContainer, contentColor = colors.onErrorContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Couldn’t complete that action", style = TimeboxTheme.type.body)
                    Text(actionError, style = TimeboxTheme.type.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = onDismissError, colors = ButtonDefaults.textButtonColors(contentColor = colors.onErrorContainer)) { Text("Dismiss") }
                }
            }
        }
        if (connected && configured) {
            HorizontalDivider(color = colors.hairline)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(enabled = enabled, role = Role.Button, onClick = { manage = !manage })
                    .semantics { contentDescription = "Manage calendars"; stateDescription = if (manage) "Expanded" else "Collapsed" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Calendars to import", style = TimeboxTheme.type.body, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
                    val count = order.size
                    val selectionSummary = if (count == 0) "No calendars selected" else "$count ${if (count == 1) "calendar" else "calendars"} selected"
                    CalendarHelp(selectionSummary + if (selected != null && selected != state!!.strings("selected")) " · Unsaved" else "")
                }
                Icon(if (manage) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.onVariant)
            }
            if (manage) {
                CalendarSelectionList(
                    calendars = calendars, order = order,
                    enabled = enabled && loadError == null, onSelect = onSelect,
                )
                if (order != state!!.strings("selected")) {
                    if (order.isEmpty()) CalendarHelp("Removing all calendars will remove future imported plans. Review the changes before applying.")
                    Button(onClick = onReview, enabled = enabled && loadError == null, modifier = Modifier.fillMaxWidth()) { Text("Review calendar changes") }
                    TextButton(onClick = { onSelect(state.strings("selected")) }, enabled = enabled) { Text("Discard selection changes") }
                }
            }
        }
        if (account != null) {
            HorizontalDivider(color = colors.hairline)
            TextButton(onClick = { accountOptions = !accountOptions }, enabled = enabled) { Text(if (accountOptions) "Hide account options" else "Account options") }
            if (accountOptions) {
                CalendarHelp("Disconnecting removes future imported plans. Google events and recorded activity stay unchanged. You’ll review the changes first.")
                if (connected) TextButton(onClick = onDisconnect, enabled = enabled && loadError == null) { Text("Disconnect") }
                CalendarHelp("Forget import data also clears cached calendar details and remembered choices.")
                TextButton(onClick = onForget, enabled = enabled && loadError == null, colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) { Text(if (connected) "Disconnect and forget data" else "Forget import data") }
                if (connected && configured && !reconnect) TextButton(onClick = onConnect, enabled = enabled && loadError == null) { Text("Reconnect Google") }
            }
        }
    }
}

@Composable
private fun CalendarHelp(text: String) {
    Text(text, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
}
