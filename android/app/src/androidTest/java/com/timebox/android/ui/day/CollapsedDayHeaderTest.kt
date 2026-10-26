package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CollapsedDayHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hiddenSectionsKeepDatesReadableAndActionsReachable() {
        val today = LocalDate.of(2026, 9, 23)
        val selected = mutableStateOf(today)
        val calendar = mutableStateOf(false)
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                Box(Modifier.width(360.dp)) {
                    DayCalendarHeader(
                        selectedDate = selected.value, today = today,
                        isPlanningMode = false, showCalendar = calendar.value,
                        hiddenTrackingAction = { TextButton(onClick = {}) { Text("● Tracking !") } },
                        viewAction = { IconButton(onClick = {}) { Icon(Icons.Outlined.Tune, "Day view options") } },
                        onSetPlanningMode = {}, onSelectDate = { selected.value = it },
                        onNavigateToday = { selected.value = it },
                    )
                }
            }
        }
        for (showCalendar in listOf(false, true)) {
            for (date in listOf(today, today.minusDays(1), today.plusDays(1), today.plusYears(1))) {
                compose.runOnIdle { calendar.value = showCalendar; selected.value = date }
                for (text in listOf(dayTitle(date, today), dayTitleKicker(date, today))) {
                    val results = mutableListOf<TextLayoutResult>()
                    compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                    val layout = results.single()
                    assertFalse("Clipped date height: $text", layout.didOverflowHeight)
                    for (line in 0 until layout.lineCount) {
                        assertFalse("Ellipsized date: $text", layout.isLineEllipsized(line))
                        // Text's integer pixel bounds can round down its fractional glyph width.
                        assertTrue("Clipped date width: $text", layout.getLineRight(line) <= layout.size.width + 1f)
                    }
                }
                compose.onNodeWithText("● Tracking !").assertIsDisplayed()
                compose.onNodeWithText("Day").assertIsDisplayed()
                val titleBounds = compose.onNodeWithText("Day").fetchSemanticsNode().boundsInRoot
                val dateBounds = compose.onNodeWithText(dayTitle(date, today)).fetchSemanticsNode().boundsInRoot
                assertTrue("Date needs its own row below Day", dateBounds.top > titleBounds.bottom)
                compose.onNodeWithText("Plan").assertIsDisplayed()
                compose.onNodeWithContentDescription("Day view options").assertIsDisplayed()
                compose.onNodeWithContentDescription("Previous day").assertDoesNotExist()
                compose.onNodeWithContentDescription("Next day").assertDoesNotExist()
                if (date != today) {
                    compose.onNodeWithContentDescription("Go to today").assertIsDisplayed().performClick()
                    compose.onNodeWithText(dayTitleKicker(today, today)).assertIsDisplayed()
                }
            }
        }
    }
}
