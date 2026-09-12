package com.timebox.android.ui.day

import com.timebox.android.data.ReportingTime
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StopTrackingPreviewTest {
    private val now = Instant.parse("2026-09-12T06:45:37Z")
    private val start = Instant.parse("2026-09-12T06:00:12Z")
    private val zone = ZoneId.of("Asia/Singapore")

    @Test fun nowUsesReportingZoneAndActualElapsedSeconds() {
        val preview = stopTrackingPreview(start, null, now, zone)
        assertEquals("14:00 – 14:45", preview.range)
        assertEquals(45L, preview.minutes)
        assertNull(preview.dateContext)
    }
    @Test fun backdatePreservesOriginalSeconds() {
        val preview = stopTrackingPreview(start, ActivityTimeValue.from(now.minusSeconds(900), zone), now, zone)
        assertEquals(30L, preview.minutes)
        assertEquals("14:30", preview.endLabel)
    }
    @Test fun rejectsEndBeforeStartAndInFuture() {
        listOf(start.minusSeconds(1), now.plusSeconds(1)).forEach {
            assertThrows(IllegalArgumentException::class.java) { stopTrackingPreview(start, ActivityTimeValue.from(it, zone), now, zone) }
        }
    }
    @Test fun overnightRangeShowsBothDates() {
        val preview = stopTrackingPreview(Instant.parse("2026-09-11T15:30:00Z"), null, now, zone)
        assertEquals("11 Sep 2026 – 12 Sep 2026", preview.dateContext)
    }
    @Test fun repeatedHourUsesElapsedTimeAndShowsOffsets() {
        val eastern = ZoneId.of("America/New_York")
        val end = ActivityTimeValue("2025-11-02T01:30", ReportingTime.Occurrence.Later)
        val preview = stopTrackingPreview(Instant.parse("2025-11-02T05:30:00Z"), end, Instant.parse("2025-11-02T07:00:00Z"), eastern)
        assertEquals(60L, preview.minutes)
        assertEquals("01:30 -04:00 – 01:30 -05:00", preview.range)
        assertThrows(IllegalArgumentException::class.java) {
            stopTrackingPreview(Instant.parse("2025-11-02T05:00:00Z"), end.copy(occurrence = null), Instant.parse("2025-11-02T07:00:00Z"), eastern)
        }
    }
}
