package com.timebox.android.quicktracking

import android.content.Context
import android.os.Build
import com.timebox.android.BuildConfig
import com.timebox.android.data.*
import com.timebox.android.data.remote.ApiFactory
import com.timebox.android.ui.assistant.HttpAssistantTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

data class QuickCaptureState(val text: String = "", val busy: Boolean = false, val message: String? = null, val saved: Boolean = false, val recorded: Boolean = false, val failureId: String? = null)

/** Save before submission. Uploading history and diagnostics never executes a command. */
class QuickTrackingController(context: Context, private val repository: TimeboxRepository, private val activity: ActivityRepository,
    private val transport: (AppSettings) -> com.timebox.android.ui.assistant.AssistantTransport = { HttpAssistantTransport(it) }) {
    private val applicationContext = context.applicationContext
    private val preferences = context.getSharedPreferences("quick-tracking-failures", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("quick-tracking-draft", Context.MODE_PRIVATE)
    private val json = ApiFactory.json
    private val installationFile = java.io.File(context.noBackupFilesDir, "quick-tracking-installation")
    private val deviceId = if (installationFile.exists()) installationFile.readText() else UUID.randomUUID().toString().also { installationFile.writeText(it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val loaded = runCatching { json.decodeFromString<List<QuickFailure>>(preferences.getString("entries", "[]")!!) }
    val historyUnavailable = loaded.isFailure
    private val mutableFailures = MutableStateFlow(loaded.getOrDefault(emptyList()))
    val failures = mutableFailures.asStateFlow()
    private val mutableState = MutableStateFlow(QuickCaptureState())
    val state = mutableState.asStateFlow()
    private val mutablePreview = MutableStateFlow<QuickRetryPreview?>(null)
    val preview = mutablePreview.asStateFlow()
    private val mutableEndpoint = MutableStateFlow("")
    val endpoint = mutableEndpoint.asStateFlow()
    private var active: String? = null
    private var replacement: String? = null
    private var syncing = false
    fun close() { scope.cancel() }

    init {
        scope.launch {
            repository.settings.collect { settings ->
                mutableEndpoint.value = settings.assistantIdentity()
                if (legacy.contains("text")) {
                    save(QuickFailure(endpoint = legacy.getString("endpoint", "").orEmpty(), deviceId = deviceId,
                        command = legacy.getString("text", null), occurredAt = Instant.EPOCH.toString(),
                        requestIds = listOfNotNull(legacy.getString("request", null)), reason = "An earlier command needs checking. Its original time is unavailable; use Day to correct it."))
                    legacy.edit().clear().commit()
                }
            }
        }
        scope.launch {
            while (isActive) {
                try { synchronize() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Retain the queue. */ }
                delay(15_000)
            }
        }
    }

    private fun save(record: QuickFailure) {
        check(!historyUnavailable) { "Saved command history could not be read. It has been retained on this device; check Day before repeating commands." }
        val next = failures.value.filterNot { it.id == record.id } + record
        check(preferences.edit().putString("entries", json.encodeToString(next)).commit()) { "Could not retain your command on this device." }
        mutableFailures.value = next
        if (record.uploadedRevision < record.revision || record.diagnostics.isNotEmpty() || (record.dismissed && !record.dismissalUploaded)) {
            androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork("quick-tracking-diagnostics",
                androidx.work.ExistingWorkPolicy.KEEP,
                androidx.work.OneTimeWorkRequestBuilder<QuickFailureUploadWorker>().setConstraints(
                    androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build())
        }
    }
    private fun entry(id: String) = failures.value.first { it.id == id }
    fun owns(record: QuickFailure) = record.deviceId == deviceId && record.endpoint == endpoint.value
    private fun update(id: String, change: (QuickFailure) -> QuickFailure) {
        val old = entry(id); save(change(old).copy(revision = old.revision + 1))
    }
    fun edit(text: String) { if (!state.value.busy) mutableState.value = QuickCaptureState(text) }
    fun cancelPreview() { mutablePreview.value = null }
    fun replaceCurrent(): Boolean {
        val id = state.value.failureId ?: return true
        return try { speakAgain(id); true } catch (_: Exception) {
            mutableState.value = state.value.copy(message = "Check whether this command recorded in Review before speaking again.")
            false
        }
    }
    fun speakAgain(id: String) {
        val old = entry(id)
        check(owns(old) && old.status == "failed" && old.requestIds.none(activity::hasQuickOperation)) { "Check this command in Review before speaking again." }
        replacement = id
    }
    fun dismiss(id: String) { update(id) { it.copy(dismissed = true) }; refresh() }
    fun refresh() { scope.launch { try { synchronize() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { } } }

    private suspend fun create(text: String?, source: String): QuickFailure {
        val identity = repository.settings.first().assistantIdentity()
        val parent = replacement?.let(::entry)
        check(parent == null || parent.endpoint == identity) { "Return to the original server to recover this command." }
        replacement = null
        return if (parent == null) QuickFailure(endpoint = identity, deviceId = deviceId, command = text, source = source, occurredAt = activity.now().toString())
        else parent.copy(command = text, source = source, occurredAt = activity.now().toString(), request = null, result = null,
            status = "uncertain", dismissed = false, dismissalUploaded = false, revision = parent.revision + 1,
            history = parent.history + QuickFailureEvent(parent.occurredAt, parent.command, parent.reason))
    }

    fun captureFailed(reason: String, source: String, error: Throwable = IllegalStateException("Speech capture failed")) {
        scope.launch {
            try {
                val record = create(null, source).copy(status = "failed", reason = reason, stage = "capture")
                save(record.copy(diagnostics = record.diagnostics + diagnostic(error, "capture", null)))
                mutableState.value = QuickCaptureState(message = reason, failureId = record.id)
            } catch (_: Exception) { mutableState.value = QuickCaptureState(message = "Could not save the capture failure on this device.") }
        }
    }
    private fun diagnostic(error: Throwable, stage: String, requestId: String?) =
        quickDiagnostic(error, stage, requestId, BuildConfig.VERSION_NAME, Build.VERSION.RELEASE)

    private fun run(id: String?, text: String = "", work: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = QuickCaptureState(text, busy = true)
        active = id
        scope.launch {
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutablePreview.value = null
                try { fail(active, error) } catch (_: Exception) { mutableState.value = QuickCaptureState(message = "Could not retain this failure on the device. Check Day before repeating the command.") }
            }
            finally { active = null }
        }
    }
    fun submit(text: String, source: String) {
        if (text.isBlank()) return
        run(null, text) {
            val record = create(text, source); save(record); active = record.id
            val (result, fingerprint) = interpret(record.id)
            if (result.action == null) noAction(record.id, result) else apply(record.id, result, fingerprint)
        }
    }

    private suspend fun interpret(id: String): Pair<QuickInterpretation, String> {
        val settings = repository.settings.first()
        val record = entry(id)
        check(record.endpoint == settings.assistantIdentity()) { "Return to the original server to recover this command." }
        if (!activity.bootstrappedThisRun) activity.refresh()
        checkNotNull(activity.state.value.snapshot) { "Connect once to initialize Activity Tracking, then retry." }
        val fingerprint = activity.quickFingerprint()
        record.result?.let { return it to fingerprint }
        val request = record.request ?: buildJsonObject {
            put("request_id", UUID.randomUUID().toString()); put("attempt_id", id)
            put("device_id", deviceId)
            put("message", record.command); put("source", record.source); put("reference_at", record.occurredAt)
            activity.state.value.snapshot?.current?.let { current ->
                putJsonObject("current") { put("task_type_id", current.taskTypeId); put("name", current.name) }
            }
        }
        val requestId = request.getValue("request_id").jsonPrimitive.content
        update(id) { it.copy(request = request, requestIds = (it.requestIds + requestId).distinct(), stage = "interpretation", status = "uncertain", reason = "Checking command") }
        val result = transport(settings).interpretQuick(request)
        check(repository.settings.first().assistantIdentity() == record.endpoint) { "Return to the original server to recover this command." }
        check(result.requestId == requestId) { "The interpretation did not match this command." }
        update(id) { it.copy(result = result) }
        return result to fingerprint
    }

    private fun noAction(id: String, result: QuickInterpretation) {
        val information = result.message == "No tracking change requested."
        update(id) { it.copy(status = if (information) "information" else "failed", reason = result.message, retryable = false) }
        mutableState.value = QuickCaptureState(entry(id).command.orEmpty(), message = result.message, failureId = if (information) null else id)
    }
    private suspend fun apply(id: String, result: QuickInterpretation, fingerprint: String) {
        check(repository.settings.first().assistantIdentity() == entry(id).endpoint) { "Return to the original server to recover this command." }
        update(id) { it.copy(stage = "recording", status = "uncertain", reason = "Checking whether this was recorded", retryable = false) }
        val message = activity.applyQuick(result, fingerprint)
        val recorded = activity.hasQuickOperation(result.requestId)
        val pending = recorded && activity.state.value.quickReviews.any { it.interpretation.requestId == result.requestId }
        update(id) { it.copy(status = if (pending) "uncertain" else if (recorded) "recorded" else "information",
            reason = if (pending) "Saved on this device. Waiting to check recording." else message, stage = if (pending) "sync" else it.stage, retryable = false) }
        mutableState.value = QuickCaptureState(entry(id).command.orEmpty(), message = message, saved = true, recorded = recorded)
        mutablePreview.value = null
    }
    private fun fail(id: String?, error: Exception) {
        val reason = error.message ?: "The command could not finish. Try again."
        if (id != null) {
            val old = entry(id)
            val hasOperation = old.requestIds.any(activity::hasQuickOperation)
            val readable = if (hasOperation) "Saved on this device. Waiting to check recording." else reason
            update(id) { it.copy(status = if (hasOperation) "uncertain" else "failed", reason = readable,
                retryable = !hasOperation && it.command != null, diagnostics = it.diagnostics + diagnostic(error, it.stage, it.requestIds.lastOrNull()),
                history = it.history + QuickFailureEvent(Instant.now().toString(), it.command, readable)) }
        }
        mutableState.value = QuickCaptureState(id?.let { entry(it).command }.orEmpty(), message = reason, failureId = id)
    }

    fun retry(id: String) = run(id, entry(id).command.orEmpty()) {
        val record = entry(id)
        check(record.deviceId == deviceId) { "Retry is only available on the originating installation. Use Day to correct the activity." }
        check(record.endpoint == repository.settings.first().assistantIdentity()) { "Return to the original server to recover this command." }
        activity.reconcileTask()
        val shared = transport(repository.settings.first()).reviewRequest("quick-failures", record.report())
        if (record.requestIds.any(activity::hasQuickOperation) || shared["status"]?.jsonPrimitive?.content == "recorded") {
            update(id) { it.copy(status = "recorded", reason = "Already saved. Check the recorded activity.", retryable = false) }
            mutableState.value = QuickCaptureState(message = "Already saved. Check Assistant Review.")
        } else {
            check(!record.expired(now = activity.now(), timezone = activity.state.value.snapshot!!.reportingTimezone)) { "This command is too old to retry. Correct the activity in Day." }
            check(record.command != null && record.retryable) { "Speak again to replace this command." }
            val snapshotBefore = checkNotNull(activity.state.value.snapshot)
            val resultInvalid = record.result?.let { it.timezone != snapshotBefore.reportingTimezone ||
                (it.action == "track" && snapshotBefore.taskTypes.none { type -> type.id == it.taskTypeId }) } ?: true
            if (resultInvalid) update(id) { it.copy(request = null, result = null) }
            val (result, fingerprint) = interpret(id)
            if (result.action == null) noAction(id, result) else {
                val snapshot = checkNotNull(activity.state.value.snapshot)
                quickTrackingKind(result, snapshot, activity.now())
                mutablePreview.value = QuickRetryPreview(id, result, fingerprint,
                    if (result.action == "stop") "Stop tracking" else "Track ${snapshot.taskTypes.first { it.id == result.taskTypeId }.name}")
                update(id) { it.copy(status = "failed", reason = "Ready to retry. Confirm the activity and time.", retryable = true) }
                mutableState.value = QuickCaptureState(record.command, message = "Confirm retry in Review.")
            }
        }
    }
    fun confirmRetry() {
        val proposed = preview.value ?: return
        run(proposed.failureId) {
            check(entry(proposed.failureId).endpoint == repository.settings.first().assistantIdentity()) { "Return to the original server to recover this command." }
            activity.reconcileTask()
            if (activity.quickFingerprint() != proposed.fingerprint) {
                mutablePreview.value = null
                update(proposed.failureId) { it.copy(status = "failed", reason = "Activity changed. Review a fresh retry preview.", retryable = true) }
                mutableState.value = QuickCaptureState(message = "Activity changed. Retry again to review the updated result.")
            } else apply(proposed.failureId, proposed.result, proposed.fingerprint)
        }
    }

    suspend fun synchronize() {
        if (syncing) throw java.io.IOException("Command history is already synchronizing")
        syncing = true
        try {
            val cutoff = Instant.now().minusSeconds(30L * 24 * 3600)
            for (record in failures.value) {
                val retained = record.diagnostics.filter { Instant.parse(it.getValue("occurred_at").jsonPrimitive.content) > cutoff }
                if (retained.size != record.diagnostics.size) save(record.copy(diagnostics = retained))
            }
            val settings = repository.settings.first()
            val transport = transport(settings)
            val candidates = failures.value.filter { it.deviceId == deviceId && it.endpoint == settings.assistantIdentity() && it.id != active &&
                (it.uploadedRevision < it.revision || it.diagnostics.isNotEmpty() || (it.dismissed && !it.dismissalUploaded) ||
                    it.status == "uncertain" || (it.status == "failed" && !it.dismissed)) }
            if (candidates.any { it.status == "uncertain" || (it.status == "failed" && it.requestIds.any(activity::hasQuickOperation)) }) activity.refresh()
            for (candidate in candidates) {
                if (repository.settings.first().assistantIdentity() != settings.assistantIdentity()) break
                if (candidate.id == active) continue
                var record = entry(candidate.id)
                val operation = record.requestIds.firstOrNull(activity::hasQuickOperation)
                if (operation != null) {
                    val local = activity.state.value.quickReviews.find { it.interpretation.requestId == operation }
                    val status = if (local?.rejected == true) "failed" else if (local != null) "uncertain" else "recorded"
                    val reason = if (local?.rejected == true) "Could not sync. Open Day for recovery; repeating the command cannot fix the conflict." else if (local != null) "Saved on this device. Waiting to check recording." else "Recorded. Open the activity in Day."
                    if (record.status != status || record.reason != reason) update(record.id) { it.copy(status = status, reason = reason, retryable = false,
                        stage = "sync", diagnostics = if (status == "failed") it.diagnostics + diagnostic(IllegalStateException("Activity sync rejected"), "sync", operation) else it.diagnostics) }
                } else if (record.status == "uncertain" && activity.bootstrappedThisRun && !activity.state.value.offline && activity.state.value.error == null) {
                    update(record.id) { it.copy(status = "failed", reason = "The previous attempt stopped before recording.", retryable = it.command != null) }
                }
                record = entry(record.id)
                if (record.uploadedRevision < record.revision) {
                    val response = transport.reviewRequest("quick-failures", record.report())
                    val latest = entry(record.id)
                    save(latest.copy(uploadedRevision = record.revision, dismissed = latest.dismissed || response["dismissed"]?.jsonPrimitive?.boolean == true,
                        reviewId = response["review_id"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int))
                }
                if (record.id != active && record.uploadedRevision >= record.revision) {
                    val shared = transport.reviewRequest("quick-failures/${record.id}")
                    val latest = entry(record.id)
                    save(latest.copy(dismissed = latest.dismissed || shared["dismissed"]?.jsonPrimitive?.boolean == true,
                        reviewId = shared["review_id"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int))
                }
                record = entry(record.id)
                if (record.dismissed && !record.dismissalUploaded) {
                    transport.reviewRequest("quick-failures/${record.id}/dismiss", buildJsonObject {})
                    save(entry(record.id).copy(dismissalUploaded = true))
                }
                for (event in record.diagnostics) {
                    transport.reviewRequest("quick-failures/${record.id}/diagnostics", event)
                    save(entry(record.id).copy(diagnostics = entry(record.id).diagnostics.filterNot { it["id"] == event["id"] }))
                }
            }
        } finally { syncing = false }
    }
}
