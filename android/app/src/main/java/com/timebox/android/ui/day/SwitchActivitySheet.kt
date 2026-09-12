package com.timebox.android.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.TaskType
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import java.time.ZoneId

/** Presentation only: the Activity Tracking owner controls the draft and commits the handoff. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SwitchActivitySheet(
    currentActivity: String,
    taskTypes: List<TaskType>,
    selectedType: TaskType?,
    onTypeChange: (TaskType) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    timing: ActivityTimeValue?,
    onTimingChange: (ActivityTimeValue?) -> Unit,
    now: Instant,
    zone: ZoneId,
    enabled: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    var pickerOpen by remember { mutableStateOf(false) }
    var timeChoice by remember { mutableIntStateOf(if (timing == null) 0 else 2) }
    val nextActivity = name.trim().ifBlank { selectedType?.name ?: "Next activity" }
    val effectiveTime = timing?.local?.replace('T', ' ') ?: "now"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp)
                .imePadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("ACTIVITY TRACKING", style = type.kicker, color = colors.onVariant)
                Text("Switch activity", style = type.screenTitle.copy(fontSize = 28.sp))
                Surface(color = colors.low, shape = TimeboxShapes.group) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("AFTER THIS CHANGE", style = type.kicker, color = colors.onVariant)
                        Text(currentActivity, style = type.sectionTitle)
                        Text("↓  ends $effectiveTime · next starts at the same time", style = type.bodySmall, color = colors.onVariant)
                        Text(nextActivity, style = type.sectionTitle)
                        Text("● Recording continues without a gap.", style = type.bodySmall, color = colors.onVariant)
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 500) onNameChange(it) },
                    label = { Text("Block Name (optional)", style = type.bodySmall) },
                    modifier = Modifier.fillMaxWidth(), shape = TimeboxShapes.field,
                    textStyle = type.body, singleLine = true, enabled = !busy,
                )
                ExposedDropdownMenuBox(expanded = pickerOpen, onExpandedChange = { if (!busy) pickerOpen = !pickerOpen }) {
                    OutlinedTextField(
                        value = selectedType?.name.orEmpty(), onValueChange = {}, readOnly = true,
                        label = { Text("Task Type", style = type.bodySmall) },
                        placeholder = { Text("Choose Task Type", style = type.bodySmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pickerOpen) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !busy).fillMaxWidth(),
                        shape = TimeboxShapes.field, textStyle = type.body, enabled = !busy,
                    )
                    ExposedDropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        taskTypes.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name, style = type.body) }, onClick = { onTypeChange(option); pickerOpen = false })
                        }
                    }
                }
                Text("When did this change happen?", style = type.label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Now", "15 min ago", "Choose time").forEachIndexed { index, label ->
                        FilterChip(selected = timeChoice == index, enabled = !busy, onClick = {
                            timeChoice = index
                            onTimingChange(if (index == 0) null else ActivityTimeValue.from(if (index == 1) now.minusSeconds(900) else now, zone))
                        }, label = { Text(label, style = type.bodySmall) })
                    }
                }
                timing?.let { value ->
                    ActivityTimeField("Change", value, zone) { onTimingChange(it); timeChoice = 2 }
                    Text(zone.id, style = type.bodySmall, color = colors.onVariant)
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = type.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", style = type.button) }
                Button(onClick = onConfirm, enabled = enabled && selectedType != null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(50)) {
                    Text(if (busy) "Switching…" else "Switch activity", style = type.button)
                }
            }
        }
    }
}
