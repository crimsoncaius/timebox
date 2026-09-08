package com.timebox.android.ui.readiness

import com.timebox.android.data.BattleTask
import com.timebox.android.data.BattleTaskPatch
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.flattenBattleTasks
import com.timebox.android.data.remote.PatchField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Transport boundary for the app-scoped Ready to Plan coordinator. */
internal interface ReadyToPlanTransport {
    suspend fun setReady(taskId: Int, ready: Boolean): Result<BattleTask>
    suspend fun reconcile(taskId: Int): Result<BattleTask?> =
        Result.failure(UnsupportedOperationException("Readiness reconciliation is unavailable"))
}

internal class RepositoryReadyToPlanTransport(
    private val repository: TimeboxRepository,
) : ReadyToPlanTransport {
    override suspend fun setReady(taskId: Int, ready: Boolean): Result<BattleTask> =
        repository.patchBattleTask(
            taskId,
            BattleTaskPatch(readyToPlan = PatchField.of(ready)),
        )

    override suspend fun reconcile(taskId: Int): Result<BattleTask?> =
        repository.listBattleTasks().map { tasks ->
            tasks.items.flattenBattleTasks().firstOrNull { it.id == taskId }
        }
}

private data class ReadinessFailure(val desired: Boolean, val message: String)

private data class ReadinessEntry(
    val task: BattleTask,
    val confirmed: Boolean,
    val desired: Boolean,
    val writing: Boolean = false,
    val failure: ReadinessFailure? = null,
    val removed: Boolean = false,
    val intentVersion: Long = 0,
) {
    fun projection(): BattleTask = task.copy(
        readyToPlan = desired,
        readinessPending = writing || desired != confirmed,
        readinessFailureMessage = failure?.message,
    )
}

/**
 * Owns the latest readiness choice for every Battle Plan Task in this app process.
 * Screens consume projected Tasks; only this coordinator writes readiness.
 */
