package com.timebox.android.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.timebox.android.ui.components.*
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.theme.TimeboxShapes

@Composable fun CheckInSettings() {
    val context = LocalContext.current
    val app = context.applicationContext as TimeboxApplication
    val repository = app.activityRepository
    val status by app.checkIns.status.collectAsState()
    val state by repository.state.collectAsState()
    val settings = remember(state) { repository.checkInPreferences() }
    var minutes by remember { mutableStateOf(settings.thresholdMinutes.toString()) }
    val scope = rememberCoroutineScope()
    var showHelp by remember { mutableStateOf(false) }
    val colors = TimeboxTheme.colors
    val validMinutes = minutes.toIntOrNull() in 15..480
    SectionCard {
        SectionHeader(title = "Inactivity check-ins", description = "Ask whether your activity is still continuing.")
        Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingRow(title = "Enable on this device", description = "Recording continues while a check-in waits.") {
                TimeboxSwitch(checked = settings.enabled, onCheckedChange = { scope.launch {
                    repository.setCheckInPreferences(settings.copy(enabled = it))
                    app.checkIns.settingsChanged()
                } })
            }
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it },
                    label = { Text("Inactivity minutes", style = TimeboxTheme.type.bodySmall) },
                    supportingText = { Text("15 to 480 minutes; default 60", style = TimeboxTheme.type.bodySmall) },
                    singleLine = true, isError = !validMinutes,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TimeboxTheme.type.body.copy(color = colors.on),
                    shape = TimeboxShapes.field,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.field, unfocusedContainerColor = colors.field,
                        focusedIndicatorColor = colors.outline, unfocusedIndicatorColor = colors.hairline,
                        cursorColor = colors.on,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(text = "Save threshold", enabled = validMinutes && minutes.toIntOrNull() != settings.thresholdMinutes,
                    modifier = Modifier.fillMaxWidth(), onClick = { scope.launch {
                        repository.setCheckInPreferences(settings.copy(thresholdMinutes = minutes.toInt()))
                        app.checkIns.settingsChanged()
                    } })
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
    }
}
