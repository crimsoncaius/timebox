package com.timebox.android.ui.day

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SwitchTimelineModelTest {
    private val now = Instant.parse("2026-09-12T14:00:00Z")
    private val window = SwitchTimelineWindow.around(now)

    @Test fun selectionCannotRewriteEarlierActivitiesOrReachTheFuture() {
        val start = now.minusSeconds(3707)
        assertEquals(start, window.select(-1f, start, now, emptyList()))
        assertEquals(now, window.select(2f, start, now, emptyList()))
    }

    @Test fun snappingPreservesTheExactBoundaryAndIgnoresInvalidBoundaries() {
        val exact = now.minusSeconds(1817)
        assertEquals(exact, window.select(window.fraction(exact.plusSeconds(40)), now.minusSeconds(3600), now, listOf(exact)))
        assertEquals(now, window.select(1f, now.minusSeconds(3600), now, listOf(now.plusSeconds(30))))
    }

    @Test fun earlierWindowsCrossMidnightAndCanReturnToNow() {
        val midnight = SwitchTimelineWindow.around(Instant.parse("2026-09-12T00:15:00Z"))
        assertEquals(Instant.parse("2026-09-11T22:00:00Z"), midnight.start)
        assertEquals(midnight, midnight.shift(-2).shift(2))
    }

    @Test fun repeatedLocalTimesRemainDifferentInstantsAndHaveDistinctLabels() {
        val zone = ZoneId.of("America/New_York")
        val earlier = Instant.parse("2025-11-02T05:30:00Z")
        val later = Instant.parse("2025-11-02T06:30:00Z")
        val dstWindow = SwitchTimelineWindow(earlier.minusSeconds(1800))
        assertEquals(earlier, dstWindow.select(dstWindow.fraction(earlier), dstWindow.start, later.plusSeconds(3600), emptyList()))
        assertEquals(later, dstWindow.select(dstWindow.fraction(later), dstWindow.start, later.plusSeconds(3600), emptyList()))
        assertNotEquals(switchTimeLabel(earlier, zone), switchTimeLabel(later, zone))
        assertEquals(earlier, ActivityTimeValue.from(earlier, zone).resolve(zone))
        assertEquals(later, ActivityTimeValue.from(later, zone).resolve(zone))
    }
}
