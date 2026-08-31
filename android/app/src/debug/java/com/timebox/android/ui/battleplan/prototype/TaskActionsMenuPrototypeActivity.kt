package com.timebox.android.ui.battleplan.prototype

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.util.Locale

/**
 * PROTOTYPE — throwaway code.
 *
 * Three variants of the Battle Plan task-actions dropdown, switchable via
 * timebox://prototype/task-actions-menu?variant=A on this debug-only host.
 */
class TaskActionsMenuPrototypeActivity : ComponentActivity() {
    private var activeVariant by mutableStateOf(PrototypeVariant.A)
    private var darkTheme by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        updateSystemBars()

        setContent {
            TimeboxTheme(darkTheme = darkTheme) {
                TaskActionsMenuPrototype(
                    variant = activeVariant,
                    darkTheme = darkTheme,
                    onVariantChange = ::showVariant,
                    onThemeChange = ::showTheme,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        updateSystemBars()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            showVariant(activeVariant.previous())
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            showVariant(activeVariant.next())
            true
        }
        else -> super.onKeyUp(keyCode, event)
    }

    private fun readIntent(intent: Intent) {
        activeVariant = PrototypeVariant.from(intent.data)
        darkTheme = intent.data?.getQueryParameter("theme") != "light"
    }

    private fun showVariant(variant: PrototypeVariant) {
        activeVariant = variant
        updateUri()
    }

    private fun showTheme(dark: Boolean) {
        darkTheme = dark
        updateUri()
        updateSystemBars()
    }

    private fun updateUri() {
        setIntent(
            Intent(intent).setData(
                Uri.parse(
                    "timebox://prototype/task-actions-menu" +
                        "?variant=${activeVariant.key}&theme=${if (darkTheme) "dark" else "light"}",
                ),
            ),
        )
    }

    private fun updateSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private enum class PrototypeVariant(val key: String, val label: String) {
    A("A", "Section bands"),
    B("B", "Grouped controls"),
    C("C", "Action rail");

    fun previous(): PrototypeVariant = entries[(ordinal - 1 + entries.size) % entries.size]
    fun next(): PrototypeVariant = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(uri: Uri?): PrototypeVariant {
            val requested = uri?.getQueryParameter("variant")?.uppercase(Locale.ENGLISH)
            return entries.firstOrNull { it.key == requested } ?: A
        }
    }
}

private data class PrototypeAction(
    val label: String,
    val icon: ImageVector,
    val tone: ActionTone = ActionTone.Neutral,
)

private enum class ActionTone { Neutral, Open, Active, Complete }

private val reorderActions = listOf(
    PrototypeAction("Move earlier", Icons.Outlined.KeyboardArrowUp),
    PrototypeAction("Move later", Icons.Outlined.KeyboardArrowDown),
)

private val destinationActions = listOf(
    PrototypeAction("Move to In Progress", Icons.Outlined.PlayArrow, ActionTone.Active),
    PrototypeAction("Move to Completed", Icons.Outlined.CheckCircle, ActionTone.Complete),
)

@Composable
private fun TaskActionsMenuPrototype(
    variant: PrototypeVariant,
    darkTheme: Boolean,
    onVariantChange: (PrototypeVariant) -> Unit,
    onThemeChange: (Boolean) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var lastAction by remember { mutableStateOf("No action chosen") }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 88.dp),
        ) {
            PrototypeHeader(
                lastAction = lastAction,
                darkTheme = darkTheme,
                onThemeChange = onThemeChange,
            )
            BattlePlanContext()
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 218.dp, end = 14.dp),
        ) {
            when (variant) {
                PrototypeVariant.A -> SectionBandsMenu(onAction = { lastAction = it })
                PrototypeVariant.B -> GroupedControlsMenu(onAction = { lastAction = it })
                PrototypeVariant.C -> ActionRailMenu(onAction = { lastAction = it })
            }
        }

        PrototypeSwitcher(
            variant = variant,
            onPrevious = { onVariantChange(variant.previous()) },
            onNext = { onVariantChange(variant.next()) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PrototypeHeader(
    lastAction: String,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BATTLE PLAN", style = TimeboxTheme.type.kicker, color = colors.onVariant)
                Text("Tasks", style = TimeboxTheme.type.screenTitle, color = colors.on)
            }
            Row(
                modifier = Modifier
                    .clip(TimeboxShapes.chip)
                    .background(colors.low)
                    .padding(2.dp),
            ) {
                ThemeChoice("Light", selected = !darkTheme) { onThemeChange(false) }
                ThemeChoice("Dark", selected = darkTheme) { onThemeChange(true) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "PROTOTYPE STATE  ·  $lastAction",
            style = TimeboxTheme.type.navLabel,
            color = colors.onVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    Box(
        modifier = Modifier
            .clip(TimeboxShapes.chip)
            .background(if (selected) colors.selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TimeboxTheme.type.navLabel,
            color = if (selected) colors.onSelected else colors.onVariant,
        )
    }
}

@Composable
private fun BattlePlanContext() {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Inbox, contentDescription = null, tint = colors.on, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("All Tasks", style = TimeboxTheme.type.sectionTitle, color = colors.on)
            Spacer(Modifier.weight(1f))
            Text("OPEN  3", style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = TimeboxShapes.card,
            color = colors.card,
        ) {
            Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 16.dp, end = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Prepare the quarterly planning review",
                        style = TimeboxTheme.type.label,
                        color = colors.on,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Admin  ·  2/4 subtasks", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    Spacer(Modifier.height(9.dp))
                    Text("Ready to Plan", style = TimeboxTheme.type.bodySmall, color = colors.planned)
                }
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Outlined.EventAvailable,
                        contentDescription = "Remove from Ready to Plan",
                        tint = colors.planned,
                    )
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Task actions", tint = colors.onVariant)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        repeat(2) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = TimeboxShapes.card,
                color = colors.card.copy(alpha = if (index == 0) 0.72f else 0.5f),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Box(Modifier.fillMaxWidth(0.62f).height(13.dp).clip(TimeboxShapes.chip).background(colors.high))
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.fillMaxWidth(0.38f).height(9.dp).clip(TimeboxShapes.chip).background(colors.low))
                }
            }
        }
    }
}

