package com.timebox.android.ui.day

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.timebox.android.data.TaskType
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

/** The choice shared by starting and switching: an optional Block Name and a required Task Type. */
@Composable
internal fun ActivitySelectionFields(
    taskTypes: List<TaskType>,
    selectedType: TaskType?,
    onTypeChange: (TaskType) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    busy: Boolean,
    onCreateType: (String) -> Unit,
    typeError: String? = null,
    onTypeQueryChange: () -> Unit = {},
) {
    val type = TimeboxTheme.type
    var typeQuery by remember { mutableStateOf(selectedType?.name.orEmpty()) }
    OutlinedTextField(
        value = name, onValueChange = { if (it.length <= 500) onNameChange(it) },
        label = { Text("Block Name (optional)", style = type.bodySmall) },
        modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field,
        textStyle = type.body, singleLine = true, enabled = !busy,
    )
    TaskTypePicker(
        recommendationName = name, recommendationEnabled = !busy,
        taskTypes = taskTypes,
        query = typeQuery,
        onQueryChange = { typeQuery = it; onTypeQueryChange() },
        selectedTypeId = selectedType?.id,
        onChoose = { chosen ->
            onTypeChange(chosen)
            typeQuery = chosen.name
        },
        onCreate = onCreateType,
    )
    typeError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = type.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
