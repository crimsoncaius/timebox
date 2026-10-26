package com.timebox.android.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import com.timebox.android.ui.components.LocalNavigationBlockers
import com.timebox.android.ui.components.LocalNavigationDockInset
import com.timebox.android.ui.components.NavigationDock
import com.timebox.android.ui.components.NavigationShortcutEditor
import com.timebox.android.ui.components.navigationShortcutRoutes
import com.timebox.android.ui.components.navigationDestinations
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlinx.coroutines.flow.map

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
    val reviewRequested by application.reviewRequested.collectAsState()
    LaunchedEffect(reviewRequested) {
        if (reviewRequested) {
            navController.navigate(AppRoutes.Review) { launchSingleTop = true }
            application.reviewRequested.value = false
        }
    }

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
    val route = backStackEntry?.destination?.route ?: AppRoutes.Home
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
    val routeTaskId = backStackEntry?.arguments?.getInt(AppRoutes.TaskIdArg)
    val routeBlockId = backStackEntry?.arguments?.getInt(AppRoutes.BlockIdArg)?.takeIf { it >= 0 }
    val routeTemplateId = backStackEntry?.arguments?.getInt(AppRoutes.TemplateIdArg)
    val snackbarHostState = remember { SnackbarHostState() }
    val openedDayEntryIds = remember { mutableSetOf<String>() }
    val openedBattlePlanEntryIds = remember { mutableSetOf<String>() }
    val openedSettingsEntryIds = remember { mutableSetOf<String>() }

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
            AppRoutes.Chronicle, AppRoutes.Trends, AppRoutes.Habits, AppRoutes.TimeGoals -> {
                dayState.day?.today?.let(chronicleViewModel::setToday)
                chronicleViewModel.selectView(when (route) {
                    AppRoutes.Trends -> ChronicleView.Trends
                    AppRoutes.Habits -> ChronicleView.Habits
                    AppRoutes.TimeGoals -> ChronicleView.TimeGoals
                    else -> ChronicleView.Calendar
                })
                if (route == AppRoutes.Chronicle) chronicleViewModel.open()
            }
            AppRoutes.Types -> typesViewModel.load()
            AppRoutes.Settings -> {
                val entryId = backStackEntry?.id ?: return@LaunchedEffect
                if (openedSettingsEntryIds.add(entryId)) settingsViewModel.load(dayState.day?.timezone)
            }
            AppRoutes.BattlePlan -> {
                val entryId = backStackEntry?.id ?: return@LaunchedEffect
                if (openedBattlePlanEntryIds.add(entryId)) battlePlanViewModel.load()
            }
            AppRoutes.TaskDetailPattern -> routeTaskId?.let(taskDetailViewModel::load)
            AppRoutes.Recurring -> recurringViewModel.load(showSpinner = recurringState.templates.isEmpty())
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
    LaunchedEffect(repository) {
        repository.assistantTasksChanged.collect { ids ->
            dayViewModel.refreshAfterTaskCompletion()
            battlePlanViewModel.refreshAfterTaskCompletion()
            ids.forEach { taskDetailViewModel.refreshAfterTaskCompletion(it) }
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

    val navigationBlockers = remember { mutableStateOf(0) }
    var shortcutEditorOpen by rememberSaveable { mutableStateOf(false) }
    val shortcuts by remember(repository) { repository.navigationShortcuts.map(::navigationShortcutRoutes) }.collectAsState(initial = null)
    var navigationHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    var shortcutSlide by remember { mutableStateOf<ShortcutSlide?>(null) }
    LaunchedEffect(route) {
        if (shortcutSlide?.to != route) shortcutSlide = null
    }
    fun selectDestination(target: String, fromShortcut: Boolean = false) {
        shortcutSlide = if (fromShortcut) shortcutSlide(shortcuts.orEmpty(), route, target) else null
        if (target == route) return
        // Discard contextual drill-ins before saving a directory destination. Otherwise
        // restoring its stack would reopen a different destination above it.
        while (navController.previousBackStackEntry != null &&
            navController.currentBackStackEntry?.savedStateHandle?.get<Boolean>("directoryRoot") != true &&
            navController.previousBackStackEntry?.destination?.route != AppRoutes.Home) {
            if (!navController.popBackStack()) break
        }
        if (target == AppRoutes.Home) {
            navController.popBackStack(AppRoutes.Home, inclusive = false, saveState = true)
        } else {
            val destination = if (target == AppRoutes.DayPattern) AppRoutes.day(dayState.date) else target
            navController.navigate(destination) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            navController.currentBackStackEntry?.savedStateHandle?.set("directoryRoot", true)
        }
    }
    val colors = TimeboxTheme.colors
    val isImeVisible = imeVisibleOverride ?: WindowInsets.isImeVisible
    val accessibilityManager = LocalAccessibilityManager.current
    val recommendedUndoTimeoutMillis = accessibilityManager?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = 3_000L,
        containsIcons = false,
        containsText = true,
        containsControls = true,
    ) ?: 3_000L
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
                undoLifecycle.offer(context, if (offer.planChange) "plan change" else if (started) "activity start" else "activity switch",
                    if (offer.planChange) "Planned ${offer.name}" else if (started) "Started ${offer.name}" else "Switched to ${offer.name}", undo = {
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
        onOpenNotificationSettings, exactAlarmsAllowed, onRequestExactAlarms, reducedMotion, shortcutSlide,
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

    CompositionLocalProvider(LocalReadyToPlanRetry provides readinessCoordinator::retry, LocalNavigationBlockers provides navigationBlockers) {
    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        val editing = navigationBlockers.value > 0 || battlePlanState.showComposer || battlePlanState.projectEditor != null || typesState.renaming != null ||
            (route == AppRoutes.DayPattern && (dayState.sheetOpen || dayState.isPlanningMode))
        val showNavigation = !focused && navigationDestinations.any { it.route == route } &&
            shouldShowBottomNavigation(editing, isImeVisible, route == AppRoutes.TaskDetailPattern)
        val showDock = showNavigation && route != AppRoutes.Settings && shortcuts?.isNotEmpty() == true
        val feedbackBottom = if (showDock) navigationHeight + 12.dp else 24.dp
        if (!focused) Box(
            modifier = Modifier.fillMaxSize().imePadding().then(if (shortcutEditorOpen) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            CompositionLocalProvider(
                LocalNavigationDockInset provides if (showDock) navigationHeight else 0.dp,
                // Home has its own header without ScreenBackButton. Keep this callback
                // available while the outgoing destination fades away above Home.
                com.timebox.android.ui.components.LocalScreenBack provides if (showNavigation || route == AppRoutes.Home) ({
                    if (navController.previousBackStackEntry?.destination?.route == AppRoutes.Home) selectDestination(AppRoutes.Home)
                    else navController.popBackStack()
                    Unit
                }) else null,
            ) {
                TimeboxNavHost(navigation, { selectDestination(it) }, { shortcutEditorOpen = true })
            }

            if (showDock) shortcuts?.let { pins ->
                NavigationDock(pins, route, { selectDestination(it, fromShortcut = true) },
                    Modifier.align(Alignment.BottomCenter).onSizeChanged { navigationHeight = with(density) { it.height.toDp() } },
                    resetScroll = shortcutEditorOpen)
            }

        }

        if (route != AppRoutes.TaskDetailPattern) {
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = if (undoNotice == null) feedbackBottom else feedbackBottom + 80.dp)) { data ->
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
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = feedbackBottom),
                )
            }
        }

        if (focused) FocusMode(onTaskChanged = { dayViewModel.refreshAfterTaskCompletion(); battlePlanViewModel.refreshAfterTaskCompletion() })
        // Direct destinations return Home; contextual links keep their source.
        BackHandler(enabled = showNavigation && route != AppRoutes.Home &&
            navigationDestinations.any { it.route == route } &&
            navController.previousBackStackEntry?.destination?.route == AppRoutes.Home) {
            selectDestination(AppRoutes.Home)
        }
        if (shortcutEditorOpen && !focused) shortcuts?.let { pins ->
            NavigationShortcutEditor(pins, onCancel = { shortcutEditorOpen = false }, onSave = { updated ->
                repository.setNavigationShortcuts(updated)
                shortcutEditorOpen = false
            })
        }

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

internal fun routeKicker(route: String): String = when (route) {
    AppRoutes.DayPattern -> "Day"
    AppRoutes.Chronicle -> "Calendar"
    AppRoutes.Trends -> "Chronicle"
    AppRoutes.Habits, AppRoutes.TimeGoals -> "Routines and goals"
    AppRoutes.Types -> "Organization"
    AppRoutes.BattlePlan, AppRoutes.TaskDetailPattern -> "Daily work"
    AppRoutes.Recurring, AppRoutes.RecurringNew,
    AppRoutes.RecurringDetailPattern, AppRoutes.RecurringEditPattern -> "Routines and goals"
    AppRoutes.Assistant, AppRoutes.Review -> "Assistant"
    AppRoutes.Settings, AppRoutes.ThemePreview -> "Settings"
    else -> "Timebox"
}

internal fun routeTitle(route: String, day: String, chronicle: String): String =
    when (route) {
        AppRoutes.DayPattern -> day
        AppRoutes.Chronicle -> chronicle
        AppRoutes.Trends -> "Trends"
        AppRoutes.Habits -> "Habits"
        AppRoutes.TimeGoals -> "Time Goals"
        AppRoutes.Review -> "Review"
        AppRoutes.BattlePlan -> "Battle Plan"
        AppRoutes.TaskDetailPattern -> "Task details"
        AppRoutes.Recurring -> "Recurring Tasks"
        AppRoutes.RecurringNew -> "New recurrence"
        AppRoutes.RecurringDetailPattern -> "Template details"
        AppRoutes.RecurringEditPattern -> "Edit recurrence"
        AppRoutes.Types -> "Task Types"
        AppRoutes.Assistant -> "Conversations"
        AppRoutes.Settings -> "Preferences"
        AppRoutes.ThemePreview -> "Theme preview"
        else -> "Timebox"
    }
