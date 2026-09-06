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

class ProjectNavigationListTest {
    @get:Rule val compose = createComposeRule()
    private val projects = listOf("Alpha", "Beta", "Gamma").mapIndexed { index, name ->
        Project(index + 1, name, "", null, null, Instant.EPOCH, Instant.EPOCH)
    }

    @Test fun primaryProjectMenuSupportsDraggingWithoutOpeningAnotherDialog() {
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

    @Test fun projectActionsMoveWithoutDraggingAndRespectBoundaries() {
        var requested = emptyList<Int>()
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                ProjectNavigationList(projects, null, false, {}, { requested = it })
            }
        }
        compose.onNodeWithContentDescription("More actions for Alpha").performClick()
        compose.onNodeWithText("Move up").assertIsNotEnabled()
        compose.onNodeWithText("Move down").performClick()
        compose.runOnIdle { assertEquals(listOf(2, 1, 3), requested) }
    }

    @Test fun canceledDragPreservesTheSavedOrder() {
        var requested = emptyList<Int>()
        compose.setContent {
            TimeboxTheme(darkTheme = false) { ProjectNavigationList(projects, null, false, {}, { requested = it }) }
        }
        val first = compose.onNodeWithContentDescription("Drag Alpha").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription("Drag Beta").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Drag Alpha").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, second.center.y - first.center.y), delayMillis = 100)
            cancel()
        }
        compose.runOnIdle { assertEquals(emptyList<Int>(), requested) }
    }

    private fun dragAlphaBelowBeta() {
        val first = compose.onNodeWithContentDescription("Drag Alpha").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription("Drag Beta").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Drag Alpha").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, second.center.y - first.center.y), delayMillis = 100)
            up()
        }
    }
}
