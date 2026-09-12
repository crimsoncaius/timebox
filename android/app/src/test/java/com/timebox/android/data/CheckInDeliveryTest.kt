package com.timebox.android.data

import com.timebox.android.checkin.CheckInDelivery
import com.timebox.android.checkin.CheckInNotificationSink
import com.timebox.android.data.remote.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CheckInDeliveryTest {
    private class Memory : ActivityStorage {
        var value: String? = null
        var fail = false
        override fun load() = value
        override fun save(value: String) { check(!fail); this.value = value }
    }
    private class Scenario {
        val at = "2026-09-11T10:00:00Z"
        val end = "2026-09-11T12:00:00Z"
        val row = ActualBlockDto(1, 1, TaskTypeDto(1, "Reading"), startAt = at, createdAt = at, updatedAt = at)
        var snapshot = ActivitySnapshotDto(cursor = 1, serverAt = end, reportingTimezone = "UTC", current = row,
            records = listOf(row), offlineReady = true, checkIn = CheckInStateDto(generation = "a", rearm = 0, armedAt = at))
        var offline = false
        var wrongOwner = false
        var allowed = true
        var crashOnShow = false
        val shown = mutableListOf<String>()
        val journal = Memory()
        val attempts = Memory()
        val transport = object : ActivityTransport {
            override suspend fun read(): ActivitySnapshotDto { check(!offline); return snapshot }
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto {
                check(!offline)
                val shared = snapshot.checkIn!!
                val event = command.checkIn!!
                val question = when (event.action) {
                    "candidate" -> shared.question ?: CheckInQuestionDto("a:0", command.actionAt, command.deviceId, command.operationId)
                    "delivery" -> shared.question?.copy(delivery = CheckInDeliveryDto(command.deviceId, if (wrongOwner) "different-operation" else command.operationId, command.actionAt))
                    else -> shared.question
                }
                snapshot = snapshot.copy(cursor = snapshot.cursor + 1, checkIn = shared.copy(question = question),
                    acknowledgement = ActivityAcknowledgementDto(command.operationId, ActivityOutcome.Applied))
                return snapshot
            }
        }
        val sink = object : CheckInNotificationSink {
            override fun allowed() = allowed
            override fun show(question: String) { check(attempts.value == question); shown += question; check(!crashOnShow) }
        }
        fun repository() = ActivityRepository(transport, journal)
        fun delivery(repository: ActivityRepository) = CheckInDelivery(repository, sink, attempts)
        fun event() = CheckInEventDto("candidate", "a", capability = "approximate", permission = "granted", observed = "locked", coverageStart = at, coverageEnd = end)
    }
    @Test fun `acknowledged origin and claim delivers once and persists attempt before OS`() = runTest {
        val s = Scenario(); val repo = s.repository(); repo.refresh()
        s.delivery(repo).candidate(s.event())
        s.delivery(s.repository()).candidate(s.event())
        assertEquals(listOf("a:0"), s.shown)
        assertEquals(s.row, repo.state.value.snapshot!!.current)
    }
    @Test fun `claim by a different operation cannot deliver`() = runTest {
        val s = Scenario(); s.wrongOwner = true; val repo = s.repository(); repo.refresh()
        s.delivery(repo).candidate(s.event())
        assertTrue(s.shown.isEmpty())
        assertNotNull(repo.state.value.snapshot!!.checkIn!!.question)
    }
    @Test fun `offline candidate persists question but reconnect cannot notify old candidate`() = runTest {
        val s = Scenario(); val repo = s.repository(); repo.refresh(); s.offline = true
        s.delivery(repo).candidate(s.event())
        assertNotNull(repo.state.value.snapshot!!.checkIn!!.question)
        s.offline = false
        val restored = s.repository(); restored.refresh()
        s.delivery(restored).candidate(s.event())
        assertTrue(s.shown.isEmpty())
    }
    @Test fun `notification denial leaves question pending without delivery`() = runTest {
        val s = Scenario(); s.allowed = false; val repo = s.repository(); repo.refresh()
        s.delivery(repo).candidate(s.event()); s.allowed = true
        s.delivery(repo).candidate(s.event())
        assertTrue(s.shown.isEmpty()); assertNotNull(repo.state.value.snapshot!!.checkIn!!.question)
    }
    @Test fun `OS failure after persisted attempt does not escalate after restart`() = runTest {
        val s = Scenario(); s.crashOnShow = true; val repo = s.repository(); repo.refresh()
        try { s.delivery(repo).candidate(s.event()); fail() } catch (_: IllegalStateException) { }
        s.crashOnShow = false; s.delivery(s.repository()).candidate(s.event())
        assertEquals(listOf("a:0"), s.shown)
    }
    @Test fun `failed attempt storage prevents OS delivery`() = runTest {
        val s = Scenario(); s.attempts.fail = true; val repo = s.repository(); repo.refresh()
        try { s.delivery(repo).candidate(s.event()); fail() } catch (_: IllegalStateException) { }
        assertTrue(s.shown.isEmpty())
    }
    @Test fun `retry after consumed eligibility can recover pending question without escalating`() = runTest {
        val s = Scenario(); val repo = s.repository(); repo.refresh()
        s.delivery(repo).candidate(s.event(), notificationEligible = false)
        assertNotNull(repo.state.value.snapshot!!.checkIn!!.question)
        assertTrue(s.shown.isEmpty())
    }
}