@Composable
private fun SectionBandsMenu(onAction: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(
        modifier = Modifier.widthIn(min = 286.dp, max = 304.dp).shadow(12.dp, TimeboxShapes.group),
        shape = TimeboxShapes.group,
        color = colors.lowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            SectionBand("Reorder")
            reorderActions.forEach { action -> BandMenuItem(action, onAction) }
            HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 5.dp), color = colors.hairline)
            SectionBand("Move to")
            destinationActions.forEach { action -> BandMenuItem(action, onAction) }
        }
    }
}

@Composable
private fun SectionBand(label: String) {
    val colors = TimeboxTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth().background(colors.low).padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label.uppercase(Locale.ENGLISH), style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
    }
}

@Composable
private fun BandMenuItem(action: PrototypeAction, onAction: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TimeboxDimens.touchTarget)
            .clickable { onAction(action.label) }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(TimeboxShapes.chip).background(colors.low),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, contentDescription = null, tint = toneColor(action.tone), modifier = Modifier.size(18.dp))
        }
        Text(
            action.label,
            style = TimeboxTheme.type.label,
            color = colors.on,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
    }
}

@Composable
private fun GroupedControlsMenu(onAction: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(
        modifier = Modifier.widthIn(min = 300.dp, max = 316.dp).shadow(14.dp, TimeboxShapes.group),
        shape = TimeboxShapes.group,
        color = colors.raised,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("REORDER", style = TimeboxTheme.type.laneLabel, color = colors.onVariant, modifier = Modifier.padding(start = 3.dp, bottom = 7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reorderActions.forEach { action ->
                    CompactActionTile(action = action, onAction = onAction, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("MOVE TO", style = TimeboxTheme.type.laneLabel, color = colors.onVariant, modifier = Modifier.padding(start = 3.dp, bottom = 7.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TimeboxShapes.card)
                    .background(colors.low),
            ) {
                destinationActions.forEachIndexed { index, action ->
                    DestinationRow(action, onAction)
                    if (index != destinationActions.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 46.dp), color = colors.hairline)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactActionTile(
    action: PrototypeAction,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier = modifier
            .height(68.dp)
            .clip(TimeboxShapes.card)
            .background(colors.low)
            .clickable { onAction(action.label) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(action.icon, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(19.dp))
        Text(action.label, style = TimeboxTheme.type.bodySmall, color = colors.on, maxLines = 2)
    }
}

@Composable
private fun DestinationRow(action: PrototypeAction, onAction: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onAction(action.label) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(TimeboxShapes.chip).background(toneColor(action.tone)))
        Spacer(Modifier.width(12.dp))
        Text(action.label, style = TimeboxTheme.type.label, color = colors.on, modifier = Modifier.weight(1f), maxLines = 2)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ActionRailMenu(onAction: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(
        modifier = Modifier.widthIn(min = 306.dp, max = 322.dp).shadow(12.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = colors.lowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            RailGroup("REORDER", reorderActions, onAction)
            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = colors.hairline)
            RailGroup("MOVE TO", destinationActions, onAction)
        }
    }
}

@Composable
private fun RailGroup(
    label: String,
    actions: List<PrototypeAction>,
    onAction: (String) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.width(72.dp).height((actions.size * 52).dp).padding(start = 12.dp, top = 13.dp),
        ) {
            Text(label, style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
        }
        Column(Modifier.weight(1f)) {
            actions.forEach { action -> RailMenuItem(action, onAction) }
        }
    }
}

@Composable
private fun RailMenuItem(action: PrototypeAction, onAction: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onAction(action.label) }
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(3.dp).height(28.dp).clip(TimeboxShapes.chip).background(toneColor(action.tone)),
        )
        Spacer(Modifier.width(12.dp))
        Text(action.label, style = TimeboxTheme.type.label, color = colors.on, modifier = Modifier.weight(1f), maxLines = 2)
        Icon(action.icon, contentDescription = null, tint = colors.onVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun toneColor(tone: ActionTone): Color {
    val colors = TimeboxTheme.colors
    return when (tone) {
        ActionTone.Neutral -> colors.onVariant
        ActionTone.Open -> colors.onVariant
        ActionTone.Active -> colors.actual
        ActionTone.Complete -> colors.planned
    }
}

@Composable
private fun PrototypeSwitcher(
    variant: PrototypeVariant,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF202526)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clickable(onClick = onPrevious), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous variant", tint = Color.White)
        }
        Text(
            "${variant.key} — ${variant.label}",
            style = TimeboxTheme.type.label,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(176.dp),
        )
        Box(Modifier.size(48.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next variant", tint = Color.White)
        }
    }
}
