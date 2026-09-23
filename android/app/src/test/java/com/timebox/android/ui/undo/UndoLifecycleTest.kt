package com.timebox.android.ui.undo

import com.timebox.android.data.ApiError
import com.timebox.android.data.ApiErrorException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UndoLifecycleTest {
    @Test fun `visible exposure pauses in background and new offer consumes old one`() = runTest {
        var elapsed = 0L
        val lifecycle = UndoLifecycle(backgroundScope) { elapsed }
        lifecycle.setExposure("battle-plan", true, 10_000)
        val first = lifecycle.offer("battle-plan", "First", "First trashed", undo = { Result.success(Unit) })
        advanceTimeBy(4_000); elapsed += 4_000
        lifecycle.setExposure("battle-plan", false, 10_000)
        advanceTimeBy(20_000); elapsed += 20_000
        assertEquals(first, lifecycle.notice.value?.id)
        lifecycle.setExposure("battle-plan", true, 10_000)
        advanceTimeBy(6_000); elapsed += 6_000; runCurrent()
        assertEquals(UndoPhase.Expiring, lifecycle.notice.value?.phase)
        lifecycle.finishExpiry(first)
        assertNull(lifecycle.notice.value)

        val second = lifecycle.offer("battle-plan", "Second", "Second completed", undo = { Result.success(Unit) })
        val third = lifecycle.offer("battle-plan", "Third", "Third recorded", undo = { Result.success(Unit) })
        lifecycle.dismiss(second)
        assertEquals(third, lifecycle.notice.value?.id)
    }

    @Test fun `late failure leaves newer undo in place and identifies the failed target`() = runTest {
        val deferred = CompletableDeferred<Result<Unit>>()
        val lifecycle = UndoLifecycle(backgroundScope) { 0L }
        lifecycle.setExposure("battle-plan", true, 10_000)
        val old = lifecycle.offer("battle-plan", "First", "First trashed", undo = { deferred.await() })
        val late = mutableListOf<String>()
        backgroundScope.launch { lifecycle.lateErrors.collect { late += it } }
        runCurrent()
        lifecycle.undo(old)
        runCurrent()
        val next = lifecycle.offer("battle-plan", "Second", "Second completed", undo = { Result.success(Unit) })
        deferred.complete(Result.failure(ApiErrorException(ApiError("Restore failed"))))
        runCurrent()
        assertEquals(next, lifecycle.notice.value?.id)
        assertEquals("Second completed", lifecycle.notice.value?.message)
        assertEquals(listOf("First: Restore failed"), late)
    }
}
