package com.timebox.android.ui.day

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Lane
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkModeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun taskContentShowsContextInteractiveSubtasksAndOnlyExit() {
        var exited = false
        val task = task(subtasks = listOf(subtask(2, checked = true), subtask(3, checked = false)))
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                WorkModeScreen(workMode(current = block(), task = task), onToggleSubtask = {}, onLeave = {}, onExit = { exited = true })
            }
        }

        compose.onNodeWithText("Ship Android").fetchSemanticsNode()
        compose.onNodeWithText("coding").fetchSemanticsNode()
        compose.onNodeWithText("Keep the release small.").fetchSemanticsNode()
        compose.onNodeWithText("Subtasks 1/2").fetchSemanticsNode()
        assertEquals(1, compose.onAllNodesWithText("Exit Work Mode").fetchSemanticsNodes().size)
        compose.onNodeWithText("Exit Work Mode").performClick()
        compose.runOnIdle { assertTrue(exited) }
        check(compose.onAllNodesWithText("complete Task", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        check(compose.onAllNodesWithText("Skip this block", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun tasklessCurrentUsesTaskTypeAndNote() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                WorkModeScreen(workMode(current = block(taskless = true), task = null), onToggleSubtask = {}, onLeave = {}, onExit = {})
            }
        }
        compose.onNodeWithText("Read in the garden").fetchSemanticsNode()
        compose.onNodeWithText("coding").fetchSemanticsNode()
    }

    @Test
    fun upNextAndEmptyStatesStayPresentTense() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                WorkModeScreen(workMode(current = null, next = block().copy(startMinute = 9 * 60 + 27), task = null), onToggleSubtask = {}, onLeave = {}, onExit = {})
            }
        }
        compose.onNodeWithText("UP NEXT").fetchSemanticsNode()
        compose.onNodeWithText("in 10 minutes", substring = true).fetchSemanticsNode()
    }

    @Test
    fun emptyStateStaysPresentTense() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                WorkModeScreen(workMode(current = null, next = null, task = null), onToggleSubtask = {}, onLeave = {}, onExit = {})
            }
        }
        compose.onNodeWithText("No more planned work today").fetchSemanticsNode()
    }

    @Test
    fun completedTaskFreezesSubtasks() {
        val completed = task(subtasks = listOf(subtask(2, checked = false))).copy(status = TaskStatus.Completed)
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                WorkModeScreen(workMode(current = block(), task = completed), onToggleSubtask = {}, onLeave = {}, onExit = {})
            }
        }
        compose.onNodeWithTag("work-mode-subtask-2").assertIsNotEnabled()
    }

    private fun workMode(current: TimeBlock?, next: TimeBlock? = null, task: BattleTask?) = WorkModeUiState(
        entryAt = Instant.parse("2026-08-30T01:17:00Z"),
        lastConfirmedAt = Instant.parse("2026-08-30T01:17:00Z"),
        lastObservedAt = Instant.parse("2026-08-30T01:17:00Z"),
        currentBlock = current,
        nextBlock = next,
        task = task,
        timezone = "Asia/Singapore",
    )

    private fun block(taskless: Boolean = false) = TimeBlock(
        id = 31, lane = Lane.Planned, taskTypeId = 3, taskTypeName = "coding",
        taskId = if (taskless) null else 1, task = null,
        note = if (taskless) "Read in the garden" else "Block note",
        plannedBlockId = null, startMinute = 9 * 60, endMinute = 10 * 60,
    )

    private fun subtask(id: Int, checked: Boolean) = Subtask(
        id = id, parentTaskId = 1, title = "Subtask $id", checked = checked,
        effectivelyResolved = checked, position = id, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun task(subtasks: List<Subtask> = emptyList()) = BattleTask(
        id = 1, parentId = null, parentTitle = null, projectId = null, project = null,
        taskTypeId = 3, taskType = null, recurringTemplateId = null, recurringTemplateTitle = null,
        occurrenceKey = null, recurrenceKind = null, quotaPeriodStart = null, quotaPeriodEnd = null,
        expectedSessions = null, sessionIndex = null, quotaCompleted = null,
        title = "Ship Android", description = "Keep the release small.", readyToPlan = false,
        status = TaskStatus.Open, urgency = PriorityLevel.High, importance = null,
        deadlineDate = null, deadlineAt = null, reminderAt = null, reminderDeliveredAt = null,
        position = 0, archivedAt = null, deletedAt = null, createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH, overdue = false, subtasks = subtasks,
    )
}
