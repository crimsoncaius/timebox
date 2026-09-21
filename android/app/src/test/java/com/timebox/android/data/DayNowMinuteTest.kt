package com.timebox.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The now line's minute, which advances off the fetch rather than the device's clock. */
class DayNowMinuteTest {

    private fun day(serverNowMinute: Int?, capturedAtElapsedMillis: Long = 1_000_000L) = Day(
        date = LocalDate.of(2026, 8, 10),
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        blocks = emptyList(),
        timezone = "Asia/Singapore",
        today = LocalDate.of(2026, 8, 10),
        elapsedRealtime = { 0L }, serverNowMinute = serverNowMinute,
        capturedAtElapsedMillis = capturedAtElapsedMillis,
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
    fun `rejects an elapsed reading earlier than its capture`() {
        assertEquals(556, day(556).nowMinuteAt(1_000_000L - 5 * 60_000L))
    }

    @Test
    fun `stays unknown when the server sent no clock`() {
        assertNull(day(null).nowMinuteAt(1_000_000L))
    }

    @Test
    fun `now and Actual bounds advance through sleep without reading wall time`() {
        var elapsed = 1_000_000L
        val d = day(556).copy(elapsedRealtime = { elapsed })
        assertEquals(556, d.nowMinuteAt())
        assertEquals(556, com.timebox.android.ui.day.actualPlacementEnd(d))
        // Both consumers must use this elapsed clock, never the host's epoch clock.
        elapsed += 90 * 60_000L // Android elapsedRealtime includes deep sleep.
        assertEquals(646, d.nowMinuteAt())
        assertEquals(646, com.timebox.android.ui.day.actualPlacementEnd(d))
    }

    @Test
    fun `fresh server responses recapture elapsed time and reporting zone`() {
        var elapsed = 1_000_000L
        fun fetch(zone: String, minute: String): Day =
            com.timebox.android.data.remote.ApiFactory.json.decodeFromString(
                com.timebox.android.data.remote.DayDto.serializer(),
                """{"id":1,"date":"2026-08-10","start_hour":0,"end_hour":24,
                "show_full_day":true,"time_blocks":[],"actual_blocks":[],
                "meta":{"timezone":"$zone","today":"2026-08-10",
                "server_now_iso":"2026-08-10T$minute"}}""",
            ).toModel(elapsedRealtime = { elapsed })
        val first = fetch("Asia/Singapore", "09:16:00+08:00")
        elapsed += 60_000L
        assertEquals(557, first.nowMinuteAt())
        val fresh = fetch("UTC", "01:20:00Z")
        assertEquals("UTC", fresh.timezone)
        assertEquals(80, fresh.nowMinuteAt())
        elapsed += 60_000L
        assertEquals(81, fresh.nowMinuteAt())
        assertEquals(558, first.copy(blocks = emptyList()).nowMinuteAt())
    }
}
