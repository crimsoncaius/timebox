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

    fun dismissCascadePrompt() = _state.update { it.copy(pendingCascade = null) }

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
        if (type.usageCount > 0) {
            // Ask before destroying blocks; the API would refuse with a 409 anyway.
            _state.update { it.copy(pendingCascade = type) }
            return
        }
        performDelete(type, cascade = false)
    }

    fun confirmCascadeDelete() {
        val type = _state.value.pendingCascade ?: return
        _state.update { it.copy(pendingCascade = null) }
        performDelete(type, cascade = true)
    }

    private fun performDelete(type: TaskType, cascade: Boolean) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.deleteTaskType(type.id, cascadeBlocks = cascade).fold(
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
