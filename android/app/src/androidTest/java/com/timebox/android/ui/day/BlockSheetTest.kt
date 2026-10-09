package com.timebox.android.ui.day

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextEquals
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.LinkedTask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class BlockSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tasklessPlannedDraftNamesBeforeTaskTypeAndCreatesWithoutChoosingOne() {
        var createdName: String? = null
        compose.setContent {
            var state by remember {
                mutableStateOf(
                    DayUiState(
                        draft = Draft(Lane.Planned, 18 * 60, 19 * 60),
                    )
                )
            }
            TimeboxTheme(darkTheme = false) {
                BlockSheet(
                    state = state,
                    onDismiss = {},
                    onChooseType = {},
                    onTypeQueryChange = {},
                    onCreateType = {},
                    onNameChange = { state = state.copy(nameInput = it) },
                    onNoteChange = {},
                    onCreateDraft = { createdName = state.nameInput },
                    onDelete = {},
                    onConfirmTaskCompletion = {},
                    onReopenTask = {},
                    onOpenLinkedTask = {},
                )
            }
        }

        val nameTop = compose.onNodeWithText("Name").fetchSemanticsNode().boundsInRoot.top
        val taskTypeTop = compose.onNodeWithText("Task type").fetchSemanticsNode().boundsInRoot.top
        assertTrue(nameTop < taskTypeTop)
        compose.onNodeWithTag("block-name-input").performTextInput("Dinner with Alex")
        compose.onNodeWithText("Create block").performClick()
        compose.runOnIdle { assertEquals("Dinner with Alex", createdName) }
    }

    @Test
    fun taskBackedPlannedNameCanBeChangedAndReloadedSeparatelyFromLinkedContext() {
        showTaskBackedNameEditor(Lane.Planned, plannedBlockId = null)

        compose.onNodeWithText("Prepare launch").fetchSemanticsNode()
        val name = compose.onNodeWithTag("block-name-input")
        name.assertTextEquals("Outline session")
        name.performTextReplacement("Review session")
        name.assertTextEquals("Review session")
        compose.onNodeWithText("Keep this note").fetchSemanticsNode()
    }

    private fun showTaskBackedNameEditor(lane: Lane, plannedBlockId: Int?) {
        val date = LocalDate.of(2026, 8, 30)
        val task = LinkedTask(
            id = 42,
            title = "Prepare launch",
            status = TaskStatus.InProgress,
            taskTypeId = 3,
            archivedAt = null,
            deletedAt = null,
        )
        val block = TimeBlock(
            id = if (lane == Lane.Actual) -4 else 4,
            lane = lane,
            taskTypeId = 3,
            taskTypeName = "Deep work",
            taskId = task.id,
            task = task,
            note = "Keep this note",
            plannedBlockId = plannedBlockId,
            actualBlockId = if (lane == Lane.Actual) 4 else null,
            startMinute = 18 * 60,
            endMinute = 19 * 60,
            name = "Outline session",
        )
        compose.setContent {
            var state by remember {
                mutableStateOf(
                    DayUiState(
                        date = date,
                        pages = mapOf(
                            date to DayPageState(
                                day = Day(
                                    date = date,
                                    startHour = 8,
                                    endHour = 20,
                                    showFullDay = false,
                                    blocks = listOf(block),
                                    timezone = "Asia/Singapore",
                                    today = date,
                                    serverNowMinute = 9 * 60,
                                ),
                                loading = false,
                                materialized = true,
                            )
                        ),
                        selectedBlockId = block.id,
                        nameInput = block.name.orEmpty(),
                        noteInput = block.note.orEmpty(),
                    )
                )
            }
            TimeboxTheme(darkTheme = false) {
                BlockSheet(
                    state = state,
                    onDismiss = {},
                    onChooseType = {},
                    onTypeQueryChange = {},
                    onCreateType = {},
                    onNameChange = { state = state.copy(nameInput = it) },
                    onNoteChange = { state = state.copy(noteInput = it) },
                    onCreateDraft = {},
                    onDelete = {},
                    onConfirmTaskCompletion = {},
                    onReopenTask = {},
                    onOpenLinkedTask = {},
                )
            }
        }
    }
}
