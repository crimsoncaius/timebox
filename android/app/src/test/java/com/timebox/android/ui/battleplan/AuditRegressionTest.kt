package com.timebox.android.ui.battleplan

import androidx.lifecycle.viewModelScope
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.remote.*
import com.timebox.android.ui.taskcompletion.RepositoryTaskCompletionTransport
import com.timebox.android.ui.taskcompletion.TaskCompletion
import com.timebox.android.reminders.*
import java.lang.reflect.Proxy
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Regression checks from the November audit: obsolete reads and DST transitions. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val stamp = "2026-11-06T00:00:00Z"
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()
    private fun proxy(action: (String, Array<out Any?>?) -> Any?): TimeboxApi =
        Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args -> action(method.name, args) } as TimeboxApi

    @Test fun lateTaskReadMustNotReplaceNewerTask() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        fun task(id: Int) = BattleTaskDto(id = id, title = "Task $id", description = "", readyToPlan = false,
            status = "open", position = 0, createdAt = stamp, updatedAt = stamp)
        val api = proxy { name, _ -> when (name) {
            "listBattleTasks" -> {
                if (reads.incrementAndGet() == 1) { started.complete(Unit); runBlocking { gate.await() } }
                BattleTaskListDto(listOf(task(1), task(2)), "UTC", stamp)
            }
            "listProjects", "listTaskTypes" -> emptyList<Nothing>()
            else -> error(name)
        } }
        val repo = TimeboxRepository(api)
        val model = TaskDetailViewModel(repo, TaskCompletion(RepositoryTaskCompletionTransport(repo)))
        try {
            model.load(1); started.await()
            val firstLoad = model.viewModelScope.coroutineContext[Job]!!.children.first()
            model.load(2); model.state.first { !it.loading && it.taskId == 2 }
            println("Task 2 loaded; releasing older Task 1 response")
            gate.complete(Unit)
            firstLoad.join()
            assertEquals("Current task must remain Task 2", 2, model.state.value.taskId)
        } finally { gate.complete(Unit); model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun lateRoutineReadMustNotReplaceNewerRoutine() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val api = proxy { name, args -> when (name) {
            "listTaskTypes" -> emptyList<TaskTypeDto>()
            "previewRecurrence" -> RecurrencePreviewDto(emptyList(), 0, 0)
            "getRecurringTemplate" -> {
                val id = args!![0] as Int
                if (id == 1) { started.complete(Unit); runBlocking { gate.await() } }
                RecurringTemplateDto(id = id, title = "Routine $id", description = "", mode = "scheduled", status = "active",
                    frequency = "daily", interval = 1, startDate = "2026-11-06", createdAt = stamp, updatedAt = stamp, cadence = "Daily")
            }
            else -> error(name)
        } }
        val model = RecurringEditorViewModel(TimeboxRepository(api))
        try {
            model.open(1); started.await()
            val firstLoad = model.viewModelScope.coroutineContext[Job]!!.children.first()
            model.open(2); model.state.first { !it.loading && it.template?.id == 2 }
            println("Routine 2 loaded; releasing older Routine 1 response")
            gate.complete(Unit)
            firstLoad.join()
            assertEquals("Current routine must remain Routine 2", 2, model.state.value.templateId)
        } finally { gate.complete(Unit); model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun springGapMustNotMoveTomorrowReminder() {
        val now = ZonedDateTime.parse("2026-03-08T04:00:00-04:00[America/New_York]")
        val result = nextDailyReminderSchedules(DailyReminderSettings(planning = DailyReminder(true, LocalTime.of(2, 30))), now).single()
        println("Expected tomorrow 02:30; actual ${result.at.atZone(now.zone)}")
        assertEquals(LocalTime.of(2, 30), result.at.atZone(now.zone).toLocalTime())
    }

    @Test fun lateRoutineFailureMustNotReplaceNewerRoutineWithError() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val api = proxy { name, args -> when (name) {
            "listTaskTypes" -> emptyList<TaskTypeDto>()
            "previewRecurrence" -> RecurrencePreviewDto(emptyList(), 0, 0)
            "getRecurringTemplate" -> {
                val id = args!![0] as Int
                if (id == 1) { started.complete(Unit); runBlocking { gate.await() }; throw java.io.IOException("Old request failed") }
                RecurringTemplateDto(id = id, title = "Routine $id", description = "", mode = "scheduled", status = "active",
                    frequency = "daily", interval = 1, startDate = "2026-11-06", createdAt = stamp, updatedAt = stamp, cadence = "Daily")
            }
            else -> error(name)
        } }
        val model = RecurringEditorViewModel(TimeboxRepository(api))
        try {
            model.open(1); started.await()
            val firstLoad = model.viewModelScope.coroutineContext[Job]!!.children.first()
            model.open(2); model.state.first { !it.loading && it.template?.id == 2 }
            gate.complete(Unit); firstLoad.join()
            assertEquals(2, model.state.value.templateId)
            assertNull(model.state.value.error)
        } finally { gate.complete(Unit); model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}


