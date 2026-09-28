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

    @Test fun `goal history bounds navigation and offline retains the confirmed report`() = runTest(dispatcher) {
        var offline = false
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            if (offline) return@newProxyInstance offline(args)
            val start = args?.get(0) as String? ?: "2026-09-21"
            when (method.name) {
                "timeGoals" -> week(start)
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = TimeGoalsViewModel(TimeboxRepository(api, dispatcher))
        vm.refresh(); advanceUntilIdle()
        vm.shiftWeek(-1); advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-09-14"), vm.state.value.goals!!.weekStart)
        val confirmed = vm.state.value.goals
        offline = true
        vm.shiftWeek(-1); advanceUntilIdle()
        assertTrue(vm.state.value.offline)
        assertEquals(confirmed, vm.state.value.goals)
        assertEquals(LocalDate.parse("2026-09-14"), vm.state.value.goals!!.weekStart)
        offline = false
        vm.refresh(); advanceUntilIdle()
        assertFalse(vm.state.value.offline)
    }

    @Test fun `tabs keep independent weeks and goal failure does not block habits`() = runTest(dispatcher) {
        var failGoals = false
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            val start = args?.get(0) as String? ?: "2026-09-21"
            when (method.name) {
                "habitsWeek" -> HabitsWeekDto("2026-09-27", start, "2026-09-07", emptyList())
                "timeGoals" -> if (failGoals) offline(args) else week(start)
                else -> error(method.name)
            }
        } as TimeboxApi
        val repository = TimeboxRepository(api, dispatcher)
        val habits = HabitsViewModel(repository)
        val goals = TimeGoalsViewModel(repository)
        habits.refresh(); goals.refresh(); advanceUntilIdle()
        goals.shiftWeek(-2); habits.shiftWeek(-1); advanceUntilIdle()
        habits.refresh(); goals.refresh(); advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-09-14"), habits.state.value.week!!.weekStart)
        assertEquals(LocalDate.parse("2026-09-07"), goals.state.value.goals!!.weekStart)
        failGoals = true
        goals.refresh(); habits.thisWeek(); advanceUntilIdle()
        assertTrue(goals.state.value.offline)
        assertFalse(habits.state.value.offline)
        assertNull(habits.state.value.error)
        assertEquals(LocalDate.parse("2026-09-21"), habits.state.value.week!!.weekStart)
        assertEquals(LocalDate.parse("2026-09-07"), goals.state.value.goals!!.weekStart)
    }

    @Test fun `failed save keeps the editor open and does not change a target`() = runTest(dispatcher) {
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "timeGoals" -> week("2026-09-21")
                "changeTimeGoalTarget" -> offline(args)
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = TimeGoalsViewModel(TimeboxRepository(api, dispatcher))
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
                "timeGoals" -> week("2026-09-21")
                "timeGoalPeriod" -> if (failPeriod) offline(args) else goal("2026-09-23")
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = TimeGoalsViewModel(TimeboxRepository(api, dispatcher))
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
