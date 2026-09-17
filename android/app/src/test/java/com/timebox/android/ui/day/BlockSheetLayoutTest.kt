package com.timebox.android.ui.day

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockSheetLayoutTest {
    @Test
    fun keepsBottomNavClearanceWhileKeyboardIsClosed() {
        assertEquals(96.dp, blockSheetNavClearance(imeBottom = 0.dp))
    }

    @Test
    fun clearanceShrinksAsKeyboardSlidesIn() {
        assertEquals(56.dp, blockSheetNavClearance(imeBottom = 40.dp))
    }

    @Test
    fun openKeyboardLeavesNoGapBetweenSheetAndKeyboard() {
        assertEquals(0.dp, blockSheetNavClearance(imeBottom = 320.dp))
    }
}
