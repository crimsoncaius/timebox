package com.timebox.android.ui.workmode

import com.timebox.android.data.ActualBlock
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.Subtask
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.WorkModeSnapshot
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkModeExecutionTest {
    @Test
    fun `activity cutover rejects every legacy entry and preserves recovery snapshot`() = runTest {
        org.junit.Assume.assumeTrue(com.timebox.android.BuildConfig.ACTIVITY_TRACKING_DEV)
        val clock = FakeClock("2026-08-30T01:17:00Z")
        val transport = MemoryTransport(day(block(31, 540, 600)))
        val saved = WorkModeSnapshot("2026-08-30T01:00:00Z", "2026-08-30T01:10:00Z", "2026-08-30T01:15:00Z")
        val store = MemoryPersistence(saved)
        val execution = WorkModeExecution(transport, store, backgroundScope, clock::now)

        execution.restore(transport.day)
        execution.begin(transport.day, force = true)
        execution.continueEntry(transport.day)
        execution.resume(transport.day, actual(31, Instant.parse(saved.entryAt)))
        clock.advance(3600)
        advanceTimeBy(3600_001)
        runCurrent()

        assertNull(execution.state.value.session)
        assertFalse(execution.state.value.visible)
        assertEquals(saved, store.snapshot)
        assertTrue(transport.started.isEmpty())
        assertTrue(transport.created.isEmpty())
        assertTrue(transport.ended.isEmpty())
    }
}

private class FakeClock(initial: String) {
    private var value = Instant.parse(initial)
    fun now(): Instant = value
    fun advance(seconds: Long) { value = value.plusSeconds(seconds) }
}

private class MemoryPersistence(var snapshot: WorkModeSnapshot? = null) : WorkModePersistence {
    override suspend fun load() = snapshot
    override suspend fun save(snapshot: WorkModeSnapshot?) { this.snapshot = snapshot }
}

private class MemoryTransport(val day: Day) : WorkModeTransport {
    val started = mutableListOf<Pair<Int, Instant>>()
    val created = mutableListOf<Triple<Int, Instant, Instant>>()
    val ended = mutableListOf<Pair<Int, Instant>>()
    var active: ActualBlock? = null
    var failStart = false

    override suspend fun getDay(date: LocalDate) = Result.success(day.copy(date = date))
    override suspend fun getActiveActual() = Result.success(active)
    override suspend fun listTasks() = Result.success(emptyList<BattleTask>())
    override suspend fun startActual(plannedBlockId: Int, startAt: Instant): Result<ActualBlock> {
        if (failStart) return Result.failure(IllegalStateException("start failed"))
        started += plannedBlockId to startAt
        return Result.success(actual(plannedBlockId, startAt).also { active = it })
    }
    override suspend fun createActual(plannedBlockId: Int, startAt: Instant, endAt: Instant): Result<ActualBlock> {
        created += Triple(plannedBlockId, startAt, endAt)
        return Result.success(actual(plannedBlockId, startAt).copy(endAt = endAt))
    }
    override suspend fun endActual(actualBlockId: Int, endAt: Instant): Result<ActualBlock> {
        ended += actualBlockId to endAt
        val endedActual = (active ?: actual(31, endAt)).copy(endAt = endAt)
        active = null
        return Result.success(endedActual)
    }
    override suspend fun setSubtask(subtask: Subtask, checked: Boolean) = Result.success(subtask.copy(checked = checked))
}

private fun day(vararg blocks: TimeBlock) = Day(
    date = LocalDate.parse("2026-08-30"), startHour = 8, endHour = 20, showFullDay = false,
    blocks = blocks.toList(), timezone = "Asia/Singapore", today = LocalDate.parse("2026-08-30"), serverNowMinute = 9 * 60 + 17,
)

private fun block(id: Int, start: Int, end: Int) = TimeBlock(
    id, Lane.Planned, 3, "coding", 10, null, null, null, null, start, end,
)

private fun actual(plannedBlockId: Int?, startAt: Instant) = ActualBlock(
    44, 3, "coding", 10, null, null, plannedBlockId, startAt, null,
)
