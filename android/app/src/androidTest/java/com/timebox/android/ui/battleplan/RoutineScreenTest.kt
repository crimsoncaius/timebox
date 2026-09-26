package com.timebox.android.ui.battleplan

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import com.timebox.android.ui.theme.TimeboxTheme
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoutineScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val api = RoutineTestApi()
    private lateinit var editor: RecurringEditorViewModel

    @After fun clearModels() { compose.runOnIdle { store.clear() } }

    private fun show(creating: Boolean = false, draft: RecurringEditorUiState? = null, onOpenTask: (Int) -> Unit = {}) {
        lateinit var lifecycle: RecurringViewModel
        compose.runOnIdle {
            val repository = api.repository()
            editor = RecurringEditorViewModel(repository)
            lifecycle = RecurringViewModel(repository)
            store.put("editor", editor)
            store.put("lifecycle", lifecycle)
            editor.open(if (creating) null else 7)
            if (!creating) lifecycle.openDetail(7)
        }
        compose.waitUntil(5_000) { !editor.state.value.loading && (creating || lifecycle.state.value.selectedTemplate != null) }
        if (draft != null) compose.runOnIdle { editor.applyDraft(draft) }
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                RoutineScreen(editor.state.collectAsState().value, editor, {}, onOpenTask,
                    if (creating) null else lifecycle.state.collectAsState().value, lifecycle)
            }
        }
    }

    @Test fun creationExposesOvernightPreplanningSlot() {
        show(creating = true, draft = RecurringEditorUiState(title = "Review", startDate = "2026-09-08",
            preplanningSlots = listOf(RecurringPreplanningSlotDraft(start = "23:00", end = "00:00"))))
        compose.onNodeWithText("Pre-planning").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Planned Block start 1").assertTextContains("23:00")
        compose.onNodeWithContentDescription("Planned Block end 1").assertTextContains("00:00")
    }

    @Test fun removingPreplanningSlotChangesDraftOnlyUntilSaved() {
        show(creating = true, draft = RecurringEditorUiState(title = "Review", startDate = "2026-09-08",
            preplanningSlots = listOf(RecurringPreplanningSlotDraft(start = "08:00", end = "09:00"),
                RecurringPreplanningSlotDraft(start = "15:00", end = "16:00"))))
        compose.onNodeWithText("Pre-planning").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Planned Block start 2").assertTextContains("15:00")
        compose.onNodeWithContentDescription("Remove pre-planning slot 2").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Planned Block start 2").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, editor.state.value.preplanningSlots.size) }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(1, editor.state.value.preplanningSlots.size) }
    }

    @Test fun creationShowsSubtasksAndRepeatOptions() {
        show(creating = true, draft = RecurringEditorUiState(title = "Review", startDate = "2026-09-08"))
        compose.onNodeWithText("Add subtask").performScrollTo().assertExists()
        compose.onNodeWithText("Repeat", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Keep unfinished", substring = true).performScrollTo().assertExists()
    }

    @Test fun endRequiresExplicitConfirmation() {
        show()
        compose.onNodeWithText("End").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, api.endCalls) }
        compose.onNodeWithText("Today's and future pristine tasks", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, api.endCalls) }
        compose.onNodeWithText("End").performScrollTo().performClick()
        compose.onNodeWithText("End routine").performClick()
        compose.waitUntil(5_000) { api.endCalls == 1 }
    }

    @Test fun detailShowsPersistedSlotsAndUnavailableTime() {
        api.template = api.template.copy(preplanningMode = "planned_time",
            preplanningSchedule = RecurringPreplanningScheduleDto(listOf(
                RecurringPreplanningSlotDto(key = "morning", startMinute = 480, endMinute = 540),
                RecurringPreplanningSlotDto(key = "afternoon", position = 1, startMinute = 900, endMinute = 1440)),
                listOf(RecurringPreplanningUnavailableSlotDto("2026-09-08", "afternoon", 900, 1440))))
        show()
        compose.onNodeWithText("Unavailable on 2026-09-08: 15:00–00:00").performScrollTo().assertExists()
        compose.onNodeWithText("Pre-planning").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Planned Block start 1").assertTextContains("08:00")
        compose.onNodeWithContentDescription("Planned Block start 2").assertTextContains("15:00")
        compose.onNodeWithContentDescription("Planned Block end 2").assertTextContains("00:00")
    }

    @Test fun detailShowsSubtasksAndOpensCurrentWork() {
        api.template = api.template.copy(checklistItems = listOf(RecurringChecklistItemDto(1, "Scan the week", 0)),
            currentTasks = listOf(RecurringTaskLinkDto(42, "Today's review", overdue = false)))
        var opened: Int? = null
        show(onOpenTask = { opened = it })
        compose.onNodeWithText("Scan the week").performScrollTo().assertExists()
        compose.onNodeWithText("Current work").performScrollTo().assertExists()
        compose.onNodeWithText("Today's review").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(42, opened) }
    }
}

internal class RoutineTestApi {
    @Volatile var endCalls = 0
    var template = RecurringTemplateDto(id = 7, title = "Daily review", description = "", mode = "scheduled",
        status = "active", frequency = "daily", interval = 1, startDate = "2026-09-08",
        createdAt = "2026-09-08T00:00:00Z", updatedAt = "2026-09-08T00:00:00Z", cadence = "Daily")

    fun repository() = TimeboxRepository(Proxy.newProxyInstance(TimeboxApi::class.java.classLoader,
        arrayOf(TimeboxApi::class.java)) { _, method, _ ->
        when (method.name) {
            "listTaskTypes" -> emptyList<TaskTypeDto>()
            "getRecurringTemplate" -> template
            "listRecurringTemplates" -> listOf(template)
            "previewRecurrence" -> RecurrencePreviewDto(emptyList(), 0, 0)
            "getRoutineCalendar" -> RoutineCalendarDto("2026-09-08", "2026-09-01", hasDates = false,
                upcoming = emptyList(), completed = emptyList())
            "endRecurringTemplate" -> { endCalls++; template.copy(status = "ended").also { template = it } }
            else -> error("Unexpected API: ${method.name}")
        }
    } as TimeboxApi)
}
