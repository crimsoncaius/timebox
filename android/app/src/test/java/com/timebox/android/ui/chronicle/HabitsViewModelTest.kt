package com.timebox.android.ui.chronicle

import com.timebox.android.data.HabitTotalTone
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

    private val wednesday = LocalDate.of(2026, 9, 23)

    private fun api(respond: (name: String, args: Array<Any?>?) -> Any?) =
        Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            respond(method.name, args)
        } as TimeboxApi

    private fun gymState(vm: HabitsViewModel) = vm.state.value.week!!.habits.single().days[2].state.wire

    @Test fun `tick shows at once and the server's week replaces it`() = runTest(dispatcher) {
        var fail = false
        val vm = HabitsViewModel(TimeboxRepository(api { name, args ->
            when (name) {
                "habitsWeek" -> week("2026-09-21")
                "tickHabit" -> {
                    assertEquals(listOf<Any?>(7, "2026-09-23"), args!!.take(2))
                    if (fail) throw IllegalStateException("offline")
                    week("2026-09-21", state = "met")
                }
                else -> error(name)
            }
        }, dispatcher))
        vm.refresh()
        advanceUntilIdle()
        vm.tick(7, wednesday)
        assertEquals("met", gymState(vm))
        assertEquals(1, vm.state.value.week!!.habits.single().total.done)
        assertTrue(HabitCellKey(7, wednesday) in vm.state.value.pending)
        advanceUntilIdle()
        assertEquals("met", gymState(vm))
        assertTrue(vm.state.value.pending.isEmpty())
        fail = true
        vm.tick(7, wednesday)
        advanceUntilIdle()
        assertTrue(vm.state.value.actionError != null)
        assertEquals("met", gymState(vm))
    }

    @Test fun `a failed tick rolls the cell back`() = runTest(dispatcher) {
        val vm = HabitsViewModel(TimeboxRepository(api { name, _ ->
            when (name) {
                "habitsWeek" -> week("2026-09-21")
                "tickHabit" -> throw IllegalStateException("offline")
                else -> error(name)
            }
        }, dispatcher))
        vm.refresh()
        advanceUntilIdle()
        vm.tick(7, wednesday)
        assertEquals("met", gymState(vm))
        advanceUntilIdle()
        assertEquals("missed", gymState(vm))
        assertEquals(0, vm.state.value.week!!.habits.single().total.done)
        assertTrue(vm.state.value.pending.isEmpty())
        assertTrue(vm.state.value.actionError != null)
    }

    @Test fun `rapid taps are sent in order and the last one wins`() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val vm = HabitsViewModel(TimeboxRepository(api { name, _ ->
            when (name) {
                "habitsWeek" -> week("2026-09-21")
                "tickHabit" -> { calls += name; week("2026-09-21", state = "met") }
                "untickHabit" -> { calls += name; week("2026-09-21", state = "missed") }
                else -> error(name)
            }
        }, dispatcher))
        vm.refresh()
        advanceUntilIdle()
        vm.tick(7, wednesday)
        vm.untick(7, wednesday)
        assertEquals("missed", gymState(vm))
        advanceUntilIdle()
        assertEquals(listOf("tickHabit", "untickHabit"), calls)
        assertEquals("missed", gymState(vm))
        assertTrue(vm.state.value.pending.isEmpty())
    }

    @Test fun `a read started before a tick settles does not undo it`() = runTest(dispatcher) {
        var reads = 0
        val vm = HabitsViewModel(TimeboxRepository(api { name, _ ->
            when (name) {
                // The second read answers with the week as it stood before the tick.
                "habitsWeek" -> week("2026-09-21").also { reads++ }
                "tickHabit" -> week("2026-09-21", state = "met")
                else -> error(name)
            }
        }, dispatcher))
        vm.refresh()
        advanceUntilIdle()
        vm.tick(7, wednesday)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, reads)
        assertEquals("met", gymState(vm))
    }

    @Test fun `quota edits predict counts, states and totals`() {
        val today = LocalDate.of(2026, 9, 26)
        fun quota(frequency: String, unit: String, month: String? = null) = HabitsWeekDto(
            today.toString(), "2026-09-21", "2026-09-14",
            listOf(
                HabitDto(
                    templateId = 3, title = "Read", mode = "quota", status = "active", frequency = frequency,
                    interval = 1, quotaCount = 2,
                    days = List(7) {
                        val date = LocalDate.of(2026, 9, 21).plusDays(it.toLong())
                        val state = when {
                            date.isAfter(today) -> "upcoming"
                            frequency == "daily" -> if (date == today) "open" else "missed"
                            else -> if (date == today) "open" else "empty"
                        }
                        HabitDayDto(date.toString(), state, target = if (frequency == "daily") 2 else null, tickable = !date.isAfter(today))
                    },
                    total = HabitTotalDto(0, if (frequency == "daily") 6 else 2, unit, month, tone = "open"),
                ),
            ),
        ).toModel()
        val tuesday = HabitCellKey(3, LocalDate.of(2026, 9, 22))

        val daily = quota("daily", "days").withHabitEdit(tuesday, tick = true).habits.single()
        assertEquals("partial", daily.days[1].state.wire)
        assertEquals(1, daily.days[1].count)
        val dailyMet = quota("daily", "days").withHabitEdit(tuesday, true).withHabitEdit(tuesday, true).habits.single()
        assertEquals("met", dailyMet.days[1].state.wire)
        assertEquals(1, dailyMet.total.done)

        val weekly = quota("weekly", "sessions").withHabitEdit(tuesday, true).withHabitEdit(tuesday, true)
        assertEquals("count", weekly.habits.single().days[1].state.wire)
        assertEquals(2, weekly.habits.single().total.done)
        assertEquals(HabitTotalTone.Met, weekly.habits.single().total.tone)
        val undone = weekly.withHabitEdit(tuesday, tick = false).habits.single()
        assertEquals(1, undone.total.done)
        assertEquals(HabitTotalTone.Open, undone.total.tone)

        val future = quota("weekly", "sessions").withHabitEdit(HabitCellKey(3, LocalDate.of(2026, 9, 27)), true)
        assertEquals("upcoming", future.habits.single().days[6].state.wire)
        assertEquals(0, future.habits.single().total.done)

        val otherMonth = quota("monthly", "month", month = "2026-08-01").withHabitEdit(tuesday, true)
        assertEquals(0, otherMonth.habits.single().total.done)
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
