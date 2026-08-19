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
