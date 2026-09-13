package com.timebox.android.ui.day

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkModeTimeTest {
    @Test
    fun `duration rolls into hours and keeps seconds`() {
        assertEquals("0 secs", formatDurationSeconds(0))
        assertEquals("9 mins 7 secs", formatDurationSeconds(9 * 60 + 7))
        assertEquals("1 hour", formatDurationSeconds(60 * 60))
    }
}