class ReadyToPlanCoordinator internal constructor(
    private val transport: ReadyToPlanTransport,
    private val scope: CoroutineScope,
) {
    private val entries = mutableMapOf<Int, ReadinessEntry>()
    private val _projections = MutableStateFlow<List<BattleTask>>(emptyList())
    val projections: StateFlow<List<BattleTask>> = _projections.asStateFlow()

    @Synchronized
    fun mergeServerTasks(tasks: List<BattleTask>) {
        tasks.flattenBattleTasks().forEach { incoming ->
            val current = entries[incoming.id]
            if (current == null) {
                entries[incoming.id] = ReadinessEntry(incoming, incoming.readyToPlan, incoming.readyToPlan)
            } else if (incoming.version >= current.task.version) {
                entries[incoming.id] = if (current.writing || current.desired != current.confirmed) {
                    current.copy(
                        task = incoming,
                        confirmed = incoming.readyToPlan,
                        removed = false,
                    )
                } else {
                    current.copy(
                        task = incoming,
                        confirmed = incoming.readyToPlan,
                        desired = incoming.readyToPlan,
                        failure = current.failure?.let { failure ->
                            failure.takeUnless { failure.desired == incoming.readyToPlan }
                        },
                        removed = false,
                    )
                }
            }
        }
        publish()
    }

    @Synchronized
    fun projectTasks(tasks: List<BattleTask>): List<BattleTask> = tasks
        .filterNot { entries[it.id]?.removed == true }
        .map(::projectTree)

    @Synchronized
    fun projectedTask(taskId: Int): BattleTask? = entries[taskId]?.projection()

    @Synchronized
    fun intentVersion(taskId: Int): Long = entries[taskId]?.intentVersion ?: 0

    fun setReady(task: BattleTask, ready: Boolean) {
        submitIntent(task, ready)
    }

    /**
     * Submits the readiness field of a saved multi-field draft at its captured ordering boundary.
     * A choice made after that draft started saving wins and makes this submission a no-op.
     */
    fun setReadyFromDraft(task: BattleTask, ready: Boolean, observedIntentVersion: Long): Boolean {
        return submitIntent(task, ready, observedIntentVersion)
    }

    private fun submitIntent(
        task: BattleTask,
        ready: Boolean,
        expectedIntentVersion: Long? = null,
    ): Boolean {
        var startWorker = false
        synchronized(this) {
            val current = entries[task.id] ?: ReadinessEntry(task, task.readyToPlan, task.readyToPlan)
            if (
                current.task.status == TaskStatus.Completed ||
                expectedIntentVersion != null && current.intentVersion != expectedIntentVersion
            ) {
                return false
            }
            val updated = current.copy(
                desired = ready,
                failure = null,
                intentVersion = current.intentVersion + 1,
            )
            startWorker = !updated.writing && updated.desired != updated.confirmed
            entries[task.id] = updated.copy(writing = updated.writing || startWorker)
            publish()
        }
        if (startWorker) scope.launch { persist(task.id) }
        return true
    }

    fun retry(taskId: Int) {
        var startWorker = false
        synchronized(this) {
            val current = entries[taskId] ?: return
            if (current.task.status == TaskStatus.Completed) return
            val failed = current.failure ?: return
            val updated = current.copy(
                desired = failed.desired,
                failure = null,
                intentVersion = current.intentVersion + 1,
            )
            startWorker = !updated.writing && updated.desired != updated.confirmed
            entries[taskId] = updated.copy(writing = updated.writing || startWorker)
            publish()
        }
        if (startWorker) scope.launch { persist(taskId) }
    }

    private suspend fun persist(taskId: Int) {
        while (true) {
            val target = synchronized(this) { entries[taskId]?.desired } ?: return
            val result = transport.setReady(taskId, target)
            if (result.isFailure) {
                val reconciliation = transport.reconcile(taskId)
                val continueWriting = synchronized(this) {
                    val current = entries[taskId] ?: return@synchronized false
                    val reconciled = reconciliation.getOrNull()
                    var merged = current
                    if (reconciliation.isSuccess) {
                        merged = if (reconciled == null) {
                            current.copy(confirmed = false, removed = true)
                        } else if (reconciled.version >= current.task.version) {
                            current.copy(
                                task = reconciled,
                                confirmed = reconciled.readyToPlan,
                                removed = false,
                            )
                        } else {
                            current
                        }
                    }
                    val latestIntent = merged.desired == target
                    if (merged.task.status == TaskStatus.Completed) {
                        entries[taskId] = merged.copy(
                            desired = merged.confirmed,
                            writing = false,
                            failure = null,
                        )
                        publish()
                        false
                    } else if (!latestIntent) {
                        val keepWriting = merged.desired != merged.confirmed
                        entries[taskId] = merged.copy(writing = keepWriting)
                        publish()
                        keepWriting
                    } else if (reconciliation.isSuccess && merged.confirmed == target && !merged.removed) {
                        entries[taskId] = merged.copy(
                            desired = target,
                            writing = false,
                            failure = null,
                        )
                        publish()
                        false
                    } else {
                        entries[taskId] = merged.copy(
                            desired = merged.confirmed,
                            writing = false,
                            failure = ReadinessFailure(
                                desired = target,
                                message = if (reconciliation.isSuccess) {
                                    "Ready to Plan was not saved. Retry your latest choice."
                                } else {
                                    "Ready to Plan could not be confirmed. Retry your latest choice."
                                },
                            ),
                        )
                        publish()
                        false
                    }
                }
                if (!continueWriting) return
                continue
            }
            val continueWriting = synchronized(this) {
                val current = entries[taskId] ?: return@synchronized false
                val saved = result.getOrThrow()
                if (saved.version < current.task.version) {
                    val latestIntent = current.desired == target
                    val keepWriting = !latestIntent && current.desired != current.confirmed
                    entries[taskId] = when {
                        !latestIntent -> current.copy(writing = keepWriting)
                        current.confirmed == target -> current.copy(
                            desired = target,
                            writing = false,
                            failure = null,
                        )
                        else -> current.copy(
                            desired = current.confirmed,
                            writing = false,
                            failure = ReadinessFailure(
                                desired = target,
                                message = "Ready to Plan was not saved. Retry your latest choice.",
                            ),
                        )
                    }
                    publish()
                    return@synchronized keepWriting
                }
                val newestTask = if (saved.version >= current.task.version) {
                    current.task.copy(
                        readyToPlan = saved.readyToPlan,
                        status = saved.status,
                        completedAt = saved.completedAt,
                        version = saved.version,
                        archivedAt = saved.archivedAt,
                        deletedAt = saved.deletedAt,
                        updatedAt = saved.updatedAt,
                    )
                } else {
                    current.task
                }
                val lifecycleChanged = saved.status != current.task.status ||
                    saved.completedAt != current.task.completedAt ||
                    saved.archivedAt != current.task.archivedAt ||
                    saved.deletedAt != current.task.deletedAt
                val lifecycleRejected = lifecycleChanged || saved.readyToPlan != target
                if (lifecycleRejected && saved.version >= current.task.version) {
                    entries[taskId] = ReadinessEntry(
                        task = newestTask,
                        confirmed = saved.readyToPlan,
                        desired = saved.readyToPlan,
                        intentVersion = current.intentVersion,
                    )
                    publish()
                    false
                } else {
                    val confirmed = target
                    val keepWriting = current.desired != confirmed
                    entries[taskId] = current.copy(
                        task = newestTask,
                        confirmed = confirmed,
                        writing = keepWriting,
                        failure = null,
                    )
                    publish()
                    keepWriting
                }
            }
            if (!continueWriting) return
        }
    }

    private fun projectTree(task: BattleTask): BattleTask {
        val entry = entries[task.id]
        val source = if (entry != null && entry.task.version > task.version) entry.task else task
        val readiness = entry?.projection()
        val projection = if (readiness == null) {
            task
        } else {
            source.copy(
                readyToPlan = readiness.readyToPlan,
                readinessPending = readiness.readinessPending,
                readinessFailureMessage = readiness.readinessFailureMessage,
            )
        }
        return projection.copy(
            sessionTasks = source.sessionTasks
                .filterNot { entries[it.id]?.removed == true }
                .map(::projectTree),
        )
    }

    private fun publish() {
        _projections.value = entries.values.map(ReadinessEntry::projection)
    }
}

internal fun createReadyToPlanCoordinator(
    repository: TimeboxRepository,
    scope: CoroutineScope,
): ReadyToPlanCoordinator = ReadyToPlanCoordinator(
    RepositoryReadyToPlanTransport(repository),
    scope,
)
