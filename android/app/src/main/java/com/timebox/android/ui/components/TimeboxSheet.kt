package com.timebox.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.timebox.android.data.Lane
import com.timebox.android.ui.theme.BodyFamily
import com.timebox.android.ui.theme.HeadlineFamily
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

/**
 * The right-hand footer button of a [TimeboxSheet] or [TimeboxDialog]. It either names its
 * result or, when it cannot run, says why; there is no bare disabled label.
 */
sealed interface SheetPrimary {
    data class Action(val label: String, val onClick: () -> Unit) : SheetPrimary
    data class Disabled(val reason: String) : SheetPrimary
}

/** The left-hand footer slot: Cancel on sheets that edit, the secondary action on view-only ones. */
sealed interface SheetSecondary {
    data object Cancel : SheetSecondary
    data class Action(val label: String, val onClick: () -> Unit, val enabled: Boolean = true) : SheetSecondary
}

/**
 * One frame for every Day sheet: lane kicker and light title, a scrolling body and a pinned
 * footer, opened fully expanded on an opaque surface. Swipe, Back, scrim and Cancel all
 * discard ([onCancel] lets an in-sheet edit step back instead); while [busy] the sheet cannot be dismissed. View-only sheets (a [SheetSecondary.Action]
 * in the Cancel slot) carry a "Close" accessibility action instead of a visible Close button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeboxSheet(
    lane: Lane,
    kicker: String,
    title: String,
    onDismiss: () -> Unit,
    primary: SheetPrimary,
    modifier: Modifier = Modifier,
    secondary: SheetSecondary = SheetSecondary.Cancel,
    subtitle: String? = null,
    meta: String? = null,
    live: Boolean = false,
    busy: Boolean = false,
    onCancel: () -> Unit = onDismiss,
    dismissAllowed: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TimeboxTheme.colors
    val dismiss = { if (!busy) onDismiss() }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden || (!busy && dismissAllowed) }),
        containerColor = colors.sheet.copy(alpha = 1f),
        contentColor = colors.on,
        scrimColor = colors.scrim,
        shape = TimeboxShapes.sheet,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(34.dp, 4.dp)
                .clip(CircleShape).background(colors.outlineVariant.copy(alpha = .6f)))
        },
    ) {
        Column(
            modifier
                .fillMaxWidth()
                // Outside the scroll, so the keyboard shrinks the viewport instead of
                // covering the fields. `union` keeps the nav bar from being paid for twice.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp)
                .semantics {
                    customActions = listOf(CustomAccessibilityAction("Close") { dismiss(); true })
                },
        ) {
            SheetHeader(lane, kicker, title, subtitle, meta, live)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = SheetGutter).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
            SheetFooter(lane, primary, secondary, onCancel = { if (!busy) onCancel() }, busy = busy)
        }
    }
}

/**
 * A confirmation on the same frame as the sheets: kicker, light title, body and the footer's
 * button order. Red appears only when [destructive].
 */
@Composable
fun TimeboxDialog(
    lane: Lane,
    kicker: String,
    title: String,
    body: String,
    onDismiss: () -> Unit,
    primary: SheetPrimary,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    busy: Boolean = false,
) {
    val colors = TimeboxTheme.colors
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            color = colors.sheet.copy(alpha = 1f),
            contentColor = colors.on,
            shape = TimeboxShapes.group,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)) {
                SheetKicker(kicker, lane)
                Text(title, Modifier.padding(top = 6.dp).semantics { heading() }, style = SheetTitleStyle.copy(fontSize = 24.sp, lineHeight = 28.sp), color = colors.on)
                Text(body, Modifier.padding(top = 10.dp), style = TimeboxTheme.type.body, color = colors.onVariant)
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GhostButton(dismissLabel, enabled = !busy, onClick = onDismiss)
                    SheetPrimaryButton(primary, lane, Modifier.weight(1f), destructive = destructive, enabled = !busy)
                }
            }
        }
    }
}

/** Lane-colored, uppercase, with a dot; [live] rings the dot for something still running. */
@Composable
fun SheetKicker(text: String, lane: Lane, modifier: Modifier = Modifier, live: Boolean = false) {
    val color = laneColor(lane)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier.size(if (live) 11.dp else 7.dp).clip(CircleShape)
                .background(if (live) color.copy(alpha = .25f) else color),
            contentAlignment = Alignment.Center,
        ) { if (live) Box(Modifier.size(7.dp).clip(CircleShape).background(color)) }
        Text(text.uppercase(), style = KickerStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A sentence-case label above its field, with a muted "optional" where it applies. */
@Composable
fun SheetField(label: String, modifier: Modifier = Modifier, optional: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SheetLabel(label, optional = optional)
        content()
    }
}

