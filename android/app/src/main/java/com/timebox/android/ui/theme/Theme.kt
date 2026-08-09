package com.timebox.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Corner radii used across the design. */
object TimeboxShapes {
    val block = RoundedCornerShape(6.dp)
    val chip = RoundedCornerShape(percent = 50)
    val field = RoundedCornerShape(12.dp)
    val cell = RoundedCornerShape(10.dp)
    val card = RoundedCornerShape(14.dp)
    val group = RoundedCornerShape(18.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/** Fixed measurements the timeline and chrome depend on. */
object TimeboxDimens {
    /** Height of one 30-minute slot. `--slotHeight` in the design, default 34. */
    val slotHeight = 34.dp
    /**
     * Wider than the design's 38: the labels fall back to the platform sans face,
     * which sets `10 AM` a few dp broader than Inter does. Leaves room up to a
     * system font scale of roughly 1.3 before `12 PM` starts to clip.
     */
    val gutterWidth = 44.dp
    /** Clearance between an hour label and the rule down the gutter's right edge. */
    val gutterLabelGap = 6.dp
    val laneGap = 6.dp
    val screenPadding = 12.dp
    /** Space under the scroll content so the nav bar never covers the last block. */
    val bottomInset = 24.dp
    val grooveHeight = 8.dp
    val touchTarget = 44.dp
}

val LocalTimeboxColors = staticCompositionLocalOf { LightTimeboxColors }
val LocalTimeboxType = staticCompositionLocalOf { TimeboxType }

object TimeboxTheme {
    val colors: TimeboxColors
        @Composable @ReadOnlyComposable get() = LocalTimeboxColors.current

    val type: TimeboxTypography
        @Composable @ReadOnlyComposable get() = LocalTimeboxType.current
}

@Composable
fun TimeboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkTimeboxColors else LightTimeboxColors

    // Material3 components (sheets, text fields, ripples) read the standard scheme,
    // so map the tokens onto it rather than letting them fall back to purple.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.on,
            onPrimary = colors.bg,
            background = colors.bg,
            onBackground = colors.on,
            surface = colors.low,
            onSurface = colors.on,
            surfaceVariant = colors.surf,
            onSurfaceVariant = colors.onVariant,
            outline = colors.outline,
            outlineVariant = colors.outlineVariant,
            error = colors.error,
            scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.on,
            onPrimary = colors.bg,
            background = colors.bg,
            onBackground = colors.on,
            surface = colors.low,
            onSurface = colors.on,
            surfaceVariant = colors.surf,
            onSurfaceVariant = colors.onVariant,
            outline = colors.outline,
            outlineVariant = colors.outlineVariant,
            error = colors.error,
            scrim = colors.scrim,
        )
    }

    CompositionLocalProvider(
        LocalTimeboxColors provides colors,
        LocalTimeboxType provides TimeboxType,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
