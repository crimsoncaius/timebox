package com.timebox.android.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.timebox.android.MainActivity
import com.timebox.android.R
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.remote.ActivityKind
import com.timebox.android.data.remote.ActivitySnapshotDto
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

const val PLANNED_BLOCK_REMINDER_CHANNEL_ID = "planned_block_reminders"
private const val NOTIFICATION_TAG = "planned-block"
private const val ACTION_FIRE = "com.timebox.android.action.PLANNED_BLOCK_REMINDER"
private const val ACTION_SWITCH = "com.timebox.android.action.PLANNED_BLOCK_SWITCH"
private const val EXTRA_KEY = "planned_block_key"
private const val EXTRA_BLOCK_ID = "planned_block_id"
private const val WORK_PREFIX = "timebox.planned-block-reminder."
private const val SYNC_WORK = "timebox.planned-block-reminders.sync"
private const val HANDLED = "handled"
private const val SCHEDULED = "scheduled"
private const val DELIVERED = "delivered"
private val HandledRetention: Duration = Duration.ofDays(2)

fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

fun ActivitySnapshotDto.reminderBlocks(): List<ReminderBlock> = plans.mapNotNull { plan ->
    runCatching {
        ReminderBlock(
            id = plan.id,
            start = parseActivityInstant(plan.startAt),
            end = parseActivityInstant(plan.endAt),
            title = plannedBlockReminderTitle(plan.name, plan.taskTitle, taskTypes.find { it.id == plan.taskTypeId }?.name),
        )
    }.getOrNull()
}

private fun ActivitySnapshotDto.zone(): ZoneId = runCatching { ZoneId.of(reportingTimezone) }.getOrDefault(ZoneId.systemDefault())

/**
 * Owns Planned Block Reminders on this device: schedules them from the Activity Tracking snapshot,
 * delivers them, and silently withdraws them once they no longer apply.
 */
