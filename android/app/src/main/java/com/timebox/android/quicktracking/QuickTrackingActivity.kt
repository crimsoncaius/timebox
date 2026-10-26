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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
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

class QuickTrackingActivity : ComponentActivity(), RecognitionListener {
    private val app get() = application as TimeboxApplication
    private var recognizer: SpeechRecognizer? = null
    private var listening by mutableStateOf(false)
    private var speechStatus by mutableStateOf<String?>(null)
    private var voiceSettings by mutableStateOf<Intent?>(null)
    private var setup by mutableStateOf(false)
    private var assistantAssigned by mutableStateOf(false)
    private var started = false
    private var source = "assistant"
    private var speechGeneration = 0
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else { speechStatus = "Microphone permission is off."; voiceSettings = appSettingsIntent() }
    }
    private val assistantRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateRole() }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getBooleanExtra("setup", false)) setTheme(R.style.Theme_Timebox)
        super.onCreate(savedInstanceState)
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
        if (started && !setup && !app.quickTracking.state.value.busy && !app.quickTracking.state.value.saved)
            speechStatus = "Listening stopped. Tap Retry when ready."
        setContent {
            val settings by app.repository.settings.collectAsState(AppSettings("", "", null))
            val capture by app.quickTracking.state.collectAsState()
            TimeboxTheme(darkTheme = settings.darkTheme ?: isSystemInDarkTheme()) {
                if (!setup) {
                    QuickTrackingOverlay(capture, listening, speechStatus, voiceSettings != null,
                        onClose = { cancelListening(); finish() },
                        onRetry = {
                            if (speechStatus == null && capture.text.isNotBlank()) app.quickTracking.submit(capture.text, source)
                            else requestListen()
                        },
                        onSettings = { voiceSettings?.let { startActivity(it) } },
                        onSpeakAgain = ::requestListen)
                } else Surface(color = TimeboxTheme.colors.bg, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (setup) "Quick tracking" else "Track activity", style = TimeboxTheme.type.screenTitle, modifier = Modifier.weight(1f))
                            TextButton(onClick = { cancelListening(); finish() }) { Text("Close") }
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
                            speechStatus?.let { Text(it) }
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
            if (!setup && !app.quickTracking.state.value.busy && app.quickTracking.state.value.message == null) requestListen()
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

    override fun onStop() { cancelListening(); super.onStop() }
    override fun onDestroy() { recognizer?.destroy(); recognizer = null; super.onDestroy() }

    private fun requestListen() {
        if (app.quickTracking.state.value.busy) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else if (getSystemService(KeyguardManager::class.java).isDeviceLocked) {
            speechStatus = "Unlock your phone to enable microphone access."
            voiceSettings = appSettingsIntent()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun listen() {
        cancelListening()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { speechStatus = "Speech recognition is unavailable. Check your voice input settings."; voiceSettings = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS); return }
        app.quickTracking.edit("")
        voiceSettings = null
        speechStatus = "Listening…"
        recognizer?.destroy()
        val generation = ++speechGeneration
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (generation == speechGeneration) this@QuickTrackingActivity.onReadyForSpeech(params) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (generation == speechGeneration) this@QuickTrackingActivity.onEndOfSpeech() }
            override fun onError(error: Int) { if (generation == speechGeneration) this@QuickTrackingActivity.onError(error) }
            override fun onResults(results: Bundle?) { if (generation == speechGeneration) this@QuickTrackingActivity.onResults(results) }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }) }
        listening = true
        try {
            recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (error: Exception) { listening = false; speechStatus = "Could not start the microphone. Try again." }
    }

    private fun cancelListening() {
        speechGeneration++
        if (listening) { listening = false; recognizer?.cancel(); speechStatus = "Listening stopped. Tap Retry when ready." }
    }
    override fun onReadyForSpeech(params: Bundle?) { speechStatus = "Listening…" }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { if (listening) speechStatus = "Finishing transcription…" }
    override fun onError(error: Int) {
        if (!listening) return
        listening = false
        voiceSettings = if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) appSettingsIntent() else null
        speechStatus = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required. Enable it in app settings."
            else -> "Speech recognition could not finish. Try again."
        }
    }
    override fun onResults(results: Bundle?) {
        if (!listening) return
        listening = false
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
        if (text.isNullOrEmpty()) { speechStatus = "No speech heard. Try again."; return }
        speechStatus = null
        app.quickTracking.submit(text, source)
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

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
        ) { result -> speechStatus = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) "Tile is available in Quick Settings." else "Tile was not added. You can add Track activity from Quick Settings → Edit." }
        else speechStatus = "Open Quick Settings → Edit, then drag Track activity into your tiles."
    }
    private fun appSettingsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
}
