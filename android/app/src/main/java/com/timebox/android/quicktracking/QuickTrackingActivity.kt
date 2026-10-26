package com.timebox.android.quicktracking

import com.timebox.android.ui.components.ContextualHelp
import android.Manifest
import android.app.KeyguardManager
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import androidx.activity.SystemBarStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.timebox.android.R
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.AppSettings
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay

class QuickTrackingActivity : ComponentActivity() {
    private val app get() = application as TimeboxApplication
    private val speech get() = app.quickSpeech
    private var setup by mutableStateOf(false)
    private var assistantAssigned by mutableStateOf(false)
    private var started = false
    private var source = "assistant"
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { speech.status = "Microphone permission is off."; speech.settings = appSettingsIntent() }
        else if (!isFinishing && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) speech.listen(source)
    }
    private val assistantRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateRole() }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getBooleanExtra("setup", false)) setTheme(R.style.Theme_Timebox)
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { if (!setup) speech.finish(); finish() }
        if (Build.VERSION.SDK_INT < 27) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        source = intent.getStringExtra("source")?.takeIf { it in listOf("tile", "app") } ?: "assistant"
        setup = intent.getBooleanExtra("setup", false)
        if (savedInstanceState == null && app.quickTracking.state.value.saved && !setup) app.quickTracking.edit("")
        // Rotation or process recreation never submits or reopens the microphone by itself.
        started = savedInstanceState != null
        if (started && !setup && !speech.listening && !app.quickTracking.state.value.busy && !app.quickTracking.state.value.saved)
            speech.status = "Listening stopped. Tap Retry when ready."
        setContent {
            val settings by app.repository.settings.collectAsState(AppSettings("", "", null))
            val capture by app.quickTracking.state.collectAsState()
            TimeboxTheme(darkTheme = settings.darkTheme ?: isSystemInDarkTheme()) {
                if (!setup) {
                    QuickTrackingOverlay(capture, speech.listening, speech.status, speech.settings != null,
                        onClose = { speech.finish(); finish() },
                        onRetry = {
                            if (speech.status == null && capture.text.isNotBlank()) app.quickTracking.submit(capture.text, source)
                            else requestListen()
                        },
                        onSettings = { speech.settings?.let { startActivity(it) } },
                        onSpeakAgain = ::requestListen)
                } else Surface(color = TimeboxTheme.colors.bg, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (setup) "Quick tracking" else "Track activity", style = TimeboxTheme.type.screenTitle, modifier = Modifier.weight(1f))
                            TextButton(onClick = { finish() }) { Text("Close") }
                        }
                        if (setup) {
                            Text("Speak an activity. When you finish speaking, Timebox records it automatically. Correct the result later in Assistant → Review.")
                            Text(if (assistantAssigned) "Timebox is your default digital assistant." else "Timebox is not your default digital assistant.")
                            Button(onClick = ::chooseAssistant) { Text(if (assistantAssigned) "Assistant settings" else "Choose default assistant") }
                            Button(onClick = ::addTile) { Text("Add Quick Settings tile") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Assistant setup", modifier = Modifier.weight(1f))
                                ContextualHelp("Assistant setup", "Choose Timebox as your default digital assistant to use the system assistant gesture.\n\nOn Samsung: Settings → Apps → Choose default apps → Digital assistant app → Timebox. Then Advanced features → Side button → Long press → Digital assistant. Availability and menu names depend on your model and One UI version.\n\nOn gesture navigation, enable the device’s assistant gesture. A button assigned to Bixby or the power menu will not open Timebox.")
                            }
                            speech.status?.let { Text(it) }
                            TextButton(onClick = {
                                startActivity(Intent(this@QuickTrackingActivity, QuickTrackingActivity::class.java).putExtra("source", "app").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
                                finish()
                            }) { Text("Try voice tracking") }
                        }
                    }
                }
            }
            LaunchedEffect(capture.saved, setup) {
                if (capture.saved && !setup) { delay(900); finish() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateRole()
        if (!started) {
            started = true
            if (!setup && !speech.listening && !app.quickTracking.state.value.busy && app.quickTracking.state.value.message == null) requestListen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!app.quickTracking.state.value.busy) {
            source = intent.getStringExtra("source")?.takeIf { it in listOf("tile", "app") } ?: "assistant"
            setup = intent.getBooleanExtra("setup", false)
            if (app.quickTracking.state.value.saved) app.quickTracking.edit("")
            // Dispatch may bring a stopped task forward; start only after it resumes.
            started = false
        }
    }

    override fun onUserLeaveHint() { if (!setup) speech.finish(); super.onUserLeaveHint() }
    override fun onStop() {
        if (!setup) {
            if (isChangingConfigurations) speech.cancel() else speech.finish()
        }
        super.onStop()
    }

    private fun requestListen() {
        if (app.quickTracking.state.value.busy) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.listen(source)
        else if (getSystemService(KeyguardManager::class.java).isDeviceLocked) {
            speech.status = "Unlock your phone to enable microphone access."
            speech.settings = appSettingsIntent()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun updateRole() {
        assistantAssigned = if (Build.VERSION.SDK_INT >= 29) getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        else Settings.Secure.getString(contentResolver, "assistant")?.startsWith("$packageName/") == true
    }
    private fun chooseAssistant() {
        // ASSISTANT is available but not requestable on Android 36; Settings owns selection.
        assistantRole.launch(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
    }
    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, QuickTrackingTile::class.java), "Track activity", AndroidIcon.createWithResource(this, R.drawable.ic_quick_tracking), mainExecutor
        ) { result -> speech.status = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) "Tile is available in Quick Settings." else "Tile was not added. You can add Track activity from Quick Settings → Edit." }
        else speech.status = "Open Quick Settings → Edit, then drag Track activity into your tiles."
    }
    private fun appSettingsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
}
