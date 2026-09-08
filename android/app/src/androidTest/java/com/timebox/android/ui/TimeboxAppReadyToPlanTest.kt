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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.BattleTaskDto
import com.timebox.android.data.remote.BattleTaskListDto
import com.timebox.android.data.remote.DayDto
import com.timebox.android.data.remote.DayMetaDto
import com.timebox.android.data.remote.DayPreviewDto
import com.timebox.android.data.remote.DaySummaryDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.ui.readiness.createReadyToPlanCoordinator
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimeboxAppReadyToPlanTest {
    @get:Rule val compose = createComposeRule()
    private val readinessScopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelReadinessScopes() {
        readinessScopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun applicationOwnsOneStableReadyToPlanCoordinator() {
        val application = ApplicationProvider.getApplicationContext<TimeboxApplication>()

        assertSame(application.readinessCoordinator, application.readinessCoordinator)
    }

    @Test
    fun pendingReadyToPlanChoiceSurvivesNavigationAndSettlesWithoutFlicker() {
        val transport = ControllableTimeboxApi()
        val repository = TimeboxRepository(transport.proxy())
        val readinessCoordinator = testReadyToPlanCoordinator(repository)
        compose.setContent {
            TimeboxApp(
                isDark = false,
                onToggleDark = {},
                notificationsAllowed = true,
                onRequestNotificationPermission = {},
                onOpenNotificationSettings = {},
                repository = repository,
                taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository)),
                readinessCoordinator = readinessCoordinator,
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
            transport.setServerTask(ready = false, version = 2, status = "completed")
            transport.failReadiness()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").assertDoesNotExist()
        compose.onNodeWithText("Completed").performClick()
        compose.onNodeWithText("App projection Task").assertExists()
        compose.onNodeWithContentDescription("App projection Task readiness error").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(true), transport.readinessCalls) }

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithTag("planning-mode-action").performClick()
        compose.onNodeWithContentDescription("Schedule App projection Task").assertDoesNotExist()
    }

    @Test
    fun ordinaryCompletedRefreshOverridesPendingReadinessAndIgnoresLateWriteResponse() {
        val transport = ControllableTimeboxApi()
        setAppContent(transport)

        compose.onNodeWithText("Battle Plan").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        compose.onNodeWithContentDescription("Saving Ready to Plan for App projection Task")
            .assertExists()

        compose.runOnIdle { transport.setServerTask(ready = false, version = 2, status = "completed") }
        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Battle Plan").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Completed").performClick()
        compose.onNodeWithText("App projection Task").assertExists()
        compose.onNodeWithContentDescription("App projection Task readiness error").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()

        compose.runOnIdle { transport.completeReadiness(ready = true, version = 3) }
        compose.waitForIdle()

        compose.onNodeWithText("App projection Task").assertExists()
        compose.onNodeWithContentDescription("Remove App projection Task from Ready to Plan")
            .assertDoesNotExist()
        compose.onNodeWithContentDescription("App projection Task readiness error").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(true), transport.readinessCalls) }
    }

    @Test
    fun failedLatestChoiceReconcilesAndKeepsManualRetryAcrossNavigation() {
        val transport = ControllableTimeboxApi()
        setAppContent(transport)

        compose.onNodeWithText("Battle Plan").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        compose.runOnIdle { transport.failReadiness() }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("App projection Task readiness error")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Ready to Plan was not saved. Retry your latest choice.").assertExists()

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Battle Plan").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("App projection Task readiness error")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Retry").performClick()

        compose.runOnIdle {
            assertEquals(2, transport.readinessCalls.size)
            assertEquals(listOf(true, true), transport.readinessCalls)
        }
    }

    @Test
    fun unavailableReconciliationFallsBackAndKeepsAdditionOutOfDay() {
        val transport = ControllableTimeboxApi()
        setAppContent(transport)

        compose.onNodeWithText("Battle Plan").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        compose.runOnIdle {
            transport.failNextBattleTaskRead = true
            transport.failReadiness()
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Ready to Plan could not be confirmed. Retry your latest choice.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        enterPlanningMode()
        compose.onNodeWithContentDescription("Schedule App projection Task").assertDoesNotExist()
    }

    @Test
    fun TaskDetailKeepsReadinessInDraftUntilSaveThenUsesCoordinator() {
        val transport = ControllableTimeboxApi()
        setAppContent(transport)

        compose.onNodeWithText("Battle Plan").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("battle-plan-task-10").performTouchInput {
            click(center.copy(y = center.y / 3f))
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Edit details").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Edit details").performClick()
        compose.onNodeWithTag("task-detail-description").performTextReplacement("Draft notes")
        compose.onNodeWithText("Ready to Plan").performClick()

        compose.runOnIdle { assertEquals(emptyList<JsonObject>(), transport.patchBodies) }

        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(5_000) { transport.patchBodies.size == 1 }
        compose.runOnIdle {
            assertTrue("description" in transport.patchBodies.single())
            assertFalse("ready_to_plan" in transport.patchBodies.single())
            assertEquals(emptyList<Boolean>(), transport.readinessCalls)
            transport.completePatch(ready = false, version = 2)
        }
        compose.waitUntil(5_000) { transport.readinessCalls == listOf(true) }
        compose.onNodeWithContentDescription("Saving Ready to Plan for App projection Task")
            .assertExists()

        compose.runOnIdle { transport.completeReadiness(ready = true, version = 3) }
    }

    private fun setAppContent(transport: ControllableTimeboxApi) {
        val repository = TimeboxRepository(transport.proxy())
        val readinessCoordinator = testReadyToPlanCoordinator(repository)
        compose.setContent {
            TimeboxApp(
                isDark = false,
                onToggleDark = {},
                notificationsAllowed = true,
                onRequestNotificationPermission = {},
                onOpenNotificationSettings = {},
                repository = repository,
                taskCompletion = TaskCompletion(RepositoryTaskCompletionTransport(repository)),
                readinessCoordinator = readinessCoordinator,
                imeVisibleOverride = false,
            )
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Battle Plan").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun testReadyToPlanCoordinator(repository: TimeboxRepository) =
        createReadyToPlanCoordinator(
            repository,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also(readinessScopes::add),
        )

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
    private val readinessContinuations = ArrayDeque<Continuation<Any?>>()
    val readinessCalls = mutableListOf<Boolean>()
    val patchBodies = mutableListOf<JsonObject>()
    var failNextBattleTaskRead = false

    fun proxy(): TimeboxApi = Proxy.newProxyInstance(
        TimeboxApi::class.java.classLoader,
        arrayOf(TimeboxApi::class.java),
    ) { _, method, args ->
        if (method.name == "patchBattleTask") {
            val body = args?.getOrNull(1) as JsonObject
            patchBodies += body
            body["ready_to_plan"]?.jsonPrimitive?.boolean?.let(readinessCalls::add)
            @Suppress("UNCHECKED_CAST")
            readinessContinuations.addLast(args?.lastOrNull() as Continuation<Any?>)
            COROUTINE_SUSPENDED
        } else {
            val value: Any? = when (method.name) {
                "getDay" -> day(args?.firstOrNull() as? String ?: meta.today)
                "getDayPreview" -> preview(args?.firstOrNull() as? String ?: meta.today)
                "getDaySummary" -> DaySummaryDto(meta.today, 0, 0, emptyList(), meta)
                "getActiveActualBlock" -> null
                "listTaskTypes" -> listOf(TaskTypeDto(1, "Work"))
                "listProjects" -> emptyList<Any>()
                "listBattleTasks" -> if (failNextBattleTaskRead) {
                    failNextBattleTaskRead = false
                    throw IllegalStateException("task reconciliation unavailable")
                } else {
                    BattleTaskListDto(listOf(task), meta.timezone, meta.serverNowIso)
                }
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
        readinessContinuations.removeFirst().resumeWith(Result.success(task))
    }

    fun completePatch(ready: Boolean, version: Int, status: String = "open") {
        task = taskDto(ready, version, status)
        readinessContinuations.removeFirst().resumeWith(Result.success(task))
    }

    fun setServerTask(ready: Boolean, version: Int, status: String = "open") {
        task = taskDto(ready, version, status)
    }

    fun failReadiness() {
        readinessContinuations.removeFirst().resumeWith(
            Result.failure(IllegalStateException("readiness write failed")),
        )
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
