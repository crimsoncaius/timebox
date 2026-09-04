package com.timebox.android.ui.battleplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay

@Composable
internal fun BattlePlanTrashUndoNotice(
    notice: TrashUndoNotice,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onExpiryFinished: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    var visible by remember(notice.noticeId) { mutableStateOf(true) }
    LaunchedEffect(notice.noticeId, notice.phase, reducedMotion) {
        if (notice.phase != TrashUndoPhase.Expiring) return@LaunchedEffect
        if (reducedMotion) {
            onExpiryFinished()
        } else {
            visible = false
            delay(TRASH_UNDO_FADE_MILLIS)
            onExpiryFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(TRASH_UNDO_FADE_MILLIS.toInt())),
        modifier = modifier,
    ) {
        val colors = TimeboxTheme.colors
        Row(
            modifier = Modifier
                .background(colors.on)
                .semantics {
                    liveRegion = if (notice.phase == TrashUndoPhase.Failed) {
                        LiveRegionMode.Assertive
                    } else {
                        LiveRegionMode.Polite
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (notice.phase) {
                TrashUndoPhase.Ready, TrashUndoPhase.Expiring -> {
                    Text("${notice.title} moved to Trash", color = colors.bg, modifier = Modifier.weight(1f))
                    TextButton(onClick = onUndo, enabled = notice.phase == TrashUndoPhase.Ready) {
                        Text("Undo", color = colors.bg)
                    }
                    TextButton(onClick = onDismiss, enabled = notice.phase == TrashUndoPhase.Ready) {
                        Text("Dismiss", color = colors.bg)
                    }
                }
                TrashUndoPhase.Restoring -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.bg,
                        strokeWidth = 2.dp,
                    )
                    Text("Restoring ${notice.title}", color = colors.bg, modifier = Modifier.weight(1f))
                    TextButton(onClick = {}, enabled = false) { Text("Restoring…", color = colors.bg) }
                    TextButton(onClick = {}, enabled = false) { Text("Dismiss", color = colors.bg) }
                }
                TrashUndoPhase.Failed -> {
                    val failure = notice.error?.let { "Could not restore ${notice.title}. $it" }
                        ?: "Could not restore ${notice.title}"
                    Text(failure, color = colors.bg, modifier = Modifier.weight(1f))
                    TextButton(onClick = onUndo) { Text("Retry", color = colors.bg) }
                    TextButton(onClick = onDismiss) { Text("Dismiss", color = colors.bg) }
                }
            }
        }
    }
}

internal const val TRASH_UNDO_FADE_MILLIS = 150L
