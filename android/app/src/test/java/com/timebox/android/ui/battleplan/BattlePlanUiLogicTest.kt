package com.timebox.android.ui.battleplan

import com.timebox.android.data.BattleTask
import com.timebox.android.data.BattlePlanSort
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.Project
import com.timebox.android.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BattlePlanUiLogicTest {
    @Test
    fun scopesSeparateAdminAndProjects() {
        val admin = task(1, projectId = null)
        val project = task(2, projectId = 7)

        assertEquals(listOf(1, 2), listOf(admin, project).inScope(BattlePlanScope.All).map { it.id })
        assertEquals(listOf(1), listOf(admin, project).inScope(BattlePlanScope.Admin).map { it.id })
        assertEquals(
            listOf(2),
            listOf(admin, project).inScope(BattlePlanScope(BattlePlanScopeKind.Project, 7, "Project")).map { it.id },
        )
    }

    @Test
    fun statusMoveReindexesSourceAndAppendsToTarget() {
        val moving = task(2, status = TaskStatus.Open, position = 1)
        val placements = statusMovePlacements(
            listOf(
                task(1, status = TaskStatus.Open, position = 0),
                moving,
                task(3, status = TaskStatus.InProgress, position = 0),
            ),
            moving,
            TaskStatus.InProgress,
        )

        assertEquals(
            listOf(
                Triple(1, TaskStatus.Open, 0),
                Triple(3, TaskStatus.InProgress, 0),
                Triple(2, TaskStatus.InProgress, 1),
            ),
            placements.map { Triple(it.taskId, it.status, it.position) },
        )
    }

    @Test
    fun dropInsertsAtTheVisibleTargetPositionAndPreservesHiddenTasks() {
        val moving = task(2, status = TaskStatus.Open, position = 1)
        val openOne = task(1, status = TaskStatus.Open, position = 0)
        val hidden = task(3, status = TaskStatus.InProgress, position = 0)
        val visibleTarget = task(4, status = TaskStatus.InProgress, position = 1)

        val placements = dropTaskPlacements(
            tasks = listOf(openOne, moving, hidden, visibleTarget),
            visible = listOf(openOne, moving, visibleTarget),
            moving = moving,
            target = TaskStatus.InProgress,
            targetIndex = 0,
        )

        assertEquals(
            listOf(
                Triple(1, TaskStatus.Open, 0),
                Triple(3, TaskStatus.InProgress, 0),
                Triple(2, TaskStatus.InProgress, 1),
                Triple(4, TaskStatus.InProgress, 2),
            ),
            placements.map { Triple(it.taskId, it.status, it.position) },
        )
    }

    @Test
    fun sortingMatchesWebPriorityAndDeadlineRules() {
        val low = task(1, position = 0, urgency = PriorityLevel.Low, deadlineDate = LocalDate.parse("2026-09-02"))
        val unset = task(2, position = 1)
        val high = task(3, position = 2, urgency = PriorityLevel.High, deadlineDate = LocalDate.parse("2026-09-01"))

        assertEquals(listOf(3, 1, 2), listOf(low, unset, high).sortedWith(taskComparator(BattlePlanSort.Urgency)).map { it.id })
        assertEquals(listOf(3, 1, 2), listOf(low, unset, high).sortedWith(taskComparator(BattlePlanSort.Deadline)).map { it.id })
    }

    @Test
    fun nullableFiltersUseExplicitUnsetToken() {
        assertEquals(true, setOf("unset").matches(null))
        assertEquals(false, setOf("high").matches(null))
        assertEquals(true, emptySet<String>().matches("low"))
    }

    @Test
    fun trashRetentionUsesServerTimeAndNeverDropsBelowZero() {
        val deleted = Instant.parse("2026-08-01T00:00:00Z")
        assertEquals(30, trashRetentionDays(deleted.plusSeconds(23 * 3600), deleted))
        assertEquals(29, trashRetentionDays(deleted.plusSeconds(24 * 3600), deleted))
        assertEquals(0, trashRetentionDays(deleted.plusSeconds(35 * 24 * 3600), deleted))
    }
}

internal fun task(
    id: Int,
    projectId: Int? = null,
    status: TaskStatus = TaskStatus.Open,
    position: Int = 0,
    ready: Boolean = false,
    subtasks: List<BattleTask> = emptyList(),
    urgency: PriorityLevel? = null,
    deadlineDate: LocalDate? = null,
) = BattleTask(
    id = id,
    parentId = null,
    parentTitle = null,
    projectId = projectId,
    project = projectId?.let { Project(it, "Project $it", "", null, null, Instant.EPOCH, Instant.EPOCH) },
    taskTypeId = null,
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
    title = "Task $id",
    description = "",
    readyToPlan = ready,
    status = status,
    urgency = urgency,
    importance = null,
    deadlineDate = deadlineDate,
    deadlineAt = null,
    reminderAt = null,
    reminderDeliveredAt = null,
    position = position,
    archivedAt = null,
    deletedAt = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    overdue = false,
    subtasks = subtasks,
)
