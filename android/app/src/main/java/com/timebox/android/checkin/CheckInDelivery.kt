package com.timebox.android.checkin

import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.ActivityStorage
import com.timebox.android.data.remote.CheckInEventDto

interface CheckInNotificationSink {
    fun allowed(): Boolean
    fun show(question: String)
}

/** Only this synchronous candidate/ack/claim path can request an optional OS notification. */
class CheckInDelivery(
    private val repository: ActivityRepository,
    private val notifier: CheckInNotificationSink,
    private val attempts: ActivityStorage,
) {
    suspend fun candidate(event: CheckInEventDto, notificationEligible: Boolean = true) {
        require(event.action == "candidate")
        val result = repository.submitCheckIn(event)
        val pending = repository.state.value.snapshot?.checkIn?.question ?: return
        if (!notificationEligible || !result.acknowledged || pending.candidateDevice != result.deviceId ||
            pending.candidateOperationId != result.operationId || !notifier.allowed() || attempts.load() == pending.id) return
        val claim = repository.submitCheckIn(CheckInEventDto("delivery", event.generation, event.rearm, questionId = pending.id))
        val current = repository.state.value.snapshot?.checkIn?.question ?: return
        if (claim.acknowledged && current.id == pending.id && current.delivery?.operationId == claim.operationId && current.delivery?.deviceId == claim.deviceId) {
            attempts.save(pending.id) // Crash after this point may lose delivery, never repeat it.
            notifier.show(pending.id)
        }
    }
}
