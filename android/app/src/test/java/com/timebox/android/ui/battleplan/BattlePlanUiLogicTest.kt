package com.timebox.android.ui.battleplan

import androidx.compose.ui.geometry.Rect
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
    fun sameStatusDropUsesTheListAfterRemovingTheSourceTask() {
        val moving = task(2, status = TaskStatus.Open, position = 1)
        val placements = dropTaskPlacements(
            tasks = listOf(
                task(1, status = TaskStatus.Open, position = 0),
                moving,
                task(3, status = TaskStatus.Open, position = 2),
            ),
            visible = listOf(task(1, position = 0), moving, task(3, position = 2)),
            moving = moving,
            target = TaskStatus.Open,
            targetIndex = 2,
        )

        assertEquals(listOf(1, 3, 2), placements.sortedBy { it.position }.map { it.taskId })
    }

    @Test
    fun dropIntoAnEmptyStatusUsesPositionZero() {
        val moving = task(2, status = TaskStatus.Open, position = 1)
        val placements = dropTaskPlacements(
            tasks = listOf(task(1, position = 0), moving),
            visible = listOf(task(1, position = 0), moving),
            moving = moving,
            target = TaskStatus.Completed,
            targetIndex = 0,
        )

        assertEquals(
            listOf(Triple(1, TaskStatus.Open, 0), Triple(2, TaskStatus.Completed, 0)),
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

    @Test
    fun dragAtAnAvailableScreenEdgeRequestsTheAdjacentPage() {
        assertEquals(-1, edgePageDirection(20f, 400f, 52f, currentPage = 1, pageCount = 3))
        assertEquals(1, edgePageDirection(380f, 400f, 52f, currentPage = 1, pageCount = 3))
        assertEquals(0, edgePageDirection(200f, 400f, 52f, currentPage = 1, pageCount = 3))
    }

    @Test
    fun dragAtTheOuterBoardEdgesDoesNotRequestANonexistentPage() {
        assertEquals(0, edgePageDirection(20f, 400f, 52f, currentPage = 0, pageCount = 3))
        assertEquals(0, edgePageDirection(380f, 400f, 52f, currentPage = 2, pageCount = 3))
    }

    @Test
    fun pointerPositionResolvesInsertionAroundVariableHeightCards() {
        val bounds = listOf(
            IndexedValue(0, Rect(0f, 100f, 300f, 140f)),
            IndexedValue(1, Rect(0f, 150f, 300f, 250f)),
            IndexedValue(2, Rect(0f, 260f, 300f, 320f)),
        )

        assertEquals(0, insertionIndexForPointer(90f, 3, bounds))
        assertEquals(1, insertionIndexForPointer(121f, 3, bounds))
        assertEquals(2, insertionIndexForPointer(201f, 3, bounds))
        assertEquals(3, insertionIndexForPointer(321f, 3, bounds))
    }

    @Test
    fun insertionResolutionSupportsEmptyAndPartiallyMeasuredLanes() {
        assertEquals(0, insertionIndexForPointer(100f, 0, emptyList()))
        assertEquals(4, insertionIndexForPointer(100f, 8, listOf(IndexedValue(4, Rect(0f, 120f, 300f, 180f)))))
        assertEquals(5, insertionIndexForPointer(200f, 8, listOf(IndexedValue(4, Rect(0f, 120f, 300f, 180f)))))
    }

    @Test
    fun insertionHysteresisKeepsTheCurrentSlotUntilTheAdjacentCenterIsClearlyCrossed() {
        val bounds = listOf(
            IndexedValue(0, Rect(0f, 100f, 300f, 140f)),
            IndexedValue(1, Rect(0f, 150f, 300f, 210f)),
        )

        assertEquals(1, insertionIndexWithHysteresis(181f, 2, bounds, currentIndex = 1, hysteresis = 8f))
        assertEquals(2, insertionIndexWithHysteresis(189f, 2, bounds, currentIndex = 1, hysteresis = 8f))
        assertEquals(1, insertionIndexWithHysteresis(113f, 2, bounds, currentIndex = 1, hysteresis = 8f))
        assertEquals(0, insertionIndexWithHysteresis(111f, 2, bounds, currentIndex = 1, hysteresis = 8f))
    }

    @Test
    fun verticalAutoScrollUsesProportionalTopAndBottomEdgeSteps() {
        val lane = Rect(0f, 100f, 400f, 700f)

        assertEquals(-18f, verticalAutoScrollStep(100f, lane, edgeSize = 60f, maximumStep = 18f))
        assertEquals(9f, verticalAutoScrollStep(670f, lane, edgeSize = 60f, maximumStep = 18f))
        assertEquals(0f, verticalAutoScrollStep(400f, lane, edgeSize = 60f, maximumStep = 18f))
    }

    @Test
    fun onlyTheOriginalSameStatusSlotIsAnUnchangedDrop() {
        assertEquals(true, isUnchangedDrop(TaskStatus.Open, TaskStatus.Open, 1, 1))
        assertEquals(false, isUnchangedDrop(TaskStatus.Open, TaskStatus.Open, 1, 2))
        assertEquals(false, isUnchangedDrop(TaskStatus.Open, TaskStatus.Completed, 1, 1))
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