@Composable
fun SheetLabel(label: String, modifier: Modifier = Modifier, optional: Boolean = false) {
    val colors = TimeboxTheme.colors
    Text(
        buildAnnotatedString {
            append(label)
            if (optional) withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = colors.outline)) { append("  optional") }
        },
        modifier,
        style = LabelStyle,
        color = colors.on,
    )
}

/** The one outlined text field style inside sheets; its label sits above it, not inside. */
@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    fieldModifier: Modifier = Modifier,
) {
    val colors = TimeboxTheme.colors
    SheetField(label, modifier, optional) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            placeholder = placeholder?.let { { Text(it, style = TimeboxTheme.type.body, color = colors.outline) } },
            textStyle = TimeboxTheme.type.body.copy(color = colors.on),
            shape = TimeboxShapes.field,
            colors = sheetFieldColors(),
            modifier = fieldModifier.fillMaxWidth(),
        )
    }
}

@Composable
fun sheetFieldColors() = TimeboxTheme.colors.let { colors ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedBorderColor = colors.outline,
        unfocusedBorderColor = colors.outlineVariant,
        disabledBorderColor = colors.outlineVariant.copy(alpha = .6f),
        cursorColor = colors.on,
    )
}

/** A tappable value in the shared field outline, such as a start or end time. */
@Composable
fun SheetValueField(
    caption: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier
            .clip(TimeboxShapes.field)
            .border(1.dp, colors.outlineVariant, TimeboxShapes.field)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Change $caption", onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .heightIn(min = 56.dp)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(caption, style = TimeboxTheme.type.bodySmall, color = colors.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = TimeValueStyle, color = if (enabled) colors.on else colors.onVariant, maxLines = 1)
    }
}

@Composable
fun SheetHelp(text: String, modifier: Modifier = Modifier, color: Color = TimeboxTheme.colors.outline) {
    Text(text, modifier, style = TimeboxTheme.type.bodySmall, color = color)
}

/**
 * Time in the other lane, linked to what this sheet shows. [lane] is the lane of that time;
 * [warn] turns the card amber when the action overwrites recorded time.
 */
@Composable
fun RelatedCard(
    lane: Lane,
    caption: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    text: String? = null,
    warn: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = TimeboxTheme.colors
    val accent = if (warn) colors.warn else laneColor(lane)
    val tint = when {
        warn -> colors.warnSurface
        lane == Lane.Planned -> colors.plannedTint
        else -> colors.actualTint
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(TimeboxShapes.card)
            .background(tint)
            .drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 17.dp, end = 14.dp, top = 11.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                caption, Modifier.weight(1f), style = TimeboxTheme.type.bodySmall,
                color = if (warn) colors.warn else colors.onVariant,
                fontWeight = if (warn) FontWeight.SemiBold else null,
            )
            trailing?.let { Text(it, style = TimeboxTheme.type.bodySmall.merge(Tabular), color = colors.onVariant) }
        }
        Text(title, style = CardTitleStyle, color = colors.on)
        text?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
        if (actions != null) Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** A link inside a [RelatedCard], colored by the lane it opens. */
@Composable
fun CardLink(label: String, lane: Lane, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = TimeboxTheme.colors
    Text(
        label,
        Modifier.clip(TimeboxShapes.chip).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp).padding(vertical = 12.dp),
        style = TimeboxTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
        color = if (enabled) laneColor(lane) else colors.disabledContent,
    )
}

/** The secondary, outlined pill used inside cards and in the footer of view-only sheets. */
@Composable
fun SheetOutlinedButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false) {
    val colors = TimeboxTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = TimeboxShapes.chip,
        color = Color.Transparent,
        contentColor = if (enabled) colors.on else colors.disabledContent,
        border = BorderStroke(1.dp, if (enabled) colors.outline else colors.outlineVariant),
        modifier = modifier.heightIn(min = if (compact) 40.dp else 48.dp),
    ) {
        Box(Modifier.padding(horizontal = if (compact) 14.dp else 18.dp), contentAlignment = Alignment.Center) {
            Text(label, style = ButtonStyle.copy(fontSize = if (compact) 13.sp else 14.5.sp), maxLines = 1)
        }
    }
}

/** A full-width row that opens something else, e.g. Edit details or Notes. */
@Composable
fun SheetRowLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null, enabled: Boolean = true) {
    val colors = TimeboxTheme.colors
    Row(
        modifier.fillMaxWidth().clip(TimeboxShapes.cell).background(colors.low)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TimeboxTheme.type.body, color = if (enabled) colors.on else colors.disabledContent)
            detail?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text("›", style = TimeboxTheme.type.sectionTitle.copy(fontSize = 20.sp), color = colors.outline)
    }
}

/** The linked Battle Plan task: its title, a caption and a link that opens it. */
@Composable
fun LinkedTaskRow(title: String, caption: String, onOpen: () -> Unit, modifier: Modifier = Modifier, lane: Lane = Lane.Planned, enabled: Boolean = true) {
    val colors = TimeboxTheme.colors
    Row(
        modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.low).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, style = TimeboxTheme.type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.on)
            Text(caption, style = TimeboxTheme.type.bodySmall, color = colors.outline)
        }
        CardLink("Open task", lane, onOpen, enabled = enabled)
    }
}

