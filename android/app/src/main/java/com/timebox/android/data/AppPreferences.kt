package com.timebox.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timebox.android.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "timebox")

/** Connection details and theme override; feature preferences are stored separately below. */
data class AppSettings(
    val baseUrl: String,
    val apiKey: String,
    /** null follows the system setting. */
    val darkTheme: Boolean?,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
}

/** Device-local Battle Plan navigation state. Server data is never stored here. */
data class BattlePlanPreferences(
    val scope: String = "all",
    val status: TaskStatus = TaskStatus.Open,
    val sort: BattlePlanSort = BattlePlanSort.Manual,
    val hideCompleted: Boolean = false,
    val urgency: Set<String> = emptySet(),
    val importance: Set<String> = emptySet(),
    val taskTypes: Set<String> = emptySet(),
)

/** Device-local Work Mode lifecycle. Actual Blocks remain authoritative server data. */
data class WorkModeSnapshot(
    val entryAt: String,
    val lastConfirmedAt: String,
    val lastObservedAt: String,
    val confirmingPlannedBlockId: Int? = null,
    val confirmationStartedAt: String? = null,
    val activeActualId: Int? = null,
    val activePlannedBlockId: Int? = null,
    val activePlannedEndAt: String? = null,
)

enum class BattlePlanSort(val wire: String) {
    Manual("manual"), Deadline("deadline"), Urgency("urgency"), Importance("importance");

    companion object {
        fun fromWire(value: String?): BattlePlanSort = entries.firstOrNull { it.wire == value } ?: Manual
    }
}

