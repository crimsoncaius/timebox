package com.timebox.android.data

import com.timebox.android.data.remote.ApiFactory
import com.timebox.android.data.remote.BattleTaskCreateDto
import com.timebox.android.data.remote.PatchField
import com.timebox.android.data.remote.ProjectCreateDto
import com.timebox.android.data.remote.RecurrenceRuleDto
import com.timebox.android.data.remote.RecurringTemplateCreateDto
import com.timebox.android.data.remote.SettingsPatchDto
import com.timebox.android.data.remote.TaskTypeCreateDto
import com.timebox.android.data.remote.TaskIdsDto
import com.timebox.android.data.remote.TaskPlacementDto
import com.timebox.android.data.remote.TaskReorderDto
import com.timebox.android.data.remote.TimeBlockCreateDto
import com.timebox.android.data.remote.TimeboxApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

/** Message shown when a call fails, plus whether retrying makes sense. */
data class ApiError(
    val message: String,
    val isAuth: Boolean = false,
    val isNetwork: Boolean = false,
    val statusCode: Int? = null,
    val code: ApiErrorCode? = null,
    val detail: ServerErrorDetail? = null,
)

enum class ApiErrorCode {
    BackfillConfirmationRequired,
    TaskTypeReferenceConflict,
    DuplicateProjectName,
    InvalidReminder,
    RestoreParentFirst,
    InvalidRecurrenceRule,
}

sealed interface ServerErrorDetail {
    data class Text(val value: String) : ServerErrorDetail
    data class Validation(val issues: List<ValidationIssue>) : ServerErrorDetail
    data class BackfillConfirmation(val pastCycles: Int, val pastTasks: Int) : ServerErrorDetail
    data class Unknown(val body: String?) : ServerErrorDetail
}

data class ValidationIssue(val location: List<String>, val message: String, val type: String?)

/**
 * Talks to the Timebox API. Online-only: nothing is cached, every read hits the
 * network and every failure surfaces as an [ApiError] the screen can render.
 */
