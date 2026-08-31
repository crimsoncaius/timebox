package com.timebox.android.ui.visual

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.timebox.android.data.BattleTask
import com.timebox.android.data.Day
import com.timebox.android.data.PriorityLevel
import com.timebox.android.data.Subtask
import com.timebox.android.data.TaskStatus
import com.timebox.android.data.TaskType
import com.timebox.android.ui.battleplan.BattlePlanScreen
import com.timebox.android.ui.battleplan.BattlePlanUiState
import com.timebox.android.ui.battleplan.TaskDetailScreen
import com.timebox.android.ui.battleplan.TaskDetailUiState
import com.timebox.android.ui.battleplan.RecurringEditorScreen
import com.timebox.android.ui.battleplan.RecurringEditorUiState
import com.timebox.android.ui.chronicle.ChronicleScreen
import com.timebox.android.ui.chronicle.ChronicleUiState
import com.timebox.android.ui.day.DayPageState
import com.timebox.android.ui.day.DayScreen
import com.timebox.android.ui.day.DayUiState
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.theme.DarkTimeboxColors
import com.timebox.android.ui.theme.ThemePreviewScreen
import com.timebox.android.ui.types.TypeGroup
import com.timebox.android.ui.types.TypesScreen
import com.timebox.android.ui.types.TypesUiState
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Regenerates the dark-theme evidence set used for visual review.
 *
 * Run `scripts/android-dark-theme-screenshots.ps1` from the repository root to
 * execute this class and pull the PNGs into `artifacts/android-dark-theme`.
 */
class DarkThemeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun darkThemeProvidesReadableDefaultContentColor() {
        var providedColor = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            TimeboxTheme(darkTheme = true) {
                providedColor = LocalContentColor.current
            }
        }

        compose.runOnIdle { assertEquals(DarkTimeboxColors.on, providedColor) }
    }

    @Test
    fun taskDetailsCoverTopAndLowerContent() {
        val task = battleTask(
            id = 1,
            plannedDates = (1..7).map { LocalDate.of(2026, 9, it) },
            subtasks = listOf(
                Subtask(11, 1, "Check the dark card hierarchy", false, false, 0, Instant.EPOCH, Instant.EPOCH),
            ),
        )
        compose.setContent {
            DarkFrame {
                TaskDetailScreen(
                    state = TaskDetailUiState(
                        taskId = task.id,
                        loading = false,
                        task = task,
                        timezone = "Asia/Singapore",
                        serverNow = Instant.parse("2026-08-31T12:00:00Z"),
                        title = task.title,
                        description = "A deterministic visual-regression fixture.",
                        status = task.status,
                        readyToPlan = true,
                    ),
                    onBack = {}, onRetry = {}, onOpenTask = {}, onTitleChange = {},
                    onDescriptionChange = {}, onStatusChange = {}, onProjectChange = {},
                    onTaskTypeChange = {}, onUrgencyChange = {}, onImportanceChange = {},
                    onDeadlineModeChange = {}, onDeadlineDateChange = {}, onDeadlineTimeChange = {},
                    onReminderEnabledChange = {}, notificationsAllowed = true,
                    onReminderDateChange = {}, onReminderTimeChange = {}, onReadyChange = {},
                    onOpenDay = { _, _ -> }, onAddSubtask = {}, onToggleSubtask = {},
                    onTrashSubtask = {}, onDismissSubtaskTrash = {}, onConfirmSubtaskTrash = {},
                    onUndoSubtaskTrash = {}, onRequestTrash = {}, onDismissTrash = {},
                    onConfirmTrash = {}, onTrashed = {}, onReopen = {}, onSave = {},
                )
            }
        }

        saveScreenshot("dark-task-details-top")
        compose.onNodeWithText("Ready to Plan").performScrollTo()
        saveScreenshot("dark-task-details-lower")
    }

    @Test
    fun battlePlanScopeMenuUsesMappedMaterialColors() {
        compose.setContent {
            DarkFrame {
                BattlePlanScreen(
                    state = BattlePlanUiState(loading = false, tasks = listOf(battleTask(1))),
                    onRetry = {}, onSelectScope = {}, onSelectStatus = {},
                    onToggleUrgency = {}, onToggleImportance = {}, onToggleTaskType = {},
                    onClearFilters = {}, onOpenTask = {}, onToggleReady = {},
                    onMoveTask = { _, _ -> }, onReorderTask = { _, _ -> },
                    onCreateSubtask = { _, _ -> }, onToggleSubtask = {},
                    onCreateTask = { _, _, _ -> }, onShowComposer = {},
                    onOpenRecurring = {}, onNewProject = {}, onPrepareDeleteProject = {},
                    onDismissDeleteProject = {}, onConfirmDeleteProject = {},
                    onRestoreArchived = {}, onRestoreTrashed = {}, onUndoTrash = {},
                    onDismissUndo = {}, onRequestPermanentDelete = {},
                    onDismissPermanentDelete = {}, onConfirmPermanentDelete = {},
                )
            }
        }

        compose.onNodeWithText("All Tasks", substring = true).performClick()
        saveScreenshot("dark-battle-plan-scope-menu")
    }

    @Test
    fun destructiveDialogUsesDarkErrorContainerSystem() {
        val focus = TaskType(
            id = 7,
            name = "work/focus",
            usageCount = 3,
            taskUsageCount = 1,
            recurringTemplateUsageCount = 1,
        )
        val admin = TaskType(id = 8, name = "admin", usageCount = 0)
        compose.setContent {
            DarkFrame {
                TypesScreen(
                    state = TypesUiState(
                        groups = listOf(TypeGroup("work", listOf(focus)), TypeGroup("admin", listOf(admin))),
                        loading = false,
                        pendingCascade = focus,
                    ),
                    onInputChange = {}, onAdd = {}, onDelete = {}, onConfirmCascade = {},
                    onMigrateTarget = {}, onConfirmMigrate = {}, onDismissCascade = {}, onRetry = {},
                )
            }
        }

        saveScreenshot("dark-types-delete-dialog")
    }

    @Test
    fun dayMonthCalendarCoversAdjacentDates() {
        val date = LocalDate.of(2026, 8, 31)
        compose.setContent {
            DarkFrame {
                DayScreen(
                    state = DayUiState(
                        date = date,
                        today = date,
                        pages = mapOf(
                            date to DayPageState(
                                day = Day(
                                    date = date,
                                    startHour = 7,
                                    endHour = 22,
                                    showFullDay = false,
                                    blocks = emptyList(),
                                    timezone = "Asia/Singapore",
                                    today = date,
                                    serverNowMinute = 12 * 60,
                                ),
                                loading = false,
                                materialized = true,
                            ),
                        ),
                    ),
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {},
                    onConfirmSelectedTaskCompletion = {}, onReopenSelectedTask = {},
                    onOpenLinkedTask = {}, onSetPlanningMode = {}, onPlanTask = { _, _ -> },
                    onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithText("Month").performClick()
        saveScreenshot("dark-day-month-calendar")
    }

    @Test
    fun chronicleCoversAdjacentDates() {
        compose.setContent {
            DarkFrame {
                ChronicleScreen(
                    state = ChronicleUiState(
                        monthStart = LocalDate.of(2026, 8, 1),
                        today = LocalDate.of(2026, 8, 31),
                        loading = false,
                    ),
                    onPrevMonth = {}, onNextMonth = {}, onThisMonth = {},
                    onOpenDay = {}, onRetry = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Chronicle August 2026").assertIsDisplayed()
        saveComposeScreenshot("dark-chronicle-calendar")
    }

    @Test
    fun recurringEditorUsesGroupedSectionsAndQuietSelections() {
        compose.setContent {
            DarkFrame {
                RecurringEditorScreen(
                    state = RecurringEditorUiState(
                        title = "Weekly product review",
                        description = "A deterministic grouped-editor fixture.",
                        startDate = "2026-08-31",
                        checklistText = "Review outcomes\nChoose next focus",
                    ),
                    onBack = {}, onRetry = {}, onTitle = {}, onDescription = {},
                    onProject = {}, onTaskType = {}, onUrgency = {}, onImportance = {},
                    onMode = {}, onFrequency = {}, onInterval = {}, onToggleWeekday = {},
                    onMonthDay = {}, onQuotaCount = {}, onStartDate = {}, onEndMode = {},
                    onEndDate = {}, onCycleLimit = {}, onChecklist = {}, onRefreshPreview = {},
                    onSave = {}, onConfirmBackfill = {}, onDismissBackfill = {},
                )
            }
        }

        saveScreenshot("dark-recurring-editor-grouped-top")
        compose.onNodeWithText("Server preview").performScrollTo()
        saveScreenshot("dark-recurring-editor-grouped-lower")
    }

    @Test
    fun emptyPlanningQueueCollapsesIntoGuidanceCard() {
        val date = LocalDate.of(2026, 8, 31)
        compose.setContent {
            DarkFrame {
                DayScreen(
                    state = DayUiState(
                        date = date,
                        today = date,
                        isPlanningMode = true,
                        pages = mapOf(date to DayPageState(
                            day = Day(
                                date = date,
                                startHour = 7,
                                endHour = 22,
                                showFullDay = false,
                                blocks = emptyList(),
                                timezone = "Asia/Singapore",
                                today = date,
                                serverNowMinute = 12 * 60,
                            ),
                            loading = false,
                            materialized = true,
                        )),
                    ),
                    onDateSettled = {}, onRetry = {}, onTapSlot = { _, _ -> },
                    onSelectBlock = {}, onCommitMove = { _, _, _ -> }, onDismissSheet = {},
                    onChooseType = {}, onTypeQueryChange = {}, onCreateType = {},
                    onNoteChange = {}, onDeleteSelected = {}, onConfirmSelectedTaskCompletion = {},
                    onReopenSelectedTask = {}, onOpenLinkedTask = {}, onSetPlanningMode = {},
                    onPlanTask = { _, _ -> }, onArmAccessibleTask = {}, onRetryReadyTasks = {},
                )
            }
        }

        compose.onNodeWithText("Your planning queue is clear").assertIsDisplayed()
        saveScreenshot("dark-plan-mode-empty-collapsed")
    }

    @Test
    fun darkThemePreviewCoversProductTokens() {
        compose.setContent { DarkFrame { ThemePreviewScreen(onBack = {}) } }
        saveScreenshot("dark-theme-preview")
    }

    @Test
    fun lightThemePreviewCoversProductTokens() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize(), color = com.timebox.android.ui.theme.TimeboxTheme.colors.bg) {
                    ThemePreviewScreen(onBack = {})
                }
            }
        }
        saveScreenshot("light-theme-preview")
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        writeScreenshot(
            name,
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
        )
    }

    private fun saveComposeScreenshot(name: String) {
        compose.waitForIdle()
        writeScreenshot(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun writeScreenshot(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "visual-regression")
        assertTrue(directory.exists() || directory.mkdirs())
        val destination = File(directory, "$name.png")
        FileOutputStream(destination).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        assertTrue("Expected a non-empty screenshot at $destination", destination.length() > 1_000)
    }
}

@Composable
private fun DarkFrame(content: @Composable () -> Unit) {
    TimeboxTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = com.timebox.android.ui.theme.TimeboxTheme.colors.bg,
            contentColor = com.timebox.android.ui.theme.TimeboxTheme.colors.on,
            content = content,
        )
    }
}

private fun battleTask(
    id: Int,
    plannedDates: List<LocalDate> = emptyList(),
    subtasks: List<Subtask> = emptyList(),
) = BattleTask(
    id = id,
    parentId = null,
    parentTitle = null,
    projectId = null,
    project = null,
    taskTypeId = null,
    taskType = null,
    recurringTemplateId = null,
    recurringTemplateTitle = null,
    occurrenceKey = null,
    recurrenceKind = null,
    quotaPeriodStart = null,
    quotaPeriodEnd = null,
    expectedSessions = null,
    sessionIndex = null,
    quotaCompleted = null,
    title = "Visual QA task $id",
    description = "",
    readyToPlan = true,
    status = TaskStatus.Open,
    urgency = PriorityLevel.Medium,
    importance = PriorityLevel.High,
    deadlineDate = null,
    deadlineAt = null,
    reminderAt = null,
    reminderDeliveredAt = null,
    position = id - 1,
    archivedAt = null,
    deletedAt = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    overdue = false,
    subtasks = subtasks,
    plannedDates = plannedDates,
)
