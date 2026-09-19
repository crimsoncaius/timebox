package com.timebox.android.ui.battleplan

import androidx.compose.material3.CircularProgressIndicator

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Category
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.timebox.android.data.BattleTask
import com.timebox.android.data.BattlePlanSort
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.Project
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskCollection
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.Hairline
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.components.TimeboxSwitch
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.readiness.ReadyToPlanFailureNotice
import com.timebox.android.ui.theme.TimeboxColors
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class CardCompletionActions(val saving: Boolean = false, val move: (BattleTask, TaskStatus) -> Unit = { _, _ -> })
private val LocalCardCompletion = androidx.compose.runtime.staticCompositionLocalOf { CardCompletionActions() }

@Composable
fun BattlePlanScreen(
    state: BattlePlanUiState,
    onRetry: () -> Unit,
    onSelectCollection: (TaskCollection) -> Unit = {},
    onSelectScope: (BattlePlanScope) -> Unit,
    onReorderProjects: (List<Int>) -> Unit = {},
    onSelectStatus: (TaskStatus) -> Unit,
    onToggleUrgency: (String) -> Unit,
    onToggleImportance: (String) -> Unit,
    onToggleTaskType: (String) -> Unit,
    onClearFilters: () -> Unit,
    onSetHideCompleted: (Boolean) -> Unit = {},
    onArchiveCompleted: () -> Unit = {},
    onOpenTask: (Int) -> Unit,
    onToggleReady: (BattleTask) -> Unit,
    onMoveProject: (BattleTask, Int?) -> Unit = { _, _ -> },
    onMoveTask: (BattleTask, TaskStatus) -> Unit,
    onReorderTask: (BattleTask, Int) -> Unit,
    onMoveTaskToBoundary: (BattleTask, Boolean) -> Unit = { _, _ -> },
    onDropTask: (BattleTask, TaskStatus, Int) -> Unit = { _, _, _ -> },
    onSetBlocked: (BattleTask, Boolean, String?) -> Unit = { _, _, _ -> },
    onCreateSubtask: (BattleTask, String) -> Unit,
    onToggleSubtask: (Subtask) -> Unit,
    onCreateTask: (String, String, Int?) -> Unit,
    onShowComposer: (Boolean) -> Unit,
    onComposerDraftChange: (TaskComposerDraft) -> Unit = {},
    onComposerReminderEnabledChange: (Boolean) -> Unit = {},
    onCreateComposerTaskType: (String) -> Unit = {},
    notificationsAllowed: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenRecurring: () -> Unit,
    onOpenTaskTypes: () -> Unit = {},
    onNewProject: () -> Unit,
    onProjectNameChange: (String) -> Unit = {},
    onSaveProject: () -> Unit = {},
    onCancelProjectEditor: () -> Unit = {},
    onDismissProjectEditor: () -> Unit = {},
    onEditProject: (Project) -> Unit = {},
    onPrepareDeleteProject: (Project) -> Unit,
    onDismissDeleteProject: () -> Unit,
    onConfirmDeleteProject: () -> Unit,
    onRestoreArchived: (BattleTask) -> Unit,
    onRestoreTrashed: (BattleTask) -> Unit,
    onUndoTrash: () -> Unit,
    onDismissUndo: () -> Unit,
    onRequestTrash: (BattleTask) -> Unit = {},
    onDismissTrash: () -> Unit = {},
    onConfirmTrash: () -> Unit = {},
    onRequestPermanentDelete: (BattleTask) -> Unit,
    onDismissPermanentDelete: () -> Unit,
    onConfirmPermanentDelete: () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalProjectMove provides ProjectMoveActions(state.projects, state.saving, state.message, onMoveProject),
        LocalCardCompletion provides CardCompletionActions(state.saving, onMoveTask),
    ) {
        var movingTask by remember { mutableStateOf<BattleTask?>(null) }
        val onRequestMoveProject: (BattleTask) -> Unit = { movingTask = it }
        movingTask?.let { task ->
            MoveTaskProjectDialog(task, state.projects, state.saving,
                onMove = { destination -> onMoveProject(task, destination); movingTask = null },
                onDismiss = { movingTask = null })
        }
        var projectOrderOpen by remember { mutableStateOf(false) }
        if (projectOrderOpen) ProjectOrderDialog(state.projects, state.projectOrderSaving, state.error, onReorderProjects) { projectOrderOpen = false }
        val colors = TimeboxTheme.colors
        when {
            state.loading -> LoadingState()
            state.error != null && state.tasks.isEmpty() -> ErrorState(state.error, onRetry)
            else -> Column(Modifier.fillMaxSize()) {
                if (state.collection == TaskCollection.Active) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        MobileKanbanBoard(
                            state = state,
                            onSelectScope = onSelectScope,
                            onSelectCollection = onSelectCollection,
                            onSelectStatus = onSelectStatus,
                            onToggleUrgency = onToggleUrgency,
                            onToggleImportance = onToggleImportance,
                            onToggleTaskType = onToggleTaskType,
                            onClearFilters = onClearFilters,
                            onSetHideCompleted = onSetHideCompleted,
                            onArchiveCompleted = onArchiveCompleted,
                            onOpenTask = onOpenTask,
                            onToggleReady = onToggleReady,
                            onRequestMoveProject = onRequestMoveProject,
                            onDropTask = onDropTask,
                            onMoveTaskToBoundary = onMoveTaskToBoundary,
                            onSetBlocked = onSetBlocked,
                            onRequestTrash = onRequestTrash,
                            onShowComposer = onShowComposer,
                            onOpenRecurring = onOpenRecurring,
                            onOpenTaskTypes = onOpenTaskTypes,
                            onNewProject = onNewProject,
                            onReorderProjects = onReorderProjects,
                            onEditProject = onEditProject,
                            onPrepareDeleteProject = onPrepareDeleteProject,
                        )
                    }
                } else {
                    UtilityTaskList(
                        state,
                        onBackToBoard = { onSelectCollection(TaskCollection.Active) },
                        onRestoreArchived,
                        onRestoreTrashed,
                        onRequestPermanentDelete,
                    )
                }
            }
        }

        state.projectEditor?.let { draft ->
            ProjectNameSheet(
                state = draft,
                onNameChange = onProjectNameChange,
                onSave = onSaveProject,
                onCancel = onCancelProjectEditor,
                onDismiss = onDismissProjectEditor,
            )
        }

        if (state.showComposer) {
            TaskComposerOverlay(
                state = state,
                notificationsAllowed = notificationsAllowed,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onDraftChange = onComposerDraftChange,
                onReminderEnabledChange = onComposerReminderEnabledChange,
                onDismiss = { onShowComposer(false) },
                onCreate = { onCreateTask("", "", null) },
                onCreateTaskType = onCreateComposerTaskType,
            )
        }

        state.projectDeleteSummary?.let { summary ->
            ProjectDeleteDialog(summary, onDismissDeleteProject, onConfirmDeleteProject)
        }
        state.pendingTrashTask?.let { task ->
            AlertDialog(
                onDismissRequest = onDismissTrash,
                title = { Text("Move ${task.title} to Trash?") },
                text = {
                    Text(
                        if (task.subtasks.isEmpty()) "You can restore it for 30 days."
                        else "This also moves every Subtask to Trash. You can restore it for 30 days.",
                    )
                },
                confirmButton = { TextButton(onClick = onConfirmTrash) { Text("Move to Trash") } },
                dismissButton = { TextButton(onClick = onDismissTrash) { Text("Cancel") } },
            )
        }
        state.permanentDeleteTask?.let { task ->
            AlertDialog(
                onDismissRequest = onDismissPermanentDelete,
                title = { Text("Permanently delete ${task.title}?") },
                text = { Text("This removes the task and its subtasks permanently. This cannot be undone.") },
                confirmButton = { TextButton(onClick = onConfirmPermanentDelete) { Text("Delete permanently") } },
                dismissButton = { TextButton(onClick = onDismissPermanentDelete) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun ScopeSelector(
    scopes: List<BattlePlanScope>,
    selected: BattlePlanScope,
    onSelect: (BattlePlanScope) -> Unit,
    onNewProject: () -> Unit,
    onReorderProjects: () -> Unit,
    onOpenRecurring: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        scopes.forEach { scope ->
            val active = scope.preferenceKey == selected.preferenceKey
            Text(
                text = scope.label,
                style = TimeboxTheme.type.label,
                color = if (active) colors.bg else colors.on,
                modifier = Modifier.clip(RoundedCornerShape(18.dp))
                    .background(if (active) colors.on else colors.low)
                    .clickable { onSelect(scope) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        Text(
            text = "Recurring Tasks",
            style = TimeboxTheme.type.label,
            color = colors.on,
            modifier = Modifier.clip(RoundedCornerShape(18.dp))
                .background(colors.low)
                .clickable(onClick = onOpenRecurring)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
        TextButton(onClick = onReorderProjects) { Text("Reorder projects") }
        RoundIconButton(
            icon = Icons.Outlined.Add,
            contentDescription = "Create project",
            onClick = onNewProject,
            background = colors.low,
        )
    }
}

@Composable
private fun StatusTabs(state: BattlePlanUiState, onSelectStatus: (TaskStatus) -> Unit) {
    val colors = TimeboxTheme.colors
    ScrollableTabRow(
        selectedTabIndex = battlePlanStatuses.indexOf(state.selectedStatus).coerceAtLeast(0),
        edgePadding = 8.dp,
        containerColor = colors.bg,
        contentColor = colors.on,
        divider = {},
    ) {
        battlePlanStatuses.forEach { status ->
            Tab(
                selected = state.selectedStatus == status,
                onClick = { onSelectStatus(status) },
                modifier = Modifier.semantics {
                    contentDescription = "${status.label}, ${state.count(status)} tasks"
                },
                text = { Text("${status.label}  ${state.count(status)}", maxLines = 1) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MobileKanbanBoard(
    state: BattlePlanUiState,
    onSelectScope: (BattlePlanScope) -> Unit,
    onSelectCollection: (TaskCollection) -> Unit,
    onSelectStatus: (TaskStatus) -> Unit,
    onToggleUrgency: (String) -> Unit,
    onToggleImportance: (String) -> Unit,
    onToggleTaskType: (String) -> Unit,
    onClearFilters: () -> Unit,
    onSetHideCompleted: (Boolean) -> Unit,
    onArchiveCompleted: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onToggleReady: (BattleTask) -> Unit,
    onRequestMoveProject: (BattleTask) -> Unit,
    onDropTask: (BattleTask, TaskStatus, Int) -> Unit,
    onMoveTaskToBoundary: (BattleTask, Boolean) -> Unit,
    onSetBlocked: (BattleTask, Boolean, String?) -> Unit,
    onRequestTrash: (BattleTask) -> Unit,
    onShowComposer: (Boolean) -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenTaskTypes: () -> Unit = {},
    onNewProject: () -> Unit,
    onReorderProjects: (List<Int>) -> Unit,
    onEditProject: (Project) -> Unit,
    onPrepareDeleteProject: (Project) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val dragScope = rememberCoroutineScope()
    val initialPage = battlePlanStatuses.indexOf(state.selectedStatus).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { battlePlanStatuses.size })
    val projectMotion = projectSelectionMotion(state.selectedScope.preferenceKey)
    var filterSheet by remember { mutableStateOf(false) }
    var dragLayerBounds by remember { mutableStateOf(Rect.Zero) }
    var dragLayerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var activeDrag by remember { mutableStateOf<MobileTaskDragState?>(null) }
    var settlingDrop by remember { mutableStateOf<MobileTaskDropSettleState?>(null) }
    val settleProgress = remember { Animatable(0f) }
    val cardBounds = remember { mutableStateMapOf<Pair<TaskStatus, Int>, Rect>() }
    val dropIndicatorBounds = remember { mutableStateMapOf<Pair<TaskStatus, Int>, Rect>() }
    val laneBounds = remember { mutableStateMapOf<TaskStatus, Rect>() }
    val openListState = rememberLazyListState()
    val inProgressListState = rememberLazyListState()
    val completedListState = rememberLazyListState()
    val laneListStates = remember(openListState, inProgressListState, completedListState) {
        mapOf(
            TaskStatus.Open to openListState,
            TaskStatus.InProgress to inProgressListState,
            TaskStatus.Completed to completedListState,
        )
    }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val edgeWidthPx = with(density) { 52.dp.toPx() }
    val insertionHysteresisPx = with(density) { 8.dp.toPx() }
    val verticalScrollEdgePx = with(density) { 56.dp.toPx() }
    val verticalScrollStepPx = with(density) { 18.dp.toPx() }

    fun resolveTarget(drag: MobileTaskDragState, status: TaskStatus): MobileTaskDragState {
        val candidates = state.filteredTasks.filter { it.status == status && it.id != drag.task.id }
        val measuredBounds = candidates.mapIndexedNotNull { index, task ->
            cardBounds[status to task.id]?.let { IndexedValue(index, it) }
        }
        val rawIndex = insertionIndexForPointer(drag.pointerInRoot.y, candidates.size, measuredBounds)
        val targetIndex = if (drag.targetStatus == status) {
            insertionIndexWithHysteresis(
                pointerY = drag.pointerInRoot.y,
                itemCount = candidates.size,
                measuredBounds = measuredBounds,
                currentIndex = drag.targetIndex,
                hysteresis = insertionHysteresisPx,
            )
        } else {
            rawIndex
        }
        return drag.copy(
            targetStatus = status,
            targetIndex = targetIndex,
        )
    }

    LaunchedEffect(state.selectedStatus) {
        val page = battlePlanStatuses.indexOf(state.selectedStatus).coerceAtLeast(0)
        if (page != pagerState.currentPage) pagerState.animateScrollToPage(page)
    }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) onSelectStatus(battlePlanStatuses[pagerState.currentPage])
    }
    LaunchedEffect(activeDrag?.edgeDirection, activeDrag?.edgeLockedAtPage, pagerState.settledPage) {
        val lockedDrag = activeDrag
        if (lockedDrag?.edgeLockedAtPage != null && lockedDrag.edgeLockedAtPage != pagerState.settledPage) {
            val direction = edgePageDirection(
                pointerX = lockedDrag.pointerInRoot.x - dragLayerBounds.left,
                viewportWidth = dragLayerBounds.width,
                edgeWidth = edgeWidthPx,
                currentPage = pagerState.settledPage,
                pageCount = battlePlanStatuses.size,
            )
            activeDrag = lockedDrag.copy(edgeDirection = direction, edgeLockedAtPage = null)
            return@LaunchedEffect
        }
        val direction = activeDrag?.edgeDirection ?: return@LaunchedEffect
        if (direction == 0) return@LaunchedEffect
        val targetPage = pagerState.settledPage + direction
        if (targetPage !in battlePlanStatuses.indices) return@LaunchedEffect
        delay(MobileDragEdgeDwellMillis)
        if (activeDrag?.edgeDirection != direction) return@LaunchedEffect
        activeDrag = activeDrag?.copy(edgeDirection = 0, edgeLockedAtPage = pagerState.settledPage)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        dragScope.launch { pagerState.animateScrollToPage(targetPage) }
    }
    LaunchedEffect(pagerState.targetPage, activeDrag?.task?.id) {
        val drag = activeDrag ?: return@LaunchedEffect
        activeDrag = resolveTarget(drag, battlePlanStatuses[pagerState.targetPage])
    }
    LaunchedEffect(activeDrag?.task?.id, activeDrag?.targetStatus, activeDrag?.pointerInRoot?.y) {
        while (true) {
            val drag = activeDrag ?: break
            val bounds = laneBounds[drag.targetStatus] ?: break
            val step = verticalAutoScrollStep(
                pointerY = drag.pointerInRoot.y,
                laneBounds = bounds,
                edgeSize = verticalScrollEdgePx,
                maximumStep = verticalScrollStepPx,
            )
            if (step == 0f) break
            withFrameNanos { }
            val consumed = laneListStates.getValue(drag.targetStatus).scrollBy(step)
            if (consumed == 0f) break
        }
    }
    LaunchedEffect(settlingDrop) {
        val settling = settlingDrop ?: run {
            settleProgress.snapTo(0f)
            return@LaunchedEffect
        }
        settleProgress.snapTo(0f)
        settleProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = MobileDropSettleDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        if (settlingDrop != settling) return@LaunchedEffect
        if (!settling.unchanged) {
            onDropTask(settling.drag.task, settling.drag.targetStatus, settling.drag.targetIndex)
        }
        settlingDrop = null
    }

    val visualDrag = activeDrag ?: settlingDrop?.drag
    val pickupProgress by animateFloatAsState(
        targetValue = if (visualDrag == null) 0f else 1f,
        animationSpec = tween(
            durationMillis = MobilePickupDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "battle-plan-task-pickup",
    )
    val sourceGapCloseProgress by animateFloatAsState(
        targetValue = if (visualDrag == null) 0f else 1f,
        animationSpec = tween(
            durationMillis = MobileSourceGapCloseDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "battle-plan-source-gap-close",
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskNavigationMenu(
                state = state, modifier = Modifier.weight(1f),
                onSelectScope = onSelectScope, onSelectCollection = onSelectCollection,
                onOpenRecurring = onOpenRecurring, onOpenTaskTypes = onOpenTaskTypes,
                onReorderProjects = onReorderProjects,
                onEditProject = onEditProject, onPrepareDeleteProject = onPrepareDeleteProject,
                onNewProject = onNewProject,
            )
            IconButton(onClick = { filterSheet = true }) {
                Icon(Icons.Outlined.FilterList, contentDescription = "Filter tasks", tint = colors.on)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            battlePlanStatuses.forEachIndexed { index, status ->
                val selected = pagerState.currentPage == index
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                        onSelectStatus(status)
                    }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${status.label}  ${state.count(status)}", style = TimeboxTheme.type.label, color = if (selected) colors.on else colors.onVariant)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.height(2.dp).width(38.dp).background(if (selected) colors.on else colors.bg))
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    dragLayerCoordinates = it
                    dragLayerBounds = it.boundsInRoot()
                }
                .pointerInput(state.saving, state.sort, state.filteredTasks.map { it.id }) {
                    if (state.saving || state.sort != BattlePlanSort.Manual) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { startOffset ->
                            if (settlingDrop != null) return@detectDragGesturesAfterLongPress
                            val coordinates = dragLayerCoordinates ?: return@detectDragGesturesAfterLongPress
                            val pointerInRoot = coordinates.localToRoot(startOffset)
                            val sourceStatus = battlePlanStatuses[pagerState.currentPage]
                            val sourceTasks = state.filteredTasks.filter { it.status == sourceStatus }
                            val task = sourceTasks.firstOrNull { candidate ->
                                cardBounds[sourceStatus to candidate.id]?.contains(pointerInRoot) == true
                            } ?: return@detectDragGesturesAfterLongPress
                            val sourceIndex = sourceTasks.indexOfFirst { it.id == task.id }
                            val drag = MobileTaskDragState(
                                task = task,
                                sourceIndex = sourceIndex,
                                cardBoundsInRoot = cardBounds.getValue(sourceStatus to task.id),
                                startPointerInRoot = pointerInRoot,
                                pointerInRoot = pointerInRoot,
                                targetStatus = sourceStatus,
                                targetIndex = sourceIndex,
                            )
                            activeDrag = resolveTarget(drag, sourceStatus)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragCancel = { activeDrag = null },
                        onDragEnd = {
                            val drag = activeDrag ?: return@detectDragGesturesAfterLongPress
                            val indicatorBounds = dropIndicatorBounds[drag.targetStatus to drag.targetIndex]
                            activeDrag = null
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (indicatorBounds != null) {
                                val landingOffset = Offset(
                                    x = indicatorBounds.left,
                                    y = if (drag.targetIndex == 0) indicatorBounds.top else indicatorBounds.center.y,
                                )
                                settlingDrop = MobileTaskDropSettleState(
                                    drag = drag,
                                    targetTopLeftInRoot = landingOffset,
                                    unchanged = isUnchangedDrop(
                                        drag.task.status,
                                        drag.targetStatus,
                                        drag.sourceIndex,
                                        drag.targetIndex,
                                    ),
                                )
                            } else if (!isUnchangedDrop(drag.task.status, drag.targetStatus, drag.sourceIndex, drag.targetIndex)) {
                                onDropTask(drag.task, drag.targetStatus, drag.targetIndex)
                            }
                        },
                        onDrag = { change, _ ->
                            val drag = activeDrag ?: return@detectDragGesturesAfterLongPress
                            val coordinates = dragLayerCoordinates ?: return@detectDragGesturesAfterLongPress
                            change.consume()
                            val pointerInRoot = coordinates.localToRoot(change.position)
                            val edgeDirection = edgePageDirection(
                                pointerX = pointerInRoot.x - dragLayerBounds.left,
                                viewportWidth = dragLayerBounds.width,
                                edgeWidth = edgeWidthPx,
                                currentPage = pagerState.targetPage,
                                pageCount = battlePlanStatuses.size,
                            )
                            val moved = drag.copy(
                                pointerInRoot = pointerInRoot,
                                edgeDirection = if (drag.edgeLockedAtPage == null) edgeDirection else 0,
                            )
                            activeDrag = resolveTarget(moved, battlePlanStatuses[pagerState.targetPage])
                        },
                    )
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().then(projectMotion),
                beyondViewportPageCount = battlePlanStatuses.size,
                userScrollEnabled = visualDrag == null,
            ) { page ->
                val status = battlePlanStatuses[page]
                val tasks = state.filteredTasks.filter { it.status == status }
                MobileTaskLane(
                    status = status,
                    tasks = tasks,
                    listState = laneListStates.getValue(status),
                    taskCount = { target -> state.filteredTasks.count { it.status == target } },
                    serverNow = state.serverNow,
                    timezone = state.timezone,
                    onOpen = onOpenTask,
                    onToggleReady = onToggleReady,
                    onRequestMoveProject = onRequestMoveProject,
                    onDrop = onDropTask,
                    onMoveToBoundary = onMoveTaskToBoundary,
                    onSetBlocked = onSetBlocked,
                    onRequestTrash = onRequestTrash,
                    manualOrder = state.sort == BattlePlanSort.Manual,
                    absoluteLaneTasks = state.tasks.filter { it.status == status }.sortedBy { it.position },
                    activeDrag = visualDrag,
                    sourceGapCloseProgress = sourceGapCloseProgress,
                    onLaneBoundsChanged = { bounds ->
                        if (bounds == null) {
                            laneBounds.remove(status)
                        } else if (laneBounds[status] != bounds) {
                            laneBounds[status] = bounds
                        }
                    },
                    onCardBoundsChanged = { taskId, bounds ->
                        val key = status to taskId
                        if (bounds == null) {
                            cardBounds.remove(key)
                        } else if (cardBounds[key] != bounds) {
                            cardBounds[key] = bounds
                        }
                        activeDrag?.takeIf { it.targetStatus == status }?.let { drag ->
                            activeDrag = resolveTarget(drag, status)
                        }
                    },
                    onDropIndicatorBoundsChanged = { index, bounds ->
                        val key = status to index
                        if (bounds == null) {
                            dropIndicatorBounds.remove(key)
                        } else if (dropIndicatorBounds[key] != bounds) {
                            dropIndicatorBounds[key] = bounds
                        }
                    },
                )
            }
            PrimaryButton(
                text = "New task",
                onClick = { onShowComposer(true) },
                enabled = !state.saving,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                leading = { Icon(Icons.Outlined.Add, null, tint = colors.onAction, modifier = Modifier.size(18.dp)) },
            )
            visualDrag?.let { drag ->
                val settling = settlingDrop?.takeIf { it.drag.task.id == drag.task.id }
                MobileTaskDragPreview(
                    drag = drag,
                    dragLayerBounds = dragLayerBounds,
                    pickupProgress = pickupProgress,
                    settleTargetTopLeftInRoot = settling?.targetTopLeftInRoot,
                    settleProgress = if (settling == null) 0f else settleProgress.value,
                    serverNow = state.serverNow,
                    timezone = state.timezone,
                )
            }
        }
    }

    if (filterSheet) {
        ModalBottomSheet(
            onDismissRequest = { filterSheet = false },
            sheetState = filterSheetState,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            containerColor = colors.lowest,
            scrimColor = colors.scrim,
        ) {
            BattlePlanFilterSheetContent(
                state = state,
                onToggleUrgency = onToggleUrgency,
                onToggleImportance = onToggleImportance,
                onToggleTaskType = onToggleTaskType,
                onClearFilters = onClearFilters,
                onSetHideCompleted = onSetHideCompleted,
                onArchiveCompleted = {
                    onArchiveCompleted()
                    filterSheet = false
                },
                onDismiss = { filterSheet = false },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BattlePlanFilterSheetContent(
    state: BattlePlanUiState,
    onToggleUrgency: (String) -> Unit,
    onToggleImportance: (String) -> Unit,
    onToggleTaskType: (String) -> Unit,
    onClearFilters: () -> Unit,
    onSetHideCompleted: (Boolean) -> Unit,
    onArchiveCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val hasFilters = state.urgencyFilter.isNotEmpty() ||
        state.importanceFilter.isNotEmpty() || state.taskTypeFilter.isNotEmpty()
    val resultCount = state.filteredTasks.size

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Filters",
                style = TimeboxTheme.type.sectionTitle.copy(fontSize = 19.sp, letterSpacing = (-0.02).em),
                color = colors.on,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (hasFilters) {
                TextButton(onClick = onClearFilters, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        "Clear all",
                        style = TimeboxTheme.type.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.planned,
                    )
                }
            }
        }

        FilterPrioritySection(
            title = "Urgency",
            selected = state.urgencyFilter,
            onToggle = onToggleUrgency,
            modifier = Modifier.padding(top = 14.dp),
        )
        FilterPrioritySection(
            title = "Importance",
            selected = state.importanceFilter,
            onToggle = onToggleImportance,
            modifier = Modifier.padding(top = 16.dp),
        )

        Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp)) {
            FilterSectionLabel("Task type")
            Spacer(Modifier.height(9.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                state.taskTypes.forEach { type ->
                    val id = type.id.toString()
                    TimeboxChip(
                        label = type.name,
                        selected = id in state.taskTypeFilter,
                        onClick = { onToggleTaskType(id) },
                        height = 36.dp,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    )
                }
            }
        }

        Hairline(Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .toggleable(
                    value = state.hideCompleted,
                    role = Role.Switch,
                    onValueChange = onSetHideCompleted,
                )
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Hide completed",
                    style = TimeboxTheme.type.label.copy(fontSize = 14.sp),
                    color = colors.on,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Keeps finished work out of the board",
                    style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.5.sp),
                    color = colors.onVariant,
                )
            }
            TimeboxSwitch(state.hideCompleted, onSetHideCompleted)
        }

        if (state.completedForArchive.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 14.dp, end = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onArchiveCompleted)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Archive,
                    contentDescription = null,
                    tint = colors.onVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Archive completed (${state.completedForArchive.size})",
                    style = TimeboxTheme.type.label.copy(fontSize = 13.sp),
                    color = colors.onVariant,
                )
            }
        }

        PrimaryButton(
            text = "Show $resultCount ${if (resultCount == 1) "task" else "tasks"}",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 20.dp),
            height = 50.dp,
            shape = RoundedCornerShape(25.dp),
        )
    }
}

@Composable
private fun FilterPrioritySection(
    title: String,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        FilterSectionLabel(title)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("low", "medium", "high").forEach { value ->
                TimeboxChip(
                    label = value.replaceFirstChar(Char::uppercase),
                    selected = value in selected,
                    onClick = { onToggle(value) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    height = 40.dp,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    textStyle = TimeboxTheme.type.label.copy(fontSize = 13.sp),
                )
            }
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = TimeboxTheme.type.laneLabel.copy(letterSpacing = 0.16.em),
        color = TimeboxTheme.colors.onVariant,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun MenuSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = TimeboxTheme.type.laneLabel,
        color = TimeboxTheme.colors.onVariant,
        modifier = Modifier
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ScopeMenuInsetSection(
    label: String,
    testTag: String,
    content: @Composable () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Column {
        Text(
            text = label.uppercase(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TimeboxTheme.type.laneLabel,
            color = colors.onVariant,
            modifier = Modifier
                .padding(start = 4.dp, bottom = 6.dp)
                .semantics { heading() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TimeboxShapes.card)
                .background(colors.low)
                .testTag(testTag),
        ) {
            content()
        }
    }
}

@Composable
private fun ScopeMenuInsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        color = TimeboxTheme.colors.hairline,
    )
}

@Composable
private fun ScopeMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val contentColor = if (destructive) colors.error else colors.on
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = TimeboxTheme.type.label,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(TimeboxShapes.chip)
                    .background(if (selected) colors.lowest else colors.raised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(17.dp),
                )
            }
        },
        trailingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = colors.on) }
        } else null,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TimeboxDimens.touchTarget)
            .background(if (selected) colors.selected else Color.Transparent),
    )
}

