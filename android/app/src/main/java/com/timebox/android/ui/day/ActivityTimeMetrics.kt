package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.elapsedDurationSeconds
import com.timebox.android.ui.runningTime
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/** Running Time and, when linked to a Planned Block, time until its end. */
@Composable
internal fun ActivityTimeMetrics(
    focus: Boolean,
    elapsedSeconds: Long,
    now: Instant,
    plannedEnd: Instant?,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(if (focus) 5.dp else 1.dp)) {
        Text(
            if (focus) elapsedDurationSeconds(elapsedSeconds) else runningTime(elapsedSeconds),
            style = if (focus) type.display else type.bodySmall, color = colors.on,
        )
        Text("Running Time", style = type.bodySmall, color = colors.onVariant)
        plannedEnd?.let { end ->
            val remaining = Duration.between(now, end).seconds
            val seconds = abs(remaining)
            val time = if (seconds >= 3600)
                String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
            else String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
            Text(
                if (remaining >= 0) "$time left in plan" else "$time over plan",
                style = if (focus) type.sectionTitle else type.bodySmall, color = colors.planned,
            )
        }
    }
}
