package com.timebox.android.reminders

import android.Manifest
import android.annotation.SuppressLint
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
import com.timebox.android.data.BattleTask
import com.timebox.android.data.DueReminder
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.ui.AppRoutes
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val REMINDER_CHANNEL_ID = "battle_plan_reminders"
private const val PERIODIC_SYNC_WORK = "timebox.reminders.periodic-sync"
private const val IMMEDIATE_SYNC_WORK = "timebox.reminders.immediate-sync"
private const val REMINDER_WORK_PREFIX = "timebox.reminder."
private const val TASK_ID_KEY = "task_id"

/** Platform boundary kept narrow so delivery ownership can be unit tested. */
interface ReminderNotifier {
    fun canNotify(): Boolean
    fun show(reminder: DueReminder): Boolean
}

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Battle Plan reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Task reminders from the Timebox Battle Plan"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun canNotify(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // canNotify checks the runtime grant immediately before notify.
    override fun show(reminder: DueReminder): Boolean {
        if (!canNotify()) return false
        val deepLink = Uri.parse(AppRoutes.taskDeepLink(reminder.id))
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminder.id,
            Intent(Intent.ACTION_VIEW, deepLink, context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("Battle Plan reminder")
            .setContentText(reminder.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.title))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(reminder.id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

data class ReminderDeliveryResult(
    val handedOff: Int = 0,
    val acknowledgementFailures: Int = 0,
    val fetchFailed: Boolean = false,
)

/**
 * The server list is authoritative. A notification is acknowledged only after Android accepts it.
 * [shownInProcess] closes the one-minute foreground polling race while backend acknowledgement runs.
 */
suspend fun deliverDueReminders(
    repository: TimeboxRepository,
    notifier: ReminderNotifier,
    taskId: Int? = null,
    shownInProcess: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
): ReminderDeliveryResult {
    if (!notifier.canNotify()) return ReminderDeliveryResult()
    val due = repository.listDueReminders().getOrElse {
        return ReminderDeliveryResult(fetchFailed = true)
    }
    var handedOff = 0
    var acknowledgementFailures = 0
    due.asSequence()
        .filter { taskId == null || it.id == taskId }
        .filter { shownInProcess.add(it.id) }
        .forEach { reminder ->
            if (!notifier.show(reminder)) {
                shownInProcess.remove(reminder.id)
                return@forEach
            }
            handedOff += 1
            if (repository.acknowledgeReminder(reminder.id).isFailure) {
                acknowledgementFailures += 1
            }
        }
    return ReminderDeliveryResult(handedOff, acknowledgementFailures)
}

data class ReminderScheduleEntry(val taskId: Int, val at: Instant)

/** Pure scheduling decision used by WorkManager and release tests. */
fun reminderSchedule(tasks: List<BattleTask>): List<ReminderScheduleEntry> = tasks
    .asSequence()
    .flatMap { it.flattenForReminders() }
    .filter { it.status != TaskStatus.Completed }
    .filter { it.archivedAt == null && it.deletedAt == null }
    .filter { it.reminderDeliveredAt == null }
    .mapNotNull { task -> task.reminderAt?.let { ReminderScheduleEntry(task.id, it) } }
    .distinctBy(ReminderScheduleEntry::taskId)
    .toList()

private fun BattleTask.flattenForReminders(): Sequence<BattleTask> =
    sequenceOf(this) + sessionTasks.asSequence().flatMap { it.flattenForReminders() }

class ReminderScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)
    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    private val trackedPreferences = context.getSharedPreferences("scheduled_reminders", Context.MODE_PRIVATE)

    fun start() {
        enqueuePeriodicSync()
        enqueueImmediateSync()
    }

    fun enqueuePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<ReminderSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueImmediateSync() {
        val request = OneTimeWorkRequestBuilder<ReminderSyncWorker>()
            .setConstraints(connected)
            .build()
        workManager.enqueueUniqueWork(IMMEDIATE_SYNC_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun replaceSchedules(tasks: List<BattleTask>, now: Instant = Instant.now()) {
        val entries = reminderSchedule(tasks)
        val next = entries.associate { it.taskId to it.at }
        val activeById = tasks.asSequence()
            .flatMap { it.flattenForReminders() }
            .associateBy(BattleTask::id)
        val previous = trackedPreferences.getStringSet("schedules", emptySet())
            .orEmpty()
            .mapNotNull { encoded ->
                val parts = encoded.split('|', limit = 2)
                val id = parts.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
                val at = parts.getOrNull(1)?.let { runCatching { Instant.parse(it) }.getOrNull() }
                id to at
            }
            .toMap()
        (previous.keys - next.keys).forEach { taskId ->
            cancelWork(taskId)
            // An acknowledged reminder should stay in the tray for the user to open.
            // Cleared/completed/archived/trashed/deleted reminders should not.
            if (activeById[taskId]?.reminderDeliveredAt == null) cancelNotification(taskId)
        }
        entries.forEach { entry ->
            if (previous[entry.taskId] != null && previous[entry.taskId] != entry.at) {
                cancelNotification(entry.taskId)
            }
            val delay = Duration.between(now, entry.at).coerceAtLeast(Duration.ZERO)
            val request = OneTimeWorkRequestBuilder<DueReminderWorker>()
                .setInputData(Data.Builder().putInt(TASK_ID_KEY, entry.taskId).build())
                .setInitialDelay(delay)
                .setConstraints(connected)
                .addTag(REMINDER_WORK_PREFIX + entry.taskId)
                .build()
            workManager.enqueueUniqueWork(
                REMINDER_WORK_PREFIX + entry.taskId,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
        trackedPreferences.edit()
            .remove("task_ids")
            .putStringSet("schedules", next.map { (id, at) -> "$id|$at" }.toSet())
            .apply()
    }

    private fun cancelWork(taskId: Int) {
        workManager.cancelUniqueWork(REMINDER_WORK_PREFIX + taskId)
    }

    private fun cancelNotification(taskId: Int) {
        NotificationManagerCompat.from(context).cancel(taskId)
    }
}

class ReminderSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TimeboxApplication
        return app.repository.listBattleTasks().fold(
            onSuccess = { tasks ->
                app.reminderScheduler.replaceSchedules(tasks.items)
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }
}

class DueReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getInt(TASK_ID_KEY, -1)
        if (taskId < 0) return Result.failure()
        val app = applicationContext as TimeboxApplication
        val result = deliverDueReminders(app.repository, app.reminderNotifier, taskId)
        return when {
            result.fetchFailed || result.acknowledgementFailures > 0 -> Result.retry()
            else -> Result.success()
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        ReminderScheduler(context.applicationContext).start()
    }
}
