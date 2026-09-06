package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.LinkedTask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.components.TimeboxBottomNav
import com.timebox.android.ui.components.TimeboxTab
import com.timebox.android.ui.theme.DarkTimeboxColors
import com.timebox.android.ui.theme.LightTimeboxColors
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class BlockSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun darkBlockSheetUsesOpaqueWarmSurfaceWithoutChangingLightTreatment() {
        val darkSurface = blockSheetContainerColor(DarkTimeboxColors)

        assertEquals(DarkTimeboxColors.low, darkSurface)
        assertEquals(1f, darkSurface.alpha)
        assertEquals(LightTimeboxColors.sheet, blockSheetContainerColor(LightTimeboxColors))
    }

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
    fun standaloneActualDraftNamesBeforeTaskTypeAndCreatesWithoutChoosingOne() {
        var createdName: String? = null
        compose.setContent {
            var state by remember {
                mutableStateOf(DayUiState(draft = Draft(Lane.Actual, 18 * 60, 19 * 60)))
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
        compose.onNodeWithTag("block-name-input").performTextInput("Walk home")
        compose.onNodeWithText("Create block").performClick()
        compose.runOnIdle { assertEquals("Walk home", createdName) }
    }

    @Test
    fun standaloneActualNameCanBeChangedClearedAndReloadedSeparatelyFromNote() {
        val date = LocalDate.of(2026, 8, 30)
        val block = TimeBlock(
            id = -4,
            lane = Lane.Actual,
            taskTypeId = 3,
            taskTypeName = "unspecified",
            taskId = null,
            task = null,
            note = "Keep this note",
            plannedBlockId = null,
            actualBlockId = 4,
            startMinute = 18 * 60,
            endMinute = 19 * 60,
            name = "Evening walk",
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

        val name = compose.onNodeWithTag("block-name-input")
        name.assertTextEquals("Evening walk")
        compose.onNodeWithText("Keep this note").fetchSemanticsNode()
        name.performTextReplacement("Walk home")
        name.assertTextEquals("Walk home")
        name.performTextReplacement("")
        name.assertTextContains("Optional")
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

    @Test
    fun taskBackedActualNameCanBeChangedEvenWhenItCorrespondsToAPlannedBlock() {
        showTaskBackedNameEditor(Lane.Actual, plannedBlockId = 8)

        compose.onNodeWithText("Prepare launch").fetchSemanticsNode()
        val name = compose.onNodeWithTag("block-name-input")
        name.assertTextEquals("Outline session")
        name.performTextReplacement("")
        name.assertTextContains("Optional")
        compose.onNodeWithText("Keep this note").fetchSemanticsNode()
    }

    @Test
    fun deleteActionStaysAboveBottomNavigation() {
        val date = LocalDate.of(2026, 8, 30)
        val taskType = TaskType(id = 1, name = "work work", usageCount = 1)
        val block = TimeBlock(
            id = 1,
            lane = Lane.Planned,
            taskTypeId = taskType.id,
            taskTypeName = taskType.name,
            taskId = null,
            task = null,
            note = null,
            plannedBlockId = null,
            startMinute = 11 * 60,
            endMinute = 11 * 60 + 30,
        )
        val state = DayUiState(
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
                ),
            ),
            taskTypes = listOf(taskType),
            selectedBlockId = block.id,
            typeQuery = taskType.name,
        )

        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        BlockSheet(
                            state = state,
                            onDismiss = {},
                            onChooseType = {},
                            onTypeQueryChange = {},
                            onCreateType = {},
                            onNameChange = {},
                            onNoteChange = {},
                            onCreateDraft = {},
                            onDelete = {},
                            onConfirmTaskCompletion = {},
                            onReopenTask = {},
                            onOpenLinkedTask = {},
                        )
                    }
                    TimeboxBottomNav(selected = TimeboxTab.Day, onSelect = {})
                }
            }
        }

        compose.waitForIdle()
        val deleteBottom = compose.onNodeWithContentDescription("Delete block")
            .fetchSemanticsNode().boundsInRoot.bottom
        val bottomNavTop = compose.onNodeWithContentDescription("Day")
            .fetchSemanticsNode().boundsInRoot.top
        val clearance = with(compose.density) { 8.dp.toPx() }

        assertTrue(
            "Delete action must clear bottom navigation by 8 dp; " +
                "delete bottom=$deleteBottom, navigation top=$bottomNavTop",
            deleteBottom + clearance <= bottomNavTop,
        )
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
