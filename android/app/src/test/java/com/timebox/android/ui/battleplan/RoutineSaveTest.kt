package com.timebox.android.ui.battleplan

import androidx.lifecycle.viewModelScope
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import java.lang.reflect.Proxy
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineSaveTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun `failed edit survives retry and response becomes next baseline`() = runTest(dispatcher) {
        val stamp = "2026-09-19T00:00:00Z"
        var saved = RecurringTemplateDto(id = 1, title = "Review", description = "Original", mode = "scheduled", status = "active", frequency = "weekly", interval = 1, weekdays = listOf(0), startDate = "2026-09-21", createdAt = stamp, updatedAt = stamp, cadence = "Weekly")
        var fail = true
        val patches = mutableListOf<JsonObject>()
        val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
            when (method.name) {
                "listTaskTypes" -> emptyList<TaskTypeDto>()
                "getRecurringTemplate" -> saved
                "previewRecurrence" -> RecurrencePreviewDto(emptyList(), 0, 0)
                "patchRecurringTemplate" -> {
                    val patch = args!![1] as JsonObject
                    patches += patch
                    if (fail) throw IOException("No connection")
                    saved = saved.copy(title = patch["title"]?.jsonPrimitive?.content ?: saved.title, description = patch["description"]?.jsonPrimitive?.content ?: saved.description)
                    saved
                }
                else -> error("Unexpected API call ${method.name}")
            }
        } as TimeboxApi
        val model = RecurringEditorViewModel(TimeboxRepository(api))
        try {
            model.open(1); model.state.first { !it.loading }
            model.applyDraft(model.state.value.copy(title = "Renamed")); model.save(); model.state.first { !it.saving }
            assertTrue(model.state.value.dirty)
            assertEquals("Renamed", model.state.value.title)
            assertNotNull(model.state.value.saveError)
            fail = false
            model.save(); model.state.first { !it.saving }
            assertFalse(model.state.value.dirty)
            assertNull(model.state.value.saveError)
            assertEquals("Renamed", model.state.value.template?.title)
            model.applyDraft(model.state.value.copy(description = "New description")); model.save(); model.state.first { !it.saving }
            assertEquals(setOf("description", "confirm_backfill"), patches.last().keys)
            model.applyDraft(model.state.value.copy(title = "Unsaved")); model.discardDraft()
            assertEquals("Renamed", model.state.value.title)
            assertEquals("New description", model.state.value.description)
        } finally { model.viewModelScope.cancel() }
    }
}
