package com.timebox.android.ui.day

import com.timebox.android.data.TaskStatus
import com.timebox.android.ui.battleplan.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadyToPlanTest {
    @Test
    fun readySelectorIncludesSessionTasksButNeverSubtasks() {
        val sessionReady = task(3, ready = true)
        val sessionNotReady = task(4)
        val parent = task(1, ready = false, sessionTasks = listOf(sessionReady, sessionNotReady))
        val standalone = task(2, status = TaskStatus.Blocked, ready = true)

        assertEquals(listOf(3, 2), listOf(parent, standalone).readyToPlanTasks().map { it.id })
    }

    @Test
    fun emptyPlanningQueueCollapsesWhileLoadingAndErrorsStayActionable() {
        assertFalse(DayUiState(readyTasksLoading = false).hasPlanningRailContent(java.time.LocalDate.parse("2026-08-31")))
        assertTrue(DayUiState(readyTasksLoading = true).hasPlanningRailContent(java.time.LocalDate.parse("2026-08-31")))
        assertTrue(DayUiState(readyTasksError = "Offline").hasPlanningRailContent(java.time.LocalDate.parse("2026-08-31")))
        assertTrue(DayUiState(readyTasks = listOf(task(8, ready = true))).hasPlanningRailContent(java.time.LocalDate.parse("2026-08-31")))
    }
}