class AppPreferences(private val context: Context) {

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val darkTheme = booleanPreferencesKey("dark_theme")
        val darkThemeSet = booleanPreferencesKey("dark_theme_set")
        val battlePlanScope = stringPreferencesKey("battle_plan_scope")
        val battlePlanStatus = stringPreferencesKey("battle_plan_status")
        val battlePlanSort = stringPreferencesKey("battle_plan_sort")
        val battlePlanHideCompleted = booleanPreferencesKey("battle_plan_hide_completed")
        val battlePlanUrgency = stringPreferencesKey("battle_plan_urgency")
        val battlePlanImportance = stringPreferencesKey("battle_plan_importance")
        val battlePlanTaskTypes = stringPreferencesKey("battle_plan_task_types")
        val workModeEntryAt = stringPreferencesKey("work_mode_entry_at")
        val workModeLastConfirmedAt = stringPreferencesKey("work_mode_last_confirmed_at")
        val workModeLastObservedAt = stringPreferencesKey("work_mode_last_observed_at")
        val workModeConfirmingPlannedId = stringPreferencesKey("work_mode_confirming_planned_id")
        val workModeConfirmationStartedAt = stringPreferencesKey("work_mode_confirmation_started_at")
        val workModeActiveActualId = stringPreferencesKey("work_mode_active_actual_id")
        val workModeActivePlannedId = stringPreferencesKey("work_mode_active_planned_id")
        val workModeActivePlannedEndAt = stringPreferencesKey("work_mode_active_planned_end_at")
    }

    val battlePlanPreferences: Flow<BattlePlanPreferences> = context.dataStore.data.map { prefs ->
        BattlePlanPreferences(
            scope = prefs[Keys.battlePlanScope] ?: "all",
            status = prefs[Keys.battlePlanStatus]
                ?.let { value -> TaskStatus.entries.firstOrNull { it.wire == value } }
                ?: TaskStatus.Open,
            sort = BattlePlanSort.fromWire(prefs[Keys.battlePlanSort]),
            hideCompleted = prefs[Keys.battlePlanHideCompleted] ?: false,
            urgency = prefs[Keys.battlePlanUrgency].toPreferenceSet(),
            importance = prefs[Keys.battlePlanImportance].toPreferenceSet(),
            taskTypes = prefs[Keys.battlePlanTaskTypes].toPreferenceSet(),
        )
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[Keys.baseUrl] ?: BuildConfig.DEFAULT_BASE_URL,
            apiKey = prefs[Keys.apiKey].orEmpty(),
            darkTheme = if (prefs[Keys.darkThemeSet] == true) prefs[Keys.darkTheme] else null,
        )
    }

    val workMode: Flow<WorkModeSnapshot?> = context.dataStore.data.map { prefs ->
        val entryAt = prefs[Keys.workModeEntryAt] ?: return@map null
        val confirmedAt = prefs[Keys.workModeLastConfirmedAt] ?: entryAt
        val observedAt = prefs[Keys.workModeLastObservedAt] ?: confirmedAt
        WorkModeSnapshot(
            entryAt = entryAt,
            lastConfirmedAt = confirmedAt,
            lastObservedAt = observedAt,
            confirmingPlannedBlockId = prefs[Keys.workModeConfirmingPlannedId]?.toIntOrNull(),
            confirmationStartedAt = prefs[Keys.workModeConfirmationStartedAt],
            activeActualId = prefs[Keys.workModeActiveActualId]?.toIntOrNull(),
            activePlannedBlockId = prefs[Keys.workModeActivePlannedId]?.toIntOrNull(),
            activePlannedEndAt = prefs[Keys.workModeActivePlannedEndAt],
        )
    }

    suspend fun setConnection(baseUrl: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.baseUrl] = baseUrl.trim()
            prefs[Keys.apiKey] = apiKey.trim()
        }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.darkTheme] = dark
            prefs[Keys.darkThemeSet] = true
        }
    }

    suspend fun setBattlePlanView(preferences: BattlePlanPreferences) {
        context.dataStore.edit { prefs ->
            prefs[Keys.battlePlanScope] = preferences.scope
            prefs[Keys.battlePlanStatus] = preferences.status.wire
            prefs[Keys.battlePlanSort] = preferences.sort.wire
            prefs[Keys.battlePlanHideCompleted] = preferences.hideCompleted
            prefs[Keys.battlePlanUrgency] = preferences.urgency.toPreferenceString()
            prefs[Keys.battlePlanImportance] = preferences.importance.toPreferenceString()
            prefs[Keys.battlePlanTaskTypes] = preferences.taskTypes.toPreferenceString()
        }
    }

    suspend fun setWorkMode(snapshot: WorkModeSnapshot?) {
        context.dataStore.edit { prefs ->
            val keys = listOf(
                Keys.workModeEntryAt, Keys.workModeLastConfirmedAt, Keys.workModeLastObservedAt,
                Keys.workModeConfirmingPlannedId, Keys.workModeConfirmationStartedAt,
                Keys.workModeActiveActualId, Keys.workModeActivePlannedId, Keys.workModeActivePlannedEndAt,
            )
            keys.forEach(prefs::remove)
            snapshot ?: return@edit
            prefs[Keys.workModeEntryAt] = snapshot.entryAt
            prefs[Keys.workModeLastConfirmedAt] = snapshot.lastConfirmedAt
            prefs[Keys.workModeLastObservedAt] = snapshot.lastObservedAt
            snapshot.confirmingPlannedBlockId?.let { prefs[Keys.workModeConfirmingPlannedId] = it.toString() }
            snapshot.confirmationStartedAt?.let { prefs[Keys.workModeConfirmationStartedAt] = it }
            snapshot.activeActualId?.let { prefs[Keys.workModeActiveActualId] = it.toString() }
            snapshot.activePlannedBlockId?.let { prefs[Keys.workModeActivePlannedId] = it.toString() }
            snapshot.activePlannedEndAt?.let { prefs[Keys.workModeActivePlannedEndAt] = it }
        }
    }
}

private fun String?.toPreferenceSet(): Set<String> =
    this?.split(',')?.filter(String::isNotBlank)?.toSet().orEmpty()

private fun Set<String>.toPreferenceString(): String = sorted().joinToString(",")
