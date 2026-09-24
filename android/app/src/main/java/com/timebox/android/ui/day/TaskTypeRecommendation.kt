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
    var dismissed by mutableStateOf(false)
    internal var resultId by mutableStateOf<Int?>(null)
    fun dismiss() { dismissed = true; resultId = null }
}

@Composable
fun rememberTaskTypeRecommendation(name: String?, types: List<TaskType>, selectedId: Int?, enabled: Boolean,
                                   query: String = "", linkedTaskName: String? = null): Pair<TaskTypeRecommendationState, TaskType?> {
    val context = listOf(name.orEmpty().trim(), query.trim(), linkedTaskName.orEmpty().trim())
    val catalog = types.map { it.id to it.name }.sortedBy { it.first }
    // A context change or new picker session cannot display an old response.
    val state = remember(context, catalog, enabled) { TaskTypeRecommendationState() }
    val eligible = enabled && context.any { it.isNotBlank() } && !state.dismissed
    val repository = (LocalContext.current.applicationContext as? TimeboxApplication)?.repository
    LaunchedEffect(state, eligible) {
        if (!eligible || repository == null) return@LaunchedEffect
        delay(500)
        try {
            val result = repository.recommendTaskType(context[0], context[1], context[2])
            if (result.reason == "recommended" && (result.confidence ?: 0.0) >= 0.8) state.resultId = result.taskTypeId
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { /* Optional: manual selection remains available. */ }
    }
    val result = if (eligible) types.find { it.id == state.resultId && it.id != selectedId && it.name != "unspecified" } else null
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
