package com.timebox.android.ui.planning

import com.timebox.android.data.ApiError
import com.timebox.android.data.ApiErrorException
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.PlanningCommitPlacement
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.battleplan.task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanningSessionTest {
    @Test
    fun `queue load flattens Ready to Plan session Tasks and keeps failure retryable`() = runTest {
        val nested = task(2, ready = true)
        val transport = InMemoryPlanningSessionTransport(
            readyTasks = listOf(task(1, sessionTasks = listOf(nested)), task(3, ready = true)),
        )
        val session = PlanningSession(transport)

        session.refreshQueue()

        assertEquals(listOf(2, 3), session.state.value.readyTasks.map { it.id })
        assertFalse(session.state.value.queueLoading)
        assertNull(session.state.value.queueError)

        transport.loadFailure = ApiErrorException(ApiError("Offline"))
        session.refreshQueue()

        assertEquals(listOf(2, 3), session.state.value.readyTasks.map { it.id })
        assertEquals("Offline", session.state.value.queueError)
        assertFalse(session.state.value.queueLoading)
    }

    @Test
    fun `drafting owns block and session overlap policy`() = runTest {
        val transport = InMemoryPlanningSessionTransport(
            readyTasks = listOf(task(1, ready = true), task(2, ready = true)),
        )
        val session = PlanningSession(transport)
        val day = day(blocks = arrayOf(block(41, 10 * 60, 11 * 60)))
        session.refreshQueue()
        session.begin()

        assertEquals(
            PlanningEditResult.Rejected("That time is already planned"),
            session.place(1, day, 10 * 60),
        )
        assertEquals(PlanningEditResult.Accepted, session.place(1, day, 11 * 60))
        assertNull(session.state.value.selectedTaskId)
        assertEquals(
            PlanningEditResult.Rejected("That time is already planned"),
            session.place(2, day, 11 * 60),
        )
        assertEquals(PlanningEditResult.Accepted, session.place(2, day, 12 * 60))
        assertEquals(
            PlanningEditResult.Rejected("That time is already planned"),
            session.update(2, 11 * 60, 12 * 60),
        )

        session.returnTask(1)

        assertEquals(setOf(2), session.state.value.drafts.keys)
        assertEquals(PlanningEditResult.Accepted, session.update(2, 11 * 60, 12 * 60))
    }

    @Test
    fun `cancel discards the whole session but preserves the reusable queue`() = runTest {
        val transport = InMemoryPlanningSessionTransport(listOf(task(1, ready = true)))
        val session = PlanningSession(transport)
        val day = day()
        session.refreshQueue()
        session.begin()
        session.toggleSelection(1)
        assertEquals(1, session.state.value.selectedTaskId)
        session.toggleSelection(1)
        assertNull(session.state.value.selectedTaskId)
        session.toggleSelection(1)
        session.place(1, day, 9 * 60)

        session.cancel()

        assertFalse(session.state.value.active)
        assertTrue(session.state.value.drafts.isEmpty())
        assertNull(session.state.value.selectedTaskId)
        assertEquals(listOf(1), session.state.value.readyTasks.map { it.id })
    }

    @Test
    fun `successful commit is one atomic transport call and refreshes the queue`() = runTest {
        val transport = InMemoryPlanningSessionTransport(
            readyTasks = listOf(task(1, ready = true), task(2, ready = true)),
        )
        val session = PlanningSession(transport)
        val today = day()
        val tomorrow = day(today.date.plusDays(1))
        transport.committedDays = listOf(today, tomorrow)
        session.refreshQueue()
        session.begin()
        session.place(1, today, 9 * 60)
        session.place(2, tomorrow, 9 * 60)

        val outcome = session.commit()

        assertTrue(outcome is PlanningCommitOutcome.Saved)
        assertEquals(
            listOf(
                PlanningCommitPlacement(today.date, 1, 9 * 60, 9 * 60 + 30),
                PlanningCommitPlacement(tomorrow.date, 2, 9 * 60, 9 * 60 + 30),
            ),
            transport.commits.single(),
        )
        assertEquals(listOf("load", "commit", "load"), transport.calls)
        assertFalse(session.state.value.active)
        assertFalse(session.state.value.saving)
        assertTrue(session.state.value.drafts.isEmpty())
        assertTrue(session.state.value.readyTasks.isEmpty())
    }

    @Test
    fun `failed commit preserves drafts and can be retried without rebuilding the session`() = runTest {
        val transport = InMemoryPlanningSessionTransport(listOf(task(1, ready = true)))
        val session = PlanningSession(transport)
        val day = day()
        session.refreshQueue()
        session.begin()
        session.place(1, day, 9 * 60)
        transport.commitFailure = ApiErrorException(ApiError("Plan could not be saved"))

        val failed = session.commit()

        assertEquals(PlanningCommitOutcome.Failed("Plan could not be saved"), failed)
        assertTrue(session.state.value.active)
        assertEquals(setOf(1), session.state.value.drafts.keys)
        assertEquals("Plan could not be saved", session.state.value.failure)
        assertFalse(session.state.value.saving)

        transport.commitFailure = null
        transport.committedDays = listOf(day)
        assertTrue(session.commit() is PlanningCommitOutcome.Saved)
        assertFalse(session.state.value.active)
        assertTrue(session.state.value.drafts.isEmpty())
    }
}

private class InMemoryPlanningSessionTransport(
    var readyTasks: List<BattleTask>,
) : PlanningSessionTransport {
    val calls = mutableListOf<String>()
    val commits = mutableListOf<List<PlanningCommitPlacement>>()
    var loadFailure: Throwable? = null
    var commitFailure: Throwable? = null
    var committedDays: List<Day> = emptyList()

    override suspend fun loadReadyTasks(): Result<List<BattleTask>> {
        calls += "load"
        return loadFailure?.let(Result.Companion::failure) ?: Result.success(readyTasks.readyToPlanTasks())
    }

    override suspend fun commit(placements: List<PlanningCommitPlacement>): Result<List<Day>> {
        calls += "commit"
        commits += placements
        commitFailure?.let { return Result.failure(it) }
        val planned = placements.mapTo(mutableSetOf()) { it.taskId }
        readyTasks = readyTasks.filterNot { it.id in planned }
        return Result.success(committedDays)
    }
}

private fun day(
    date: LocalDate = LocalDate.of(2026, 8, 31),
    blocks: Array<out TimeBlock> = emptyArray(),
) = Day(
    date = date,
    startHour = 8,
    endHour = 20,
    showFullDay = false,
    blocks = blocks.toList(),
    timezone = "Asia/Singapore",
    today = LocalDate.of(2026, 8, 31),
    serverNowMinute = 9 * 60,
)

private fun block(id: Int, start: Int, end: Int) = TimeBlock(
    id = id,
    lane = Lane.Planned,
    taskTypeId = 1,
    taskTypeName = "work",
    taskId = null,
    task = null,
    note = null,
    plannedBlockId = null,
    startMinute = start,
    endMinute = end,
)
