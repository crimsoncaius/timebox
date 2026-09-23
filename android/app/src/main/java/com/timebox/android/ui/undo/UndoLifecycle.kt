package com.timebox.android.ui.undo

import android.os.SystemClock
import com.timebox.android.data.apiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UndoPhase { Ready, Running, Failed, Unavailable, Expiring }

data class UndoNotice(
    val id: Long,
    val context: String,
    val title: String,
    val message: String,
    val detail: String? = null,
    val targetId: Int? = null,
    val phase: UndoPhase = UndoPhase.Ready,
    val error: String? = null,
)

/** Single in-memory Undo slot for Trash, Task Completion, and Recording. */
class UndoLifecycle(
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _notice = MutableStateFlow<UndoNotice?>(null)
    val notice = _notice.asStateFlow()
    private val _lateErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val lateErrors = _lateErrors.asSharedFlow()
    private var nextId = 1L
    private var activeContext: String? = null
    private var visible = false
    private var timeoutMillis = 10_000L
    private var exposedMillis = 0L
    private var startedAt: Long? = null
    private var expiry: Job? = null
    private var operation: (suspend () -> Result<Unit>)? = null
    private var onRelease: (suspend () -> Unit)? = null

    fun offer(
        context: String,
        title: String,
        message: String,
        detail: String? = null,
        targetId: Int? = null,
        undo: suspend () -> Result<Unit>,
        release: suspend () -> Unit = {},
    ): Long {
        if (activeContext != null && activeContext != context) return -1L
        clear()
        val id = nextId++
        operation = undo
        onRelease = release
        _notice.value = UndoNotice(id, context, title, message, detail, targetId)
        schedule()
        return id
    }

    fun setExposure(context: String?, resumed: Boolean, recommendedTimeoutMillis: Long) {
        pause()
        if (_notice.value?.context != context) clear()
        activeContext = context
        visible = resumed
        timeoutMillis = maxOf(10_000L, recommendedTimeoutMillis)
        schedule()
    }

    fun dismiss(id: Long? = _notice.value?.id) {
        if (_notice.value?.id == id) clear()
    }

    fun finishExpiry(id: Long) {
        if (_notice.value?.id == id && _notice.value?.phase == UndoPhase.Expiring) clear()
    }

    fun undo(id: Long) {
        val current = _notice.value ?: return
        if (current.id != id || current.phase !in setOf(UndoPhase.Ready, UndoPhase.Failed)) return
        val request = operation ?: return
        pause()
        _notice.value = current.copy(phase = UndoPhase.Running, error = null)
        scope.launch {
            request().fold(
                onSuccess = { if (_notice.value?.id == id) clear() },
                onFailure = { cause ->
                    val status = cause.apiError.statusCode
                    val unavailable = status == 404 || status == 409 || status == 410
                    val message = if (unavailable) "Undo is no longer available for ${current.title}. ${cause.apiError.message}"
                        else cause.apiError.message
                    if (_notice.value?.id == id) {
                        _notice.update { it?.copy(phase = if (unavailable) UndoPhase.Unavailable else UndoPhase.Failed, error = message) }
                    } else {
                        _lateErrors.tryEmit("${current.title}: $message")
                    }
                },
            )
        }
    }

    private fun pause() {
        expiry?.cancel()
        expiry = null
        startedAt?.let { exposedMillis += (elapsedRealtime() - it).coerceAtLeast(0L) }
        startedAt = null
    }

    private fun schedule() {
        val current = _notice.value ?: return
        if (!visible || current.context != activeContext || current.phase != UndoPhase.Ready) return
        val remaining = (timeoutMillis - exposedMillis).coerceAtLeast(0L)
        startedAt = elapsedRealtime()
        expiry = scope.launch {
            delay(remaining)
            if (_notice.value?.id == current.id && _notice.value?.phase == UndoPhase.Ready) {
                startedAt = null
                exposedMillis = timeoutMillis
                _notice.update { it?.copy(phase = UndoPhase.Expiring) }
            }
        }
    }

    private fun clear() {
        pause()
        val released = onRelease
        onRelease = null
        if (released != null) scope.launch { released() }
        operation = null
        exposedMillis = 0L
        _notice.value = null
    }
}
