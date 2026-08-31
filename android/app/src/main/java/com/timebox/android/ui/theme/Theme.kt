package com.timebox.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
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

    CompositionLocalProvider(
        LocalTimeboxColors provides colors,
        LocalTimeboxType provides TimeboxType,
    ) {
        MaterialTheme(colorScheme = timeboxColorScheme(colors)) {
            CompositionLocalProvider(LocalContentColor provides colors.on, content = content)
        }
    }
}

/** Maps Timebox tokens to the Material roles consumed by stock Compose controls. */
internal fun timeboxColorScheme(colors: TimeboxColors): ColorScheme =
    if (colors.isDark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondary = colors.secondary,
            onSecondary = colors.onSecondary,
            secondaryContainer = colors.secondaryContainer,
            onSecondaryContainer = colors.onSecondaryContainer,
            tertiary = colors.tertiary,
            onTertiary = colors.onTertiary,
            tertiaryContainer = colors.tertiaryContainer,
            onTertiaryContainer = colors.onTertiaryContainer,
            background = colors.bg,
            onBackground = colors.on,
            surface = colors.bg,
            onSurface = colors.on,
            surfaceVariant = colors.high,
            onSurfaceVariant = colors.onVariant,
            surfaceDim = colors.surfaceDim,
            surfaceBright = colors.surfaceBright,
            surfaceContainerLowest = colors.lowest,
            surfaceContainerLow = colors.low,
            surfaceContainer = colors.surf,
            surfaceContainerHigh = colors.high,
            surfaceContainerHighest = colors.highest,
            outline = colors.outline,
            outlineVariant = colors.outlineVariant,
            error = colors.error,
            onError = colors.onError,
            errorContainer = colors.errorContainer,
            onErrorContainer = colors.onErrorContainer,
            inverseSurface = colors.inverseSurface,
            inverseOnSurface = colors.inverseOnSurface,
            inversePrimary = colors.inversePrimary,
            surfaceTint = colors.primary,
            scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondary = colors.secondary,
            onSecondary = colors.onSecondary,
            secondaryContainer = colors.secondaryContainer,
            onSecondaryContainer = colors.onSecondaryContainer,
            tertiary = colors.tertiary,
            onTertiary = colors.onTertiary,
            tertiaryContainer = colors.tertiaryContainer,
            onTertiaryContainer = colors.onTertiaryContainer,
            background = colors.bg,
            onBackground = colors.on,
            surface = colors.bg,
            onSurface = colors.on,
            surfaceVariant = colors.high,
            onSurfaceVariant = colors.onVariant,
            surfaceDim = colors.surfaceDim,
            surfaceBright = colors.surfaceBright,
            surfaceContainerLowest = colors.lowest,
            surfaceContainerLow = colors.low,
            surfaceContainer = colors.surf,
            surfaceContainerHigh = colors.high,
            surfaceContainerHighest = colors.highest,
            outline = colors.outline,
            outlineVariant = colors.outlineVariant,
            error = colors.error,
            onError = colors.onError,
            errorContainer = colors.errorContainer,
            onErrorContainer = colors.onErrorContainer,
            inverseSurface = colors.inverseSurface,
            inverseOnSurface = colors.inverseOnSurface,
            inversePrimary = colors.inversePrimary,
            surfaceTint = colors.primary,
            scrim = colors.scrim,
        )
    }
