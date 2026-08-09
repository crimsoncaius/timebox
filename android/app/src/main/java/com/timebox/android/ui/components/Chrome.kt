package com.timebox.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme

/** Kicker + large title, with the theme toggle and (on Day) the review shortcut. */
@Composable
fun TimeboxTopBar(
    kicker: String,
    title: String,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenReview: (() -> Unit)? = null,
) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kicker.uppercase(),
                style = TimeboxTheme.type.kicker,
                color = colors.onVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = TimeboxTheme.type.screenTitle,
                color = colors.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onOpenReview != null) {
            RoundIconButton(
                icon = Icons.Outlined.Insights,
                contentDescription = "Day review",
                onClick = onOpenReview,
            )
        }
        RoundIconButton(
            icon = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = "Toggle theme",
            onClick = onToggleTheme,
        )
    }
}

enum class TimeboxTab(val label: String, val icon: ImageVector) {
    Day("Day", Icons.Outlined.CalendarToday),
    Chronicle("Chronicle", Icons.Outlined.History),
    Types("Types", Icons.Outlined.Category),
    Settings("Settings", Icons.Outlined.Settings),
}

@Composable
fun TimeboxBottomNav(
    selected: TimeboxTab,
    onSelect: (TimeboxTab) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.low),
    ) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TimeboxTab.entries.forEach { tab ->
                NavItem(
                    tab = tab,
                    active = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: TimeboxTab,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    val pill by animateColorAsState(
        targetValue = when {
            !active -> Color.Transparent
            colors.isDark -> Color(0x29F5F5F4)
            else -> colors.high
        },
        label = "navPill",
    )
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = colors.on,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(tab.label, style = TimeboxTheme.type.navLabel, color = colors.on, maxLines = 1)
    }
}
