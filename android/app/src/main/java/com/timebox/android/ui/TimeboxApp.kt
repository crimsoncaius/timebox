package com.timebox.android.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import com.timebox.android.ui.components.TransientFeedback
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.dialog
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.ui.assistant.AssistantScreen
import com.timebox.android.ui.chronicle.ChronicleScreen
import com.timebox.android.ui.chronicle.ChronicleView
import com.timebox.android.ui.chronicle.ChronicleViewModel
import com.timebox.android.ui.battleplan.BattlePlanScreen
import com.timebox.android.ui.battleplan.BattlePlanTrashUndoNotice
import com.timebox.android.ui.battleplan.BattlePlanViewModel
import com.timebox.android.ui.battleplan.RecurringDetailScreen
import com.timebox.android.ui.battleplan.RecurringEditorScreen
import com.timebox.android.ui.battleplan.RecurringEditorViewModel
import com.timebox.android.ui.battleplan.RecurringScreen
import com.timebox.android.ui.battleplan.RecurringViewModel
import com.timebox.android.ui.battleplan.TaskDetailScreen
import com.timebox.android.ui.battleplan.TaskNavigationMenu
import com.timebox.android.ui.battleplan.TaskDetailViewModel
import com.timebox.android.ui.components.TimeboxBottomNav
import com.timebox.android.ui.components.TimeboxTab
import com.timebox.android.ui.components.TimeboxTopBar
import com.timebox.android.ui.day.DayScreen
import com.timebox.android.ui.day.DayViewModel
import com.timebox.android.ui.day.WorkModeScreen
import com.timebox.android.ui.day.WorkModeEntryDialog
import com.timebox.android.ui.day.WorkModeRestoreDialog
import com.timebox.android.ui.readiness.ReadyToPlanCoordinator
import com.timebox.android.ui.readiness.LocalReadyToPlanRetry
import com.timebox.android.ui.settings.SettingsScreen
import com.timebox.android.ui.settings.SettingsViewModel
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.theme.ThemePreviewScreen
import com.timebox.android.ui.types.TypesScreen
import com.timebox.android.ui.types.TypesViewModel
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.time.LocalDate
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
    val activityRepository = if (com.timebox.android.BuildConfig.ACTIVITY_TRACKING_DEV) (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.timebox.android.TimeboxApplication).activityRepository else null
    val factory = remember(repository, taskCompletion, readinessCoordinator, activityRepository) {
        timeboxViewModelFactory(repository, taskCompletion, readinessCoordinator, activityRepository)
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
    val focusController = if (activityRepository != null) (LocalContext.current.applicationContext as com.timebox.android.TimeboxApplication).focusController else null
    val focusState = focusController?.state?.collectAsState()?.value
    val currentActivity = activityRepository?.state?.collectAsState()?.value
    val focused = focusState?.active == true && currentActivity?.snapshot?.current != null && !dayState.focusPlanningBlocked
    val checkIns = if (activityRepository != null) (LocalContext.current.applicationContext as com.timebox.android.TimeboxApplication).checkIns else null
    val requestedCheckIn = checkIns?.openQuestion?.collectAsState()?.value
    LaunchedEffect(requestedCheckIn) {
        if (requestedCheckIn != null && !focused && activityRepository != null) {
            val zone = java.time.ZoneId.of(activityRepository.state.value.snapshot?.reportingTimezone ?: "UTC")
            navController.navigate(AppRoutes.day(activityRepository.now().atZone(zone).toLocalDate())) { launchSingleTop = true }
        }
    }
    val activityApplication = LocalContext.current.applicationContext as com.timebox.android.TimeboxApplication
    LaunchedEffect(currentActivity, dayState.focusPlanningBlocked) {
        if (activityRepository != null) {
            activityApplication.recoverLegacyWorkMode(dayViewModel.state.value.focusPlanningBlocked)
            focusController?.reconcile(activityRepository, dayViewModel.state.value.focusPlanningBlocked)
        }
    }

    val chronicleState by chronicleViewModel.state.collectAsState()
    val typesState by typesViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val battlePlanState by battlePlanViewModel.state.collectAsState()
    val taskDetailState by taskDetailViewModel.state.collectAsState()
    val taskCompletionNotice by taskCompletion.notice.collectAsState()
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
    val taskCompletionScope = rememberCoroutineScope()
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
                chronicleViewModel.load()
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
            AppRoutes.RecurringDetailPattern -> routeTemplateId?.let(recurringViewModel::openDetail)
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
            val result = snackbarHostState.showSnackbar(
                message = notice.message,
                actionLabel = if (notice.canUndo) "Undo" else null,
                duration = taskCompletionSnackbarDuration(),
            )
            if (result == SnackbarResult.ActionPerformed && notice.canUndo) {
                taskCompletionScope.launch {
                    taskCompletion.undo(notice.id).onSuccess { task ->
                        dayViewModel.refreshAfterTaskCompletion()
                        battlePlanViewModel.refreshAfterTaskCompletion()
                        taskDetailViewModel.refreshAfterTaskCompletion(task.id)
                    }
                }
            } else {
                taskCompletion.dismiss(notice.id)
            }
        }
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
    LaunchedEffect(withinBattlePlan, appResumed, recommendedUndoTimeoutMillis) {
        if (!withinBattlePlan) battlePlanViewModel.dismissUndo()
        battlePlanViewModel.setUndoExposureActive(
            active = withinBattlePlan && appResumed,
            recommendedTimeoutMillis = recommendedUndoTimeoutMillis,
        )
    }

    CompositionLocalProvider(LocalReadyToPlanRetry provides readinessCoordinator::retry) {
    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        if (!focused) Column(
            modifier = Modifier.fillMaxSize().imePadding().then(
                if (dayState.workMode != null && dayState.workModeVisible) Modifier.clearAndSetSemantics { } else Modifier
            )
        ) {
            if (surfaceRoute != AppRoutes.DayPattern &&
                surfaceRoute != AppRoutes.RecurringNew &&
                surfaceRoute != AppRoutes.RecurringEditPattern &&
                surfaceRoute != AppRoutes.RecurringDetailPattern &&
                surfaceRoute?.startsWith("prototype/") != true) {
                TimeboxTopBar(
                    kicker = routeKicker(surfaceRoute),
                    title = routeTitle(
                        surfaceRoute,
                        formatFullDate(dayState.date),
                        if (chronicleState.view == ChronicleView.Trends) "Trends" else formatMonthTitle(chronicleState.monthStart),
                    ),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                NavHost(navController, startDestination = AppRoutes.DayPattern) {
                    if (com.timebox.android.BuildConfig.DEBUG) {
                        composable(
                            "prototype/task-sheet?mode={mode}&layout={layout}&sample={sample}",
                            deepLinks = listOf(navDeepLink { uriPattern = "timebox://prototype/task-sheet?mode={mode}&layout={layout}&sample={sample}" }),
                            arguments = listOf(navArgument("mode") { defaultValue = "details" }, navArgument("layout") { defaultValue = "full" }, navArgument("sample") { defaultValue = "normal" }),
                        ) { entry ->
                            com.timebox.android.ui.battleplan.TaskSheetPrototype(entry.arguments?.getString("mode") == "create", entry.arguments?.getString("layout") ?: "full", entry.arguments?.getString("sample") ?: "normal")
                        }
                        composable(
                            "prototype/recurring-details?flow={flow}&layout={layout}&mode={mode}",
                            deepLinks = listOf(navDeepLink { uriPattern = "timebox://prototype/recurring-details?flow={flow}&layout={layout}&mode={mode}" }),
                            arguments = listOf(
                                navArgument("flow") { defaultValue = "edit" },
                                navArgument("layout") { defaultValue = "rows" },
                                navArgument("mode") { defaultValue = "scheduled" },
                            ),
                        ) { entry ->
                            com.timebox.android.ui.battleplan.RecurringDetailsHierarchyPrototype(
                                initialFlow = entry.arguments?.getString("flow") ?: "edit",
                                initialScenario = entry.arguments?.getString("mode") ?: "scheduled",
                            )
                        }
                    }
                    composable(
                        AppRoutes.DayPattern,
                        deepLinks = listOf(navDeepLink { uriPattern = AppRoutes.DayDeepLinkPattern }),
                        arguments = listOf(navArgument(AppRoutes.DateArg) {
                            type = NavType.StringType
                            defaultValue = LocalDate.now().toString()
                        }, navArgument(AppRoutes.BlockIdArg) {
                            type = NavType.IntType
                            defaultValue = -1
                        }),
                    ) {
                        DayScreen(
                            state = dayState,
                            // Date changes are state within the mounted Day destination. Replacing
                            // the route here recreated the header and also re-ran Day initialization,
                            // including a duplicate day load and unrelated task refreshes.
                            onNavigateToday = { dayViewModel.goToDate(it, scrollToNow = true) },
                            onDateSettled = { date ->
                                dayViewModel.goToDate(date, scrollToNow = date == dayState.today)
                            },
                            onRetry = dayViewModel::retryPage,
                            onTapSlot = { lane: Lane, minute: Int -> dayViewModel.startDraft(lane, minute) },
                            onSelectBlock = dayViewModel::selectBlock,
                            onCommitMove = dayViewModel::moveBlock,
                            onDismissSheet = dayViewModel::closeSheet,
                            onChooseType = dayViewModel::chooseTaskType,
                            onTypeQueryChange = dayViewModel::onTypeQueryChange,
                            onCreateType = dayViewModel::createTaskTypeAndChoose,
                            onNameChange = dayViewModel::onNameChange,
                            onNoteChange = dayViewModel::onNoteChange,
                            onCreateDraft = dayViewModel::createTasklessPlannedDraft,
                            onDeleteSelected = dayViewModel::deleteSelected,
                            onRecordPlanned = dayViewModel::recordPlanned,
                            onCancelRecording = dayViewModel::cancelRecordingPreview,
                            onUndoRecording = dayViewModel::undoRecording,
                            onConfirmSelectedTaskCompletion = dayViewModel::completeSelectedTask,
                            onReopenSelectedTask = dayViewModel::reopenSelectedTask,
                            onOpenLinkedTask = { taskId ->
                                dayViewModel.closeSheet()
                                navController.navigate(AppRoutes.taskDetail(taskId))
                            },
                            onSetPlanningMode = dayViewModel::setPlanningMode,
                            onCommitPlanningMode = dayViewModel::commitPlanningSession,
                            onCancelPlanningMode = dayViewModel::cancelPlanningSession,
                            onPlanTask = dayViewModel::planTaskAt,
                            onDropPlanningTask = dayViewModel::dropPlanningTask,
                            onUpdatePlanningDraft = dayViewModel::updatePlanningDraft,
                            onReturnPlanningDraft = dayViewModel::returnPlanningDraft,
                            onArmAccessibleTask = dayViewModel::armAccessiblePlanningTask,
                            onRetryReadyTasks = dayViewModel::refreshReadyToPlan,
                            onOpenWorkMode = dayViewModel::startWorkMode,
                            onEnterFocus = { trackingScope.launch { if (activityRepository != null) focusController?.enter(activityRepository) { dayViewModel.state.value.focusPlanningBlocked } } },
                        )
                    }
                    composable(AppRoutes.Chronicle) {
                        ChronicleScreen(
                            state = chronicleState,
                            onPrevMonth = { chronicleViewModel.shiftMonth(-1) },
                            onNextMonth = { chronicleViewModel.shiftMonth(1) },
                            onThisMonth = chronicleViewModel::goToThisMonth,
                            onSelectView = chronicleViewModel::selectView,
                            onOpenDay = { navController.navigate(AppRoutes.day(it)) },
                            onRetry = chronicleViewModel::load,
                        )
                    }
                    composable(AppRoutes.BattlePlan) {
                        BattlePlanScreen(
                            state = battlePlanState,
                            onRetry = { battlePlanViewModel.load() },
                            onSelectCollection = battlePlanViewModel::selectCollection,
                            onSelectScope = battlePlanViewModel::selectScope,
                            onReorderProjects = battlePlanViewModel::reorderProjects,
                            onSelectStatus = battlePlanViewModel::selectStatus,
                            onToggleUrgency = battlePlanViewModel::toggleUrgency,
                            onToggleImportance = battlePlanViewModel::toggleImportance,
                            onToggleTaskType = battlePlanViewModel::toggleTaskType,
                            onClearFilters = battlePlanViewModel::clearFilters,
                            onSetHideCompleted = battlePlanViewModel::setHideCompleted,
                            onArchiveCompleted = battlePlanViewModel::archiveCompleted,
                            onOpenTask = { navController.navigate(AppRoutes.taskDetail(it)) },
                            onToggleReady = battlePlanViewModel::toggleReady,
                            onMoveProject = battlePlanViewModel::moveProject,
                            onMoveTask = battlePlanViewModel::moveTask,
                            onReorderTask = battlePlanViewModel::reorderTask,
                            onMoveTaskToBoundary = battlePlanViewModel::moveTaskToBoundary,
                            onDropTask = battlePlanViewModel::dropTask,
                            onSetBlocked = battlePlanViewModel::setBlocked,
                            onCreateSubtask = battlePlanViewModel::createSubtask,
                            onToggleSubtask = battlePlanViewModel::toggleSubtaskComplete,
                            onCreateTask = { _, _, _ -> battlePlanViewModel.createTask() },
                            onShowComposer = battlePlanViewModel::setComposerVisible,
                            onComposerDraftChange = battlePlanViewModel::updateComposerDraft,
                            onComposerReminderEnabledChange = battlePlanViewModel::setComposerReminderEnabled,
                            onCreateComposerTaskType = battlePlanViewModel::createComposerTaskType,
                            notificationsAllowed = notificationsAllowed,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenRecurring = { navController.navigate(AppRoutes.Recurring) },
                            onOpenTaskTypes = { navController.navigate(AppRoutes.Types) },
                            onNewProject = battlePlanViewModel::startProjectCreation,
                            onProjectNameChange = battlePlanViewModel::setProjectName,
                            onSaveProject = battlePlanViewModel::saveProject,
                            onCancelProjectEditor = battlePlanViewModel::cancelProjectEditor,
                            onDismissProjectEditor = battlePlanViewModel::dismissProjectEditor,
                            onEditProject = battlePlanViewModel::editProject,
                            onPrepareDeleteProject = battlePlanViewModel::prepareProjectDelete,
                            onDismissDeleteProject = battlePlanViewModel::dismissProjectDelete,
                            onConfirmDeleteProject = battlePlanViewModel::confirmProjectDelete,
                            onRestoreArchived = battlePlanViewModel::restoreArchived,
                            onRestoreTrashed = battlePlanViewModel::restoreTrashed,
                            onUndoTrash = { battlePlanViewModel.undoTrash() },
                            onDismissUndo = { battlePlanViewModel.dismissUndo() },
                            onRequestTrash = battlePlanViewModel::requestTrash,
                            onDismissTrash = battlePlanViewModel::dismissTrash,
                            onConfirmTrash = battlePlanViewModel::confirmTrash,
                            onRequestPermanentDelete = battlePlanViewModel::requestPermanentDelete,
                            onDismissPermanentDelete = battlePlanViewModel::dismissPermanentDelete,
                            onConfirmPermanentDelete = battlePlanViewModel::confirmPermanentDelete,
                        )
                    }
                    dialog(
                        AppRoutes.TaskDetailPattern,
                        dialogProperties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
                        arguments = listOf(navArgument(AppRoutes.TaskIdArg) { type = NavType.IntType }),
                        deepLinks = listOf(navDeepLink { uriPattern = AppRoutes.TaskDeepLinkPattern }),
                    ) {
                        val taskId = it.arguments?.getInt(AppRoutes.TaskIdArg) ?: return@dialog
                        TaskDetailScreen(
                            state = taskDetailState,
                            onTrackTask = if (com.timebox.android.BuildConfig.ACTIVITY_TRACKING_DEV && taskDetailState.task?.let { it.status != com.timebox.android.data.TaskStatus.Completed && it.recurrenceKind != "quota_parent" && (it.parentId == null || it.recurrenceKind == "quota_session") } == true) ({
                                trackingScope.launch {
                                    val activity = (context.applicationContext as com.timebox.android.TimeboxApplication).activityRepository
                                    val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { activity.trackTask(taskDetailState.task!!) }
                                    if (saved) navController.navigate(AppRoutes.day(LocalDate.now()))
                                    else snackbarHostState.showSnackbar(activity.state.value.error ?: "Could not start tracking")
                                }
                            }) else null,
                            onBack = { navController.popBackStack() },
                            onRetry = { taskDetailViewModel.load(taskId) },
                            onOpenTask = { navController.navigate(AppRoutes.taskDetail(it)) },
                            onTitleChange = taskDetailViewModel::setTitle,
                            onDescriptionChange = taskDetailViewModel::setDescription,
                            onStatusChange = taskDetailViewModel::setStatus,
                            onProjectChange = taskDetailViewModel::setProject,
                            onTaskTypeChange = taskDetailViewModel::setTaskType,
                            onUrgencyChange = taskDetailViewModel::setUrgency,
                            onImportanceChange = taskDetailViewModel::setImportance,
                            onDeadlineModeChange = taskDetailViewModel::setDeadlineMode,
                            onDeadlineDateChange = taskDetailViewModel::setDeadlineDate,
                            onDeadlineTimeChange = taskDetailViewModel::setDeadlineTime,
                            onReminderEnabledChange = { enabled ->
                                if (enabled && !notificationsAllowed) onRequestNotificationPermission()
                                taskDetailViewModel.setReminderEnabled(enabled)
                            },
                            notificationsAllowed = notificationsAllowed,
                            onReminderDateChange = taskDetailViewModel::setReminderDate,
                            onReminderTimeChange = taskDetailViewModel::setReminderTime,
                            onReadyChange = taskDetailViewModel::setReadyImmediately,
                            onOpenDay = { date, blockId -> navController.navigate(AppRoutes.day(date, blockId)) },
                            onAddSubtask = taskDetailViewModel::addSubtask,
                            onToggleSubtask = taskDetailViewModel::toggleSubtask,
                            onTrashSubtask = taskDetailViewModel::requestSubtaskTrash,
                            onStartSubtaskRename = taskDetailViewModel::startSubtaskRename,
                            onRenameSubtask = taskDetailViewModel::renameSubtask,
                            onDismissSubtaskRename = taskDetailViewModel::dismissSubtaskRename,
                            onDismissSubtaskTrash = taskDetailViewModel::dismissSubtaskTrash,
                            onConfirmSubtaskTrash = taskDetailViewModel::confirmSubtaskTrash,
                            onUndoSubtaskTrash = {},
                            onRequestTrash = taskDetailViewModel::requestTrash,
                            onDismissTrash = taskDetailViewModel::dismissTrash,
                            onConfirmTrash = taskDetailViewModel::confirmTrash,
                            onTrashed = {},
                            onStartEditing = taskDetailViewModel::startEditing,
                            onDiscardChanges = taskDetailViewModel::discardChanges,
                            onUseLatestTask = taskDetailViewModel::useLatestTask,
                            onRestoreRecoveredDraft = taskDetailViewModel::restoreRecoveredDraft,
                            onComplete = taskDetailViewModel::completeTask,
                            onReopen = taskDetailViewModel::reopenTask,
                            onSave = taskDetailViewModel::save,
                            onSaveField = taskDetailViewModel::saveField,
                            onCreateTaskType = taskDetailViewModel::createTaskTypeAndChoose,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            feedback = {
                                val notice = battlePlanState.trashUndo
                                if (notice != null) BattlePlanTrashUndoNotice(
                                    notice = notice,
                                    onUndo = { battlePlanViewModel.undoTrash(notice.noticeId) },
                                    onDismiss = { battlePlanViewModel.dismissUndo(notice.noticeId) },
                                    onExpiryFinished = { battlePlanViewModel.finishUndoExpiry(notice.noticeId) },
                                    reducedMotion = reducedMotion,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) else SnackbarHost(snackbarHostState, Modifier.padding(horizontal = 16.dp)) { data ->
                                    TransientFeedback(
                                        message = data.visuals.message,
                                        modifier = Modifier.semantics { paneTitle = "Feedback"; dismiss { data.dismiss(); true } },
                                        actionLabel = data.visuals.actionLabel,
                                        onAction = data::performAction,
                                        onDismiss = if (data.visuals.withDismissAction) data::dismiss else null,
                                    )
                                }
                            },
                        )
                    }
                    composable(AppRoutes.Recurring) {
                        fun returnToTasks() {
                            navController.popBackStack(AppRoutes.BattlePlan, inclusive = false)
                        }
                        RecurringScreen(
                            state = recurringState,
                            navigationState = battlePlanState,
                            onSelectScope = {
                                battlePlanViewModel.selectCollection(com.timebox.android.data.TaskCollection.Active)
                                battlePlanViewModel.selectScope(it)
                                returnToTasks()
                            },
                            onSelectCollection = { battlePlanViewModel.selectCollection(it); returnToTasks() },
                            onReorderProjects = battlePlanViewModel::reorderProjects,
                            onEditProject = { battlePlanViewModel.editProject(it); returnToTasks() },
                            onPrepareDeleteProject = { battlePlanViewModel.prepareProjectDelete(it); returnToTasks() },
                            onNewProject = { battlePlanViewModel.startProjectCreation(); returnToTasks() },
                            onOpenTaskTypes = {
                                navController.navigate(AppRoutes.Types) { popUpTo(AppRoutes.BattlePlan) }
                            },
                            onRetry = { recurringViewModel.load() },
                            onSelectStatus = recurringViewModel::selectStatus,
                            onNew = { navController.navigate(AppRoutes.RecurringNew) },
                            onOpen = { navController.navigate(AppRoutes.recurringDetail(it)) },
                            onRequestDelete = { recurringViewModel.requestDelete(it) },
                            onDismissDelete = recurringViewModel::dismissDelete,
                            onConfirmDelete = { recurringViewModel.confirmDelete() },
                        )
                    }
                    composable(AppRoutes.RecurringNew) {
                        RecurringEditorScreen(
                            state = recurringEditorState,
                            onBack = { navController.popBackStack() },
                            onRetry = { recurringEditorViewModel.open(null) },
                            onTitle = recurringEditorViewModel::setTitle,
                            onDescription = recurringEditorViewModel::setDescription,
                            onTaskType = recurringEditorViewModel::setTaskType,
                            onCreateTaskType = recurringEditorViewModel::createTaskTypeAndChoose,
                            onUrgency = recurringEditorViewModel::setUrgency,
                            onImportance = recurringEditorViewModel::setImportance,
                            onMode = recurringEditorViewModel::setMode,
                            onFrequency = recurringEditorViewModel::setFrequency,
                            onInterval = recurringEditorViewModel::setInterval,
                            onToggleWeekday = recurringEditorViewModel::toggleWeekday,
                            onMonthDay = recurringEditorViewModel::setMonthDay,
                            onQuotaCount = recurringEditorViewModel::setQuotaCount,
                            onStartDate = recurringEditorViewModel::setStartDate,
                            onEndMode = recurringEditorViewModel::setEndMode,
                            onEndDate = recurringEditorViewModel::setEndDate,
                            onCycleLimit = recurringEditorViewModel::setCycleLimit,
                            onChecklist = recurringEditorViewModel::setChecklistText,
                            onKeepUnfinishedOverdue = recurringEditorViewModel::setKeepUnfinishedOverdue,
                            onPreplanningEnabled = recurringEditorViewModel::setPreplanningEnabled,
                            onAddPreplanningSlot = recurringEditorViewModel::addPreplanningSlot,
                            onRemovePreplanningSlot = recurringEditorViewModel::removePreplanningSlot,
                            onPreplanningStart = recurringEditorViewModel::setPreplanningStart,
                            onPreplanningEnd = recurringEditorViewModel::setPreplanningEnd,
                            onPreplanningWeekday = recurringEditorViewModel::setPreplanningWeekday,
                            onRefreshPreview = recurringEditorViewModel::refreshPreview,
                            onSave = { recurringEditorViewModel.save() },
                            onConfirmBackfill = { recurringEditorViewModel.save(confirmBackfill = true) },
                            onDismissBackfill = recurringEditorViewModel::dismissBackfill,
                        )
                    }
                    composable(
                        AppRoutes.RecurringDetailPattern,
                        arguments = listOf(navArgument(AppRoutes.TemplateIdArg) { type = NavType.IntType }),
                    ) { entry ->
                        val templateId = entry.arguments?.getInt(AppRoutes.TemplateIdArg) ?: return@composable
                        RecurringDetailScreen(
                            state = recurringState,
                            onBack = { navController.popBackStack() },
                            onRetry = { recurringViewModel.openDetail(templateId) },
                            onEdit = { navController.navigate(AppRoutes.recurringEdit(it)) },
                            onOpenTask = { navController.navigate(AppRoutes.taskDetail(it)) },
                            onPause = recurringViewModel::pause,
                            onResume = recurringViewModel::resume,
                            onEnd = recurringViewModel::end,
                            onRequestDelete = { recurringViewModel.requestDelete() },
                            onDismissDelete = recurringViewModel::dismissDelete,
                            onConfirmDelete = {
                                recurringViewModel.confirmDelete { navController.popBackStack() }
                            },
                        )
                    }
                    composable(
                        AppRoutes.RecurringEditPattern,
                        arguments = listOf(navArgument(AppRoutes.TemplateIdArg) { type = NavType.IntType }),
                    ) { entry ->
                        val templateId = entry.arguments?.getInt(AppRoutes.TemplateIdArg) ?: return@composable
                        RecurringEditorScreen(
                            state = recurringEditorState,
                            onBack = { navController.popBackStack() },
                            onRetry = { recurringEditorViewModel.open(templateId) },
                            onTitle = recurringEditorViewModel::setTitle,
                            onDescription = recurringEditorViewModel::setDescription,
                            onTaskType = recurringEditorViewModel::setTaskType,
                            onCreateTaskType = recurringEditorViewModel::createTaskTypeAndChoose,
                            onUrgency = recurringEditorViewModel::setUrgency,
                            onImportance = recurringEditorViewModel::setImportance,
                            onMode = recurringEditorViewModel::setMode,
                            onFrequency = recurringEditorViewModel::setFrequency,
                            onInterval = recurringEditorViewModel::setInterval,
                            onToggleWeekday = recurringEditorViewModel::toggleWeekday,
                            onMonthDay = recurringEditorViewModel::setMonthDay,
                            onQuotaCount = recurringEditorViewModel::setQuotaCount,
                            onStartDate = recurringEditorViewModel::setStartDate,
                            onEndMode = recurringEditorViewModel::setEndMode,
                            onEndDate = recurringEditorViewModel::setEndDate,
                            onCycleLimit = recurringEditorViewModel::setCycleLimit,
                            onChecklist = recurringEditorViewModel::setChecklistText,
                            onKeepUnfinishedOverdue = recurringEditorViewModel::setKeepUnfinishedOverdue,
                            onPreplanningEnabled = recurringEditorViewModel::setPreplanningEnabled,
                            onAddPreplanningSlot = recurringEditorViewModel::addPreplanningSlot,
                            onRemovePreplanningSlot = recurringEditorViewModel::removePreplanningSlot,
                            onPreplanningStart = recurringEditorViewModel::setPreplanningStart,
                            onPreplanningEnd = recurringEditorViewModel::setPreplanningEnd,
                            onPreplanningWeekday = recurringEditorViewModel::setPreplanningWeekday,
                            onRefreshPreview = recurringEditorViewModel::refreshPreview,
                            onSave = { recurringEditorViewModel.save() },
                            onConfirmBackfill = { recurringEditorViewModel.save(confirmBackfill = true) },
                            onDismissBackfill = recurringEditorViewModel::dismissBackfill,
                        )
                    }
                    composable(AppRoutes.Types) {
                        fun returnToTasks() {
                            if (!navController.popBackStack(AppRoutes.BattlePlan, inclusive = false)) {
                                navController.navigate(AppRoutes.BattlePlan) { popUpTo(AppRoutes.Types) { inclusive = true } }
                            }
                        }
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                TaskNavigationMenu(
                                    state = battlePlanState,
                                    taskTypes = true,
                                    onSelectScope = {
                                        battlePlanViewModel.selectCollection(com.timebox.android.data.TaskCollection.Active)
                                        battlePlanViewModel.selectScope(it)
                                        returnToTasks()
                                    },
                                    onSelectCollection = { battlePlanViewModel.selectCollection(it); returnToTasks() },
                                    onOpenRecurring = {
                                        navController.navigate(AppRoutes.Recurring) { popUpTo(AppRoutes.BattlePlan) }
                                    },
                                    onReorderProjects = battlePlanViewModel::reorderProjects,
                                    onEditProject = { battlePlanViewModel.editProject(it); returnToTasks() },
                                    onPrepareDeleteProject = { battlePlanViewModel.prepareProjectDelete(it); returnToTasks() },
                                    onNewProject = { battlePlanViewModel.startProjectCreation(); returnToTasks() },
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                TypesScreen(
                                    state = typesState,
                                    onInputChange = typesViewModel::onInputChange,
                                    onAdd = typesViewModel::addType,
                                    onDelete = typesViewModel::deleteType,
                                    onConfirmCascade = typesViewModel::confirmCascadeDelete,
                                    onMigrateTarget = typesViewModel::setMigrateBlocksTo,
                                    onConfirmMigrate = typesViewModel::confirmMigrateDelete,
                                    onDismissCascade = typesViewModel::dismissCascadePrompt,
                                    onRetry = typesViewModel::load,
                                    onRename = typesViewModel::beginRename,
                                    onRenameChange = typesViewModel::changeRename,
                                    onSaveRename = typesViewModel::saveRename,
                                    onCancelRename = typesViewModel::cancelRename,
                                )
                            }
                        }
                    }
                    composable(AppRoutes.Assistant) {
                        AssistantScreen()
                    }
                    composable(AppRoutes.Settings) {
                        SettingsScreen(
                            state = settingsState,
                            isDark = isDark,
                            onToggleDark = onToggleDark,
                            onStartHourDelta = settingsViewModel::adjustStartHour,
                            onEndHourDelta = settingsViewModel::adjustEndHour,
                            onToggleFullDay = settingsViewModel::toggleFullDay,
                            onDailyReminderChange = settingsViewModel::updateDailyReminder,
                            onPlannedBlockRemindersChange = { next ->
                                if (next.enabled && !settingsState.plannedBlockReminders.enabled && !exactAlarmsAllowed) onRequestExactAlarms()
                                settingsViewModel.updatePlannedBlockReminders(next)
                            },
                            exactAlarmsAllowed = exactAlarmsAllowed,
                            onRequestExactAlarms = onRequestExactAlarms,
                            onBaseUrlChange = settingsViewModel::onBaseUrlChange,
                            onApiKeyChange = settingsViewModel::onApiKeyChange,
                            onReportingZoneChange = settingsViewModel::changeReportingZone,
                            onSaveReportingZone = { settingsViewModel.saveReportingZone { dayViewModel.load(showSpinner = true) } },
                            onSaveConnection = {
                                settingsViewModel.saveConnection()
                                dayViewModel.load(showSpinner = true)
                            },
                            notificationsAllowed = notificationsAllowed,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenNotificationSettings = onOpenNotificationSettings,
                            onOpenThemePreview = { navController.navigate(AppRoutes.ThemePreview) },
                            onRetry = { settingsViewModel.load(dayState.day?.timezone) },
                        )
                    }
                    composable(AppRoutes.ThemePreview) {
                        ThemePreviewScreen(onBack = { navController.popBackStack() })
                    }
                }
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

        // Keep the queued snackbar available while task-scoped Trash recovery owns the slot.
        if (route != AppRoutes.TaskDetailPattern && (!withinBattlePlan || battlePlanState.trashUndo == null)) {
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 92.dp)) { data ->
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

        if (withinBattlePlan && route != AppRoutes.TaskDetailPattern) {
            battlePlanState.trashUndo?.let { notice ->
                BattlePlanTrashUndoNotice(
                    notice = notice,
                    onUndo = { battlePlanViewModel.undoTrash(notice.noticeId) },
                    onDismiss = { battlePlanViewModel.dismissUndo(notice.noticeId) },
                    onExpiryFinished = { battlePlanViewModel.finishUndoExpiry(notice.noticeId) },
                    reducedMotion = reducedMotion,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 92.dp),
                )
            }
        }

        if (focused) com.timebox.android.ui.focus.FocusMode(onTaskChanged = { dayViewModel.refreshAfterTaskCompletion(); battlePlanViewModel.refreshAfterTaskCompletion() })
        dayState.workMode?.takeIf { dayState.workModeVisible }?.let { workMode ->
            WorkModeScreen(
                state = workMode,
                onToggleSubtask = dayViewModel::toggleWorkModeSubtask,
                onExit = dayViewModel::exitWorkMode,
            )
        }
        if (dayState.workModeEntryWarning) {
            WorkModeEntryDialog(
                onPlanFirst = dayViewModel::planSomethingBeforeWorkMode,
                onContinue = dayViewModel::continueWorkModeEntry,
            )
        }
        if (dayState.workModeRestorePrompt) {
            WorkModeRestoreDialog(
                onDecline = dayViewModel::declineWorkContinued,
                onConfirm = dayViewModel::confirmWorkContinued,
            )
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
