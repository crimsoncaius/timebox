package com.timebox.android.ui.day

import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import com.timebox.android.ui.AppRoutes
import com.timebox.android.ui.planning.*
import com.timebox.android.ui.readiness.*
import com.timebox.android.ui.taskcompletion.*
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportingTodayNavigationTest {
    @Test fun trackTaskUsesReportingDateAcrossMidnight() {
        val now = Instant.parse("2026-11-06T17:00:00Z")
        assertEquals(LocalDate.parse("2026-11-07"), now.atZone(ZoneId.of("Asia/Singapore")).toLocalDate())
        assertEquals("day/2026-11-06", AppRoutes.today(now, "UTC"))
        assertEquals("day/2026-11-07", AppRoutes.today(now, "Asia/Singapore"))
        assertEquals("timebox://day/today", AppRoutes.TodayDeepLink)
    }

    @Test fun reminderTodayRequestReplacesPreviouslySelectedDayWithServerToday() = runTest {
        val meta = DayMetaDto("UTC", "2026-11-06", "2026-11-06T17:00:00Z")
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "getDaySummary" -> DaySummaryDto(args!![0] as String, 0, 0, emptyList(), meta)
                "getDay" -> DayDto(date = args!![0] as String, timeBlocks = emptyList(), meta = meta)
                "getDayPreview" -> DayPreviewDto(date = args!![0] as String, timeBlocks = emptyList(), meta = meta)
                "listTaskTypes" -> emptyList<TaskTypeDto>()
                else -> error("Unexpected ${method.name}")
            }
        } as TimeboxApi
        val repository = TimeboxRepository(api, StandardTestDispatcher(testScheduler), dayElapsedRealtime = { 0L })
        val coordinator = ReadyToPlanCoordinator(object : ReadyToPlanTransport {
            override suspend fun setReady(taskId: Int, ready: Boolean) = error("Unused")
        }, backgroundScope)
        val model = DayViewModel(repository, TaskCompletion(RepositoryTaskCompletionTransport(repository)),
            PlanningSession(RepositoryPlanningSessionTransport(repository, coordinator)), injectedScope = backgroundScope,
            readinessCoordinator = coordinator)
        model.goToDate(LocalDate.parse("2026-11-01")); runCurrent()
        model.openToday(); runCurrent()
        assertEquals(LocalDate.parse("2026-11-06"), model.state.value.date)
        assertEquals(LocalDate.parse("2026-11-06"), model.state.value.today)
        assertEquals(LocalDate.parse("2026-11-06"), model.state.value.day?.date)
    }
}
