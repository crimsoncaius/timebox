package com.timebox.android

import com.timebox.android.data.parseActivityInstant
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityInstantPlatformTest {
    @Test fun numericOffsetsResolveOnAndroidRuntime() {
        assertEquals("2026-09-11T19:00:00Z", parseActivityInstant("2026-09-11T15:00:00-04:00").toString())
        assertEquals("2026-09-11T07:00:00Z", parseActivityInstant("2026-09-11T15:00:00+08:00").toString())
        assertEquals("2026-09-11T15:00:00Z", parseActivityInstant("2026-09-11T15:00:00Z").toString())
    }
}
