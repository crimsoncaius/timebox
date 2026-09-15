package com.timebox.android.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

/** Compact Activity Tracking status. Healthy Synced draws nothing. */
@Composable
fun ActivityTrackingStatusChip(
    flags: StatusFlags,
    retryDisabled: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (statusMarkKind(flags) == null) return
    val colors = TimeboxTheme.colors
    val label = compactStatusLabel(flags)
    val retry = statusShowsRetry(flags)
    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = TimeboxShapes.chip,
        color = colors.low,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).padding(start = 12.dp, end = if (retry) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                label,
                style = TimeboxTheme.type.bodySmall,
                color = colors.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (retry) {
                VerticalDivider(Modifier.padding(horizontal = 8.dp).height(12.dp), color = colors.hairline)
                Text(
                    "Retry",
                    style = TimeboxTheme.type.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (retryDisabled) colors.onVariant else colors.planned,
                    modifier = Modifier
                        .clip(TimeboxShapes.chip)
                        .clickable(enabled = !retryDisabled, onClick = onRetry)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}
