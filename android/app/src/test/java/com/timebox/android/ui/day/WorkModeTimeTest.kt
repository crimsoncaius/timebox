package com.timebox.android.ui.day

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkModeTimeTest {
    @Test
    fun `duration includes zero-padded seconds`() {
        assertEquals("00:00", formatDurationSeconds(0))
        assertEquals("09:07", formatDurationSeconds(9 * 60 + 7))
        assertEquals("60:00", formatDurationSeconds(60 * 60))
    }
}
