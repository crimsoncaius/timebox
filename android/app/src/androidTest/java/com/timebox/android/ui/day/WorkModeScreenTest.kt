package com.timebox.android.ui.day

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.timebox.android.data.ActualBlock
import com.timebox.android.data.BattleTask
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskStatus
import com.timebox.android.ui.components.TimeboxBottomNav
import com.timebox.android.ui.components.TimeboxTab
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkModeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun taskCapabilitiesExposeSubtasksAndBothFinishOutcomes() {
        var finished = false
        var completed = false
        val task = task(subtasks = listOf(subtask(2, checked = true), subtask(3, checked = false)))
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                WorkModeScreen(
                    state = workMode(task),
                    onStartChange = {}, onEndChange = {}, onSaveActual = {}, onToggleSubtask = {},
                    onFinish = { finished = true }, onFinishAndComplete = { completed = true }, onClose = {},
                )
            }
        }

        compose.onNodeWithTag("work-mode").fetchSemanticsNode()
        compose.onNodeWithText("Subtasks 1/2").fetchSemanticsNode()
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish session + complete Task").performClick()
        compose.runOnIdle { assertTrue(finished && completed) }
    }

    @Test
    fun nonTaskCapabilitiesHideTaskCompletionAndSubtasks() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                WorkModeScreen(
                    state = workMode(task = null),
                    onStartChange = {}, onEndChange = {}, onSaveActual = {}, onToggleSubtask = {},
                    onFinish = {}, onFinishAndComplete = {}, onClose = {},
                )
            }
        }

        compose.onNodeWithText("Finish session").fetchSemanticsNode()
        check(compose.onAllNodesWithText("Finish session + complete Task").fetchSemanticsNodes().isEmpty())
        check(compose.onAllNodesWithText("Subtasks", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun fullCanvasWorkModeCoversAndRemovesBottomNavigationSemantics() {
        val state = workMode(task())
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                Box {
                    Column(Modifier.clearAndSetSemantics { }) {
                        TimeboxBottomNav(TimeboxTab.Day) { }
                    }
                    WorkModeScreen(
                        state = state,
                        onStartChange = {}, onEndChange = {}, onSaveActual = {}, onToggleSubtask = {},
                        onFinish = {}, onFinishAndComplete = {}, onClose = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("work-mode").fetchSemanticsNode()
        check(compose.onAllNodesWithContentDescription("Day").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun occurrenceUsesOrdinaryTaskCapabilitiesWithoutSeriesActions() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                WorkModeScreen(
                    state = workMode(task().copy(recurringTemplateId = 9, occurrenceKey = "2026-08-30")),
                    onStartChange = {}, onEndChange = {}, onSaveActual = {}, onToggleSubtask = {},
                    onFinish = {}, onFinishAndComplete = {}, onClose = {},
                )
            }
        }
        compose.onNodeWithText("Finish session + complete Task").fetchSemanticsNode()
        check(compose.onAllNodesWithText("series", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun completedTaskFreezesSubtasksAndHidesCompletionAction() {
        val completed = task(subtasks = listOf(subtask(2, checked = false))).copy(status = TaskStatus.Completed)
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                WorkModeScreen(
                    state = workMode(completed),
                    onStartChange = {}, onEndChange = {}, onSaveActual = {}, onToggleSubtask = {},
                    onFinish = {}, onFinishAndComplete = {}, onClose = {},
                )
            }
        }

        compose.onNodeWithTag("work-mode-subtask-2").assertIsNotEnabled()
        check(compose.onAllNodesWithText("complete Task", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    }

    private fun workMode(task: BattleTask?) = WorkModeUiState(
        actualBlock = ActualBlock(
            id = 44,
            taskTypeId = 3,
            taskTypeName = "coding",
            taskId = task?.id,
            task = null,
            note = null,
            plannedBlockId = 8,
            startAt = Instant.parse("2026-08-30T01:17:00Z"),
            endAt = null,
        ),
        task = task,
        timezone = "Asia/Singapore",
        startInput = "2026-08-30 09:17",
        endInput = "",
    )

    private fun subtask(id: Int, checked: Boolean) = Subtask(
        id = id,
        parentTaskId = 1,
        title = "Subtask $id",
        checked = checked,
        effectivelyResolved = checked,
        position = id,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun task(subtasks: List<Subtask> = emptyList()) = BattleTask(
        id = 1,
        parentId = null,
        parentTitle = null,
        projectId = null,
        project = null,
        taskTypeId = 3,
        taskType = null,
        recurringTemplateId = null,
        recurringTemplateTitle = null,
        occurrenceKey = null,
        recurrenceKind = null,
        quotaPeriodStart = null,
        quotaPeriodEnd = null,
        expectedSessions = null,
        sessionIndex = null,
        quotaCompleted = null,
        title = "Ship Android",
        description = "",
        readyToPlan = false,
        status = TaskStatus.Open,
        urgency = PriorityLevel.High,
        importance = null,
        deadlineDate = null,
        deadlineAt = null,
        reminderAt = null,
        reminderDeliveredAt = null,
        position = 0,
        archivedAt = null,
        deletedAt = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        overdue = false,
        subtasks = subtasks,
    )
}
