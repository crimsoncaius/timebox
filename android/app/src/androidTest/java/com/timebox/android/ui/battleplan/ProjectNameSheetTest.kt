package com.timebox.android.ui.battleplan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.data.Project
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class ProjectNameSheetTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(BattlePlanUiState(loading = false))
    private var retained: ProjectNameDraft? = null
    private var submit: () -> Unit = {}

    private fun openNew() {
        compose.onNodeWithText("All Tasks").performClick()
        compose.onNodeWithText("New project").performScrollTo().performClick()
        compose.waitForIdle()
        waitForKeyboard()
    }

    private fun waitForKeyboard() {
        compose.waitUntil(10_000) {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys input_method")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { "mInputShown=true" in it.readText() }
        }
    }

    @Test fun newProjectClosesMenuAndOpensFocusedSheetWithReachableActions() {
        showBoard()
        openNew()
        compose.onNodeWithTag("battle-plan-scope-menu").assertDoesNotExist()
        compose.onNodeWithContentDescription("Project name").assertIsFocused().performTextInput("Launch")
        compose.onNodeWithText("Create project").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("project-name-sheet").assertDoesNotExist()
        openNew()
        compose.onNodeWithContentDescription("Project name").assertTextContains("")
        compose.onNodeWithText("Create project").assertIsNotEnabled()
    }

    @Test fun keyboardDoneClosesSheetWhenCreationCompletesImmediately() {
        val project = Project(42, "Launch", Instant.EPOCH, Instant.EPOCH)
        submit = {
            state = state.copy(projects = listOf(project), selectedScope = BattlePlanScope.project(project), projectEditor = null)
        }
        showBoard()
        openNew()
        compose.onNodeWithContentDescription("Project name").performTextInput("Launch")
        compose.onNodeWithContentDescription("Project name").performImeAction()
        compose.onNodeWithTag("project-name-sheet").assertDoesNotExist()
        compose.onNodeWithText("Launch").assertExists()
    }

    @Test fun backHidesKeyboardThenDismissesAndReopeningRestoresFocusedDraft() {
        showBoard()
        openNew()
        compose.onNodeWithContentDescription("Project name").performTextInput("Launch")
        waitForKeyboard()
        Espresso.pressBackUnconditionally()
        compose.onNodeWithTag("project-name-sheet").assertExists()
        Espresso.pressBackUnconditionally()
        compose.onNodeWithTag("project-name-sheet").assertDoesNotExist()
        openNew()
        compose.onNodeWithContentDescription("Project name").assertIsFocused().assertTextContains("Launch")
    }

    @Test fun pendingSaveBlocksBackAndDisablesEditingCancelAndSubmission() {
        showBoard()
        openNew()
        compose.onNodeWithContentDescription("Project name").performTextInput("Launch")
        compose.runOnIdle { state = state.copy(projectEditor = state.projectEditor!!.copy(saving = true)) }
        compose.onNodeWithContentDescription("Project name").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        Espresso.pressBackUnconditionally()
        Espresso.pressBackUnconditionally()
        compose.onNodeWithTag("project-name-sheet").assertExists()
    }

    @Test fun requestFailureRetainsNameAndDisplaysFullErrorForRetry() {
        submit = { state = state.copy(projectEditor = state.projectEditor!!.copy(error = "Project already exists.")) }
        showBoard()
        openNew()
        compose.onNodeWithText("Create project").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Project name").performTextInput("Launch")
        compose.onNodeWithText("Create project").performClick()
        compose.onNodeWithText("Project already exists.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Project name").assertTextContains("Launch")
        compose.onNodeWithText("Create project").assertIsEnabled()
    }

    @Test fun editingUsesSameSheetWithoutDeleteAndKeepsCurrentScopeAfterSaving() {
        state = state.copy(projectEditor = ProjectNameDraft(projectId = 1, name = "Alpha"))
        submit = { state = state.copy(projectEditor = null) }
        showBoard()
        compose.onNodeWithText("Edit project").assertExists()
        compose.onNodeWithContentDescription("Project name").assertIsFocused().performTextReplacement("Renamed")
        compose.onNodeWithText("Delete project").assertDoesNotExist()
        compose.onNodeWithText("Save changes").assertIsDisplayed().performClick()
        compose.onNodeWithTag("project-name-sheet").assertDoesNotExist()
        compose.onNodeWithText("All Tasks").assertExists()
    }

    @Test fun outsideDismissPreservesDraft() {
        showBoard()
        openNew()
        compose.onNodeWithContentDescription("Project name").performTextInput("Launch")
        compose.onNodeWithContentDescription("Close sheet").performClick()
        compose.onNodeWithTag("project-name-sheet").assertDoesNotExist()
        openNew()
        compose.onNodeWithContentDescription("Project name").assertTextContains("Launch")
    }

    @Test fun swipeDismissPreservesDraft() {
        showBoard()
        openNew()
        compose.onNodeWithContentDescription("Project name").performTextInput("Launch")
        compose.onNodeWithContentDescription("Drag handle").performTouchInput {
            swipe(center, center + Offset(0f, 600f), durationMillis = 250)
        }
        compose.onNodeWithTag("project-name-sheet").assertDoesNotExist()
        openNew()
        compose.onNodeWithContentDescription("Project name").assertTextContains("Launch")
    }

    @Test fun projectOverflowEditClosesMenuAndOpensExistingName() {
        state = state.copy(projects = listOf(Project(1, "Alpha", Instant.EPOCH, Instant.EPOCH)))
        showBoard()
        compose.onNodeWithText("All Tasks").performClick()
        compose.onNodeWithContentDescription("More actions for Alpha").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithTag("battle-plan-scope-menu").assertDoesNotExist()
        compose.onNodeWithText("Edit project").assertExists()
        compose.onNodeWithContentDescription("Project name").assertIsFocused().assertTextContains("Alpha")
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
                    onNewProject = { state = state.copy(projectEditor = retained ?: ProjectNameDraft()) },
                    onProjectNameChange = { state = state.copy(projectEditor = state.projectEditor!!.copy(name = it, error = null)) },
                    onCancelProjectEditor = { retained = null; state = state.copy(projectEditor = null) },
                    onDismissProjectEditor = { retained = state.projectEditor; state = state.copy(projectEditor = null) },
                    onEditProject = { state = state.copy(projectEditor = ProjectNameDraft(projectId = it.id, name = it.name)) },
                    onSaveProject = { submit() },
                    onOpenRecurring = {}, onPrepareDeleteProject = {}, onDismissDeleteProject = {},
                    onConfirmDeleteProject = {}, onRestoreArchived = {}, onRestoreTrashed = {},
                    onUndoTrash = {}, onDismissUndo = {}, onRequestPermanentDelete = {},
                    onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }
    }
}
