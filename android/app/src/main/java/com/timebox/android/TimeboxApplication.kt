package com.timebox.android

import android.app.Application
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.reminders.AndroidReminderNotifier
import com.timebox.android.reminders.DailyReminderNotifier
import com.timebox.android.reminders.DailyReminderScheduler
import com.timebox.android.reminders.ReminderScheduler
import com.timebox.android.ui.readiness.ReadyToPlanCoordinator
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
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
    val focusController by lazy { com.timebox.android.ui.focus.FocusController(com.timebox.android.ui.focus.AndroidFocusStorage(this)) }
    val activityRepository by lazy {
        com.timebox.android.data.ActivityRepository(
            com.timebox.android.data.RepositoryActivityTransport(repository),
            com.timebox.android.data.AndroidActivityStorage(this),
        )
    }

    lateinit var repository: TimeboxRepository
        private set
    lateinit var taskCompletion: TaskCompletion
        private set
    lateinit var readinessCoordinator: ReadyToPlanCoordinator
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
        readinessCoordinator = createReadyToPlanCoordinator(repository, applicationScope)
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
