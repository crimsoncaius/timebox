package com.timebox.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavDeepLink
import androidx.navigation.NavBackStackEntry
import com.timebox.android.ui.components.TimeboxTopBar
import com.timebox.android.ui.theme.TimeboxTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.timebox.android.data.ActivityRepository
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskCollection
import com.timebox.android.data.TaskStatus
import com.timebox.android.ui.assistant.AssistantScreen
import com.timebox.android.ui.battleplan.BattlePlanComposerActions
import com.timebox.android.ui.battleplan.BattlePlanFilterActions
import com.timebox.android.ui.battleplan.BattlePlanProjectActions
import com.timebox.android.ui.battleplan.BattlePlanRemovalActions
import com.timebox.android.ui.battleplan.BattlePlanScreen
import com.timebox.android.ui.battleplan.BattlePlanTaskActions
import com.timebox.android.ui.undo.UndoLifecycle
import com.timebox.android.ui.undo.UndoNoticeHost
import com.timebox.android.ui.battleplan.BattlePlanViewModel
import com.timebox.android.ui.battleplan.RecurringEditorViewModel
import com.timebox.android.ui.battleplan.RecurringScreen
import com.timebox.android.ui.battleplan.RecurringViewModel
import com.timebox.android.ui.battleplan.RoutineScreen
import com.timebox.android.ui.battleplan.TaskDetailScreen
import com.timebox.android.ui.battleplan.TaskDetailViewModel
import com.timebox.android.ui.chronicle.ChronicleView
import com.timebox.android.ui.chronicle.ChronicleScreen
import com.timebox.android.ui.chronicle.ChronicleViewModel
import com.timebox.android.ui.chronicle.TrendsScreen
import com.timebox.android.ui.chronicle.HabitsScreen
import com.timebox.android.ui.chronicle.TimeGoalsViewModel
import com.timebox.android.ui.chronicle.TimeGoalsScreen
import com.timebox.android.ui.chronicle.HabitsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timebox.android.ui.components.TransientFeedback
import com.timebox.android.ui.day.DayScreen
import com.timebox.android.ui.day.DayViewModel
import com.timebox.android.ui.prototype.prototypeRoutes
import com.timebox.android.ui.settings.SettingsScreen
import com.timebox.android.ui.settings.SettingsViewModel
import com.timebox.android.ui.theme.ThemePreviewScreen
import com.timebox.android.ui.types.TypesScreen
import com.timebox.android.ui.types.TypesViewModel
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** App-owned models retain their scope across destination changes. */
internal class TimeboxScreenModels(
    val dayViewModel: DayViewModel,
    val chronicleViewModel: ChronicleViewModel,
    val typesViewModel: TypesViewModel,
    val settingsViewModel: SettingsViewModel,
    val battlePlanViewModel: BattlePlanViewModel,
    val taskDetailViewModel: TaskDetailViewModel,
    val recurringViewModel: RecurringViewModel,
    val recurringEditorViewModel: RecurringEditorViewModel,
)

internal data class TimeboxNavigationOptions(
    val isDark: Boolean,
    val onToggleDark: () -> Unit,
    val notificationsAllowed: Boolean,
    val onRequestNotificationPermission: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val exactAlarmsAllowed: Boolean,
    val onRequestExactAlarms: () -> Unit,
    val reducedMotion: Boolean,
    val shortcutSlide: ShortcutSlide? = null,
)

/** Mutable collaborators, deliberately without an Immutable/Stable promise.
 * Options use Compose State so retained destination lambdas see permission/theme updates.
 * Screen states are collected inside destinations, never captured as graph-build snapshots.
 */
internal class TimeboxNavigationDependencies(
    val models: TimeboxScreenModels,
    val navController: NavHostController,
    val snackbarHostState: SnackbarHostState,
    val trackingScope: CoroutineScope,
    val activityRepository: ActivityRepository,
    val onEnterFocus: () -> Unit,
    val options: State<TimeboxNavigationOptions>,
    val undoLifecycle: UndoLifecycle,
)

