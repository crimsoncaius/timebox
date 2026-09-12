package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxTheme

/** Day activity summary with controls revealed on demand. Mutations remain with the caller. */
@Composable
internal fun CurrentActivityControl(
    activity: String, enabled: Boolean, focusEnabled: Boolean, elapsed: String, running: Boolean, expanded: Boolean,
    onToggle: () -> Unit, onStart: () -> Unit, onSwitch: () -> Unit,
    onFocus: () -> Unit, onStop: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "Tracking disclosure")
    Column(Modifier.fillMaxWidth().animateContentSize(tween(180))) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .clickable(enabled = running || enabled, role = Role.Button, onClickLabel = if (!running) "Start tracking" else if (expanded) "Hide recording controls" else "Show recording controls", onClick = if (running) onToggle else onStart)
                .semantics { stateDescription = if (!running) "Tracking stopped" else if (expanded) "Recording, controls expanded" else "Recording, controls collapsed" }
                .heightIn(min = 52.dp).padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(if (running) colors.actual else colors.onVariant.copy(alpha = .5f)))
                    Text(if (running) "Current activity" else "Tracking stopped", color = colors.onVariant, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = .15.sp)
                }
                Text(if (running) activity else "Start tracking", color = colors.on, fontSize = 14.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (running) Text(elapsed, color = colors.onVariant, fontSize = 13.sp,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(end = 14.dp))
            Icon(if (running) Icons.Rounded.ExpandMore else Icons.Rounded.PlayArrow, contentDescription = null,
                tint = colors.onVariant, modifier = Modifier.size(18.dp).rotate(if (running) rotation else 0f))
        }
        if (running && expanded) {
            HorizontalDivider(Modifier.padding(start = 6.dp, end = 10.dp, top = 2.dp), color = colors.hairline)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = enabled, onClick = onSwitch, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Switch activity", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = focusEnabled, onClick = onFocus, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Rounded.CenterFocusStrong, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Focus", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
                TextButton(enabled = enabled, onClick = onStop, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}