@Composable
private fun MobileTaskLane(
    status: TaskStatus,
    tasks: List<BattleTask>,
    listState: LazyListState,
    taskCount: (TaskStatus) -> Int,
    serverNow: java.time.Instant,
    timezone: String,
    onOpen: (Int) -> Unit,
    onToggleReady: (BattleTask) -> Unit,
    onRequestMoveProject: (BattleTask) -> Unit,
    onDrop: (BattleTask, TaskStatus, Int) -> Unit,
    onMoveToBoundary: (BattleTask, Boolean) -> Unit,
    onSetBlocked: (BattleTask, Boolean, String?) -> Unit,
    onRequestTrash: (BattleTask) -> Unit,
    manualOrder: Boolean,
    absoluteLaneTasks: List<BattleTask>,
    activeDrag: MobileTaskDragState?,
    sourceGapCloseProgress: Float,
    onLaneBoundsChanged: (Rect?) -> Unit,
    onCardBoundsChanged: (Int, Rect?) -> Unit,
    onDropIndicatorBoundsChanged: (Int, Rect?) -> Unit,
) {
    val density = LocalDensity.current
    val visibleTasks = tasks.filterNot { it.id == activeDrag?.task?.id }
    val insertionIndex = activeDrag
        ?.takeIf { it.targetStatus == status }
        ?.targetIndex
        ?.coerceIn(0, visibleTasks.size)
    val sourceGapIndex = activeDrag
        ?.takeIf { it.task.status == status }
        ?.sourceIndex
        ?.coerceIn(0, visibleTasks.size)
    val sourceGapHeight = activeDrag
        ?.takeIf { sourceGapIndex != null }
        ?.let { drag ->
            val originalCardHeight = with(density) { drag.cardBoundsInRoot.height.toDp() }
            val originalBottomSpacing = if (drag.sourceIndex < tasks.lastIndex) 10.dp else 0.dp
            val markerHeight = if (drag.targetStatus == status && insertionIndex == sourceGapIndex) 20.dp else 0.dp
            (originalCardHeight + originalBottomSpacing - markerHeight)
                .coerceAtLeast(0.dp) * (1f - sourceGapCloseProgress.coerceIn(0f, 1f))
        }
        ?: 0.dp

    DisposableEffect(status) {
        onDispose { onLaneBoundsChanged(null) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { onLaneBoundsChanged(it.boundsInRoot()) },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        ) {
            for (index in 0..visibleTasks.size) {
                if (insertionIndex == index) {
                    item(key = "drop-indicator-${status.name}-$index") {
                        MobileDropIndicator(
                            status = status,
                            index = index,
                            onBoundsChanged = onDropIndicatorBoundsChanged,
                        )
                    }
                }
                if (sourceGapIndex == index && sourceGapHeight > 0.dp) {
                    item(key = "source-gap-${activeDrag?.task?.id}") {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(sourceGapHeight)
                                .testTag("battle-plan-source-gap"),
                        )
                    }
                }
                if (index < visibleTasks.size) {
                    val task = visibleTasks[index]
                    item(key = task.id) {
                        MobileKanbanCard(
                            task = task,
                            taskCount = taskCount,
                            serverNow = serverNow,
                            timezone = timezone,
                            bottomSpacing = if (index < visibleTasks.lastIndex && insertionIndex != index + 1) 10.dp else 0.dp,
                            onOpen = onOpen,
                            onToggleReady = onToggleReady,
                            onRequestMoveProject = onRequestMoveProject,
                            onDrop = onDrop,
                            onMoveToBoundary = onMoveToBoundary,
                            onSetBlocked = onSetBlocked,
                            onRequestTrash = onRequestTrash,
                            manualOrder = manualOrder,
                            canMoveToTop = absoluteLaneTasks.firstOrNull()?.id != task.id,
                            canMoveToBottom = absoluteLaneTasks.lastOrNull()?.id != task.id,
                            onBoundsChanged = onCardBoundsChanged,
                        )
                    }
                }
            }
        }
        if (visibleTasks.isEmpty() && insertionIndex == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No ${status.label.lowercase()} tasks", color = TimeboxTheme.colors.onVariant)
            }
        }
    }
}