class PlannedBlockReminders(
    private val context: Context,
    private val activity: ActivityRepository,
    private val settings: Flow<PlannedBlockReminderSettings>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val store = context.getSharedPreferences("planned_block_reminders", Context.MODE_PRIVATE)
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val workManager get() = WorkManager.getInstance(context)
    private val notifications get() = NotificationManagerCompat.from(context)
    private var syncEnabled: Boolean? = null

    fun launch(block: suspend PlannedBlockReminders.() -> Unit) = scope.launch { block() }

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLANNED_BLOCK_REMINDER_CHANNEL_ID,
                "Planned Block reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "A reminder before each Planned Block starts" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    suspend fun reconcile(now: Instant = Instant.now()) = mutex.withLock {
        val current = settings.first()
        val snapshot = activity.state.value.snapshot
        val blocks = snapshot?.reminderBlocks().orEmpty()
        val currentPlan = snapshot?.current?.plannedBlockId
        val withdrawn = plannedBlockRemindersToWithdraw(read(DELIVERED), blocks, currentPlan, now)
        withdrawn.forEach { key -> plannedBlockIdOf(key)?.let { notifications.cancel(NOTIFICATION_TAG, it) } }
        val previous = read(SCHEDULED)
        val plan = planPlannedBlockReminders(blocks, current, read(HANDLED), previous, now)
        val next = plan.alarms.map { it.key }.toSet()
        (previous - next).mapNotNull(::plannedBlockIdOf).forEach(::cancelAlarm)
        val exact = canScheduleExactAlarms(context)
        plan.alarms.forEach { schedule(it, exact, now) }
        write(HANDLED, prune(read(HANDLED) + plan.skipped, now))
        write(SCHEDULED, next)
        write(DELIVERED, read(DELIVERED) - withdrawn)
        updateSyncWork(current.enabled)
    }

    suspend fun fire(key: String, now: Instant = Instant.now()) {
        mutex.withLock {
            write(SCHEDULED, read(SCHEDULED) - key)
            if (!settings.first().enabled) return@withLock
            val snapshot = activity.state.value.snapshot ?: return@withLock
            val block = snapshot.reminderBlocks().find { it.id == plannedBlockIdOf(key) }
            when (decidePlannedBlockReminder(key, block, snapshot.current?.plannedBlockId, now)) {
                PlannedBlockReminderDecision.Deliver -> {
                    // A reminder that cannot be shown is missed, not retried.
                    if (show(block!!, now, snapshot.zone())) write(DELIVERED, read(DELIVERED) + key)
                    write(HANDLED, read(HANDLED) + key)
                }
                PlannedBlockReminderDecision.AlreadyTracking, PlannedBlockReminderDecision.Ended ->
                    write(HANDLED, read(HANDLED) + key)
                PlannedBlockReminderDecision.Stale -> Unit
            }
        }
        reconcile(now)
    }

    /** Adopts the Planned Block exactly like the in-app suggestion. Works offline through the local journal. */
    suspend fun switchTo(blockId: Int, now: Instant = Instant.now()) {
        notifications.cancel(NOTIFICATION_TAG, blockId)
        mutex.withLock { write(DELIVERED, read(DELIVERED).filterTo(mutableSetOf()) { plannedBlockIdOf(it) != blockId }) }
        val snapshot = activity.state.value.snapshot ?: return
        val plan = snapshot.plans.find { it.id == blockId } ?: return
        if (now >= parseActivityInstant(plan.endAt) || snapshot.current?.plannedBlockId == blockId) return
        val kind = if (snapshot.current == null) ActivityKind.Start else ActivityKind.Switch
        if (!activity.command(kind, plan = plan)) {
            snapshot.reminderBlocks().find { it.id == blockId }?.let { showSwitchFailed(it, snapshot.zone()) }
        }
    }

    private fun schedule(alarm: PlannedBlockAlarm, exact: Boolean, now: Instant) {
        if (exact) {
            workManager.cancelUniqueWork(WORK_PREFIX + alarm.blockId)
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.fireAt.toEpochMilli(), fireIntent(alarm.blockId, alarm.key))
                return
            } catch (_: SecurityException) {
                // Exact alarm access was revoked between the check and the call; fall back below.
            }
        }
        alarmManager.cancel(fireIntent(alarm.blockId, null))
        val request = OneTimeWorkRequestBuilder<PlannedBlockReminderWorker>()
            .setInputData(Data.Builder().putString(EXTRA_KEY, alarm.key).build())
            .setInitialDelay(Duration.between(now, alarm.fireAt).coerceAtLeast(Duration.ZERO))
            .build()
        workManager.enqueueUniqueWork(WORK_PREFIX + alarm.blockId, ExistingWorkPolicy.REPLACE, request)
    }

    private fun cancelAlarm(blockId: Int) {
        alarmManager.cancel(fireIntent(blockId, null))
        workManager.cancelUniqueWork(WORK_PREFIX + blockId)
    }

    private fun updateSyncWork(enabled: Boolean) {
        if (syncEnabled == enabled) return
        syncEnabled = enabled
        if (!enabled) {
            workManager.cancelUniqueWork(SYNC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<PlannedBlockReminderSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun fireIntent(blockId: Int, key: String?): PendingIntent = PendingIntent.getBroadcast(
        context,
        blockId,
        Intent(context, PlannedBlockReminderReceiver::class.java).setAction(ACTION_FIRE).apply { key?.let { putExtra(EXTRA_KEY, it) } },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && notifications.areNotificationsEnabled()
    }

    private fun builder(block: ReminderBlock, zone: ZoneId): NotificationCompat.Builder {
        val date = block.start.atZone(zone).toLocalDate()
        val open = PendingIntent.getActivity(
            context,
            block.id,
            Intent(Intent.ACTION_VIEW, Uri.parse("timebox://day/$date?blockId=${block.id}"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, PLANNED_BLOCK_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(block.title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    }

    @SuppressLint("MissingPermission") // canNotify checks the runtime grant immediately before notify.
    private fun show(block: ReminderBlock, now: Instant, zone: ZoneId): Boolean {
        if (!canNotify()) return false
        val switch = PendingIntent.getBroadcast(
            context,
            block.id,
            Intent(context, PlannedBlockReminderReceiver::class.java).setAction(ACTION_SWITCH).putExtra(EXTRA_BLOCK_ID, block.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = builder(block, zone)
            .setContentText(plannedBlockReminderText(block, now, zone))
            .addAction(0, "Switch", switch)
            .setTimeoutAfter(Duration.between(now, block.end).toMillis().coerceAtLeast(1))
            .build()
        return try {
            notifications.notify(NOTIFICATION_TAG, block.id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun showSwitchFailed(block: ReminderBlock, zone: ZoneId) {
        if (!canNotify()) return
        val notification = builder(block, zone)
            .setContentText("Couldn't switch. Open Timebox to try again.")
            .setTimeoutAfter(Duration.between(Instant.now(), block.end).toMillis().coerceAtLeast(1))
            .build()
        runCatching { notifications.notify(NOTIFICATION_TAG, block.id, notification) }
    }

    private fun read(name: String): Set<String> = store.getStringSet(name, emptySet()).orEmpty().toSet()
    private fun write(name: String, value: Set<String>) { store.edit().putStringSet(name, value).apply() }
    private fun prune(keys: Set<String>, now: Instant) =
        keys.filterTo(mutableSetOf()) { key -> plannedBlockStartOf(key)?.let { it > now.minus(HandledRetention) } ?: false }

    companion object {
        fun receive(context: Context, intent: Intent, pending: BroadcastReceiver.PendingResult) {
            val reminders = (context.applicationContext as? TimeboxApplication)?.plannedBlockReminders
            if (reminders == null) return pending.finish()
            reminders.launch {
                try {
                    withTimeoutOrNull(9_000) {
                        when (intent.action) {
                            ACTION_FIRE -> intent.getStringExtra(EXTRA_KEY)?.let { fire(it) }
                            ACTION_SWITCH -> intent.getIntExtra(EXTRA_BLOCK_ID, -1).takeIf { it >= 0 }?.let { switchTo(it) }
                            else -> reconcile()
                        }
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}

class PlannedBlockReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlannedBlockReminders.receive(context, intent, goAsync())
    }
}

/** Fallback delivery when exact alarms are not allowed: may run a few minutes late. */
class PlannedBlockReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString(EXTRA_KEY) ?: return Result.failure()
        (applicationContext as TimeboxApplication).plannedBlockReminders.fire(key)
        return Result.success()
    }
}

/** Refreshes the snapshot so blocks planned elsewhere reach this device's schedule. */
class PlannedBlockReminderSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TimeboxApplication
        app.activityRepository.refresh()
        app.plannedBlockReminders.reconcile()
        return Result.success()
    }
}
