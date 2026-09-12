package com.timebox.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme

/** Visual shell only. Its owner supplies lifecycle, expiry, and recovery behavior. */
@Composable
fun TransientFeedback(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
    isError: Boolean = false,
) {
    val colors = TimeboxTheme.colors
    Surface(
        modifier = modifier.widthIn(max = 600.dp).fillMaxWidth().semantics {
            liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
        },
        shape = RoundedCornerShape(24.dp),
        color = colors.raised,
        contentColor = colors.on,
        border = BorderStroke(1.dp, colors.hairline),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message, style = TimeboxTheme.type.bodySmall, color = if (isError) colors.error else colors.on)
                detail?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
            }
            actionLabel?.let { label ->
                TextButton(
                    onClick = onAction,
                    enabled = actionsEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (colors.isDark) colors.high else colors.low,
                        contentColor = colors.on,
                        disabledContentColor = colors.disabledContent,
                    ),
                    shape = RoundedCornerShape(50),
                ) { Text(label, style = TimeboxTheme.type.bodySmall) }
            }
            onDismiss?.let { dismiss ->
                IconButton(onClick = dismiss, enabled = actionsEnabled) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = colors.onVariant)
                }
            }
        }
    }
}
