package com.timebox.android.data

import com.timebox.android.data.remote.ApiErrorDto
import com.timebox.android.data.remote.ApiFactory
import com.timebox.android.data.remote.SettingsPatchDto
import com.timebox.android.data.remote.TaskTypeCreateDto
import com.timebox.android.data.remote.TimeBlockCreateDto
import com.timebox.android.data.remote.TimeBlockPatchDto
import com.timebox.android.data.remote.TimeboxApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

/** Message shown when a call fails, plus whether retrying makes sense. */
data class ApiError(
    val message: String,
    val isAuth: Boolean = false,
    val isNetwork: Boolean = false,
)

/**
 * Talks to the Timebox API. Online-only: nothing is cached, every read hits the
 * network and every failure surfaces as an [ApiError] the screen can render.
 */
class TimeboxRepository(private val preferences: AppPreferences) {

    val settings: Flow<AppSettings> = preferences.settings

    @Volatile
    private var cachedApiKey: String = ""

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var api: TimeboxApi? = null

    private suspend fun api(): TimeboxApi {
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

    suspend fun deleteTaskType(id: Int, cascadeBlocks: Boolean): Result<Unit> =
        call { api().deleteTaskType(id, cascadeBlocks = cascadeBlocks) }

    suspend fun createBlock(
        date: LocalDate,
        lane: Lane,
        taskTypeId: Int,
        startMinute: Int,
        endMinute: Int,
        note: String?,
    ): Result<Day> = call {
        api().createBlock(
            date.toString(),
            TimeBlockCreateDto(
                lane = lane.wire,
                taskTypeId = taskTypeId,
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
    ): Result<Day> = call {
        api().patchBlock(
            date.toString(),
            blockId,
            TimeBlockPatchDto(
                taskTypeId = taskTypeId,
                note = note,
                startMinute = startMinute,
                endMinute = endMinute,
            ),
        ).toModel()
    }

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
        preferences.setConnection(baseUrl, apiKey)
        cachedApiKey = apiKey.trim()
        // Force the next call to rebuild against the new host.
        api = null
        cachedBaseUrl = null
    }

    suspend fun setDarkTheme(dark: Boolean) = preferences.setDarkTheme(dark)

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

private fun HttpException.toApiErrorException(): ApiErrorException {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val detail = body?.let {
        runCatching { ApiFactory.json.decodeFromString(ApiErrorDto.serializer(), it).detail }
            .getOrNull()
    }
    val message = when (code()) {
        401 -> "This server needs an API key. Add it in Settings."
        403 -> "The API key was rejected. Check it in Settings."
        404 -> detail ?: "Not found."
        409 -> detail ?: "That conflicts with existing data."
        422 -> detail ?: "The server rejected that change."
        else -> detail ?: "Server error (${code()})."
    }
    return ApiErrorException(
        ApiError(message = message, isAuth = code() == 401 || code() == 403)
    )
}
