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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class ProjectDeadlineMode { None, DateOnly, DateTime }

data class ProjectEditorUiState(
    val projectId: Int? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val name: String = "",
    val description: String = "",
    val deadlineDate: String = "",
    val deadlineTime: String = "09:00",
    val deadlineMode: ProjectDeadlineMode = ProjectDeadlineMode.None,
    val timezone: String = "UTC",
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
            _state.value = ProjectEditorUiState(loading = true)
            viewModelScope.launch {
                repository.getDayPreview(LocalDate.now()).fold(
                    onSuccess = { day -> _state.update { it.copy(loading = false, timezone = day.timezone) } },
                    onFailure = { _state.update { it.copy(loading = false) } },
                )
            }
            return
        }
        _state.value = ProjectEditorUiState(projectId = projectId, loading = true)
        viewModelScope.launch {
            val projects = repository.listProjects()
            val timezone = repository.getDayPreview(LocalDate.now()).getOrNull()?.timezone ?: "UTC"
            projects.fold(
                onSuccess = { rows ->
                    val project = rows.firstOrNull { it.id == projectId }
                    if (project == null) {
                        _state.update { it.copy(loading = false, error = "Project not found.") }
                    } else {
                        _state.value = project.toEditorState(timezone)
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, error = error.apiError.message) }
                },
            )
        }
    }

    fun setName(value: String) = edit { copy(name = value) }
    fun setDescription(value: String) = edit { copy(description = value) }
    fun setDeadlineDate(value: String) = edit { copy(deadlineDate = value) }
    fun setDeadlineTime(value: String) = edit { copy(deadlineTime = value) }
    fun setDeadlineMode(value: ProjectDeadlineMode) = edit { copy(deadlineMode = value) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(message = "Project name is required.") }
            return
        }
        val deadlineDate = if (current.deadlineMode == ProjectDeadlineMode.None) null else
            runCatching { LocalDate.parse(current.deadlineDate.trim()) }.getOrElse {
                _state.update { state -> state.copy(message = "Use YYYY-MM-DD for the deadline.") }
                return
            }
        val deadlineAt = if (current.deadlineMode == ProjectDeadlineMode.DateTime) {
            val time = runCatching { LocalTime.parse(current.deadlineTime.trim()) }.getOrElse {
                _state.update { state -> state.copy(message = "Use HH:MM for the deadline time.") }
                return
            }
            LocalDateTime.of(checkNotNull(deadlineDate), time).atZone(ZoneId.of(current.timezone)).toInstant()
        } else null
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result: Result<Project> = if (current.projectId == null) {
                repository.createProject(
                    ProjectCreate(
                        name = current.name.trim(),
                        description = current.description.trim(),
                        deadlineDate = deadlineDate.takeIf { current.deadlineMode == ProjectDeadlineMode.DateOnly },
                        deadlineAt = deadlineAt,
                    )
                )
            } else {
                repository.patchProject(
                    current.projectId,
                    ProjectPatch(
                        name = PatchField.of(current.name.trim()),
                        description = PatchField.of(current.description.trim()),
                        deadlineDate = deadlineDate
                            ?.takeIf { current.deadlineMode == ProjectDeadlineMode.DateOnly }
                            ?.let { PatchField.of(it) }
                            ?: PatchField.Null,
                        deadlineAt = deadlineAt?.let { PatchField.of(it) } ?: PatchField.Null,
                    ),
                )
            }
            result.fold(
                onSuccess = { project ->
                    _state.value = project.toEditorState(current.timezone).copy(saved = true, message = "Project saved")
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

private fun Project.toEditorState(timezone: String) = ProjectEditorUiState(
    projectId = id,
    name = name,
    description = description,
    deadlineDate = deadlineDate?.toString()
        ?: deadlineAt?.atZone(ZoneId.of(timezone))?.toLocalDate()?.toString().orEmpty(),
    deadlineTime = deadlineAt?.atZone(ZoneId.of(timezone))?.toLocalTime()?.withSecond(0)?.withNano(0)?.toString()
        ?: "09:00",
    deadlineMode = when {
        deadlineAt != null -> ProjectDeadlineMode.DateTime
        deadlineDate != null -> ProjectDeadlineMode.DateOnly
        else -> ProjectDeadlineMode.None
    },
    timezone = timezone,
)
