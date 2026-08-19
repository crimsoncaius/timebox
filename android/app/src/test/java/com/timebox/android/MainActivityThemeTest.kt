package com.timebox.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityThemeTest {
    @Test
    fun `system bar icon appearance follows the app theme`() {
        assertFalse(useDarkSystemBarIcons(isDarkTheme = true))
        assertTrue(useDarkSystemBarIcons(isDarkTheme = false))
    }
}
