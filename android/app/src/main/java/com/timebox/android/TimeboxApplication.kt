package com.timebox.android

import android.app.Application
import com.timebox.android.data.assistantIdentity
import kotlinx.coroutines.runBlocking
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.reminders.AndroidReminderNotifier
import com.timebox.android.reminders.DailyReminderNotifier
import com.timebox.android.reminders.DailyReminderScheduler
import com.timebox.android.reminders.PlannedBlockReminders
import com.timebox.android.reminders.ReminderScheduler
import com.timebox.android.reminders.trackedActivity
import com.timebox.android.reminders.AndroidReminderSuppressionStore
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
import com.timebox.android.ui.undo.UndoLifecycle

/** Manual DI: the application owns process-wide modules and their shared adapters. */
class TimeboxApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var preferences: AppPreferences
    private val assistantScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var taskJournal: com.timebox.android.data.AssistantTaskJournal
    @Volatile private var assistantServerIdentity = ""
    val taskRecovery by lazy {
        createTaskRecovery { com.timebox.android.ui.assistant.HttpAssistantTransport(repository.settings.first()) }
    }
    fun createTaskRecovery(transportFactory: suspend () -> com.timebox.android.ui.assistant.AssistantTransport) =
        com.timebox.android.ui.assistant.AssistantTaskRecovery(assistantScope, taskJournal,
            transportFactory,
            reserve = { record ->
                if (record.affectsTracking) activityRepository.reserveForTask(record.submissionId)
                try { readinessCoordinator.reserveForTask(record.affectedTaskIds) }
                catch (error: Throwable) { if (record.affectsTracking) activityRepository.releaseForTask(record.submissionId); throw error }
            },
            release = { record ->
                readinessCoordinator.releaseForTask(record.affectedTaskIds)
                if (record.affectsTracking) activityRepository.releaseForTask(record.submissionId)
            },
            reconcile = { record, _ ->
                check(repository.settings.first().assistantIdentity() == record.serverIdentity) { "Return to the original server to reconcile Task changes." }
                val tasks = repository.listBattleTasks().getOrThrow()
                readinessCoordinator.mergeServerTasks(tasks.items)
                activityRepository.reconcileTask()
                repository.onTaskChanged()
                repository.assistantTasksChanged.emit(record.affectedTaskIds)
            },
        )
    val assistant by lazy {
        com.timebox.android.ui.assistant.AssistantController(
            assistantScope, taskRecovery,
        ) { com.timebox.android.ui.assistant.HttpAssistantTransport(repository.settings.first()) }
    }
    val focusController by lazy { com.timebox.android.ui.focus.FocusController(com.timebox.android.ui.focus.AndroidFocusStorage(this)) }
    /** Assistant ↔ Day: prefilled tracking sheets, their results, and View in Day landings. */
    val trackingHandoff by lazy { com.timebox.android.ui.day.TrackingHandoff() }
    val checkIns by lazy { com.timebox.android.checkin.AndroidCheckIns(this, activityRepository) }
    val activityRepository by lazy {
        com.timebox.android.data.ActivityRepository(
            com.timebox.android.data.RepositoryActivityTransport(repository),
            com.timebox.android.data.AndroidActivityStorage(this),
        ).also { activity -> activity.taskRecoveryBlocked = { taskJournal.trackingBlocked(repository.settings.first().assistantIdentity()) } }
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
    val undoLifecycle by lazy { UndoLifecycle(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    lateinit var readinessCoordinator: ReadyToPlanCoordinator
        private set
    lateinit var reminderNotifier: AndroidReminderNotifier
        private set
    lateinit var reminderScheduler: ReminderScheduler
    lateinit var reminderSuppressions: AndroidReminderSuppressionStore
        private set
    lateinit var dailyReminderScheduler: DailyReminderScheduler
        private set
    lateinit var plannedBlockReminders: PlannedBlockReminders
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        taskJournal = com.timebox.android.data.AssistantTaskJournal(com.timebox.android.data.AndroidTaskJournalStorage(this))
        repository = TimeboxRepository(preferences).also { it.assistantTaskJournal = taskJournal }
        assistantServerIdentity = runBlocking { repository.settings.first().assistantIdentity() }
        applicationScope.launch { repository.settings.collect { assistantServerIdentity = it.assistantIdentity() } }
        readinessCoordinator = createReadyToPlanCoordinator(repository, applicationScope)
        readinessCoordinator.taskRecoveryBlocked = { taskJournal.blocked(assistantServerIdentity, listOf(it)) }
        taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository))
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        fun publishConnection() { assistantScope.launch { taskRecovery.connectionChanged(connectivity.activeNetwork != null) } }
        connectivity.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = publishConnection()
            override fun onLost(network: android.net.Network) = publishConnection()
        })
        publishConnection()
        reminderNotifier = AndroidReminderNotifier(this).also { it.createChannel() }
        reminderSuppressions = AndroidReminderSuppressionStore(this)
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
        // Tracking Proposal cards follow changes made elsewhere: a prefilled sheet on Day, or Undo.
        applicationScope.launch(Dispatchers.Main) {
            trackingHandoff.applied.collect { (proposal, record) -> assistant.proposalApplied(proposal, record, record.source) }
        }
        applicationScope.launch(Dispatchers.Main) {
            activityRepository.undoneOperations.collect(assistant::operationUndone)
        }
        plannedBlockReminders = PlannedBlockReminders(this, activityRepository, preferences.plannedBlockReminders)
            .also { it.createChannel() }
        applicationScope.launch {
            // Plans, the Current Activity, and settings changes all reschedule or withdraw Planned Block Reminders.
            combine(
                activityRepository.state.map { it.snapshot?.let { s -> s.plans to s.trackedActivity() } }.distinctUntilChanged(),
                preferences.plannedBlockReminders,
            ) { _, _ -> }.collect { plannedBlockReminders.reconcile() }
        }
    }
}
