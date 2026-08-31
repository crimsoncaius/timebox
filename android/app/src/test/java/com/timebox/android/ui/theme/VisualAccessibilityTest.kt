package com.timebox.android.ui.theme

import androidx.compose.ui.graphics.Color
import com.timebox.android.ui.chronicle.chronicleDateTextColor
import com.timebox.android.ui.day.monthDateTextColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class VisualAccessibilityTest {
    @Test
    fun darkErrorTreatmentsMeetNormalTextContrast() {
        assertContrastAtLeast(DarkTimeboxColors.error, DarkTimeboxColors.bg, 4.5)
        assertContrastAtLeast(DarkTimeboxColors.error, DarkTimeboxColors.low, 4.5)
        assertContrastAtLeast(DarkTimeboxColors.onError, DarkTimeboxColors.error, 4.5)
        assertContrastAtLeast(DarkTimeboxColors.onErrorContainer, DarkTimeboxColors.errorContainer, 4.5)
    }

    @Test
    fun interactiveAdjacentMonthDatesMeetNormalTextContrast() {
        listOf(LightTimeboxColors, DarkTimeboxColors).forEach { colors ->
            assertContrastAtLeast(
                monthDateTextColor(colors, selected = false, today = false, inDisplayedMonth = false),
                colors.bg,
                4.5,
            )
            assertContrastAtLeast(
                chronicleDateTextColor(colors, isToday = false, inMonth = false),
                colors.bg,
                4.5,
            )
        }
    }

    @Test
    fun materialSurfaceAndContainerRolesAreFullyMapped() {
        listOf(LightTimeboxColors, DarkTimeboxColors).forEach { colors ->
            val scheme = timeboxColorScheme(colors)
            assertEquals(colors.primary, scheme.primary)
            assertEquals(colors.onPrimary, scheme.onPrimary)
            assertEquals(colors.primaryContainer, scheme.primaryContainer)
            assertEquals(colors.onPrimaryContainer, scheme.onPrimaryContainer)
            assertEquals(colors.secondary, scheme.secondary)
            assertEquals(colors.onSecondary, scheme.onSecondary)
            assertEquals(colors.secondaryContainer, scheme.secondaryContainer)
            assertEquals(colors.onSecondaryContainer, scheme.onSecondaryContainer)
            assertEquals(colors.tertiary, scheme.tertiary)
            assertEquals(colors.onTertiary, scheme.onTertiary)
            assertEquals(colors.tertiaryContainer, scheme.tertiaryContainer)
            assertEquals(colors.onTertiaryContainer, scheme.onTertiaryContainer)
            assertEquals(colors.bg, scheme.background)
            assertEquals(colors.on, scheme.onBackground)
            assertEquals(colors.bg, scheme.surface)
            assertEquals(colors.on, scheme.onSurface)
            assertEquals(colors.high, scheme.surfaceVariant)
            assertEquals(colors.onVariant, scheme.onSurfaceVariant)
            assertEquals(colors.surfaceDim, scheme.surfaceDim)
            assertEquals(colors.surfaceBright, scheme.surfaceBright)
            assertEquals(colors.lowest, scheme.surfaceContainerLowest)
            assertEquals(colors.low, scheme.surfaceContainerLow)
            assertEquals(colors.surf, scheme.surfaceContainer)
            assertEquals(colors.high, scheme.surfaceContainerHigh)
            assertEquals(colors.highest, scheme.surfaceContainerHighest)
            assertEquals(colors.outline, scheme.outline)
            assertEquals(colors.outlineVariant, scheme.outlineVariant)
            assertEquals(colors.error, scheme.error)
            assertEquals(colors.onError, scheme.onError)
            assertEquals(colors.errorContainer, scheme.errorContainer)
            assertEquals(colors.onErrorContainer, scheme.onErrorContainer)
            assertEquals(colors.inverseSurface, scheme.inverseSurface)
            assertEquals(colors.inverseOnSurface, scheme.inverseOnSurface)
            assertEquals(colors.inversePrimary, scheme.inversePrimary)
            assertEquals(colors.primary, scheme.surfaceTint)
            assertEquals(colors.scrim, scheme.scrim)
        }
    }

    @Test
    fun productActionsSelectionsAndDisabledStatesUseDedicatedTokens() {
        listOf(LightTimeboxColors, DarkTimeboxColors).forEach { colors ->
            assertNotEquals(colors.on, colors.action)
            assertNotEquals(colors.planned, colors.selected)
            assertNotEquals(colors.bg, colors.disabledContainer)
            assertContrastAtLeast(colors.onAction, colors.action, 4.5)
            assertContrastAtLeast(colors.onSelected, colors.selected, 4.5)
            assertContrastAtLeast(colors.disabledContent, colors.disabledContainer, 4.5)
        }
    }

    @Test
    fun semanticSurfaceLadderSeparatesPageCardAndRaisedContent() {
        listOf(LightTimeboxColors, DarkTimeboxColors).forEach { colors ->
            assertNotEquals(colors.bg, colors.card)
            assertNotEquals(colors.card, colors.raised)
            assertNotEquals(colors.bg, colors.field)
        }
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Double) {
        val effective = foreground.compositeOver(background)
        val lighter = max(effective.relativeLuminance(), background.relativeLuminance())
        val darker = min(effective.relativeLuminance(), background.relativeLuminance())
        val ratio = (lighter + 0.05) / (darker + 0.05)
        assertTrue("Expected contrast >= $minimum, got $ratio", ratio >= minimum)
    }
}

private fun Color.relativeLuminance(): Double {
    fun linear(value: Float): Double =
        if (value <= 0.04045f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
}

private fun Color.compositeOver(background: Color): Color {
    val outAlpha = alpha + background.alpha * (1f - alpha)
    if (outAlpha == 0f) return Color.Transparent
    return Color(
        red = (red * alpha + background.red * background.alpha * (1f - alpha)) / outAlpha,
        green = (green * alpha + background.green * background.alpha * (1f - alpha)) / outAlpha,
        blue = (blue * alpha + background.blue * background.alpha * (1f - alpha)) / outAlpha,
        alpha = outAlpha,
    )
}
