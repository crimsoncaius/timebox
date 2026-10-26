package com.timebox.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import com.timebox.android.ui.day.TaskTypePicker
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Rule
import org.junit.Test

class ContextualHelpTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortHelpPreservesNestedEditorDraft() = verify("Short optional rule.")
    @Test fun longHelpPreservesNestedEditorDraft() = verify("A longer optional explanation with several rules. ".repeat(12))

    @Test fun taskTypeSearchHelpPreservesPathWithoutAssigningOrCreating() {
        var mutations = 0
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                Dialog(onDismissRequest = { mutations++ }) {
                    var query by remember { mutableStateOf("") }
                    TaskTypePicker(taskTypes = emptyList(), query = query, onQueryChange = { query = it },
                        selectedTypeId = null, onChoose = { mutations++ }, onCreate = { mutations++ })
                }
            }
        }
        compose.onNodeWithTag("task-type-search").performTextInput("work/deep")
        compose.onNodeWithContentDescription("Help: Task Type Paths").performClick()
        compose.onNodeWithText("Close help").performScrollTo().performClick()
        compose.onNodeWithTag("task-type-search").assertTextContains("work/deep")
        org.junit.Assert.assertEquals(0, mutations)
    }

    private fun verify(help: String) {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                Dialog(onDismissRequest = {}) {
                    Column {
                        var draft by remember { mutableStateOf("") }
                        OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { androidx.compose.material3.Text("Draft") })
                        ContextualHelp("Editing", help)
                    }
                }
            }
        }
        compose.onNodeWithText(help).assertDoesNotExist()
        compose.onNodeWithText("Draft").performTextInput("Unsaved change")
        compose.onNodeWithContentDescription("Help: Editing").performClick()
        compose.onNodeWithText(help).assertExists()
        compose.onNodeWithText("Close help").performScrollTo().performClick()
        compose.onNodeWithText("Unsaved change").assertExists()
        compose.onNodeWithText(help).assertDoesNotExist()
    }
}