@Composable
internal fun TimeboxNavHost(dependencies: TimeboxNavigationDependencies, startDestination: String) {
    NavHost(dependencies.navController, startDestination = startDestination,
        modifier = Modifier.clipToBounds(),
        enterTransition = {
            val options = dependencies.options.value
            val slide = options.shortcutSlide?.takeIf { it.from == initialState.destination.route && it.to == targetState.destination.route }
            when {
                options.reducedMotion -> EnterTransition.None
                slide != null -> slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { slide.direction * it }
                else -> fadeIn(tween(220))
            }
        },
        exitTransition = {
            val options = dependencies.options.value
            val slide = options.shortcutSlide?.takeIf { it.from == initialState.destination.route && it.to == targetState.destination.route }
            when {
                options.reducedMotion -> ExitTransition.None
                slide != null -> slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { -slide.direction * it }
                else -> fadeOut(tween(220))
            }
        },
    ) {
        prototypeRoutes()
        dayRoute(dependencies)
        chronicleRoute(dependencies)
        battlePlanRoute(dependencies)
        taskDetailRoute(dependencies)
        recurringRoute(dependencies)
        newRecurringRoute(dependencies)
        recurringDetailRoute(dependencies)
        editRecurringRoute(dependencies)
        typesRoute(dependencies)
        assistantRoute(dependencies)
        screen(dependencies, AppRoutes.Review) {
            val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.timebox.android.TimeboxApplication
            com.timebox.android.ui.assistant.AssistantReviewScreen(application.assistant,
                { dependencies.navController.navigate(AppRoutes.day(it)) }, Modifier.fillMaxSize())
        }
        settingsRoute(dependencies)
        themePreviewRoute(dependencies)
    }
}

private fun NavGraphBuilder.dayRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies,
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
                val dayState by dayViewModel.state.collectAsState()
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
                    onLogGap = dayViewModel::startActualDraft,
                    onSelectBlock = dayViewModel::selectBlock,
                    onCommitMove = dayViewModel::moveBlock,
                    onDismissSheet = dayViewModel::closeSheet,
                    onChooseType = dayViewModel::chooseTaskType,
                    onTypeQueryChange = dayViewModel::onTypeQueryChange,
                    onCreateType = dayViewModel::createTaskTypeAndChoose,
                    onNameChange = dayViewModel::onNameChange,
                    onNoteChange = dayViewModel::onNoteChange,
                    onCreateDraft = dayViewModel::createTasklessPlannedDraft,
                    onPlannedTimesInput = dayViewModel::onTimesInput,
                    onSavePlanned = dayViewModel::saveSelected,
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
                    onEnterFocus = onEnterFocus,
                )
            }
        }
    }
}

private fun NavGraphBuilder.chronicleRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            mapOf(AppRoutes.Chronicle to ChronicleView.Calendar, AppRoutes.Trends to ChronicleView.Trends,
                AppRoutes.Habits to ChronicleView.Habits, AppRoutes.TimeGoals to ChronicleView.TimeGoals).forEach { (route, view) ->
                screen(dependencies, route) {
                    val chronicleState by chronicleViewModel.state.collectAsState()
                    ChronicleScreen(
                        state = chronicleState.copy(view = view),
                        onPrevMonth = { chronicleViewModel.shiftMonth(-1) },
                        onNextMonth = { chronicleViewModel.shiftMonth(1) },
                        onThisMonth = chronicleViewModel::goToThisMonth,
                        onSelectView = {
                            if (it == ChronicleView.Trends) {
                                if (navController.previousBackStackEntry?.destination?.route == AppRoutes.Trends) navController.popBackStack()
                                else navController.navigate(AppRoutes.Trends)
                            }
                        },
                        onClearHighlights = chronicleViewModel::clearHighlights,
                        trendsContent = { TrendsScreen(chronicleState, chronicleViewModel, onOpenCalendar = { navController.navigate(AppRoutes.Chronicle) }) },
                        timeGoalsContent = {
                            val repository = rememberRepository()
                            val goalsViewModel: TimeGoalsViewModel = viewModel(
                                factory = viewModelFactory { initializer { TimeGoalsViewModel(repository) } },
                            )
                            val goalsState by goalsViewModel.state.collectAsState()
                            TimeGoalsScreen(goalsViewModel, goalsState)
                        },
                        habitsContent = {
                            val repository = rememberRepository()
                            val habitsViewModel: HabitsViewModel = viewModel(
                                factory = viewModelFactory { initializer { HabitsViewModel(repository) } },
                            )
                            val habitsState by habitsViewModel.state.collectAsState()
                            HabitsScreen(habitsViewModel, habitsState, onCreateRoutine = { navController.navigate(AppRoutes.RecurringNew) }) { templateId ->
                                navController.navigate(AppRoutes.recurringDetail(templateId))
                            }
                        },
                        onOpenDay = { navController.navigate(AppRoutes.day(it)) },
                        onRetry = chronicleViewModel::load,
                    )
                }
            }
        }
    }
}

