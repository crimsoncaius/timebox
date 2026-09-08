package com.timebox.android.ui.readiness

import com.timebox.android.data.BattleTask
import com.timebox.android.data.BattleTaskPatch
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.PatchField
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Transport boundary for the app-scoped Ready to Plan coordinator. */
internal interface ReadyToPlanTransport {
    suspend fun setReady(taskId: Int, ready: Boolean): Result<BattleTask>
}

internal class RepositoryReadyToPlanTransport(
    private val repository: TimeboxRepository,
) : ReadyToPlanTransport {
    override suspend fun setReady(taskId: Int, ready: Boolean): Result<BattleTask> =
        repository.patchBattleTask(
            taskId,
            BattleTaskPatch(readyToPlan = PatchField.of(ready)),
        )
}

private data class ReadinessEntry(
    val task: BattleTask,
    val confirmed: Boolean,
    val desired: Boolean,
    val writing: Boolean = false,
    val authoritativeLifecycle: Boolean = false,
) {
    fun projection(): BattleTask = task.copy(
        readyToPlan = desired,
        readinessPending = writing || desired != confirmed,
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
        tasks.flattenTasks().forEach { incoming ->
            val current = entries[incoming.id]
            if (current == null) {
                entries[incoming.id] = ReadinessEntry(incoming, incoming.readyToPlan, incoming.readyToPlan)
            } else if (incoming.version >= current.task.version) {
                entries[incoming.id] = if (current.writing || current.desired != current.confirmed) {
                    current.copy(
                        task = incoming,
                        confirmed = incoming.readyToPlan,
                        authoritativeLifecycle = false,
                    )
                } else {
                    ReadinessEntry(incoming, incoming.readyToPlan, incoming.readyToPlan)
                }
            }
        }
        publish()
    }

    @Synchronized
    fun projectTasks(tasks: List<BattleTask>): List<BattleTask> = tasks.map(::projectTree)

    @Synchronized
    fun projectedTask(taskId: Int): BattleTask? = entries[taskId]?.projection()

    @Synchronized
    fun readyTasks(): List<BattleTask> = entries.values
        .map(ReadinessEntry::projection)
        .filter { task ->
            task.readyToPlan && task.status != TaskStatus.Completed &&
                task.archivedAt == null && task.deletedAt == null
        }

    fun setReady(task: BattleTask, ready: Boolean) {
        var startWorker = false
        synchronized(this) {
            val current = entries[task.id] ?: ReadinessEntry(task, task.readyToPlan, task.readyToPlan)
            if (current.task.status == TaskStatus.Completed) return
            val updated = current.copy(desired = ready)
            startWorker = !updated.writing && updated.desired != updated.confirmed
            entries[task.id] = updated.copy(writing = updated.writing || startWorker)
            publish()
        }
        if (startWorker) scope.launch { persist(task.id) }
    }

    private suspend fun persist(taskId: Int) {
        while (true) {
            val target = synchronized(this) { entries[taskId]?.desired } ?: return
            val result = transport.setReady(taskId, target)
            val continueWriting = synchronized(this) {
                val current = entries[taskId] ?: return@synchronized false
                result.fold(
                    onSuccess = { saved ->
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
                        if (lifecycleRejected) {
                            entries[taskId] = ReadinessEntry(
                                task = newestTask,
                                confirmed = saved.readyToPlan,
                                desired = saved.readyToPlan,
                                authoritativeLifecycle = lifecycleChanged,
                            )
                            false
                        } else {
                            val confirmed = target
                            val keepWriting = current.desired != confirmed
                            entries[taskId] = current.copy(
                                task = newestTask,
                                confirmed = confirmed,
                                writing = keepWriting,
                            )
                            keepWriting
                        }
                    },
                    onFailure = {
                        entries[taskId] = current.copy(
                            desired = current.confirmed,
                            writing = false,
                        )
                        false
                    },
                ).also { publish() }
            }
            if (!continueWriting) return
        }
    }

    private fun projectTree(task: BattleTask): BattleTask {
        val entry = entries[task.id]
        val readiness = entry?.projection()
        var projection = if (readiness == null) {
            task
        } else {
            task.copy(
                readyToPlan = readiness.readyToPlan,
                readinessPending = readiness.readinessPending,
            )
        }
        if (entry?.authoritativeLifecycle == true && entry.task.version >= task.version) {
            projection = projection.copy(
                status = entry.task.status,
                completedAt = entry.task.completedAt,
                archivedAt = entry.task.archivedAt,
                deletedAt = entry.task.deletedAt,
                version = entry.task.version,
                updatedAt = entry.task.updatedAt,
            )
        }
        return projection.copy(sessionTasks = task.sessionTasks.map(::projectTree))
    }

    private fun publish() {
        _projections.value = entries.values.map(ReadinessEntry::projection)
    }
}

private fun List<BattleTask>.flattenTasks(): List<BattleTask> =
    flatMap { task -> listOf(task) + task.sessionTasks.flattenTasks() }

/** One coordinator per app-owned repository, shared by every screen ViewModel. */
internal object ReadyToPlanCoordinators {
    private val coordinators = WeakHashMap<TimeboxRepository, ReadyToPlanCoordinator>()

    @Synchronized
    fun forRepository(repository: TimeboxRepository): ReadyToPlanCoordinator =
        coordinators.getOrPut(repository) {
            ReadyToPlanCoordinator(
                RepositoryReadyToPlanTransport(repository),
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            )
        }
}
