package com.timebox.android.ui.types

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.timebox.android.data.TaskType
import com.timebox.android.ui.theme.TimeboxTheme
import com.timebox.android.ui.theme.TimeboxShapes

/** THROWAWAY: native variant A, backed only by in-memory sample data. */
class TaskTypeMergePrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TimeboxTheme { MergePrototype() } }
    }
}

private val sampleNames = listOf("Reading", "Transportation", "Transportation/Train", "Travel", "Travel/Flights", "Travel/Train", "unspecified")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergePrototype() {
    val colors = TimeboxTheme.colors
    var names by remember { mutableStateOf(sampleNames) }
    var source by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var review by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showState by remember { mutableStateOf(false) }
    var activity by remember { mutableStateOf("Travel") }
    val keyboard = LocalSoftwareKeyboardController.current
    val target = names.firstOrNull { it.equals(input.trim(), ignoreCase = true) }
    val affected = names.filter { it == source || it.startsWith("$source/") }
    val invalid = input.isBlank() || input.split('/').any { it.isBlank() } || target == "unspecified" || (target != null && source != null && (target == source || target.startsWith("$source/") || source!!.startsWith("$target/")))
    fun complete() {
        val destination = target ?: input.trim()
        val mapping = affected.associateWith { destination + it.removePrefix(source!!) }
        names = (names.filterNot { it in affected } + mapping.values).distinct().sorted()
        activity = mapping[activity] ?: activity
        message = if (target != null) "$source merged into $destination. Associated work preserved." else "$source renamed to $destination."
        source = null
        review = false
    }
    Column(Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Task Types", style = TimeboxTheme.type.sectionTitle, color = colors.on)
            TextButton(onClick = { names = sampleNames; source = null; message = ""; search = ""; activity = "Travel" }) { Text("Reset") }
        }
        Text("PROTOTYPE A · Sample data only", Modifier.padding(horizontal = 16.dp), style = TimeboxTheme.type.laneLabel, color = colors.onVariant)
        Text("Rename Travel to Transportation to try merging.", Modifier.padding(16.dp), style = TimeboxTheme.type.bodySmall, color = colors.on)
        if (message.isNotEmpty()) Text(message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = colors.on)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tracking: $activity · continues", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
            TextButton(onClick = { showState = !showState }) { Text("State") }
        }
        if (showState) Text("${names.size} types: ${names.joinToString()}\nSource: $source · Input: $input · Review: $review\nMemory only; tracking is simulated.", Modifier.padding(16.dp), color = colors.onVariant, style = TimeboxTheme.type.bodySmall)
        val groups = names.filter { it.contains(search, ignoreCase = true) }.mapIndexed { i, n -> TaskType(i, n, 0) }.groupBy { it.root }.map { TypeGroup(it.key, it.value) }
        Box(Modifier.weight(1f)) {
            TypesScreen(TypesUiState(groups = groups, loading = false, input = search),
                onInputChange = { search = it }, onAdd = { message = "Sample preview: use Rename to explore merging." },
                onDelete = { message = "Deletion is outside this merge preview." }, onConfirmCascade = {}, onMigrateTarget = {},
                onConfirmMigrate = {}, onDismissCascade = {}, onRetry = {},
                onRename = { source = it.name; input = it.name; review = false; message = "" })
        }
    }
    source?.let { selected ->
        ModalBottomSheet(onDismissRequest = { source = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.sheet, contentColor = colors.on, shape = TimeboxShapes.sheet) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (review) "Review merge" else "Rename task type", style = TimeboxTheme.type.sectionTitle)
                Text(if (review) "$selected → $target" else selected, style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                if (!review) {
                    OutlinedTextField(input, { input = it }, label = { Text("Task type path") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field)
                    when {
                        target == selected -> Text("Enter a new name to continue.", style = TimeboxTheme.type.bodySmall)
                        invalid -> Text("Choose a separate branch. A type cannot merge with its ancestors, descendants, or unspecified.", color = colors.error, style = TimeboxTheme.type.bodySmall)
                        target != null -> Column(Modifier.fillMaxWidth().background(colors.low, TimeboxShapes.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$target already exists.", style = TimeboxTheme.type.label)
                            Text("Combine $selected and its descendants with this branch. Review the changes before confirming.", style = TimeboxTheme.type.bodySmall)
                        }
                        else -> Text("Updates this type and its descendants everywhere, including past work.", style = TimeboxTheme.type.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { source = null }) { Text("Cancel") }
                        Button(enabled = !invalid, onClick = { keyboard?.hide(); if (target != null) review = true else complete() }) { Text(if (target != null && target != selected) "Review merge" else "Save") }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().background(colors.low, TimeboxShapes.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        affected.forEach { old ->
                            val next = target + old.removePrefix(selected)
                            Text("$old → $next\n${if (next in names) "Combine into existing type" else "Move into destination branch"}", style = TimeboxTheme.type.bodySmall)
                        }
                    }
                    Text("Associated work · sample counts", style = TimeboxTheme.type.label)
                    Text("8 Tasks · 12 Planned Blocks\n34 Actual Blocks · 2 Recurring Task Series", style = TimeboxTheme.type.bodySmall)
                    Text("Tasks include 2 completed, 1 archived and 1 trashed. History combines under $target. Existing links and other classifications stay intact.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    Text("Running activity continues without stopping.", style = TimeboxTheme.type.bodySmall)
                    Text("This permanently combines the categories. $selected disappears. This cannot be undone.", style = TimeboxTheme.type.label)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { review = false }) { Text("Back") }
                        Button(onClick = { complete() }) { Text("Merge into $target") }
                    }
                }
            }
        }
    }
}