private fun NavGraphBuilder.battlePlanRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies, AppRoutes.BattlePlan) {
                val battlePlanState by battlePlanViewModel.state.collectAsState()
                val currentOptions by options
                val notificationsAllowed = currentOptions.notificationsAllowed
                val onRequestNotificationPermission = currentOptions.onRequestNotificationPermission
                BattlePlanScreen(
                    state = battlePlanState,
                    onRetry = { battlePlanViewModel.load() },
                    filterActions = BattlePlanFilterActions(
                        selectCollection = battlePlanViewModel::selectCollection,
                        selectScope = battlePlanViewModel::selectScope,
                        selectStatus = battlePlanViewModel::selectStatus,
                        toggleUrgency = battlePlanViewModel::toggleUrgency,
                        toggleImportance = battlePlanViewModel::toggleImportance,
                        toggleTaskType = battlePlanViewModel::toggleTaskType,
                        clearFilters = battlePlanViewModel::clearFilters,
                        setHideCompleted = battlePlanViewModel::setHideCompleted,
                    ),
                    taskActions = BattlePlanTaskActions(
                        open = { navController.navigate(AppRoutes.taskDetail(it)) },
                        toggleReady = battlePlanViewModel::toggleReady,
                        move = battlePlanViewModel::moveTask,
                        moveToBoundary = battlePlanViewModel::moveTaskToBoundary,
                        drop = battlePlanViewModel::dropTask,
                        setBlocked = battlePlanViewModel::setBlocked,
                        archiveCompleted = battlePlanViewModel::archiveCompleted,
                    ),
                    projectActions = BattlePlanProjectActions(
                        reorder = battlePlanViewModel::reorderProjects,
                        moveTaskTo = battlePlanViewModel::moveProject,
                        create = battlePlanViewModel::startProjectCreation,
                        edit = battlePlanViewModel::editProject,
                        changeName = battlePlanViewModel::setProjectName,
                        save = battlePlanViewModel::saveProject,
                        cancelEditor = battlePlanViewModel::cancelProjectEditor,
                        dismissEditor = battlePlanViewModel::dismissProjectEditor,
                        prepareDelete = battlePlanViewModel::prepareProjectDelete,
                        dismissDelete = battlePlanViewModel::dismissProjectDelete,
                        confirmDelete = battlePlanViewModel::confirmProjectDelete,
                    ),
                    composerActions = BattlePlanComposerActions(
                        show = battlePlanViewModel::setComposerVisible,
                        create = { _, _, _ -> battlePlanViewModel.createTask() },
                        changeDraft = battlePlanViewModel::updateComposerDraft,
                        changeReminderEnabled = battlePlanViewModel::setComposerReminderEnabled,
                        createTaskType = battlePlanViewModel::createComposerTaskType,
                        notificationsAllowed = notificationsAllowed,
                        requestNotificationPermission = onRequestNotificationPermission,
                    ),
                    removalActions = BattlePlanRemovalActions(
                        requestTrash = battlePlanViewModel::requestTrash,
                        dismissTrash = battlePlanViewModel::dismissTrash,
                        confirmTrash = battlePlanViewModel::confirmTrash,
                        restoreArchived = battlePlanViewModel::restoreArchived,
                        restoreTrashed = battlePlanViewModel::restoreTrashed,
                        requestPermanentDelete = battlePlanViewModel::requestPermanentDelete,
                        dismissPermanentDelete = battlePlanViewModel::dismissPermanentDelete,
                        confirmPermanentDelete = battlePlanViewModel::confirmPermanentDelete,
                    ),
                    onOpenRecurring = { navController.navigate(AppRoutes.Recurring) },
                )
            }
        }
    }
}

