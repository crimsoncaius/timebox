package com.timebox.android.quicktracking

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Capture stays visually separate from the screen underneath, without revealing the command. */
@Composable
internal fun QuickTrackingOverlay(
    capture: QuickCaptureState,
    listening: Boolean,
    speechStatus: String?,
    hasSettings: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onSpeakAgain: () -> Unit = onRetry,
) {
    val error = if (!listening && !capture.busy && !capture.saved) speechStatus ?: capture.message else null
    val state = when {
        capture.saved -> "Activity recorded"
        capture.busy -> "Recording activity"
        listening -> "Listening"
        error != null -> "Voice tracking needs attention"
        else -> "Preparing microphone"
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black.copy(alpha = .6f)))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, .22f to Color.Black.copy(alpha = .94f), 1f to Color.Black))
                .navigationBarsPadding().padding(top = 88.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (error != null) {
                Column(Modifier.widthIn(max = 420.dp).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text(if (speechStatus == null && capture.text.isNotBlank()) "Review command" else "Retry") }
                        if (hasSettings) TextButton(onClick = onSettings, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Settings") }
                        else if (speechStatus == null && capture.text.isNotBlank()) TextButton(onClick = onSpeakAgain, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Speak again") }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(144.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                val transition = rememberInfiniteTransition(label = "Voice orb")
                val pulse by transition.animateFloat(.94f, 1.04f,
                    infiniteRepeatable(tween(if (capture.busy) 650 else 1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Breathing")
                Box(Modifier.size(100.dp).graphicsLayer {
                    val scale = if (listening || capture.busy) pulse else 1f
                    scaleX = scale; scaleY = scale
                }.semantics { contentDescription = state; liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Brush.linearGradient(
                            listOf(Color(0xFF202224), Color(0xFF44474A), Color(0xFFF5F5F4), Color(0xFFA5A8AB)),
                            start = Offset(size.width * .25f, 0f), end = Offset(size.width * .75f, size.height)))
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .35f), Color.Transparent),
                            center = Offset(size.width * .7f, size.height * .7f), radius = size.width * .65f))
                    }
                    if (capture.saved) Icon(Icons.Outlined.Check, null, tint = Color.Black, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd).size(56.dp)) {
                    Icon(Icons.Outlined.Close, "Close voice tracking", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
