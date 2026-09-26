package com.timebox.android.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.ui.undo.UndoNoticeHost
import com.timebox.android.ui.battleplan.BattlePlanViewModel
import com.timebox.android.ui.battleplan.RecurringEditorViewModel
import com.timebox.android.ui.battleplan.RecurringViewModel
import com.timebox.android.ui.battleplan.TaskDetailViewModel
import com.timebox.android.ui.chronicle.ChronicleView
import com.timebox.android.ui.chronicle.ChronicleViewModel
import com.timebox.android.ui.components.TimeboxBottomNav
import com.timebox.android.ui.components.TimeboxTab
import com.timebox.android.ui.components.TimeboxTopBar
import com.timebox.android.ui.components.TransientFeedback
import com.timebox.android.ui.day.DayViewModel
import com.timebox.android.ui.focus.FocusMode
import com.timebox.android.ui.readiness.LocalReadyToPlanRetry
import com.timebox.android.ui.readiness.ReadyToPlanCoordinator
import com.timebox.android.ui.settings.SettingsViewModel
import com.timebox.android.ui.taskcompletion.TaskCompletion
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.types.TypesViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TimeboxApp(
    isDark: Boolean,
    onToggleDark: () -> Unit,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    exactAlarmsAllowed: Boolean = true,
    onRequestExactAlarms: () -> Unit = {},
    repository: TimeboxRepository = rememberRepository(),
    taskCompletion: TaskCompletion = rememberTaskCompletion(),
    readinessCoordinator: ReadyToPlanCoordinator,
    imeVisibleOverride: Boolean? = null,
) {
    val application = LocalContext.current.applicationContext as TimeboxApplication
    val activityRepository = application.activityRepository
    val undoLifecycle = application.undoLifecycle
    val factory = remember(repository, taskCompletion, readinessCoordinator, activityRepository, undoLifecycle) {
        timeboxViewModelFactory(repository, taskCompletion, readinessCoordinator, activityRepository, undoLifecycle)
    }
    val navController = rememberNavController()

    val dayViewModel: DayViewModel = viewModel(factory = factory)
    val chronicleViewModel: ChronicleViewModel = viewModel(factory = factory)
    val typesViewModel: TypesViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val battlePlanViewModel: BattlePlanViewModel = viewModel(factory = factory)
    val taskDetailViewModel: TaskDetailViewModel = viewModel(factory = factory)
    val recurringViewModel: RecurringViewModel = viewModel(factory = factory)
    val recurringEditorViewModel: RecurringEditorViewModel = viewModel(factory = factory)
    val dayState by dayViewModel.state.collectAsState()
    val focusController = if (activityRepository != null) application.focusController else null
    val focusState = focusController?.state?.collectAsState()?.value
    val currentActivity = activityRepository?.state?.collectAsState()?.value
    val focused = focusState?.active == true && currentActivity?.snapshot?.current != null && !dayState.focusPlanningBlocked
    val checkIns = if (activityRepository != null) application.checkIns else null
    val requestedCheckIn = checkIns?.openQuestion?.collectAsState()?.value
    LaunchedEffect(requestedCheckIn) {
        if (requestedCheckIn != null && !focused && activityRepository != null) {
            val zone = ZoneId.of(activityRepository.state.value.snapshot?.reportingTimezone ?: "UTC")
            navController.navigate(AppRoutes.day(activityRepository.now().atZone(zone).toLocalDate())) { launchSingleTop = true }
        }
    }
    LaunchedEffect(currentActivity, dayState.focusPlanningBlocked) {
        if (activityRepository != null) {
            application.recoverLegacyWorkMode(dayViewModel.state.value.focusPlanningBlocked)
            focusController?.reconcile(activityRepository, dayViewModel.state.value.focusPlanningBlocked)
        }
    }

    val chronicleState by chronicleViewModel.state.collectAsState()
    val typesState by typesViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val battlePlanState by battlePlanViewModel.state.collectAsState()
    val taskDetailState by taskDetailViewModel.state.collectAsState()
    val taskCompletionNotice by taskCompletion.notice.collectAsState()
    val undoNotice by undoLifecycle.notice.collectAsState()
    val recurringState by recurringViewModel.state.collectAsState()
    val recurringEditorState by recurringEditorViewModel.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: AppRoutes.DayPattern
    val lifecycleOwner = LocalLifecycleOwner.current
    var appResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            appResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val routeDate = backStackEntry?.arguments?.getString(AppRoutes.DateArg)?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }
    val surfaceRoute = if (route == AppRoutes.TaskDetailPattern) navController.previousBackStackEntry?.destination?.route ?: AppRoutes.BattlePlan else route
    val routeTaskId = backStackEntry?.arguments?.getInt(AppRoutes.TaskIdArg)
    val routeBlockId = backStackEntry?.arguments?.getInt(AppRoutes.BlockIdArg)?.takeIf { it >= 0 }
    val routeTemplateId = backStackEntry?.arguments?.getInt(AppRoutes.TemplateIdArg)
    val snackbarHostState = remember { SnackbarHostState() }
    val openedDayEntryIds = remember { mutableSetOf<String>() }
    val openedBattlePlanEntryIds = remember { mutableSetOf<String>() }

    LaunchedEffect(route, routeDate, routeTaskId, routeTemplateId, routeBlockId, backStackEntry?.id) {
        when (route) {
            AppRoutes.DayPattern -> {
                val entryId = backStackEntry?.id ?: return@LaunchedEffect
                // A Day entry's argument seeds selection once. Returning from another tab
                // must retain any newer date selected inside the mounted screen.
                if (openedDayEntryIds.add(entryId)) {
                    routeDate?.let(dayViewModel::goToDate)
                    if (routeBlockId != null) dayViewModel.noteBlockLanding()
                }
                dayViewModel.start()
                dayViewModel.refreshTaskTypes()
                dayViewModel.refreshReadyToPlan()
            }
            AppRoutes.Chronicle -> {
                dayState.day?.today?.let(chronicleViewModel::setToday)
                chronicleViewModel.open()
            }
            AppRoutes.Types -> typesViewModel.load()
            AppRoutes.Settings -> settingsViewModel.load(dayState.day?.timezone)
            AppRoutes.BattlePlan -> {
                val entryId = backStackEntry?.id ?: return@LaunchedEffect
                if (openedBattlePlanEntryIds.add(entryId)) battlePlanViewModel.load()
            }
            AppRoutes.TaskDetailPattern -> routeTaskId?.let(taskDetailViewModel::load)
            AppRoutes.Recurring -> recurringViewModel.load()
            AppRoutes.RecurringNew -> recurringEditorViewModel.open(null)
            AppRoutes.RecurringDetailPattern -> routeTemplateId?.let { recurringViewModel.openDetail(it); recurringEditorViewModel.open(it) }
            AppRoutes.RecurringEditPattern -> recurringEditorViewModel.open(routeTemplateId)
        }
    }
    LaunchedEffect(dayState.message) {
        dayState.message?.let { snackbarHostState.showSnackbar(it); dayViewModel.consumeMessage() }
    }
    LaunchedEffect(route, routeDate, routeBlockId, dayState.day) {
        if (
            route == AppRoutes.DayPattern &&
            routeBlockId != null &&
            dayState.day?.date == routeDate &&
            dayState.selectedBlockId != routeBlockId
        ) {
            dayViewModel.selectBlock(routeBlockId)
        }
    }
    LaunchedEffect(typesState.message) {
        typesState.message?.let { snackbarHostState.showSnackbar(it); typesViewModel.consumeMessage() }
    }
    LaunchedEffect(typesState.mergeRevision) {
        if (typesState.mergeRevision > 0) {
            battlePlanViewModel.load(showSpinner = false)
            dayViewModel.load(showSpinner = false)
            dayViewModel.refreshTaskTypes()
        }
    }
    LaunchedEffect(settingsState.message) {
        settingsState.message?.let { snackbarHostState.showSnackbar(it); settingsViewModel.consumeMessage() }
    }
    LaunchedEffect(battlePlanState.message) {
        battlePlanState.message?.let { snackbarHostState.showSnackbar(it); battlePlanViewModel.consumeMessage() }
    }
    LaunchedEffect(battlePlanState.createdTaskNotice?.taskId) {
        battlePlanState.createdTaskNotice?.let { notice ->
            val result = snackbarHostState.showSnackbar(
                message = notice.message,
                actionLabel = "Open",
                duration = SnackbarDuration.Short,
            )
            battlePlanViewModel.consumeCreatedTaskNotice()
            if (result == SnackbarResult.ActionPerformed) navController.navigate(AppRoutes.taskDetail(notice.taskId))
        }
    }
    LaunchedEffect(taskDetailState.message) {
        taskDetailState.message?.let { snackbarHostState.showSnackbar(it); taskDetailViewModel.consumeMessage() }
    }
    LaunchedEffect(taskDetailState.trashUndoTarget) {
        taskDetailState.trashUndoTarget?.let { target ->
            taskDetailViewModel.consumeTrashUndoTarget()
            battlePlanViewModel.offerUndo(target.taskId, target.title)
            battlePlanViewModel.applyRemovedTask(target.taskId)
            if (target.leaveTaskDetail && route == AppRoutes.TaskDetailPattern) navController.popBackStack()
        }
    }
    LaunchedEffect(battlePlanState.restoredTrashTaskId) {
        battlePlanState.restoredTrashTaskId?.let {
            battlePlanViewModel.consumeRestoredTrashTask()
            battlePlanViewModel.load(showSpinner = false)
            if (route == AppRoutes.TaskDetailPattern) routeTaskId?.let(taskDetailViewModel::load)
        }
    }
    LaunchedEffect(taskCompletionNotice?.id) {
        taskCompletionNotice?.let { notice ->
            if (notice.canUndo) {
                val context = if (focused) "focus" else if (isBattlePlanRoute(route)) "battle-plan" else "day:${routeDate ?: dayState.date}"
                val title = notice.taskTitle ?: "Task"
                undoLifecycle.offer(context, title, "$title completed", notice.message, targetId = notice.taskId, undo = {
                    taskCompletion.undo(notice.id).onSuccess { task ->
                        dayViewModel.refreshAfterTaskCompletion()
                        battlePlanViewModel.refreshAfterTaskCompletion()
                        taskDetailViewModel.refreshAfterTaskCompletion(task.id)
                    }.map { Unit }
                }, release = { taskCompletion.dismiss(notice.id) })
            } else {
                if (notice.taskId != null && undoLifecycle.notice.value?.targetId == notice.taskId) undoLifecycle.dismiss()
                snackbarHostState.showSnackbar(notice.message, duration = taskCompletionSnackbarDuration())
                taskCompletion.dismiss(notice.id)
            }
        }
    }
    LaunchedEffect(undoLifecycle) {
        undoLifecycle.lateErrors.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(recurringState.message) {
        recurringState.message?.let { snackbarHostState.showSnackbar(it); recurringViewModel.consumeMessage() }
    }
    LaunchedEffect(recurringEditorState.message) {
        recurringEditorState.message?.let { snackbarHostState.showSnackbar(it); recurringEditorViewModel.consumeMessage() }
    }
    LaunchedEffect(recurringEditorState.savedTemplateId) {
        recurringEditorState.savedTemplateId?.let { templateId ->
            val created = route == AppRoutes.RecurringNew
            recurringEditorViewModel.consumeSaved()
            recurringViewModel.load(showSpinner = false)
            if (created) {
                navController.popBackStack(AppRoutes.Recurring, inclusive = false)
            } else if (route == AppRoutes.RecurringDetailPattern) {
                recurringViewModel.openDetail(templateId)
            } else {
                navController.navigate(AppRoutes.recurringDetail(templateId)) {
                    popUpTo(AppRoutes.RecurringEditPattern) { inclusive = true }
                }
            }
        }
    }

    val selectedTab = when (route) {
        AppRoutes.DayPattern -> TimeboxTab.Day
        AppRoutes.Chronicle -> TimeboxTab.Chronicle
        AppRoutes.BattlePlan, AppRoutes.TaskDetailPattern,
        AppRoutes.Recurring, AppRoutes.RecurringNew,
        AppRoutes.RecurringDetailPattern, AppRoutes.RecurringEditPattern,
        AppRoutes.Types -> TimeboxTab.BattlePlan
        AppRoutes.Assistant -> TimeboxTab.Assistant
        AppRoutes.Settings, AppRoutes.ThemePreview -> TimeboxTab.Settings
        else -> null
    }
    val colors = TimeboxTheme.colors
    val isImeVisible = imeVisibleOverride ?: WindowInsets.isImeVisible
    val accessibilityManager = LocalAccessibilityManager.current
    val recommendedUndoTimeoutMillis = accessibilityManager?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = 10_000L,
        containsIcons = false,
        containsText = true,
        containsControls = true,
    ) ?: 10_000L
    val trackingScope = rememberCoroutineScope()
    val context = LocalContext.current
    val reducedMotion = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
    val withinBattlePlan = isBattlePlanRoute(route)
    val undoContext = when {
        focused -> "focus"
        withinBattlePlan -> "battle-plan"
        route == AppRoutes.DayPattern -> "day:${routeDate ?: dayState.date}"
        // Tracking Proposals are confirmed here; their Undo appears in the same notice.
        route == AppRoutes.Assistant -> "assistant"
        else -> null
    }
    LaunchedEffect(undoContext, appResumed, recommendedUndoTimeoutMillis) {
        undoLifecycle.setExposure(undoContext, appResumed, recommendedUndoTimeoutMillis)
    }
    LaunchedEffect(activityRepository, undoContext) {
        activityRepository.switchUndoOffers.collect { offer ->
            undoContext?.let { context ->
                val started = offer.kind == com.timebox.android.data.remote.ActivityKind.Start
                undoLifecycle.offer(context, if (started) "activity start" else "activity switch",
                    if (started) "Started ${offer.name}" else "Switched to ${offer.name}", undo = {
                    activityRepository.undoSwitch(offer.operationId).also { result ->
                        if (result.isSuccess) launch { activityRepository.refresh() }
                    }
                })
            }
        }
    }

    val screenModels = remember(
        dayViewModel, chronicleViewModel, typesViewModel, settingsViewModel,
        battlePlanViewModel, taskDetailViewModel, recurringViewModel, recurringEditorViewModel,
    ) {
        TimeboxScreenModels(
            dayViewModel, chronicleViewModel, typesViewModel, settingsViewModel,
            battlePlanViewModel, taskDetailViewModel, recurringViewModel, recurringEditorViewModel,
        )
    }
    val navigationOptions = rememberUpdatedState(TimeboxNavigationOptions(
        isDark, onToggleDark, notificationsAllowed, onRequestNotificationPermission,
        onOpenNotificationSettings, exactAlarmsAllowed, onRequestExactAlarms, reducedMotion,
    ))
    val navigation = remember(
        screenModels, navController, snackbarHostState, trackingScope,
        activityRepository, focusController, navigationOptions, undoLifecycle,
    ) {
        TimeboxNavigationDependencies(
            models = screenModels,
            navController = navController,
            snackbarHostState = snackbarHostState,
            trackingScope = trackingScope,
            activityRepository = activityRepository,
            onEnterFocus = {
                trackingScope.launch {
                    focusController?.enter(activityRepository) { dayViewModel.state.value.focusPlanningBlocked }
                }
            },
            options = navigationOptions,
            undoLifecycle = undoLifecycle,
        )
    }

    CompositionLocalProvider(LocalReadyToPlanRetry provides readinessCoordinator::retry) {
    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        if (!focused) Column(
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            if (surfaceRoute != AppRoutes.DayPattern &&
                surfaceRoute != AppRoutes.Assistant &&
                surfaceRoute != AppRoutes.RecurringNew &&
                surfaceRoute != AppRoutes.RecurringEditPattern &&
                surfaceRoute != AppRoutes.RecurringDetailPattern &&
                surfaceRoute?.startsWith("prototype/") != true) {
                TimeboxTopBar(
                    kicker = routeKicker(surfaceRoute),
                    title = routeTitle(
                        surfaceRoute,
                        formatFullDate(dayState.date),
                        when (chronicleState.view) {
                            ChronicleView.Trends -> "Trends"
                            ChronicleView.Habits -> "Habits"
                            ChronicleView.Calendar -> formatMonthTitle(chronicleState.monthStart)
                        },
                    ),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                TimeboxNavHost(navigation)
            }

            if (shouldShowBottomNavigation(
                    battlePlanState.showComposer,
                    isImeVisible,
                    route == AppRoutes.TaskDetailPattern,
                )) {
                TimeboxBottomNav(selectedTab) { tab ->
                    val target = when (tab) {
                        TimeboxTab.Day -> AppRoutes.day(dayState.date)
                        TimeboxTab.Chronicle -> AppRoutes.Chronicle
                        TimeboxTab.BattlePlan -> AppRoutes.BattlePlan
                        TimeboxTab.Assistant -> AppRoutes.Assistant
                        TimeboxTab.Settings -> AppRoutes.Settings
                    }
                    navController.navigate(target) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        if (route != AppRoutes.TaskDetailPattern) {
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = if (undoNotice == null) 92.dp else 172.dp)) { data ->
                TransientFeedback(
                    message = data.visuals.message,
                    modifier = Modifier.semantics {
                        paneTitle = "Feedback"
                        dismiss { data.dismiss(); true }
                    },
                    actionLabel = data.visuals.actionLabel,
                    onAction = data::performAction,
                    onDismiss = if (data.visuals.withDismissAction) data::dismiss else null,
                )
            }
        }

        if (route != AppRoutes.TaskDetailPattern) {
            undoNotice?.let { notice ->
                UndoNoticeHost(
                    notice = notice,
                    onUndo = { undoLifecycle.undo(notice.id) },
                    onDismiss = { undoLifecycle.dismiss(notice.id) },
                    onExpiryFinished = { undoLifecycle.finishExpiry(notice.id) },
                    reducedMotion = reducedMotion,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 92.dp),
                )
            }
        }

        if (focused) FocusMode(onTaskChanged = { dayViewModel.refreshAfterTaskCompletion(); battlePlanViewModel.refreshAfterTaskCompletion() })
    }
    }
}

