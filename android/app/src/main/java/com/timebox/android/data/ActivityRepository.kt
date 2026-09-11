package com.timebox.android.data

import android.content.Context
import com.timebox.android.data.remote.ActivityCalibrationDto
import com.timebox.android.data.remote.ActivityCommandDto
import com.timebox.android.data.remote.ActivityEffectiveDto
import com.timebox.android.data.remote.ActivityKind
import com.timebox.android.data.remote.ActivityOutcome
import com.timebox.android.data.remote.ActivitySnapshotDto
import com.timebox.android.data.remote.ApiFactory
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

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
interface ActivityStorage {
    fun load(): String?
    fun save(value: String)
}
class AndroidActivityStorage(context: Context) : ActivityStorage {
    private val preferences = context.getSharedPreferences("activity-online-v1", Context.MODE_PRIVATE)
    override fun load() = preferences.getString("journal", null)
    override fun save(value: String) {
        check(preferences.edit().putString("journal", value).commit()) { "Activity storage failed. Change has not been confirmed." }
    }
}
@Serializable private data class ActivityJournal(
    val device: String = UUID.randomUUID().toString(), val sequence: Int = 0,
    val lastAction: Long = 0, val endpoint: String? = null,
    val pending: ActivityCommandDto? = null, val snapshot: ActivitySnapshotDto? = null,
    val rejected: ActivityCommandDto? = null,
)
data class ActivityUiState(
    val snapshot: ActivitySnapshotDto? = null, val pending: Boolean = false,
    val busy: Boolean = false, val error: String? = null,
)

/** A durable online receipt/retry boundary, independent of navigation and Work Mode. */
class ActivityRepository(private val transport: ActivityTransport, private val storage: ActivityStorage) {
    private val mutex = Mutex()
    private var journal = ActivityJournal()
    private val mutableState = MutableStateFlow(ActivityUiState())
    val state = mutableState.asStateFlow()
    private var serverAnchor: Long? = null
    private var monotonicAnchor = 0L
    private var calibration: ActivityCalibrationDto? = null
    private var storageError: String? = null
    init {
        try {
            storage.load()?.let { journal = ApiFactory.json.decodeFromString<ActivityJournal>(it) }
            publish()
        } catch (error: Exception) {
            storageError = "Activity storage unavailable: ${error.message}"
            publish(storageError)
        }
    }
    private fun publish(error: String? = null, busy: Boolean = false) {
        mutableState.value = ActivityUiState(journal.snapshot, journal.pending != null, busy, error)
    }
    private fun save(value: ActivityJournal) {
        storage.save(ApiFactory.json.encodeToString(value))
        journal = value
    }
    private suspend fun checkEndpoint() {
        check(storageError == null) { storageError!! }
        val endpoint = transport.endpoint()
        check(journal.endpoint == null || journal.endpoint == endpoint) { "Return to the original activity server to recover this device's recording." }
        if (journal.endpoint == null) save(journal.copy(endpoint = endpoint))
    }
    private fun accept(snapshot: ActivitySnapshotDto) {
        check(snapshot.protocol == "activity-online-v1") { "Incompatible activity server" }
        if (snapshot.cursor >= (journal.snapshot?.cursor ?: -1)) save(journal.copy(snapshot = snapshot))
    }
    suspend fun refresh() = mutex.withLock {
        try {
            checkEndpoint()
            val snapshot = transport.read()
            accept(snapshot)
            serverAnchor = Instant.parse(snapshot.serverAt).toEpochMilli()
            monotonicAnchor = System.nanoTime()
            calibration = ActivityCalibrationDto(snapshot.serverAt, serverAnchor!! - System.currentTimeMillis())
            publish(if (journal.pending != null) "Change not confirmed. Retry to check the saved result." else null)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { publish(error.message ?: "Could not refresh activity") }
    }
    fun now(): Instant = serverAnchor?.let { Instant.ofEpochMilli(it + (System.nanoTime() - monotonicAnchor) / 1_000_000) } ?: Instant.now()
    suspend fun command(kind: ActivityKind, taskTypeId: Int? = null, name: String? = null, retryOnly: Boolean = false): Boolean = mutex.withLock {
        publish(busy = true)
        try {
            checkEndpoint()
            if (retryOnly && journal.pending == null) { publish(); return@withLock false }
            check(retryOnly || journal.pending == null) { "Retry the unconfirmed change before recording another activity" }
            if (journal.pending == null) {
                val snapshot = checkNotNull(journal.snapshot) { "Refresh before recording" }
                val server = checkNotNull(serverAnchor) { "Refresh before recording" }
                val action = maxOf(journal.lastAction, server + (System.nanoTime() - monotonicAnchor) / 1_000_000)
                val sequence = journal.sequence + 1
                val pending = ActivityCommandDto(UUID.randomUUID().toString(), journal.device, sequence,
                    Instant.ofEpochMilli(action).toString(), checkNotNull(calibration), snapshot.cursor,
                    ActivityEffectiveDto("server_now"), snapshot.current?.id, kind, taskTypeId, name?.trim()?.ifEmpty { null })
                save(journal.copy(sequence = sequence, lastAction = action, pending = pending))
            }
            val pending = checkNotNull(journal.pending)
            val response = try { transport.execute(pending) } catch (error: retrofit2.HttpException) {
                if (error.code() == 422) save(journal.copy(rejected = pending, pending = null))
                throw error
            }
            val ack = checkNotNull(response.acknowledgement) { "Activity acknowledgement missing" }
            check(ack.operationId == pending.operationId) { "Activity acknowledgement does not match" }
            accept(response)
            save(journal.copy(pending = null))
            publish(if (ack.outcome == ActivityOutcome.Conflict) "Activity changed on another device. Review before trying again." else null)
            ack.outcome == ActivityOutcome.Applied
        } catch (cancelled: CancellationException) { publish("Change not confirmed. Retry to check the saved result."); throw cancelled
        } catch (error: Exception) { publish(error.message ?: "Change not confirmed"); false }
    }
    suspend fun retry() = command(journal.pending?.kind ?: ActivityKind.Start, retryOnly = true)
}
