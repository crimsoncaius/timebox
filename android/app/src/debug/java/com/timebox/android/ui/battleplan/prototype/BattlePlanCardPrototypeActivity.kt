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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.timebox.android.ui.theme.TimeboxTheme
import java.util.Locale

/**
 * PROTOTYPE — throwaway code.
 *
 * Five variants of the mobile Battle Plan task card, switchable via
 * timebox://prototype/battle-plan-card?variant=A in a debug build.
 */
class BattlePlanCardPrototypeActivity : ComponentActivity() {
    private var activeVariant by mutableStateOf(CardVariant.A)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        activeVariant = CardVariant.from(intent.data)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            TimeboxTheme(darkTheme = true) {
                BattlePlanCardPrototype(
                    variant = activeVariant,
                    onVariantChange = ::showVariant,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeVariant = CardVariant.from(intent.data)
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

    private fun showVariant(variant: CardVariant) {
        activeVariant = variant
        setIntent(
            Intent(intent).setData(
                Uri.parse("timebox://prototype/battle-plan-card?variant=${variant.key}"),
            ),
        )
    }
}

private enum class CardVariant(val key: String, val label: String) {
    A("A", "Signal rail"),
    B("B", "Action footer"),
    C("C", "Inline blocker"),
    D("D", "Metadata blocker"),
    E("E", "Quiet footer");

    fun previous(): CardVariant = entries[(ordinal - 1 + entries.size) % entries.size]
    fun next(): CardVariant = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(uri: Uri?): CardVariant {
            val requested = uri?.getQueryParameter("variant")?.uppercase(Locale.ENGLISH)
            return entries.firstOrNull { it.key == requested } ?: A
        }
    }
}

private enum class PrototypePlanState(val key: String, val label: String) {
    AddToReady("add_to_ready", "Add to Ready to Plan"),
    Ready("ready", "Ready to Plan"),
    Planned("planned", "Planned · Jan 1, 2099");

    fun next(): PrototypePlanState = entries[(ordinal + 1) % entries.size]
}

private val PrototypePlanState.icon: ImageVector
    get() = when (this) {
        PrototypePlanState.AddToReady -> Icons.Outlined.EventAvailable
        PrototypePlanState.Ready -> Icons.Outlined.CheckCircle
        PrototypePlanState.Planned -> Icons.Outlined.CalendarToday
    }

@Composable
private fun BattlePlanCardPrototype(
    variant: CardVariant,
    onVariantChange: (CardVariant) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var planningState by rememberSaveable { mutableStateOf(PrototypePlanState.Planned) }
    var blocked by rememberSaveable { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 92.dp),
        ) {
            PrototypeStatusTabs()
            Text(
                "CARD REDESIGN PROTOTYPE",
                style = TimeboxTheme.type.kicker,
                color = colors.onVariant,
                modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 10.dp),
            )

            when (variant) {
                CardVariant.A -> SignalRailCard(
                    planningState = planningState,
                    blocked = blocked,
                    onCyclePlanningState = { planningState = planningState.next() },
                    onToggleBlocked = { blocked = !blocked },
                )
                CardVariant.B -> ActionFooterCard(
                    planningState = planningState,
                    blocked = blocked,
                    onCyclePlanningState = { planningState = planningState.next() },
                    onToggleBlocked = { blocked = !blocked },
                )
                CardVariant.C -> InlineBlockerCard(
                    planningState = planningState,
                    blocked = blocked,
                    onCyclePlanningState = { planningState = planningState.next() },
                    onToggleBlocked = { blocked = !blocked },
                )
                CardVariant.D -> MetadataBlockerCard(
                    planningState = planningState,
                    blocked = blocked,
                    onCyclePlanningState = { planningState = planningState.next() },
                    onToggleBlocked = { blocked = !blocked },
                )
                CardVariant.E -> QuietFooterCard(
                    planningState = planningState,
                    blocked = blocked,
                    onCyclePlanningState = { planningState = planningState.next() },
                    onToggleBlocked = { blocked = !blocked },
                )
            }

            PrototypeState(planningState = planningState, blocked = blocked)
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
private fun PrototypeStatusTabs() {
    val colors = TimeboxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        StatusTab("Open", 1, selected = true)
        StatusTab("In Progress", 0, selected = false)
        StatusTab("Completed", 0, selected = false)
    }
    HorizontalDivider(color = colors.hairline)
}

