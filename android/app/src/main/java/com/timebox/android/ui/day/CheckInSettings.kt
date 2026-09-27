package com.timebox.android.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.CheckInPreferences
import kotlinx.coroutines.launch
import com.timebox.android.ui.components.*
import com.timebox.android.ui.theme.TimeboxTheme

internal val CheckInThresholdPresets = listOf(15, 30, 45, 60, 90, 120, 180, 240, 360, 480)

internal fun checkInThresholdLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes % 60 == 0 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Inactivity check-in rows for the Settings "Focus & check-ins" group. */
@Composable fun CheckInSettingRows() {
    val context = LocalContext.current
    val app = context.applicationContext as TimeboxApplication
    val repository = app.activityRepository
    val status by app.checkIns.status.collectAsState()
    val state by repository.state.collectAsState()
    val settings = remember(state) { repository.checkInPreferences() }
    val scope = rememberCoroutineScope()
    var choosingThreshold by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val colors = TimeboxTheme.colors
    fun save(next: CheckInPreferences) = scope.launch {
        repository.setCheckInPreferences(next)
        app.checkIns.settingsChanged()
    }
    SettingRow(title = "Inactivity check-ins", description = "Ask whether an activity is still going after a quiet stretch.") {
        TimeboxSwitch(checked = settings.enabled, onCheckedChange = { save(settings.copy(enabled = it)) })
    }
    Box(Modifier.alpha(if (settings.enabled) 1f else 0.5f)) {
        SettingRow(title = "Check in after", description = "How long the device must be idle before asking.") {
            Box {
                TextButton(enabled = settings.enabled, onClick = { choosingThreshold = true }) {
                    Text(checkInThresholdLabel(settings.thresholdMinutes))
                }
                DropdownMenu(expanded = choosingThreshold, onDismissRequest = { choosingThreshold = false }) {
                    (CheckInThresholdPresets + settings.thresholdMinutes).distinct().sorted().forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(checkInThresholdLabel(minutes)) },
                            onClick = {
                                choosingThreshold = false
                                save(settings.copy(thresholdMinutes = minutes))
                            },
                        )
                    }
                }
            }
        }
    }
    SettingRow(title = "Usage access", description = status) {
        if (android.os.Build.VERSION.SDK_INT >= 28) TextButton(onClick = {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}")))
        }) { Text("Manage") }
    }
    TextButton(onClick = { showHelp = !showHelp }, modifier = Modifier.fillMaxWidth()) {
        Text(if (showHelp) "Hide details" else "How check-ins work")
    }
    if (showHelp) Text(
        "Usage access lets Android report screen transitions. Leaving Timebox or missing usage events does not count as inactivity. Allow or block system check-ins in Notifications settings; the in-app question remains available either way.",
        style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
