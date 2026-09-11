package com.timebox.android.data

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReportingTimeTest {
    @Test fun repeatedTimeRequiresChoiceAndGapIsRejected() {
        val zone = ZoneId.of("America/New_York")
        val repeated = LocalDateTime.parse("2025-11-02T01:30")
        assertEquals(listOf("2025-11-02T05:30:00Z", "2025-11-02T06:30:00Z"), ReportingTime.candidates(repeated, zone).map { it.toString() })
        assertThrows(IllegalArgumentException::class.java) { ReportingTime.resolve(repeated, zone) }
        assertEquals("2025-11-02T06:30:00Z", ReportingTime.resolve(repeated, zone, ReportingTime.Occurrence.Later).toString())
        assertThrows(IllegalArgumentException::class.java) { ReportingTime.resolve(LocalDateTime.parse("2026-03-08T02:30"), zone) }
    }
}
