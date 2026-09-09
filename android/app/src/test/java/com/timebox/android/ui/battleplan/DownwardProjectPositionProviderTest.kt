package com.timebox.android.ui.battleplan

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class DownwardProjectPositionProviderTest {
    @Test fun keyboardReducesTheViewportWithoutMovingTheScopeMenu() {
        var height = 0
        val provider = DownwardProjectPositionProvider(8, startAligned = true) { height = it }
        val anchor = IntRect(12, 130, 330, 178)
        val popup = IntSize(304, 640)
        assertEquals(IntOffset(12, 178), provider.calculatePosition(anchor, IntSize(412, 900), LayoutDirection.Ltr, popup))
        assertEquals(714, height)
        assertEquals(IntOffset(12, 178), provider.calculatePosition(anchor, IntSize(412, 560), LayoutDirection.Ltr, popup))
        assertEquals(374, height)
    }

    @Test fun growingContentKeepsItsTopBelowTheMenuRow() {
        var height = 0
        val provider = DownwardProjectPositionProvider(8) { height = it }
        val anchor = IntRect(180, 250, 460, 298)
        val window = IntSize(480, 800)
        assertEquals(IntOffset(180, 298), provider.calculatePosition(anchor, window, LayoutDirection.Ltr, IntSize(280, 200)))
        assertEquals(IntOffset(180, 298), provider.calculatePosition(anchor, window, LayoutDirection.Ltr, IntSize(280, 650)))
        assertEquals(494, height)
        val nested = DownwardProjectPositionProvider(8, { IntOffset(180, 400) }) { height = it }
        assertEquals(IntOffset(180, 498), nested.calculatePosition(IntRect(0, 50, 280, 98), window, LayoutDirection.Ltr, IntSize(280, 350)))
        assertEquals(294, height)
    }

    @Test fun lowAnchorLimitsHeightInsteadOfFlippingAbove() {
        var height = 0
        val provider = DownwardProjectPositionProvider(8) { height = it }
        assertEquals(IntOffset(10, 700), provider.calculatePosition(IntRect(10, 652, 290, 700), IntSize(360, 800), LayoutDirection.Rtl, IntSize(280, 350)))
        assertEquals(92, height)
    }
}
