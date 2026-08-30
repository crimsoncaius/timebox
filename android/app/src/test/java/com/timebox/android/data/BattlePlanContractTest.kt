package com.timebox.android.data

import com.timebox.android.data.remote.ApiFactory
import com.timebox.android.data.remote.BattleTaskListDto
import com.timebox.android.data.remote.BattleTaskCreateDto
import com.timebox.android.data.remote.DueReminderDto
import com.timebox.android.data.remote.RecurrencePreviewDto
import com.timebox.android.data.remote.RecurrenceRuleDto
import com.timebox.android.data.remote.RecurringTemplateDto
import com.timebox.android.data.remote.RecurringTemplateCreateDto
import com.timebox.android.data.remote.ProjectCreateDto
import com.timebox.android.data.remote.PlanningCommitDto
import com.timebox.android.data.remote.PlanningPlacementDto
import com.timebox.android.data.remote.TaskIdsDto
import com.timebox.android.data.remote.TaskPlacementDto
import com.timebox.android.data.remote.TaskReorderDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.data.remote.TimeBlockCreateDto
import com.timebox.android.data.remote.TimeBlockDto
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BattlePlanContractTest {
    private val json = ApiFactory.json

    @Test
    fun `task list deserializes every relationship and java time field`() {
        val dto = json.decodeFromString(
            BattleTaskListDto.serializer(),
            """{
              "items":[{
                "id":10,"parent_id":null,"parent_title":null,
                "project_id":2,"project":{"id":2,"name":"Launch","description":"Ship it","deadline_date":"2026-09-01","deadline_at":null,"created_at":"2026-08-01T10:00:00+08:00","updated_at":"2026-08-02T10:00:00+08:00"},
                "task_type_id":4,"task_type":{"id":4,"name":"coding"},
                "recurring_template_id":7,"recurring_template_title":"Weekly planning","occurrence_key":"2026-08-17","recurrence_kind":"scheduled",
                "quota_period_start":"2026-08-17","quota_period_end":"2026-08-23","expected_sessions":3,"session_index":1,"quota_completed":0,
                "title":"Plan","description":"Details","ready_to_plan":true,"is_blocked":true,"blocking_reason":"Waiting for legal","status":"in_progress","completed_at":null,"version":4,"urgency":"high","importance":"medium",
                "deadline_date":null,"deadline_at":"2026-08-20T09:00:00+08:00","reminder_at":"2026-08-20T08:00:00+08:00","reminder_delivered_at":null,
                "position":2,"archived_at":null,"deleted_at":null,"created_at":"2026-08-17T01:00:00Z","updated_at":"2026-08-17T02:00:00Z","overdue":false,
                "planned_dates":["2026-08-19","bad","2026-08-17","2026-08-17"],
                "subtasks":[{"id":11,"parent_task_id":10,"title":"Outline","checked":false,"effectively_resolved":false,"position":0,"created_at":"2026-08-17T01:00:00Z","updated_at":"2026-08-17T01:00:00Z"}],
                "session_tasks":[]
              }],"timezone":"Asia/Singapore","server_now_iso":"2026-08-17T12:00:00+08:00"
            }""",
        ).toModel()

        val task = dto.items.single()
        assertEquals(TaskStatus.InProgress, task.status)
        assertTrue(task.isBlocked)
        assertEquals("Waiting for legal", task.blockingReason)
        assertEquals(PriorityLevel.High, task.urgency)
        assertEquals(LocalDate.parse("2026-08-17"), task.quotaPeriodStart)
        assertEquals(Instant.parse("2026-08-20T01:00:00Z"), task.deadlineAt)
        assertEquals("Outline", task.subtasks.single().title)
        assertEquals(false, task.subtasks.single().checked)
        assertEquals(4, task.version)
        assertEquals(listOf(LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-19")), task.plannedDates)
        assertEquals("Asia/Singapore", dto.timezone)
    }

    @Test
    fun `legacy blocked status is presented in the open lane as a condition`() {
        val dto = json.decodeFromString(
            BattleTaskListDto.serializer(),
            """{"items":[{"id":1,"title":"Legacy","description":"","ready_to_plan":false,"status":"blocked","position":0,"created_at":"2026-08-17T01:00:00Z","updated_at":"2026-08-17T01:00:00Z"}],"timezone":"UTC","server_now_iso":"2026-08-17T01:00:00Z"}""",
        ).toModel().items.single()

        assertEquals(TaskStatus.Open, dto.status)
        assertTrue(dto.isBlocked)
        assertEquals(emptyList<LocalDate>(), dto.plannedDates)
    }

    @Test
    fun `recurrence preview and template deserialize complete contracts`() {
        val preview = json.decodeFromString(
            RecurrencePreviewDto.serializer(),
            """{"upcoming":[{"key":"2026-W34","start":"2026-08-17","end":"2026-08-23"}],"past_cycles":2,"past_tasks":6}""",
        ).toModel()
        assertEquals(6, preview.pastTasks)
        assertEquals(LocalDate.parse("2026-08-23"), preview.upcoming.single().end)

        val template = json.decodeFromString(
            RecurringTemplateDto.serializer(),
            """{"id":5,"title":"Practice","description":"","project_id":null,"task_type_id":3,"task_type":{"id":3,"name":"practice"},"mode":"quota","status":"paused","frequency":"weekly","interval":1,"weekdays":[],"month_day":null,"quota_count":3,"start_date":"2026-08-01","end_date":null,"cycle_limit":10,"urgency":"low","importance":null,"paused_at":"2026-08-16T00:00:00Z","ended_at":null,"created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-16T00:00:00Z","checklist_items":[{"id":1,"title":"Warm up","position":0}],"upcoming":[],"current_tasks":[{"id":99,"title":"Session 1","deadline_date":"2026-08-23","overdue":false}],"cadence":"3 times weekly","next_occurrence":"2026-08-17"}""",
        ).toModel()
        assertEquals(RecurrenceMode.Quota, template.mode)
        assertEquals(RecurrenceStatus.Paused, template.status)
        assertEquals(3, template.quotaCount)
        assertEquals("Warm up", template.checklistItems.single().title)
        assertEquals(99, template.currentTasks.single().id)
    }

    @Test
    fun `task type usage and linked time block are retained`() {
        val type = json.decodeFromString(
            TaskTypeDto.serializer(),
            """{"id":3,"name":"coding","usage_count":4,"task_usage_count":5,"recurring_template_usage_count":6}""",
        ).toModel()
        assertEquals(5, type.taskUsageCount)
        assertEquals(6, type.recurringTemplateUsageCount)

        val block = json.decodeFromString(
            TimeBlockDto.serializer(),
            """{"id":1,"lane":"planned","task_type_id":3,"task_type":{"id":3,"name":"coding"},"task_id":10,"task":{"id":10,"title":"Plan","status":"open","task_type_id":3,"archived_at":"2026-08-18T00:00:00Z","deleted_at":null},"start_minute":540,"end_minute":570}""",
        ).toModel()
        assertEquals(10, block.taskId)
        assertEquals("Plan", block.task?.title)
        assertEquals(TaskStatus.Open, block.task?.status)
        assertTrue(block.task?.isReadOnly == true)
    }

    @Test
    fun `time block create serializes optional task link without global nulls`() {
        val linked = json.encodeToString(TimeBlockCreateDto("planned", 3, 10, null, 540, 570))
        assertTrue("\"task_id\":10" in linked)
        assertTrue("note" !in linked)

        val unlinked = json.encodeToString(TimeBlockCreateDto("planned", 3, null, null, 540, 570))
        assertTrue("task_id" !in unlinked)
        assertNull(json.decodeFromString(TimeBlockCreateDto.serializer(), unlinked).taskId)
    }

    @Test
    fun `planning commit serializes an atomic multi-day session`() {
        val encoded = json.encodeToString(
            PlanningCommitDto.serializer(),
            PlanningCommitDto(
                listOf(
                    PlanningPlacementDto("2026-08-24", 10, 3, 540, 570),
                    PlanningPlacementDto("2026-08-25", 11, 4, 600, 660),
                )
            ),
        )

        assertTrue("\"task_id\":10" in encoded)
        assertTrue("\"task_type_id\":4" in encoded)
        assertTrue("\"date\":\"2026-08-25\"" in encoded)
        assertTrue("\"end_minute\":660" in encoded)
    }

    @Test
    fun `reminder timestamps and naive database timestamps convert at domain boundary`() {
        val reminder = json.decodeFromString(
            DueReminderDto.serializer(),
            """{"id":10,"title":"Plan","deadline_date":"2026-08-18","deadline_at":null,"reminder_at":"2026-08-17T09:00:00+08:00"}""",
        ).toModel()
        assertEquals(Instant.parse("2026-08-17T01:00:00Z"), reminder.reminderAt)
        assertEquals(Instant.parse("2026-08-17T01:00:00Z"), parseInstant("2026-08-17T01:00:00"))
    }

    @Test
    fun `project task reorder and recurrence requests serialize backend wire names`() {
        val project = json.encodeToString(
            ProjectCreateDto.serializer(),
            ProjectCreateDto("Launch", "Ship", "2026-09-01"),
        )
        assertTrue("\"deadline_date\":\"2026-09-01\"" in project)

        val task = json.encodeToString(
            BattleTaskCreateDto.serializer(),
            BattleTaskCreateDto("Task", readyToPlan = true, status = "blocked", projectId = 2),
        )
        assertTrue("\"ready_to_plan\":true" in task)
        assertTrue("\"project_id\":2" in task)

        val reorder = json.encodeToString(
            TaskReorderDto.serializer(),
            TaskReorderDto(listOf(TaskPlacementDto(10, "in_progress", 3))),
        )
        assertTrue("\"task_id\":10" in reorder)
        assertEquals("{\"task_ids\":[10,11]}", json.encodeToString(TaskIdsDto.serializer(), TaskIdsDto(listOf(10, 11))))

        val rule = RecurrenceRuleDto("scheduled", "weekly", weekdays = listOf(0, 2), startDate = "2026-08-17")
        val template = RecurringTemplateCreateDto(
            title = "Planning",
            mode = rule.mode,
            frequency = rule.frequency,
            weekdays = rule.weekdays,
            startDate = rule.startDate,
            checklistTitles = listOf("Review"),
        )
        val encodedTemplate = json.encodeToString(RecurringTemplateCreateDto.serializer(), template)
        assertTrue("\"checklist_titles\":[\"Review\"]" in encodedTemplate)
        assertTrue("\"weekdays\":[0,2]" in encodedTemplate)
    }
}
