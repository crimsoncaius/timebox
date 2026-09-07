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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectNavigationListTest {
    @get:Rule val compose = createComposeRule()
    private val projects = listOf("Alpha", "Beta", "Gamma").mapIndexed { index, name ->
        Project(index + 1, name, Instant.EPOCH, Instant.EPOCH)
    }

    @Test fun primaryProjectMenuSupportsHoldingAProjectRowToReorderWithoutOpeningAnotherDialog() {
        var requested = emptyList<Int>()
        var selected: BattlePlanScope? = null
        compose.setContent {
            var rows by remember { mutableStateOf(projects) }
            TimeboxTheme(darkTheme = true) {
                BattlePlanScreen(
                    state = BattlePlanUiState(loading = false, projects = rows),
                    onReorderProjects = { ids -> requested = ids; rows = ids.map { id -> projects.first { it.id == id } } },
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
        dragAlphaBelowBeta()
        compose.runOnIdle { assertEquals(listOf(2, 1, 3), requested); assertNull(selected) }
        compose.onNodeWithTag("battle-plan-scope-menu").assertIsDisplayed()
        compose.onNodeWithText("Beta").performClick()
        compose.runOnIdle { assertEquals(2, selected?.projectId) }
        compose.onNodeWithTag("battle-plan-scope-menu").assertDoesNotExist()
    }

    @Test fun projectActionsEditOrRequestDeletionWithoutChangingTheOrder() {
        var requested = emptyList<Int>()
        var edited: Project? = null
        var deleted: Project? = null
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                ProjectNavigationList(
                    projects = projects,
                    selectedId = null,
                    saving = false,
                    onSelect = {},
                    onReorder = { requested = it },
                    onEdit = { edited = it },
                    onDelete = { deleted = it },
                )
            }
        }
        compose.onNodeWithContentDescription("More actions for Alpha").performClick()
        compose.onNodeWithText("Move up").assertDoesNotExist()
        compose.onNodeWithText("Move down").assertDoesNotExist()
        compose.onNodeWithText("Edit").performClick()
        compose.runOnIdle { assertEquals(projects.first(), edited); assertNull(deleted); assertEquals(emptyList<Int>(), requested) }

        compose.onNodeWithContentDescription("More actions for Alpha").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(projects.first(), deleted); assertEquals(emptyList<Int>(), requested) }
    }

    @Test fun canceledProjectRowDragPreservesTheSavedOrder() {
        var requested = emptyList<Int>()
        compose.setContent {
            TimeboxTheme(darkTheme = false) { ProjectNavigationList(projects, null, false, {}, { requested = it }) }
        }
        val first = compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Alpha").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, second.center.y - first.center.y), delayMillis = 100)
            cancel()
        }
        compose.runOnIdle { assertEquals(emptyList<Int>(), requested) }
    }

    @Test fun completedProjectRowDragKeepsTheOptimisticOrderWhileSaveIsPending() {
        var requested = emptyList<Int>()
        compose.setContent {
            TimeboxTheme(darkTheme = false) { ProjectNavigationList(projects, null, false, {}, { requested = it }) }
        }
        dragAlphaBelowBeta()
        compose.runOnIdle { assertEquals(listOf(2, 1, 3), requested) }
        val alpha = compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot
        val beta = compose.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot
        assertTrue(alpha.top > beta.top)
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