private fun NavGraphBuilder.taskDetailRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            dialog(
                AppRoutes.TaskDetailPattern,
                dialogProperties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
                arguments = listOf(navArgument(AppRoutes.TaskIdArg) { type = NavType.IntType }),
                deepLinks = listOf(navDeepLink { uriPattern = AppRoutes.TaskDeepLinkPattern }),
            ) {
                val battlePlanState by battlePlanViewModel.state.collectAsState()
                val taskDetailState by taskDetailViewModel.state.collectAsState()
                val currentOptions by options
                val notificationsAllowed = currentOptions.notificationsAllowed
                val onRequestNotificationPermission = currentOptions.onRequestNotificationPermission
                val reducedMotion = currentOptions.reducedMotion
                val taskId = it.arguments?.getInt(AppRoutes.TaskIdArg) ?: return@dialog
                TaskDetailScreen(
                    state = taskDetailState,
                    onTrackTask = if (taskDetailState.task?.let { it.status != TaskStatus.Completed && it.recurrenceKind != "quota_parent" && (it.parentId == null || it.recurrenceKind == "quota_session") } == true) ({
                        trackingScope.launch {
                            val activity = activityRepository
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
                        val notice = dependencies.undoLifecycle.notice.collectAsState().value
                        Column {
                            SnackbarHost(snackbarHostState, Modifier.padding(horizontal = 16.dp)) { data ->
                                TransientFeedback(
                                    message = data.visuals.message,
                                    modifier = Modifier.semantics { paneTitle = "Feedback"; dismiss { data.dismiss(); true } },
                                    actionLabel = data.visuals.actionLabel,
                                    onAction = data::performAction,
                                    onDismiss = if (data.visuals.withDismissAction) data::dismiss else null,
                                )
                            }
                            notice?.let {
                                UndoNoticeHost(
                                    notice = it,
                                    onUndo = { dependencies.undoLifecycle.undo(it.id) },
                                    onDismiss = { dependencies.undoLifecycle.dismiss(it.id) },
                                    onExpiryFinished = { dependencies.undoLifecycle.finishExpiry(it.id) },
                                    reducedMotion = reducedMotion,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun NavGraphBuilder.recurringRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies, AppRoutes.Recurring) {
                val recurringState by recurringViewModel.state.collectAsState()
                RecurringScreen(
                    state = recurringState,
                    onRetry = { recurringViewModel.load() },
                    onSelectStatus = recurringViewModel::selectStatus,
                    onNew = { navController.navigate(AppRoutes.RecurringNew) },
                    onOpen = { navController.navigate(AppRoutes.recurringDetail(it)) },
                    onRequestDelete = { recurringViewModel.requestDelete(it) },
                    onDismissDelete = recurringViewModel::dismissDelete,
                    onConfirmDelete = { recurringViewModel.confirmDelete() },
                )
            }
        }
    }
}

private fun NavGraphBuilder.newRecurringRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies, AppRoutes.RecurringNew) {
                val recurringEditorState by recurringEditorViewModel.state.collectAsState()
                RoutineScreen(recurringEditorState, recurringEditorViewModel, { navController.popBackStack() })
            }
        }
    }
}