@Composable
private fun MobileDropIndicator(
    status: TaskStatus,
    index: Int,
    onBoundsChanged: (Int, Rect?) -> Unit,
) {
    DisposableEffect(status, index) {
        onDispose { onBoundsChanged(index, null) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .testTag("battle-plan-drop-indicator-${status.name.lowercase()}-$index")
            .onGloballyPositioned { onBoundsChanged(index, it.boundsInRoot()) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(TimeboxTheme.colors.planned),
        )
    }
}

@Composable
private fun MobileKanbanCard(
    task: BattleTask,
    taskCount: (TaskStatus) -> Int,
    serverNow: java.time.Instant,
    timezone: String,
    bottomSpacing: Dp,
    onOpen: (Int) -> Unit,
    onToggleReady: (BattleTask) -> Unit,
    onRequestMoveProject: (BattleTask) -> Unit,
    onDrop: (BattleTask, TaskStatus, Int) -> Unit,
    onMoveToBoundary: (BattleTask, Boolean) -> Unit,
    onSetBlocked: (BattleTask, Boolean, String?) -> Unit,
    onRequestTrash: (BattleTask) -> Unit,
    manualOrder: Boolean,
    canMoveToTop: Boolean,
    canMoveToBottom: Boolean,
    onBoundsChanged: (Int, Rect?) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var menu by remember(task.id) { mutableStateOf(false) }
    var blockDialog by remember(task.id) { mutableStateOf(false) }
    var blockingReason by remember(task.id, blockDialog) { mutableStateOf(task.blockingReason.orEmpty()) }

    DisposableEffect(task.id) {
        onDispose { onBoundsChanged(task.id, null) }
    }

    Box(Modifier.fillMaxWidth().padding(bottom = bottomSpacing)) {
        Column(
            Modifier.fillMaxWidth()
                .testTag("battle-plan-task-${task.id}")
                .onGloballyPositioned { onBoundsChanged(task.id, it.boundsInRoot()) }
                .clip(TimeboxShapes.card)
                .background(mobileTaskCardSurface(colors))
                .border(1.dp, colors.hairline, TimeboxShapes.card)
                .clickable { onOpen(task.id) }
                .padding(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                val completion = LocalCardCompletion.current
                IconButton(
                    onClick = { completion.move(task, if (task.status == TaskStatus.Completed) TaskStatus.Open else TaskStatus.Completed) },
                    enabled = !completion.saving && task.recurrenceKind != "quota_parent",
                    modifier = Modifier.size(44.dp).testTag("battle-plan-complete-${task.id}"),
                ) {
                    Icon(
                        if (task.status == TaskStatus.Completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        when {
                            task.recurrenceKind == "quota_parent" -> "Quota progress is derived from Session Tasks"
                            task.status == TaskStatus.Completed -> "Reopen ${task.title}"
                            else -> "Complete ${task.title}"
                        },
                        Modifier.size(21.dp), tint = colors.onVariant,
                    )
                }
                CompactTaskTitle(task, Modifier.weight(1f).padding(top = 11.dp, end = 12.dp, bottom = 9.dp))
                Box(
                    Modifier
                        .width(MobileTaskPlanningSlotWidth)
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MobilePlanningControl(
                        task = task,
                        plannedSummary = null,
                        onToggleReady = onToggleReady,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        IconButton(
                            onClick = { menu = true },
                            modifier = Modifier
                                .size(TimeboxDimens.touchTarget)
                                .clip(TimeboxShapes.chip)
                                .background(if (menu) colors.surf else Color.Transparent)
                                .semantics {
                                    stateDescription = if (menu) "Expanded" else "Collapsed"
                                },
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Actions for ${task.title}", tint = colors.onVariant)
                        }
                        MobileTaskActionMenu(
                            task = task,
                            expanded = menu,
                            status = task.status,
                            blocked = task.isBlocked,
                            manualOrder = manualOrder,
                            canMoveToTop = canMoveToTop,
                            canMoveToBottom = canMoveToBottom,
                            onDismiss = { menu = false },
                            onToggleBlocked = {
                                menu = false
                                if (task.isBlocked) onSetBlocked(task, false, null) else blockDialog = true
                            },
                            onMoveToTop = { menu = false; onMoveToBoundary(task, true) },
                            onMoveToBottom = { menu = false; onMoveToBoundary(task, false) },
                            onMoveTo = { target -> menu = false; onDrop(task, target, taskCount(target)) },
                            onTrash = { menu = false; onRequestTrash(task) },
                        )
                    }
                }
            }
            CompactTaskMetadata(task, serverNow, timezone, Modifier.padding(start = 44.dp, end = 8.dp, bottom = 6.dp))
            ReadyToPlanFailureNotice(task, Modifier.padding(horizontal = 8.dp))
        }
    }

    if (blockDialog) {
        AlertDialog(
            onDismissRequest = { blockDialog = false },
            title = { Text("Block ${task.title}") },
            text = { OutlinedTextField(blockingReason, { blockingReason = it }, label = { Text("What is blocking this? (optional)") }, minLines = 2) },
            confirmButton = { TextButton(onClick = { blockDialog = false; onSetBlocked(task, true, blockingReason) }) { Text("Mark blocked") } },
            dismissButton = { TextButton(onClick = { blockDialog = false }) { Text("Cancel") } },
        )
    }
}

private val MobileTaskPlanningSlotWidth = 116.dp

/** Shared by the resting card and lifted preview. */
@Composable
private fun CompactTaskTitle(task: BattleTask, modifier: Modifier = Modifier) {
    val completed = task.status == TaskStatus.Completed
    Text(task.title, modifier = modifier, fontSize = 15.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
        textDecoration = if (completed) TextDecoration.LineThrough else null,
        color = if (completed) TimeboxTheme.colors.onVariant else TimeboxTheme.colors.on)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactTaskMetadata(task: BattleTask, serverNow: java.time.Instant, timezone: String, modifier: Modifier = Modifier) {
    val colors = TimeboxTheme.colors
    val completed = task.status == TaskStatus.Completed
    val today = serverNow.atZone(java.time.ZoneId.of(timezone)).toLocalDate()
    val hasContext = task.taskType != null || task.project != null || task.deadlineDate != null
    val hasSignals = (!completed && (task.isBlocked || task.urgency == PriorityLevel.High || task.importance == PriorityLevel.High)) || task.subtasks.isNotEmpty()
    if (!hasContext && !hasSignals) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hasContext) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            task.project?.let { CompactTaskContext(Icons.Outlined.Folder, it.name, "Project", colors.project) }
            task.taskType?.let { CompactTaskContext(Icons.AutoMirrored.Outlined.Label, it.name, "Task type") }
            task.deadlineDate?.let { date ->
                val overdue = !completed && date < today
                CompactTaskContext(Icons.Outlined.CalendarToday,
                    "Due " + date.format(java.time.format.DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy")) + if (overdue) " · overdue" else "",
                    "Deadline", if (overdue) colors.error else colors.onVariant)
            }
        }
        if (hasSignals) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!completed && task.urgency == PriorityLevel.High) CompactTaskSignal("Urgent", true)
            if (!completed && task.importance == PriorityLevel.High) CompactTaskSignal("Important")
            if (!completed && task.isBlocked) CompactTaskSignal("Blocked", true)
            if (task.subtasks.isNotEmpty()) CompactTaskSignal("${task.subtasks.count { it.checked }}/${task.subtasks.size} subtasks")
        }
    }
}

