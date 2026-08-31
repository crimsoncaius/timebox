package com.timebox.android.ui.battleplan.prototype

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Repeat
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
 * Three variants of the mobile Battle Plan scope dropdown opened from “All Tasks”,
 * switchable via timebox://prototype/scope-menu?variant=A in a debug build.
 */
class ScopeMenuPrototypeActivity : ComponentActivity() {
    private var activeVariant by mutableStateOf(PrototypeVariant.A)
    private var darkTheme by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        updateSystemBars()

        setContent {
            TimeboxTheme(darkTheme = darkTheme) {
                ScopeMenuPrototype(
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
        darkTheme = intent.data?.getQueryParameter("theme") == "dark"
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
                    "timebox://prototype/scope-menu" +
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
    A("A", "Reference table"),
    B("B", "Inset groups"),
    C("C", "Section rail");

    fun previous(): PrototypeVariant = entries[(ordinal - 1 + entries.size) % entries.size]
    fun next(): PrototypeVariant = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(uri: Uri?): PrototypeVariant {
            val requested = uri?.getQueryParameter("variant")?.uppercase(Locale.ENGLISH)
            return entries.firstOrNull { it.key == requested } ?: A
        }
    }
}

private data class ScopeAction(
    val label: String,
    val icon: ImageVector,
    val selectable: Boolean = false,
    val destructive: Boolean = false,
)

private val taskActions = listOf(
    ScopeAction("All Tasks", Icons.AutoMirrored.Outlined.ListAlt, selectable = true),
    ScopeAction("Admin", Icons.Outlined.Inbox, selectable = true),
)

private val projectActions = listOf(
    ScopeAction("New project", Icons.Outlined.Add),
)

private val libraryActions = listOf(
    ScopeAction("Recurring", Icons.Outlined.Repeat),
    ScopeAction("Archive", Icons.Outlined.Archive),
    ScopeAction("Trash", Icons.Outlined.Delete, destructive = true),
)

@Composable
private fun ScopeMenuPrototype(
    variant: PrototypeVariant,
    darkTheme: Boolean,
    onVariantChange: (PrototypeVariant) -> Unit,
    onThemeChange: (Boolean) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var selectedScope by remember { mutableStateOf("All Tasks") }
    var lastAction by remember { mutableStateOf("Menu open") }
    val onAction: (ScopeAction) -> Unit = { action ->
        if (action.selectable) selectedScope = action.label
        lastAction = action.label
    }

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
            BattlePlanContext(selectedScope = selectedScope)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 218.dp),
        ) {
            when (variant) {
                PrototypeVariant.A -> ReferenceTableMenu(selectedScope, onAction)
                PrototypeVariant.B -> InsetGroupsMenu(selectedScope, onAction)
                PrototypeVariant.C -> SectionRailMenu(selectedScope, onAction)
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
                modifier = Modifier.clip(TimeboxShapes.chip).background(colors.low).padding(2.dp),
            ) {
                ThemeChoice("Light", selected = !darkTheme) { onThemeChange(false) }
                ThemeChoice("Dark", selected = darkTheme) { onThemeChange(true) }
            }
        }
        Spacer(Modifier.height(9.dp))
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
private fun BattlePlanContext(selectedScope: String) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = TimeboxDimens.touchTarget)
                    .clip(TimeboxShapes.chip)
                    .clickable { }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (selectedScope == "Admin") Icons.Outlined.Inbox else Icons.AutoMirrored.Outlined.ListAlt,
                    contentDescription = null,
                    tint = colors.on,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(selectedScope, style = TimeboxTheme.type.label, color = colors.on)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = colors.onVariant)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.FilterList, contentDescription = "Filter tasks", tint = colors.on)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("Open  3", "In Progress  1", "Completed  0").forEachIndexed { index, label ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = TimeboxTheme.type.bodySmall, color = if (index == 0) colors.on else colors.onVariant)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(38.dp).height(2.dp).background(if (index == 0) colors.on else colors.bg))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        repeat(2) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = TimeboxShapes.card,
                color = colors.card.copy(alpha = if (index == 0) 0.76f else 0.52f),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Box(Modifier.fillMaxWidth(if (index == 0) 0.68f else 0.52f).height(13.dp).clip(TimeboxShapes.chip).background(colors.high))
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.fillMaxWidth(0.36f).height(9.dp).clip(TimeboxShapes.chip).background(colors.low))
                }
            }
        }
    }
}

