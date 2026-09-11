package com.timebox.android.ui.focus

import android.content.Context
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.remote.ActivityKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface FocusStorage { fun active(): Boolean; fun wake(): Boolean; fun save(active: Boolean, wake: Boolean) }
class AndroidFocusStorage(context: Context) : FocusStorage {
    private val preferences = context.getSharedPreferences("activity-focus-v1", Context.MODE_PRIVATE)
    override fun active() = preferences.getBoolean("active", false)
    override fun wake() = preferences.getBoolean("wake", true)
    override fun save(active: Boolean, wake: Boolean) { check(preferences.edit().putBoolean("active", active).putBoolean("wake", wake).commit()) }
}
data class FocusState(val active: Boolean = false, val entering: Boolean = false, val wake: Boolean = true, val error: String? = null)
/** Local surface intent is independent of the shared recording journal. */
class FocusController(private val storage: FocusStorage) {
    private val mutable = MutableStateFlow(runCatching { FocusState(active = storage.active(), wake = storage.wake()) }.getOrDefault(FocusState()))
    val state = mutable.asStateFlow()
    private var generation = 0
    private var legacyCancelled = false
    @Synchronized private fun update(next: FocusState) {
        mutable.value = try { storage.save(next.active, next.wake); next } catch (_: Exception) { next.copy(error = "Focus preference could not be saved on this device.") }
    }
    @Synchronized fun exit() { legacyCancelled = true; generation++; update(state.value.copy(active = false, entering = false)) }
    @Synchronized fun setWake(wake: Boolean) = update(state.value.copy(wake = wake))
    fun restoreLegacy(saved: com.timebox.android.data.WorkModeSnapshot?, repository: ActivityRepository, planning: Boolean): Boolean {
        if (!repository.bootstrappedThisRun) return false
        val snapshot = repository.state.value.snapshot ?: return false
        val valid = runCatching {
            saved != null && snapshot.current != null && saved.activeActualId == snapshot.current.id &&
                java.time.Instant.parse(saved.entryAt) <= java.time.Instant.parse(saved.lastConfirmedAt) &&
                java.time.Instant.parse(saved.lastConfirmedAt) <= java.time.Instant.parse(saved.lastObservedAt) &&
                java.time.Instant.parse(saved.lastObservedAt) <= java.time.Instant.parse(snapshot.serverAt).plusSeconds(5)
        }.getOrDefault(false)
        update(state.value.copy(active = valid && !planning && !legacyCancelled, error = null))
        return state.value.error == null
    }
    fun reconcile(repository: ActivityRepository, planning: Boolean) {
        if (planning || (repository.state.value.snapshot != null && repository.state.value.snapshot?.current == null && state.value.active)) exit()
    }
    suspend fun enter(repository: ActivityRepository, planning: () -> Boolean): Boolean {
        legacyCancelled = true
        if (planning() || state.value.entering || repository.state.value.snapshot == null) return false
        val token = synchronized(this) { generation++; update(state.value.copy(entering = true)); generation }
        val started = repository.state.value.snapshot?.current != null || repository.command(ActivityKind.Start, onPersisted = {
            synchronized(this) { if (token == generation && !planning()) update(state.value.copy(active = true, entering = false)) }
        })
        synchronized(this) {
            if (token != generation) return false
            update(state.value.copy(active = started && !planning() && repository.state.value.snapshot?.current != null, entering = false))
            return state.value.active
        }
    }
}
