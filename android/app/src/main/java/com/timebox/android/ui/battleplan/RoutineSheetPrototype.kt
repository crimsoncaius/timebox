package com.timebox.android.ui.battleplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.components.TimeboxChip
import com.timebox.android.ui.theme.TimeboxTheme

// Issue 218: debug-route experiment. All edits are local and resettable.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineSheetPrototype(initialFlow: String, initialMode: String) {
    var rule by remember { mutableStateOf(routineRuleSample(initialMode == "quota")) }
    val quota = rule.mode == com.timebox.android.data.RecurrenceMode.Quota
    var creating by remember { mutableStateOf(initialFlow == "create") }
    var title by remember { mutableStateOf(if (creating) "" else "Weekly review") }
    var description by remember { mutableStateOf(if (creating) "" else "Look back over the week and choose what deserves attention next.") }
    var names by remember { mutableStateOf(if (creating) emptyList() else listOf("Review the week", "Choose next week's priorities")) }
    val repeat = routineRuleSummary(rule)
    var importance by remember { mutableStateOf("High") }
    var urgency by remember { mutableStateOf("Not set") }
    var type by remember { mutableStateOf("Planning") }
    var opened by remember { mutableStateOf(true) }
    var editor by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val colors = TimeboxTheme.colors
    fun edit(field: String, value: String) { draft = value; editor = field }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Text("PROTOTYPE · ISSUE 218", style = TimeboxTheme.type.kicker)
        Text("Battle Plan", style = TimeboxTheme.type.screenTitle)
        Text("Recurring", style = TimeboxTheme.type.sectionTitle)
        TextButton({ opened = true }) { Text(title.ifBlank { "New routine" }) }
        Text("Local sample only. Close the sheet to change the scenario.", color = colors.onVariant)
        Row {
            TextButton({ rule = routineRuleSample(false); opened = true }) { Text("Scheduled") }
            TextButton({ rule = routineRuleSample(true); opened = true }) { Text("Quota") }
        }
        TextButton({ creating = true; title = ""; description = ""; names = emptyList(); opened = true }) { Text("Try creation") }
        TextButton({ creating = false; title = "Weekly review"; description = "Look back over the week and choose what deserves attention next."; names = listOf("Review the week", "Choose next week's priorities"); rule = routineRuleSample(false); importance = "High"; urgency = "Not set"; type = "Planning"; opened = true }) { Text("Reset sample") }
    }
    if (opened) ModalBottomSheet(onDismissRequest = { opened = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.sheet.copy(alpha = 1f)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Repeat, null, tint = colors.onVariant)
                Text("Recurring routine", Modifier.weight(1f).padding(start = 12.dp), color = colors.onVariant)
                IconButton({ opened = false }) { Icon(Icons.Outlined.Close, "Close routine") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title.ifBlank { "What repeats?" }, fontSize = 25.sp, color = colors.on, modifier = Modifier.fillMaxWidth().clickable { edit("Title", title) }.padding(vertical = 12.dp))
                Text(description.ifBlank { "Add description" }, fontSize = 15.sp, color = colors.onVariant, modifier = Modifier.fillMaxWidth().clickable { edit("Description", description) }.padding(bottom = 8.dp))
                HorizontalDivider(color = colors.hairline)
                TaskSheetRow(Icons.Outlined.Repeat, repeat, "Repeat") { editor = "Repeat" }
                if (!quota) TaskSheetRow(Icons.Outlined.EventAvailable, "Not set", "Pre-planning") { editor = "Pre-planning" }
                HorizontalDivider(color = colors.hairline)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeboxChip("Importance: $importance", false, { edit("Importance", importance) })
                    TimeboxChip("Urgency: $urgency", false, { edit("Urgency", urgency) })
                    TimeboxChip(type, false, { edit("Task Type", type) })
                }
                TaskSubtasks(emptyList(), true, false, null, {}, {}, { names = names + it }, draftNames = names, onRemoveDraft = { index -> names = names.filterIndexed { i, _ -> i != index } })
                Text("Included in each ${if (quota) "session" else "occurrence"}.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                if (!creating) {
                    HorizontalDivider(Modifier.padding(top = 16.dp), color = colors.hairline)
                    Text("Current work", style = TimeboxTheme.type.label, modifier = Modifier.padding(top = 12.dp))
                    Text(if (quota) "This week · 1 of 3 sessions complete" else "Monday, 21 September · Not completed", color = colors.onVariant)
                    Text("Occurrence navigation will be explored in a later round.", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (creating) Button({ creating = false }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text("Create routine") }
        }
    }
    if (editor == "Repeat") RoutineRepeatEditor(rule, creating, { editor = null }, { rule = it; editor = null })
    editor?.takeUnless { it == "Repeat" }?.let { field ->
        ModalBottomSheet(onDismissRequest = { editor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.sheet.copy(alpha = 1f)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(field, style = TimeboxTheme.type.sectionTitle)
                when (field) {
                    "Importance", "Urgency" -> listOf("High", "Medium", "Low", "Not set").forEach { value -> TimeboxChip(value, draft == value, { draft = value }) }
                    "Pre-planning" -> Text("A separate focused editor would choose Planned Block times here. This round tests its placement; scheduling is not connected.")
                    else -> OutlinedTextField(draft, { draft = it }, label = { Text(field) }, modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = {
                    when (field) {
                        "Title" -> title = draft.trim()
                        "Description" -> description = draft
                        "Importance" -> importance = draft
                        "Urgency" -> urgency = draft
                        "Task Type" -> type = draft
                    }
                    editor = null
                }, enabled = (field != "Title" || draft.isNotBlank()), modifier = Modifier.fillMaxWidth()) { Text(if (field == "Pre-planning") "Done" else "Save") }
                TextButton({ editor = null }) { Text("Cancel") }
            }
        }
    }
}
