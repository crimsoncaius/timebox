package com.timebox.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme

/** Whether the screen already shows the current range, can navigate to it, or is still waiting for the server's Today. */
enum class CurrentRangeState { Navigate, Current, Resolving }

/**
 * The shared "Today" / "This week" control: a quiet outlined badge while the current range is shown,
 * and a filled "Go to …" action once the user has navigated away.
 *
 * [width] fixes the pill's width so neighbouring controls stay put as the label changes between states.
 */
@Composable
fun CurrentRangeAction(
    state: CurrentRangeState,
    currentLabel: String,
    navigateLabel: String,
    onClick: () -> Unit,
    width: Dp? = null,
) {
    val colors = TimeboxTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    val interaction = when (state) {
        CurrentRangeState.Navigate, CurrentRangeState.Current -> Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (state == CurrentRangeState.Current) "Viewing ${currentLabel.lowercase()}" else navigateLabel
            }
        CurrentRangeState.Resolving -> Modifier
            .clickable(enabled = false, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$currentLabel unavailable" }
    }
    val fixedWidth = if (width != null) Modifier.width(width) else Modifier
    Box(
        modifier = Modifier
            .height(48.dp)
            .then(fixedWidth)
            .clip(shape)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .then(if (width != null) Modifier.fillMaxWidth() else Modifier)
                .height(42.dp)
                .clip(shape)
                .background(
                    when (state) {
                        CurrentRangeState.Navigate -> colors.planned
                        CurrentRangeState.Current -> colors.plannedSurface
                        CurrentRangeState.Resolving -> colors.disabledContainer
                    },
                )
                .then(
                    if (state == CurrentRangeState.Current) {
                        Modifier.border(1.dp, colors.plannedBorder, shape)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = if (state == CurrentRangeState.Current) {
                    Icons.Outlined.Check
                } else {
                    Icons.Outlined.CalendarToday
                },
                contentDescription = null,
                tint = when (state) {
                    CurrentRangeState.Navigate -> colors.lowest
                    CurrentRangeState.Current -> colors.planned
                    CurrentRangeState.Resolving -> colors.disabledContent
                },
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = if (state == CurrentRangeState.Navigate) navigateLabel else currentLabel,
                style = TimeboxTheme.type.label,
                color = when (state) {
                    CurrentRangeState.Navigate -> colors.lowest
                    CurrentRangeState.Current -> colors.on
                    CurrentRangeState.Resolving -> colors.disabledContent
                },
                maxLines = 1,
            )
        }
    }
}