private fun NavGraphBuilder.recurringDetailRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies,
                AppRoutes.RecurringDetailPattern,
                arguments = listOf(navArgument(AppRoutes.TemplateIdArg) { type = NavType.IntType }),
            ) { entry ->
                val recurringState by recurringViewModel.state.collectAsState()
                val recurringEditorState by recurringEditorViewModel.state.collectAsState()
                val templateId = entry.arguments?.getInt(AppRoutes.TemplateIdArg) ?: return@screen
                RoutineScreen(
                    recurringEditorState, recurringEditorViewModel, { navController.popBackStack() },
                    onOpenTask = { navController.navigate(AppRoutes.taskDetail(it)) },
                    lifecycle = recurringState, lifecycleViewModel = recurringViewModel,
                )
            }
        }
    }
}

private fun NavGraphBuilder.editRecurringRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies,
                AppRoutes.RecurringEditPattern,
                arguments = listOf(navArgument(AppRoutes.TemplateIdArg) { type = NavType.IntType }),
            ) { entry ->
                val recurringEditorState by recurringEditorViewModel.state.collectAsState()
                val templateId = entry.arguments?.getInt(AppRoutes.TemplateIdArg) ?: return@screen
                RoutineScreen(recurringEditorState, recurringEditorViewModel, { navController.popBackStack() })
            }
        }
    }
}

private fun NavGraphBuilder.typesRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies, AppRoutes.Types) {
                val typesState by typesViewModel.state.collectAsState()
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
                    onConfirmMerge = typesViewModel::confirmMerge,
                    onBackFromMerge = typesViewModel::backFromMerge,
                    onCancelRename = typesViewModel::cancelRename,
                )
            }
        }
    }
}

private fun NavGraphBuilder.assistantRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies, AppRoutes.Assistant) {
                AssistantScreen(onOpenReview = { navController.navigate(AppRoutes.Review) }, onOpenTask = { navController.navigate(AppRoutes.taskDetail(it)) }, onOpenDay = { navController.navigate(AppRoutes.day(it)) },
                    onOpenTrends = { start, end ->
                        models.chronicleViewModel.openRange(start, end)
                        navController.navigate(AppRoutes.Trends)
                    })
            }
        }
    }
}

private fun NavGraphBuilder.settingsRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies, AppRoutes.Settings) {
                val dayState by dayViewModel.state.collectAsState()
                val settingsState by settingsViewModel.state.collectAsState()
                val currentOptions by options
                val isDark = currentOptions.isDark
                val onToggleDark = currentOptions.onToggleDark
                val notificationsAllowed = currentOptions.notificationsAllowed
                val onRequestNotificationPermission = currentOptions.onRequestNotificationPermission
                val onOpenNotificationSettings = currentOptions.onOpenNotificationSettings
                val exactAlarmsAllowed = currentOptions.exactAlarmsAllowed
                val onRequestExactAlarms = currentOptions.onRequestExactAlarms
                SettingsScreen(
                    state = settingsState,
                    isDark = isDark,
                    onToggleDark = onToggleDark,
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
                )
            }
        }
    }
}

private fun NavGraphBuilder.themePreviewRoute(dependencies: TimeboxNavigationDependencies) {
    with(dependencies) {
        with(models) {
            screen(dependencies, AppRoutes.ThemePreview) {
                ThemePreviewScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Keep shared chrome inside its destination so its heading moves with the page. */
private fun NavGraphBuilder.screen(
    dependencies: TimeboxNavigationDependencies,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments, deepLinks = deepLinks) { entry ->
        Column(Modifier.fillMaxSize().background(TimeboxTheme.colors.bg)) {
            if (route !in listOf(AppRoutes.DayPattern, AppRoutes.Assistant, AppRoutes.RecurringNew,
                    AppRoutes.RecurringEditPattern, AppRoutes.RecurringDetailPattern)) {
                val chronicle by dependencies.models.chronicleViewModel.state.collectAsState()
                TimeboxTopBar(routeKicker(route), routeTitle(route, "", formatMonthTitle(chronicle.monthStart)))
                if (route in listOf(AppRoutes.BattlePlan, AppRoutes.Recurring, AppRoutes.TimeGoals)) {
                    com.timebox.android.ui.components.Hairline()
                }
            }
            Box(Modifier.weight(1f)) { content(entry) }
        }
    }
}
