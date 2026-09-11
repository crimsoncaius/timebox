package com.timebox.android.checkin

import com.timebox.android.data.parseActivityInstant

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.remote.CheckInEventDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** Queries the real OS history. No timers or absence of Timebox input establish inactivity. */
enum class DetectionAccess { Unknown, Unsupported, Disabled, Denied, Granted }

class AndroidCheckIns(private val context: Context, private val repository: ActivityRepository) {
    private val lock = Mutex()
    private val storage = context.getSharedPreferences("activity-screen-evidence-v1", Context.MODE_PRIVATE)
    private val notifier = CheckInNotifier(context)
    private val delivery = CheckInDelivery(repository, notifier, object : com.timebox.android.data.ActivityStorage {
        override fun load() = storage.getString("attempt", null)
        override fun save(value: String) { check(storage.edit().putString("attempt", value).commit()) }
    })
    private val mutableStatus = MutableStateFlow("Checking device detection…")
    val status = mutableStatus.asStateFlow()
    private val mutableAccess = MutableStateFlow(DetectionAccess.Unknown)
    val access = mutableAccess.asStateFlow()
    private val mutableOpenQuestion = MutableStateFlow<String?>(null)
    val openQuestion = mutableOpenQuestion.asStateFlow()

    fun usageGranted(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    private fun reset(now: Long, reason: String) {
        check(storage.edit().putLong("floor", now).putString("boundary", null).commit())
        mutableStatus.value = reason
    }

    suspend fun open(id: String) = withContext(Dispatchers.IO) {
        repository.refresh()
        if (repository.state.value.snapshot?.checkIn?.question?.id == id) {
            repository.reopenCheckIn(id)
            mutableOpenQuestion.value = id
        }
        notifier.cancel()
    }

    fun consumeOpen() { mutableOpenQuestion.value = null }
    fun reconcileNotification(questionId: String?) {
        if (questionId != storage.getString("attempt", null)) notifier.cancel()
    }
    suspend fun settingsChanged() = withContext(Dispatchers.IO) {
        lock.withLock { reset(System.currentTimeMillis(), "Observation restarted after changing detection settings.") }; tick()
    }

    suspend fun tick() = withContext(Dispatchers.IO) { lock.withLock {
        try {
            repository.refresh()
            val snapshot = repository.state.value.snapshot
            reconcileNotification(snapshot?.checkIn?.question?.id)
            val wall = System.currentTimeMillis()
            if (!ScreenEvidence.supported(Build.VERSION.SDK_INT)) {
                mutableAccess.value = DetectionAccess.Unsupported
                reset(wall, "Detection unavailable on Android 8. Screen history requires Android 9 or later.")
                return@withLock
            }
            if (!repository.checkInPreferences().enabled) {
                mutableAccess.value = DetectionAccess.Disabled
                reset(wall, "Detection is off on this device. Recording continues.")
                notifier.cancel()
                return@withLock
            }
            if (!usageGranted()) {
                mutableAccess.value = DetectionAccess.Denied
                reset(wall, "Usage access is not allowed or was revoked. Enable usage access to detect screen-off intervals. Recording continues.")
                return@withLock
            }
            mutableAccess.value = DetectionAccess.Granted
            if (!context.getSystemService(UserManager::class.java).isUserUnlocked) {
                reset(wall, "Screen history unavailable until the first unlock after reboot.")
                return@withLock
            }
            // A persisted clock/boot boundary permits process-death recovery without spanning reboot.
            val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            val clockOrigin = wall - SystemClock.elapsedRealtime()
            if (boot < 0 || storage.getInt("boot", -2) != boot || kotlin.math.abs(clockOrigin - storage.getLong("clockOrigin", 0)) > 5000) {
                reset(wall, "Observation restarted after a reboot or clock change; earlier coverage is unknown.")
                check(storage.edit().putInt("boot", boot).putLong("clockOrigin", clockOrigin).commit())
            }
            val shared = snapshot?.checkIn
            val question = shared?.question
            if (question == null) notifier.cancel()
            if (snapshot?.current == null || shared == null) {
                reset(wall, "Detection is ready when Activity Tracking is running.")
                return@withLock
            }
            val boundary = "${shared.generation}:${shared.rearm}"
            if (storage.getString("boundary", null) != boundary) {
                check(storage.edit().putString("boundary", boundary).putLong("boundaryAt", wall).commit())
            }
            mutableStatus.value = "Approximate detection: Android screen-off intervals only, not lack of touch while the screen is on. Missing history is unknown. Checks may be delayed by battery policy or until you return."
            val floor = maxOf(storage.getLong("floor", wall), wall - 9 * 60 * 60_000L)
            val history = context.getSystemService(UsageStatsManager::class.java).queryEvents(floor, wall)
            if (history == null) {
                reset(wall, "Android screen history is unavailable. Coverage is unknown; recording continues.")
                return@withLock
            }
            val events = mutableListOf<ScreenObservation>()
            val event = UsageEvents.Event()
            while (history.hasNextEvent()) {
                history.getNextEvent(event)
                if (event.timeStamp !in floor..wall) continue
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> events += ScreenObservation(event.timeStamp, false)
                    UsageEvents.Event.SCREEN_INTERACTIVE, UsageEvents.Event.KEYGUARD_HIDDEN,
                    UsageEvents.Event.USER_INTERACTION -> events += ScreenObservation(event.timeStamp, true)
                    UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> events.clear()
                }
            }
            val interval = ScreenEvidence.interval(events, wall, context.getSystemService(PowerManager::class.java).isInteractive,
                storage.getLong("boundaryAt", storage.getLong("floor", wall)))
            val offset = repository.now().toEpochMilli() - wall
            val start = interval?.first?.plus(offset)
            val end = interval?.second?.plus(offset)
            val armed = shared.armedAt?.let { parseActivityInstant(it).toEpochMilli() } ?: Long.MAX_VALUE
            val active = shared.activeAt?.let { parseActivityInstant(it).toEpochMilli() } ?: Long.MIN_VALUE
            if (question == null && start != null && end != null && end - maxOf(start, armed, active) >= repository.checkInPreferences().thresholdMinutes * 60_000L) {
                // Mark this locally produced candidate consumed BEFORE transport. Crash/offline replay loses
                // optional notification eligibility rather than escalating a question discovered on reconnect.
                val candidate = "$boundary:${interval.first}"
                if (storage.getString("candidate", null) == candidate) return@withLock
                check(storage.edit().putString("candidate", candidate).commit())
                delivery.candidate(CheckInEventDto("candidate", shared.generation, shared.rearm,
                    capability = "approximate", permission = "granted", observed = "idle",
                    coverageStart = Instant.ofEpochMilli(start).toString(), coverageEnd = Instant.ofEpochMilli(end).toString()))
            } else {
                // Only positive device-use events are synchronized. Their absence says nothing.
                val lastActive = events.lastOrNull { it.interactive }?.at?.plus(offset)
                if (lastActive != null && lastActive > active) repository.checkIn(CheckInEventDto("observe", shared.generation, shared.rearm,
                    capability = "approximate", permission = "granted", observed = "active",
                    coverageStart = Instant.ofEpochMilli(lastActive).toString(), coverageEnd = Instant.ofEpochMilli(lastActive).toString()))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            val reason = "Detection could not read or save observations. Coverage is unknown; recording remains available."
            runCatching { reset(System.currentTimeMillis(), reason) }
            mutableStatus.value = reason
        }
    } }
}
