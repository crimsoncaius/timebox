package com.timebox.android.ui.undo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.timebox.android.ui.components.TransientFeedback
import kotlinx.coroutines.delay

@Composable
fun UndoNoticeHost(
    notice: UndoNotice,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onExpiryFinished: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    var visible by remember(notice.id) { mutableStateOf(true) }
    LaunchedEffect(notice.id, notice.phase, reducedMotion) {
        if (notice.phase != UndoPhase.Expiring) return@LaunchedEffect
        if (reducedMotion) onExpiryFinished()
        else {
            visible = false
            delay(150)
            onExpiryFinished()
        }
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(150)), modifier = modifier) {
        TransientFeedback(
            message = when (notice.phase) {
                UndoPhase.Ready, UndoPhase.Expiring -> notice.message
                UndoPhase.Running -> "Undoing ${notice.title}…"
                UndoPhase.Failed -> "Could not undo ${notice.title}"
                UndoPhase.Unavailable -> "Undo unavailable for ${notice.title}"
            },
            detail = notice.error ?: notice.detail,
            actionLabel = when (notice.phase) {
                UndoPhase.Running -> "Undoing…"
                UndoPhase.Failed -> "Retry"
                UndoPhase.Unavailable -> null
                else -> "Undo"
            },
            onAction = onUndo,
            onDismiss = onDismiss,
            actionsEnabled = notice.phase == UndoPhase.Ready || notice.phase == UndoPhase.Failed || notice.phase == UndoPhase.Unavailable,
            isError = notice.phase == UndoPhase.Failed || notice.phase == UndoPhase.Unavailable,
        )
    }
}
