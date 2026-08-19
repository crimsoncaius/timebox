package com.timebox.android.data.remote

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TimeboxApi {

    @GET("days")
    suspend fun listDays(@Query("limit") limit: Int = 120): List<DayListItemDto>

    @GET("days/{date}")
    suspend fun getDay(@Path("date") date: String): DayDto

    @GET("days/{date}/preview")
    suspend fun getDayPreview(@Path("date") date: String): DayPreviewDto

    @GET("days/{date}/summary")
    suspend fun getDaySummary(@Path("date") date: String): DaySummaryDto

    @POST("days/{date}/blocks")
    suspend fun createBlock(
        @Path("date") date: String,
        @Body body: TimeBlockCreateDto,
    ): DayDto

    @PATCH("days/{date}/blocks/{blockId}")
    suspend fun patchBlock(
        @Path("date") date: String,
        @Path("blockId") blockId: Int,
        @Body body: JsonObject,
    ): DayDto

    @DELETE("days/{date}/blocks/{blockId}")
    suspend fun deleteBlock(
        @Path("date") date: String,
        @Path("blockId") blockId: Int,
    ): DayDto

    @POST("days/{date}/blocks/{blockId}/complete-as-planned")
    suspend fun completeAsPlanned(
        @Path("date") date: String,
        @Path("blockId") blockId: Int,
    ): DayDto

    @GET("settings")
    suspend fun getSettings(): SettingsDto

    @PATCH("settings")
    suspend fun patchSettings(@Body body: SettingsPatchDto): SettingsDto

    @GET("task-types")
    suspend fun listTaskTypes(): List<TaskTypeDto>

    @POST("task-types")
    suspend fun createTaskType(@Body body: TaskTypeCreateDto): TaskTypeDto

    @DELETE("task-types/{id}")
    suspend fun deleteTaskType(
        @Path("id") id: Int,
        @Query("cascade_blocks") cascadeBlocks: Boolean = false,
        @Query("migrate_blocks_to") migrateBlocksTo: Int? = null,
        @Query("clear_task_references") clearTaskReferences: Boolean = false,
    )

    @GET("projects")
    suspend fun listProjects(): List<ProjectDto>

    @POST("projects")
    suspend fun createProject(@Body body: ProjectCreateDto): ProjectDto

    @PATCH("projects/{projectId}")
    suspend fun patchProject(@Path("projectId") projectId: Int, @Body body: JsonObject): ProjectDto

    @DELETE("projects/{projectId}")
    suspend fun deleteProject(@Path("projectId") projectId: Int)

    @GET("tasks")
    suspend fun listBattleTasks(@Query("state") state: String): BattleTaskListDto

    @POST("tasks")
    suspend fun createBattleTask(@Body body: BattleTaskCreateDto): BattleTaskDto

    @PATCH("tasks/{taskId}")
    suspend fun patchBattleTask(@Path("taskId") taskId: Int, @Body body: JsonObject): BattleTaskDto

    @POST("tasks/reorder")
    suspend fun reorderBattleTasks(@Body body: TaskReorderDto)

    @POST("tasks/archive-completed")
    suspend fun archiveCompletedBattleTasks(@Body body: TaskIdsDto)

    @POST("tasks/{taskId}/unarchive")
    suspend fun unarchiveBattleTask(@Path("taskId") taskId: Int)

    @DELETE("tasks/{taskId}")
    suspend fun trashBattleTask(@Path("taskId") taskId: Int): BattleTaskDto

    @POST("tasks/{taskId}/restore")
    suspend fun restoreBattleTask(@Path("taskId") taskId: Int)

    @DELETE("tasks/{taskId}/permanent")
    suspend fun permanentlyDeleteBattleTask(@Path("taskId") taskId: Int)

    @GET("reminders/due")
    suspend fun listDueReminders(): List<DueReminderDto>

    @POST("reminders/{taskId}/delivered")
    suspend fun acknowledgeReminder(@Path("taskId") taskId: Int)

    @POST("recurring-templates/preview")
    suspend fun previewRecurrence(@Body body: RecurrenceRuleDto): RecurrencePreviewDto

    @GET("recurring-templates")
    suspend fun listRecurringTemplates(@Query("status") status: String): List<RecurringTemplateDto>

    @POST("recurring-templates")
    suspend fun createRecurringTemplate(@Body body: RecurringTemplateCreateDto): RecurringTemplateDto

    @GET("recurring-templates/{templateId}")
    suspend fun getRecurringTemplate(@Path("templateId") templateId: Int): RecurringTemplateDto

    @PATCH("recurring-templates/{templateId}")
    suspend fun patchRecurringTemplate(
        @Path("templateId") templateId: Int,
        @Body body: JsonObject,
    ): RecurringTemplateDto

    @POST("recurring-templates/{templateId}/pause")
    suspend fun pauseRecurringTemplate(@Path("templateId") templateId: Int): RecurringTemplateDto

    @POST("recurring-templates/{templateId}/resume")
    suspend fun resumeRecurringTemplate(@Path("templateId") templateId: Int): RecurringTemplateDto

    @POST("recurring-templates/{templateId}/end")
    suspend fun endRecurringTemplate(@Path("templateId") templateId: Int): RecurringTemplateDto

    @DELETE("recurring-templates/{templateId}")
    suspend fun deleteRecurringTemplate(@Path("templateId") templateId: Int)
}
