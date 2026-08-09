package com.timebox.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The design's CSS custom properties, one field per `--token`.
 *
 * Names follow the design document so the two stay diffable: `bg`, `low`, `onv`,
 * `psurf` and friends are the same tokens declared on `.tb` / `.tb.dark`.
 */
@Immutable
data class TimeboxColors(
    val bg: Color,
    val lowest: Color,
    val low: Color,
    val surf: Color,
    val high: Color,
    val on: Color,
    val onVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val hairline: Color,
    val planned: Color,
    val plannedSurface: Color,
    val plannedBorder: Color,
    val actual: Color,
    val actualSurface: Color,
    val actualBorder: Color,
    val gridStrong: Color,
    val gridSoft: Color,
    val timelineLabel: Color,
    val now: Color,
    val error: Color,
    val tertiary: Color,
    val paper: Color,
    val paperRaised: Color,
    val rule: Color,
    val groove: Color,
    val sheet: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LightTimeboxColors = TimeboxColors(
    bg = Color(0xFFF9F9F9),
    lowest = Color(0xFFFFFFFF),
    low = Color(0xFFF2F4F4),
    surf = Color(0xFFEBEEEF),
    high = Color(0xFFE4E9EA),
    on = Color(0xFF2D3435),
    onVariant = Color(0xFF5A6061),
    outline = Color(0xFF757C7D),
    outlineVariant = Color(0xFFADB3B4),
    hairline = Color(0x59ADB3B4), // --ov15: rgba(173,179,180,.35)
    planned = Color(0xFF1967D2),
    plannedSurface = Color(0xFFF5F9FF),
    plannedBorder = Color(0xFFC5D9F7),
    actual = Color(0xFF0D6B63),
    actualSurface = Color(0xFFF2FAF9),
    actualBorder = Color(0xFFB5DED6),
    gridStrong = Color(0xFFDADCE0),
    gridSoft = Color(0xFFE8EAED),
    timelineLabel = Color(0xFF5F6368),
    now = Color(0xFF9F403D),
    error = Color(0xFF9F403D),
    tertiary = Color(0xFF5C605E),
    paper = Color(0xFFEDE8D8),
    paperRaised = Color(0xFFFEFCF5),
    rule = Color(0x73322814), // rgba(50,40,20,.45)
    groove = Color(0x0F322814), // rgba(50,40,20,.06)
    sheet = Color(0xEBFFFFFF), // rgba(255,255,255,.92)
    scrim = Color(0x522D3435), // rgba(45,52,53,.32)
    isDark = false,
)

val DarkTimeboxColors = TimeboxColors(
    bg = Color(0xFF0C0A09),
    lowest = Color(0xFF0C0A09),
    low = Color(0xFF1C1917),
    surf = Color(0xFF292524),
    high = Color(0xFF44403C),
    on = Color(0xFFF5F5F4),
    onVariant = Color(0xFFA8A29E),
    outline = Color(0xFF78716C),
    outlineVariant = Color(0xFF44403C),
    hairline = Color(0xE644403C), // --ov15: rgba(68,64,60,.9)
    planned = Color(0xFF8AB4F8),
    plannedSurface = Color(0xFF0F141C),
    plannedBorder = Color(0xFF2A3F55),
    actual = Color(0xFF7DD3C8),
    actualSurface = Color(0xFF0C1211),
    actualBorder = Color(0xFF1E3D38),
    gridStrong = Color(0xFF44403C),
    gridSoft = Color(0xFF292524),
    timelineLabel = Color(0xFFA8A29E),
    now = Color(0xFF9F403D),
    error = Color(0xFF9F403D),
    tertiary = Color(0xFF5C605E),
    paper = Color(0xFF1C1917),
    paperRaised = Color(0xFF292524),
    rule = Color(0x4DFFFAF0), // rgba(255,250,240,.3)
    groove = Color(0x4D000000), // rgba(0,0,0,.3)
    sheet = Color(0xF01C1917), // rgba(28,25,23,.94)
    scrim = Color(0x80000000), // rgba(0,0,0,.5)
    isDark = true,
)
