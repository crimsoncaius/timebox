package com.timebox.android.ui.battleplan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
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
import com.timebox.android.ui.theme.TimeboxColors
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
    onToggleSubtask: (BattleTask) -> Unit,
    onCreateTask: (String, String, Int?) -> Unit,
    onShowComposer: (Boolean) -> Unit,
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
        TaskComposerDialog(
            scope = state.selectedScope,
            projects = state.projects,
            saving = state.saving,
            onDismiss = { onShowComposer(false) },
            onCreate = onCreateTask,
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
                    modifier = Modifier.width(280.dp),
                ) {
                    MenuSectionLabel("Tasks")
                    state.scopes.filter { it.kind != BattlePlanScopeKind.Project }.forEach { scope ->
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
                    }
                    MenuSectionLabel("Projects")
                    state.scopes.filter { it.kind == BattlePlanScopeKind.Project }.forEach { scope ->
                        ScopeMenuItem(
                            label = scope.label,
                            icon = Icons.Outlined.Folder,
                            selected = scope.preferenceKey == state.selectedScope.preferenceKey,
                            onClick = { scopeMenu = false; onSelectScope(scope) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("New project", style = TimeboxTheme.type.label) },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        onClick = { scopeMenu = false; onNewProject() },
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = colors.hairline)
                    MenuSectionLabel("Library")
                    ScopeMenuItem("Recurring", Icons.Outlined.Repeat) { scopeMenu = false; onOpenRecurring() }
                    ScopeMenuItem("Archive", Icons.Outlined.Archive) { scopeMenu = false; onSelectCollection(TaskCollection.Archived) }
                    ScopeMenuItem("Trash", Icons.Outlined.Delete, destructive = true) {
                        scopeMenu = false
                        onSelectCollection(TaskCollection.Trash)
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
                leading = { Icon(Icons.Outlined.Add, null, tint = colors.bg, modifier = Modifier.size(18.dp)) },
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
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 4.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(icon, contentDescription = null, tint = contentColor) },
        trailingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = colors.on) }
        } else null,
        onClick = onClick,
        modifier = Modifier.background(if (selected) colors.surf else colors.lowest),
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
                .clickable { onOpen(task.id) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(task.title, style = TimeboxTheme.type.label, color = colors.on, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onToggleReady(task) }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (task.readyToPlan) Icons.Outlined.CheckCircle else Icons.Outlined.EventAvailable,
                        contentDescription = if (task.readyToPlan) "Remove ${task.title} from Ready to Plan" else "Add ${task.title} to Ready to Plan",
                        tint = if (task.readyToPlan) colors.planned else colors.onVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Actions for ${task.title}", tint = colors.onVariant)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        if (index > 0) DropdownMenuItem({ Text("Move earlier") }, { menu = false; onDrop(task, task.status, index - 1) })
                        if (index < laneSize - 1) DropdownMenuItem({ Text("Move later") }, { menu = false; onDrop(task, task.status, index + 1) })
                        battlePlanStatuses.filter { it != task.status }.forEach { target ->
                            DropdownMenuItem({ Text("Move to ${target.label}") }, { menu = false; onDrop(task, target, taskCount(target)) })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.project?.name ?: "Admin", style = TimeboxTheme.type.bodySmall, color = colors.onVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = {
                    if (task.isBlocked) onSetBlocked(task, false, null) else blockDialog = true
                }) {
                    Text(if (task.isBlocked) "Blocked" else "Block", color = if (task.isBlocked) colors.error else colors.onVariant)
                }
            }
            task.blockingReason?.takeIf { task.isBlocked }?.let {
                Text(it, style = TimeboxTheme.type.bodySmall, color = colors.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            plannedDateSummary(task.plannedDates, serverNow, timezone)?.let { summary ->
                Spacer(Modifier.height(3.dp))
                PlannedDatePill(summary)
            }
            val metadata = buildList {
                task.deadlineDate?.let { add("Due $it") }
                task.urgency?.let { add("U ${it.wire}") }
                task.importance?.let { add("I ${it.wire}") }
                if (task.subtasks.isNotEmpty()) add("${task.subtasks.count { it.status == TaskStatus.Completed }}/${task.subtasks.size} subtasks")
            }
            if (metadata.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
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

internal fun mobileTaskCardSurface(colors: TimeboxColors): Color =
    if (colors.isDark) colors.low else colors.lowest

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
            .semantics { mobilePickupProgress = pickupProgress }
            .graphicsLayer {
                alpha = 1f - (0.25f * liftProgress)
                scaleX = 1f + (0.02f * liftProgress)
                scaleY = 1f + (0.02f * liftProgress)
                shadowElevation = 14f * liftProgress
                shape = TimeboxShapes.card
            }
            .clip(TimeboxShapes.card)
            .background(colors.lowest)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                drag.task.title,
                style = TimeboxTheme.type.label,
                color = colors.on,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                if (drag.task.readyToPlan) Icons.Outlined.CheckCircle else Icons.Outlined.EventAvailable,
                contentDescription = null,
                tint = if (drag.task.readyToPlan) colors.planned else colors.onVariant,
                modifier = Modifier.padding(7.dp).size(24.dp),
            )
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = null,
                tint = colors.onVariant,
                modifier = Modifier.padding(7.dp).size(24.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                drag.task.project?.name ?: "Admin",
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (drag.task.isBlocked) "Blocked" else "Block",
                style = TimeboxTheme.type.label,
                color = if (drag.task.isBlocked) colors.error else colors.onVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
        drag.task.blockingReason?.takeIf { drag.task.isBlocked }?.let {
            Text(
                it,
                style = TimeboxTheme.type.bodySmall,
                color = colors.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        plannedDateSummary(drag.task.plannedDates, serverNow, timezone)?.let { summary ->
            Spacer(Modifier.height(3.dp))
            PlannedDatePill(summary)
        }
        val metadata = buildList {
            drag.task.deadlineDate?.let { add("Due $it") }
            drag.task.urgency?.let { add("U ${it.wire}") }
            drag.task.importance?.let { add("I ${it.wire}") }
            if (drag.task.subtasks.isNotEmpty()) {
                add("${drag.task.subtasks.count { it.status == TaskStatus.Completed }}/${drag.task.subtasks.size} subtasks")
            }
        }
        if (metadata.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
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
    onToggleSubtask: (BattleTask) -> Unit,
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
    onToggleSubtask: (BattleTask) -> Unit,
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
    onToggleSubtask: (BattleTask) -> Unit,
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
                    add("${task.subtasks.count { it.status == TaskStatus.Completed }}/${task.subtasks.size} subtasks")
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
        IconButton(onClick = { onToggleReady(task) }) {
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
                if (manualOrder) {
                    DropdownMenuItem(text = { Text("Move earlier") }, onClick = { menu = false; onReorder(task, -1) })
                    DropdownMenuItem(text = { Text("Move later") }, onClick = { menu = false; onReorder(task, 1) })
                }
                battlePlanStatuses.filter { it != task.status }.forEach { target ->
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
                    Column(Modifier.fillMaxWidth().clickable { onOpen(child.id) }.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (child.status == TaskStatus.Completed) "✓ ${child.title}" else child.title, modifier = Modifier.weight(1f), style = TimeboxTheme.type.bodySmall)
                            TextButton(onClick = { onToggleSubtask(child) }) { Text(if (child.status == TaskStatus.Completed) "Reopen" else "Complete") }
                        }
                        plannedDateSummary(child.plannedDates, serverNow, timezone)?.let { summary ->
                            PlannedDatePill(summary)
                        }
                        child.deadlineDate?.let { Text("Due $it", style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
                        child.deadlineAt?.let { Text("Due $it", style = TimeboxTheme.type.bodySmall, color = colors.onVariant) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newSubtask, { newSubtask = it }, Modifier.weight(1f), label = { Text("New subtask") }, singleLine = true)
                    TextButton(enabled = newSubtask.isNotBlank(), onClick = { onCreateSubtask(task, newSubtask); newSubtask = "" }) { Text("Add") }
                }
            }
        }
    } else {
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
private fun TaskComposerDialog(
    scope: BattlePlanScope,
    projects: List<Project>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var projectId by remember(scope.preferenceKey) {
        mutableStateOf(if (scope.kind == BattlePlanScopeKind.Project) scope.projectId else null)
    }
    var projectMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ${scope.label} task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2)
                if (scope.kind == BattlePlanScopeKind.All) {
                    Box {
                        TextButton(onClick = { projectMenu = true }) {
                            Text(projects.firstOrNull { it.id == projectId }?.name ?: "Admin")
                        }
                        DropdownMenu(projectMenu, { projectMenu = false }) {
                            DropdownMenuItem({ Text("Admin") }, { projectId = null; projectMenu = false })
                            projects.forEach { project ->
                                DropdownMenuItem({ Text(project.name) }, { projectId = project.id; projectMenu = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && !saving, onClick = { onCreate(title, description, projectId) }) {
                Text(if (saving) "Creating…" else "Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
    onOpenDay: (java.time.LocalDate) -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (BattleTask) -> Unit,
    onTrashSubtask: (BattleTask) -> Unit,
    onDismissSubtaskTrash: () -> Unit,
    onConfirmSubtaskTrash: () -> Unit,
    onUndoSubtaskTrash: () -> Unit,
    onRequestTrash: () -> Unit,
    onDismissTrash: () -> Unit,
    onConfirmTrash: () -> Unit,
    onTrashed: () -> Unit,
    onSave: () -> Unit,
) {
    LaunchedEffect(state.trashed) { if (state.trashed) onTrashed() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var newSubtask by remember { mutableStateOf("") }
    fun requestBack() { if (state.dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = ::requestBack)
    when {
        state.loading -> LoadingState()
        state.error != null -> ErrorState(state.error, onRetry)
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 840.dp
            if (expanded && !state.isSubtask) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TaskEditForm(state, Modifier.weight(1.5f), ::requestBack, onTitleChange, onDescriptionChange, onStatusChange, onProjectChange, onTaskTypeChange, onUrgencyChange, onImportanceChange, onDeadlineModeChange, onDeadlineDateChange, onDeadlineTimeChange, onReminderEnabledChange, notificationsAllowed, onReminderDateChange, onReminderTimeChange, onReadyChange, onOpenDay, onRequestTrash, onSave)
                    SubtaskPanel(state, Modifier.weight(1f), newSubtask, { newSubtask = it }, { onAddSubtask(newSubtask); newSubtask = "" }, onOpenTask, onToggleSubtask, onTrashSubtask)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TaskEditForm(state, Modifier.fillMaxWidth(), ::requestBack, onTitleChange, onDescriptionChange, onStatusChange, onProjectChange, onTaskTypeChange, onUrgencyChange, onImportanceChange, onDeadlineModeChange, onDeadlineDateChange, onDeadlineTimeChange, onReminderEnabledChange, notificationsAllowed, onReminderDateChange, onReminderTimeChange, onReadyChange, onOpenDay, onRequestTrash, onSave)
                    if (!state.isSubtask) SubtaskPanel(state, Modifier.fillMaxWidth(), newSubtask, { newSubtask = it }, { onAddSubtask(newSubtask); newSubtask = "" }, onOpenTask, onToggleSubtask, onTrashSubtask)
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
    onOpenDay: (java.time.LocalDate) -> Unit,
    onTrash: () -> Unit,
    onSave: () -> Unit,
) {
    var showAllPlannedDates by remember(state.taskId) { mutableStateOf(false) }
    val zone = runCatching { java.time.ZoneId.of(state.timezone) }.getOrDefault(java.time.ZoneId.of("UTC"))
    val today = state.serverNow.atZone(zone).toLocalDate()
    val plannedDates = orderedPlannedDates(state.task?.plannedDates.orEmpty(), today)
    val visiblePlannedDates = if (showAllPlannedDates) plannedDates else plannedDates.take(5)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Spacer(Modifier.width(5.dp)); Text("Battle Plan") }
        state.parentTask?.let { Text("Subtask of ${it.title}", style = TimeboxTheme.type.bodySmall, color = TimeboxTheme.colors.onVariant) }
        OutlinedTextField(state.title, onTitle, Modifier.fillMaxWidth(), label = { Text("Title") })
        OutlinedTextField(state.description, onDescription, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3)
        SelectionMenu("Status", state.status.label, battlePlanStatuses.map { it.label to it }, onStatus)
        if (!state.isSubtask) SelectionMenu("Project", state.projects.firstOrNull { it.id == state.projectId }?.name ?: "Admin", listOf("Admin" to null) + state.projects.map { it.name to it.id }, onProject)
        else Text("Project: ${state.projects.firstOrNull { it.id == state.projectId }?.name ?: "Admin"} (inherited)", style = TimeboxTheme.type.bodySmall)
        SelectionMenu("Task type", state.taskTypes.firstOrNull { it.id == state.taskTypeId }?.name ?: "Unset", listOf("Unset" to null) + state.taskTypes.map { it.name to it.id }, onTaskType)
        SelectionMenu("Urgency", state.urgency?.wire?.replaceFirstChar(Char::uppercase) ?: "Unset", listOf("Unset" to null) + PriorityLevel.entries.map { it.wire.replaceFirstChar(Char::uppercase) to it }, onUrgency)
        SelectionMenu("Importance", state.importance?.wire?.replaceFirstChar(Char::uppercase) ?: "Unset", listOf("Unset" to null) + PriorityLevel.entries.map { it.wire.replaceFirstChar(Char::uppercase) to it }, onImportance)
        SelectionMenu("Deadline", when (state.deadlineMode) { TaskDeadlineMode.None -> "None"; TaskDeadlineMode.DateOnly -> "Date only"; TaskDeadlineMode.DateTime -> "Date and time" }, listOf("None" to TaskDeadlineMode.None, "Date only" to TaskDeadlineMode.DateOnly, "Date and time" to TaskDeadlineMode.DateTime), onDeadlineMode)
        if (state.deadlineMode != TaskDeadlineMode.None) OutlinedTextField(state.deadlineDate, onDeadlineDate, Modifier.fillMaxWidth(), label = { Text("Deadline date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
        if (state.deadlineMode == TaskDeadlineMode.DateTime) OutlinedTextField(state.deadlineTime, onDeadlineTime, Modifier.fillMaxWidth(), label = { Text("Deadline time (${state.timezone})") }, placeholder = { Text("HH:MM") }, singleLine = true)
        if (state.deadlineMode != TaskDeadlineMode.None) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Reminder", modifier = Modifier.weight(1f)); Switch(state.reminderEnabled, onReminderEnabled) }
            if (state.reminderEnabled) {
                if (!notificationsAllowed) {
                    Text(
                        "This reminder will be saved, but this device cannot display it until notifications are enabled in Settings.",
                        style = TimeboxTheme.type.bodySmall,
                        color = TimeboxTheme.colors.error,
                    )
                }
                OutlinedTextField(state.reminderDate, onReminderDate, Modifier.fillMaxWidth(), label = { Text("Reminder date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
                OutlinedTextField(state.reminderTime, onReminderTime, Modifier.fillMaxWidth(), label = { Text("Reminder time (${state.timezone})") }, placeholder = { Text("HH:MM") }, singleLine = true)
            }
        }
        if (plannedDates.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(TimeboxTheme.colors.low).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Planned Dates", style = TimeboxTheme.type.label)
                visiblePlannedDates.forEach { date ->
                    Text(
                        text = formatPlannedDetailDate(date, today),
                        style = TimeboxTheme.type.bodySmall,
                        color = TimeboxTheme.colors.onVariant,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenDay(date) }.padding(horizontal = 8.dp, vertical = 7.dp),
                    )
                }
                if (plannedDates.size > 5) {
                    TextButton(onClick = { showAllPlannedDates = !showAllPlannedDates }) {
                        Text(if (showAllPlannedDates) "Show less" else "Show all (${plannedDates.size})")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(TimeboxShapes.card).background(TimeboxTheme.colors.low).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Ready to Plan", style = TimeboxTheme.type.label); Text("Offer this task when creating a Planned block.", style = TimeboxTheme.type.bodySmall) }
            Switch(state.readyToPlan, onReady)
        }
        PrimaryButton("Save task", onSave, Modifier.fillMaxWidth(), enabled = state.dirty && !state.saving)
        TextButton(onClick = onTrash, enabled = !state.saving) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(5.dp)); Text("Move to Trash", color = TimeboxTheme.colors.error) }
    }
}

@Composable
private fun SubtaskPanel(
    state: TaskDetailUiState,
    modifier: Modifier,
    newSubtask: String,
    onNewSubtask: (String) -> Unit,
    onAdd: () -> Unit,
    onOpen: (Int) -> Unit,
    onToggle: (BattleTask) -> Unit,
    onTrash: (BattleTask) -> Unit,
) {
    Column(modifier.clip(TimeboxShapes.card).background(TimeboxTheme.colors.low).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Subtasks ${state.subtasks.count { it.status == TaskStatus.Completed }}/${state.subtasks.size}", style = TimeboxTheme.type.sectionTitle)
        state.subtasks.forEach { task ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(task.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (task.status == TaskStatus.Completed) "✓ ${task.title}" else task.title, modifier = Modifier.weight(1f), style = TimeboxTheme.type.bodySmall)
                TextButton(onClick = { onToggle(task) }) { Text(if (task.status == TaskStatus.Completed) "Reopen" else "Complete") }
                IconButton(onClick = { onTrash(task) }) { Icon(Icons.Outlined.Delete, "Move ${task.title} to Trash") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newSubtask, onNewSubtask, Modifier.weight(1f), label = { Text("New subtask") }, singleLine = true)
            TextButton(onClick = onAdd, enabled = newSubtask.isNotBlank() && !state.saving) { Text("Add") }
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
