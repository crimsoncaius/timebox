package com.timebox.android.ui.battleplan

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxShapes
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

    @Test fun boundarySelectionCornersMatchAnUnclippedMiddleRow() {
        var selected by mutableStateOf(2)
        var rows by mutableStateOf(projects)
        var outline = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                outline = TimeboxTheme.colors.project
                // Include the rounded ancestor used by the Projects menu section.
                Box(Modifier.width(280.dp).clip(TimeboxShapes.card)) {
                    ProjectNavigationList(rows, selected, false, {}, {})
                }
            }
        }
        fun cornerPixels(index: Int): List<androidx.compose.ui.graphics.Color> {
            val row = compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
                useUnmergedTree = true,
            )[index]
            row.performScrollTo()
            val pixels = row.captureToImage().toPixelMap()
            val corner = pixels.height / 6
            return buildList {
                for (y in 0 until corner) for (x in 0 until corner) {
                    add(pixels[x, y])
                    add(pixels[pixels.width - 1 - x, y])
                    add(pixels[x, pixels.height - 1 - y])
                    add(pixels[pixels.width - 1 - x, pixels.height - 1 - y])
                }
            }
        }
        val reference = cornerPixels(1)
        for ((count, index) in listOf(3 to 0, 3 to 2, 1 to 0, 8 to 0, 8 to 7)) {
            compose.runOnIdle {
                rows = List(count) { projects[it % projects.size].copy(id = it + 1) }
                selected = index + 1
            }
            compose.waitForIdle()
            val actual = cornerPixels(index)
            val differing = reference.zip(actual).count { (a, b) ->
                // Compare the outline itself; pixels outside the row can legitimately
                // show the ancestor's rounded background rather than the list fill.
                val isOutline = kotlin.math.abs(a.red - outline.red) +
                    kotlin.math.abs(a.green - outline.green) + kotlin.math.abs(a.blue - outline.blue) < 0.25f
                isOutline && kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) +
                    kotlin.math.abs(a.blue - b.blue) > 0.15f
            }
            assertTrue("Row $index of $count has $differing clipped corner pixels", differing < reference.size / 100)
        }
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

    @Test fun projectDragTargetDoesNotReverseAtTheSameBoundaryItJustCrossed() {
        val rowHeight = 56f
        var target = 0

        target = projectDragTarget(0, target, rowHeight * 0.66f, rowHeight, lastIndex = 2)
        assertEquals(1, target)

        // Small finger movement back over the ordinary 50% midpoint must not make
        // the neighbouring rows reverse their active placement animation.
        target = projectDragTarget(0, target, rowHeight * 0.49f, rowHeight, lastIndex = 2)
        assertEquals(1, target)

        target = projectDragTarget(0, target, rowHeight * 0.34f, rowHeight, lastIndex = 2)
        assertEquals(0, target)
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