@Composable
private fun CompactTaskContext(icon: ImageVector, label: String, description: String, tint: Color = TimeboxTheme.colors.onVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, description, Modifier.size(12.dp), tint = tint)
        Text(label, fontSize = 11.sp, lineHeight = 16.sp, color = TimeboxTheme.colors.onVariant)
    }
}

@Composable
private fun CompactTaskSignal(label: String, attention: Boolean = false) {
    val colors = TimeboxTheme.colors
    Text(label, modifier = Modifier.clip(TimeboxShapes.chip)
        .background(if (attention) colors.error.copy(alpha = 0.08f) else colors.surf)
        .padding(horizontal = 6.dp, vertical = 2.dp),
        fontSize = 11.sp, lineHeight = 16.sp, color = if (attention) colors.error else colors.onVariant)
}
@Composable
private fun MobileTaskActionMenu(
    task: BattleTask,
    expanded: Boolean,
    status: TaskStatus,
    blocked: Boolean,
    manualOrder: Boolean,
    canMoveToTop: Boolean,
    canMoveToBottom: Boolean,
    onDismiss: () -> Unit,
    onToggleBlocked: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onMoveTo: (TaskStatus) -> Unit,
    onTrash: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val projectMove = LocalProjectMove.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { if (!projectMove.saving) onDismiss() },
        modifier = Modifier
            .widthIn(min = 264.dp, max = 320.dp)
            .testTag("battle-plan-task-actions-menu"),
        shape = TimeboxShapes.group,
        containerColor = colors.lowest,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        if (status == TaskStatus.Completed) {
            MenuSectionLabel("Task")
            MobileTaskActionMenuItem(
                label = "Reopen Task",
                icon = Icons.Outlined.Inbox,
                onClick = { onMoveTo(TaskStatus.Open) },
            )
        } else {
            MenuSectionLabel("Task")
            if (task.parentId == null) MoveProjectMenuItem(task, projectMove, onDismiss)
            MobileTaskActionMenuItem(
                label = if (blocked) "Unblock task" else "Block task",
                icon = Icons.Outlined.Flag,
                onClick = onToggleBlocked,
            )
        }
        if (manualOrder) {
            MenuSectionLabel("Reorder")
            MobileTaskActionMenuItem(
                label = "Move to top",
                icon = Icons.Outlined.KeyboardArrowUp,
                enabled = canMoveToTop,
                disabledReason = "Already at the top",
                onClick = onMoveToTop,
            )
            MobileTaskActionMenuItem(
                label = "Move to bottom",
                icon = Icons.Outlined.KeyboardArrowDown,
                enabled = canMoveToBottom,
                disabledReason = "Already at the bottom",
                onClick = onMoveToBottom,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            color = colors.hairline,
        )
        MobileTaskActionMenuItem(
            label = "Move to Trash",
            icon = Icons.Outlined.DeleteOutline,
            destructive = true,
            onClick = onTrash,
        )
    }
}

