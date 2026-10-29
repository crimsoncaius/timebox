package com.timebox.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme

/** Kicker + large title, with an optional screen-specific primary action. */
@Composable
fun TimeboxTopBar(
    kicker: String,
    title: String,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenBackButton()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kicker.uppercase(),
                style = TimeboxTheme.type.kicker,
                color = colors.onVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = TimeboxTheme.type.screenTitle,
                color = colors.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (primaryActionLabel != null && onPrimaryAction != null) {
            TextButton(onClick = onPrimaryAction) {
                Text(primaryActionLabel, style = TimeboxTheme.type.label, color = colors.planned)
            }
        }
    }
}

internal val LocalScreenBack = androidx.compose.runtime.compositionLocalOf<(() -> Unit)?> { null }

internal val LocalOpenNavigationShelf = androidx.compose.runtime.compositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun ScreenBackButton() {
    val openShelf = LocalOpenNavigationShelf.current
    (openShelf ?: LocalScreenBack.current)?.let { onBack ->
        androidx.compose.material3.IconButton(onClick = onBack) {
            if (openShelf != null) {
                val color = TimeboxTheme.colors.on
                Canvas(Modifier.size(24.dp).semantics { contentDescription = "Open navigation shelf" }) {
                    val unit = size.width / 24f
                    drawRoundRect(color, Offset(2 * unit, 3 * unit), Size(20 * unit, 18 * unit),
                        CornerRadius(3 * unit), style = Stroke(2 * unit))
                    drawRoundRect(color, Offset(5 * unit, 6 * unit), Size(4 * unit, 12 * unit), CornerRadius(unit))
                }
            } else {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = TimeboxTheme.colors.on,
                )
            }
        }
    }
}
