package com.timebox.android.ui.chronicle

import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimeGoalsTest {
    @Suppress("UNCHECKED_CAST")
    private fun offline(args: Array<Any?>?): Any {
        (args!!.last() as kotlin.coroutines.Continuation<Any?>).resumeWith(Result.failure(IOException("offline")))
        return kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
    }
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private fun goal(start: String = "2026-09-21") = TimeGoalDto(1, 2, "exercise/cardio", "week", 1,
        "2026-09-01", targetMinutes = 240, nextTargetDate = "2026-09-28",
        period = GoalPeriodDto(start, "2026-09-27", 240, 7200.0, "in_progress", emptyList()),
        days = mapOf("2026-09-23" to 7200.0))
    private fun week(start: String) = TimeGoalsWeekDto("2026-09-27", start, "2026-08-31", "Asia/Singapore",
        "2026-09-27T12:00:00Z", listOf(goal(start)))

    @Test fun `goal history extends week navigation and offline retains the whole confirmed week`() = runTest(dispatcher) {
        var offline = false
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            if (offline) return@newProxyInstance offline(args)
            val start = args?.get(0) as String? ?: "2026-09-21"
            when (method.name) {
                "habitsWeek" -> HabitsWeekDto("2026-09-27", start, "2026-09-21", emptyList())
                "timeGoals" -> week(start)
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = HabitsViewModel(TimeboxRepository(api, dispatcher))
        vm.refresh(); advanceUntilIdle()
        vm.shiftWeek(-1); advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-09-14"), vm.state.value.week!!.weekStart)
        val confirmed = vm.state.value.goals
        offline = true
        vm.shiftWeek(-1); advanceUntilIdle()
        assertTrue(vm.state.value.offline)
        assertEquals(confirmed, vm.state.value.goals)
        assertEquals(LocalDate.parse("2026-09-14"), vm.state.value.week!!.weekStart)
        offline = false
        vm.refresh(); advanceUntilIdle()
        assertFalse(vm.state.value.offline)
    }

    @Test fun `failed save keeps the editor open and does not change a target`() = runTest(dispatcher) {
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "habitsWeek" -> HabitsWeekDto("2026-09-27", "2026-09-21", "2026-09-21", emptyList())
                "timeGoals" -> week("2026-09-21")
                "changeTimeGoalTarget" -> offline(args)
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = HabitsViewModel(TimeboxRepository(api, dispatcher))
        vm.refresh(); advanceUntilIdle()
        var closed = false
        vm.saveGoal(1, false, TimeGoalWriteDto(2, "week", 1, 120)) { closed = true }
        advanceUntilIdle()
        assertFalse(closed)
        assertFalse(vm.state.value.goalSaving)
        assertNotNull(vm.state.value.goalError)
        assertEquals(240, vm.state.value.goals!!.goals.single().targetMinutes)
        vm.refresh(); advanceUntilIdle()
        assertFalse(vm.state.value.offline)
        assertNull(vm.state.value.goalError)
    }

    @Test fun `preview clips first weekly and monthly cycles without rolling the anchor`() {
        assertEquals(LocalDate.parse("2026-10-04"), firstGoalPeriodEnd(LocalDate.parse("2026-09-23"), "week", 2))
        assertEquals(LocalDate.parse("2024-02-29"), firstGoalPeriodEnd(LocalDate.parse("2024-01-31"), "month", 2))
        assertEquals(LocalDate.parse("2026-09-29"), firstGoalPeriodEnd(LocalDate.parse("2026-09-27"), "day", 3))
    }

    @Test fun `refresh does not discard selected period if its request fails`() = runTest(dispatcher) {
        var failPeriod = false
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "habitsWeek" -> HabitsWeekDto("2026-09-27", "2026-09-21", "2026-09-21", emptyList())
                "timeGoals" -> week("2026-09-21")
                "timeGoalPeriod" -> if (failPeriod) offline(args) else goal("2026-09-23")
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = HabitsViewModel(TimeboxRepository(api, dispatcher))
        vm.refresh(); advanceUntilIdle()
        vm.selectGoalDay(1, LocalDate.parse("2026-09-23")); advanceUntilIdle()
        val selected = vm.state.value.goals
        failPeriod = true
        vm.refresh(); advanceUntilIdle()
        assertEquals(selected, vm.state.value.goals)
        assertTrue(vm.state.value.offline)
        assertFalse(vm.state.value.loading)
    }
}
