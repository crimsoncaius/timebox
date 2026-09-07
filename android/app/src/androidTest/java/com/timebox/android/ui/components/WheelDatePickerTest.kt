package com.timebox.android.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WheelDatePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelDoesNotCommitDate() {
        var committed: LocalDate? = null
        var dismissed = false
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                WheelDatePicker("Start date", LocalDate.of(2026, 9, 7), { committed = it }, { dismissed = true })
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(committed); assertTrue(dismissed) }
    }

    @Test fun endBeforeStartCannotBeCommitted() {
        compose.setContent {
            TimeboxTheme {
                WheelDatePicker("End date", LocalDate.of(2026, 9, 7), {}, {}, LocalDate.of(2026, 9, 8))
            }
        }
        compose.onNodeWithText("Set date").assertIsNotEnabled()
    }

    @Test fun sameDayEndIsAllowedAndPreservesDate() {
        val date = LocalDate.of(2028, 2, 29)
        var committed: LocalDate? = null
        compose.setContent {
            TimeboxTheme {
                WheelDatePicker("End date", date, { committed = it }, {}, date)
            }
        }
        compose.onNodeWithText("Set date").performClick()
        compose.runOnIdle { assertEquals(date, committed) }
    }
}
