package com.timebox.android.ui.day

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockTimeInteractionTest {
    @Test fun `initial requests floor to half hours and fit within the displayed day`() {
        assertEquals(960, initialBlockStart(980f, 480, 1200))
        assertEquals(960, initialBlockStart(989.9f, 480, 1200))
        assertEquals(990, initialBlockStart(990f, 480, 1200))
        assertEquals(480, initialBlockStart(479f, 480, 1200))
        assertEquals(1410, initialBlockStart(1439f, 0, 1440))
        assertEquals(1410, initialBlockStart(1440f, 0, 1440))
    }

    @Test fun `subsequent interactions retain five minute steps`() {
        assertEquals(965, snapToBlockInteractionStep(965f))
        assertEquals(5, snapToBlockInteractionStep(5f))
    }
}
