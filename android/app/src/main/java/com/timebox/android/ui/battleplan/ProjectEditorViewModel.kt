package com.timebox.android.ui.battleplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.Project
import com.timebox.android.data.ProjectCreate
import com.timebox.android.data.ProjectPatch
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.data.remote.PatchField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectEditorUiState(
    val projectId: Int? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val name: String = "",
    val dirty: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class ProjectEditorViewModel(private val repository: TimeboxRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProjectEditorUiState())
    val state: StateFlow<ProjectEditorUiState> = _state.asStateFlow()

    fun open(projectId: Int?) {
        if (projectId == null) {
            _state.value = ProjectEditorUiState()
            return
        }
        _state.value = ProjectEditorUiState(projectId = projectId, loading = true)
        viewModelScope.launch {
            val projects = repository.listProjects()
            projects.fold(
                onSuccess = { rows ->
                    val project = rows.firstOrNull { it.id == projectId }
                    if (project == null) {
                        _state.update { it.copy(loading = false, error = "Project not found.") }
                    } else {
                        _state.value = project.toEditorState()
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, error = error.apiError.message) }
                },
            )
        }
    }

    fun setName(value: String) = edit { copy(name = value) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(message = "Project name is required.") }
            return
        }
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result: Result<Project> = if (current.projectId == null) {
                repository.createProject(
                    ProjectCreate(
                        name = current.name.trim(),
                    )
                )
            } else {
                repository.patchProject(
                    current.projectId,
                    ProjectPatch(
                        name = PatchField.of(current.name.trim()),
                    ),
                )
            }
            result.fold(
                onSuccess = { project ->
                    _state.value = project.toEditorState().copy(saved = true, message = "Project saved")
                },
                onFailure = { error ->
                    _state.update { it.copy(saving = false, message = error.apiError.message) }
                },
            )
        }
    }

    private fun edit(block: ProjectEditorUiState.() -> ProjectEditorUiState) =
        _state.update { it.block().copy(dirty = true, saved = false) }
}

private fun Project.toEditorState() = ProjectEditorUiState(
    projectId = id,
    name = name,
)
