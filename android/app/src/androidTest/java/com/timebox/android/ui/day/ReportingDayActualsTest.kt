package com.timebox.android.ui.day

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.*
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReportingDayActualsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun repeatedHourShowsApiElapsedShareAndBothOffsets() {
        val date = LocalDate.parse("2025-11-02")
        val actual = ActualBlock(42, 1, "Reading", null, null, null, null, Instant.parse("2025-11-02T05:50:00Z"), Instant.parse("2025-11-02T06:10:00Z"))
        val block = TimeBlock(-42, Lane.Actual, 1, "Reading", null, null, null, null, actualBlockId = 42, startMinute = 110, endMinute = 70)
        val day = Day(date, 0, 24, true, listOf(block), listOf(ActualBlockDayProjection(actual, date, 110, 70, 20, 1500)), "America/New_York", date, null)
        var selected = 0
        compose.setContent { TimeboxTheme(darkTheme = false) { ReportingDayActuals(day) { selected = it } } }
        compose.onNodeWithText("20m on this day", substring = true).assertIsDisplayed()
        compose.onNodeWithText("-04:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("-05:00", substring = true).performClick()
        compose.runOnIdle { assertEquals(-42, selected) }
    }
}
