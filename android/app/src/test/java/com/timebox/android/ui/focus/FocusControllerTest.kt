package com.timebox.android.ui.focus

import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FocusControllerTest {
    class Store : FocusStorage { var value = false; var awake = true; override fun active() = value; override fun wake() = awake; override fun save(active: Boolean, wake: Boolean) { value = active; awake = wake } }
    @Test fun retainedActivityRestoresUntilExitOrRemoteStop() = runTest {
        val at = "2026-09-11T10:00:00Z"
        val row = ActualBlockDto(7, 1, TaskTypeDto(1, "Reading"), startAt = at, createdAt = at, updatedAt = at)
        var snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = at, reportingTimezone = "UTC", current = row, records = listOf(row))
        val repository = ActivityRepository(object : ActivityTransport { override suspend fun read() = snapshot; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Must not start") }, object : ActivityStorage { var text: String? = null; override fun load() = text; override fun save(value: String) { text = value } })
        repository.refresh()
        val store = Store(); val focus = FocusController(store)
        assertFalse(focus.enter(repository) { true })
        assertTrue(focus.enter(repository) { false })
        assertTrue(FocusController(store).state.value.active)
        focus.exit(); assertEquals(row, repository.state.value.snapshot!!.current)
        focus.enter(repository) { false }
        snapshot = snapshot.copy(cursor = 2, current = null)
        repository.refresh(); focus.reconcile(repository, false)
        assertFalse(focus.state.value.active)
        assertFalse(FocusController(store).state.value.active)
    }
    @Test fun exitDuringPendingStartNeverReactivatesFocus() = runTest {
        val at = "2026-09-11T10:00:00Z"
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 0, serverAt = at, reportingTimezone = "UTC", current = null, records = emptyList())
        val gate = CompletableDeferred<Unit>()
        val repository = ActivityRepository(object : ActivityTransport { override suspend fun read() = snapshot; override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto { gate.await(); error("Offline") } }, object : ActivityStorage { var text: String? = null; override fun load() = text; override fun save(value: String) { text = value } })
        repository.refresh(); val focus = FocusController(Store())
        val entry = launch { focus.enter(repository) { false } }
        runCurrent(); focus.exit(); gate.complete(Unit); entry.join()
        assertFalse(focus.state.value.active)
        assertNotNull(repository.state.value.snapshot!!.current)
    }
}
