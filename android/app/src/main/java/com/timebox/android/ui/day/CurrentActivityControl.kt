package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxTheme
import java.util.Locale

/** Running Time as a clock: `m:ss` under an hour, `h:mm:ss` from then on. */
internal fun runningClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/** What the Now card says about the Planned Block covering now, and what tapping it does. */
internal sealed interface PlanHint {
    val text: String

    /** The Current Activity is already counted toward the plan. */
    data class OnPlan(override val text: String = "matches your plan") : PlanHint

    /** Same activity as the plan but not yet counted toward it. */
    data class CountToward(override val text: String) : PlanHint

    /** A different activity is planned now; tapping switches to it. */
    data class SwitchTo(override val text: String) : PlanHint
}

/**
 * The Day's Now card: what is running, a display-size clock, and Switch and Stop one tap
 * away. When nothing runs it collapses to a single Start row. Mutations remain with the caller.
 */
@Composable
internal fun CurrentActivityControl(
    activity: String, enabled: Boolean, focusEnabled: Boolean, elapsedSeconds: Long, running: Boolean,
    onStart: () -> Unit, onNotes: () -> Unit, onSwitch: () -> Unit,
    onFocus: () -> Unit, onStop: () -> Unit, secondary: String? = null,
    since: String? = null,
    planHint: PlanHint? = null,
    onPlanHint: () -> Unit = {},
    onEdit: (() -> Unit)? = null,
) {
    val colors = TimeboxTheme.colors
    if (!running) {
        // The stopped bar keeps the running card's teal edge and tint, so both read as the Actual lane.
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp)).background(colors.actualBlock)
                .semantics { stateDescription = "Tracking stopped" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(colors.actual))
            Text(
                "Not tracking",
                color = colors.onVariant, style = TimeboxTheme.type.body,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            )
            Button(
                enabled = enabled, onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = colors.actual, contentColor = colors.bg),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(end = 10.dp).heightIn(min = 40.dp)
                    // Read as "Start tracking" so it is never confused with a sheet's own Start.
                    .clearAndSetSemantics {
                        contentDescription = "Start tracking"
                        role = Role.Button
                        if (enabled) onClick { onStart(); true }
                    },
            ) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start", style = TimeboxTheme.type.button.copy(fontWeight = FontWeight.SemiBold))
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp)).background(colors.actualBlock)
            .semantics { stateDescription = "Recording" },
    ) {
        // The teal edge ties the card to the Actual lane it writes to.
        Box(Modifier.width(4.dp).fillMaxHeight().background(colors.actual))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(colors.actual))
                        Spacer(Modifier.width(6.dp))
                        Text("NOW TRACKING", color = colors.actual, style = TimeboxTheme.type.laneLabel.copy(fontWeight = FontWeight.Bold))
                    }
                    Text(
                        activity, color = colors.on, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                            .then(if (onEdit != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = "Edit current activity", onClick = onEdit) else Modifier),
                    )
                }
                Text(
                    runningClock(elapsedSeconds),
                    style = TimeboxTheme.type.body.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                    color = colors.on, maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = "Running Time ${com.timebox.android.ui.runningTime(elapsedSeconds)}" },
                )
            }
            val meta = listOfNotNull(secondary, since?.let { "since $it" })
            if (meta.isNotEmpty() || planHint != null) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (meta.isNotEmpty()) Text(
                        meta.joinToString(" · "), color = colors.onVariant, style = TimeboxTheme.type.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (planHint != null) {
                        if (meta.isNotEmpty()) Text(" · ", color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
                        val actionable = planHint !is PlanHint.OnPlan
                        Text(
                            planHint.text,
                            color = colors.planned,
                            style = TimeboxTheme.type.bodySmall.copy(fontWeight = if (actionable) FontWeight.Medium else FontWeight.Normal),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).then(
                                if (actionable) Modifier.clip(RoundedCornerShape(6.dp)).clickable(
                                    enabled = enabled, role = Role.Button,
                                    onClickLabel = if (planHint is PlanHint.CountToward) "Count toward plan" else "Switch to planned activity",
                                    onClick = onPlanHint,
                                ) else Modifier,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = enabled, onClick = onSwitch, modifier = Modifier.heightIn(min = 40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.on, contentColor = colors.bg),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Switch", style = TimeboxTheme.type.button.copy(fontWeight = FontWeight.SemiBold))
                }
                OutlinedButton(
                    enabled = enabled, onClick = onStop, modifier = Modifier.heightIn(min = 40.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.lowest, contentColor = colors.on),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(Icons.Rounded.CropSquare, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop", style = TimeboxTheme.type.button.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.weight(1f))
                RoundCardAction(Icons.AutoMirrored.Rounded.Notes, "Notes", enabled, onNotes)
                RoundCardAction(Icons.Rounded.CenterFocusStrong, "Focus", focusEnabled, onFocus)
            }
        }
    }
}

/** A small circular secondary action on the Now card. */
@Composable
private fun RoundCardAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(colors.lowest)
            .border(1.dp, colors.hairline, RoundedCornerShape(50))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) colors.on else colors.onVariant.copy(alpha = .5f), modifier = Modifier.size(18.dp))
    }
}
