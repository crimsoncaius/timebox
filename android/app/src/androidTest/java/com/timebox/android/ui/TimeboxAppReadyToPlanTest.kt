package com.timebox.android.ui

import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.getUnclippedBoundsInRoot

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun directoryRisesAndCloseLowersItBackToTheSource() {
        setAppContent(ControllableTimeboxApi())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Navigate").performClick()
        compose.mainClock.advanceTimeBy(96)
        val enteringTop = compose.onNodeWithText("Timebox").getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(300)
        val openTop = compose.onNodeWithText("Timebox").getUnclippedBoundsInRoot().top
        assertTrue("Directory must rise", enteringTop > openTop)
        compose.onNodeWithContentDescription("Close navigation").performClick()
        compose.mainClock.advanceTimeBy(96)
        assertTrue("Directory must lower on Close", compose.onNodeWithText("Timebox").getUnclippedBoundsInRoot().top > openTop)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Timebox").assertDoesNotExist()
        compose.onNodeWithTag("planning-mode-action").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun directoryLowersAfterChoosingADestination() {
        setAppContent(ControllableTimeboxApi())
        compose.onNodeWithText("Navigate").performClick()
        val openTop = compose.onNodeWithText("Timebox").getUnclippedBoundsInRoot().top
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Battle Plan").performClick()
        compose.mainClock.advanceTimeBy(96)
        assertTrue("Directory must lower after selection", compose.onNodeWithText("Timebox").getUnclippedBoundsInRoot().top > openTop)
        // Destination accessibility stays hidden while the departing menu covers it.
        compose.onNodeWithText("Navigate").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Timebox").assertDoesNotExist()
        compose.onAllNodesWithText("Battle Plan").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Navigate").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun addShortcutOpensEditorDirectlyAndCancelReturnsToDay() {
        setAppContent(ControllableTimeboxApi())
        compose.onNodeWithText("Add shortcut").performScrollTo().performClick()
        compose.onNodeWithText("Edit shortcuts").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("day-pager").assertIsDisplayed()
        compose.onNodeWithText("Navigate").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close navigation").assertDoesNotExist()
    }

    @Test
    fun dayContentExtendsBehindFloatingNavigation() {
        setAppContent(ControllableTimeboxApi())
        val day = compose.onNodeWithTag("day-pager").getUnclippedBoundsInRoot()
        val navigation = compose.onNodeWithText("Navigate").getUnclippedBoundsInRoot()
        assertTrue("Day must extend below the floating navigation", day.bottom > navigation.bottom)
    }

    @Test
    fun shortcutMovesThePageHeadingWhileDockStaysFixed() {
        setAppContent(ControllableTimeboxApi())
        val dock = compose.onAllNodesWithText("Battle Plan").onLast()
        val dockLeft = dock.getUnclippedBoundsInRoot().left
        compose.mainClock.autoAdvance = false
        dock.performClick()
        compose.mainClock.advanceTimeBy(96)
        val movingLeft = compose.onAllNodesWithText("Battle Plan").onFirst().getUnclippedBoundsInRoot().left
        assertEquals(dockLeft, compose.onAllNodesWithText("Battle Plan").onLast().getUnclippedBoundsInRoot().left)
        compose.mainClock.advanceTimeBy(400)
        val settledLeft = compose.onAllNodesWithText("Battle Plan").onFirst().getUnclippedBoundsInRoot().left
        assertTrue("Page heading must move left as the right-hand shortcut enters", movingLeft > settledLeft)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun assistantShowsNavigationOnlyWhileKeyboardIsHidden() {
        val transport = ControllableTimeboxApi()
        val repository = TimeboxRepository(transport.proxy())
        val readinessCoordinator = testReadyToPlanCoordinator(repository)
        val imeVisible = mutableStateOf(false)
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
                imeVisibleOverride = imeVisible.value,
            )
        }
        navigate("Conversation")
        compose.onNodeWithContentDescription("Message Assistant").assertIsDisplayed()
        compose.onNodeWithText("Navigate").assertIsDisplayed()
        compose.runOnIdle { imeVisible.value = true }
        compose.onNodeWithText("Navigate").assertDoesNotExist()
        compose.onNodeWithContentDescription("Send").assertIsDisplayed()
        compose.runOnIdle { imeVisible.value = false }
        navigate("Day")
        compose.onNodeWithText("Navigate").assertIsDisplayed()

    }

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
            compose.onAllNodesWithText("Navigate").fetchSemanticsNodes().isNotEmpty()
        }
        navigate("Battle Plan")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        compose.onNodeWithContentDescription("Saving Ready to Plan for App projection Task")
            .assertIsEnabled()

        navigate("Day")
        compose.onNodeWithTag("planning-mode-action").performClick()
        compose.onNodeWithContentDescription("App projection Task is saving and unavailable")
            .assertIsNotEnabled()

        navigate("Battle Plan")
        compose.onNodeWithContentDescription("Saving Ready to Plan for App projection Task")
            .assertIsEnabled()

        awaitReadinessRequest(transport)
        compose.runOnIdle { transport.completeReadiness(ready = true, version = 2) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Remove App projection Task from Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }

        navigate("Day")
        compose.onNodeWithContentDescription("Schedule App projection Task").assertIsEnabled()
    }

    @Test
    fun leavingPlanningClearsDraftAndRemovalRemainsConsistentOnReturn() {
        val transport = ControllableTimeboxApi(initialReady = true)
        setAppContent(transport)

        enterPlanningMode()
        compose.onNodeWithContentDescription("Schedule App projection Task")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("App projection Task selected", useUnmergedTree = true).assertExists()

        navigate("Battle Plan")
        compose.onNodeWithContentDescription("Remove App projection Task from Ready to Plan").performClick()
        navigate("Day")
        compose.onNodeWithText("App projection Task selected", useUnmergedTree = true).assertDoesNotExist()

        awaitReadinessRequest(transport)
        compose.runOnIdle { transport.completeReadiness(ready = false, version = 2) }
        navigate("Battle Plan")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        awaitReadinessRequest(transport)
        compose.runOnIdle { transport.completeReadiness(ready = true, version = 3) }

        navigate("Day")
        compose.onNodeWithContentDescription("Schedule App projection Task")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("day-lane-planned").performTouchInput { click(center) }
        compose.onNodeWithContentDescription("Planning draft App projection Task").assertExists()
        compose.onNodeWithText("Navigate").assertDoesNotExist()

        navigate("Battle Plan")
        compose.onNodeWithContentDescription("Remove App projection Task from Ready to Plan").performClick()
        navigate("Day")
        compose.onNodeWithContentDescription("Planning draft App projection Task").assertDoesNotExist()
    }

    @Test
    fun serverLifecycleRejectionIsAuthoritativeAcrossAppNavigation() {
        val transport = ControllableTimeboxApi()
        setAppContent(transport)

        navigate("Battle Plan")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        awaitReadinessRequest(transport)
        compose.runOnIdle {
            transport.setServerTask(ready = false, version = 2, status = "completed")
            transport.failReadiness()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").assertDoesNotExist()
        // Reconciliation resumes asynchronously after the failed write. The old
        // Add action is also absent while saving, so its absence is not a barrier.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Completed  1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Completed  1").performClick()
        compose.onNodeWithText("App projection Task").assertExists()
        compose.onNodeWithContentDescription("App projection Task readiness error").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(true), transport.readinessCalls) }

        navigate("Day")
        compose.onNodeWithTag("planning-mode-action").performClick()
        compose.onNodeWithContentDescription("Schedule App projection Task").assertDoesNotExist()
    }

    @Test
    fun ordinaryCompletedRefreshOverridesPendingReadinessAndIgnoresLateWriteResponse() {
        val transport = ControllableTimeboxApi()
        setAppContent(transport)

        navigate("Battle Plan")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        compose.onNodeWithContentDescription("Saving Ready to Plan for App projection Task")
            .assertExists()

        awaitReadinessRequest(transport)
        compose.runOnIdle { transport.setServerTask(ready = false, version = 2, status = "completed") }
        navigate("Day")
        navigate("Battle Plan")
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

        navigate("Battle Plan")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        awaitReadinessRequest(transport)
        compose.runOnIdle { transport.failReadiness() }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("App projection Task readiness error")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Ready to Plan was not saved. Retry your latest choice.").assertExists()

        navigate("Day")
        navigate("Battle Plan")
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

        navigate("Battle Plan")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add App projection Task to Ready to Plan").performClick()
        awaitReadinessRequest(transport)
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
    fun TaskDetailSavesFieldsInPlaceAndReadinessUsesCoordinatorImmediately() {
        val transport = ControllableTimeboxApi()
        setAppContent(transport)
        navigate("Battle Plan")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add App projection Task to Ready to Plan").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("battle-plan-task-10").performTouchInput { click(center.copy(y = center.y / 3f)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Close task").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Description").performClick()
        compose.onNodeWithContentDescription("Task description").performTextReplacement("Saved notes")
        compose.onNodeWithText("Save").performClick()
        awaitNonReadinessPatchRequest(transport)
        compose.runOnIdle {
            assertEquals(setOf("description"), transport.patchBodies.single().keys)
            transport.completePatch(ready = false, version = 2)
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Edit task").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Ready to Plan").performClick()
        awaitReadinessRequest(transport)
        compose.runOnIdle { assertEquals(listOf(true), transport.readinessCalls) }
        compose.onNodeWithContentDescription("Ready to Plan").assertIsEnabled()
        compose.runOnIdle { transport.completeReadiness(ready = true, version = 3) }
    }

    private var resumePlanning = false

    private fun navigate(label: String) {
        val onDay = compose.onAllNodesWithTag("planning-mode-action").fetchSemanticsNodes().isNotEmpty()
        if (label == "Day" && onDay) return
        if (compose.onAllNodesWithText("Navigate").fetchSemanticsNodes().isEmpty() && onDay) {
            // The redesign requires leaving the planning editor before navigating.
            resumePlanning = true
            androidx.test.espresso.Espresso.pressBack()
        }
        compose.onNodeWithText("Navigate").performClick()
        compose.onNodeWithText(label).performScrollTo().performClick()
        if (label == "Day" && resumePlanning) compose.onNodeWithTag("planning-mode-action").performClick()
    }

    @Test
    fun directoryClosesWithoutResetAndRestoresConversationDraft() {
        setAppContent(ControllableTimeboxApi())
        navigate("Conversation")
        compose.onNodeWithContentDescription("Message Assistant").performTextReplacement("Keep this draft")
        compose.onNodeWithText("Navigate").performClick()
        compose.onNodeWithContentDescription("Close navigation").performClick()
        compose.onNodeWithText("Keep this draft").assertIsDisplayed()
        navigate("Battle Plan")
        navigate("Conversation")
        compose.onNodeWithText("Keep this draft").assertIsDisplayed()
        compose.onNodeWithText("Review").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        navigate("Conversation")
        compose.onNodeWithText("Keep this draft").assertIsDisplayed()
    }

    @Test
    fun directoryBackReturnsToSourceAndTopLevelBackReturnsToDay() {
        setAppContent(ControllableTimeboxApi())
        navigate("Battle Plan")
        compose.onNodeWithText("Navigate").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onAllNodesWithText("Battle Plan").onFirst().assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("planning-mode-action").assertIsDisplayed()
        navigate("Battle Plan")
        compose.onAllNodesWithText("Battle Plan").onFirst().assertIsDisplayed()
    }

    @Test
    fun calendarMonthAndContextualReturnSurviveDirectorySwitches() {
        setAppContent(ControllableTimeboxApi())
        navigate("Calendar")
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithText("August 2026").assertIsDisplayed()
        navigate("Battle Plan")
        navigate("Calendar")
        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("2026-08-21, no time recorded").performClick()
        compose.onNodeWithTag("planning-mode-action").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("August 2026").assertIsDisplayed()
        // Leaving a contextual Day must not save it above Calendar's directory entry.
        compose.onNodeWithContentDescription("2026-08-21, no time recorded").performClick()
        navigate("Battle Plan")
        navigate("Calendar")
        compose.onNodeWithText("August 2026").assertIsDisplayed()
    }

    @Test
    fun taskDetailHidesNavigationAndBackReturnsToList() {
        setAppContent(ControllableTimeboxApi())
        navigate("Battle Plan")
        compose.onNodeWithText("App projection Task").performClick()
        compose.onNodeWithText("Navigate").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.onAllNodesWithText("Battle Plan").onFirst().assertIsDisplayed()
        compose.onNodeWithText("App projection Task").assertIsDisplayed()
        compose.onNodeWithText("Navigate").assertIsDisplayed()
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
            compose.onAllNodesWithText("Navigate").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun testReadyToPlanCoordinator(repository: TimeboxRepository) =
        createReadyToPlanCoordinator(
            repository,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also(readinessScopes::add),
        )

    private fun enterPlanningMode() {
        navigate("Day")
        compose.onNodeWithTag("planning-mode-action").performClick()
    }

    private fun awaitReadinessRequest(transport: ControllableTimeboxApi) {
        compose.waitUntil(5_000) { transport.hasPendingReadinessRequest() }
    }

    private fun awaitNonReadinessPatchRequest(transport: ControllableTimeboxApi) {
        compose.waitUntil(5_000) { transport.hasPendingNonReadinessPatchRequest() }
    }
}

