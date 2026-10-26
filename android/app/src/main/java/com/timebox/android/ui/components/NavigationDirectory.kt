package com.timebox.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
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
internal fun NavigationDirectory(currentRoute: String, onSelect: (String) -> Unit, onEditShortcuts: (() -> Unit)? = null) {
    val colors = TimeboxTheme.colors
    val groupShape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxSize().background(colors.bg).pointerInput(Unit) { detectTapGestures { } }.semantics { paneTitle = "Home" }
        .statusBarsPadding().navigationBarsPadding()) {
        Text("Home", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp).semantics { heading() },
            style = TimeboxTheme.type.display, color = colors.on)
        Hairline()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = LocalNavigationDockInset.current + 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            navigationDestinations.groupBy { it.group }.forEach { (group, destinations) ->
                Column(Modifier.fillMaxWidth().clip(groupShape).background(colors.low)
                    .border(1.dp, colors.hairline, groupShape).padding(8.dp)) {
                    Text(group.uppercase(Locale.ROOT), Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
                        .semantics { heading() }, style = TimeboxTheme.type.laneLabel.copy(fontSize = 11.sp), color = colors.onVariant)
                    if (group == "Preferences" && onEditShortcuts != null) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onEditShortcuts).heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Tune, null, Modifier.size(24.dp), tint = colors.onVariant)
                            Text("Edit shortcuts", Modifier.weight(1f).padding(horizontal = 16.dp),
                                style = TimeboxTheme.type.body, color = colors.on)
                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = colors.onVariant)
                        }
                    }
                    if (group == "Preferences" && onEditShortcuts != null) Hairline(Modifier.padding(horizontal = 12.dp))
                    destinations.forEachIndexed { index, destination ->
                        val current = currentRoute == destination.route
                        if (index > 0 && !current && currentRoute != destinations[index - 1].route) {
                            Hairline(Modifier.padding(horizontal = 12.dp))
                        }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (current) colors.high else Color.Transparent)
                            .semantics { selected = current }
                            .clickable(onClickLabel = "Open ${destination.label}") { onSelect(destination.route) }
                            .heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(destination.icon, null, Modifier.size(24.dp), tint = colors.onVariant)
                            Text(destination.label, Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
                                style = TimeboxTheme.type.body, color = colors.on)
                            if (current) {
                                Text("Current", Modifier.clip(RoundedCornerShape(50)).background(colors.highest)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = TimeboxTheme.type.bodySmall, color = colors.on)
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = colors.onVariant)
                        }
                    }
                }
            }
        }
    }
}
