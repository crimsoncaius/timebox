package com.timebox.android.ui.day

import com.timebox.android.data.remote.ActivityPlanDto
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlannedBlockSuggestionTest {
    private val plan = ActivityPlanDto(4, 2, null, "Chapter", null, "2026-09-17T09:00:00Z", "2026-09-17T10:30:00Z")

    @Test
    fun timingShowsAllocatedTimesInReportingTimezoneAndTimeLeft() {
        assertEquals(
            "5:00 PM – 6:30 PM · 1 hour 7 mins left",
            plannedSuggestionTiming(plan, Instant.parse("2026-09-17T09:23:00Z"), ZoneId.of("Asia/Singapore"), Locale.US),
        )
    }

    @Test
    fun timeLeftNeverGoesNegative() {
        assertEquals(
            "9:00 AM – 10:30 AM · 0 mins left",
            plannedSuggestionTiming(plan, Instant.parse("2026-09-17T11:00:00Z"), ZoneId.of("UTC"), Locale.US),
        )
    }
}
