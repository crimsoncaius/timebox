package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SubdirectoryArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.TaskType
import com.timebox.android.data.canonicalizeTaskTypePath
import com.timebox.android.data.createAncestorHint
import com.timebox.android.data.rankTaskTypes
import com.timebox.android.data.shouldOfferCreate
import com.timebox.android.data.taskTypePathParts
import com.timebox.android.ui.theme.MonoFamily
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay

/**
 * The task type combobox from the block sheet: a search field over the existing types
 * with a trailing row that creates whatever path the query names.
 *
 * Choosing a row *is* the commit — there is no separate save step — so both [onChoose]
 * and [onCreate] assign the type to the block straight away.
 *
 * @param selectedTypeId the block's current type, drawn distinctly and floated to the top.
 * @param autoFocus raises the keyboard on first composition; only wanted for a new block.
 */
@Composable
fun TaskTypePicker(
    taskTypes: List<TaskType>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedTypeId: Int?,
    onChoose: (TaskType) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val colors = TimeboxTheme.colors
    val haptics = LocalHapticFeedback.current

    val canonical = remember(query) { canonicalizeTaskTypePath(query) }
    val results = remember(taskTypes, query, selectedTypeId) {
        rankTaskTypes(taskTypes, query, selectedTypeId)
    }
    val showCreate = remember(taskTypes, query) { shouldOfferCreate(taskTypes, query) }
    val hint = remember(taskTypes, canonical) {
        canonical?.let { createAncestorHint(taskTypes, it) }
    }

    // A light tick on commit, the same weight as a text-handle nudge — the app's first
    // haptic, so it sets the selection feel for everything that follows.
    fun tick() = haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    val choose: (TaskType) -> Unit = { type ->
        tick()
        onChoose(type)
    }
    val create: () -> Unit = {
        canonical?.let {
            tick()
            onCreate(it)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            // The example path doubles as the syntax lesson when there is nothing to search.
            placeholder = if (taskTypes.isEmpty()) {
                "Name this type, e.g. coding/ai"
            } else {
                "Search or create a type"
            },
            autoFocus = autoFocus,
            onSubmit = {
                when {
                    showCreate -> create()
                    // An exact match ranks first, so enter on a fully typed path commits it.
                    results.isNotEmpty() -> choose(results.first())
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        if (taskTypes.isEmpty() && canonical == null) {
            EmptyPanel()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ListMaxHeight)
                    .clip(TimeboxShapes.field)
                    .background(colors.bg)
                    .border(1.dp, colors.hairline, TimeboxShapes.field)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (results.isEmpty()) {
                    Text(
                        text = "No type matches that path.",
                        style = TimeboxTheme.type.bodySmall.copy(fontSize = 12.5.sp),
                        color = colors.onVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
                results.forEachIndexed { index, type ->
                    if (index > 0) RowDivider()
                    ResultRow(
                        type = type,
                        selected = type.id == selectedTypeId,
                        onClick = { choose(type) },
                    )
                }
                if (showCreate && canonical != null) {
                    if (results.isNotEmpty()) RowDivider()
                    CreateRow(path = canonical, onClick = create)
                }
            }
        }

        if (showCreate && hint != null) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = buildAnnotatedString {
                    append(hint.lead)
                    withStyle(SpanStyle(fontFamily = MonoFamily, fontSize = 11.sp)) {
                        append(hint.path)
                    }
                    append(hint.tail)
                },
                style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.5.sp),
                color = colors.onVariant,
            )
        }
    }
}

/** Four rows of results before the list starts scrolling inside itself. */
private val ListMaxHeight = 176.dp

/** Minimum comfortable tap height; rows pad out to it rather than shrinking their text. */
private val RowMinHeight = 44.dp

