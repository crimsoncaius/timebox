package com.timebox.android.ui.readiness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.timebox.android.data.BattleTask
import com.timebox.android.ui.theme.TimeboxTheme

internal val LocalReadyToPlanRetry = staticCompositionLocalOf<(Int) -> Unit> { {} }

@Composable
internal fun ReadyToPlanFailureNotice(task: BattleTask, modifier: Modifier = Modifier) {
    val message = task.readinessFailureMessage ?: return
    val retry = LocalReadyToPlanRetry.current
    Row(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = "${task.title} readiness error"
        },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = TimeboxTheme.type.bodySmall,
            color = TimeboxTheme.colors.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { retry(task.id) }) {
            Text("Retry", color = TimeboxTheme.colors.error)
        }
    }
}
