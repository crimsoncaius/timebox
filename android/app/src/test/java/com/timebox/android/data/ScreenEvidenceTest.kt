package com.timebox.android.data

import com.timebox.android.checkin.ScreenEvidence
import com.timebox.android.checkin.ScreenObservation
import org.junit.Assert.*
import org.junit.Test

class ScreenEvidenceTest {
    @Test fun `missing usage events never prove inactivity`() {
        assertNull(ScreenEvidence.interval(emptyList(), 1000, false))
    }
    @Test fun `explicit screen off followed by screen on qualifies only that interval`() {
        val interval = ScreenEvidence.interval(listOf(ScreenObservation(100, false), ScreenObservation(900, true)), 1000, true)
        assertEquals(100L to 900L, interval)
    }
    @Test fun `API26 and 27 do not advertise historical screen support`() {
        assertFalse(ScreenEvidence.supported(26)); assertFalse(ScreenEvidence.supported(27))
        assertTrue(ScreenEvidence.supported(28)); assertTrue(ScreenEvidence.supported(36))
    }
    @Test fun `current off state cannot invent a missing historical start`() {
        assertNull(ScreenEvidence.interval(listOf(ScreenObservation(100, true)), 1000, false))
    }
    @Test fun `explicit open off interval requires matching current power state`() {
        assertEquals(100L to 1000L, ScreenEvidence.interval(listOf(ScreenObservation(100, false)), 1000, false))
        assertNull(ScreenEvidence.interval(listOf(ScreenObservation(100, false)), 1000, true))
    }
    @Test fun `new explicit off interval wins over an earlier completed interval`() {
        val events = listOf(ScreenObservation(100, false), ScreenObservation(300, true), ScreenObservation(900, false))
        assertEquals(900L to 1000L, ScreenEvidence.interval(events, 1000, false))
    }
    @Test fun `future transitions are unknown rather than negative coverage`() {
        assertNull(ScreenEvidence.interval(listOf(ScreenObservation(2000, false)), 1000, false))
    }
}
