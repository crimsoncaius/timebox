package com.timebox.android.ui.day

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * Clip during drawing instead of [androidx.compose.ui.draw.clip].
 *
 * A graphics-layer clip becomes its own RenderNode. Nested under `verticalScroll`,
 * which places content with a layer translation, those nodes can stay on screen
 * while the hour gutter moves.
 */
internal fun Modifier.clipInPlace(shape: Shape): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    onDrawWithContent {
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}
