package com.timebox.android.ui.day

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxTheme

/** The block Day was opened on, labelled until the first touch in Day. */
internal data class BlockEmphasis(val blockId: Int, val label: String)

internal val LocalBlockEmphasis = compositionLocalOf<BlockEmphasis?> { null }

/**
 * A just-applied block is often seconds tall, so the ring keeps a minimum height and the callout sits above
 * the block's start. Apply before the block's clip: everything here draws outside the block's bounds.
 */
internal fun Modifier.blockEmphasis(blockId: Int, spaceAbove: androidx.compose.ui.unit.Dp): Modifier = composed {
    val emphasis = LocalBlockEmphasis.current?.takeIf { it.blockId == blockId }
    val strength = remember(blockId) { Animatable(0f) }
    LaunchedEffect(emphasis) { strength.animateTo(if (emphasis != null) 1f else 0f, tween(if (emphasis != null) 420 else 300)) }
    val colors = TimeboxTheme.colors
    val measurer = rememberTextMeasurer()
    val label = emphasis?.label
    (if (label != null) Modifier.semantics { stateDescription = "Opened from Assistant: $label" } else Modifier)
        .drawWithContent {
            drawContent()
            val a = strength.value
            if (a <= 0f) return@drawWithContent
            val inset = 2.dp.toPx()
            val grow = maxOf(0f, 24.dp.toPx() - size.height) / 2
            val top = -grow - inset
            val ringSize = Size(size.width + inset * 2, size.height + grow * 2 + inset * 2)
            val corner = CornerRadius(8.dp.toPx())
            drawRoundRect(colors.actual.copy(alpha = .14f * a), topLeft = Offset(-inset, top), size = ringSize, cornerRadius = corner)
            drawRoundRect(colors.actual.copy(alpha = a), topLeft = Offset(-inset, top), size = ringSize, cornerRadius = corner, style = Stroke(2.dp.toPx()))
            if (label != null) {
                val pad = 8.dp.toPx()
                val text = measurer.measure(label, TextStyle(fontSize = 12.sp, color = colors.onPrimary.copy(alpha = a)),
                    overflow = TextOverflow.Ellipsis, maxLines = 1,
                    constraints = Constraints(maxWidth = (size.width + inset * 2 - pad * 2).toInt().coerceAtLeast(1)))
                val height = text.size.height + pad
                // Near the top of the timeline there is no room above the start; label below the block instead.
                val above = spaceAbove.toPx() + top >= height + 4.dp.toPx()
                val y = if (above) top - height - 4.dp.toPx() else top + ringSize.height + 4.dp.toPx()
                drawRoundRect(colors.actual.copy(alpha = a), topLeft = Offset(-inset, y),
                    size = Size(text.size.width + pad * 2, height), cornerRadius = CornerRadius(height / 2))
                drawText(text, topLeft = Offset(-inset + pad, y + pad / 2))
            }
        }
}
