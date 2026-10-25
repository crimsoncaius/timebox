package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timebox.android.data.ApiError
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Why a Day could not be loaded, in the terms the user acts on. Raw error text is never shown. */
enum class LoadProblem { Offline, ServerProblem, SignedOut, Other }

fun ApiError.loadProblem(): LoadProblem = when {
    isNetwork -> LoadProblem.Offline
    isAuth -> LoadProblem.SignedOut
    (statusCode ?: 0) >= 500 -> LoadProblem.ServerProblem
    else -> LoadProblem.Other
}

/** The cause line under "Couldn't load", when nothing for the date is on the phone. */
internal fun neverLoadedCause(problem: LoadProblem): String = when (problem) {
    LoadProblem.Offline -> "You're offline. Connect to load your planned and tracked time. Anything you track now is kept and will sync."
    LoadProblem.ServerProblem -> "The server isn't responding right now. Anything you track now is kept and will sync."
    LoadProblem.SignedOut -> "This server needs a valid API key. Check it in Settings."
    LoadProblem.Other -> "Something went wrong."
}

/** The status line above a Day that is shown but could not be refreshed. */
internal fun staleDayStatus(problem: LoadProblem): String = when (problem) {
    LoadProblem.Offline -> "You're offline. Showing what was last loaded. Tracking still works."
    LoadProblem.ServerProblem -> "The server isn't responding. Showing what was last loaded."
    LoadProblem.SignedOut -> "This server needs a valid API key. Check it in Settings."
    LoadProblem.Other -> "Couldn't refresh this day. Showing what was last loaded."
}

private val couldNotLoadFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

/** Shown instead of the lanes when a date has never loaded, so it cannot pass for an empty day. */
@Composable
internal fun DayCouldNotLoad(date: LocalDate, problem: LoadProblem, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TimeboxDimens.screenPadding, vertical = 24.dp)
            .testTag("day-could-not-load")
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Couldn't load ${date.format(couldNotLoadFormatter)}",
            style = TimeboxTheme.type.sectionTitle.copy(fontWeight = FontWeight.Medium),
            color = colors.on,
        )
        Text(neverLoadedCause(problem), style = TimeboxTheme.type.body, color = colors.onVariant)
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp)) {
            Text("Try again", style = TimeboxTheme.type.button, color = colors.on)
        }
    }
}

/** One quiet line with Retry above a Day whose latest refresh failed. */
@Composable
internal fun StaleDayStatus(problem: LoadProblem, onRetry: () -> Unit) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TimeboxDimens.screenPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.low)
            .testTag("day-stale-status")
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(staleDayStatus(problem), style = TimeboxTheme.type.bodySmall, color = colors.on, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        Text(
            "Retry",
            style = TimeboxTheme.type.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = colors.planned,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onRetry)
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        )
    }
}
