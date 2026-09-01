package com.timebox.android.ui.battleplan

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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
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
import com.timebox.android.ui.theme.TimeboxColors
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BattlePlanScreen(
    state: BattlePlanUiState,
    onRetry: () -> Unit,
    onSelectCollection: (TaskCollection) -> Unit = {},
    onSelectScope: (BattlePlanScope) -> Unit,
    onSelectStatus: (TaskStatus) -> Unit,
    onToggleUrgency: (String) -> Unit,
    onToggleImportance: (String) -> Unit,
    onToggleTaskType: (String) -> Unit,
    onClearFilters: () -> Unit,
    onSetHideCompleted: (Boolean) -> Unit = {},
    onArchiveCompleted: () -> Unit = {},
    onOpenTask: (Int) -> Unit,
    onToggleReady: (BattleTask) -> Unit,
    onMoveTask: (BattleTask, TaskStatus) -> Unit,
    onReorderTask: (BattleTask, Int) -> Unit,
    onDropTask: (BattleTask, TaskStatus, Int) -> Unit = { _, _, _ -> },
    onSetBlocked: (BattleTask, Boolean, String?) -> Unit = { _, _, _ -> },
    onCreateSubtask: (BattleTask, String) -> Unit,
    onToggleSubtask: (Subtask) -> Unit,
    onCreateTask: (String, String, Int?) -> Unit,
    onShowComposer: (Boolean) -> Unit,
    onComposerDraftChange: (TaskComposerDraft) -> Unit = {},
    onComposerReminderEnabledChange: (Boolean) -> Unit = {},
    notificationsAllowed: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenRecurring: () -> Unit,
    onNewProject: () -> Unit,
    onPrepareDeleteProject: (Project) -> Unit,
    onDismissDeleteProject: () -> Unit,
    onConfirmDeleteProject: () -> Unit,
    onRestoreArchived: (BattleTask) -> Unit,
    onRestoreTrashed: (BattleTask) -> Unit,
    onUndoTrash: () -> Unit,
    onDismissUndo: () -> Unit,
    onRequestPermanentDelete: (BattleTask) -> Unit,
    onDismissPermanentDelete: () -> Unit,
    onConfirmPermanentDelete: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    when {
        state.loading -> LoadingState()
        state.error != null && state.tasks.isEmpty() -> ErrorState(state.error, onRetry)
        else -> Column(Modifier.fillMaxSize()) {
            state.undoTaskId?.let {
                Row(Modifier.fillMaxWidth().background(colors.on).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Moved to Trash", color = colors.bg, modifier = Modifier.weight(1f))
                    TextButton(onClick = onUndoTrash) { Text("Undo", color = colors.bg) }
                    TextButton(onClick = onDismissUndo) { Text("Dismiss", color = colors.bg) }
                }
            }

            if (state.collection == TaskCollection.Active) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= 840.dp) {
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize()) {
                                ScopeSelector(
                                    scopes = state.scopes,
                                    selected = state.selectedScope,
                                    onSelect = onSelectScope,
                                    onNewProject = onNewProject,
                                    onOpenRecurring = onOpenRecurring,
                                )
                                BattlePlanFilters(state, onToggleUrgency, onToggleImportance, onToggleTaskType, onClearFilters)
                                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    battlePlanStatuses.forEach { status ->
                                        TaskColumn(
                                            status.label, state.filteredTasks.filter { it.status == status }, Modifier.weight(1f),
                                            true, state.serverNow, state.timezone, onOpenTask, onToggleReady, onMoveTask,
                                            onReorderTask, onCreateSubtask, onToggleSubtask,
                                        )
                                    }
                                }
                            }
                            PrimaryButton(
                                text = "New task",
                                onClick = { onShowComposer(true) },
                                enabled = !state.saving,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                                leading = { Icon(Icons.Outlined.Add, null, tint = colors.onAction, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    } else {
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
                            onDropTask = onDropTask,
                            onSetBlocked = onSetBlocked,
                            onShowComposer = onShowComposer,
                            onOpenRecurring = onOpenRecurring,
                            onNewProject = onNewProject,
                        )
                    }
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

    if (state.showComposer) {
        TaskComposerOverlay(
            state = state,
            notificationsAllowed = notificationsAllowed,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onDraftChange = onComposerDraftChange,
            onReminderEnabledChange = onComposerReminderEnabledChange,
            onDismiss = { onShowComposer(false) },
            onCreate = { onCreateTask("", "", null) },
        )
    }

    state.projectDeleteSummary?.let { summary ->
        ProjectDeleteDialog(summary, onDismissDeleteProject, onConfirmDeleteProject)
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

@Composable
private fun ScopeSelector(
    scopes: List<BattlePlanScope>,
    selected: BattlePlanScope,
    onSelect: (BattlePlanScope) -> Unit,
    onNewProject: () -> Unit,
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
    onDropTask: (BattleTask, TaskStatus, Int) -> Unit,
    onSetBlocked: (BattleTask, Boolean, String?) -> Unit,
    onShowComposer: (Boolean) -> Unit,
    onOpenRecurring: () -> Unit,
    onNewProject: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val dragScope = rememberCoroutineScope()
    val initialPage = battlePlanStatuses.indexOf(state.selectedStatus).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { battlePlanStatuses.size })
    var scopeMenu by remember { mutableStateOf(false) }
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
    LaunchedEffect(activeDrag?.edgeDirection, pagerState.settledPage) {
        val direction = activeDrag?.edgeDirection ?: return@LaunchedEffect
        if (direction == 0) return@LaunchedEffect
        val targetPage = pagerState.settledPage + direction
        if (targetPage !in battlePlanStatuses.indices) return@LaunchedEffect
        delay(MobileDragEdgeDwellMillis)
        if (activeDrag?.edgeDirection != direction) return@LaunchedEffect
        activeDrag = activeDrag?.copy(edgeDirection = 0, edgeLocked = true)
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
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { scopeMenu = true }) {
                    Icon(
                        imageVector = if (state.selectedScope.kind == BattlePlanScopeKind.Project) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.ListAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(state.selectedScope.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = scopeMenu,
                    onDismissRequest = { scopeMenu = false },
                    modifier = Modifier
                        .width(304.dp)
                        .testTag("battle-plan-scope-menu"),
                    shape = TimeboxShapes.group,
                    containerColor = colors.raised,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(11.dp),
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
                                    selected = scope.preferenceKey == state.selectedScope.preferenceKey,
                                    onClick = { scopeMenu = false; onSelectScope(scope) },
                                )
                                if (index != taskScopes.lastIndex) ScopeMenuInsetDivider()
                            }
                        }

                        val projectScopes = state.scopes.filter { it.kind == BattlePlanScopeKind.Project }
                        ScopeMenuInsetSection("Projects", "battle-plan-scope-menu-projects") {
                            projectScopes.forEach { scope ->
                                ScopeMenuItem(
                                    label = scope.label,
                                    icon = Icons.Outlined.Folder,
                                    selected = scope.preferenceKey == state.selectedScope.preferenceKey,
                                    onClick = { scopeMenu = false; onSelectScope(scope) },
                                )
                                ScopeMenuInsetDivider()
                            }
                            ScopeMenuItem(
                                label = "New project",
                                icon = Icons.Outlined.Add,
                                onClick = { scopeMenu = false; onNewProject() },
                            )
                        }

                        ScopeMenuInsetSection("Library", "battle-plan-scope-menu-library") {
                            ScopeMenuItem("Recurring", Icons.Outlined.Repeat) {
                                scopeMenu = false
                                onOpenRecurring()
                            }
                            ScopeMenuInsetDivider()
                            ScopeMenuItem("Archive", Icons.Outlined.Archive) {
                                scopeMenu = false
                                onSelectCollection(TaskCollection.Archived)
                            }
                            ScopeMenuInsetDivider()
                            ScopeMenuItem("Trash", Icons.Outlined.Delete, destructive = true) {
                                scopeMenu = false
                                onSelectCollection(TaskCollection.Trash)
                            }
                        }
                    }
                }
            }
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
                                edgeDirection = if (drag.edgeLocked) 0 else edgeDirection,
                                edgeLocked = drag.edgeLocked && edgeDirection != 0,
                            )
                            activeDrag = resolveTarget(moved, battlePlanStatuses[pagerState.targetPage])
                        },
                    )
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
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
                    onDrop = onDropTask,
                    onSetBlocked = onSetBlocked,
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
    onDrop: (BattleTask, TaskStatus, Int) -> Unit,
    onSetBlocked: (BattleTask, Boolean, String?) -> Unit,
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
                            index = index,
                            laneSize = visibleTasks.size,
                            taskCount = taskCount,
                            serverNow = serverNow,
                            timezone = timezone,
                            bottomSpacing = if (index < visibleTasks.lastIndex && insertionIndex != index + 1) 10.dp else 0.dp,
                            onOpen = onOpen,
                            onToggleReady = onToggleReady,
                            onDrop = onDrop,
                            onSetBlocked = onSetBlocked,
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
    index: Int,
    laneSize: Int,
    taskCount: (TaskStatus) -> Int,
    serverNow: java.time.Instant,
    timezone: String,
    bottomSpacing: Dp,
    onOpen: (Int) -> Unit,
    onToggleReady: (BattleTask) -> Unit,
    onDrop: (BattleTask, TaskStatus, Int) -> Unit,
    onSetBlocked: (BattleTask, Boolean, String?) -> Unit,
    onBoundsChanged: (Int, Rect?) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val plannedSummary = plannedDateSummary(task.plannedDates, serverNow, timezone)
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        task.parentId != null -> "SUBTASK"
                        task.project != null -> "PROJECT TASK"
                        else -> "ADMIN TASK"
                    },
                    style = TimeboxTheme.type.laneLabel,
                    color = colors.onVariant,
                    modifier = Modifier.weight(1f),
                )
                MobileBlockedPill(
                    task = task,
                    onClick = {
                        if (task.isBlocked) onSetBlocked(task, false, null) else blockDialog = true
                    },
                )
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
                        expanded = menu,
                        status = task.status,
                        canMoveEarlier = index > 0,
                        canMoveLater = index < laneSize - 1,
                        onDismiss = { menu = false },
                        onMoveEarlier = { menu = false; onDrop(task, task.status, index - 1) },
                        onMoveLater = { menu = false; onDrop(task, task.status, index + 1) },
                        onMoveTo = { target -> menu = false; onDrop(task, target, taskCount(target)) },
                    )
                }
            }
            Text(
                task.title,
                style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, fontWeight = FontWeight.Medium),
                color = colors.on,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.project?.name ?: task.parentTitle?.let { "Subtask of $it" } ?: "Admin",
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MobilePrioritySignals(task)
            }
            task.blockingReason?.trim()?.takeIf { task.isBlocked && it.isNotEmpty() }?.let { reason ->
                Text(
                    "Blocker: $reason",
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                    modifier = Modifier.padding(top = 9.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MobilePlanningControl(
                task = task,
                plannedSummary = plannedSummary,
                onToggleReady = onToggleReady,
                modifier = Modifier.padding(top = 14.dp),
            )
            val metadata = buildList {
                task.deadlineDate?.let { add("Due $it") }
                task.urgency?.takeIf { it != PriorityLevel.High }?.let { add("Urgency ${it.wire}") }
                task.importance?.takeIf { it != PriorityLevel.High }?.let { add("Importance ${it.wire}") }
                if (task.subtasks.isNotEmpty()) add("${task.subtasks.count { it.checked }}/${task.subtasks.size} subtasks")
            }
            if (metadata.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                Text(metadata.joinToString("  ·  "), style = TimeboxTheme.type.bodySmall, color = colors.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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

@Composable
private fun MobileTaskActionMenu(
    expanded: Boolean,
    status: TaskStatus,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onDismiss: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onMoveTo: (TaskStatus) -> Unit,
) {
    val colors = TimeboxTheme.colors
    val hasReorderActions = canMoveEarlier || canMoveLater

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
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
            if (hasReorderActions) {
                MenuSectionLabel("Reorder")
                if (canMoveEarlier) {
                    MobileTaskActionMenuItem(
                        label = "Move earlier",
                        icon = Icons.Outlined.KeyboardArrowUp,
                        onClick = onMoveEarlier,
                    )
                }
                if (canMoveLater) {
                    MobileTaskActionMenuItem(
                        label = "Move later",
                        icon = Icons.Outlined.KeyboardArrowDown,
                        onClick = onMoveLater,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    color = colors.hairline,
                )
            }

            MenuSectionLabel("Move to")
            battlePlanStatuses.filter { it != status }.forEach { target ->
                MobileTaskActionMenuItem(
                    label = "Move to ${target.label}",
                    icon = when (target) {
                        TaskStatus.Open -> Icons.Outlined.Inbox
                        TaskStatus.InProgress -> Icons.Outlined.PlayArrow
                        TaskStatus.Completed -> Icons.Outlined.CheckCircle
                        TaskStatus.Blocked -> Icons.Outlined.Inbox
                    },
                    onClick = { onMoveTo(target) },
                )
            }
        }
    }
}

@Composable
private fun MobileTaskActionMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = TimeboxTheme.type.label,
                color = colors.on,
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
                    tint = colors.onVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TimeboxDimens.touchTarget),
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
    val edgeLocked: Boolean = false,
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
    val plannedSummary = plannedDateSummary(drag.task.plannedDates, serverNow, timezone)
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    drag.task.parentId != null -> "SUBTASK"
                    drag.task.project != null -> "PROJECT TASK"
                    else -> "ADMIN TASK"
                },
                style = TimeboxTheme.type.laneLabel,
                color = colors.onVariant,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (drag.task.isBlocked) colors.error.copy(alpha = 0.08f) else Color.Transparent)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(
                    if (drag.task.isBlocked) "BLOCKED" else "BLOCK",
                    style = TimeboxTheme.type.laneLabel,
                    color = if (drag.task.isBlocked) colors.error else colors.onVariant,
                )
            }
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = null,
                tint = colors.onVariant,
                modifier = Modifier.padding(7.dp).size(24.dp),
            )
        }
        Text(
            drag.task.title,
            style = TimeboxTheme.type.screenTitle.copy(fontSize = 23.sp, fontWeight = FontWeight.Medium),
            color = colors.on,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                drag.task.project?.name ?: drag.task.parentTitle?.let { "Subtask of $it" } ?: "Admin",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MobilePrioritySignals(drag.task)
        }
        drag.task.blockingReason?.trim()?.takeIf { drag.task.isBlocked && it.isNotEmpty() }?.let { reason ->
            Text(
                "Blocker: $reason",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                modifier = Modifier.padding(top = 9.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MobilePlanningControl(
            task = drag.task,
            plannedSummary = plannedSummary,
            onToggleReady = {},
            modifier = Modifier.padding(top = 14.dp),
            allowInteraction = false,
        )
        val metadata = buildList {
            drag.task.deadlineDate?.let { add("Due $it") }
            drag.task.urgency?.takeIf { it != PriorityLevel.High }?.let { add("Urgency ${it.wire}") }
            drag.task.importance?.takeIf { it != PriorityLevel.High }?.let { add("Importance ${it.wire}") }
            if (drag.task.subtasks.isNotEmpty()) {
                add("${drag.task.subtasks.count { it.checked }}/${drag.task.subtasks.size} subtasks")
            }
        }
        if (metadata.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Text(
                metadata.joinToString("  ·  "),
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                    DropdownMenuItem({ Text(filterMark("Unset", "unset" in state.taskTypeFilter)) }, { onTaskType("unset") })
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
        TextButton(onClick = { onToggle(value) }) { Text(filterMark(value.replaceFirstChar(Char::uppercase), value in selected)) }
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
    onMove: (BattleTask, TaskStatus) -> Unit,
    onReorder: (BattleTask, Int) -> Unit,
    onCreateSubtask: (BattleTask, String) -> Unit,
    onToggleSubtask: (Subtask) -> Unit,
) {
    Column(modifier.fillMaxSize().clip(TimeboxShapes.card).background(TimeboxTheme.colors.low).padding(6.dp)) {
        Text("$title  ${tasks.size}", style = TimeboxTheme.type.label, modifier = Modifier.padding(8.dp))
        TaskList(tasks, Modifier.fillMaxSize(), manualOrder, serverNow, timezone, onOpen, onToggleReady, onMove, onReorder, onCreateSubtask, onToggleSubtask)
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
                BattleTaskCard(task, manualOrder, serverNow, timezone, onOpen, onToggleReady, onMove, onReorder, onCreateSubtask, onToggleSubtask)
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
            val details = buildList {
                task.deadlineDate?.let { add("Due $it") }
                task.deadlineAt?.let { add("Due $it") }
                task.recurringTemplateTitle?.let { add("Recurring: $it") }
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
                Text("Ready to Plan", style = TimeboxTheme.type.bodySmall, color = colors.planned)
            }
        }
        IconButton(onClick = { onToggleReady(task) }, enabled = task.status != TaskStatus.Completed) {
            Icon(
                if (task.readyToPlan) Icons.Outlined.CheckCircle else Icons.Outlined.EventAvailable,
                contentDescription = if (task.readyToPlan) "Remove from Ready to Plan" else "Mark Ready to Plan",
                tint = if (task.readyToPlan) colors.planned else colors.onVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Task actions", tint = colors.onVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
                    "and their subtasks. Recurring templates are kept and moved to Admin. This cannot be undone."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete permanently") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
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
    onReopen: () -> Unit,
    onSave: () -> Unit,
) {
    LaunchedEffect(state.trashed) { if (state.trashed) onTrashed() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var newSubtask by remember { mutableStateOf("") }
    fun requestBack() { if (state.dirty) confirmDiscard = true else onBack() }
    fun requestSave() = onSave()
    BackHandler(onBack = ::requestBack)
    when {
        state.loading -> LoadingState()
        state.error != null -> ErrorState(state.error, onRetry)
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 840.dp
            if (expanded && !state.isSubtask) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TaskEditForm(state, Modifier.weight(1.5f), ::requestBack, onTitleChange, onDescriptionChange, onStatusChange, onProjectChange, onTaskTypeChange, onUrgencyChange, onImportanceChange, onDeadlineModeChange, onDeadlineDateChange, onDeadlineTimeChange, onReminderEnabledChange, notificationsAllowed, onReminderDateChange, onReminderTimeChange, onReadyChange, onOpenDay, onRequestTrash, onReopen, ::requestSave)
                    SubtaskPanel(state, Modifier.weight(1f), newSubtask, { newSubtask = it }, { onAddSubtask(newSubtask); newSubtask = "" }, onToggleSubtask, onTrashSubtask)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TaskEditForm(state, Modifier.fillMaxWidth(), ::requestBack, onTitleChange, onDescriptionChange, onStatusChange, onProjectChange, onTaskTypeChange, onUrgencyChange, onImportanceChange, onDeadlineModeChange, onDeadlineDateChange, onDeadlineTimeChange, onReminderEnabledChange, notificationsAllowed, onReminderDateChange, onReminderTimeChange, onReadyChange, onOpenDay, onRequestTrash, onReopen, ::requestSave)
                    if (!state.isSubtask) SubtaskPanel(state, Modifier.fillMaxWidth(), newSubtask, { newSubtask = it }, { onAddSubtask(newSubtask); newSubtask = "" }, onToggleSubtask, onTrashSubtask)
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("Your task edits have not been saved.") },
            confirmButton = { TextButton(onClick = onBack) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
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
    if (state.undoSubtaskId != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Subtask moved to Trash") },
            confirmButton = { TextButton(onClick = onUndoSubtaskTrash) { Text("Undo") } },
            dismissButton = { TextButton(onClick = onRetry) { Text("Dismiss") } },
        )
    }
}

@Composable
private fun TaskEditForm(
    state: TaskDetailUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onStatus: (TaskStatus) -> Unit,
    onProject: (Int?) -> Unit,
    onTaskType: (Int?) -> Unit,
    onUrgency: (PriorityLevel?) -> Unit,
    onImportance: (PriorityLevel?) -> Unit,
    onDeadlineMode: (TaskDeadlineMode) -> Unit,
    onDeadlineDate: (String) -> Unit,
    onDeadlineTime: (String) -> Unit,
    onReminderEnabled: (Boolean) -> Unit,
    notificationsAllowed: Boolean,
    onReminderDate: (String) -> Unit,
    onReminderTime: (String) -> Unit,
    onReady: (Boolean) -> Unit,
    onOpenDay: (java.time.LocalDate, Int?) -> Unit,
    onTrash: () -> Unit,
    onReopen: () -> Unit,
    onSave: () -> Unit,
) {
    var editing by remember(state.taskId) { mutableStateOf(false) }
    var showAllPlannedDates by remember(state.taskId) { mutableStateOf(false) }
    val zone = runCatching { java.time.ZoneId.of(state.timezone) }.getOrDefault(java.time.ZoneId.of("UTC"))
    val today = state.serverNow.atZone(zone).toLocalDate()
    val plannedDates = orderedPlannedDates(state.task?.plannedDates.orEmpty(), today)
    val visiblePlannedDates = if (showAllPlannedDates) plannedDates else plannedDates.take(5)
    val projectLabel = state.projects.firstOrNull { it.id == state.projectId }?.name ?: "Admin"
    val taskTypeLabel = state.taskTypes.firstOrNull { it.id == state.taskTypeId }?.name ?: "Unset"
    val deadlineLabel = when (state.deadlineMode) {
        TaskDeadlineMode.None -> "None"
        TaskDeadlineMode.DateOnly -> state.deadlineDate.ifBlank { "Set date" }
        TaskDeadlineMode.DateTime -> listOf(state.deadlineDate, state.deadlineTime).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Set date" }
    }
    val reminderLabel = if (state.reminderEnabled) {
        listOf(state.reminderDate, state.reminderTime).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Reminder set" }
    } else {
        "No reminder"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TaskDetailBackRow(
            onBack = onBack,
            actionLabel = when {
                state.task?.status == TaskStatus.Completed -> "Completed"
                editing -> "Done"
                else -> "Edit details"
            },
            actionSelected = editing,
            actionEnabled = !state.saving && state.task?.status != TaskStatus.Completed,
            onAction = {
                if (editing && state.dirty) {
                    onSave()
                    if (validateTaskDraft(state) is TaskDraftValidation.Valid) editing = false
                } else {
                    editing = !editing
                }
            },
        )
        if (state.task?.status == TaskStatus.Completed) {
            Text(state.task.title, style = TimeboxTheme.type.display, color = TimeboxTheme.colors.on)
            if (state.task.description.isNotBlank()) {
                Text(state.task.description, style = TimeboxTheme.type.body, color = TimeboxTheme.colors.onVariant)
            }
            Text(
                "Completed Tasks are frozen. Reopen to edit the Task or its Subtasks.",
                style = TimeboxTheme.type.bodySmall,
                color = TimeboxTheme.colors.onVariant,
            )
            PrimaryButton("Reopen Task", onReopen, Modifier.fillMaxWidth(), enabled = !state.saving)
            return@Column
        }
        Text("TASK", style = TimeboxTheme.type.kicker, color = TimeboxTheme.colors.onVariant)
        state.parentTask?.let {
            Text("Subtask of ${it.title}", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        }
        if (editing) {
            OutlinedTextField(
                state.title,
                onTitle,
                Modifier.fillMaxWidth(),
                label = { Text("Title") },
                textStyle = TimeboxTheme.type.screenTitle.copy(color = TimeboxTheme.colors.on),
            )
            OutlinedTextField(
                state.description,
                onDescription,
                Modifier.fillMaxWidth(),
                label = { Text("Description") },
                minLines = 2,
            )
            Text(
                "Tap a block or chip below to change that detail.",
                style = TimeboxTheme.type.bodySmall,
                color = TimeboxTheme.colors.onVariant,
            )
        } else {
            Text(state.title, style = TimeboxTheme.type.display, color = TimeboxTheme.colors.on)
            if (state.description.isNotBlank()) {
                Text(state.description, style = TimeboxTheme.type.body, color = TimeboxTheme.colors.onVariant)
            }
        }
        TaskDetailSelectionChip(
            label = state.status.label,
            values = battlePlanStatuses.map { it.label to it },
            enabled = editing,
            onSelect = onStatus,
        )
        if (!state.isSubtask && state.status != TaskStatus.Completed && state.subtasks.isNotEmpty() && state.subtasks.all { it.checked }) {
            TextButton(onClick = { onStatus(TaskStatus.Completed) }, enabled = editing) {
                Text("All subtasks complete · Complete Parent Task")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TaskDetailDashboardTile(
                icon = Icons.Outlined.CalendarMonth,
                label = "Ready to Plan",
                value = if (state.readyToPlan) "Ready" else "Not ready",
                modifier = Modifier.weight(1f),
                accent = state.readyToPlan,
                changeHint = editing,
                enabled = editing,
                onClick = { onReady(!state.readyToPlan) },
            )
            TaskDetailMenuTile(
                icon = Icons.Outlined.Event,
                label = "Deadline",
                value = deadlineLabel,
                values = listOf(
                    "None" to TaskDeadlineMode.None,
                    "Date only" to TaskDeadlineMode.DateOnly,
                    "Date and time" to TaskDeadlineMode.DateTime,
                ),
                modifier = Modifier.weight(1f),
                enabled = editing,
                onSelect = onDeadlineMode,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.isSubtask) {
                TaskDetailDashboardTile(
                    icon = Icons.Outlined.Folder,
                    label = "Project",
                    value = "$projectLabel · inherited",
                    modifier = Modifier.weight(1f),
                    enabled = false,
                    onClick = {},
                )
            } else {
                TaskDetailMenuTile(
                    icon = Icons.Outlined.Folder,
                    label = "Project",
                    value = projectLabel,
                    values = listOf("Admin" to null) + state.projects.map { it.name to it.id },
                    modifier = Modifier.weight(1f),
                    enabled = editing,
                    onSelect = onProject,
                )
            }
            TaskDetailPriorityTile(
                importance = state.importance?.displayLabel() ?: "Unset",
                urgency = state.urgency?.displayLabel() ?: "Unset",
                enabled = editing,
                modifier = Modifier.weight(1f),
                onImportance = onImportance,
                onUrgency = onUrgency,
            )
        }
        if (editing && state.deadlineMode != TaskDeadlineMode.None) {
            TaskDetailInlineEditor("Schedule") {
                OutlinedTextField(
                    state.deadlineDate,
                    onDeadlineDate,
                    Modifier.fillMaxWidth(),
                    label = { Text("Deadline date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                )
                if (state.deadlineMode == TaskDeadlineMode.DateTime) {
                    OutlinedTextField(
                        state.deadlineTime,
                        onDeadlineTime,
                        Modifier.fillMaxWidth(),
                        label = { Text("Deadline time (${state.timezone})") },
                        placeholder = { Text("HH:MM") },
                        singleLine = true,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reminder", style = TimeboxTheme.type.label)
                        Text("Notify me before the deadline.", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
                    }
                    Switch(state.reminderEnabled, onReminderEnabled)
                }
                if (state.reminderEnabled) {
                    if (!notificationsAllowed) {
                        Text(
                            "This reminder will be saved, but this device cannot display it until notifications are enabled in Settings.",
                            style = TimeboxTheme.type.bodySmall,
                            color = TimeboxTheme.colors.error,
                        )
                    }
                    OutlinedTextField(
                        state.reminderDate,
                        onReminderDate,
                        Modifier.fillMaxWidth(),
                        label = { Text("Reminder date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        state.reminderTime,
                        onReminderTime,
                        Modifier.fillMaxWidth(),
                        label = { Text("Reminder time (${state.timezone})") },
                        placeholder = { Text("HH:MM") },
                        singleLine = true,
                    )
                }
            }
        }

        Column {
            Text("THE PLAN", style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.onVariant)
            Spacer(Modifier.height(10.dp))
            if (plannedDates.isEmpty() && state.deadlineMode == TaskDeadlineMode.None) {
                Text("No Planned Blocks or deadline yet.", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            } else {
                if (plannedDates.isNotEmpty()) {
                    Text("Planned Dates", style = TimeboxTheme.type.label, color = TimeboxTheme.colors.on)
                    visiblePlannedDates.forEachIndexed { index, date ->
                        TaskDetailTimelineRow(
                            date = formatPlannedDetailDate(date, today),
                            title = "Planned Block",
                            active = index == 0 && date >= today,
                            onClick = { onOpenDay(date, null) },
                        )
                    }
                    if (plannedDates.size > 5) {
                        TextButton(onClick = { showAllPlannedDates = !showAllPlannedDates }) {
                            Text(if (showAllPlannedDates) "Show less" else "Show all (${plannedDates.size})")
                        }
                    }
                }
                if (state.deadlineMode != TaskDeadlineMode.None) {
                    TaskDetailTimelineRow(date = deadlineLabel, title = "Deadline", active = false)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MORE DETAILS", style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.onVariant)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskDetailInfoMenuChip(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    label = taskTypeLabel,
                    values = listOf("Unset" to null) + state.taskTypes.map { it.name to it.id },
                    enabled = editing,
                    onSelect = onTaskType,
                )
                TaskDetailInfoChip(Icons.Outlined.NotificationsNone, reminderLabel)
            }
        }

        PrimaryButton("Save task", onSave, Modifier.fillMaxWidth(), enabled = state.dirty && !state.saving)
        Row(
            Modifier.fillMaxWidth().clip(TimeboxShapes.cell).clickable(enabled = !state.saving, onClick = onTrash).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DeleteOutline, null, tint = TimeboxTheme.colors.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Move to Trash", style = TimeboxTheme.type.label, color = TimeboxTheme.colors.error)
        }
    }
}

@Composable
private fun MobileBlockedPill(
    task: BattleTask,
    onClick: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val enabled = task.status != TaskStatus.Completed
    val interactiveModifier = if (enabled) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (task.isBlocked) colors.error.copy(alpha = 0.08f) else Color.Transparent)
            .then(interactiveModifier)
            .semantics {
                contentDescription = if (task.isBlocked) {
                    "Clear blocked condition for ${task.title}"
                } else {
                    "Mark ${task.title} blocked"
                }
            }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            if (task.isBlocked) "BLOCKED" else "BLOCK",
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
    val interactive = allowInteraction && !completed && plannedSummary == null
    val label = when {
        completed -> "Completed"
        plannedSummary != null -> plannedSummary.label
        task.readyToPlan -> "Ready to Plan"
        else -> "Add to Ready to Plan"
    }
    val icon = when {
        completed -> Icons.Outlined.CheckCircle
        plannedSummary != null -> Icons.Outlined.CalendarMonth
        task.readyToPlan -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.EventAvailable
    }
    val accented = !completed && (plannedSummary != null || task.readyToPlan)
    val interactiveModifier = if (interactive) Modifier.clickable { onToggleReady(task) } else Modifier

    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (accented) colors.plannedSurface else colors.lowest)
            .border(1.dp, if (accented) colors.plannedBorder else colors.hairline, RoundedCornerShape(10.dp))
            .then(interactiveModifier)
            .semantics {
                contentDescription = when {
                    completed -> "Completed Task"
                    plannedSummary != null -> plannedSummary.label
                    task.readyToPlan -> "Remove ${task.title} from Ready to Plan"
                    else -> "Add ${task.title} to Ready to Plan"
                }
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (accented) colors.planned else colors.onVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = TimeboxTheme.type.label,
            color = if (accented) colors.planned else colors.onVariant,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TaskDetailBackRow(
    onBack: () -> Unit,
    actionLabel: String,
    actionSelected: Boolean,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Battle Plan")
        }
        Text("Battle Plan", style = TimeboxTheme.type.label, color = TimeboxTheme.colors.onVariant, modifier = Modifier.weight(1f))
        TimeboxChip(
            label = actionLabel,
            selected = actionSelected,
            onClick = { if (actionEnabled) onAction() },
            modifier = Modifier.then(if (actionEnabled) Modifier else Modifier.semantics { stateDescription = "Disabled" }),
        )
    }
}

@Composable
private fun <T> TaskDetailSelectionChip(
    label: String,
    values: List<Pair<String, T>>,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TimeboxChip(label = label, selected = true, onClick = { if (enabled) expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (name, value) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { expanded = false; onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun TaskDetailDashboardTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    changeHint: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(120.dp)
            .clip(TimeboxShapes.card)
            .background(if (accent) TimeboxTheme.colors.selected else TimeboxTheme.colors.card)
            .border(1.dp, if (accent) TimeboxTheme.colors.outlineVariant else TimeboxTheme.colors.hairline, TimeboxShapes.card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (accent) TimeboxTheme.colors.onSelected else TimeboxTheme.colors.onVariant, modifier = Modifier.size(20.dp))
            if (changeHint) {
                Spacer(Modifier.weight(1f))
                Text("CHANGE", style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.onVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(label, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        Text(value, style = TimeboxTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun <T> TaskDetailMenuTile(
    icon: ImageVector,
    label: String,
    value: String,
    values: List<Pair<String, T>>,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TaskDetailDashboardTile(
            icon = icon,
            label = label,
            value = value,
            modifier = Modifier.fillMaxWidth(),
            changeHint = enabled,
            enabled = enabled,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (name, item) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { expanded = false; onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun TaskDetailPriorityTile(
    importance: String,
    urgency: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onImportance: (PriorityLevel?) -> Unit,
    onUrgency: (PriorityLevel?) -> Unit,
) {
    var importanceExpanded by remember { mutableStateOf(false) }
    var urgencyExpanded by remember { mutableStateOf(false) }
    val values = listOf("Unset" to null) + PriorityLevel.entries.map { it.displayLabel() to it }
    Column(
        modifier
            .height(120.dp)
            .clip(TimeboxShapes.card)
            .background(TimeboxTheme.colors.card)
            .border(1.dp, TimeboxTheme.colors.hairline, TimeboxShapes.card)
            .padding(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Flag, null, tint = TimeboxTheme.colors.onVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Priority", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
            if (enabled) {
                Spacer(Modifier.weight(1f))
                Text("CHANGE", style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.onVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth().clip(TimeboxShapes.cell)
                        .clickable(enabled = enabled) { importanceExpanded = true }
                        .semantics { contentDescription = "Change importance" }
                        .padding(horizontal = 2.dp),
                ) {
                    Text("IMPORTANCE", style = TimeboxTheme.type.laneLabel.copy(fontSize = 8.sp), color = TimeboxTheme.colors.onVariant, maxLines = 1)
                    Text(importance, style = TimeboxTheme.type.label, maxLines = 1)
                }
                DropdownMenu(importanceExpanded, { importanceExpanded = false }) {
                    values.forEach { (name, value) ->
                        DropdownMenuItem({ Text(name) }, { importanceExpanded = false; onImportance(value) })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth().clip(TimeboxShapes.cell)
                        .clickable(enabled = enabled) { urgencyExpanded = true }
                        .semantics { contentDescription = "Change urgency" }
                        .padding(horizontal = 2.dp),
                ) {
                    Text("URGENCY", style = TimeboxTheme.type.laneLabel.copy(fontSize = 8.sp), color = TimeboxTheme.colors.onVariant, maxLines = 1)
                    Text(urgency, style = TimeboxTheme.type.label, maxLines = 1)
                }
                DropdownMenu(urgencyExpanded, { urgencyExpanded = false }) {
                    values.forEach { (name, value) ->
                        DropdownMenuItem({ Text(name) }, { urgencyExpanded = false; onUrgency(value) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskDetailInlineEditor(title: String, content: @Composable () -> Unit) {
    Surface(shape = TimeboxShapes.card, color = TimeboxTheme.colors.card, contentColor = TimeboxTheme.colors.on) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.onVariant)
            content()
        }
    }
}

@Composable
private fun TaskDetailTimelineRow(
    date: String,
    title: String,
    active: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).clickable(enabled = onClick != null) { onClick?.invoke() },
        verticalAlignment = Alignment.Top,
    ) {
        Text(date, style = TimeboxTheme.type.laneLabel, color = TimeboxTheme.colors.onVariant, modifier = Modifier.width(92.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) TimeboxTheme.colors.tertiary else TimeboxTheme.colors.highest))
            Spacer(Modifier.width(1.dp).weight(1f).background(TimeboxTheme.colors.hairline))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = TimeboxTheme.type.body, color = TimeboxTheme.colors.on, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TaskDetailInfoChip(icon: ImageVector, label: String) {
    Row(
        Modifier.height(38.dp).clip(TimeboxShapes.chip).border(1.dp, TimeboxTheme.colors.hairline, TimeboxShapes.chip).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = TimeboxTheme.colors.onVariant, modifier = Modifier.size(16.dp))
        Text(label, style = TimeboxTheme.type.bodySmall, maxLines = 1)
    }
}

@Composable
private fun <T> TaskDetailInfoMenuChip(
    icon: ImageVector,
    label: String,
    values: List<Pair<String, T>>,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(38.dp).clip(TimeboxShapes.chip).border(1.dp, TimeboxTheme.colors.hairline, TimeboxShapes.chip)
                .clickable(enabled = enabled) { expanded = true }.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = TimeboxTheme.colors.onVariant, modifier = Modifier.size(16.dp))
            Text(label, style = TimeboxTheme.type.bodySmall, maxLines = 1)
        }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { (name, value) ->
                DropdownMenuItem({ Text(name) }, { expanded = false; onSelect(value) })
            }
        }
    }
}

private fun PriorityLevel.displayLabel(): String = wire.replaceFirstChar(Char::uppercase)

@Composable
private fun SubtaskPanel(
    state: TaskDetailUiState,
    modifier: Modifier,
    newSubtask: String,
    onNewSubtask: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (Subtask) -> Unit,
    onTrash: (Subtask) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = TimeboxShapes.card,
        color = TimeboxTheme.colors.low,
        contentColor = TimeboxTheme.colors.on,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Subtasks ${state.subtasks.count { it.checked }}/${state.subtasks.size}", style = TimeboxTheme.type.sectionTitle)
            state.subtasks.forEach { task ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (task.checked) "✓ ${task.title}" else task.title, modifier = Modifier.weight(1f), style = TimeboxTheme.type.bodySmall)
                    TextButton(
                        onClick = { onToggle(task) },
                        enabled = state.status != TaskStatus.Completed,
                    ) { Text(if (task.checked) "Uncheck" else "Check") }
                    IconButton(
                        onClick = { onTrash(task) },
                        enabled = state.status != TaskStatus.Completed,
                    ) { Icon(Icons.Outlined.Delete, "Move ${task.title} to Trash") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    newSubtask,
                    onNewSubtask,
                    Modifier.weight(1f),
                    enabled = state.status != TaskStatus.Completed,
                    label = { Text("New subtask") },
                    singleLine = true,
                )
                TextButton(
                    onClick = onAdd,
                    enabled = newSubtask.isNotBlank() && !state.saving && state.status != TaskStatus.Completed,
                ) { Text("Add") }
            }
        }
    }
}

@Composable
private fun <T> SelectionMenu(
    label: String,
    selectedLabel: String,
    values: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant)
        TextButton(onClick = { expanded = true }) { Text(selectedLabel) }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { (name, value) ->
                DropdownMenuItem({ Text(name) }, { expanded = false; onSelect(value) })
            }
        }
    }
}

@Composable
fun ProjectEditorScreen(
    state: ProjectEditorUiState,
    deleteSummary: ProjectDeleteSummary?,
    deleteSummaryLoading: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDeadlineChange: (String) -> Unit,
    onDeadlineTimeChange: (String) -> Unit,
    onDeadlineModeChange: (ProjectDeadlineMode) -> Unit,
    onSave: () -> Unit,
    onPrepareDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onSaved: () -> Unit,
) {
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    var confirmDiscard by remember { mutableStateOf(false) }
    fun requestBack() { if (state.dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = ::requestBack)
    when {
        state.loading -> LoadingState()
        state.error != null -> ErrorState(state.error, onRetry)
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextButton(onClick = ::requestBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                Spacer(Modifier.width(5.dp))
                Text("Battle Plan")
            }
            Text(
                if (state.projectId == null) "Create project" else "Edit project",
                style = TimeboxTheme.type.screenTitle,
            )
            OutlinedTextField(state.name, onNameChange, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(
                state.description,
                onDescriptionChange,
                Modifier.fillMaxWidth(),
                label = { Text("Description") },
                minLines = 3,
            )
            SelectionMenu(
                "Deadline",
                when (state.deadlineMode) {
                    ProjectDeadlineMode.None -> "No deadline"
                    ProjectDeadlineMode.DateOnly -> "Date only"
                    ProjectDeadlineMode.DateTime -> "Date and time"
                },
                listOf(
                    "No deadline" to ProjectDeadlineMode.None,
                    "Date only" to ProjectDeadlineMode.DateOnly,
                    "Date and time" to ProjectDeadlineMode.DateTime,
                ),
                onDeadlineModeChange,
            )
            if (state.deadlineMode != ProjectDeadlineMode.None) {
                OutlinedTextField(
                    state.deadlineDate,
                    onDeadlineChange,
                    Modifier.fillMaxWidth(),
                    label = { Text("Deadline date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                )
            }
            if (state.deadlineMode == ProjectDeadlineMode.DateTime) {
                OutlinedTextField(
                    state.deadlineTime,
                    onDeadlineTimeChange,
                    Modifier.fillMaxWidth(),
                    label = { Text("Deadline time (${state.timezone})") },
                    placeholder = { Text("HH:MM") },
                    singleLine = true,
                )
            }
            PrimaryButton("Save project", onSave, Modifier.fillMaxWidth(), enabled = state.name.isNotBlank() && !state.saving)
            if (state.projectId != null) {
                TextButton(onClick = onPrepareDelete, enabled = !deleteSummaryLoading) {
                    Icon(Icons.Outlined.Delete, null)
                    Spacer(Modifier.width(5.dp))
                    Text(if (deleteSummaryLoading) "Counting affected tasks…" else "Delete project")
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard unsaved changes?") },
            confirmButton = { TextButton(onClick = onBack) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
    deleteSummary?.let { ProjectDeleteDialog(it, onDismissDelete, onConfirmDelete) }
}
