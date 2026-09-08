package com.timebox.android.ui.battleplan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                        preplanningSlots = listOf(
                            RecurringPreplanningSlotDraft(start = "23:00", end = "00:00"),
                        ),
                    ),
                    onBack = {}, onTitle = {}, onDescription = {}, onTaskType = {},
                    onUrgency = {}, onImportance = {}, onMode = {}, onFrequency = {},
                    onInterval = {}, onToggleWeekday = {}, onMonthDay = {},
                    onQuotaCount = {}, onStartDate = {}, onEndMode = {}, onEndDate = {},
                    onCycleLimit = {}, onChecklist = {}, onKeepUnfinishedOverdue = {},
                    onPreplanningEnabled = {}, onAddPreplanningSlot = {}, onRemovePreplanningSlot = {},
                    onPreplanningStart = { _, _ -> }, onPreplanningEnd = { _, _ -> },
                    onPreplanningWeekday = { _, _ -> }, onRefreshPreview = {}, onSave = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Pre-plan each Task Occurrence").fetchSemanticsNode()
        compose.onNodeWithText("Planned Block start").fetchSemanticsNode()
        compose.onNodeWithText("23:00").fetchSemanticsNode()
        compose.onNodeWithText("Planned Block end").fetchSemanticsNode()
        compose.onNodeWithText("00:00").fetchSemanticsNode()
    }

    @Test
    fun scheduledEditorExposesMultipleRecurringPreplanningScheduleSlots() {
        var removed = -1
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                RecurringEditorScreen(
                    state = RecurringEditorUiState(
                        templateId = 7,
                        title = "Daily review",
                        startDate = "2026-09-08",
                        preplanningSlots = listOf(
                            RecurringPreplanningSlotDraft(key = "morning", start = "08:00", end = "09:00"),
                            RecurringPreplanningSlotDraft(key = "afternoon", start = "15:00", end = "16:00"),
                        ),
                    ),
                    onBack = {}, onRetry = {}, onTitle = {}, onDescription = {}, onTaskType = {},
                    onUrgency = {}, onImportance = {}, onMode = {}, onFrequency = {},
                    onInterval = {}, onToggleWeekday = {}, onMonthDay = {},
                    onQuotaCount = {}, onStartDate = {}, onEndMode = {}, onEndDate = {},
                    onCycleLimit = {}, onChecklist = {}, onKeepUnfinishedOverdue = {},
                    onPreplanningEnabled = {}, onAddPreplanningSlot = {},
                    onRemovePreplanningSlot = { removed = it },
                    onPreplanningStart = { _, _ -> }, onPreplanningEnd = { _, _ -> },
                    onPreplanningWeekday = { _, _ -> }, onRefreshPreview = {}, onSave = {},
                    onConfirmBackfill = {}, onDismissBackfill = {},
                )
            }
        }

        compose.onNodeWithText("Planned Block start 2").fetchSemanticsNode()
        compose.onNodeWithText("15:00").fetchSemanticsNode()
        compose.onNodeWithContentDescription("Remove pre-planning slot 2")
            .performScrollTo()
            .performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, removed) }
    }
}