@Composable
private fun StatusTab(label: String, count: Int, selected: Boolean) {
    val colors = TimeboxTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(label, style = TimeboxTheme.type.bodySmall, color = if (selected) colors.on else colors.onVariant)
            Text(count.toString(), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
        }
        Box(
            Modifier
                .height(2.dp)
                .width(if (selected) 38.dp else 0.dp)
                .background(colors.on),
        )
    }
}

@Composable
private fun SignalRailCard(
    planningState: PrototypePlanState,
    blocked: Boolean,
    onCyclePlanningState: () -> Unit,
    onToggleBlocked: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (blocked) colors.error else colors.planned),
        )
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ADMIN",
                    style = TimeboxTheme.type.laneLabel,
                    color = colors.onVariant,
                    modifier = Modifier.weight(1f),
                )
                if (blocked) StatusPill("BLOCKED", colors.error, colors.error.copy(alpha = 0.12f), onToggleBlocked)
            }
            Text(
                "x",
                style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, fontWeight = FontWeight.Medium),
                color = colors.on,
                modifier = Modifier.padding(top = 6.dp),
            )
            PrioritySignals(Modifier.padding(top = 9.dp))
            if (blocked) {
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.ReportProblem, null, tint = colors.error, modifier = Modifier.size(17.dp))
                    Column {
                        Text("BLOCKED BY", style = TimeboxTheme.type.laneLabel, color = colors.error)
                        Text("test reason", style = TimeboxTheme.type.body, color = colors.on)
                    }
                }
            }
            Row(
                Modifier
                    .padding(top = 15.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onCyclePlanningState)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    planningState.icon,
                    null,
                    tint = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    planningState.label,
                    style = TimeboxTheme.type.bodySmall,
                    color = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                    modifier = Modifier.padding(start = 7.dp).weight(1f),
                )
                PrototypeMoreAction()
            }
        }
    }
}

@Composable
private fun ActionFooterCard(
    planningState: PrototypePlanState,
    blocked: Boolean,
    onCyclePlanningState: () -> Unit,
    onToggleBlocked: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ADMIN TASK", style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
                Spacer(Modifier.weight(1f))
                PrototypeMoreAction()
            }
            Text(
                "x",
                style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, fontWeight = FontWeight.Medium),
                color = colors.on,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Admin", style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.weight(1f))
                PrioritySignals()
            }
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (planningState == PrototypePlanState.AddToReady) colors.lowest else colors.plannedSurface)
                    .border(1.dp, if (planningState == PrototypePlanState.AddToReady) colors.hairline else colors.plannedBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onCyclePlanningState)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        planningState.icon,
                        null,
                        tint = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        planningState.label,
                        style = TimeboxTheme.type.label,
                        color = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (blocked) colors.error.copy(alpha = 0.12f) else colors.lowest)
                .clickable(onClick = onToggleBlocked)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ReportProblem, null, tint = if (blocked) colors.error else colors.onVariant, modifier = Modifier.size(18.dp))
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text(if (blocked) "Blocked" else "Not blocked", style = TimeboxTheme.type.label, color = if (blocked) colors.error else colors.onVariant)
                if (blocked) Text("test reason", style = TimeboxTheme.type.bodySmall, color = colors.on)
            }
            Text(if (blocked) "UNBLOCK" else "BLOCK", style = TimeboxTheme.type.laneLabel, color = if (blocked) colors.error else colors.onVariant)
        }
    }
}

@Composable
private fun InlineBlockerCard(
    planningState: PrototypePlanState,
    blocked: Boolean,
    onCyclePlanningState: () -> Unit,
    onToggleBlocked: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ADMIN TASK", style = TimeboxTheme.type.laneLabel, color = colors.onVariant, modifier = Modifier.weight(1f))
                PrototypeMoreAction()
            }
            Text(
                "x",
                style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, fontWeight = FontWeight.Medium),
                color = colors.on,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Admin", style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.weight(1f))
                PrioritySignals()
            }
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (planningState == PrototypePlanState.AddToReady) colors.lowest else colors.plannedSurface)
                    .border(1.dp, if (planningState == PrototypePlanState.AddToReady) colors.hairline else colors.plannedBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onCyclePlanningState)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    planningState.icon,
                    null,
                    tint = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    planningState.label,
                    style = TimeboxTheme.type.label,
                    color = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleBlocked)
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (blocked) colors.error else colors.outlineVariant),
                )
                Text(
                    if (blocked) "Blocked · test reason" else "Not blocked",
                    style = TimeboxTheme.type.bodySmall,
                    color = if (blocked) colors.error else colors.onVariant,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                Text(if (blocked) "CLEAR" else "BLOCK", style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
            }
        }
    }
}

