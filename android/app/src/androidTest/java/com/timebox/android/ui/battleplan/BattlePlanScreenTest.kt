package com.timebox.android.ui.battleplan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.timebox.android.data.TaskType
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test

class BattlePlanScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactActiveViewUsesOneLogicalNavigationGroup() {
        var recurringOpened = false
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                BattlePlanScreen(
                    state = BattlePlanUiState(loading = false),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {},
                    onToggleTaskType = {}, onClearFilters = {}, onOpenTask = {},
                    onToggleReady = {}, onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {}, onNewProject = {},
                    onOpenRecurring = { recurringOpened = true },
                    onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {},
                    onRestoreTrashed = {}, onUndoTrash = {}, onDismissUndo = {},
                    onRequestPermanentDelete = {}, onDismissPermanentDelete = {},
                    onConfirmPermanentDelete = {},
                )
            }
        }

        compose.onNodeWithText("All Tasks", substring = true).performClick()
        compose.onNodeWithText("Admin").fetchSemanticsNode()
        compose.onNodeWithText("Recurring").performClick()
        compose.runOnIdle { check(recurringOpened) }
        compose.onNodeWithText("Open", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("In Progress", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("Completed", substring = true).fetchSemanticsNode()
    }

    @Test
    fun compactFiltersUseSectionsWithoutUnsetAndForwardChipTaps() {
        var urgencyTapped: String? = null
        var taskTypeTapped: String? = null
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                BattlePlanScreen(
                    state = BattlePlanUiState(
                        loading = false,
                        taskTypes = listOf(TaskType(id = 7, name = "Work / Focus", usageCount = 0)),
                    ),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = { urgencyTapped = it }, onToggleImportance = {},
                    onToggleTaskType = { taskTypeTapped = it }, onClearFilters = {}, onOpenTask = {},
                    onToggleReady = {}, onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {}, onNewProject = {},
                    onOpenRecurring = {}, onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {}, onRestoreTrashed = {},
                    onUndoTrash = {}, onDismissUndo = {}, onRequestPermanentDelete = {},
                    onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Filter tasks").performClick()
        compose.onNodeWithText("Filters").fetchSemanticsNode()
        compose.onNodeWithText("URGENCY").fetchSemanticsNode()
        compose.onNodeWithText("IMPORTANCE").fetchSemanticsNode()
        compose.onNodeWithText("TASK TYPE").fetchSemanticsNode()
        check(compose.onAllNodesWithText("Unset").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Show 0 tasks").fetchSemanticsNode()

        compose.onAllNodesWithText("High")[0].performClick()
        compose.onNodeWithText("Work / Focus").performClick()
        compose.runOnIdle {
            check(urgencyTapped == "high")
            check(taskTypeTapped == "7")
        }
    }
}
