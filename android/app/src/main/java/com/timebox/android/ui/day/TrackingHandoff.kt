package com.timebox.android.ui.day

import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.remote.ActivitySnapshotDto
import com.timebox.android.data.remote.ActualBlockDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

/**
 * Follows one Actual Block across acknowledgement and later changes: its origin (the operation that created it,
 * or its baseline source) and its start. Local and server identities differ; these do not.
 */
data class RecordRef(val source: String, val start: Instant) {
    fun find(snapshot: ActivitySnapshotDto): ActualBlockDto? = snapshot.records.find {
        (snapshot.provenance[it.id.toString()] ?: "baseline:${it.id}") == source && parseActivityInstant(it.startAt) == start
    }

    companion object {
        fun of(record: ActualBlockDto, snapshot: ActivitySnapshotDto) =
            RecordRef(snapshot.provenance[record.id.toString()] ?: "baseline:${record.id}", parseActivityInstant(record.startAt))
    }
}

/** A Start or Switch sheet prefilled elsewhere, such as from a Tracking Proposal's Change. */
data class TrackingSheetRequest(val taskTypeId: Int?, val name: String?, val at: Instant?, val proposalId: String? = null)

/** Day opened on a block reached from elsewhere; it is marked until the first touch in Day. */
data class DayLanding(val record: RecordRef, val label: String, val id: String = UUID.randomUUID().toString())

/** Process-owned handoffs between the Assistant and Day. */
class TrackingHandoff {
    private val sheetRequest = MutableStateFlow<TrackingSheetRequest?>(null)
    val sheet = sheetRequest.asStateFlow()
    private val dayLanding = MutableStateFlow<DayLanding?>(null)
    val landing = dayLanding.asStateFlow()
    private val sheetResults = MutableSharedFlow<Pair<String, RecordRef>>(extraBufferCapacity = 4)
    /** Proposal id and the record its prefilled sheet produced. */
    val applied = sheetResults.asSharedFlow()

    fun requestSheet(request: TrackingSheetRequest) { sheetRequest.value = request }
    fun consumeSheet(): TrackingSheetRequest? = sheetRequest.value.also { sheetRequest.value = null }
    fun reportApplied(proposalId: String, record: RecordRef) { sheetResults.tryEmit(proposalId to record) }
    fun land(landing: DayLanding) { dayLanding.value = landing }
    fun clearLanding(id: String) { if (dayLanding.value?.id == id) dayLanding.value = null }
}
