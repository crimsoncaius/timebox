package com.timebox.android.ui.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityStatusTest {
    private fun flags(
        offline: Boolean = false,
        pending: Boolean = false,
        busy: Boolean = false,
        hasSnapshot: Boolean = true,
        error: String? = null,
    ) = StatusFlags(offline, pending, busy, hasSnapshot, error)

    @Test
    fun compactSavingReplacesConfirmationAndKeepsOffline() {
        assertEquals("Saving…", compactStatusLabel(flags(busy = true)))
        assertEquals("Offline · Saving…", compactStatusLabel(flags(offline = true, busy = true)))
    }

    @Test
    fun healthySyncedDrawsNothingAndAttentionIsAChip() {
        assertNull(statusMarkKind(flags()))
        assertEquals("chip", statusMarkKind(flags(pending = true)))
        assertEquals("chip", statusMarkKind(flags(busy = true)))
    }

    @Test
    fun storageOrConnectionFailureLooksLikeUnsyncedWithRetry() {
        val failed = flags(pending = true, error = "Activity storage failed: Storage full")
        assertEquals("Unsynced", compactStatusLabel(failed))
        assertTrue(statusShowsRetry(failed))
        assertEquals("Unsynced", compactStatusLabel(flags(error = "Connection lost")))
        assertTrue(statusShowsRetry(flags(error = "Connection lost")))
        assertFalse(statusShowsRetry(flags(offline = true)))
    }
}
