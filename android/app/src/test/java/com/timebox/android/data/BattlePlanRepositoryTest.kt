package com.timebox.android.data

import com.timebox.android.data.remote.BattleTaskDto
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.BattleTaskListDto
import com.timebox.android.data.remote.DueReminderDto
import com.timebox.android.data.remote.DayDto
import com.timebox.android.data.remote.DayMetaDto
import com.timebox.android.data.remote.PatchField
import com.timebox.android.data.remote.ProjectDto
import com.timebox.android.data.remote.PlanningCommitResponseDto
import com.timebox.android.data.remote.RecurrencePreviewDto
import com.timebox.android.data.remote.RecurringTemplateDto
import com.timebox.android.data.remote.TimeboxApi
import com.timebox.android.data.remote.TaskCompletionResponseDto
import com.timebox.android.data.remote.SubtaskDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.Instant

class BattlePlanRepositoryTest {
    private val calls = mutableListOf<String>()
    private val repository = TimeboxRepository(fakeApi())

    @Test
    fun `every Battle Plan and recurring endpoint is callable through repository`() = runBlocking {
        repository.listProjects().getOrThrow()
        repository.createProject(ProjectCreate("New")).getOrThrow()
        repository.patchProject(1, ProjectPatch(name = PatchField.of("Changed"))).getOrThrow()
        repository.deleteProject(1).getOrThrow()

        repository.listBattleTasks(TaskCollection.Active).getOrThrow()
        repository.createBattleTask(BattleTaskCreate("Task")).getOrThrow()
        repository.patchBattleTask(10, BattleTaskPatch(projectId = PatchField.clear())).getOrThrow()
        repository.completeBattleTask(10).getOrThrow()
        repository.checkSubtask(11).getOrThrow()
        repository.uncheckSubtask(11).getOrThrow()
        repository.reopenBattleTask(10).getOrThrow()
        repository.undoBattleTaskCompletion(10, "undo-token").getOrThrow()
        repository.reorderBattleTasks(listOf(TaskPlacement(10, TaskStatus.Open, 0))).getOrThrow()
        repository.archiveCompletedBattleTasks(listOf(10)).getOrThrow()
        repository.unarchiveBattleTask(10).getOrThrow()
        repository.trashBattleTask(10).getOrThrow()
        repository.restoreBattleTask(10).getOrThrow()
        repository.permanentlyDeleteBattleTask(10).getOrThrow()
        repository.commitPlan(
            listOf(PlanningCommitPlacement(LocalDate.parse("2026-08-20"), 10, 3, 540, 570))
        ).getOrThrow()
        repository.startActualBlock(taskId = 10, plannedBlockId = 31).getOrThrow()
        repository.createActualBlock(
            startAt = Instant.parse("2026-08-20T01:00:00Z"),
            endAt = Instant.parse("2026-08-20T02:00:00Z"),
            taskTypeId = 3,
            taskId = 10,
        ).getOrThrow()
        repository.getActualBlock(44).getOrThrow()
        repository.getActiveActualBlock().getOrThrow()
        repository.patchActualBlock(44, startAt = Instant.parse("2026-08-20T01:01:00Z")).getOrThrow()
        repository.finishActualBlock(44).getOrThrow()
        repository.deleteActualBlock(44).getOrThrow()

        repository.listDueReminders().getOrThrow()
        repository.acknowledgeReminder(10).getOrThrow()

        val rule = RecurrenceRule(
            RecurrenceMode.Scheduled,
            RecurrenceFrequency.Daily,
            startDate = LocalDate.parse("2026-08-17"),
        )
        repository.previewRecurrence(rule).getOrThrow()
        repository.listRecurringTemplates(RecurrenceStatus.Active).getOrThrow()
        repository.createRecurringTemplate(RecurringTemplateCreate("Daily", rule = rule)).getOrThrow()
        repository.getRecurringTemplate(5).getOrThrow()
        repository.patchRecurringTemplate(5, RecurringTemplatePatch(title = PatchField.of("Updated"))).getOrThrow()
        repository.pauseRecurringTemplate(5).getOrThrow()
        repository.resumeRecurringTemplate(5).getOrThrow()
        repository.endRecurringTemplate(5).getOrThrow()
        repository.deleteRecurringTemplate(5).getOrThrow()

        assertEquals(
            setOf(
                "listProjects", "createProject", "patchProject", "deleteProject",
                "listBattleTasks", "createBattleTask", "patchBattleTask", "reorderBattleTasks",
                "completeBattleTask", "reopenBattleTask", "undoBattleTaskCompletion",
                "checkSubtask", "uncheckSubtask",
                "archiveCompletedBattleTasks", "unarchiveBattleTask", "trashBattleTask",
                "restoreBattleTask", "permanentlyDeleteBattleTask", "listDueReminders",
                "commitPlan",
                "startActualBlock", "createActualBlock", "getActualBlock", "getActiveActualBlock",
                "patchActualBlock", "finishActualBlock", "deleteActualBlock",
                "acknowledgeReminder", "previewRecurrence", "listRecurringTemplates",
                "createRecurringTemplate", "getRecurringTemplate", "patchRecurringTemplate",
                "pauseRecurringTemplate", "resumeRecurringTemplate", "endRecurringTemplate",
                "deleteRecurringTemplate",
            ),
            calls.toSet(),
        )
        assertEquals(37, calls.size)
    }

