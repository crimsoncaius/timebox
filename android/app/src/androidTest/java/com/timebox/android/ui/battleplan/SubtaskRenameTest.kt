package com.timebox.android.ui.battleplan

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import com.timebox.android.data.Subtask
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SubtaskRenameTest {
    @get:Rule val compose = createComposeRule()

    private val checkpoint = Subtask(11, 10, "Checkpoint", false, false, 0, Instant.EPOCH, Instant.EPOCH)

    @Test
    fun tappingTheTitleOpensTheEditorWhileTheCheckboxStillToggles() {
        val toggled = mutableListOf<Subtask>()
        val renames = mutableListOf<String>()
        compose.setContent {
            var subtasks by remember { mutableStateOf(listOf(checkpoint)) }
            var rename by remember { mutableStateOf<SubtaskRename?>(null) }
            TimeboxTheme(darkTheme = false) { Column {
                TaskSubtasks(
                    subtasks = subtasks, enabled = true, saving = false, error = null,
                    onToggle = { toggled += it }, onTrash = {}, onAdd = {},
                    rename = rename,
                    onStartRename = { rename = SubtaskRename(it.id) },
                    onRename = { subtask, title ->
                        renames += title
                        subtasks = subtasks.map { if (it.id == subtask.id) it.copy(title = title) else it }
                        rename = null
                    },
                    onDismissRename = { rename = null },
                )
            } }
        }

        compose.onNodeWithContentDescription("Check Checkpoint").performClick()
        assertEquals(listOf(checkpoint), toggled)
        compose.onNodeWithText("Edit subtask").assertDoesNotExist()

        compose.onNodeWithText("Checkpoint").performClick()
        compose.onNodeWithText("Edit subtask").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Subtask name").performTextReplacement("  Draft outline  ")
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Subtask name").performImeAction()

        compose.onNodeWithText("Edit subtask").assertDoesNotExist()
        compose.onNodeWithText("Draft outline").assertIsDisplayed()
        assertEquals(listOf("Draft outline"), renames)
    }

    @Test
    fun closingAChangedEditorAsksBeforeDiscarding() {
        var dismissed by mutableStateOf(false)
        compose.setContent {
            TimeboxTheme(darkTheme = false) { Column {
                TaskSubtasks(
                    subtasks = listOf(checkpoint), enabled = true, saving = false, error = null,
                    onToggle = {}, onTrash = {}, onAdd = {},
                    rename = if (dismissed) null else SubtaskRename(checkpoint.id),
                    onStartRename = {},
                    onDismissRename = { dismissed = true },
                )
            } }
        }

        compose.onNodeWithText("Subtask name").performTextReplacement("Draft outline")
        compose.onNodeWithContentDescription("Close subtask editor").performClick()
        compose.onNodeWithText("Discard this subtask edit?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Draft outline").assertIsDisplayed()

        compose.onNodeWithContentDescription("Close subtask editor").performClick()
        compose.onNodeWithText("Discard").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun aFailedRenameShowsItsErrorAndKeepsTheEditorOpen() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) { Column {
                TaskSubtasks(
                    subtasks = listOf(checkpoint), enabled = true, saving = false, error = null,
                    onToggle = {}, onTrash = {}, onAdd = {},
                    rename = SubtaskRename(checkpoint.id, error = "Server request failed (HTTP 503)"),
                    onStartRename = {},
                )
            } }
        }

        compose.onNodeWithText("Edit subtask").assertIsDisplayed()
        compose.onNodeWithText("Server request failed (HTTP 503)").assertIsDisplayed()
    }
}
