package com.timebox.android.ui.chronicle

import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.data.remote.TrendsDto
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
class ChronicleTrendsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test fun `uses report dates for navigation and preserves Trends while drilling across months`() = runTest(dispatcher) {
        val calls = mutableListOf<List<Any?>>()
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            check(method.name == "trends")
            calls += args!!.take(4)
            TrendsDto("2026-09-28", "2026-10-04", "2026-10-02", "Asia/Singapore", "2026-10-02T00:00:00Z", 0.0, emptyList())
        } as TimeboxApi
        val vm = ChronicleViewModel(TimeboxRepository(api, dispatcher))
        vm.selectView(ChronicleView.Trends)
        advanceUntilIdle()
        assertEquals(listOf("week", null, null, null), calls.last())
        assertFalse(canAdvanceTrendRange(vm.state.value.trends!!, "week"))
        vm.shiftRange(1)
        advanceUntilIdle()
        assertEquals(1, calls.size)
        vm.shiftRange(-1)
        advanceUntilIdle()
        assertEquals("2026-09-21", calls.last()[1])
        vm.showContributingDays("work", mapOf("2026-09-30" to 600.0, "2026-10-01" to 900.0))
        assertEquals(LocalDate.of(2026, 10, 1), vm.state.value.monthStart)
        assertEquals(2, vm.state.value.highlightedDays.size)
        assertEquals("week", vm.state.value.period)
        assertEquals(LocalDate.of(2026, 9, 21), vm.state.value.anchor)
        assertEquals(TrendHighlightRange("week", LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)), vm.state.value.highlightedRange)
        vm.clearHighlights()
        assertTrue(vm.state.value.highlightedDays.isEmpty())
        assertNull(vm.state.value.highlightedRange)
    }

    @Test fun `highlight summary counts contributing days against the drilled range`() {
        val week = TrendHighlightRange("week", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27))
        assertEquals("Week · 21–27 Sep 2026", chronicleHighlightRangeLabel(week))
        assertEquals("Month · September 2026", chronicleHighlightRangeLabel(TrendHighlightRange("month", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))))
        assertEquals("Custom range · 28 Sep – 4 Oct 2026", chronicleHighlightRangeLabel(TrendHighlightRange("custom", LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4))))
        assertEquals(" on 1 of 7 days, 2h 5m in total.", chronicleHighlightSummary(mapOf("2026-09-26" to 7500.0), week))
        assertEquals(" on 2 days, 0h 30m in total.", chronicleHighlightSummary(mapOf("2026-09-22" to 600.0, "2026-09-23" to 1200.0), null))
    }

    @Test fun `custom range stops at server Today and rejects future dates before requesting`() = runTest(dispatcher) {
        val calls = mutableListOf<List<Any?>>()
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            check(method.name == "trends")
            calls += args!!.take(4)
            TrendsDto("2026-09-28", "2026-10-04", "2026-10-02", "Asia/Singapore", "2026-10-02T00:00:00Z", 0.0, emptyList())
        } as TimeboxApi
        val vm = ChronicleViewModel(TimeboxRepository(api, dispatcher))
        vm.selectView(ChronicleView.Trends)
        advanceUntilIdle()
        vm.setPeriod("custom")
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 2), vm.state.value.customEnd)
        assertEquals("2026-10-02", calls.last()[3])
        val callCount = calls.size
        vm.customRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 3))
        advanceUntilIdle()
        assertEquals(callCount, calls.size)
        assertTrue(vm.state.value.customRangeError!!.contains("Today"))
        vm.customRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2))
        advanceUntilIdle()
        assertEquals(callCount + 1, calls.size)
        assertNull(vm.state.value.customRangeError)
    }

    @Test fun `subminute recorded time does not look empty`() {
        assertEquals("<1m", trendDuration(40.0))
        assertEquals("1h 1m", trendDuration(3660.0))
    }
}
