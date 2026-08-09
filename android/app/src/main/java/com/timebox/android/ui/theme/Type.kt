package com.timebox.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The design uses three families:
 *
 *  - Manrope for headline/display text (weights 200-600, tight tracking)
 *  - Inter for body, labels and buttons
 *  - a monospace face for times, durations and paths
 *
 * Manrope and Inter are not bundled: adding them means dropping binary font files
 * into `res/font`. Until then both fall back to the platform sans face, which keeps
 * every weight and size from the design intact. To switch on the real faces, drop
 * `manrope_*.ttf` / `inter_*.ttf` into `app/src/main/res/font` and replace the two
 * families below with `FontFamily(Font(R.font.manrope_extralight, FontWeight.ExtraLight), ...)`.
 */
val HeadlineFamily: FontFamily = FontFamily.SansSerif
val BodyFamily: FontFamily = FontFamily.SansSerif
val MonoFamily: FontFamily = FontFamily.Monospace

@Immutable
data class TimeboxTypography(
    /** Uppercase tracked kicker above the screen title. */
    val kicker: TextStyle,
    /** Large light screen title. */
    val screenTitle: TextStyle,
    /** Section heading inside a card. */
    val sectionTitle: TextStyle,
    /** Very large tabular figure, e.g. the sheet's time range. */
    val display: TextStyle,
    /** Lane headers and other uppercase micro labels. */
    val laneLabel: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val blockTitle: TextStyle,
    val blockTitleSelected: TextStyle,
    val mono: TextStyle,
    val monoSmall: TextStyle,
    val gutter: TextStyle,
    val navLabel: TextStyle,
)

val TimeboxType = TimeboxTypography(
    kicker = TextStyle(
        fontFamily = HeadlineFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.2.em,
    ),
    screenTitle = TextStyle(
        fontFamily = HeadlineFamily,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 27.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.045).em,
    ),
    sectionTitle = TextStyle(
        fontFamily = HeadlineFamily,
        fontWeight = FontWeight.Light,
        fontSize = 15.sp,
        letterSpacing = (-0.01).em,
    ),
    display = TextStyle(
        fontFamily = HeadlineFamily,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 34.sp,
        letterSpacing = (-0.035).em,
    ),
    laneLabel = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 0.09.em,
    ),
    body = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    label = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
    ),
    button = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    blockTitle = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 15.sp,
    ),
    blockTitleSelected = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 15.6.sp,
    ),
    mono = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
    ),
    monoSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
    ),
    gutter = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
    ),
    navLabel = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
    ),
)
