package com.timebox.android.ui.chronicle

import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.HabitDayDto
import com.timebox.android.data.remote.HabitDto
import com.timebox.android.data.remote.HabitTotalDto
import com.timebox.android.data.remote.HabitsWeekDto
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.data.toModel
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    private fun gym(state: String = "missed") = HabitDto(
        templateId = 7, title = "Gym", mode = "scheduled", status = "active", frequency = "weekly",
        interval = 1, weekdays = listOf(0, 2, 4),
        days = List(7) { HabitDayDto(LocalDate.of(2026, 9, 21).plusDays(it.toLong()).toString(), if (it == 2) state else "not_due", tickable = it == 2) },
        total = HabitTotalDto(if (state == "met") 1 else 0, 3, "days", tone = "open"),
    )

    private fun week(start: String, state: String = "missed") =
        HabitsWeekDto("2026-09-26", start, "2026-09-14", listOf(gym(state)))

    @Test fun `navigates within earliest and current week`() = runTest(dispatcher) {
        val weeks = mutableListOf<Any?>()
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            check(method.name == "habitsWeek")
            weeks += args!![0]
            week((args[0] as String?) ?: "2026-09-21")
        } as TimeboxApi
        val vm = HabitsViewModel(TimeboxRepository(api, dispatcher))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf<Any?>(null), weeks)
        vm.shiftWeek(1)
        advanceUntilIdle()
        assertEquals(1, weeks.size)
        vm.shiftWeek(-1)
        advanceUntilIdle()
        assertEquals("2026-09-14", weeks.last())
        vm.shiftWeek(-1)
        advanceUntilIdle()
        assertEquals(2, weeks.size)
        vm.thisWeek()
        advanceUntilIdle()
        assertNull(weeks.last())
    }

    @Test fun `tick replaces the week and failures surface without losing it`() = runTest(dispatcher) {
        var fail = false
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "habitsWeek" -> week("2026-09-21")
                "tickHabit" -> {
                    assertEquals(listOf<Any?>(7, "2026-09-23"), args!!.take(2))
                    if (fail) throw IllegalStateException("offline")
                    week("2026-09-21", state = "met")
                }
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = HabitsViewModel(TimeboxRepository(api, dispatcher))
        vm.refresh()
        advanceUntilIdle()
        vm.tick(7, LocalDate.of(2026, 9, 23))
        assertTrue(HabitCellKey(7, LocalDate.of(2026, 9, 23)) in vm.state.value.pending)
        advanceUntilIdle()
        assertEquals("met", vm.state.value.week!!.habits.single().days[2].state.wire)
        assertTrue(vm.state.value.pending.isEmpty())
        fail = true
        vm.tick(7, LocalDate.of(2026, 9, 23))
        advanceUntilIdle()
        assertTrue(vm.state.value.actionError != null)
        assertEquals("met", vm.state.value.week!!.habits.single().days[2].state.wire)
    }

    @Test fun `adding a habit opts the routine in and reloads`() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        var patch: JsonObject? = null
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            calls += method.name
            when (method.name) {
                "habitsWeek" -> week("2026-09-21")
                "patchRecurringTemplate" -> {
                    patch = args!![1] as JsonObject
                    throw IllegalStateException("stop after capturing the patch")
                }
                else -> error(method.name)
            }
        } as TimeboxApi
        val vm = HabitsViewModel(TimeboxRepository(api, dispatcher))
        vm.addHabit(9)
        advanceUntilIdle()
        assertEquals("true", patch!!.getValue("track_as_habit").jsonPrimitive.content)
        assertEquals(setOf("track_as_habit"), patch!!.keys)
        assertTrue(vm.state.value.actionError != null)
    }

    @Test fun `cadence and total units read as short labels`() {
        val scheduled = gym().toModel()
        assertEquals("Mon · Wed · Fri", habitCadence(scheduled))
        assertEquals("3× week", habitCadence(scheduled.copy(mode = RecurrenceMode.Quota, quotaCount = 3)))
        assertEquals("Daily", habitCadence(scheduled.copy(frequency = RecurrenceFrequency.Daily)))
        assertEquals("days", habitTotalUnit(scheduled.total))
        assertEquals("in Sep", habitTotalUnit(scheduled.total.copy(unit = "month", month = LocalDate.of(2026, 9, 1))))
        assertEquals("sessions", habitTotalUnit(scheduled.total.copy(unit = "sessions")))
    }
}
