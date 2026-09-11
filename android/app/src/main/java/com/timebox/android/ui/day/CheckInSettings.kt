package com.timebox.android.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import kotlinx.coroutines.launch

@Composable fun CheckInSettings() {
    val context = LocalContext.current
    val app = context.applicationContext as TimeboxApplication
    val repository = app.activityRepository
    val status by app.checkIns.status.collectAsState()
    val state by repository.state.collectAsState()
    val settings = remember(state) { repository.checkInPreferences() }
    var minutes by remember { mutableStateOf(settings.thresholdMinutes.toString()) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Inactivity check-ins", style = MaterialTheme.typography.titleLarge)
        Row { Text("Enable on this device", Modifier.weight(1f)); Switch(settings.enabled, { scope.launch { repository.setCheckInPreferences(settings.copy(enabled = it)); app.checkIns.settingsChanged() } }) }
        OutlinedTextField(minutes, { minutes = it }, label = { Text("Inactivity minutes") })
        TextButton(enabled = minutes.toIntOrNull() in 15..480, onClick = { scope.launch { repository.setCheckInPreferences(settings.copy(thresholdMinutes = minutes.toInt())); app.checkIns.settingsChanged() } }) { Text("Save threshold") }
        Text("15 minutes–8 hours; default one hour. $status")
        if (android.os.Build.VERSION.SDK_INT >= 28) TextButton(onClick = {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}")))
        }) { Text("Manage usage access") }
        Text("Usage access lets Android report screen transitions. We do not treat leaving Timebox or missing usage events as inactivity. Notification permission is separate; use Notifications settings to allow or block system check-ins. The in-app question remains available either way.")
    }
}
