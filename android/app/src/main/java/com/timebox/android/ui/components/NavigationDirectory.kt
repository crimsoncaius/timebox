package com.timebox.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.AppRoutes
import com.timebox.android.ui.theme.TimeboxTheme

internal val LocalNavigationBlockers = compositionLocalOf<MutableState<Int>?> { null }

/** Editors and local detail surfaces temporarily suspend global navigation. */
@Composable
internal fun HideNavigate() {
    val blockers = LocalNavigationBlockers.current
    DisposableEffect(blockers) {
        blockers?.let { it.value++ }
        onDispose { blockers?.let { it.value-- } }
    }
}

internal data class NavigationDestination(val group: String, val label: String, val route: String, val icon: ImageVector)

internal val navigationDestinations = listOf(
    NavigationDestination("Daily work", "Day", AppRoutes.DayPattern, Icons.Outlined.CalendarToday),
    NavigationDestination("Daily work", "Battle Plan", AppRoutes.BattlePlan, Icons.Outlined.Checklist),
    NavigationDestination("Chronicle", "Calendar", AppRoutes.Chronicle, Icons.Outlined.CalendarMonth),
    NavigationDestination("Chronicle", "Trends", AppRoutes.Trends, Icons.Outlined.ShowChart),
    NavigationDestination("Routines and goals", "Habits", AppRoutes.Habits, Icons.Outlined.DoneAll),
    NavigationDestination("Routines and goals", "Recurring Tasks", AppRoutes.Recurring, Icons.Outlined.Repeat),
    NavigationDestination("Routines and goals", "Time Goals", AppRoutes.TimeGoals, Icons.Outlined.Flag),
    NavigationDestination("Assistant", "Conversation", AppRoutes.Assistant, Icons.Outlined.ChatBubbleOutline),
    NavigationDestination("Assistant", "Review", AppRoutes.Review, Icons.Outlined.FactCheck),
    NavigationDestination("Organization", "Task Types", AppRoutes.Types, Icons.Outlined.Category),
    NavigationDestination("Preferences", "Settings", AppRoutes.Settings, Icons.Outlined.Settings),
)

@Composable
internal fun NavigateButton(onOpen: () -> Unit) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding()) {
        Hairline()
        TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)) {
            Icon(Icons.Outlined.GridView, null, Modifier.size(20.dp), tint = colors.on)
            Spacer(Modifier.width(10.dp))
            Text("Navigate", style = TimeboxTheme.type.label, color = colors.on)
        }
    }
}

@Composable
internal fun NavigationDirectory(currentRoute: String, onClose: () -> Unit, onSelect: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bg).pointerInput(Unit) { detectTapGestures { } }.semantics { paneTitle = "Timebox navigation" }
        .statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Timebox", Modifier.weight(1f).semantics { heading() }, style = TimeboxTheme.type.screenTitle, color = colors.on)
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close navigation", tint = colors.on) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            navigationDestinations.groupBy { it.group }.forEach { (group, destinations) ->
                Text(group, Modifier.padding(start = 8.dp, top = 20.dp, bottom = 6.dp).semantics { heading() },
                    style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                destinations.forEach { destination ->
                    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${destination.label}") { onSelect(destination.route) }
                        .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(destination.icon, null, Modifier.size(21.dp), tint = colors.onVariant)
                        Spacer(Modifier.width(14.dp))
                        Text(destination.label, Modifier.weight(1f), style = TimeboxTheme.type.body, color = colors.on)
                        if (currentRoute == destination.route) {
                            Text("Current", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                        }
                    }
                }
            }
        }
    }
}
