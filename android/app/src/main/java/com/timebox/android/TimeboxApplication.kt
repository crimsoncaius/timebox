package com.timebox.android

import android.app.Application
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.reminders.AndroidReminderNotifier
import com.timebox.android.reminders.ReminderScheduler
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion

/** Manual DI: the application owns process-wide modules and their shared adapters. */
class TimeboxApplication : Application() {

    lateinit var repository: TimeboxRepository
        private set
    lateinit var taskCompletion: TaskCompletion
        private set
    lateinit var reminderNotifier: AndroidReminderNotifier
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        repository = TimeboxRepository(AppPreferences(this))
        taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository))
        reminderNotifier = AndroidReminderNotifier(this).also { it.createChannel() }
        reminderScheduler = ReminderScheduler(this)
        repository.onActiveTasksLoaded = { tasks -> reminderScheduler.replaceSchedules(tasks.items) }
        repository.onConnectionChanged = reminderScheduler::enqueueImmediateSync
        reminderScheduler.start()
    }
}