@Composable
private fun MobileTaskActionMenuItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    disabledReason: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = TimeboxTheme.type.label,
                color = when {
                    !enabled -> colors.onVariant.copy(alpha = 0.55f)
                    destructive -> colors.error
                    else -> colors.on
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(TimeboxShapes.chip)
                    .background(colors.low),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (destructive) colors.error else colors.onVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        enabled = enabled,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TimeboxDimens.touchTarget)
            .semantics {
                if (!enabled && disabledReason != null) contentDescription = "$label. $disabledReason"
            },
    )
}

internal fun mobileTaskCardSurface(colors: TimeboxColors): Color =
    if (colors.isDark) colors.low else colors.lowest

internal fun mobileTaskDragPreviewSurface(colors: TimeboxColors): Color =
    if (colors.isDark) colors.surf else colors.lowest

@Composable
private fun PlannedDatePill(summary: PlannedDateSummary) {
    val colors = TimeboxTheme.colors
    val background = when (summary.tone) {
        PlannedDateTone.Today -> colors.plannedSurface
        PlannedDateTone.Future -> colors.low
        PlannedDateTone.Past -> colors.low.copy(alpha = 0.55f)
    }
    val foreground = when (summary.tone) {
        PlannedDateTone.Today -> colors.planned
        PlannedDateTone.Future -> colors.onVariant
        PlannedDateTone.Past -> colors.onVariant.copy(alpha = 0.65f)
    }
    Text(
        text = summary.label,
        style = TimeboxTheme.type.bodySmall,
        color = foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

private data class MobileTaskDragState(
    val task: BattleTask,
    val sourceIndex: Int,
    val cardBoundsInRoot: Rect,
    val startPointerInRoot: Offset,
    val pointerInRoot: Offset,
    val targetStatus: TaskStatus,
    val targetIndex: Int,
    val edgeDirection: Int = 0,
    val edgeLockedAtPage: Int? = null,
)

private data class MobileTaskDropSettleState(
    val drag: MobileTaskDragState,
    val targetTopLeftInRoot: Offset,
    val unchanged: Boolean,
)

private const val MobileDragEdgeDwellMillis = 400L
private const val MobilePickupDurationMillis = 220
private const val MobileSourceGapCloseDurationMillis = 240
private const val MobileDropSettleDurationMillis = 220

internal val MobilePickupProgressKey = SemanticsPropertyKey<Float>("MobilePickupProgress")
private var SemanticsPropertyReceiver.mobilePickupProgress by MobilePickupProgressKey
internal val MobileDragPreviewSurfaceKey = SemanticsPropertyKey<Color>("MobileDragPreviewSurface")
private var SemanticsPropertyReceiver.mobileDragPreviewSurface by MobileDragPreviewSurfaceKey

internal fun insertionIndexForPointer(
    pointerY: Float,
    itemCount: Int,
    measuredBounds: List<IndexedValue<Rect>>,
): Int {
    if (itemCount == 0 || measuredBounds.isEmpty()) return 0
    val ordered = measuredBounds.sortedBy { it.index }
    val next = ordered.firstOrNull { pointerY < it.value.center.y }
    return (next?.index ?: (ordered.last().index + 1)).coerceIn(0, itemCount)
}

internal fun insertionIndexWithHysteresis(
    pointerY: Float,
    itemCount: Int,
    measuredBounds: List<IndexedValue<Rect>>,
    currentIndex: Int,
    hysteresis: Float,
): Int {
    val raw = insertionIndexForPointer(pointerY, itemCount, measuredBounds)
    val current = currentIndex.coerceIn(0, itemCount)
    if (raw == current || hysteresis <= 0f) return raw
    val boundaryIndex = if (raw > current) current else current - 1
    val boundary = measuredBounds.firstOrNull { it.index == boundaryIndex }?.value?.center?.y ?: return raw
    return when {
        raw > current && pointerY <= boundary + hysteresis -> current
        raw < current && pointerY >= boundary - hysteresis -> current
        else -> raw
    }
}

internal fun verticalAutoScrollStep(
    pointerY: Float,
    laneBounds: Rect,
    edgeSize: Float,
    maximumStep: Float,
): Float = when {
    edgeSize <= 0f || maximumStep <= 0f -> 0f
    pointerY < laneBounds.top + edgeSize -> {
        val strength = ((laneBounds.top + edgeSize - pointerY) / edgeSize).coerceIn(0f, 1f)
        -maximumStep * strength
    }
    pointerY > laneBounds.bottom - edgeSize -> {
        val strength = ((pointerY - (laneBounds.bottom - edgeSize)) / edgeSize).coerceIn(0f, 1f)
        maximumStep * strength
    }
    else -> 0f
}

internal fun isUnchangedDrop(
    sourceStatus: TaskStatus,
    targetStatus: TaskStatus,
    sourceIndex: Int,
    targetIndex: Int,
): Boolean = sourceStatus == targetStatus && sourceIndex == targetIndex

internal fun edgePageDirection(
    pointerX: Float,
    viewportWidth: Float,
    edgeWidth: Float,
    currentPage: Int,
    pageCount: Int,
): Int = when {
    pointerX <= edgeWidth && currentPage > 0 -> -1
    pointerX >= viewportWidth - edgeWidth && currentPage < pageCount - 1 -> 1
    else -> 0
}

@Composable
private fun MobileTaskDragPreview(
    drag: MobileTaskDragState,
    dragLayerBounds: Rect,
    pickupProgress: Float,
    settleTargetTopLeftInRoot: Offset?,
    settleProgress: Float,
    serverNow: java.time.Instant,
    timezone: String,
) {
    val colors = TimeboxTheme.colors
    val previewSurface = mobileTaskDragPreviewSurface(colors)
    val density = LocalDensity.current
    val delta = drag.pointerInRoot - drag.startPointerInRoot
    val width = with(density) { drag.cardBoundsInRoot.width.toDp() }
    val pickupLiftPx = with(density) { 2.dp.toPx() }
    val draggedTopLeftInRoot = Offset(
        x = drag.cardBoundsInRoot.left + delta.x,
        y = drag.cardBoundsInRoot.top + delta.y,
    )
    val progress = settleProgress.coerceIn(0f, 1f)
    val liftProgress = pickupProgress.coerceIn(0f, 1f) * (1f - progress)
    val currentTopLeftInRoot = settleTargetTopLeftInRoot?.let { target ->
        draggedTopLeftInRoot + (target - draggedTopLeftInRoot) * progress
    } ?: draggedTopLeftInRoot
    val left = currentTopLeftInRoot.x - dragLayerBounds.left
    val top = currentTopLeftInRoot.y - dragLayerBounds.top - (pickupLiftPx * liftProgress)

    Column(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .width(width)
            .zIndex(4f)
            .testTag("battle-plan-drag-preview")
            .semantics {
                mobilePickupProgress = pickupProgress
                mobileDragPreviewSurface = previewSurface
            }
            .graphicsLayer {
                alpha = 1f - (0.25f * liftProgress)
                scaleX = 1f + (0.02f * liftProgress)
                scaleY = 1f + (0.02f * liftProgress)
                shadowElevation = 14f * liftProgress
                shape = TimeboxShapes.card
            }
            .clip(TimeboxShapes.card)
            .background(previewSurface)
            .border(1.dp, colors.hairline, TimeboxShapes.card)
            .padding(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(if (drag.task.status == TaskStatus.Completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    null, Modifier.size(21.dp), tint = colors.onVariant)
            }
            CompactTaskTitle(drag.task, Modifier.weight(1f).padding(top = 11.dp, end = 12.dp, bottom = 9.dp))
            Box(
                Modifier
                    .width(MobileTaskPlanningSlotWidth)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                MobilePlanningControl(drag.task, null, {}, allowInteraction = false)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(TimeboxDimens.touchTarget), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MoreVert, null, tint = colors.onVariant)
                }
            }
        }
        CompactTaskMetadata(drag.task, serverNow, timezone, Modifier.padding(start = 44.dp, end = 8.dp, bottom = 6.dp))
    }
}

