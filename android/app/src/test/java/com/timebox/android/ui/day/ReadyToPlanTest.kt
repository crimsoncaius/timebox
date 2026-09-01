package com.timebox.android.ui.day

import com.timebox.android.ui.battleplan.task
import com.timebox.android.ui.planning.PlanningSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadyToPlanTest {
    @Test
    fun emptyPlanningQueueCollapsesWhileLoadingAndErrorsStayActionable() {
        val date = java.time.LocalDate.parse("2026-08-31")
        assertFalse(DayUiState().hasPlanningRailContent(date))
        assertTrue(DayUiState(planning = PlanningSessionState(queueLoading = true)).hasPlanningRailContent(date))
        assertTrue(DayUiState(planning = PlanningSessionState(queueError = "Offline")).hasPlanningRailContent(date))
        assertTrue(
            DayUiState(planning = PlanningSessionState(readyTasks = listOf(task(8, ready = true))))
                .hasPlanningRailContent(date),
        )
    }
}
