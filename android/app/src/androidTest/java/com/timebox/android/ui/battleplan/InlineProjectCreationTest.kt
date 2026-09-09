package com.timebox.android.ui.battleplan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.data.Project
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class InlineProjectCreationTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(BattlePlanUiState(loading = false))
    private var submit: () -> Unit = {}

    @Test fun inlineDraftSavingAndErrorsKeepTheMenuAndLibraryInPlace() {
        showBoard()
        compose.onNodeWithText("All Tasks").performClick()
        val menu = compose.onNodeWithTag("battle-plan-scope-menu")
        val library = compose.onNodeWithTag("battle-plan-scope-menu-library")
        val height = menu.fetchSemanticsNode().boundsInRoot.height
        val libraryTop = library.fetchSemanticsNode().boundsInRoot.top
        // Change draft state without opening the IME: keyboard resizing is a
        // separate, allowed constraint; the editor itself must never grow.
        listOf(
            InlineProjectCreation(active = true, name = "Launch"),
            InlineProjectCreation(active = true, name = "Launch", saving = true),
            InlineProjectCreation(active = true, name = "Launch", error = "Project already exists."),
            InlineProjectCreation(active = true, name = "Launch", error = "Unable to save the project. Check your connection and try again."),
            InlineProjectCreation(),
        ).forEach { draft ->
            compose.runOnIdle { state = state.copy(projectCreation = draft) }
            assertEquals("Menu height changed for $draft", height, menu.fetchSemanticsNode().boundsInRoot.height, 0.5f)
            assertEquals("Library moved for $draft", libraryTop, library.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        }
    }

    @Test fun newProjectReplacesActionWithFocusedInputAndCancelRestoresAction() {
        showBoard()
        compose.onNodeWithText("All Tasks").performClick()
        compose.onNodeWithText("New project").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Project name").assertIsFocused().performTextInput("Launch")
        compose.onNodeWithText("New project").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cancel project creation").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Project name").assertDoesNotExist()
        compose.onNodeWithText("New project").assertExists()
    }

    @Test fun keyboardDoneClosesTheMenuEvenWhenCreationCompletesImmediately() {
        val project = Project(42, "Launch", Instant.EPOCH, Instant.EPOCH)
        submit = {
            state = state.copy(projects = listOf(project), selectedScope = BattlePlanScope.project(project),
                projectCreation = InlineProjectCreation(), lastCreatedProjectId = project.id)
        }
        showBoard()
        compose.onNodeWithText("All Tasks").performClick()
        compose.onNodeWithText("New project").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Project name").performTextInput("Launch")
        compose.onNodeWithContentDescription("Project name").performImeAction()
        compose.onNodeWithTag("battle-plan-scope-menu").assertDoesNotExist()
        compose.onNodeWithText("Launch").assertExists()
    }

    @Test fun selectingAnotherScopePreservesTheDraftWithoutRefocusingOnReopen() {
        showBoard()
        compose.onNodeWithText("All Tasks").performClick()
        compose.onNodeWithText("New project").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Project name").performTextInput("Launch")
        compose.onNodeWithText("Admin").performScrollTo().performClick()
        compose.onNodeWithTag("battle-plan-scope-menu").assertDoesNotExist()
        compose.onNodeWithText("Admin").performClick()
        compose.onNodeWithContentDescription("Project name").assertIsNotFocused()
        compose.onNodeWithText("Launch").assertExists()
    }

    @Test fun pendingCreationBlocksBackAndDisablesEditingAndCancel() {
        state = state.copy(projectCreation = InlineProjectCreation(active = true, name = "Launch", saving = true))
        showBoard()
        compose.onNodeWithText("All Tasks").performClick()
        compose.onNodeWithContentDescription("Project name").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Cancel project creation").assertIsNotEnabled()
        compose.onNodeWithText("Creating project…").assertExists()
        Espresso.pressBackUnconditionally()
        compose.onNodeWithTag("battle-plan-scope-menu").assertExists()
    }

    @Test fun requestFailureStaysInlineWithTheNameForRetry() {
        submit = { state = state.copy(projectCreation = state.projectCreation.copy(error = "Project already exists.")) }
        showBoard()
        compose.onNodeWithText("All Tasks").performClick()
        compose.onNodeWithText("New project").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Create project").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Project name").performTextInput("Launch")
        compose.onNodeWithContentDescription("Create project").performScrollTo().performClick()
        compose.onNodeWithText("Project already exists.").assertExists()
        compose.onNodeWithText("Launch").assertExists()
        compose.onNodeWithContentDescription("Create project").assertIsEnabled()
    }

    private fun showBoard() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                BattlePlanScreen(
                    state = state,
                    onRetry = {}, onSelectScope = { state = state.copy(selectedScope = it) }, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                    onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                    onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {},
                    onNewProject = { state = state.copy(projectCreation = InlineProjectCreation(active = true)) },
                    onNewProjectNameChange = { state = state.copy(projectCreation = state.projectCreation.copy(name = it)) },
                    onCancelProjectCreation = { state = state.copy(projectCreation = InlineProjectCreation()) },
                    onCreateProject = { submit() },
                    onOpenRecurring = {}, onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {}, onRestoreTrashed = {},
                    onUndoTrash = {}, onDismissUndo = {}, onRequestPermanentDelete = {},
                    onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }
    }
}
