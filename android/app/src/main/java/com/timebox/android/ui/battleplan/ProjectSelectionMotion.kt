package com.timebox.android.ui.battleplan

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/** Fade only the new scope's content; never retain an outgoing, interactive board. */
@Composable
internal fun projectSelectionMotion(scopeKey: String): Modifier {
    val alpha = remember { Animatable(1f) }
    var previousScope by remember { mutableStateOf(scopeKey) }
    val context = LocalContext.current
    LaunchedEffect(scopeKey) {
        if (previousScope == scopeKey) return@LaunchedEffect
        previousScope = scopeKey
        val reducedMotion = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
        if (reducedMotion) {
            alpha.snapTo(1f)
        } else {
            alpha.snapTo(0.2f)
            alpha.animateTo(1f, tween(durationMillis = 170, easing = LinearOutSlowInEasing))
        }
    }
    return Modifier.graphicsLayer { this.alpha = alpha.value }
}
