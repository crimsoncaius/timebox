package com.timebox.android.ui.calendar

import com.timebox.android.data.ReportingTime
import com.timebox.android.ui.day.ActivityTimeValue
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalendarPlanEditTest {
    private val zone = ZoneId.of("America/New_York")
    @Test fun repeatedTimeRequiresChoiceAndLaterChoiceKeepsItsOffset() {
        val ambiguous = ActivityTimeValue("2026-11-01T01:30")
        val end = ActivityTimeValue("2026-11-01T02:30")
        assertTrue(runCatching { CalendarPlanEdit(1, "move", ambiguous, end).payload(zone) }.isFailure)
        val earlier = CalendarPlanEdit(1, "move", ambiguous.copy(occurrence = ReportingTime.Occurrence.Earlier), end)
        val later = earlier.copy(start = ambiguous.copy(occurrence = ReportingTime.Occurrence.Later))
        assertEquals("2026-11-01T05:30:00Z", earlier.payload(zone)["start"]!!.jsonPrimitive.content)
        assertEquals("2026-11-01T06:30:00Z", later.payload(zone)["start"]!!.jsonPrimitive.content)
        assertEquals(120L, earlier.duration(zone))
        assertEquals(60L, later.duration(zone))
        assertTrue(stamp("2026-11-01T05:30:00Z", zone.id).endsWith("-04:00"))
        assertTrue(stamp("2026-11-01T06:30:00Z", zone.id).endsWith("-05:00"))
    }
    @Test fun gapAndReversedIntervalsAreRejectedButMidnightCrossingWorks() {
        assertTrue(runCatching { CalendarPlanEdit(1, "move", ActivityTimeValue("2026-03-08T02:30"), ActivityTimeValue("2026-03-08T04:00")).payload(zone) }.isFailure)
        assertTrue(runCatching { CalendarPlanEdit(1, "move", ActivityTimeValue("2026-11-06T12:00"), ActivityTimeValue("2026-11-06T11:00")).payload(zone) }.isFailure)
        assertEquals(60L, CalendarPlanEdit(1, "move", ActivityTimeValue("2026-11-06T23:30"), ActivityTimeValue("2026-11-07T00:30")).duration(zone))
    }
}
