package com.timebox.android.ui.assistant

import com.timebox.android.data.remote.ActivitySnapshotDto
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.ui.day.RecordRef
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TrackingProposalTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-25T12:50:00Z")
    private val work = TaskTypeDto(1, "Work")
    private val meals = TaskTypeDto(2, "Meals")
    private val deep = ActualBlockDto(1, 1, work, startAt = "2026-09-25T09:00:00Z", endAt = "2026-09-25T11:30:00Z", name = "Deep work", createdAt = "", updatedAt = "")
    private val timebox = deep.copy(id = 2, name = "Timebox", startAt = "2026-09-25T11:30:00Z", endAt = null)

    private fun snapshot(vararg records: ActualBlockDto) = ActivitySnapshotDto(cursor = 1, serverAt = now.toString(), reportingTimezone = "UTC",
        current = records.find { it.endAt == null }, records = records.toList(), taskTypes = listOf(work, meals),
        switchHistoryReady = true, startHistoryReady = true)

    private fun track(vararg types: TaskTypeDto, at: String? = null, name: String? = null) = TrackingProposal("p", false,
        types.map { ProposalTaskType(it.id, it.name) }, name, at?.let(Instant::parse), now, now.plusSeconds(900))

    @Test fun parsesServerProposalAndRejectsMalformedOnes() {
        val wire = buildJsonObject {
            put("schema_version", 1); put("proposal_id", "p1"); put("action", "track")
            putJsonArray("task_types") { addJsonObject { put("id", 2); put("path", "Meals") } }
            put("block_name", JsonNull); put("at", "2026-09-25T12:40:00Z")
            put("proposed_at", "2026-09-25T12:50:00Z"); put("expires_at", "2026-09-25T13:05:00Z"); put("reporting_timezone", "UTC")
        }
        val parsed = TrackingProposal.parse(wire)
        assertEquals(listOf(ProposalTaskType(2, "Meals")), parsed.taskTypes)
        assertEquals(Instant.parse("2026-09-25T12:40:00Z"), parsed.at)
        assertThrows(Exception::class.java) { TrackingProposal.parse(JsonObject(wire + ("action" to JsonPrimitive("write")))) }
        assertThrows(Exception::class.java) { TrackingProposal.parse(JsonObject(wire + ("task_types" to JsonArray(emptyList())))) }
    }

    @Test fun trackIsDecidedAtConfirmationAsSwitchOrStart() {
        assertEquals(ProposalAction.Switch, deriveProposal(track(meals), ProposalState(), snapshot(deep, timebox), now, zone).action)
        val stopped = timebox.copy(endAt = "2026-09-25T12:10:00Z")
        assertEquals(ProposalAction.Start, deriveProposal(track(meals), ProposalState(), snapshot(deep, stopped), now, zone).action)
    }

    @Test fun switchFromNowNamesTheRunningActivityAndShowsNoStrip() {
        val view = deriveProposal(track(meals), ProposalState(), snapshot(deep, timebox), now, zone)
        assertEquals(listOf("Timebox · Work ends at 12:50"), view.impact)
        assertFalse(view.reachesBack)
    }

    @Test fun earlierStartReplacesHistoryFillsGapAndShowsStrip() {
        val stopped = timebox.copy(endAt = "2026-09-25T12:10:00Z")
        val view = deriveProposal(track(meals, at = "2026-09-25T12:00:00Z"), ProposalState(), snapshot(deep, stopped), now, zone)
        assertEquals(listOf("Timebox · Work ends at 12:00", "40 min of unrecorded time filled"), view.impact)
        assertTrue(view.reachesBack)
    }

    @Test fun invalidStatesExplainThemselves() {
        val running = snapshot(deep, timebox)
        val same = track(work, name = "Timebox")
        assertEquals("You're already tracking Timebox · Work.", deriveProposal(same, ProposalState(), running, now, zone).invalid)
        val earlier = track(work, name = "Timebox", at = "2026-09-25T11:00:00Z")
        assertNull("moving the start earlier is a switch", deriveProposal(earlier, ProposalState(), running, now, zone).invalid)
        val stop = TrackingProposal("s", true, emptyList(), null, Instant.parse("2026-09-25T11:00:00Z"), now, now.plusSeconds(900))
        assertEquals("Timebox · Work started at 11:30, after 11:00.", deriveProposal(stop, ProposalState(), running, now, zone).invalid)
        assertEquals("Nothing is being tracked.", deriveProposal(stop, ProposalState(), snapshot(deep), now, zone).invalid)
    }

    @Test fun choicesWaitForAPickBeforeShowingImpact() {
        val choices = track(work, meals)
        assertNull(deriveProposal(choices, ProposalState(), snapshot(deep, timebox), now, zone).title)
        assertEquals("Meals", deriveProposal(choices, ProposalState(chosen = 2), snapshot(deep, timebox), now, zone).title)
    }

    @Test fun appliedCardFollowsItsBlockThenFreezes() {
        val created = timebox.copy(id = 9, name = "Lunch", startAt = "2026-09-25T12:40:00Z")
        val ref = RecordRef("op", Instant.parse("2026-09-25T12:40:00Z"))
        val live = snapshot(deep, timebox.copy(endAt = "2026-09-25T12:40:00Z"), created).copy(provenance = mapOf("9" to "op"))
        val state = ProposalState(status = ProposalStatus.Applied, record = ref)
        assertEquals("Tracking since 12:40 · 10m", appliedLine(state, live, now, zone).first)
        val frozen = live.copy(records = live.records.map { if (it.id == 9) it.copy(endAt = "2026-09-25T13:00:00Z") else it })
        assertEquals("12:40–13:00 · 20m", appliedLine(state, frozen, now, zone).first)
        assertEquals("Applied · since replaced", appliedLine(state, snapshot(deep), now, zone).first)
    }
}
