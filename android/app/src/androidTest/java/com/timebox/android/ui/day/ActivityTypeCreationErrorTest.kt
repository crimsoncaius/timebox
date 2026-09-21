package com.timebox.android.ui.day

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivityTypeCreationErrorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun startCreationFailurePreservesPathAndRetryClearsError() = verifyCreation(false)
    @Test fun switchCreationFailureStaysBesideTypeAndRetryClearsError() = verifyCreation(true)

    private fun verifyCreation(switching: Boolean) {
        val at = "2026-09-11T10:00:00Z"
        val current = if (switching) ActualBlockDto(7, 1, TaskTypeDto(1, "Reading"), startAt = at, createdAt = at, updatedAt = at) else null
        val snapshot = ActivitySnapshotDto(offlineReady = true, cursor = 1, serverAt = at, reportingTimezone = "UTC",
            current = current, records = listOfNotNull(current), taskTypes = listOf(TaskTypeDto(1, "Reading")))
        var journal: String? = null
        val repository = ActivityRepository(object : ActivityTransport {
            override suspend fun read() = snapshot
            override suspend fun execute(command: ActivityCommandDto): ActivitySnapshotDto = error("Unused")
        }, object : ActivityStorage {
            override fun load() = journal
            override fun save(value: String) { journal = value }
        })
        val paths = mutableListOf<String>()
        compose.setContent { TimeboxTheme(darkTheme = false) {
            ActivityTracking(emptyList(), {}, repository, createTaskType = { path ->
                paths += path
                if (paths.size == 1) Result.failure(IllegalStateException("Creation unavailable"))
                else Result.success(TaskType(2, path, 0))
            })
        } }
        compose.waitUntil(5000) { repository.state.value.snapshot != null }
        if (switching) {
            compose.onNodeWithText("Current activity").performClick()
            compose.onNodeWithText("Switch activity").performClick()
        } else compose.onNodeWithText("Start tracking").performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performScrollTo().performTextInput("work/deep")
        compose.onNodeWithText("Create work/deep").performScrollTo().performClick()
        compose.onNodeWithText("Creation unavailable").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("work/deep")).assertExists()
        if (switching) {
            val error = compose.onNodeWithText("Creation unavailable").fetchSemanticsNode().boundsInRoot
            val time = compose.onNodeWithText("When did this change happen?").fetchSemanticsNode().boundsInRoot
            assertTrue("Type errors belong before time controls", error.top < time.top)
        }
        compose.onNodeWithText("Create work/deep").performScrollTo().performClick()
        compose.waitUntil(5000) { paths.size == 2 }
        compose.onNodeWithText("Creation unavailable").assertDoesNotExist()
        compose.onNode(hasText(if (switching) "Switch activity" else "Start") and hasClickAction()).assertIsEnabled()
        assertEquals(listOf("work/deep", "work/deep"), paths)
    }
}
