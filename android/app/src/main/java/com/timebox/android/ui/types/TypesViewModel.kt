package com.timebox.android.ui.types

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.TaskType
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One root path and the types beneath it, in the order the design renders them. */
data class TypeGroup(
    val root: String,
    val items: List<TaskType>,
)

data class TypesUiState(
    val groups: List<TypeGroup> = emptyList(),
    val input: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** Set when a delete needs the user to confirm dropping its blocks too. */
    val pendingCascade: TaskType? = null,
    val migrateBlocksTo: Int? = null,
    val renaming: TaskType? = null,
    val renameInput: String = "",
    val renameError: String? = null,
    val mergePreview: com.timebox.android.data.remote.TaskTypeMergePreview? = null,
    val mergeRevision: Long = 0,
)

class TypesViewModel(private val repository: TimeboxRepository) : ViewModel() {

    private val _state = MutableStateFlow(TypesUiState())
    val state: StateFlow<TypesUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = it.groups.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.listTaskTypes().fold(
                onSuccess = { types ->
                    _state.update { it.copy(groups = group(types), loading = false, error = null) }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.apiError.message) }
                },
            )
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun beginRename(type: TaskType) {
        if (type.name == "unspecified" || _state.value.saving) return
        _state.update { it.copy(renaming = type, renameInput = type.name, renameError = null, mergePreview = null) }
    }

    fun changeRename(value: String) = _state.update { it.copy(renameInput = value, renameError = null, mergePreview = null) }

    fun cancelRename() {
        if (!_state.value.saving) _state.update { it.copy(renaming = null, renameError = null, mergePreview = null) }
    }

    fun saveRename() {
        val state = _state.value
        val type = state.renaming ?: return
        if (state.saving) return
        val name = state.renameInput.split('/').joinToString("/") { it.trim().lowercase(java.util.Locale.ROOT) }
        if (name.split('/').any { it.isEmpty() }) {
            _state.update { it.copy(renameError = "Enter a path with no empty segments.") }
            return
        }
        val target = state.groups.flatMap { it.items }.firstOrNull { it.name == name && it.id != type.id }
        if (target != null) { previewMerge(target.id); return }
        _state.update { it.copy(saving = true, renameError = null) }
        viewModelScope.launch {
            repository.renameTaskType(type.id, name).fold(
                onSuccess = {
                    _state.update { current -> current.copy(
                        groups = group(current.groups.flatMap { it.items }.map { item ->
                            if (item.id == type.id || item.name.startsWith("${type.name}/"))
                                item.copy(name = name + item.name.removePrefix(type.name)) else item
                        }),
                        saving = false, renaming = null, message = "Renamed ${type.name} to $name",
                    ) }
                    load()
                },
                onFailure = { e -> _state.update { it.copy(saving = false, renameError = e.apiError.message) } },
            )
        }
    }

    private fun previewMerge(targetId: Int) {
        val type = _state.value.renaming ?: return
        _state.update { it.copy(saving = true, renameError = null) }
        viewModelScope.launch {
            repository.previewTaskTypeMerge(type.id, targetId).fold(
                onSuccess = { preview -> _state.update { it.copy(saving = false, mergePreview = preview) } },
                onFailure = { e -> _state.update { it.copy(saving = false, renameError = e.apiError.message) } },
            )
        }
    }

    fun backFromMerge() {
        if (!_state.value.saving) _state.update { it.copy(mergePreview = null, renameError = null) }
    }

    fun confirmMerge() {
        val preview = _state.value.mergePreview ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, renameError = null) }
        viewModelScope.launch {
            repository.mergeTaskType(preview).fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, renaming = null, mergePreview = null, mergeRevision = it.mergeRevision + 1, message = "Merged ${preview.sourceName} into ${preview.targetName}") }
                    load()
                },
                onFailure = { e ->
                    if (e.apiError.statusCode == 409) {
                        repository.previewTaskTypeMerge(preview.sourceId, preview.targetId).fold(
                            onSuccess = { refreshed -> _state.update { it.copy(saving = false, mergePreview = refreshed, renameError = "The branches changed. Review the refreshed changes and confirm again.") } },
                            onFailure = { _state.update { it.copy(saving = false, mergePreview = null, renameError = "The branches are no longer available. Close and reopen Task Types to refresh.") } },
                        )
                    } else _state.update { it.copy(saving = false, renameError = e.apiError.message) }
                },
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun dismissCascadePrompt() = _state.update { it.copy(pendingCascade = null, migrateBlocksTo = null) }
    fun setMigrateBlocksTo(id: Int?) = _state.update { it.copy(migrateBlocksTo = id) }

    fun addType() {
        val name = _state.value.input.trim()
        if (name.isEmpty()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.createTaskType(name).fold(
                onSuccess = {
                    _state.update { it.copy(input = "", saving = false, message = "Added $name") }
                    load()
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.apiError.message) }
                },
            )
        }
    }

    fun deleteType(type: TaskType) {
        if (type.name == "unspecified" || _state.value.saving) return
        val allTypes = _state.value.groups.flatMap { it.items }
        if (allTypes.any { it.id != type.id && it.name.startsWith("${type.name}/") }) {
            _state.update { it.copy(message = "Delete saved subpaths under ${type.name} first.") }
            return
        }
        if (type.totalUsageCount > 0) {
            _state.update { it.copy(pendingCascade = type, migrateBlocksTo = null) }
            return
        }
        performDelete(type)
    }

    fun confirmCascadeDelete() {
        val type = _state.value.pendingCascade ?: return
        _state.update { it.copy(pendingCascade = null, migrateBlocksTo = null) }
        performDelete(type, cascade = type.usageCount > 0, clearReferences = type.hasTaskReferences)
    }

    fun confirmMigrateDelete() {
        val state = _state.value
        val type = state.pendingCascade ?: return
        val target = state.migrateBlocksTo ?: return
        _state.update { it.copy(pendingCascade = null, migrateBlocksTo = null) }
        performDelete(type, migrateTo = target, clearReferences = type.hasTaskReferences)
    }

    private fun performDelete(
        type: TaskType,
        cascade: Boolean = false,
        migrateTo: Int? = null,
        clearReferences: Boolean = false,
    ) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.deleteTaskType(type.id, cascadeBlocks = cascade, migrateBlocksTo = migrateTo, clearTaskReferences = clearReferences).fold(
                onSuccess = {
                    _state.update {
                        it.copy(saving = false, message = "Deleted ${type.name}")
                    }
                    load()
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.apiError.message) }
                },
            )
        }
    }

    private fun group(types: List<TaskType>): List<TypeGroup> =
        types.sortedBy { it.name.lowercase() }
            .groupBy { it.root }
            .map { (root, items) -> TypeGroup(root, items) }
            .sortedBy { it.root.lowercase() }
}

val TaskType.hasTaskReferences: Boolean
    get() = taskUsageCount > 0 || recurringTemplateUsageCount > 0

val TaskType.totalUsageCount: Int
    get() = usageCount + taskUsageCount + recurringTemplateUsageCount