    @Test
    fun `repository maps wire values to domain values`() = runBlocking {
        val task = repository.listBattleTasks().getOrThrow().items.single()
        assertEquals(TaskStatus.Open, task.status)
        assertEquals("Project", task.project?.name)
        val reminder = repository.listDueReminders().getOrThrow().single()
        assertTrue(reminder.reminderAt.toString().startsWith("2026-08-17T"))
        val template = repository.getRecurringTemplate(5).getOrThrow()
        assertEquals(RecurrenceFrequency.Daily, template.frequency)
    }

    private fun fakeApi(): TimeboxApi {
        val project = projectDto()
        val task = taskDto(project)
        val template = templateDto()
        val actual = actualDto()
        val subtask = SubtaskDto(11, 10, "Check contract", false, false, 0, "2026-08-17T00:00:00Z", "2026-08-17T00:00:00Z")
        val handler = java.lang.reflect.InvocationHandler { _, method, _ ->
            calls += method.name
            when (method.name) {
                "listProjects" -> listOf(project)
                "createProject", "patchProject" -> project
                "listBattleTasks" -> BattleTaskListDto(
                    listOf(task), "Asia/Singapore", "2026-08-17T12:00:00+08:00",
                )
                "createBattleTask", "patchBattleTask", "trashBattleTask", "reopenBattleTask",
                "undoBattleTaskCompletion" -> task
                "completeBattleTask" -> TaskCompletionResponseDto(task, "undo-token", emptyList())
                "checkSubtask", "uncheckSubtask" -> subtask
                "startActualBlock", "createActualBlock", "getActualBlock", "getActiveActualBlock",
                "patchActualBlock", "finishActualBlock" -> actual
                "commitPlan" -> PlanningCommitResponseDto(
                    listOf(
                        DayDto(
                            id = 1,
                            date = "2026-08-20",
                            startHour = 8,
                            endHour = 20,
                            showFullDay = false,
                            timeBlocks = emptyList(),
                            meta = DayMetaDto(
                                timezone = "Asia/Singapore",
                                today = "2026-08-20",
                                serverNowIso = "2026-08-20T09:00:00+08:00",
                            ),
                        )
                    )
                )
                "listDueReminders" -> listOf(
                    DueReminderDto(10, "Task", null, "2026-08-18T10:00:00+08:00", "2026-08-17T09:00:00+08:00")
                )
                "previewRecurrence" -> RecurrencePreviewDto(emptyList(), 0, 0)
                "listRecurringTemplates" -> listOf(template)
                "createRecurringTemplate", "getRecurringTemplate", "patchRecurringTemplate",
                "pauseRecurringTemplate", "resumeRecurringTemplate", "endRecurringTemplate" -> template
                else -> Unit
            }
        }
        return Proxy.newProxyInstance(
            TimeboxApi::class.java.classLoader,
            arrayOf(TimeboxApi::class.java),
            handler,
        ) as TimeboxApi
    }

    private fun projectDto() = ProjectDto(
        1, "Project", "", null, null, "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z",
    )

    private fun taskDto(project: ProjectDto) = BattleTaskDto(
        id = 10,
        projectId = project.id,
        project = project,
        title = "Task",
        description = "",
        readyToPlan = false,
        status = "open",
        position = 0,
        createdAt = "2026-08-17T00:00:00Z",
        updatedAt = "2026-08-17T00:00:00Z",
    )

    private fun templateDto() = RecurringTemplateDto(
        id = 5,
        title = "Daily",
        description = "",
        mode = "scheduled",
        status = "active",
        frequency = "daily",
        interval = 1,
        startDate = "2026-08-17",
        createdAt = "2026-08-17T00:00:00Z",
        updatedAt = "2026-08-17T00:00:00Z",
        cadence = "Daily",
    )

    private fun actualDto() = ActualBlockDto(
        id = 44,
        taskTypeId = 3,
        taskType = com.timebox.android.data.remote.TaskTypeDto(3, "coding"),
        taskId = 10,
        startAt = "2026-08-20T01:00:00Z",
        endAt = "2026-08-20T02:00:00Z",
        createdAt = "2026-08-20T01:00:00Z",
        updatedAt = "2026-08-20T02:00:00Z",
    )
}
