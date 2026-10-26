package com.timebox.android.quicktracking

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-owned: leaving capture stops the microphone, but retains its final transcription. */
class QuickTrackingSpeech(private val context: Context, private val capture: QuickTrackingController) : RecognitionListener {
    var listening by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
    var settings by mutableStateOf<Intent?>(null)
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var source = "assistant"
    private var finishing = false
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        release()
        status = "Speech recognition did not finish. Try again."
    }

    fun listen(source: String) {
        if (listening || capture.state.value.busy) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "Speech recognition is unavailable. Check your voice input settings."
            settings = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            return
        }
        this.source = source
        capture.edit("")
        settings = null
        status = "Listening…"
        listening = true
        val current = ++generation
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer!!.also {
                it.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { if (current == generation) this@QuickTrackingSpeech.onReadyForSpeech(params) }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { if (current == generation) this@QuickTrackingSpeech.onEndOfSpeech() }
                    override fun onError(error: Int) { if (current == generation) this@QuickTrackingSpeech.onError(error) }
                    override fun onResults(results: Bundle?) { if (current == generation) this@QuickTrackingSpeech.onResults(results) }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                it.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                })
            }
        } catch (_: Exception) {
            release()
            status = "Could not start the microphone. Try again."
        }
    }

    fun finish() {
        if (!listening || finishing) return
        finishing = true
        status = "Finishing transcription…"
        // A recognizer that never calls back must not retain its connection indefinitely.
        handler.postDelayed(timeout, 15_000)
        try { recognizer?.stopListening() }
        catch (_: Exception) { onError(SpeechRecognizer.ERROR_CLIENT) }
    }

    fun cancel() {
        if (!listening) return
        release()
        status = "Listening stopped. Tap Retry when ready."
    }

    private fun release() {
        generation++
        listening = false
        finishing = false
        handler.removeCallbacks(timeout)
        recognizer?.destroy()
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) { if (!finishing) status = "Listening…" }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { if (listening) status = "Finishing transcription…" }
    override fun onError(error: Int) {
        if (!listening) return
        release()
        settings = if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")) else null
        status = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required. Enable it in app settings."
            else -> "Speech recognition could not finish. Try again."
        }
    }
    override fun onResults(results: Bundle?) {
        if (!listening) return
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
        release()
        if (text.isNullOrEmpty()) { status = "No speech heard. Try again."; return }
        status = null
        capture.submit(text, source)
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
