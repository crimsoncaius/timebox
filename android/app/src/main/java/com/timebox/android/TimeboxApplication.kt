package com.timebox.android

import android.app.Application
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.reminders.AndroidReminderNotifier
import com.timebox.android.reminders.ReminderScheduler

/** Manual DI: the app has one repository and one preference store. */
class TimeboxApplication : Application() {

    lateinit var repository: TimeboxRepository
        private set
    lateinit var reminderNotifier: AndroidReminderNotifier
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        repository = TimeboxRepository(AppPreferences(this))
        reminderNotifier = AndroidReminderNotifier(this).also { it.createChannel() }
        reminderScheduler = ReminderScheduler(this)
        repository.onActiveTasksLoaded = { tasks -> reminderScheduler.replaceSchedules(tasks.items) }
        repository.onConnectionChanged = reminderScheduler::enqueueImmediateSync
        reminderScheduler.start()
    }
}
