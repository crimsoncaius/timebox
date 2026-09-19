package com.timebox.android.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timebox.android.TimeboxApplication
import com.timebox.android.data.TaskType
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class TaskTypeRecommendationState {
    var chosen by mutableStateOf(false)
        private set
    var dismissedName by mutableStateOf<String?>(null)
        private set
    internal var resultName by mutableStateOf<String?>(null)
    internal var resultCatalog by mutableStateOf<List<Pair<Int, String>>>(emptyList())
    internal var resultId by mutableStateOf<Int?>(null)
    fun markChosen() { chosen = true }
    fun dismiss(name: String?) { dismissedName = name }
    fun nameChanged() { dismissedName = null; resultId = null }
}

@Composable
fun rememberTaskTypeRecommendation(name: String?, types: List<TaskType>, selectedId: Int?, enabled: Boolean = true): Pair<TaskTypeRecommendationState, TaskType?> {
    val state = remember { TaskTypeRecommendationState() }
    val initialName = remember { name }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(name) {
        if (name != initialName) edited = true
        state.nameChanged()
    }
    val catalog = types.map { it.id to it.name }.sortedBy { it.first }
    val classified = selectedId != null && types.find { it.id == selectedId }?.name != "unspecified"
    val eligible = enabled && !name.isNullOrBlank() && (edited || name != initialName) && !classified && !state.chosen && state.dismissedName != name
    val repository = (LocalContext.current.applicationContext as? TimeboxApplication)?.repository
    LaunchedEffect(name, catalog, eligible) {
        state.resultId = null
        if (!eligible || repository == null) return@LaunchedEffect
        delay(500)
        try {
            val result = repository.recommendTaskType(name)
            if (!state.chosen && result.reason == "recommended" && (result.confidence ?: 0.0) >= 0.8) {
                state.resultName = name; state.resultCatalog = catalog; state.resultId = result.taskTypeId
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { /* Optional: manual selection remains available. */ }
    }
    val result = if (eligible && state.resultName == name && state.resultCatalog == catalog)
        types.find { it.id == state.resultId && it.name != "unspecified" } else null
    return state to result
}

@Composable
fun TaskTypeRecommendation(recommendation: TaskType?, onAccept: (TaskType) -> Unit, onDismiss: () -> Unit, enabled: Boolean = true) {
    recommendation ?: return
    val colors = TimeboxTheme.colors
    Surface(color = colors.low, shape = TimeboxShapes.card, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(enabled = enabled) { onAccept(recommendation) }.padding(12.dp)) {
                Text("Suggested Task Type", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                Text(recommendation.name, style = TimeboxTheme.type.body, color = colors.on)
            }
            TextButton(enabled = enabled, onClick = { onAccept(recommendation) }) { Text("Use") }
            IconButton(enabled = enabled, onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss Task Type recommendation", Modifier.size(18.dp)) }
        }
    }
}
