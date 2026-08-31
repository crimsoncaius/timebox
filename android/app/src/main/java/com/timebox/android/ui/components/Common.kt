package com.timebox.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

/** Uppercase micro-label with wide tracking, used for lane headers and kickers. */
@Composable
fun Kicker(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = TimeboxTheme.type.laneLabel,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The pill switch from the settings screen. */
@Composable
fun TimeboxSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val track by animateColorAsState(
        targetValue = if (checked) colors.tertiary else Color(0x47808080),
        label = "switchTrack",
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, colors.hairline, CircleShape)
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset, y = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else colors.lowest),
        )
    }
}

/** Rounded selectable chip; a quiet tonal selection, hairline outline when inactive. */
@Composable
fun TimeboxChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TimeboxShapes.chip,
    height: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    textStyle: TextStyle = TimeboxTheme.type.bodySmall.copy(fontSize = 12.5.sp),
) {
    val colors = TimeboxTheme.colors
    val fill by animateColorAsState(
        targetValue = if (selected) colors.selected else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "chipFill",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) colors.outlineVariant else colors.hairline,
        animationSpec = tween(durationMillis = 150),
        label = "chipOutline",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onSelected else colors.on,
        animationSpec = tween(durationMillis = 150),
        label = "chipContent",
    )
    Box(
        modifier = modifier
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(shape)
            .background(fill)
            .border(1.dp, outline, shape)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = textStyle,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** Grouped card matching the settings/review sections. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TimeboxShapes.group)
            .background(TimeboxTheme.colors.card)
            .border(1.dp, TimeboxTheme.colors.hairline, TimeboxShapes.group)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, description: String? = null) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)) {
        Text(title, style = TimeboxTheme.type.sectionTitle, color = TimeboxTheme.colors.on)
        if (description != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = TimeboxTheme.type.bodySmall,
                color = TimeboxTheme.colors.onVariant,
            )
        }
    }
}

/** Inset row inside a [SectionCard]. */
@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TimeboxShapes.card)
            .background(TimeboxTheme.colors.raised)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = TimeboxTheme.type.label, color = TimeboxTheme.colors.on)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.5.sp),
                    color = TimeboxTheme.colors.onVariant,
                )
            }
        }
        trailing()
    }
}

/** Compact, consistently placed guidance for an empty collection. */
@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TimeboxShapes.card)
            .background(colors.card)
            .border(1.dp, colors.hairline, TimeboxShapes.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, style = TimeboxTheme.type.label, color = colors.on)
        Text(description, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = TimeboxTheme.colors.onVariant,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Full-screen failure with a retry affordance. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = colors.onVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = message,
            style = TimeboxTheme.type.body,
            color = colors.onVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(text = "Try again", onClick = onRetry)
    }
}

/** Filled pill using dedicated action and disabled-state tokens. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    height: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(24.dp),
) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (enabled) colors.action else colors.disabledContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        leading?.invoke()
        Text(
            text,
            style = TimeboxTheme.type.button,
            color = if (enabled) colors.onAction else colors.disabledContent,
        )
    }
}

/** Circular icon button used throughout the chrome. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TimeboxTheme.colors.onVariant,
    diameter: Dp = 40.dp,
    background: Color = Color.Transparent,
    border: Color? = null,
    iconSize: Dp = 21.dp,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Hairline divider matching `--ov15`. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TimeboxTheme.colors.hairline),
    )
}
