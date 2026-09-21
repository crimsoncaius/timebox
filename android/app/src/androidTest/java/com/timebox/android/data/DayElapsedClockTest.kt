package com.timebox.android.data

import android.os.SystemClock
import com.timebox.android.ui.day.actualPlacementEnd
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayElapsedClockTest {
    @Test
    fun productionClockCapturesAndAdvancesInAndroidElapsedRealtimeDomain() {
        val before = SystemClock.elapsedRealtime()
        val date = LocalDate.of(2026, 8, 10)
        val day = Day(date, 0, 24, true, emptyList(), timezone = "Asia/Singapore",
            today = date, serverNowMinute = 600)
        val after = SystemClock.elapsedRealtime()
        assertTrue(day.capturedAtElapsedMillis in before..after)
        assertEquals(600, day.nowMinuteAt())
        assertEquals(600, actualPlacementEnd(day))
        val afterSleep = day.copy(capturedAtElapsedMillis = after - 90 * 60_000L)
        assertEquals(690, afterSleep.nowMinuteAt())
        assertEquals(690, actualPlacementEnd(afterSleep))
    }
}