@Composable
private fun BattlePlanFilters(
    state: BattlePlanUiState,
    onUrgency: (String) -> Unit,
    onImportance: (String) -> Unit,
    onTaskType: (String) -> Unit,
    onClear: () -> Unit,
) {
    var typeMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterButtons("Urgency", state.urgencyFilter, onUrgency)
            FilterButtons("Importance", state.importanceFilter, onImportance)
            Box {
                TextButton(onClick = { typeMenu = true }) { Text("Task types${if (state.taskTypeFilter.isEmpty()) "" else " · ${state.taskTypeFilter.size}"}") }
                DropdownMenu(typeMenu, { typeMenu = false }) {
                    DropdownMenuItem({ Text(filterMark("No task type", "unset" in state.taskTypeFilter)) }, { onTaskType("unset") })
                    state.taskTypes.forEach { type ->
                        DropdownMenuItem({ Text(filterMark(type.name, type.id.toString() in state.taskTypeFilter)) }, { onTaskType(type.id.toString()) })
                    }
                }
            }
            if (state.urgencyFilter.isNotEmpty() || state.importanceFilter.isNotEmpty() || state.taskTypeFilter.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear filters") }
            }
        }
    }
}

@Composable
private fun FilterButtons(label: String, selected: Set<String>, onToggle: (String) -> Unit) {
    Text(label, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
    listOf("low", "medium", "high", "unset").forEach { value ->
        val displayValue = if (value == "unset") "No ${label.lowercase()}" else value.replaceFirstChar(Char::uppercase)
        TextButton(onClick = { onToggle(value) }) { Text(filterMark(displayValue, value in selected)) }
    }
}

private fun filterMark(label: String, selected: Boolean) = if (selected) "✓ $label" else label

@Composable
private fun TaskColumn(
    title: String,
    tasks: List<BattleTask>,
    modifier: Modifier,
    manualOrder: Boolean,
    serverNow: java.time.Instant,
    timezone: String,
    onOpen: (Int) -> Unit,
    onToggleReady: (BattleTask) -> Unit,
    onRequestMoveProject: (BattleTask) -> Unit,
    onMove: (BattleTask, TaskStatus) -> Unit,
    onReorder: (BattleTask, Int) -> Unit,
    onCreateSubtask: (BattleTask, String) -> Unit,
    onToggleSubtask: (Subtask) -> Unit,
) {
    Column(modifier.fillMaxSize().clip(TimeboxShapes.card).background(TimeboxTheme.colors.low).padding(6.dp)) {
        Text("$title  ${tasks.size}", style = TimeboxTheme.type.label, modifier = Modifier.padding(8.dp))
        TaskList(tasks, Modifier.fillMaxSize(), manualOrder, serverNow, timezone, onOpen, onToggleReady, onRequestMoveProject, onMove, onReorder, onCreateSubtask, onToggleSubtask)
    }
}

@Composable
private fun TaskList(
    tasks: List<BattleTask>,
    modifier: Modifier,
    manualOrder: Boolean,
    serverNow: java.time.Instant,
    timezone: String,
    onOpen: (Int) -> Unit,
    onToggleReady: (BattleTask) -> Unit,
    onRequestMoveProject: (BattleTask) -> Unit,
    onMove: (BattleTask, TaskStatus) -> Unit,
    onReorder: (BattleTask, Int) -> Unit,
    onCreateSubtask: (BattleTask, String) -> Unit,
    onToggleSubtask: (Subtask) -> Unit,
) {
    if (tasks.isEmpty()) {
        Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) { Text("Nothing here.", color = TimeboxTheme.colors.onVariant) }
    } else {
        LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp, 2.dp, 4.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tasks, key = { it.id }) { task ->
                BattleTaskCard(task, manualOrder, serverNow, timezone, onOpen, onToggleReady, onRequestMoveProject, onMove, onReorder, onCreateSubtask, onToggleSubtask)
            }
        }
    }
}

@Composable
private fun BattleTaskCard(
    task: BattleTask,
    manualOrder: Boolean,
    serverNow: java.time.Instant,
    timezone: String,
    onOpen: (Int) -> Unit,
    onToggleReady: (BattleTask) -> Unit,
    onRequestMoveProject: (BattleTask) -> Unit,
    onMove: (BattleTask, TaskStatus) -> Unit,
    onReorder: (BattleTask, Int) -> Unit,
    onCreateSubtask: (BattleTask, String) -> Unit,
    onToggleSubtask: (Subtask) -> Unit,
) {
    val colors = TimeboxTheme.colors
    var menu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var newSubtask by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(colors.lowest)
            .clickable { onOpen(task.id) }.padding(start = 15.dp, top = 14.dp, bottom = 14.dp, end = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(task.title, style = TimeboxTheme.type.label, color = colors.on)
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    task.description,
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(task.project?.name ?: "Admin", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                task.taskType?.let { Text("· ${it.name}", style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
                if (task.overdue) Text("Overdue", color = colors.error, style = TimeboxTheme.type.bodySmall)
            }
            val leadingDetails = buildList {
                task.urgency?.let { add("Urgency ${it.wire}") }
                task.importance?.let { add("Importance ${it.wire}") }
            }
            if (leadingDetails.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(leadingDetails.joinToString(" · "), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            plannedDateSummary(task.plannedDates, serverNow, timezone)?.let { summary ->
                Spacer(Modifier.height(5.dp))
                PlannedDatePill(summary)
            }
            if (task.recurringTemplateId != null) {
                Spacer(Modifier.height(5.dp))
                RecurrenceBadge(task)
            }
            val details = buildList {
                task.deadlineDate?.let { add("Due $it") }
                task.deadlineAt?.let { add("Due $it") }
                if (task.outstandingOccurrenceCount > 1) {
                    add("${task.outstandingOccurrenceCount} outstanding")
                }
                if (task.subtasks.isNotEmpty()) {
                    add("${task.subtasks.count { it.checked }}/${task.subtasks.size} subtasks")
                }
            }
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(details.joinToString(" · "), style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            }
            if (task.readyToPlan) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (task.readinessPending) "Saving · Ready to Plan" else "Ready to Plan",
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.planned,
                )
            }
            ReadyToPlanFailureNotice(task)
        }
        IconButton(onClick = { onToggleReady(task) }, enabled = task.status != TaskStatus.Completed) {
            Icon(
                if (task.readyToPlan) Icons.Outlined.CheckCircle else Icons.Outlined.EventAvailable,
                contentDescription = readyToPlanActionDescription(task),
                tint = if (task.readyToPlan) colors.planned else colors.onVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Task actions", tint = colors.onVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (task.status != TaskStatus.Completed) {
                    DropdownMenuItem(text = { Text("Move to project") }, onClick = { menu = false; onRequestMoveProject(task) })
                }
                if (task.status == TaskStatus.Completed) {
                    DropdownMenuItem(
                        text = { Text("Reopen Task") },
                        onClick = { menu = false; onMove(task, TaskStatus.Open) },
                    )
                } else if (manualOrder) {
                    DropdownMenuItem(text = { Text("Move earlier") }, onClick = { menu = false; onReorder(task, -1) })
                    DropdownMenuItem(text = { Text("Move later") }, onClick = { menu = false; onReorder(task, 1) })
                }
                battlePlanStatuses.filter { task.status != TaskStatus.Completed && it != task.status }.forEach { target ->
                    DropdownMenuItem(
                        text = { Text("Move to ${target.label}") },
                        onClick = { menu = false; onMove(task, target) },
                    )
                }
            }
        }
    }
    if (task.subtasks.isNotEmpty() || expanded) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, bottom = 8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide subtasks" else "Show ${task.subtasks.size} subtasks") }
            if (expanded) {
                task.subtasks.forEach { child ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (child.checked) "✓ ${child.title}" else child.title, modifier = Modifier.weight(1f), style = TimeboxTheme.type.bodySmall)
                            TextButton(
                                onClick = { onToggleSubtask(child) },
                                enabled = task.status != TaskStatus.Completed,
                            ) { Text(if (child.checked) "Uncheck" else "Check") }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newSubtask,
                        { newSubtask = it },
                        Modifier.weight(1f),
                        enabled = task.status != TaskStatus.Completed,
                        label = { Text("New subtask") },
                        singleLine = true,
                    )
                    TextButton(
                        enabled = newSubtask.isNotBlank() && task.status != TaskStatus.Completed,
                        onClick = { onCreateSubtask(task, newSubtask); newSubtask = "" },
                    ) { Text("Add") }
                }
            }
        }
    } else if (task.status != TaskStatus.Completed) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.padding(start = 12.dp)) { Text("Add subtask") }
    }
}

