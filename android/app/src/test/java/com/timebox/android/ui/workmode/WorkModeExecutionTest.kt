package com.timebox.android.ui.workmode

import com.timebox.android.data.ActualBlock
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.Subtask
import com.timebox.android.data.TimeBlock
import com.timebox.android.data.WorkModeSnapshot
import com.timebox.android.data.primaryIdentity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun `resuming an active Actual without its Planned origin preserves snapshotted name`() = runTest {
        val clock = FakeClock("2026-08-30T01:17:00Z")
        val transport = MemoryTransport(day()).also {
            it.active = actual(null, Instant.parse("2026-08-30T01:00:00Z")).copy(
                name = "Snapshotted session",
                taskId = null,
                taskTypeName = "unspecified",
            )
        }
        val execution = WorkModeExecution(transport, MemoryPersistence(), this, clock::now, 1_000)

        execution.begin(transport.day)

        assertEquals("Snapshotted session", execution.state.value.session?.currentBlock?.name)
        assertEquals("Snapshotted session", execution.state.value.session?.currentBlock?.primaryIdentity())
        execution.exit()
        runCurrent()
    }

    @Test
    fun `begin confirms for a minute records Actual and exit clears durable session`() = runTest {
        val clock = FakeClock("2026-08-30T01:17:00Z")
        val transport = MemoryTransport(day(block(31, 9 * 60, 10 * 60)))
        val store = MemoryPersistence()
        val execution = WorkModeExecution(transport, store, this, clock::now, 1_000)

        execution.begin(transport.day)
        assertNotNull(execution.state.value.session)
        assertTrue(transport.started.isEmpty())

        clock.advance(60)
        advanceTimeBy(60_001)
        runCurrent()
        assertEquals(31, transport.started.single().first)
        assertNotNull(execution.state.value.session?.activeActual)
        assertNotNull(store.snapshot)

        execution.exit()
        runCurrent()
        assertNull(execution.state.value.session)
        assertNull(store.snapshot)
        assertEquals("Actual time preserved · Task remains open", execution.state.value.notice)
    }

    @Test
    fun `recovery prompts after absence and backfills elapsed planned blocks without duplication`() = runTest {
        val clock = FakeClock("2026-08-30T01:40:00Z")
        val transport = MemoryTransport(day(block(31, 9 * 60, 9 * 60 + 30)))
        val store = MemoryPersistence(WorkModeSnapshot(
            entryAt = "2026-08-30T01:00:00Z",
            lastConfirmedAt = "2026-08-30T01:10:00Z",
            lastObservedAt = "2026-08-30T01:20:00Z",
        ))
        val execution = WorkModeExecution(transport, store, this, clock::now, 1_000)

        execution.restore(transport.day)
        assertTrue(execution.state.value.restorePrompt)
        execution.continueAfterAbsence()
        runCurrent()

        assertFalse(execution.state.value.restorePrompt)
        assertEquals(1, transport.created.size)
        assertNotNull(store.snapshot)
        execution.exit()
        runCurrent()
    }

    @Test
    fun `transport failure preserves the session and exposes a retryable error`() = runTest {
        val clock = FakeClock("2026-08-30T01:17:00Z")
        val transport = MemoryTransport(day(block(31, 9 * 60, 10 * 60))).also { it.failStart = true }
        val execution = WorkModeExecution(transport, MemoryPersistence(), this, clock::now, 1_000)

        execution.begin(transport.day)
        clock.advance(60)
        advanceTimeBy(60_001)
        runCurrent()

        assertNotNull(execution.state.value.session)
        assertEquals("start failed", execution.state.value.session?.error)
        assertNull(execution.state.value.session?.activeActual)
        execution.declineAfterAbsence()
        runCurrent()
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