/** Long enough for the bottom sheet's enter animation to hand over window focus. */
private const val SheetEnterDelayMs = 250L

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    autoFocus: Boolean,
    onSubmit: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        // The sheet gets its own window and only becomes focusable once it has finished
        // sliding up; a request made before that is dropped and the keyboard never opens.
        delay(SheetEnterDelayMs)
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TimeboxTheme.type.body.copy(color = colors.on),
        cursorBrush = SolidColor(colors.planned),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            // Uri keeps `/` on the main keyboard plane, which is the whole point of paths.
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(TimeboxShapes.field)
                    .background(colors.bg)
                    .border(
                        width = 1.dp,
                        color = if (focused) colors.outline else colors.hairline,
                        shape = TimeboxShapes.field,
                    )
                    // The clear button carries its own 44dp target, which already sets the
                    // icon 13dp off the edge, so only the idle field needs the right inset.
                    .padding(start = 12.dp, end = if (query.isEmpty()) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.onVariant,
                    modifier = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TimeboxTheme.type.body,
                            color = colors.onVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cancel,
                            contentDescription = "Clear search",
                            tint = colors.onVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ResultRow(type: TaskType, selected: Boolean, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        selected && pressed -> colors.high
        selected || pressed -> colors.surf
        else -> Color.Transparent
    }
    val parts = taskTypePathParts(type.name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = twoTonePath(parts.ancestors, parts.leaf, colors.onVariant, colors.on),
            style = TimeboxTheme.type.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Current type",
                tint = colors.tertiary,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = type.usageCount.toString(),
                style = TimeboxTheme.type.mono,
                color = colors.onVariant,
            )
        }
    }
}

@Composable
private fun CreateRow(path: String, onClick: () -> Unit) {
    val colors = TimeboxTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Tight slashes here, unlike the spaced ones in a result row: this is the literal
    // string that gets saved, so it has to read as one path rather than a breadcrumb.
    val parts = taskTypePathParts(path, separator = "/")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) colors.surf else colors.low)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = colors.tertiary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.onVariant)) { append("Create ") }
                withStyle(
                    SpanStyle(
                        fontFamily = MonoFamily,
                        fontSize = 12.5.sp,
                        color = colors.onVariant,
                    ),
                ) {
                    append(parts.ancestors)
                }
                withStyle(
                    SpanStyle(
                        fontFamily = MonoFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.on,
                    ),
                ) {
                    append(parts.leaf)
                }
            },
            style = TimeboxTheme.type.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.SubdirectoryArrowLeft,
            contentDescription = null,
            tint = colors.onVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Shown instead of the list until the very first type exists. */
@Composable
private fun EmptyPanel() {
    val colors = TimeboxTheme.colors
    val code = SpanStyle(fontFamily = MonoFamily, fontSize = 12.sp, color = colors.on)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TimeboxShapes.field)
            .background(colors.bg)
            .dashedBorder(colors.hairline, cornerRadius = 12.dp)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append("No types yet. Type a name to make your first one. Use ")
                withStyle(code) { append("/") }
                append(" to nest — ")
                withStyle(code) { append("coding/ai") }
                append(" creates ")
                withStyle(code) { append("coding") }
                append(" too.")
            },
            style = TimeboxTheme.type.body.copy(fontSize = 13.sp, lineHeight = 20.8.sp),
            color = colors.onVariant,
        )
    }
}

@Composable
private fun RowDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TimeboxTheme.colors.hairline),
    )
}

private fun twoTonePath(
    ancestors: String,
    leaf: String,
    ancestorColor: Color,
    leafColor: Color,
): AnnotatedString = buildAnnotatedString {
    if (ancestors.isNotEmpty()) {
        withStyle(SpanStyle(color = ancestorColor)) { append(ancestors) }
    }
    withStyle(SpanStyle(color = leafColor, fontWeight = FontWeight.Medium)) { append(leaf) }
}

/** `border: 1px dashed` — Compose has no dashed variant of [border]. */
private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.dp,
    dash: Dp = 4.dp,
): Modifier = drawBehind {
    val stroke = strokeWidth.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
        ),
    )
}