@Composable
private fun RecurrenceBadge(task: BattleTask) {
    val accessibilityLabel = recurrenceAccessibleName(task) ?: return
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .clip(TimeboxShapes.chip)
            .background(colors.surf)
            .border(1.dp, colors.hairline, TimeboxShapes.chip)
            .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Repeat,
            contentDescription = null,
            tint = colors.onVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            "Recurring",
            style = TimeboxTheme.type.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = colors.onVariant,
            maxLines = 1,
        )
    }
}

internal fun recurrenceAccessibleName(task: BattleTask): String? {
    if (task.recurringTemplateId == null || task.parentId != null) return null
    val seriesTitle = task.recurringTemplateTitle?.trim()?.takeIf(String::isNotEmpty)
    return if (task.recurrenceKind == "quota_parent") {
        seriesTitle?.let { "Quota Tracker from Recurring Task Series $it" }
            ?: "Quota Tracker from a Recurring Task Series"
    } else {
        seriesTitle?.let { "Recurring Task Occurrence from $it" }
            ?: "Recurring Task Occurrence"
    }
}

@Composable
private fun UtilityTaskList(
    state: BattlePlanUiState,
    onBackToBoard: () -> Unit,
    onRestoreArchived: (BattleTask) -> Unit,
    onRestoreTrashed: (BattleTask) -> Unit,
    onPermanentDelete: (BattleTask) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBackToBoard) { Text("← Board") }
            Text(
                if (state.collection == TaskCollection.Archived) "Archive" else "Trash",
                style = TimeboxTheme.type.sectionTitle,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (state.visibleTasks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text("Nothing here.", color = TimeboxTheme.colors.onVariant) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.visibleTasks, key = { it.id }) { task ->
                    Column(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(TimeboxTheme.colors.low).padding(14.dp)) {
                        Text(task.title, style = TimeboxTheme.type.label)
                        Text(task.project?.name ?: "Admin", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                        if (state.collection == TaskCollection.Trash) {
                            trashRetentionDays(state.serverNow, task.deletedAt)?.let { days ->
                                Text("$days day${if (days == 1) "" else "s"} remaining", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { if (state.collection == TaskCollection.Archived) onRestoreArchived(task) else onRestoreTrashed(task) }) { Text("Restore") }
                            if (state.collection == TaskCollection.Trash) {
                                TextButton(onClick = { onPermanentDelete(task) }) { Text("Delete permanently", color = TimeboxTheme.colors.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDeleteDialog(
    summary: ProjectDeleteSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, null) },
        title = { Text("Delete ${summary.project.name}?") },
        text = {
            Text(
                "This permanently deletes ${summary.taskCount} project task(s), including archived and trashed tasks, " +
                    "and their subtasks. This cannot be undone."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete permanently") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TaskDetailScreen(
    state: TaskDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStatusChange: (TaskStatus) -> Unit,
    onProjectChange: (Int?) -> Unit,
    onTaskTypeChange: (Int?) -> Unit,
    onUrgencyChange: (PriorityLevel?) -> Unit,
    onImportanceChange: (PriorityLevel?) -> Unit,
    onDeadlineModeChange: (TaskDeadlineMode) -> Unit,
    onDeadlineDateChange: (String) -> Unit,
    onDeadlineTimeChange: (String) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    notificationsAllowed: Boolean,
    onReminderDateChange: (String) -> Unit,
    onReminderTimeChange: (String) -> Unit,
    onReadyChange: (Boolean) -> Unit,
    onOpenDay: (java.time.LocalDate, Int?) -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (Subtask) -> Unit,
    onTrashSubtask: (Subtask) -> Unit,
    onDismissSubtaskTrash: () -> Unit,
    onConfirmSubtaskTrash: () -> Unit,
    onUndoSubtaskTrash: () -> Unit,
    onRequestTrash: () -> Unit,
    onDismissTrash: () -> Unit,
    onConfirmTrash: () -> Unit,
    onTrashed: () -> Unit,
    onStartEditing: () -> Unit,
    onDiscardChanges: () -> Unit,
    onUseLatestTask: () -> Unit,
    onRestoreRecoveredDraft: () -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onSave: () -> Unit,
    onTrackTask: (() -> Unit)? = null,
    onSaveField: (TaskDetailDraft) -> Unit = {},
    onCreateTaskType: (String) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    feedback: @Composable () -> Unit = {},
) {
    var showAllPlannedDates by remember(state.taskId) { mutableStateOf(false) }
    val today = state.serverNow.atZone(java.time.ZoneId.of(state.timezone)).toLocalDate()
    val plannedDates = orderedPlannedDates(state.task?.plannedDates.orEmpty(), today)
    LaunchedEffect(state.trashed) { if (state.trashed) onTrashed() }
    when {
        state.loading || state.error != null -> ModalBottomSheet(onDismissRequest = onBack, containerColor = TimeboxTheme.colors.sheet) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                TextButton(onClick = onBack) { Text("Close task") }
                if (state.loading) CircularProgressIndicator(Modifier.padding(24.dp))
                else {
                    Text(state.error.orEmpty(), color = TimeboxTheme.colors.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
        else -> TaskFieldsSheet(
            draft = state.toTaskDetailDraft(), projects = state.projects, taskTypes = state.taskTypes,
            timezone = state.timezone,
            today = state.serverNow.atZone(java.time.ZoneId.of(state.timezone)).toLocalDate(),
            creating = false, saving = state.saving, dirty = state.dirty,
            error = state.saveError ?: state.validationError?.message,
            notificationsAllowed = notificationsAllowed,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onChange = onSaveField, onDismiss = onBack, onDiscard = onDiscardChanges,
            onRetrySave = onSave, onComplete = if (state.status == TaskStatus.Completed) onReopen else onComplete,
            onReady = onReadyChange,
            onCreateTaskType = onCreateTaskType,
            projectLocked = state.isSubtask || state.task?.recurringTemplateId != null,
            contextLabel = state.task?.recurringTemplateTitle,
            completable = state.task?.recurrenceKind != "quota_parent",
            feedback = feedback,
        ) {
            state.task?.let {
                if (it.readinessPending) Text(if (it.readyToPlan) "Saving · Ready to Plan" else "Saving · Not Ready to Plan",
                    modifier = Modifier.semantics { contentDescription = readyToPlanActionDescription(it) }, color = TimeboxTheme.colors.onVariant)
                ReadyToPlanFailureNotice(it)
            }
            if (state.task?.recurrenceKind == "quota_parent") {
                Text("Quota progress: ${state.task.quotaCompleted ?: 0} / ${state.task.expectedSessions ?: 0}")
                state.task.sessionTasks.forEach { session ->
                    TextButton(onClick = { onOpenTask(session.id) }) { Text("${session.title} · ${session.status.label}") }
                }
            }
            if (!state.isSubtask && state.task?.recurrenceKind != "quota_parent") TaskSubtasks(
                state.subtasks, state.status != TaskStatus.Completed && !state.dirty,
                state.saving, state.saveError, onToggleSubtask, onTrashSubtask, onAddSubtask,
            )
            if (plannedDates.isNotEmpty()) {
                Text("Planned Dates", style = TimeboxTheme.type.label)
                (if (showAllPlannedDates) plannedDates else plannedDates.take(5)).forEach { date ->
                    TextButton(onClick = { onOpenDay(date, null) }) { Text(formatPlannedDetailDate(date, today)) }
                }
                if (plannedDates.size > 5) TextButton(onClick = { showAllPlannedDates = !showAllPlannedDates }) {
                    Text(if (showAllPlannedDates) "Show less" else "Show all (${plannedDates.size})")
                }
            }
            if (onTrackTask != null) TextButton(onClick = onTrackTask, enabled = !state.saving && !state.dirty) { Text("Track task") }
            TextButton(onClick = onRequestTrash, enabled = !state.saving && !state.dirty) { Text("Move to Trash", color = TimeboxTheme.colors.error) }
        }
    }
    state.recoveryConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Recovered edits conflict with a newer Task") },
            text = {
                Text(
                    "This Task changed from version ${conflict.baselineVersion} to ${conflict.currentVersion} while the editor was closed. " +
                        "Use the latest Task, or restore your draft and review it before saving."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onRestoreRecoveredDraft,
                    enabled = state.task?.status != TaskStatus.Completed,
                ) { Text("Restore my draft") }
            },
            dismissButton = { TextButton(onClick = onUseLatestTask) { Text("Use latest task") } },
        )
    }
    if (state.confirmTrash) {
        AlertDialog(
            onDismissRequest = onDismissTrash,
            title = { Text("Move ${state.title} to Trash?") },
            text = { Text(if (state.isSubtask) "The subtask can be restored from Trash while its parent remains active." else "This also moves every subtask to Trash. You can restore it for 30 days.") },
            confirmButton = { TextButton(onClick = onConfirmTrash) { Text("Move to Trash") } },
            dismissButton = { TextButton(onClick = onDismissTrash) { Text("Cancel") } },
        )
    }
    state.pendingSubtaskTrash?.let { task ->
        AlertDialog(
            onDismissRequest = onDismissSubtaskTrash,
            title = { Text("Move ${task.title} to Trash?") },
            text = { Text("The subtask can be restored while its parent remains active.") },
            confirmButton = { TextButton(onClick = onConfirmSubtaskTrash) { Text("Move to Trash") } },
            dismissButton = { TextButton(onClick = onDismissSubtaskTrash) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MobileBlockedPill(task: BattleTask) {
    val colors = TimeboxTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.error.copy(alpha = 0.08f))
            .semantics {
                contentDescription = "${task.title} is blocked"
            }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            "BLOCKED",
            style = TimeboxTheme.type.laneLabel,
            color = if (task.isBlocked) colors.error else colors.onVariant,
        )
    }
}

@Composable
private fun MobilePrioritySignals(task: BattleTask) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (task.urgency == PriorityLevel.High) {
            MobilePrioritySignal("URGENT", urgent = true)
        }
        if (task.importance == PriorityLevel.High) {
            MobilePrioritySignal("IMPORTANT", urgent = false)
        }
    }
}

@Composable
private fun MobilePrioritySignal(label: String, urgent: Boolean) {
    val colors = TimeboxTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (urgent) colors.error.copy(alpha = 0.12f) else colors.plannedSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = TimeboxTheme.type.laneLabel.copy(fontSize = 9.sp),
            color = if (urgent) colors.error else colors.planned,
        )
    }
}

@Composable
private fun MobilePlanningControl(
    task: BattleTask,
    plannedSummary: PlannedDateSummary?,
    onToggleReady: (BattleTask) -> Unit,
    modifier: Modifier = Modifier,
    allowInteraction: Boolean = true,
) {
    val colors = TimeboxTheme.colors
    val completed = task.status == TaskStatus.Completed
    val actionable = !completed && plannedSummary == null
    val interactive = allowInteraction && actionable
    val label = when {
        completed -> "Completed"
        plannedSummary != null -> plannedSummary.label
        task.readinessPending && task.readyToPlan -> "Saving · Ready to Plan"
        task.readinessPending -> "Saving · Not Ready to Plan"
        task.readyToPlan -> "Ready to Plan"
        else -> "Add to Ready to Plan"
    }
    val accented = !completed && (plannedSummary != null || task.readyToPlan)
    val interactiveModifier = if (interactive) Modifier.clickable { onToggleReady(task) } else Modifier
    Box(
        modifier.heightIn(min = 44.dp).then(interactiveModifier).semantics {
            contentDescription = when {
                completed -> "Completed Task"
                plannedSummary != null -> plannedSummary.label
                else -> readyToPlanActionDescription(task)
            }
            stateDescription = label
        },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(TimeboxShapes.chip)
                .background(if (accented) colors.plannedSurface else Color.Transparent)
                .border(1.dp, if (accented) colors.plannedBorder else colors.hairline, TimeboxShapes.chip)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                if (!completed && plannedSummary == null && !task.readinessPending && !task.readyToPlan) "Add to Plan" else label,
                style = TimeboxTheme.type.bodySmall,
                color = if (accented) colors.planned else colors.onVariant,
            )
            if (actionable) Text(
                if (task.readyToPlan) "→" else "+",
                style = TimeboxTheme.type.bodySmall,
                color = if (accented) colors.planned else colors.onVariant,
            )
        }
    }
}

private fun readyToPlanActionDescription(task: BattleTask): String = when {
    task.readinessPending && task.readyToPlan -> "Saving Ready to Plan for ${task.title}"
    task.readinessPending -> "Saving removal from Ready to Plan for ${task.title}"
    task.readyToPlan -> "Remove ${task.title} from Ready to Plan"
    else -> "Add ${task.title} to Ready to Plan"
}

@Composable
internal fun MoveTaskProjectDialog(
    task: BattleTask,
    projects: List<Project>,
    saving: Boolean,
    onMove: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var destination by remember(task.id) { mutableStateOf(task.projectId) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Move to project") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(task.title, modifier = Modifier.padding(bottom = 12.dp))
                (listOf("Admin" to null) + projects.map { it.name to it.id }).forEach { (name, id) ->
                    Row(Modifier.fillMaxWidth().selectable(selected = destination == id, enabled = !saving,
                        role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { destination = id }).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = destination == id, onClick = null, enabled = !saving)
                        Text(name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !saving && destination != task.projectId, onClick = { onMove(destination) }) { Text("Move") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun TaskNavigationMenu(
    state: BattlePlanUiState,
    modifier: Modifier = Modifier,
    recurring: Boolean = false,
    taskTypes: Boolean = false,
    onSelectScope: (BattlePlanScope) -> Unit = {},
    onSelectCollection: (TaskCollection) -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenTaskTypes: () -> Unit = {},
    onReorderProjects: (List<Int>) -> Unit = {},
    onEditProject: (Project) -> Unit = {},
    onPrepareDeleteProject: (Project) -> Unit = {},
    onNewProject: () -> Unit = {},
) {
    val colors = TimeboxTheme.colors
    val density = LocalDensity.current
    var scopeMenu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    fun closeScopeMenu(action: () -> Unit = {}) {
        keyboard?.hide()
        focusManager.clearFocus()
        scopeMenu = false
        action()
    }
    // Recurring and Task Types sit outside the task scopes, so no scope or Project reads as selected.
    val outsideScopes = recurring || taskTypes
    Box(modifier) {
        TextButton(onClick = { scopeMenu = true }) {
            Icon(
                imageVector = when {
                    recurring -> Icons.Outlined.Repeat
                    taskTypes -> Icons.Outlined.Category
                    state.selectedScope.kind == BattlePlanScopeKind.Project -> Icons.Outlined.Folder
                    else -> Icons.AutoMirrored.Outlined.ListAlt
                },
                contentDescription = null,
                tint = if (!outsideScopes && state.selectedScope.kind == BattlePlanScopeKind.Project) colors.project else colors.on,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    recurring -> "Recurring"
                    taskTypes -> "Task Types"
                    else -> state.selectedScope.label
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        if (scopeMenu) {
            var availableMenuHeight by remember { mutableStateOf<Int?>(null) }
            val keyboardInset = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density)
            // Recalculate the visible window when the IME changes, even
            // if the anchor and popup content have not changed size yet.
            val menuPosition = remember(density, keyboardInset) {
                DownwardProjectPositionProvider(with(density) { 8.dp.roundToPx() },
                    startAligned = true) {
                    availableMenuHeight = it
                }
            }
            androidx.compose.ui.window.Popup(
                popupPositionProvider = menuPosition,
                onDismissRequest = { closeScopeMenu() },
                properties = PopupProperties(
                    focusable = true,
                ),
            ) {
                Surface(
                    modifier = Modifier
                        .width(304.dp)
                        .testTag("battle-plan-scope-menu"),
                    shape = TimeboxShapes.group,
                    color = colors.raised,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = availableMenuHeight?.let { with(density) { it.toDp() } }
                                ?: androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 11.dp, vertical = 19.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val taskScopes = state.scopes.filter { it.kind != BattlePlanScopeKind.Project }
                        ScopeMenuInsetSection("Tasks", "battle-plan-scope-menu-tasks") {
                            taskScopes.forEachIndexed { index, scope ->
                                ScopeMenuItem(
                                    label = scope.label,
                                    icon = when (scope.kind) {
                                        BattlePlanScopeKind.All -> Icons.AutoMirrored.Outlined.ListAlt
                                        BattlePlanScopeKind.Admin -> Icons.Outlined.Inbox
                                        BattlePlanScopeKind.Project -> Icons.Outlined.Folder
                                    },
                                    selected = !outsideScopes && scope.preferenceKey == state.selectedScope.preferenceKey,
                                    onClick = { closeScopeMenu { onSelectScope(scope) } },
                                )
                                if (index != taskScopes.lastIndex) ScopeMenuInsetDivider()
                            }
                            ScopeMenuInsetDivider()
                            ScopeMenuItem("Recurring", Icons.Outlined.Repeat, selected = recurring) {
                                closeScopeMenu { onOpenRecurring() }
                            }
                            ScopeMenuInsetDivider()
                            ScopeMenuItem("Task Types", Icons.Outlined.Category, selected = taskTypes) {
                                closeScopeMenu { onOpenTaskTypes() }
                            }
                        }

                        ScopeMenuInsetSection(
                            if (state.projectOrderSaving) "Projects · Saving…" else "Projects",
                            "battle-plan-scope-menu-projects",
                        ) {
                            ProjectNavigationList(
                                maxHeight = 344.dp,
                                projects = state.projects,
                                selectedId = if (outsideScopes) null else state.selectedScope.projectId,
                                saving = state.projectOrderSaving,
                                onSelect = { project -> closeScopeMenu { onSelectScope(BattlePlanScope.project(project)) } },
                                onReorder = onReorderProjects,
                                onEdit = { project -> closeScopeMenu { onEditProject(project) } },
                                onDelete = { project -> closeScopeMenu { onPrepareDeleteProject(project) } },
                            )
                            state.error?.let { Text(it, color = colors.error) }
                            ScopeMenuItem(
                                label = "New project",
                                icon = Icons.Outlined.Add,
                                onClick = { closeScopeMenu { onNewProject() } },
                            )
                        }

                        ScopeMenuInsetSection("Library", "battle-plan-scope-menu-library") {
                            ScopeMenuItem("Archive", Icons.Outlined.Archive) {
                                closeScopeMenu { onSelectCollection(TaskCollection.Archived) }
                            }
                            ScopeMenuInsetDivider()
                            ScopeMenuItem("Trash", Icons.Outlined.Delete, destructive = true) {
                                closeScopeMenu { onSelectCollection(TaskCollection.Trash) }
                            }
                        }
                    }
                }
            }
        }
    }
}
