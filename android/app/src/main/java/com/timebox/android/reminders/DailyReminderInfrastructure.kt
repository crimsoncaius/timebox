package com.timebox.android.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.timebox.android.MainActivity
import com.timebox.android.R
import java.time.Instant
import java.time.LocalTime
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate

enum class DailyReminderKind(val notificationId: Int, val title: String) {
    Planning(60_001, "Plan your day"),
    Review(60_002, "Review your day"),
}

data class DailyReminder(val enabled: Boolean = false, val time: LocalTime)

data class DailyReminderSettings(
    val planning: DailyReminder = DailyReminder(false, LocalTime.of(9, 0)),
    val review: DailyReminder = DailyReminder(false, LocalTime.of(18, 0)),
)

data class DailyReminderScheduleEntry(val kind: DailyReminderKind, val at: Instant)

const val DAILY_REMINDER_CHANNEL_ID = "daily_reminders"
private const val DAILY_REMINDER_WORK_PREFIX = "timebox.daily-reminder."
private const val DAILY_REMINDER_KIND_KEY = "daily_reminder_kind"
private const val DAILY_REMINDER_TIME_KEY = "daily_reminder_time"
private const val DAILY_REMINDER_AT_KEY = "daily_reminder_at"

/** Returns the next prompt for every enabled Daily Reminder in the device's current timezone. */
fun nextDailyReminderSchedules(
    settings: DailyReminderSettings,
    now: ZonedDateTime,
): List<DailyReminderScheduleEntry> = listOf(
    DailyReminderKind.Planning to settings.planning,
    DailyReminderKind.Review to settings.review,
).mapNotNull { (kind, reminder) ->
    if (!reminder.enabled) return@mapNotNull null
    var next = now.toLocalDate().atTime(reminder.time).atZone(now.zone)
    if (!next.isAfter(now)) next = next.plusDays(1)
    DailyReminderScheduleEntry(kind, next.toInstant())
}

class DailyReminderScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)
    private var latest: DailyReminderSettings? = null

    fun replace(settings: DailyReminderSettings, now: ZonedDateTime = ZonedDateTime.now()) {
        latest = settings
        val entries = nextDailyReminderSchedules(settings, now)
        DailyReminderKind.entries.forEach { kind -> workManager.cancelUniqueWork(workName(kind)) }
        entries.forEach { entry ->
            enqueue(entry, now)
        }
    }

    fun rescheduleForCurrentTimezone() {
        latest?.let { replace(it, ZonedDateTime.now(ZoneId.systemDefault())) }
    }

    private fun workName(kind: DailyReminderKind) = DAILY_REMINDER_WORK_PREFIX + kind.name

    fun scheduleNext(kind: DailyReminderKind, time: LocalTime, now: ZonedDateTime = ZonedDateTime.now()) {
        val next = nextDailyReminderSchedules(
            if (kind == DailyReminderKind.Planning) DailyReminderSettings(planning = DailyReminder(true, time))
            else DailyReminderSettings(review = DailyReminder(true, time)), now,
        ).single()
        enqueue(next, now)
    }

    private fun enqueue(entry: DailyReminderScheduleEntry, now: ZonedDateTime) {
        val delay = Duration.between(now.toInstant(), entry.at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInputData(Data.Builder()
                .putString(DAILY_REMINDER_KIND_KEY, entry.kind.name)
                .putString(DAILY_REMINDER_TIME_KEY, entry.at.atZone(now.zone).toLocalTime().toString())
                .putLong(DAILY_REMINDER_AT_KEY, entry.at.toEpochMilli()).build())
            .setInitialDelay(delay).build()
        workManager.enqueueUniqueWork(workName(entry.kind), ExistingWorkPolicy.REPLACE, request)
    }
}

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(DAILY_REMINDER_KIND_KEY)
            ?.let { runCatching { DailyReminderKind.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        val time = inputData.getString(DAILY_REMINDER_TIME_KEY)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: return Result.failure()
        val dueAt = inputData.getLong(DAILY_REMINDER_AT_KEY, -1)
        if (dueAt > 0 && Instant.now().toEpochMilli() <= dueAt + 60_000L) DailyReminderNotifier(applicationContext).show(kind)
        DailyReminderScheduler(applicationContext).scheduleNext(kind, time)
        return Result.success()
    }
}

class DailyReminderNotifier(private val context: Context) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DAILY_REMINDER_CHANNEL_ID,
                "Daily reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Daily planning and review prompts from Timebox" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun show(kind: DailyReminderKind) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            kind.notificationId,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("timebox://day/${LocalDate.now()}"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, DAILY_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(kind.title)
            .setContentText("Open Timebox to continue")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(kind.notificationId, notification) }
    }
}