/** The block sheets' one Delete control, at the bottom of the body; it always confirms. */
@Composable
fun SheetDeleteRow(onClick: () -> Unit, enabled: Boolean = true) {
    val colors = TimeboxTheme.colors
    Text(
        "Delete block",
        Modifier.clip(TimeboxShapes.chip).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp).padding(vertical = 12.dp),
        style = TimeboxTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
        color = if (enabled) colors.error else colors.disabledContent,
    )
}

/** One row of mutually exclusive choices, such as the length picker. */
@Composable
fun SheetSegmented(options: List<String>, selectedIndex: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = TimeboxTheme.colors
    Row(
        modifier.fillMaxWidth().clip(TimeboxShapes.field).background(colors.low).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val on = index == selectedIndex
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) colors.sheet.copy(alpha = 1f) else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, colors.hairline, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(index) }
                    .semantics { selected = on }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option, textAlign = TextAlign.Center,
                    style = TimeboxTheme.type.bodySmall.merge(Tabular).copy(lineHeight = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (!enabled) colors.disabledContent else if (on) colors.on else colors.onVariant,
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(lane: Lane, kicker: String, title: String, subtitle: String?, meta: String?, live: Boolean) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SheetGutter).padding(top = 4.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            SheetKicker(kicker, lane, live = live)
            Text(title, Modifier.padding(top = 4.dp).semantics { heading() }, style = SheetTitleStyle, color = colors.on)
            subtitle?.let { Text(it, Modifier.padding(top = 4.dp), style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
        }
        meta?.let { Text(it, Modifier.padding(bottom = 5.dp), style = TimeboxTheme.type.label.merge(Tabular), color = colors.onVariant, maxLines = 1) }
    }
}

@Composable
private fun SheetFooter(lane: Lane, primary: SheetPrimary, secondary: SheetSecondary, onCancel: () -> Unit, busy: Boolean) {
    val colors = TimeboxTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SheetGutter).padding(top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (secondary) {
                SheetSecondary.Cancel -> GhostButton("Cancel", enabled = !busy, onClick = onCancel)
                is SheetSecondary.Action -> SheetOutlinedButton(secondary.label, secondary.onClick, enabled = secondary.enabled && !busy)
            }
            SheetPrimaryButton(primary, lane, Modifier.weight(1f), enabled = !busy)
        }
    }
}

@Composable
private fun GhostButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    Box(
        Modifier.clip(TimeboxShapes.chip).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).widthIn(min = 48.dp).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = ButtonStyle, color = if (enabled) colors.onVariant else colors.disabledContent) }
}

@Composable
private fun SheetPrimaryButton(primary: SheetPrimary, lane: Lane, modifier: Modifier, destructive: Boolean = false, enabled: Boolean = true) {
    val colors = TimeboxTheme.colors
    val action = primary as? SheetPrimary.Action
    val active = action != null && enabled
    val container = when {
        !active -> colors.disabledContainer
        destructive -> colors.error
        lane == Lane.Planned -> colors.planned
        else -> colors.actual
    }
    val content = when {
        !active -> colors.disabledContent
        destructive -> colors.onError
        lane == Lane.Planned -> colors.onPlanned
        else -> colors.onActual
    }
    val label = when (primary) {
        is SheetPrimary.Action -> primary.label
        is SheetPrimary.Disabled -> primary.reason
    }
    Box(
        modifier.heightIn(min = 48.dp).clip(TimeboxShapes.chip).background(container)
            .clickable(enabled = active, role = Role.Button) { action?.onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ButtonStyle.copy(fontWeight = FontWeight.SemiBold), color = content, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun laneColor(lane: Lane) = if (lane == Lane.Planned) TimeboxTheme.colors.planned else TimeboxTheme.colors.actual

private val SheetGutter = 22.dp
private val Tabular = TextStyle(fontFeatureSettings = "tnum")
private val KickerStyle = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.14.em)
private val SheetTitleStyle = TextStyle(
    fontFamily = HeadlineFamily, fontWeight = FontWeight.Light, fontSize = 29.sp, lineHeight = 33.sp,
    letterSpacing = (-0.03).em, fontFeatureSettings = "tnum",
)
private val CardTitleStyle = TextStyle(fontFamily = HeadlineFamily, fontWeight = FontWeight.Light, fontSize = 22.sp, lineHeight = 27.sp, fontFeatureSettings = "tnum")
private val TimeValueStyle = TextStyle(fontFamily = HeadlineFamily, fontWeight = FontWeight.Light, fontSize = 23.sp, lineHeight = 27.sp, fontFeatureSettings = "tnum")
private val LabelStyle = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
private val ButtonStyle = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.5.sp)
