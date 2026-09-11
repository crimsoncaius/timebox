package com.timebox.android.data

import android.content.Context
import com.timebox.android.data.remote.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class CheckInPreferences(val enabled: Boolean = true, val thresholdMinutes: Int = 60)
data class CheckInSubmission(val saved: Boolean, val operationId: String? = null, val deviceId: String? = null, val acknowledged: Boolean = false)

interface ActivityTransport {
    suspend fun read(): ActivitySnapshotDto
    suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto
    suspend fun endpoint(): String = "test"
}
class RepositoryActivityTransport(private val repository: TimeboxRepository) : ActivityTransport {
    override suspend fun read() = repository.getActivity()
    override suspend fun execute(command: ActivityCommandDto) = repository.activityCommand(command)
    override suspend fun endpoint() = repository.activityEndpoint()
}
interface ActivityStorage { fun load(): String?; fun save(value: String) }
class AndroidActivityStorage(context: Context) : ActivityStorage {
    private val preferences = context.getSharedPreferences("activity-online-v1", Context.MODE_PRIVATE)
    override fun load() = preferences.getString("journal", null)
    override fun save(value: String) {
        check(preferences.edit().putString("journal", value).commit()) { "Activity storage failed. Change was not saved on this device." }
    }
}
@Serializable private data class ActivityJournal(
    val device: String = UUID.randomUUID().toString(), val sequence: Int = 0,
    val checkInPreferences: CheckInPreferences = CheckInPreferences(),
    val dismissedQuestions: Set<String> = emptySet(),
    val lastAction: Long = 0, val endpoint: String? = null,
    val pending: ActivityCommandDto? = null, val snapshot: ActivitySnapshotDto? = null,
    val rejected: ActivityCommandDto? = null,
    val outbox: List<ActivityCommandDto> = emptyList(),
    val rejectedOutbox: List<ActivityCommandDto> = emptyList(),
    val calibration: ActivityCalibrationDto? = null,
)
data class ActivityUiState(
    val snapshot: ActivitySnapshotDto? = null, val pending: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val offline: Boolean = false,
    val feedback: String? = null,
    val checkInPreferences: CheckInPreferences = CheckInPreferences(),
)
/** Confirmed history plus durable local intent. Acknowledgements never restamp intent. */
class ActivityRepository(private val transport: ActivityTransport, private val storage: ActivityStorage,
                         private val wallTime: () -> Long = System::currentTimeMillis,
                         private val monotonicTime: () -> Long = System::nanoTime) {
    private val mutex = Mutex()
    private var journal = ActivityJournal()
    private val mutableState = MutableStateFlow(ActivityUiState())
    val state = mutableState.asStateFlow()
    private var serverAnchor: Long? = null
    private var monotonicAnchor = 0L
    private var storageError: String? = null
    private var offline = false
    private var feedback: String? = null
    init {
        try {
            storage.load()?.let { journal = ApiFactory.json.decodeFromString<ActivityJournal>(it) }
            journal.pending?.let { journal = journal.copy(outbox = journal.outbox + it, pending = null) }
            publish()
        } catch (error: Exception) { storageError = "Activity storage unavailable: ${error.message}"; publish(storageError) }
    }
    private fun project(): ActivitySnapshotDto? {
        var snapshot = journal.snapshot ?: return null
        journal.outbox.forEach { command ->
            var checkIn = snapshot.checkIn ?: return@forEach
            val event = command.checkIn
            if (event?.action == "observe" && event.observed == "active" && event.generation == checkIn.generation && event.capability in listOf("supported", "approximate") && event.permission == "granted" && event.coverageStart != null && event.coverageEnd != null && parseActivityInstant(event.coverageStart) <= parseActivityInstant(event.coverageEnd) && parseActivityInstant(event.coverageEnd) <= now().plusSeconds(5)) {
                checkIn = checkIn.copy(activeAt = maxOf(parseActivityInstant(event.coverageEnd), checkIn.activeAt?.let(::parseActivityInstant) ?: Instant.MIN).toString())
                snapshot = snapshot.copy(checkIn = checkIn)
            }
            if (event?.action == "candidate" && event.generation == checkIn.generation && event.rearm == checkIn.rearm && checkIn.question == null && event.enabled != false && event.capability in listOf("supported", "approximate") && event.permission == "granted" && event.observed in listOf("idle", "locked") && event.coverageStart != null && event.coverageEnd != null && parseActivityInstant(event.coverageEnd) <= now().plusSeconds(5) && checkIn.armedAt != null) {
                val start = maxOf(parseActivityInstant(event.coverageStart), parseActivityInstant(checkIn.armedAt), checkIn.activeAt?.let(::parseActivityInstant) ?: Instant.MIN)
                if (java.time.Duration.between(start, parseActivityInstant(event.coverageEnd)).toMinutes() >= (event.thresholdMinutes ?: 60))
                    snapshot = snapshot.copy(checkIn = checkIn.copy(question = CheckInQuestionDto("${checkIn.generation}:${checkIn.rearm}", command.actionAt, journal.device, command.operationId)))
            }
            if (event?.action == "confirm" && event.questionId == checkIn.question?.id)
                snapshot = snapshot.copy(checkIn = checkIn.copy(question = null, rearm = checkIn.rearm + 1, armedAt = command.actionAt))
            if (command.kind in listOf(ActivityKind.Start, ActivityKind.Switch, ActivityKind.Stop)) snapshot = snapshot.copy(checkIn = checkIn.copy(question = null, generation = "pending:${command.operationId}", rearm = 0, armedAt = command.actionAt))
        }
        return if (journal.outbox.isNotEmpty()) projectRanges(snapshot.copy(serverAt = now().toString())) else snapshot
    }
    private data class Order(val at: Instant, val device: String, val sequence: Int, val id: String) : Comparable<Order> {
        override fun compareTo(other: Order) = compareValuesBy(this, other, { it.at }, { it.device }, { it.sequence }, { it.id })
    }
    private data class Piece(val start: Instant, val end: Instant, val row: ActualBlockDto?, val order: Order)
    private fun projectRanges(snapshot: ActivitySnapshotDto): ActivitySnapshotDto {
        val provenance = snapshot.provenance.toMutableMap()
        val coverage = snapshot.coverage.ifEmpty { snapshot.records.map { ActivityCoverageDto(it.startAt, it.endAt, it.id, listOf(kotlinx.serialization.json.JsonPrimitive(""), kotlinx.serialization.json.JsonPrimitive(""), kotlinx.serialization.json.JsonPrimitive(0), kotlinx.serialization.json.JsonPrimitive("baseline"))) } }
        var pieces = coverage.map { p -> Piece(parseActivityInstant(p.start), p.end?.let(::parseActivityInstant) ?: Instant.MAX,
            snapshot.records.find { it.id == p.recordId }, Order(p.order[0].content.takeIf { it.isNotEmpty() }?.let(::parseActivityInstant) ?: Instant.MIN,
                p.order[1].content, p.order[2].content.toInt(), p.order[3].content)) }
        journal.outbox.forEach { command ->
            if (command.kind == ActivityKind.CheckIn || command.effective.mode == "server_now") return@forEach
            val at = checkNotNull(command.effective.at)
            val start = parseActivityInstant(at)
            val end = command.effective.end?.let(::parseActivityInstant) ?: Instant.MAX
            val target = pieces.find { p -> p.row != null && command.targetSource != null && (provenance[p.row.id.toString()] ?: "baseline:${p.row.id}") == command.targetSource && p.start == command.targetStartAt?.let(::parseActivityInstant) }?.row
                ?: pieces.find { it.row?.id == command.targetId }?.row
            val order = Order(parseActivityInstant(command.actionAt), command.deviceId, command.sequence, command.operationId)
            val type = snapshot.taskTypes.find { it.id == command.taskTypeId } ?: TaskTypeDto(command.taskTypeId ?: 0, "unspecified")
            val row = if (command.kind == ActivityKind.Stop || command.kind == ActivityKind.Delete) null else ActualBlockDto(
                if (command.kind in listOf(ActivityKind.Edit, ActivityKind.Describe)) target?.id ?: command.targetId!! else -command.sequence, type.id, type,
                startAt = at, endAt = command.effective.end, createdAt = target?.createdAt ?: at, updatedAt = command.actionAt,
                name = command.name, taskId = command.taskId, task = target?.task, note = command.note,
                plannedBlockId = if (command.kind in listOf(ActivityKind.Edit, ActivityKind.Describe) && target?.taskTypeId == command.taskTypeId && target?.taskId == command.taskId) target?.plannedBlockId else command.plannedBlockId)
            if (row != null) provenance[row.id.toString()] = if (command.kind in listOf(ActivityKind.Edit, ActivityKind.Describe)) command.targetSource!! else command.operationId
            val oldStart = target?.startAt?.let(::parseActivityInstant) ?: start
            val oldEnd = target?.endAt?.let(::parseActivityInstant) ?: end
            val boundaries = (pieces.flatMap { listOf(it.start, it.end) } + start + end + Instant.MAX).distinct().sorted()
            pieces = boundaries.zipWithNext().mapNotNull { (a, b) ->
                val previous = pieces.find { it.start <= a && it.end > a }
                val inRange = a >= start && a < end
                val removed = command.kind == ActivityKind.Edit && a >= oldStart && a < oldEnd
                if ((inRange || removed) && (previous == null || order > previous.order)) Piece(a, b, if (inRange) row else null, order)
                else previous?.copy(start = a, end = b)
            }
        }
        val merged = mutableListOf<Piece>()
        pieces.forEach { piece ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.end == piece.start && previous.row?.id == piece.row?.id && previous.order == piece.order)
                merged[merged.lastIndex] = previous.copy(end = piece.end)
            else merged += piece
        }
        val records = merged.mapNotNull { p -> p.row?.copy(startAt = p.start.toString(), endAt = if (p.end == Instant.MAX) null else p.end.toString()) }
        return snapshot.copy(records = records, current = records.find { it.endAt == null }, provenance = provenance)
    }
    private fun publish(error: String? = null, busy: Boolean = false) {
        mutableState.value = ActivityUiState(project(), journal.outbox.isNotEmpty(), busy, error, offline, feedback, journal.checkInPreferences)
    }
    private fun save(value: ActivityJournal) {
        try { storage.save(ApiFactory.json.encodeToString(value)) }
        catch (error: Exception) { throw IllegalStateException("Activity storage failed. Change was not saved on this device.", error) }
        journal = value
    }
    private suspend fun checkEndpoint() {
        check(storageError == null) { storageError!! }
        val endpoint = transport.endpoint()
        check(journal.endpoint == null || journal.endpoint == endpoint) { "Return to the original activity server to recover this device's recording." }
        if (journal.endpoint == null) save(journal.copy(endpoint = endpoint))
    }
    private fun newer(snapshot: ActivitySnapshotDto): Boolean {
        check(snapshot.protocol == "activity-online-v1") { "Incompatible activity server" }
        val previous = journal.snapshot ?: return true
        return snapshot.cursor > previous.cursor || (snapshot.cursor == previous.cursor && parseActivityInstant(snapshot.serverAt) >= parseActivityInstant(previous.serverAt))
    }
    suspend fun dismissFeedback() = mutex.withLock { feedback = null; publish(state.value.error) }
    private fun noteReconciliation(snapshot: ActivitySnapshotDto) {
        if (newer(snapshot) && snapshot.operationOutcomes.any { (id, result) ->
            result.deviceId == journal.device && result.outcome == ActivityOutcome.Superseded &&
                journal.snapshot?.operationOutcomes?.get(id)?.outcome != ActivityOutcome.Superseded
        }) feedback = "A newer change on another device updated this time."
    }
    private suspend fun drain() {
        while (journal.outbox.isNotEmpty()) {
            val command = journal.outbox.first()
            val response = try { transport.execute(command) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (error is retrofit2.HttpException && error.code() == 422) {
                    save(journal.copy(rejectedOutbox = journal.outbox, outbox = emptyList()))
                } else offline = true
                throw error
            }
            val ack = checkNotNull(response.acknowledgement) { "Activity acknowledgement missing" }
            check(ack.operationId == command.operationId) { "Activity acknowledgement does not match" }
            val applied = ack.outcome == ActivityOutcome.Applied || ack.outcome == ActivityOutcome.Superseded
            noteReconciliation(response)
            save(journal.copy(snapshot = if (newer(response)) response else journal.snapshot,
                outbox = if (applied) journal.outbox.drop(1) else emptyList(),
                rejectedOutbox = if (applied) journal.rejectedOutbox else journal.outbox))
            offline = false
            check(applied) { "Activity changed on another device. Pending changes were retained for review." }
        }
    }
    fun checkInPreferences() = journal.checkInPreferences
    suspend fun setCheckInPreferences(preferences: CheckInPreferences) = mutex.withLock {
        try {
            require(preferences.thresholdMinutes in 15..480) { "Choose 15 to 480 minutes." }
            save(journal.copy(checkInPreferences = preferences)); publish()
        } catch (error: Exception) { publish(error.message) }
    }
    fun checkInDismissed(id: String) = id in journal.dismissedQuestions
    suspend fun dismissCheckIn(id: String) = mutex.withLock {
        try { save(journal.copy(dismissedQuestions = journal.dismissedQuestions + id)); publish() }
        catch (error: Exception) { publish(error.message) }
    }
    suspend fun checkIn(event: CheckInEventDto): Boolean = submitCheckIn(event).saved
    suspend fun submitCheckIn(event: CheckInEventDto): CheckInSubmission = mutex.withLock {
        var saved = false
        var operationId: String? = null
        var acknowledged = false
        try {
            checkEndpoint()
            val snapshot = checkNotNull(project())
            val prompt = checkNotNull(snapshot.checkIn)
            val calibration = checkNotNull(journal.calibration)
            val millis = maxOf(now().toEpochMilli(), journal.lastAction + 1)
            val at = Instant.ofEpochMilli(millis).toString()
            val command = ActivityCommandDto(UUID.randomUUID().toString(), journal.device, journal.sequence + 1,
                at, calibration, snapshot.cursor, ActivityEffectiveDto("instant", at), snapshot.current?.id,
                ActivityKind.CheckIn, checkIn = event.copy(enabled = if (event.action == "candidate") journal.checkInPreferences.enabled else event.enabled, thresholdMinutes = if (event.action == "candidate") journal.checkInPreferences.thresholdMinutes else event.thresholdMinutes, generation = event.generation ?: prompt.generation, rearm = if (event.generation == null) prompt.rearm else event.rearm))
            save(journal.copy(sequence = command.sequence, lastAction = millis, outbox = journal.outbox + command))
            operationId = command.operationId
            saved = true; publish(); drain(); publish()
            acknowledged = !offline && journal.outbox.none { it.operationId == operationId }
        } catch (cancelled: CancellationException) { publish(); throw cancelled }
        catch (error: Exception) { publish(error.message) }
        CheckInSubmission(saved, operationId, journal.device, acknowledged)
    }
    suspend fun reopenCheckIn(id: String) = mutex.withLock {
        if (project()?.checkIn?.question?.id == id) {
            save(journal.copy(dismissedQuestions = journal.dismissedQuestions - id)); publish()
        }
    }
    suspend fun refresh() = mutex.withLock {
        try {
            checkEndpoint()
            drain()
            val snapshot = try { transport.read() } catch (error: Exception) { offline = true; throw error }
            offline = false
            if (newer(snapshot)) {
                noteReconciliation(snapshot)
                val server = parseActivityInstant(snapshot.serverAt).toEpochMilli()
                save(journal.copy(snapshot = snapshot, calibration = ActivityCalibrationDto(snapshot.serverAt, server - wallTime())))
                serverAnchor = server
                monotonicAnchor = monotonicTime()
            }
            publish()
        } catch (cancelled: CancellationException) { publish(); throw cancelled }
        catch (error: Exception) { publish(error.message ?: "Could not refresh activity") }
    }
    fun now(): Instant = serverAnchor?.let { Instant.ofEpochMilli(it + (monotonicTime() - monotonicAnchor) / 1_000_000) }
        ?: Instant.ofEpochMilli(wallTime() + (journal.calibration?.offsetMs ?: 0))
    suspend fun command(kind: ActivityKind, taskTypeId: Int? = null, name: String? = null, retryOnly: Boolean = false, taskId: Int? = null, plan: ActivityPlanDto? = null, effectiveAt: Instant? = null, observedTargetId: Int? = null, onPersisted: () -> Unit = {}): Boolean {
        if (retryOnly) { refresh(); return !state.value.pending && state.value.error == null }
        val requestedAt = now().toEpochMilli()
        val selectedPlan = plan ?: if (kind == ActivityKind.Start && taskId == null && taskTypeId == null && name == null) currentPlan() else null
        val selectedType = selectedPlan?.taskTypeId ?: taskTypeId
        val selectedName = selectedPlan?.name ?: name
        val observed = journal
        val observedCurrent = project()?.current
        val saved = mutex.withLock {
            try {
                checkEndpoint()
                val snapshot = checkNotNull(journal.snapshot) { "Connect once to initialize Activity Tracking before recording offline." }
                check(snapshot.offlineReady) { "Connect once to an updated server to initialize Activity Tracking before recording offline." }
                val calibration = checkNotNull(journal.calibration) { "Connect once to initialize Activity Tracking before recording offline." }
                check(journal.outbox.none { it.effective.mode == "server_now" }) { "Reconnect to confirm the previous online change first." }
                val current = project()?.current
                check((kind == ActivityKind.Start) != (current != null)) { "Activity changed. Review the current activity." }
                check(kind !in listOf(ActivityKind.Switch, ActivityKind.Describe) || selectedType != null || taskId != null) { "Task Type is required" }
                check(kind != ActivityKind.Describe || (current?.name.isNullOrBlank() && current?.taskType?.name == "unspecified")) { "Only an unknown Current Activity can be described" }
                val latest = project()?.records?.maxOfOrNull { parseActivityInstant(it.endAt ?: it.startAt).toEpochMilli() } ?: 0L
                val action = maxOf(journal.lastAction + 1, latest + 1, requestedAt)
                val at = Instant.ofEpochMilli(action).toString()
                if (observedTargetId != null) check(current != null && observedTargetId == current.id && (effectiveAt == null || (effectiveAt >= parseActivityInstant(current.startAt) && effectiveAt.toEpochMilli() <= requestedAt))) { "Choose a time after the current activity started and no later than now. Review the current activity if it changed." }
                val predecessor = journal.outbox.lastOrNull { it.kind in listOf(ActivityKind.Start, ActivityKind.Switch, ActivityKind.Stop) } ?: observed.outbox.lastOrNull { it.kind in listOf(ActivityKind.Start, ActivityKind.Switch, ActivityKind.Stop) }
                val command = ActivityCommandDto(UUID.randomUUID().toString(), journal.device, journal.sequence + 1,
                    at, observed.calibration ?: calibration, observed.snapshot?.cursor ?: snapshot.cursor,
                    ActivityEffectiveDto("instant", if (kind == ActivityKind.Describe) current!!.startAt else effectiveAt?.toString() ?: at), if (predecessor == null) observedCurrent?.id else null,
                    kind, selectedType, selectedName?.trim()?.ifEmpty { null },
                    taskId = if (kind == ActivityKind.Describe) current?.taskId else selectedPlan?.taskId ?: taskId, plannedBlockId = if (kind == ActivityKind.Describe) current?.plannedBlockId else selectedPlan?.id,
                    note = if (kind == ActivityKind.Describe) current?.note else selectedPlan?.note, selectionSnapshot = true, predecessorId = predecessor?.operationId,
                    targetSource = if (kind == ActivityKind.Describe) project()?.provenance?.get(current!!.id.toString()) ?: journal.outbox.find { -it.sequence == current!!.id }?.operationId ?: "baseline:${current!!.id}" else null,
                    targetStartAt = if (kind == ActivityKind.Describe) current!!.startAt else null)
                save(journal.copy(sequence = command.sequence, lastAction = action, outbox = journal.outbox + command))
                publish()
                true
            } catch (error: Exception) { publish(error.message ?: "Could not save activity"); false }
        }
        // The durable projection is already observable while transport is pending.
        if (saved) { onPersisted(); refresh() }
        return saved
    }
    suspend fun correct(kind: ActivityKind, targetId: Int? = null, startAt: String? = null, endAt: String? = null,
                        taskTypeId: Int? = null, name: String? = null, note: String? = null, taskId: Int? = null,
                        clearName: Boolean = false, clearNote: Boolean = false): Boolean {
        val saved = mutex.withLock {
            try {
                checkEndpoint()
                val snapshot = checkNotNull(project()) { "Connect once to initialize Activity Tracking." }
                check(snapshot.offlineReady && journal.calibration != null) { "Connect once to initialize Activity Tracking." }
                check(kind in listOf(ActivityKind.Add, ActivityKind.Edit, ActivityKind.Delete))
                val target = snapshot.records.find { it.id == targetId }
                check(kind == ActivityKind.Add || target?.endAt != null) { "Select an ended Actual Block. Use Switch or Stop for the Current Activity." }
                val start = checkNotNull(if (kind == ActivityKind.Delete) target?.startAt else startAt ?: target?.startAt)
                val end = checkNotNull(if (kind == ActivityKind.Delete) target?.endAt else endAt ?: target?.endAt)
                val a = parseActivityInstant(start); val b = parseActivityInstant(end)
                check(a < b && b <= now()) { "Choose a positive time range ending no later than now." }
                check(kind == ActivityKind.Delete || snapshot.records.none { it.id != targetId && a < (it.endAt?.let(::parseActivityInstant) ?: Instant.MAX) && parseActivityInstant(it.startAt) < b }) { "Activity overlaps recorded time. Adjust the other record first." }
                val action = maxOf(now().toEpochMilli(), journal.lastAction + 1)
                val source = journal.outbox.lastOrNull { it.kind == ActivityKind.Edit && it.targetId == targetId }?.targetSource
                    ?: journal.outbox.find { -it.sequence == targetId }?.operationId ?: snapshot.provenance[targetId.toString()] ?: "baseline:$targetId"
                val command = ActivityCommandDto(UUID.randomUUID().toString(), journal.device, journal.sequence + 1,
                    Instant.ofEpochMilli(action).toString(), journal.calibration!!, journal.snapshot!!.cursor,
                    ActivityEffectiveDto("range", start, end), targetId, kind, taskTypeId ?: target?.taskTypeId,
                    if (clearName) null else name ?: target?.name, taskId = taskId ?: target?.taskId,
                    note = if (clearNote) null else note ?: target?.note, targetSource = if (target != null) source else null,
                    targetStartAt = target?.startAt, clear_fields = listOfNotNull(if (clearName) "name" else null, if (clearNote) "note" else null))
                save(journal.copy(sequence = command.sequence, lastAction = action, outbox = journal.outbox + command)); publish(); true
            } catch (error: Exception) { publish(error.message ?: "Could not save correction"); false }
        }
        if (saved) refresh()
        return saved
    }
    fun currentPlan(): ActivityPlanDto? = state.value.snapshot?.plans?.find { parseActivityInstant(it.startAt) <= now() && now() < parseActivityInstant(it.endAt) }
    suspend fun trackTask(task: BattleTask): Boolean {
        if (task.recurrenceKind == "quota_parent" || task.status == TaskStatus.Completed) return false
        if (state.value.snapshot == null) refresh()
        val type = task.taskTypeId ?: state.value.snapshot?.taskTypes?.find { it.name == "unspecified" }?.id
        val saved = command(if (state.value.snapshot?.current == null) ActivityKind.Start else ActivityKind.Switch, type, task.title, taskId = task.id)
        if (saved) { feedback = "Tracking ${task.title}"; publish(state.value.error) }
        return saved
    }
    suspend fun retry() = refresh()
}
