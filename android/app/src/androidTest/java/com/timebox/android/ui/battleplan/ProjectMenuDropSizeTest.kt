package com.timebox.android.ui.battleplan

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.Project
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ProjectMenuDropSizeTest {
    @get:Rule val compose = createComposeRule()
    private val projects = listOf("Alpha", "Beta", "Gamma").mapIndexed { index, name ->
        Project(index + 1, name, Instant.EPOCH, Instant.EPOCH)
    }

    @Test fun projectMenuKeepsItsHeightAcrossDropAndSave() {
        var requested = emptyList<Int>()
        var selected: BattlePlanScope? = null
        var saving by mutableStateOf(false)
        compose.setContent {
            var rows by remember { mutableStateOf(projects) }
            TimeboxTheme(darkTheme = true) {
                BattlePlanScreen(
                    state = BattlePlanUiState(loading = false, projects = rows, projectOrderSaving = saving),
                    onReorderProjects = { ids -> saving = true; requested = ids; rows = ids.map { id -> projects.first { it.id == id } } },
                    onRetry = {}, onSelectScope = { selected = it }, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                    onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                    onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {}, onNewProject = {},
                    onOpenRecurring = {}, onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {}, onRestoreTrashed = {},
                    onUndoTrash = {}, onDismissUndo = {}, onRequestPermanentDelete = {},
                    onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }
        compose.onNodeWithText("All Tasks").performClick()
        compose.onNodeWithText("Reorder projects").assertDoesNotExist()
        val before = compose.onNodeWithTag("battle-plan-scope-menu").fetchSemanticsNode().boundsInRoot.height
        dragAlphaBelowBeta()
        compose.runOnIdle { assertEquals(listOf(2, 1, 3), requested); assertNull(selected) }
        compose.onNodeWithTag("battle-plan-scope-menu").assertIsDisplayed()
        compose.onNodeWithText("PROJECTS · SAVING…").assertIsDisplayed()
        val during = compose.onNodeWithTag("battle-plan-scope-menu").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { saving = false }
        compose.onNodeWithText("PROJECTS · SAVING…").assertDoesNotExist()
        val after = compose.onNodeWithTag("battle-plan-scope-menu").fetchSemanticsNode().boundsInRoot.height

        assertEquals("Menu grows while saving: before=$before saving=$during after=$after", before, during, 0.5f)
        assertEquals(before, after, 0.5f)
    }
    private fun dragAlphaBelowBeta() {
        val first = compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Alpha").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, second.center.y - first.center.y), delayMillis = 100)
            up()
        }
    }
}


