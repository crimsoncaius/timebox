package com.timebox.android

import android.app.Application
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.reminders.AndroidReminderNotifier
import com.timebox.android.reminders.DailyReminderNotifier
import com.timebox.android.reminders.DailyReminderScheduler
import com.timebox.android.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion

/** Manual DI: the application owns process-wide modules and their shared adapters. */
class TimeboxApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var preferences: AppPreferences

    lateinit var repository: TimeboxRepository
        private set
    lateinit var taskCompletion: TaskCompletion
        private set
    lateinit var reminderNotifier: AndroidReminderNotifier
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set
    lateinit var dailyReminderScheduler: DailyReminderScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        repository = TimeboxRepository(preferences)
        taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository))
        reminderNotifier = AndroidReminderNotifier(this).also { it.createChannel() }
        reminderScheduler = ReminderScheduler(this)
        DailyReminderNotifier(this).createChannel()
        dailyReminderScheduler = DailyReminderScheduler(this)
        applicationScope.launch {
            preferences.dailyReminders.collectLatest { dailyReminderScheduler.replace(it) }
        }
        repository.onActiveTasksLoaded = { tasks -> reminderScheduler.replaceSchedules(tasks.items) }
        repository.onConnectionChanged = reminderScheduler::enqueueImmediateSync
        reminderScheduler.start()
    }
}
