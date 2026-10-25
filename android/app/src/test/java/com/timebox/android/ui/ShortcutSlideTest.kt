package com.timebox.android.ui

import org.junit.Assert.*
import org.junit.Test

class ShortcutSlideTest {
    @Test fun followsSavedOrderAndExcludesUnpinnedOrCurrentDestinations() {
        val pins = listOf("calendar", "day", "plan")
        assertEquals(1, shortcutSlide(pins, "calendar", "plan")?.direction)
        assertEquals(-1, shortcutSlide(pins, "plan", "day")?.direction)
        assertEquals(-1, shortcutSlide(pins.reversed(), "calendar", "plan")?.direction)
        assertNull(shortcutSlide(pins, "day", "day"))
        assertNull(shortcutSlide(pins, "settings", "day"))
        assertNull(shortcutSlide(pins, "day", "settings"))
        assertNull(shortcutSlide(emptyList(), "day", "plan"))
    }
}
