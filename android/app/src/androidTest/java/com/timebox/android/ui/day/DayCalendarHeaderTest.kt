package com.timebox.android.ui.day

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.planning.PlanningSessionState
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DayCalendarHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun timelineHeadersKeepBreathingRoomBelowTheCalendar() {
        showDay(
            state = DayUiState(
                date = LocalDate.of(2026, 8, 22),
                today = LocalDate.of(2026, 8, 22),
            ),
        )

        val dividerBottom = compose.onNodeWithTag("day-header-divider")
            .fetchSemanticsNode().boundsInRoot.bottom
        val headersTop = compose.onAllNodesWithTag("day-lane-headers")
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= dividerBottom }
            .minBy { it.boundsInRoot.top }
            .boundsInRoot.top
        val expectedGap = with(compose.density) { 8.dp.toPx() }

        val actualGap = headersTop - dividerBottom
        assertTrue(
            "Expected at least $expectedGap px between divider and lane headers, found $actualGap px",
            actualGap >= expectedGap,
        )
    }

    @Test
    fun selectedCalendarModeFillsTheToggleTrack() {
        showDay(
            state = DayUiState(
                date = LocalDate.of(2026, 8, 22),
                today = LocalDate.of(2026, 8, 22),
            ),
        )

        val controlHeight = compose.onNodeWithTag("calendar-mode-control")
            .fetchSemanticsNode().boundsInRoot.height
        val selectedHeight = compose.onNodeWithTag("calendar-mode-week")
            .fetchSemanticsNode().boundsInRoot.height
        val inset = with(compose.density) { 4.dp.toPx() }

        assertTrue(selectedHeight >= controlHeight - inset)
    }

    @Test
    fun weekSwipePreviewsTheIncomingWeekBeforeNavigationSettles() {
        val selected = LocalDate.of(2026, 8, 28)
        val navigated = mutableListOf<LocalDate>()
        compose.mainClock.autoAdvance = false

        showDay(
            state = DayUiState(date = selected, today = selected),
            onDateSettled = navigated::add,
        )

        compose.onNodeWithContentDescription("Week dates").performTouchInput { swipeLeft() }

        compose.onNodeWithContentDescription("Friday, September 4, 2026").assertIsDisplayed()
        compose.runOnIdle { assertTrue(navigated.isEmpty()) }

        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(listOf(LocalDate.of(2026, 9, 4)), navigated) }
    }

    @Test
    fun weekShowsSevenDatesAndSupportsDateTodayAndWeekNavigation() {
        val selected = LocalDate.of(2026, 8, 28)
        val today = LocalDate.of(2026, 8, 30)
        val navigated = mutableListOf<LocalDate>()
        var todayNavigation: LocalDate? = null

        showDay(
            state = DayUiState(date = selected, today = today),
            onDateSettled = navigated::add,
            onNavigateToday = { todayNavigation = it },
        )

        listOf(
            "Monday, August 24, 2026",
            "Tuesday, August 25, 2026",
            "Wednesday, August 26, 2026",
            "Thursday, August 27, 2026",
            "Friday, August 28, 2026",
            "Saturday, August 29, 2026",
            "Sunday, August 30, 2026",
        ).forEach { compose.onNodeWithContentDescription(it).fetchSemanticsNode() }
        compose.onNodeWithContentDescription("Friday, August 28, 2026").assertIsSelected()

        compose.onNodeWithContentDescription("Wednesday, August 26, 2026").performClick()
        compose.onNodeWithContentDescription("Week dates").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Go to today")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        compose.runOnIdle {
            assertEquals(listOf(LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 4)), navigated)
            assertEquals(today, todayNavigation)
        }
    }

    @Test
    fun todayBecomesStatusWhenSelectedDateIsToday() {
        val selected = LocalDate.of(2026, 8, 28)

        showDay(state = DayUiState(date = selected, today = selected))
        compose.onNodeWithContentDescription("Viewing today")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        compose.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun todayIsDisabledUntilBackendDateIsResolved() {
        val selected = LocalDate.of(2026, 8, 28)

        showDay(state = DayUiState(date = selected, today = null))
        compose.onNodeWithContentDescription("Today unavailable")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun inlineMonthStaysOpenAndNavigatesOnDateSelection() {
        val selected = LocalDate.of(2026, 8, 28)
        var picked: LocalDate? = null

        showDay(
            state = DayUiState(date = selected, today = selected),
            onDateSettled = { picked = it },
        )

        compose.onNodeWithText("Month").performClick()
        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Monday, August 10, 2026").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 8, 10), picked) }
        compose.onNodeWithText("Month").assertIsSelected()
        compose.onNodeWithText("August 2026").assertIsDisplayed()
    }

    @Test
    fun adjacentMonthDateIsTappableAndAdvancesTheGrid() {
        val selected = LocalDate.of(2026, 8, 28)
        var picked: LocalDate? = null

        showDay(
            state = DayUiState(date = selected, today = selected),
            onDateSettled = { picked = it },
        )

        compose.onNodeWithText("Month").performClick()
        compose.onNode(
            hasContentDescription("Tuesday, September 1, 2026") and
                hasStateDescription("Outside displayed month"),
        ).performClick()

        compose.runOnIdle { assertEquals(LocalDate.of(2026, 9, 1), picked) }
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithText("Month").assertIsSelected()
    }

    @Test
    fun weekControlRestoresTheExistingWeekStrip() {
        val selected = LocalDate.of(2026, 8, 28)

        showDay(state = DayUiState(date = selected, today = selected))

        compose.onNodeWithText("Month").performClick()
        compose.onNodeWithText("Week").performClick()

        compose.onNodeWithText("Week").assertIsSelected()
        compose.onNodeWithText("August 2026").assertDoesNotExist()
        compose.onNodeWithContentDescription("Friday, August 28, 2026").assertIsSelected()
    }

    @Test
    fun monthSwipeBrowsesWithoutNavigatingTheTimeline() {
        val selected = LocalDate.of(2026, 8, 28)
        val navigated = mutableListOf<LocalDate>()

        showDay(
            state = DayUiState(date = selected, today = selected),
            onDateSettled = navigated::add,
        )

        compose.onNodeWithText("Month").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("August 2026").performTouchInput { swipeLeft() }

        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.runOnIdle { assertTrue(navigated.isEmpty()) }

        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.runOnIdle { assertTrue(navigated.isEmpty()) }
    }

    @Test
    fun todayKeepsMonthOpenAndSynchronizesTheDisplayedMonth() {
        val initialDate = LocalDate.of(2026, 8, 28)
        val today = LocalDate.of(2026, 9, 2)
        var navigated: LocalDate? = null

        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                var selectedDate by remember { mutableStateOf(initialDate) }
                DayScreen(
                    state = DayUiState(date = selectedDate, today = today),
                    onNavigateToday = { date ->
                        navigated = date
                        selectedDate = date
                    },
                    onDateSettled = { selectedDate = it },
                    onRetry = {}, onTapSlot = { _, _ -> }, onSelectBlock = {},
                    onCommitMove = { _, _, _ -> }, onDismissSheet = {}, onChooseType = {},
                    onTypeQueryChange = {}, onCreateType = {}, onNoteChange = {},
                    onDeleteSelected = {},
                    onConfirmSelectedTaskCompletion = {},
                    onReopenSelectedTask = {}, onOpenLinkedTask = {},
                    onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithText("Month").performClick()
        compose.onNodeWithContentDescription("Go to today").performClick()

        compose.runOnIdle { assertEquals(today, navigated) }
        compose.onNodeWithText("Month").assertIsSelected()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNode(
            hasContentDescription("Wednesday, September 2, 2026") and
                hasStateDescription("Selected, Today"),
        ).assertIsSelected()
    }

    @Test
    fun titleLedHeaderSeparatesDateNavigationPlanningAndCalendarMode() {
        var planning = false
        var workModeOpened = false

        showDay(
            state = DayUiState(
                date = LocalDate.of(2026, 8, 28),
                today = LocalDate.of(2026, 8, 30),
            ),
            onSetPlanningMode = { planning = it },
            onOpenWorkMode = { workModeOpened = true },
        )

        compose.onNodeWithText("DAY").assertIsDisplayed()
        compose.onNodeWithText("Friday, August 28").assertIsDisplayed()
        compose.onNodeWithText("Go to today").assertIsDisplayed()
        compose.onNodeWithText("Week").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Month").assertIsDisplayed()
        compose.onNodeWithContentDescription("Work Mode").assertIsDisplayed().performClick()
        val workModeBounds = compose.onNodeWithTag("work-mode-action").fetchSemanticsNode().boundsInRoot
        val planningBounds = compose.onNodeWithTag("planning-mode-action").fetchSemanticsNode().boundsInRoot
        assertTrue(workModeBounds.right <= planningBounds.left)
        compose.onNodeWithText("Plan").performClick()
        compose.onNodeWithContentDescription("Day review").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Toggle theme").assertDoesNotExist()

        compose.runOnIdle {
            assertTrue(workModeOpened)
            assertTrue(planning)
        }
    }

    @Test
    fun planningModeUsesDoneAction() {
        var committed = false

        showDay(
            state = DayUiState(
                date = LocalDate.of(2026, 8, 28),
                today = LocalDate.of(2026, 8, 28),
                planning = PlanningSessionState(active = true),
            ),
            onCommitPlanningMode = { committed = true },
        )

        compose.onNodeWithText("Month").performClick()
        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithText("Month").assertIsSelected()
        compose.onNodeWithContentDescription("Work Mode").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(committed) }
    }

    private fun showDay(
        state: DayUiState,
        onDateSettled: (LocalDate) -> Unit = {},
        onNavigateToday: (LocalDate) -> Unit = {},
        onSetPlanningMode: (Boolean) -> Unit = {},
        onCommitPlanningMode: () -> Unit = {},
        onOpenWorkMode: () -> Unit = {},
    ) {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                DayScreen(
                    state = state,
                    onNavigateToday = onNavigateToday,
                    onDateSettled = onDateSettled,
                    onRetry = {}, onTapSlot = { _, _ -> }, onSelectBlock = {},
                    onCommitMove = { _, _, _ -> }, onDismissSheet = {}, onChooseType = {},
                    onTypeQueryChange = {}, onCreateType = {}, onNoteChange = {},
                    onDeleteSelected = {},
                    onConfirmSelectedTaskCompletion = {},
                    onReopenSelectedTask = {}, onOpenLinkedTask = {},
                    onSetPlanningMode = onSetPlanningMode, onPlanTask = { _, _ -> },
                    onCommitPlanningMode = onCommitPlanningMode,
                    onOpenWorkMode = onOpenWorkMode,
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }
    }
}
