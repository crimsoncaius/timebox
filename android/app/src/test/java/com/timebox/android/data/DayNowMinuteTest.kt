package com.timebox.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The now line's minute, which advances off the fetch rather than the device's clock. */
class DayNowMinuteTest {

    private fun day(serverNowMinute: Int?, capturedAtMillis: Long = 1_000_000L) = Day(
        date = LocalDate.of(2026, 8, 10),
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        blocks = emptyList(),
        timezone = "Asia/Singapore",
        today = LocalDate.of(2026, 8, 10),
        serverNowMinute = serverNowMinute,
        capturedAtMillis = capturedAtMillis,
    )

    @Test
    fun `returns the server minute at the moment of capture`() {
        assertEquals(556, day(556).nowMinuteAt(1_000_000L))
    }

    @Test
    fun `advances by whole minutes elapsed since the fetch`() {
        val d = day(556)
        assertEquals(556, d.nowMinuteAt(1_000_000L + 59_000L))
        assertEquals(557, d.nowMinuteAt(1_000_000L + 60_000L))
        assertEquals(616, d.nowMinuteAt(1_000_000L + 60 * 60_000L))
    }

    @Test
    fun `never runs backwards when the device clock jumps back`() {
        assertEquals(556, day(556).nowMinuteAt(1_000_000L - 5 * 60_000L))
    }

    @Test
    fun `stays unknown when the server sent no clock`() {
        assertNull(day(null).nowMinuteAt(1_000_000L))
    }
}
