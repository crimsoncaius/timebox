package com.timebox.android.ui.battleplan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test

class RecurringCreationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun scheduledCreationExposesOneRecurringPreplanningScheduleSlot() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                RecurringCreationContent(
                    state = RecurringEditorUiState(
                        title = "Morning review",
                        startDate = "2026-09-08",
                        preplanningEnabled = true,
                        preplanningStart = "23:00",
                        preplanningEnd = "00:00",
                    ),
                    onBack = {}, onTitle = {}, onDescription = {}, onTaskType = {},
                    onUrgency = {}, onImportance = {}, onMode = {}, onFrequency = {},
                    onInterval = {}, onToggleWeekday = {}, onMonthDay = {},
                    onQuotaCount = {}, onStartDate = {}, onEndMode = {}, onEndDate = {},
                    onCycleLimit = {}, onChecklist = {}, onKeepUnfinishedOverdue = {},
                    onPreplanningEnabled = {}, onPreplanningStart = {}, onPreplanningEnd = {},
                    onPreplanningWeekday = {}, onRefreshPreview = {}, onSave = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Pre-plan each Task Occurrence").fetchSemanticsNode()
        compose.onNodeWithText("Planned Block start").fetchSemanticsNode()
        compose.onNodeWithText("23:00").fetchSemanticsNode()
        compose.onNodeWithText("Planned Block end").fetchSemanticsNode()
        compose.onNodeWithText("00:00").fetchSemanticsNode()
    }
}