class TimeboxRepository private constructor(
    private val preferences: AppPreferences?,
    private val fixedApi: TimeboxApi?,
) {

    /** Production hooks keep device-local reminder work synchronized without caching task data. */
    var onActiveTasksLoaded: (BattleTaskList) -> Unit = {}
    var onConnectionChanged: () -> Unit = {}

    constructor(preferences: AppPreferences) : this(preferences, null)

    /** Test seam for repository contract tests; production always uses [AppPreferences]. */
    internal constructor(api: TimeboxApi) : this(null, api)

    val settings: Flow<AppSettings> = preferences?.settings
        ?: flowOf(AppSettings(baseUrl = "http://localhost/", apiKey = "", darkTheme = null))

    val battlePlanPreferences: Flow<BattlePlanPreferences> = preferences?.battlePlanPreferences
        ?: flowOf(BattlePlanPreferences())

    @Volatile
    private var cachedApiKey: String = ""

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var api: TimeboxApi? = null

    private suspend fun api(): TimeboxApi {
        fixedApi?.let { return it }
        val preferences = checkNotNull(preferences)
        val current = preferences.settings.first()
        if (!current.isConfigured) {
            throw ApiErrorException(
                ApiError("No server address yet. Add one under Settings › Server.")
            )
        }
        cachedApiKey = current.apiKey
        val normalized = ApiFactory.normalizeBaseUrl(current.baseUrl)
        val existing = api
        if (existing != null && cachedBaseUrl == normalized) return existing
        // The key is read through a provider so rotating it does not rebuild the client.
        val created = ApiFactory.create(normalized) { cachedApiKey }
        api = created
        cachedBaseUrl = normalized
        return created
    }

    suspend fun getDay(date: LocalDate): Result<Day> = call { api().getDay(date.toString()).toModel() }

    suspend fun getDayPreview(date: LocalDate): Result<Day> =
        call { api().getDayPreview(date.toString()).toModel() }

    suspend fun getDaySummary(date: LocalDate): Result<DaySummary> =
        call { api().getDaySummary(date.toString()).toModel() }

    suspend fun listArchivedDays(): Result<List<ArchivedDay>> =
        call { api().listDays().map { it.toModel() } }

    suspend fun listTaskTypes(): Result<List<TaskType>> =
        call { api().listTaskTypes().map { it.toModel() } }

    suspend fun createTaskType(name: String): Result<TaskType> =
        call { api().createTaskType(TaskTypeCreateDto(name)).toModel() }

    suspend fun deleteTaskType(
        id: Int,
        cascadeBlocks: Boolean = false,
        migrateBlocksTo: Int? = null,
        clearTaskReferences: Boolean = false,
    ): Result<Unit> = call {
        api().deleteTaskType(id, cascadeBlocks, migrateBlocksTo, clearTaskReferences)
    }

    suspend fun createBlock(
        date: LocalDate,
        lane: Lane,
        taskTypeId: Int,
        startMinute: Int,
        endMinute: Int,
        note: String?,
        taskId: Int? = null,
    ): Result<Day> = call {
        api().createBlock(
            date.toString(),
            TimeBlockCreateDto(
                lane = lane.wire,
                taskTypeId = taskTypeId,
                taskId = taskId,
                note = note,
                startMinute = startMinute,
                endMinute = endMinute,
            ),
        ).toModel()
    }

    suspend fun patchBlock(
        date: LocalDate,
        blockId: Int,
        taskTypeId: Int? = null,
        note: String? = null,
        startMinute: Int? = null,
        endMinute: Int? = null,
        taskId: PatchField<Int> = PatchField.Absent,
    ): Result<Day> = call {
        api().patchBlock(
            date.toString(),
            blockId,
            timeBlockPatchBody(taskTypeId, taskId, note, startMinute, endMinute),
        ).toModel()
    }

    suspend fun listProjects(): Result<List<Project>> =
        call { api().listProjects().map { it.toModel() } }

    suspend fun createProject(request: ProjectCreate): Result<Project> = call {
        api().createProject(
            ProjectCreateDto(
                name = request.name,
                description = request.description,
                deadlineDate = request.deadlineDate?.toString(),
                deadlineAt = request.deadlineAt?.toString(),
            )
        ).toModel()
    }

    suspend fun patchProject(projectId: Int, patch: ProjectPatch): Result<Project> =
        call { api().patchProject(projectId, patch.toJson()).toModel() }

    suspend fun deleteProject(projectId: Int): Result<Unit> =
        call { api().deleteProject(projectId) }

    suspend fun listBattleTasks(collection: TaskCollection = TaskCollection.Active): Result<BattleTaskList> =
        call { api().listBattleTasks(collection.wire).toModel() }.also { result ->
            if (collection == TaskCollection.Active) result.onSuccess(onActiveTasksLoaded)
        }

    suspend fun createBattleTask(request: BattleTaskCreate): Result<BattleTask> = call {
        api().createBattleTask(
            BattleTaskCreateDto(
                title = request.title,
                description = request.description,
                readyToPlan = request.readyToPlan,
                status = request.status.wire,
                projectId = request.projectId,
                parentId = request.parentId,
                taskTypeId = request.taskTypeId,
                urgency = request.urgency?.wire,
                importance = request.importance?.wire,
                deadlineDate = request.deadlineDate?.toString(),
                deadlineAt = request.deadlineAt?.toString(),
                reminderAt = request.reminderAt?.toString(),
                isBlocked = request.isBlocked,
                blockingReason = request.blockingReason,
            )
        ).toModel()
    }

    suspend fun patchBattleTask(taskId: Int, patch: BattleTaskPatch): Result<BattleTask> =
        call { api().patchBattleTask(taskId, patch.toJson()).toModel() }

    suspend fun reorderBattleTasks(placements: List<TaskPlacement>): Result<Unit> = call {
        api().reorderBattleTasks(
            TaskReorderDto(placements.map { TaskPlacementDto(it.taskId, it.status.wire, it.position) })
        )
    }

    suspend fun archiveCompletedBattleTasks(taskIds: List<Int>): Result<Unit> =
        call { api().archiveCompletedBattleTasks(TaskIdsDto(taskIds)) }

    suspend fun unarchiveBattleTask(taskId: Int): Result<Unit> =
        call { api().unarchiveBattleTask(taskId) }

    suspend fun trashBattleTask(taskId: Int): Result<BattleTask> =
        call { api().trashBattleTask(taskId).toModel() }

    suspend fun restoreBattleTask(taskId: Int): Result<Unit> =
        call { api().restoreBattleTask(taskId) }

    suspend fun permanentlyDeleteBattleTask(taskId: Int): Result<Unit> =
        call { api().permanentlyDeleteBattleTask(taskId) }

    suspend fun listDueReminders(): Result<List<DueReminder>> =
        call { api().listDueReminders().map { it.toModel() } }

    suspend fun acknowledgeReminder(taskId: Int): Result<Unit> =
        call { api().acknowledgeReminder(taskId) }

    suspend fun previewRecurrence(rule: RecurrenceRule): Result<RecurrencePreview> =
        call { api().previewRecurrence(rule.toDto()).toModel() }

    suspend fun listRecurringTemplates(status: RecurrenceStatus): Result<List<RecurringTemplate>> =
        call { api().listRecurringTemplates(status.wire).map { it.toModel() } }

    suspend fun createRecurringTemplate(request: RecurringTemplateCreate): Result<RecurringTemplate> = call {
        val rule = request.rule
        api().createRecurringTemplate(
            RecurringTemplateCreateDto(
                title = request.title,
                description = request.description,
                projectId = request.projectId,
                taskTypeId = request.taskTypeId,
                urgency = request.urgency?.wire,
                importance = request.importance?.wire,
                mode = rule.mode.wire,
                frequency = rule.frequency.wire,
                interval = rule.interval,
                weekdays = rule.weekdays,
                monthDay = rule.monthDay,
                quotaCount = rule.quotaCount,
                startDate = rule.startDate.toString(),
                endDate = rule.endDate?.toString(),
                cycleLimit = rule.cycleLimit,
                checklistTitles = request.checklistTitles,
                confirmBackfill = request.confirmBackfill,
            )
        ).toModel()
    }

    suspend fun getRecurringTemplate(templateId: Int): Result<RecurringTemplate> =
        call { api().getRecurringTemplate(templateId).toModel() }

    suspend fun patchRecurringTemplate(
        templateId: Int,
        patch: RecurringTemplatePatch,
    ): Result<RecurringTemplate> =
        call { api().patchRecurringTemplate(templateId, patch.toJson()).toModel() }

    suspend fun pauseRecurringTemplate(templateId: Int): Result<RecurringTemplate> =
        call { api().pauseRecurringTemplate(templateId).toModel() }

    suspend fun resumeRecurringTemplate(templateId: Int): Result<RecurringTemplate> =
        call { api().resumeRecurringTemplate(templateId).toModel() }

    suspend fun endRecurringTemplate(templateId: Int): Result<RecurringTemplate> =
        call { api().endRecurringTemplate(templateId).toModel() }

    suspend fun deleteRecurringTemplate(templateId: Int): Result<Unit> =
        call { api().deleteRecurringTemplate(templateId) }

    suspend fun deleteBlock(date: LocalDate, blockId: Int): Result<Day> =
        call { api().deleteBlock(date.toString(), blockId).toModel() }

    suspend fun completeAsPlanned(date: LocalDate, blockId: Int): Result<Day> =
        call { api().completeAsPlanned(date.toString(), blockId).toModel() }

    suspend fun getWindowSettings(): Result<DayWindowSettings> =
        call { api().getSettings().toModel() }

    suspend fun patchWindowSettings(
        startHour: Int? = null,
        endHour: Int? = null,
        showFullDay: Boolean? = null,
    ): Result<DayWindowSettings> = call {
        api().patchSettings(SettingsPatchDto(startHour, endHour, showFullDay)).toModel()
    }

    suspend fun setConnection(baseUrl: String, apiKey: String) {
        checkNotNull(preferences).setConnection(baseUrl, apiKey)
        cachedApiKey = apiKey.trim()
        // Force the next call to rebuild against the new host.
        api = null
        cachedBaseUrl = null
        onConnectionChanged()
    }

    suspend fun setDarkTheme(dark: Boolean) = checkNotNull(preferences).setDarkTheme(dark)

    suspend fun setBattlePlanView(view: BattlePlanPreferences) =
        preferences?.setBattlePlanView(view) ?: Unit

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: ApiErrorException) {
            Result.failure(e)
        } catch (e: HttpException) {
            Result.failure(e.toApiErrorException())
        } catch (e: IOException) {
            Result.failure(
                ApiErrorException(
                    ApiError(
                        message = "Cannot reach the Timebox API. Check the server address in Settings.",
                        isNetwork = true,
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(
                ApiErrorException(ApiError(e.message ?: "Something went wrong."))
            )
        }
    }
}

/** Carries an [ApiError] through `Result.failure`. */
class ApiErrorException(val error: ApiError) : Exception(error.message)

val Throwable.apiError: ApiError
    get() = (this as? ApiErrorException)?.error ?: ApiError(message ?: "Something went wrong.")

internal fun HttpException.toApiErrorException(): ApiErrorException {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    return apiErrorException(code(), body)
}

internal fun apiErrorException(statusCode: Int, body: String?): ApiErrorException {
    val detailElement = body?.let {
        runCatching { ApiFactory.json.parseToJsonElement(it).jsonObject["detail"] }.getOrNull()
    }
    val detail = detailElement.toServerErrorDetail(body)
    val detailMessage = when (detail) {
        is ServerErrorDetail.Text -> detail.value
        is ServerErrorDetail.Validation -> detail.issues.joinToString("; ") { it.message }.ifBlank { null }
        is ServerErrorDetail.BackfillConfirmation ->
            "This recurrence would create ${detail.pastTasks} past tasks across ${detail.pastCycles} cycles."
        is ServerErrorDetail.Unknown, null -> null
    }
    val code = detectErrorCode(detailElement, detailMessage)
    val message = when (statusCode) {
        401 -> "This server needs an API key. Add it in Settings."
        403 -> "The API key was rejected. Check it in Settings."
        404 -> detailMessage ?: "Not found."
        409 -> detailMessage ?: "That conflicts with existing data."
        422 -> detailMessage ?: "The server rejected that change."
        else -> detailMessage ?: "Server error ($statusCode)."
    }
    return ApiErrorException(
        ApiError(
            message = message,
            isAuth = statusCode == 401 || statusCode == 403,
            statusCode = statusCode,
            code = code,
            detail = detail,
        )
    )
}

private fun JsonElement?.toServerErrorDetail(rawBody: String?): ServerErrorDetail? = when (this) {
    null -> if (rawBody == null) null else ServerErrorDetail.Unknown(rawBody)
    is JsonPrimitive -> contentOrNull?.let(ServerErrorDetail::Text) ?: ServerErrorDetail.Unknown(rawBody)
    is JsonArray -> ServerErrorDetail.Validation(mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val location = item["loc"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val message = item["msg"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        ValidationIssue(location, message, item["type"]?.jsonPrimitive?.contentOrNull)
    })
    is JsonObject -> {
        if (this["code"]?.jsonPrimitive?.contentOrNull == "backfill_confirmation_required") {
            ServerErrorDetail.BackfillConfirmation(
                pastCycles = this["past_cycles"]?.jsonPrimitive?.intOrNull ?: 0,
                pastTasks = this["past_tasks"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        } else {
            ServerErrorDetail.Unknown(rawBody)
        }
    }
}

private fun detectErrorCode(detail: JsonElement?, message: String?): ApiErrorCode? {
    if ((detail as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull == "backfill_confirmation_required") {
        return ApiErrorCode.BackfillConfirmationRequired
    }
    val normalized = message.orEmpty().lowercase()
    return when {
        "task type is still used" in normalized -> ApiErrorCode.TaskTypeReferenceConflict
        "project" in normalized && ("already exists" in normalized || "unique" in normalized) -> ApiErrorCode.DuplicateProjectName
        "reminder" in normalized && ("deadline" in normalized || "earlier" in normalized) -> ApiErrorCode.InvalidReminder
        "parent" in normalized && "restore" in normalized -> ApiErrorCode.RestoreParentFirst
        "recurrence" in normalized || "quota" in normalized || "weekday" in normalized ||
            "interval" in normalized || "cycle" in normalized || "month day" in normalized ||
            "end date" in normalized || "scheduled" in normalized -> ApiErrorCode.InvalidRecurrenceRule
        else -> null
    }
}

private fun RecurrenceRule.toDto() = RecurrenceRuleDto(
    mode = mode.wire,
    frequency = frequency.wire,
    interval = interval,
    weekdays = weekdays,
    monthDay = monthDay,
    quotaCount = quotaCount,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    cycleLimit = cycleLimit,
)
