package com.timebox.android.data.remote

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
        @Body body: TimeBlockPatchDto,
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
    )
}
