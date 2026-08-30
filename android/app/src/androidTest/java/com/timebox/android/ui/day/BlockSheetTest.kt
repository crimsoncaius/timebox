package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.components.TimeboxBottomNav
import com.timebox.android.ui.components.TimeboxTab
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class BlockSheetTest {
    @get:Rule val compose = createComposeRule()

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
                            onNoteChange = {},
                            onDelete = {},
                            onStartWorkMode = {},
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
}
