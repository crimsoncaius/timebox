package com.timebox.android.ui.battleplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
        TransientFeedback(
            message = when (notice.phase) {
                TrashUndoPhase.Ready, TrashUndoPhase.Expiring -> "${notice.title} moved to Trash"
                TrashUndoPhase.Restoring -> "Restoring ${notice.title}"
                TrashUndoPhase.Failed -> "Could not restore ${notice.title}"
            },
            detail = notice.error.takeIf { notice.phase == TrashUndoPhase.Failed },
            actionLabel = when (notice.phase) {
                TrashUndoPhase.Restoring -> "Restoring…"
                TrashUndoPhase.Failed -> "Retry"
                else -> "Undo"
            },
            onAction = onUndo,
            onDismiss = onDismiss,
            actionsEnabled = notice.phase == TrashUndoPhase.Ready || notice.phase == TrashUndoPhase.Failed,
            isError = notice.phase == TrashUndoPhase.Failed,
        )
    }
}

internal const val TRASH_UNDO_FADE_MILLIS = 150L
