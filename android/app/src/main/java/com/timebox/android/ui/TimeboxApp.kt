package com.timebox.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.timebox.android.data.Lane
import com.timebox.android.ui.chronicle.ChronicleScreen
import com.timebox.android.ui.chronicle.ChronicleViewModel
import com.timebox.android.ui.battleplan.BattlePlanScreen
import com.timebox.android.ui.battleplan.BattlePlanViewModel
import com.timebox.android.ui.battleplan.ProjectEditorScreen
import com.timebox.android.ui.battleplan.ProjectEditorViewModel
import com.timebox.android.ui.battleplan.RecurringDetailScreen
import com.timebox.android.ui.battleplan.RecurringEditorScreen
import com.timebox.android.ui.battleplan.RecurringEditorViewModel
import com.timebox.android.ui.battleplan.RecurringScreen
import com.timebox.android.ui.battleplan.RecurringViewModel
import com.timebox.android.ui.battleplan.TaskDetailScreen
import com.timebox.android.ui.battleplan.TaskDetailViewModel
import com.timebox.android.ui.components.TimeboxBottomNav
import com.timebox.android.ui.components.TimeboxTab
import com.timebox.android.ui.components.TimeboxTopBar
import com.timebox.android.ui.day.DayScreen
import com.timebox.android.ui.day.DayViewModel
import com.timebox.android.ui.day.WorkModeScreen
import com.timebox.android.ui.day.WorkModeEntryDialog
import com.timebox.android.ui.day.WorkModeRestoreDialog
import com.timebox.android.ui.settings.SettingsScreen
import com.timebox.android.ui.settings.SettingsViewModel
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.theme.ThemePreviewScreen
import com.timebox.android.ui.types.TypesScreen
import com.timebox.android.ui.types.TypesViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun TimeboxApp(
    isDark: Boolean,
    onToggleDark: () -> Unit,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val repository = rememberRepository()
    val taskCompletion = rememberTaskCompletion()
    val factory = remember(repository, taskCompletion) { timeboxViewModelFactory(repository, taskCompletion) }
    val navController = rememberNavController()

    val dayViewModel: DayViewModel = viewModel(factory = factory)
    val chronicleViewModel: ChronicleViewModel = viewModel(factory = factory)
    val typesViewModel: TypesViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val battlePlanViewModel: BattlePlanViewModel = viewModel(factory = factory)
    val taskDetailViewModel: TaskDetailViewModel = viewModel(factory = factory)
    val projectEditorViewModel: ProjectEditorViewModel = viewModel(factory = factory)
    val recurringViewModel: RecurringViewModel = viewModel(factory = factory)
    val recurringEditorViewModel: RecurringEditorViewModel = viewModel(factory = factory)
    val dayState by dayViewModel.state.collectAsState()
    val chronicleState by chronicleViewModel.state.collectAsState()
    val typesState by typesViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val battlePlanState by battlePlanViewModel.state.collectAsState()
    val taskDetailState by taskDetailViewModel.state.collectAsState()
    val taskCompletionNotice by taskCompletion.notice.collectAsState()
    val projectEditorState by projectEditorViewModel.state.collectAsState()
    val recurringState by recurringViewModel.state.collectAsState()
    val recurringEditorState by recurringEditorViewModel.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: AppRoutes.DayPattern
    val routeDate = backStackEntry?.arguments?.getString(AppRoutes.DateArg)?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }
    val routeTaskId = backStackEntry?.arguments?.getInt(AppRoutes.TaskIdArg)
    val routeBlockId = backStackEntry?.arguments?.getInt(AppRoutes.BlockIdArg)?.takeIf { it >= 0 }
    val routeProjectId = backStackEntry?.arguments?.getInt(AppRoutes.ProjectIdArg)
    val routeTemplateId = backStackEntry?.arguments?.getInt(AppRoutes.TemplateIdArg)
    val snackbarHostState = remember { SnackbarHostState() }
    val taskCompletionScope = rememberCoroutineScope()
    val openedDayEntryIds = remember { mutableSetOf<String>() }

    LaunchedEffect(route, routeDate, routeTaskId, routeProjectId, routeTemplateId, backStackEntry?.id) {
        when (route) {
            AppRoutes.DayPattern -> {
                val entryId = backStackEntry?.id ?: return@LaunchedEffect
                // A Day entry's argument seeds selection once. Returning from another tab
                // must retain any newer date selected inside the mounted screen.
                if (openedDayEntryIds.add(entryId)) routeDate?.let(dayViewModel::goToDate)
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
            AppRoutes.BattlePlan -> battlePlanViewModel.load()
            AppRoutes.TaskDetailPattern -> routeTaskId?.let(taskDetailViewModel::load)
            AppRoutes.ProjectNew -> projectEditorViewModel.open(null)
            AppRoutes.ProjectDetailPattern -> projectEditorViewModel.open(routeProjectId)
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
            val result = snackbarHostState.showSnackbar(message = notice.message, actionLabel = "Open")
            battlePlanViewModel.consumeCreatedTaskNotice()
            if (result == SnackbarResult.ActionPerformed) navController.navigate(AppRoutes.taskDetail(notice.taskId))
        }
    }
    LaunchedEffect(taskDetailState.message) {
        taskDetailState.message?.let { snackbarHostState.showSnackbar(it); taskDetailViewModel.consumeMessage() }
    }
    LaunchedEffect(taskCompletionNotice?.id) {
        taskCompletionNotice?.let { notice ->
            val result = snackbarHostState.showSnackbar(
                message = notice.message,
                actionLabel = if (notice.canUndo) "Undo" else null,
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
    LaunchedEffect(projectEditorState.message) {
        projectEditorState.message?.let { snackbarHostState.showSnackbar(it); projectEditorViewModel.consumeMessage() }
    }
    LaunchedEffect(recurringState.message) {
        recurringState.message?.let { snackbarHostState.showSnackbar(it); recurringViewModel.consumeMessage() }
    }
    LaunchedEffect(recurringEditorState.message) {
        recurringEditorState.message?.let { snackbarHostState.showSnackbar(it); recurringEditorViewModel.consumeMessage() }
    }
    LaunchedEffect(recurringEditorState.savedTemplateId) {
        recurringEditorState.savedTemplateId?.let { templateId ->
            recurringEditorViewModel.consumeSaved()
            recurringViewModel.load(showSpinner = false)
            navController.navigate(AppRoutes.recurringDetail(templateId)) {
                popUpTo(if (route == AppRoutes.RecurringNew) AppRoutes.RecurringNew else AppRoutes.RecurringEditPattern) {
                    inclusive = true
                }
            }
        }
    }

    val selectedTab = when (route) {
        AppRoutes.DayPattern -> TimeboxTab.Day
        AppRoutes.Chronicle -> TimeboxTab.Chronicle
        AppRoutes.BattlePlan, AppRoutes.TaskDetailPattern, AppRoutes.ProjectNew,
        AppRoutes.ProjectDetailPattern, AppRoutes.Recurring, AppRoutes.RecurringNew,
        AppRoutes.RecurringDetailPattern, AppRoutes.RecurringEditPattern -> TimeboxTab.BattlePlan
        AppRoutes.Types -> TimeboxTab.Types
        AppRoutes.Settings, AppRoutes.ThemePreview -> TimeboxTab.Settings
        else -> null
    }
    val colors = TimeboxTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().then(
                if (dayState.workMode != null && dayState.workModeVisible) Modifier.clearAndSetSemantics { } else Modifier
            )
        ) {
            if (route != AppRoutes.DayPattern) {
                TimeboxTopBar(
                    kicker = routeKicker(route),
                    title = routeTitle(
                        route,
                        formatFullDate(dayState.date),
                        formatMonthTitle(chronicleState.monthStart),
                    ),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                NavHost(navController, startDestination = AppRoutes.DayPattern) {
                    composable(
                        AppRoutes.DayPattern,
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
                            onNavigateToday = dayViewModel::goToDate,
                            onDateSettled = dayViewModel::goToDate,
                            onRetry = dayViewModel::retryPage,
                            onTapSlot = { lane: Lane, minute: Int -> dayViewModel.startDraft(lane, minute) },
                            onSelectBlock = dayViewModel::selectBlock,
                            onCommitMove = dayViewModel::moveBlock,
                            onDismissSheet = dayViewModel::closeSheet,
                            onChooseType = dayViewModel::chooseTaskType,
                            onTypeQueryChange = dayViewModel::onTypeQueryChange,
                            onCreateType = dayViewModel::createTaskTypeAndChoose,
                            onNoteChange = dayViewModel::onNoteChange,
                            onDeleteSelected = dayViewModel::deleteSelected,
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
                            onUpdatePlanningDraft = dayViewModel::updatePlanningDraft,
                            onReturnPlanningDraft = dayViewModel::returnPlanningDraft,
                            onArmAccessibleTask = dayViewModel::armAccessiblePlanningTask,
                            onRetryReadyTasks = dayViewModel::refreshReadyToPlan,
                            onOpenWorkMode = dayViewModel::startWorkMode,
                        )
                    }
                    composable(AppRoutes.Chronicle) {
                        ChronicleScreen(
                            state = chronicleState,
                            onPrevMonth = { chronicleViewModel.shiftMonth(-1) },
                            onNextMonth = { chronicleViewModel.shiftMonth(1) },
                            onThisMonth = chronicleViewModel::goToThisMonth,
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
                            onSelectStatus = battlePlanViewModel::selectStatus,
                            onToggleUrgency = battlePlanViewModel::toggleUrgency,
                            onToggleImportance = battlePlanViewModel::toggleImportance,
                            onToggleTaskType = battlePlanViewModel::toggleTaskType,
                            onClearFilters = battlePlanViewModel::clearFilters,
                            onSetHideCompleted = battlePlanViewModel::setHideCompleted,
                            onArchiveCompleted = battlePlanViewModel::archiveCompleted,
                            onOpenTask = { navController.navigate(AppRoutes.taskDetail(it)) },
                            onToggleReady = battlePlanViewModel::toggleReady,
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
                            notificationsAllowed = notificationsAllowed,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenRecurring = { navController.navigate(AppRoutes.Recurring) },
                            onNewProject = { navController.navigate(AppRoutes.ProjectNew) },
                            onPrepareDeleteProject = battlePlanViewModel::prepareProjectDelete,
                            onDismissDeleteProject = battlePlanViewModel::dismissProjectDelete,
                            onConfirmDeleteProject = battlePlanViewModel::confirmProjectDelete,
                            onRestoreArchived = battlePlanViewModel::restoreArchived,
                            onRestoreTrashed = battlePlanViewModel::restoreTrashed,
                            onUndoTrash = battlePlanViewModel::undoTrash,
                            onDismissUndo = battlePlanViewModel::dismissUndo,
                            onRequestTrash = battlePlanViewModel::requestTrash,
                            onDismissTrash = battlePlanViewModel::dismissTrash,
                            onConfirmTrash = battlePlanViewModel::confirmTrash,
                            onRequestPermanentDelete = battlePlanViewModel::requestPermanentDelete,
                            onDismissPermanentDelete = battlePlanViewModel::dismissPermanentDelete,
                            onConfirmPermanentDelete = battlePlanViewModel::confirmPermanentDelete,
                        )
                    }
                    composable(
                        AppRoutes.TaskDetailPattern,
                        arguments = listOf(navArgument(AppRoutes.TaskIdArg) { type = NavType.IntType }),
                        deepLinks = listOf(navDeepLink { uriPattern = AppRoutes.TaskDeepLinkPattern }),
                    ) {
                        val taskId = it.arguments?.getInt(AppRoutes.TaskIdArg) ?: return@composable
                        TaskDetailScreen(
                            state = taskDetailState,
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
                            onReadyChange = taskDetailViewModel::setReady,
                            onOpenDay = { date, blockId -> navController.navigate(AppRoutes.day(date, blockId)) },
                            onAddSubtask = taskDetailViewModel::addSubtask,
                            onToggleSubtask = taskDetailViewModel::toggleSubtask,
                            onTrashSubtask = taskDetailViewModel::requestSubtaskTrash,
                            onDismissSubtaskTrash = taskDetailViewModel::dismissSubtaskTrash,
                            onConfirmSubtaskTrash = taskDetailViewModel::confirmSubtaskTrash,
                            onUndoSubtaskTrash = taskDetailViewModel::undoSubtaskTrash,
                            onRequestTrash = taskDetailViewModel::requestTrash,
                            onDismissTrash = taskDetailViewModel::dismissTrash,
                            onConfirmTrash = taskDetailViewModel::confirmTrash,
                            onTrashed = {
                                battlePlanViewModel.offerUndo(taskId)
                                battlePlanViewModel.load(showSpinner = false)
                                navController.popBackStack()
                            },
                            onReopen = taskDetailViewModel::reopenTask,
                            onSave = taskDetailViewModel::save,
                        )
                    }
                    composable(AppRoutes.ProjectNew) {
                        ProjectEditorScreen(
                            state = projectEditorState,
                            deleteSummary = battlePlanState.projectDeleteSummary,
                            deleteSummaryLoading = battlePlanState.deleteSummaryLoading,
                            onBack = { navController.popBackStack() },
                            onRetry = { projectEditorViewModel.open(null) },
                            onNameChange = projectEditorViewModel::setName,
                            onDescriptionChange = projectEditorViewModel::setDescription,
                            onDeadlineChange = projectEditorViewModel::setDeadlineDate,
                            onDeadlineTimeChange = projectEditorViewModel::setDeadlineTime,
                            onDeadlineModeChange = projectEditorViewModel::setDeadlineMode,
                            onSave = projectEditorViewModel::save,
                            onPrepareDelete = {},
                            onDismissDelete = battlePlanViewModel::dismissProjectDelete,
                            onConfirmDelete = battlePlanViewModel::confirmProjectDelete,
                            onSaved = {
                                battlePlanViewModel.load(showSpinner = false)
                                navController.popBackStack()
                            },
                        )
                    }
                    composable(
                        AppRoutes.ProjectDetailPattern,
                        arguments = listOf(navArgument(AppRoutes.ProjectIdArg) { type = NavType.IntType }),
                    ) { entry ->
                        val projectId = entry.arguments?.getInt(AppRoutes.ProjectIdArg) ?: return@composable
                        ProjectEditorScreen(
                            state = projectEditorState,
                            deleteSummary = battlePlanState.projectDeleteSummary,
                            deleteSummaryLoading = battlePlanState.deleteSummaryLoading,
                            onBack = { navController.popBackStack() },
                            onRetry = { projectEditorViewModel.open(projectId) },
                            onNameChange = projectEditorViewModel::setName,
                            onDescriptionChange = projectEditorViewModel::setDescription,
                            onDeadlineChange = projectEditorViewModel::setDeadlineDate,
                            onDeadlineTimeChange = projectEditorViewModel::setDeadlineTime,
                            onDeadlineModeChange = projectEditorViewModel::setDeadlineMode,
                            onSave = projectEditorViewModel::save,
                            onPrepareDelete = {
                                battlePlanState.projects.firstOrNull { it.id == projectId }
                                    ?.let(battlePlanViewModel::prepareProjectDelete)
                            },
                            onDismissDelete = battlePlanViewModel::dismissProjectDelete,
                            onConfirmDelete = {
                                battlePlanViewModel.confirmProjectDelete()
                                navController.popBackStack()
                            },
                            onSaved = {
                                battlePlanViewModel.load(showSpinner = false)
                                navController.popBackStack()
                            },
                        )
                    }
                    composable(AppRoutes.Recurring) {
                        RecurringScreen(
                            state = recurringState,
                            onRetry = { recurringViewModel.load() },
                            onSelectStatus = recurringViewModel::selectStatus,
                            onNew = { navController.navigate(AppRoutes.RecurringNew) },
                            onOpen = { navController.navigate(AppRoutes.recurringDetail(it)) },
                        )
                    }
                    composable(AppRoutes.RecurringNew) {
                        RecurringEditorScreen(
                            state = recurringEditorState,
                            onBack = { navController.popBackStack() },
                            onRetry = { recurringEditorViewModel.open(null) },
                            onTitle = recurringEditorViewModel::setTitle,
                            onDescription = recurringEditorViewModel::setDescription,
                            onProject = recurringEditorViewModel::setProject,
                            onTaskType = recurringEditorViewModel::setTaskType,
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
                            onRequestDelete = recurringViewModel::requestDelete,
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
                            onProject = recurringEditorViewModel::setProject,
                            onTaskType = recurringEditorViewModel::setTaskType,
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
                            onRefreshPreview = recurringEditorViewModel::refreshPreview,
                            onSave = { recurringEditorViewModel.save() },
                            onConfirmBackfill = { recurringEditorViewModel.save(confirmBackfill = true) },
                            onDismissBackfill = recurringEditorViewModel::dismissBackfill,
                        )
                    }
                    composable(AppRoutes.Types) {
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
                        )
                    }
                    composable(AppRoutes.Settings) {
                        SettingsScreen(
                            state = settingsState,
                            isDark = isDark,
                            onToggleDark = onToggleDark,
                            onStartHourDelta = settingsViewModel::adjustStartHour,
                            onEndHourDelta = settingsViewModel::adjustEndHour,
                            onToggleFullDay = settingsViewModel::toggleFullDay,
                            onBaseUrlChange = settingsViewModel::onBaseUrlChange,
                            onApiKeyChange = settingsViewModel::onApiKeyChange,
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

            TimeboxBottomNav(selectedTab) { tab ->
                val target = when (tab) {
                    TimeboxTab.Day -> AppRoutes.day(dayState.date)
                    TimeboxTab.Chronicle -> AppRoutes.Chronicle
                    TimeboxTab.BattlePlan -> AppRoutes.BattlePlan
                    TimeboxTab.Types -> AppRoutes.Types
                    TimeboxTab.Settings -> AppRoutes.Settings
                }
                navController.navigate(target) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            }
        }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)) { data ->
            Box(
                Modifier.padding(horizontal = 16.dp).background(colors.on, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Text(data.visuals.message, style = TimeboxTheme.type.bodySmall, color = colors.bg)
            }
        }

        dayState.workMode?.takeIf { dayState.workModeVisible }?.let { workMode ->
            WorkModeScreen(
                state = workMode,
                onToggleSubtask = dayViewModel::toggleWorkModeSubtask,
                onLeave = dayViewModel::leaveWorkModeVisible,
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

private fun routeKicker(route: String): String = when (route) {
    AppRoutes.DayPattern -> "Day"
    AppRoutes.Chronicle -> "Chronicle"
    AppRoutes.BattlePlan, AppRoutes.TaskDetailPattern, AppRoutes.ProjectNew,
    AppRoutes.ProjectDetailPattern, AppRoutes.Recurring, AppRoutes.RecurringNew,
    AppRoutes.RecurringDetailPattern, AppRoutes.RecurringEditPattern -> "Battle Plan"
    AppRoutes.Types -> "Task types"
    AppRoutes.Settings, AppRoutes.ThemePreview -> "Settings"
    else -> "Timebox"
}

internal fun routeTitle(route: String, day: String, chronicle: String): String =
    when (route) {
        AppRoutes.DayPattern -> day
        AppRoutes.Chronicle -> chronicle
        AppRoutes.BattlePlan -> "Tasks"
        AppRoutes.TaskDetailPattern -> "Task details"
        AppRoutes.ProjectNew -> "New project"
        AppRoutes.ProjectDetailPattern -> "Project"
        AppRoutes.Recurring -> "Recurring"
        AppRoutes.RecurringNew -> "New recurrence"
        AppRoutes.RecurringDetailPattern -> "Template details"
        AppRoutes.RecurringEditPattern -> "Edit recurrence"
        AppRoutes.Types -> "Paths"
        AppRoutes.Settings -> "Preferences"
        AppRoutes.ThemePreview -> "Theme preview"
        else -> "Timebox"
    }
