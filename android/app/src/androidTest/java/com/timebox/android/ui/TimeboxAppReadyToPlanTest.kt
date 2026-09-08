package com.timebox.android.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.BattleTaskDto
import com.timebox.android.data.remote.BattleTaskListDto
import com.timebox.android.data.remote.DayDto
import com.timebox.android.data.remote.DayMetaDto
import com.timebox.android.data.remote.DayPreviewDto
import com.timebox.android.data.remote.DaySummaryDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import org.junit.Rule
import org.junit.Test

class TimeboxAppReadyToPlanTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pendingReadyToPlanChoiceSurvivesNavigationAndSettlesWithoutFlicker() {
        val transport = ControllableTimeboxApi()
        val repository = TimeboxRepository(transport.proxy())
        compose.setContent {
            TimeboxApp(
                isDark = false,
                onToggleDark = {},
                notificationsAllowed = true,
                onRequestNotificationPermission = {},
                onOpenNotificationSettings = {},
                repository = repository,
                taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository)),
                imeVisibleOverride = false,
            )
        }

        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Battle Plan").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Battle Plan").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        compose.onNodeWithContentDescription("Saving Ready to Plan for App projection Task")
            .assertIsEnabled()

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithTag("planning-mode-action").performClick()
        compose.onNodeWithContentDescription("App projection Task is saving and unavailable")
            .assertIsNotEnabled()

        compose.onNodeWithText("Battle Plan").performClick()
        compose.onNodeWithContentDescription("Saving Ready to Plan for App projection Task")
            .assertIsEnabled()

        compose.runOnIdle { transport.completeReadiness(ready = true, version = 2) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Remove App projection Task from Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithContentDescription("Schedule App projection Task").assertIsEnabled()
    }

    @Test
    fun pendingRemovalClearsDaySelectionAndUnsubmittedDraftAcrossNavigation() {
        val transport = ControllableTimeboxApi(initialReady = true)
        setAppContent(transport)

        enterPlanningMode()
        compose.onNodeWithContentDescription("Schedule App projection Task")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("App projection Task selected", useUnmergedTree = true).assertExists()

        compose.onNodeWithText("Battle Plan").performClick()
        compose.onNodeWithContentDescription("Remove App projection Task from Ready to Plan").performClick()
        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("App projection Task selected", useUnmergedTree = true).assertDoesNotExist()

        compose.runOnIdle { transport.completeReadiness(ready = false, version = 2) }
        compose.onNodeWithText("Battle Plan").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        compose.runOnIdle { transport.completeReadiness(ready = true, version = 3) }

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithContentDescription("Schedule App projection Task")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("day-lane-planned").performTouchInput { click(center) }
        compose.onNodeWithContentDescription("Planning draft App projection Task").assertExists()

        compose.onNodeWithText("Battle Plan").performClick()
        compose.onNodeWithContentDescription("Remove App projection Task from Ready to Plan").performClick()
        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithContentDescription("Planning draft App projection Task").assertDoesNotExist()
    }

    @Test
    fun serverLifecycleRejectionIsAuthoritativeAcrossAppNavigation() {
        val transport = ControllableTimeboxApi()
        setAppContent(transport)

        compose.onNodeWithText("Battle Plan").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        compose.runOnIdle {
            transport.completeReadiness(ready = false, version = 2, status = "completed")
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").assertDoesNotExist()
        compose.onNodeWithText("Completed").performClick()
        compose.onNodeWithText("App projection Task").assertExists()

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithTag("planning-mode-action").performClick()
        compose.onNodeWithContentDescription("Schedule App projection Task").assertDoesNotExist()
    }

    private fun setAppContent(transport: ControllableTimeboxApi) {
        val repository = TimeboxRepository(transport.proxy())
        compose.setContent {
            TimeboxApp(
                isDark = false,
                onToggleDark = {},
                notificationsAllowed = true,
                onRequestNotificationPermission = {},
                onOpenNotificationSettings = {},
                repository = repository,
                taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository)),
                imeVisibleOverride = false,
            )
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Battle Plan").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun enterPlanningMode() {
        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithTag("planning-mode-action").performClick()
    }
}

private class ControllableTimeboxApi(initialReady: Boolean = false) {
    private val meta = DayMetaDto(
        timezone = "Asia/Singapore",
        today = "2026-09-08",
        serverNowIso = "2026-09-08T12:00:00+08:00",
    )
    private var task = taskDto(ready = initialReady, version = 1)
    private var readinessContinuation: Continuation<Any?>? = null

    fun proxy(): TimeboxApi = Proxy.newProxyInstance(
        TimeboxApi::class.java.classLoader,
        arrayOf(TimeboxApi::class.java),
    ) { _, method, args ->
        if (method.name == "patchBattleTask") {
            @Suppress("UNCHECKED_CAST")
            readinessContinuation = args?.lastOrNull() as Continuation<Any?>
            COROUTINE_SUSPENDED
        } else {
            val value: Any? = when (method.name) {
                "getDay" -> day(args?.firstOrNull() as? String ?: meta.today)
                "getDayPreview" -> preview(args?.firstOrNull() as? String ?: meta.today)
                "getDaySummary" -> DaySummaryDto(meta.today, 0, 0, emptyList(), meta)
                "getActiveActualBlock" -> null
                "listTaskTypes" -> listOf(TaskTypeDto(1, "Work"))
                "listProjects" -> emptyList<Any>()
                "listBattleTasks" -> BattleTaskListDto(listOf(task), meta.timezone, meta.serverNowIso)
                else -> Unit
            }
            @Suppress("UNCHECKED_CAST")
            val continuation = args?.lastOrNull() as? Continuation<Any?>
            if (continuation == null) value else {
                continuation.resumeWith(Result.success(value))
                COROUTINE_SUSPENDED
            }
        }
    } as TimeboxApi

    fun completeReadiness(ready: Boolean, version: Int, status: String = "open") {
        task = taskDto(ready, version, status)
        checkNotNull(readinessContinuation).resumeWith(Result.success(task))
        readinessContinuation = null
    }

    private fun day(date: String) = DayDto(
        id = 1,
        date = date,
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        timeBlocks = emptyList(),
        meta = meta.copy(today = meta.today),
    )

    private fun preview(date: String) = DayPreviewDto(
        date = date,
        startHour = 8,
        endHour = 20,
        showFullDay = false,
        timeBlocks = emptyList(),
        meta = meta,
    )

    private fun taskDto(ready: Boolean, version: Int, status: String = "open") = BattleTaskDto(
        id = 10,
        title = "App projection Task",
        description = "",
        readyToPlan = ready,
        status = status,
        version = version,
        position = 0,
        createdAt = "2026-09-08T00:00:00Z",
        updatedAt = "2026-09-08T00:00:00Z",
    )
}
