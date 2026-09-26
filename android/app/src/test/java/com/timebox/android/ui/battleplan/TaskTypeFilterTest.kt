package com.timebox.android.ui.battleplan

import com.timebox.android.data.BattleTask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TaskTypeFilterTest {
    private val coding = TaskType(1, "coding", 0)
    private val ai = TaskType(2, "coding/ai", 0)
    private val evals = TaskType(3, "coding/ai/evals", 0)
    private val codingTools = TaskType(4, "codingtools", 0)
    private val writing = TaskType(5, "writing", 0)
    private val types = listOf(coding, ai, evals, codingTools, writing)

    @Test
    fun chosenParentCoversItsWholeBranchButNotPrefixSiblings() {
        assertEquals(setOf("1", "2", "3"), taskTypeFilterCoverage(setOf("1"), types))
        assertEquals(setOf("2", "3", "unset"), taskTypeFilterCoverage(setOf("2", "unset"), types))
    }

    @Test
    fun coveringTypeIsTheShallowestChosenAncestor() {
        assertEquals(coding, coveringTaskType(evals, setOf("1", "2"), types))
        assertNull(coveringTaskType(coding, setOf("1"), types))
        assertNull(coveringTaskType(codingTools, setOf("1"), types))
    }

    @Test
    fun choosingParentAbsorbsDescendantsAndTogglingClears() {
        val withChildren = setOf("2", "3", "5")
        assertEquals(setOf("1", "5"), toggleTaskTypeFilter(withChildren, "1", types))
        assertEquals(setOf("5"), toggleTaskTypeFilter(setOf("1", "5"), "1", types))
        assertEquals(setOf("1", "99"), toggleTaskTypeFilter(setOf("1"), "99", types))
    }

    @Test
    fun boardFilterIncludesTasksInTheChosenBranch() {
        val tasks = listOf(task(10, coding), task(11, evals), task(12, codingTools), task(13, writing), task(14, null))
        val state = BattlePlanUiState(loading = false, taskTypes = types, tasks = tasks, taskTypeFilter = setOf("1"))
        assertEquals(listOf(10, 11), state.filteredTasks.map { it.id })
        assertEquals(listOf(10, 11, 12, 13, 14), state.copy(taskTypeFilter = emptySet()).filteredTasks.map { it.id })
    }

    private fun task(id: Int, type: TaskType?): BattleTask {
        val now = Instant.EPOCH
        return BattleTask(
            id = id, parentId = null, parentTitle = null, projectId = null, project = null,
            taskTypeId = type?.id, taskType = type, recurringTemplateId = null, recurringTemplateTitle = null,
            occurrenceKey = null, recurrenceKind = null, quotaPeriodStart = null, quotaPeriodEnd = null,
            expectedSessions = null, sessionIndex = null, quotaCompleted = null,
            title = "Task $id", description = "", readyToPlan = false, status = TaskStatus.Open,
            urgency = null, importance = null, deadlineDate = null, deadlineAt = null,
            reminderAt = null, reminderDeliveredAt = null, position = id,
            archivedAt = null, deletedAt = null, createdAt = now, updatedAt = now, overdue = false,
            subtasks = emptyList(),
        )
    }
}
