package com.timebox.android

import android.app.Application
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.reminders.AndroidReminderNotifier
import com.timebox.android.reminders.DailyReminderNotifier
import com.timebox.android.reminders.DailyReminderScheduler
import com.timebox.android.reminders.PlannedBlockReminders
import com.timebox.android.reminders.ReminderScheduler
import com.timebox.android.ui.readiness.ReadyToPlanCoordinator
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion

/** Manual DI: the application owns process-wide modules and their shared adapters. */
class TimeboxApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var preferences: AppPreferences
    val assistant by lazy {
        com.timebox.android.ui.assistant.AssistantController(
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ) { com.timebox.android.ui.assistant.HttpAssistantTransport(repository.settings.first()) }
    }
    val focusController by lazy { com.timebox.android.ui.focus.FocusController(com.timebox.android.ui.focus.AndroidFocusStorage(this)) }
    val checkIns by lazy { com.timebox.android.checkin.AndroidCheckIns(this, activityRepository) }
    val activityRepository by lazy {
        com.timebox.android.data.ActivityRepository(
            com.timebox.android.data.RepositoryActivityTransport(repository),
            com.timebox.android.data.AndroidActivityStorage(this),
        )
    }

    lateinit var repository: TimeboxRepository
        private set

    suspend fun recoverLegacyWorkMode(planning: Boolean) {
        if (!activityRepository.bootstrappedThisRun) return
        if (preferences.legacyWorkModeRecovered()) {
            activityRepository.setLegacyRecovery(preferences.legacyWorkModeRecovery())
            return
        }
        val saved = preferences.workMode.first()
        preferences.archiveLegacyWorkMode()
        if (saved == null || focusController.restoreLegacy(saved, activityRepository, planning)) {
            preferences.markLegacyWorkModeRecovered()
            activityRepository.setLegacyRecovery(preferences.legacyWorkModeRecovery())
            if (saved != null) activityRepository.showLegacyRecoveryNotice()
        }
    }
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
    lateinit var plannedBlockReminders: PlannedBlockReminders
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
        repository.onTaskChanged = reminderScheduler::enqueueImmediateSync
        repository.onConnectionChanged = reminderScheduler::enqueueImmediateSync
        reminderScheduler.start()
        com.timebox.android.checkin.CheckInWorker.schedule(this)
        applicationScope.launch {
            activityRepository.state.collect { checkIns.reconcileNotification(it.snapshot?.checkIn?.question?.id) }
        }
        plannedBlockReminders = PlannedBlockReminders(this, activityRepository, preferences.plannedBlockReminders)
            .also { it.createChannel() }
        applicationScope.launch {
            // Plans, adoption, and settings changes all reschedule or withdraw Planned Block Reminders.
            combine(
                activityRepository.state.map { it.snapshot?.let { s -> s.plans to s.current?.plannedBlockId } }.distinctUntilChanged(),
                preferences.plannedBlockReminders,
            ) { _, _ -> }.collect { plannedBlockReminders.reconcile() }
        }
    }
}
