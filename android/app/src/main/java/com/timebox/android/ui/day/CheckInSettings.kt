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
    val repository = (LocalContext.current.applicationContext as TimeboxApplication).activityRepository
    val state by repository.state.collectAsState()
    val settings = remember(state) { repository.checkInPreferences() }
    var minutes by remember { mutableStateOf(settings.thresholdMinutes.toString()) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Inactivity check-ins", style = MaterialTheme.typography.titleLarge)
        Row { Text("Enable on this device", Modifier.weight(1f)); Switch(settings.enabled, { scope.launch { repository.setCheckInPreferences(settings.copy(enabled = it)) } }) }
        OutlinedTextField(minutes, { minutes = it }, label = { Text("Inactivity minutes") })
        TextButton(enabled = minutes.toIntOrNull() in 15..480, onClick = { scope.launch { repository.setCheckInPreferences(settings.copy(thresholdMinutes = minutes.toInt())) } }) { Text("Save threshold") }
        Text("15 minutes–8 hours; default one hour. Device detection is not connected in this development slice. Recording remains available. Notification permission is separate.")
    }
}