@Composable
private fun MetadataBlockerCard(
    planningState: PrototypePlanState,
    blocked: Boolean,
    onCyclePlanningState: () -> Unit,
    onToggleBlocked: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ADMIN TASK", style = TimeboxTheme.type.laneLabel, color = colors.onVariant, modifier = Modifier.weight(1f))
                if (blocked) StatusPill("BLOCKED", colors.error, colors.error.copy(alpha = 0.08f), onToggleBlocked)
                PrototypeMoreAction()
            }
            Text(
                "x",
                style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, fontWeight = FontWeight.Medium),
                color = colors.on,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Admin", style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.weight(1f))
                PrioritySignals()
            }
            if (blocked) {
                Text(
                    "Blocker: test reason",
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                    modifier = Modifier.padding(top = 9.dp).clickable(onClick = onToggleBlocked),
                )
            }
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (planningState == PrototypePlanState.AddToReady) colors.lowest else colors.plannedSurface)
                    .border(1.dp, if (planningState == PrototypePlanState.AddToReady) colors.hairline else colors.plannedBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onCyclePlanningState)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    planningState.icon,
                    null,
                    tint = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    planningState.label,
                    style = TimeboxTheme.type.label,
                    color = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuietFooterCard(
    planningState: PrototypePlanState,
    blocked: Boolean,
    onCyclePlanningState: () -> Unit,
    onToggleBlocked: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ADMIN TASK", style = TimeboxTheme.type.laneLabel, color = colors.onVariant, modifier = Modifier.weight(1f))
                PrototypeMoreAction()
            }
            Text(
                "x",
                style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, fontWeight = FontWeight.Medium),
                color = colors.on,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Admin", style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.weight(1f))
                PrioritySignals()
            }
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (planningState == PrototypePlanState.AddToReady) colors.lowest else colors.plannedSurface)
                    .border(1.dp, if (planningState == PrototypePlanState.AddToReady) colors.hairline else colors.plannedBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onCyclePlanningState)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    planningState.icon,
                    null,
                    tint = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    planningState.label,
                    style = TimeboxTheme.type.label,
                    color = if (planningState == PrototypePlanState.AddToReady) colors.onVariant else colors.planned,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        HorizontalDivider(color = colors.hairline)
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.lowest)
                .clickable(onClick = onToggleBlocked)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (blocked) colors.error else colors.outlineVariant),
            )
            Text(
                if (blocked) "Blocked" else "Not blocked",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            if (blocked) {
                Text(" · test reason", style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.weight(1f))
                Text("CLEAR", style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
            } else {
                Spacer(Modifier.weight(1f))
                Text("BLOCK", style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, contentColor: Color, containerColor: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(label, style = TimeboxTheme.type.laneLabel, color = contentColor)
    }
}

@Composable
private fun PrioritySignals(modifier: Modifier = Modifier) {
    val colors = TimeboxTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PriorityPill("URGENT", colors.error, colors.error.copy(alpha = 0.12f))
        PriorityPill("IMPORTANT", colors.planned, colors.plannedSurface)
    }
}

@Composable
private fun PriorityPill(label: String, contentColor: Color, containerColor: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, style = TimeboxTheme.type.laneLabel.copy(fontSize = 9.sp), color = contentColor)
    }
}

@Composable
private fun PrototypeMoreAction() {
    val colors = TimeboxTheme.colors
    IconButton(onClick = {}, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "Task actions", tint = colors.onVariant)
    }
}

@Composable
private fun PrototypeState(planningState: PrototypePlanState, blocked: Boolean) {
    val colors = TimeboxTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .fillMaxWidth()
            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("PROTOTYPE STATE", style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
        Text(
            "planningState=${planningState.key}  ·  blocked=$blocked\nurgent=high  ·  important=high  ·  plannedDate=${if (planningState == PrototypePlanState.Planned) "2099-01-01" else "none"}",
            style = TimeboxTheme.type.monoSmall,
            color = colors.on,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun PrototypeSwitcher(
    variant: CardVariant,
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
            .background(Color(0xFFF5F5F4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clickable(onClick = onPrevious), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous variant", tint = Color(0xFF202526))
        }
        Text(
            "${variant.key} — ${variant.label}",
            style = TimeboxTheme.type.label,
            color = Color(0xFF202526),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(154.dp),
        )
        Box(Modifier.size(48.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next variant", tint = Color(0xFF202526))
        }
    }
}