private class ControllableTimeboxApi(initialReady: Boolean = false) {
    private data class PendingPatch(
        val body: JsonObject,
        val continuation: Continuation<Any?>,
    )

    private val meta = DayMetaDto(
        timezone = "Asia/Singapore",
        today = "2026-09-08",
        serverNowIso = "2026-09-08T12:00:00+08:00",
    )
    private var task = taskDto(ready = initialReady, version = 1)
    private val patchLock = Any()
    private val pendingPatches = ArrayDeque<PendingPatch>()
    private val observedReadinessCalls = mutableListOf<Boolean>()
    private val observedPatchBodies = mutableListOf<JsonObject>()
    val readinessCalls: List<Boolean>
        get() = synchronized(patchLock) { observedReadinessCalls.toList() }
    val patchBodies: List<JsonObject>
        get() = synchronized(patchLock) { observedPatchBodies.toList() }
    var failNextBattleTaskRead = false

    fun proxy(): TimeboxApi = Proxy.newProxyInstance(
        TimeboxApi::class.java.classLoader,
        arrayOf(TimeboxApi::class.java),
    ) { _, method, args ->
        if (method.name == "patchBattleTask") {
            val body = args?.getOrNull(1) as JsonObject
            @Suppress("UNCHECKED_CAST")
            val continuation = args?.lastOrNull() as Continuation<Any?>
            synchronized(patchLock) {
                observedPatchBodies += body
                body["ready_to_plan"]?.jsonPrimitive?.boolean?.let(observedReadinessCalls::add)
                pendingPatches.addLast(PendingPatch(body, continuation))
            }
            COROUTINE_SUSPENDED
        } else {
            val value: Any? = when (method.name) {
                "chronicleMonth" -> com.timebox.android.data.remote.ChronicleMonthDto(
                    args?.firstOrNull() as? String ?: "2026-09", meta.today, emptyList())
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

    fun hasPendingReadinessRequest(): Boolean = synchronized(patchLock) {
        pendingPatches.any { "ready_to_plan" in it.body }
    }

    fun hasPendingNonReadinessPatchRequest(): Boolean = synchronized(patchLock) {
        pendingPatches.any { "ready_to_plan" !in it.body }
    }

    fun completeReadiness(ready: Boolean, version: Int, status: String = "open") {
        val response = taskDto(ready, version, status)
        val continuation = synchronized(patchLock) {
            task = response
            removePendingPatch { "ready_to_plan" in it.body }.continuation
        }
        continuation.resumeWith(Result.success(response))
    }

    fun completePatch(ready: Boolean, version: Int, status: String = "open") {
        val response = taskDto(ready, version, status)
        val continuation = synchronized(patchLock) {
            task = response
            removePendingPatch { "ready_to_plan" !in it.body }.continuation
        }
        continuation.resumeWith(Result.success(response))
    }

    fun setServerTask(ready: Boolean, version: Int, status: String = "open") {
        task = taskDto(ready, version, status)
    }

    fun failReadiness() {
        val continuation = synchronized(patchLock) {
            removePendingPatch { "ready_to_plan" in it.body }.continuation
        }
        continuation.resumeWith(
            Result.failure(IllegalStateException("readiness write failed")),
        )
    }

    private fun removePendingPatch(predicate: (PendingPatch) -> Boolean): PendingPatch {
        val index = pendingPatches.indexOfFirst(predicate)
        check(index >= 0) { "Expected a matching queued patch request" }
        return pendingPatches.removeAt(index)
    }

    private fun day(date: String) = DayDto(
        id = 1,
        date = date,
        timeBlocks = emptyList(),
        meta = meta.copy(today = meta.today),
    )

    private fun preview(date: String) = DayPreviewDto(
        date = date,
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