internal fun shouldShowBottomNavigation(
    taskComposerVisible: Boolean,
    isImeVisible: Boolean,
    taskDetailVisible: Boolean = false,
): Boolean = !taskComposerVisible && !taskDetailVisible && !isImeVisible

/** Completion feedback must dismiss even when it offers Undo. */
internal fun taskCompletionSnackbarDuration(): SnackbarDuration = SnackbarDuration.Short

internal fun isBattlePlanRoute(route: String): Boolean = route in setOf(
    AppRoutes.BattlePlan,
    AppRoutes.TaskDetailPattern,
    AppRoutes.Recurring,
    AppRoutes.RecurringNew,
    AppRoutes.RecurringDetailPattern,
    AppRoutes.RecurringEditPattern,
)

private fun routeKicker(route: String): String = when (route) {
    AppRoutes.DayPattern -> "Day"
    AppRoutes.Chronicle -> "Chronicle"
    AppRoutes.BattlePlan, AppRoutes.TaskDetailPattern,
    AppRoutes.Recurring, AppRoutes.RecurringNew,
    AppRoutes.RecurringDetailPattern, AppRoutes.RecurringEditPattern,
    AppRoutes.Types -> "Battle Plan"
    AppRoutes.Assistant -> "Assistant"
    AppRoutes.Settings, AppRoutes.ThemePreview -> "Settings"
    else -> "Timebox"
}

internal fun routeTitle(route: String, day: String, chronicle: String): String =
    when (route) {
        AppRoutes.DayPattern -> day
        AppRoutes.Chronicle -> chronicle
        AppRoutes.BattlePlan -> "Tasks"
        AppRoutes.TaskDetailPattern -> "Task details"
        AppRoutes.Recurring -> "Recurring"
        AppRoutes.RecurringNew -> "New recurrence"
        AppRoutes.RecurringDetailPattern -> "Template details"
        AppRoutes.RecurringEditPattern -> "Edit recurrence"
        AppRoutes.Types -> "Task Types"
        AppRoutes.Assistant -> "Conversations"
        AppRoutes.Settings -> "Preferences"
        AppRoutes.ThemePreview -> "Theme preview"
        else -> "Timebox"
    }
