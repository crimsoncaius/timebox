package com.timebox.android.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.timebox.android.ui.theme.TimeboxTheme

/** Device-local Day controls; changes take effect immediately. */
@Composable
internal fun DayViewOptionsDialog(
    calendar: Boolean,
    tracking: Boolean,
    zoom: Boolean,
    zoomScale: Float,
    onResetZoom: () -> Unit,
    onCalendar: (Boolean) -> Unit,
    onTracking: (Boolean) -> Unit,
    onZoom: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.bg,
            contentColor = colors.on,
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Day view", style = TimeboxTheme.type.screenTitle)
                Spacer(Modifier.height(8.dp))
                Text("Choose what stays in view.", style = TimeboxTheme.type.body, color = colors.onVariant)
                Spacer(Modifier.height(20.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    VisibilityRow("Calendar", Icons.Outlined.CalendarToday, calendar, onCalendar)
                    HorizontalDivider(color = colors.hairline)
                    VisibilityRow("Activity Tracking", Icons.Outlined.PlayArrow, tracking, onTracking)
                    HorizontalDivider(color = colors.hairline)
                    VisibilityRow("Zoom", Icons.Outlined.ZoomIn, zoom, onZoom)
                    HorizontalDivider(color = colors.hairline)
                    ResetZoomRow(zoomScale, onResetZoom)
                    Spacer(Modifier.height(16.dp))
                    Text("Activity Tracking keeps running when hidden.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.on, contentColor = colors.bg),
                ) { Text("Done", style = TimeboxTheme.type.button) }
            }
        }
    }
}

@Composable
private fun VisibilityRow(label: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(20.dp))
        Text(label, style = TimeboxTheme.type.label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.planned,
                checkedThumbColor = colors.bg,
                checkedBorderColor = colors.planned,
                uncheckedTrackColor = colors.low,
                uncheckedThumbColor = colors.onVariant,
                uncheckedBorderColor = colors.outlineVariant,
            ),
        )
    }
}

@Composable
private fun ResetZoomRow(scale: Float, onReset: () -> Unit) {
    val colors = TimeboxTheme.colors
    val enabled = scale != 1f
    val content = if (enabled) colors.on else colors.onVariant.copy(alpha = 0.5f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onReset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.RestartAlt, contentDescription = null, tint = if (enabled) colors.onVariant else content, modifier = Modifier.size(20.dp))
        Text("Reset zoom", style = TimeboxTheme.type.label, color = content, modifier = Modifier.weight(1f))
        Text("${"%.1f".format(scale)}×", style = TimeboxTheme.type.monoSmall, color = if (enabled) colors.onVariant else content)
    }
}
