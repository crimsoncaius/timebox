package com.timebox.android.ui.chronicle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ChronicleScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun committedLeftSwipePreviewsAndAdvancesExactlyOneMonth() {
        val shifts = mutableListOf<Long>()
        compose.mainClock.autoAdvance = false
        showChronicle(
            onPrevMonth = { shifts += -1L },
            onNextMonth = { shifts += 1L },
        )

        compose.onNodeWithContentDescription("Chronicle month content")
            .performTouchInput { swipeLeft() }

        compose.onNodeWithContentDescription("Chronicle September 2026")
            .assertIsDisplayed()
        compose.runOnIdle { assertTrue(shifts.isEmpty()) }

        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(listOf(1L), shifts) }
    }

    @Test
    fun committedRightSwipePreviewsAndReturnsExactlyOneMonth() {
        val shifts = mutableListOf<Long>()
        compose.mainClock.autoAdvance = false
        showChronicle(
            onPrevMonth = { shifts += -1L },
            onNextMonth = { shifts += 1L },
        )

        compose.onNodeWithContentDescription("Chronicle month content")
            .performTouchInput { swipeRight() }

        compose.onNodeWithContentDescription("Chronicle July 2026")
            .assertIsDisplayed()
        compose.runOnIdle { assertTrue(shifts.isEmpty()) }

        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(listOf(-1L), shifts) }
    }

    @Test
    fun belowThresholdDragSnapsBackWithoutChangingMonth() {
        val shifts = mutableListOf<Long>()
        val dragDistance = with(compose.density) { 40.dp.toPx() }
        compose.mainClock.autoAdvance = false
        showChronicle(
            onPrevMonth = { shifts += -1L },
            onNextMonth = { shifts += 1L },
        )

        compose.onNodeWithContentDescription("Chronicle month content")
            .performTouchInput {
                swipe(
                    start = center,
                    end = center + Offset(-dragDistance, 0f),
                    durationMillis = 200,
                )
            }

        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertTrue(shifts.isEmpty()) }
        compose.onNodeWithContentDescription("Chronicle August 2026")
            .assertIsDisplayed()
    }

    private fun showChronicle(
        onPrevMonth: () -> Unit,
        onNextMonth: () -> Unit,
    ) {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                ChronicleScreen(
                    state = ChronicleUiState(
                        monthStart = LocalDate.of(2026, 8, 1),
                        today = LocalDate.of(2026, 8, 25),
                        loading = false,
                    ),
                    onPrevMonth = onPrevMonth,
                    onNextMonth = onNextMonth,
                    onThisMonth = {},
                    onOpenDay = {},
                    onRetry = {},
                )
            }
        }
    }
}