@Composable
private fun ReferenceTableMenu(selectedScope: String, onAction: (ScopeAction) -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(
        modifier = Modifier.width(304.dp).shadow(10.dp, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = colors.lowest,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(Modifier.padding(vertical = 5.dp)) {
            TableSectionBand("Tasks")
            taskActions.forEach { TableMenuItem(it, selectedScope, onAction) }
            TableSectionBand("Projects")
            projectActions.forEach { TableMenuItem(it, selectedScope, onAction) }
            HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 5.dp), color = colors.hairline)
            TableSectionBand("Library")
            libraryActions.forEach { TableMenuItem(it, selectedScope, onAction) }
        }
    }
}

@Composable
private fun TableSectionBand(label: String) {
    val colors = TimeboxTheme.colors
    Box(Modifier.fillMaxWidth().background(colors.low).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label.uppercase(Locale.ENGLISH), style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
    }
}

@Composable
private fun TableMenuItem(
    action: ScopeAction,
    selectedScope: String,
    onAction: (ScopeAction) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val selected = action.label == selectedScope
    val contentColor = if (action.destructive) colors.error else colors.on
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (selected) colors.surf else colors.lowest)
            .clickable { onAction(action) }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(13.dp))
        Text(action.label, style = TimeboxTheme.type.label, color = contentColor, modifier = Modifier.weight(1f), maxLines = 2)
        if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = colors.on, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InsetGroupsMenu(selectedScope: String, onAction: (ScopeAction) -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(
        modifier = Modifier.width(316.dp).shadow(12.dp, TimeboxShapes.group),
        shape = TimeboxShapes.group,
        color = colors.raised,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InsetSection("Tasks", taskActions, selectedScope, onAction)
            InsetSection("Projects", projectActions, selectedScope, onAction)
            InsetSection("Library", libraryActions, selectedScope, onAction)
        }
    }
}

@Composable
private fun InsetSection(
    label: String,
    actions: List<ScopeAction>,
    selectedScope: String,
    onAction: (ScopeAction) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column {
        Text(
            label.uppercase(Locale.ENGLISH),
            style = TimeboxTheme.type.laneLabel,
            color = colors.onVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.low)) {
            actions.forEachIndexed { index, action ->
                InsetMenuItem(action, selectedScope, onAction)
                if (index != actions.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 48.dp), color = colors.hairline)
                }
            }
        }
    }
}

@Composable
private fun InsetMenuItem(
    action: ScopeAction,
    selectedScope: String,
    onAction: (ScopeAction) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val selected = action.label == selectedScope
    val contentColor = if (action.destructive) colors.error else colors.on
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (selected) colors.selected else Color.Transparent)
            .clickable { onAction(action) }
            .padding(horizontal = 13.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(TimeboxShapes.chip).background(if (selected) colors.lowest else colors.raised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(action.label, style = TimeboxTheme.type.label, color = contentColor, modifier = Modifier.weight(1f), maxLines = 2)
        if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = colors.on, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SectionRailMenu(selectedScope: String, onAction: (ScopeAction) -> Unit) {
    val colors = TimeboxTheme.colors
    Surface(
        modifier = Modifier.widthIn(min = 316.dp, max = 324.dp).shadow(10.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = colors.lowest,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(Modifier.padding(vertical = 7.dp)) {
            RailSection("TASKS", taskActions, selectedScope, onAction)
            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = colors.hairline)
            RailSection("PROJECTS", projectActions, selectedScope, onAction)
            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = colors.hairline)
            RailSection("LIBRARY", libraryActions, selectedScope, onAction)
        }
    }
}

@Composable
private fun RailSection(
    label: String,
    actions: List<ScopeAction>,
    selectedScope: String,
    onAction: (ScopeAction) -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.width(78.dp).height((actions.size * 48).dp).padding(start = 13.dp, top = 16.dp),
        ) {
            Text(label, style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
        }
        Column(Modifier.weight(1f)) {
            actions.forEach { action ->
                val selected = action.label == selectedScope
                val contentColor = if (action.destructive) colors.error else colors.on
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                        .background(if (selected) colors.surf else Color.Transparent)
                        .clickable { onAction(action) }
                        .padding(start = 10.dp, end = 13.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(action.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(11.dp))
                    Text(action.label, style = TimeboxTheme.type.label, color = contentColor, modifier = Modifier.weight(1f), maxLines = 2)
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = colors.on, modifier = Modifier.size(19.dp))
                }
            }
        }
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
