package com.timebox.android.data

import com.timebox.android.data.remote.DueReminderDto
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.reminders.ReminderNotifier
import com.timebox.android.reminders.deliverDueReminders
import com.timebox.android.reminders.reminderSchedule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class ReminderInfrastructureTest {
    @Test
    fun `notification is handed off before backend acknowledgement and not repeated in process`() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = reminderRepository(calls)
        val notifier = RecordingNotifier(calls)
        val shown = mutableSetOf<Int>()

        val first = deliverDueReminders(repository, notifier, shownInProcess = shown)
        val second = deliverDueReminders(repository, notifier, shownInProcess = shown)

        assertEquals(listOf("due", "notify:42", "ack:42", "due"), calls)
        assertEquals(1, first.handedOff)
        assertEquals(0, second.handedOff)
        assertEquals(setOf(42), shown)
    }

    @Test
    fun `permission denial is non blocking and never acknowledges unseen reminder`() = runBlocking {
        val calls = mutableListOf<String>()
        val result = deliverDueReminders(
            reminderRepository(calls),
            RecordingNotifier(calls, allowed = false),
        )

        assertEquals(emptyList<String>(), calls)
        assertEquals(0, result.handedOff)
        assertFalse(result.fetchFailed)
    }

    @Test
    fun `schedule includes eligible parent and subtask and drops delivered or completed tasks`() {
        val future = Instant.parse("2026-08-18T01:00:00Z")
        val subtask = task(2, reminderAt = future)
        val parent = task(1, reminderAt = future, subtasks = listOf(subtask))
        val completed = task(3, reminderAt = future, status = TaskStatus.Completed)
        val delivered = task(4, reminderAt = future, reminderDeliveredAt = Instant.EPOCH)

        val schedule = reminderSchedule(listOf(parent, completed, delivered))

        assertEquals(listOf(1, 2), schedule.map { it.taskId })
        assertTrue(schedule.all { it.at == future })
    }

    private fun reminderRepository(calls: MutableList<String>): TimeboxRepository {
        val api = Proxy.newProxyInstance(
            TimeboxApi::class.java.classLoader,
            arrayOf(TimeboxApi::class.java),
        ) { _, method, args ->
            when (method.name) {
                "listDueReminders" -> {
                    calls += "due"
                    listOf(DueReminderDto(42, "Write release notes", null, null, "2026-08-17T01:00:00Z"))
                }
                "acknowledgeReminder" -> {
                    calls += "ack:${args?.first()}"
                    Unit
                }
                else -> error("Unexpected call ${method.name}")
            }
        } as TimeboxApi
        return TimeboxRepository(api)
    }

    private class RecordingNotifier(
        private val calls: MutableList<String>,
        private val allowed: Boolean = true,
    ) : ReminderNotifier {
        override fun canNotify() = allowed
        override fun show(reminder: DueReminder): Boolean {
            calls += "notify:${reminder.id}"
            return allowed
        }
    }

    private fun task(
        id: Int,
        reminderAt: Instant?,
        status: TaskStatus = TaskStatus.Open,
        reminderDeliveredAt: Instant? = null,
        subtasks: List<BattleTask> = emptyList(),
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
        title = "Task $id",
        description = "",
        readyToPlan = false,
        status = status,
        urgency = null,
        importance = null,
        deadlineDate = null,
        deadlineAt = null,
        reminderAt = reminderAt,
        reminderDeliveredAt = reminderDeliveredAt,
        position = 0,
        archivedAt = null,
        deletedAt = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        overdue = false,
        subtasks = subtasks,
    )
}
