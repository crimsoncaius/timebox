package com.timebox.android.ui.battleplan

import androidx.compose.runtime.Immutable
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Project
import com.timebox.android.data.TaskCollection
import com.timebox.android.data.TaskStatus

/**
 * What Battle Plan can be asked to do, grouped the way the screen already splits its body.
 *
 * Every callback defaults to a no-op so a caller states only the ones it cares about; the
 * screen renders the same either way, since an unstated action is one this caller cannot
 * reach. [Immutable] tells Compose these bundles never change identity-wise after
 * construction, so passing one does not by itself invalidate a recomposition scope.
 */

/** Choosing what the board shows: collection, scope, status column and the filter sheet. */
@Immutable
internal data class BattlePlanFilterActions(
    val selectCollection: (TaskCollection) -> Unit = {},
    val selectScope: (BattlePlanScope) -> Unit = {},
    val selectStatus: (TaskStatus) -> Unit = {},
    val toggleUrgency: (String) -> Unit = {},
    val toggleImportance: (String) -> Unit = {},
    val toggleTaskType: (String) -> Unit = {},
    val clearFilters: () -> Unit = {},
    val setHideCompleted: (Boolean) -> Unit = {},
)

/** Acting on a Task already on the board, including the drag and drop that moves one. */
@Immutable
internal data class BattlePlanTaskActions(
    val open: (Int) -> Unit = {},
    val toggleReady: (BattleTask) -> Unit = {},
    val move: (BattleTask, TaskStatus) -> Unit = { _, _ -> },
    val moveToBoundary: (BattleTask, Boolean) -> Unit = { _, _ -> },
    val drop: (BattleTask, TaskStatus, Int) -> Unit = { _, _, _ -> },
    val setBlocked: (BattleTask, Boolean, String?) -> Unit = { _, _, _ -> },
    val archiveCompleted: () -> Unit = {},
)

/** The Project list and its editor and delete dialogs. */
@Immutable
internal data class BattlePlanProjectActions(
    val reorder: (List<Int>) -> Unit = {},
    val moveTaskTo: (BattleTask, Int?) -> Unit = { _, _ -> },
    val create: () -> Unit = {},
    val edit: (Project) -> Unit = {},
    val changeName: (String) -> Unit = {},
    val save: () -> Unit = {},
    val cancelEditor: () -> Unit = {},
    val dismissEditor: () -> Unit = {},
    val prepareDelete: (Project) -> Unit = {},
    val dismissDelete: () -> Unit = {},
    val confirmDelete: () -> Unit = {},
)

/** The new-Task composer overlay, including the notification permission it may need. */
@Immutable
internal data class BattlePlanComposerActions(
    val show: (Boolean) -> Unit = {},
    val create: (String, String, Int?) -> Unit = { _, _, _ -> },
    val changeDraft: (TaskComposerDraft) -> Unit = {},
    val changeReminderEnabled: (Boolean) -> Unit = {},
    val createTaskType: (String) -> Unit = {},
    val notificationsAllowed: Boolean = true,
    val requestNotificationPermission: () -> Unit = {},
)

/** Taking a Task off the board and putting it back: Trash, Archive and permanent delete. */
@Immutable
internal data class BattlePlanRemovalActions(
    val requestTrash: (BattleTask) -> Unit = {},
    val dismissTrash: () -> Unit = {},
    val confirmTrash: () -> Unit = {},
    val restoreArchived: (BattleTask) -> Unit = {},
    val restoreTrashed: (BattleTask) -> Unit = {},
    val requestPermanentDelete: (BattleTask) -> Unit = {},
    val dismissPermanentDelete: () -> Unit = {},
    val confirmPermanentDelete: () -> Unit = {},
)
