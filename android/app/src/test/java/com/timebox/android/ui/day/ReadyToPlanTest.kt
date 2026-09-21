package com.timebox.android.ui.day

import com.timebox.android.ui.battleplan.task
import com.timebox.android.ui.planning.PlanningSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadyToPlanTest {
    @Test
    fun planningRequiresLoadedPlansAndRemainsAvailableAfterNavigating() {
        val date = java.time.LocalDate.parse("2026-08-31")
        val day = com.timebox.android.data.Day(
            date = date, startHour = 8, endHour = 20, showFullDay = false,
            blocks = emptyList(), timezone = "UTC", today = date, serverNowMinute = null,
        )
        val projected = DayUiState(date = date, pages = mapOf(date to DayPageState(day = day, loading = false)))
        assertFalse(projected.planningActionEnabled)
        val loaded = projected.copy(hasLoadedDay = true)
        assertTrue(loaded.planningActionEnabled)
        assertTrue(loaded.copy(date = date.plusDays(7), pages = emptyMap()).planningActionEnabled)
        assertFalse(loaded.copy(saving = true).planningActionEnabled)
        assertFalse(loaded.copy(planning = PlanningSessionState(saving = true)).planningActionEnabled)
        assertTrue(DayUiState(planning = PlanningSessionState(active = true)).planningActionEnabled)
    }

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
