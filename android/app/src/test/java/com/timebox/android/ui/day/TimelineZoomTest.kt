package com.timebox.android.ui.day

import org.junit.Assert.*
import org.junit.Test

class TimelineZoomTest {
    @Test fun `zoom clamps and preserves the minute under the fingers`() {
        val zoom = TimelineZoom()
        zoom.set(100f)
        assertEquals(12f, zoom.scale)
        zoom.set(0.01f)
        assertEquals(0.5f, zoom.scale)
        zoom.set(Float.NaN)
        assertEquals(0.5f, zoom.scale)
        // A point 100px into a viewport scrolled 200px is 300px into content.
        assertEquals(500, zoomScrollOffset(200, 100f, 1f, 2f))
    }

    @Test fun `precise fields accept one minute and midnight boundaries`() {
        assertEquals(650, parseBlockMinute("10:50"))
        assertEquals(651, parseBlockMinute("10:51"))
        assertEquals(1440, parseBlockMinute("24:00"))
        assertNull(parseBlockMinute("24:01"))
        assertNull(parseBlockMinute("10:99"))
        assertNull(parseBlockMinute("10"))
    }
}
