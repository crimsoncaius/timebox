package com.timebox.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.duration
import com.timebox.android.ui.hhmm
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BlockSheet(
    state: DayUiState,
    onDismiss: () -> Unit,
    onChooseType: (TaskType) -> Unit,
    onTypeQueryChange: (String) -> Unit,
    onCreateType: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDraft = state.draft != null
    val lane = state.sheetLane
    val laneColor = if (lane == Lane.Planned) colors.planned else colors.actual
    val laneSurface = if (lane == Lane.Planned) colors.plannedSurface else colors.actualSurface
    val laneBorder = if (lane == Lane.Planned) colors.plannedBorder else colors.actualBorder

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.sheet,
        contentColor = colors.on,
        scrimColor = colors.scrim,
        shape = TimeboxShapes.sheet,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .width(34.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.outlineVariant.copy(alpha = 0.6f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                // Outside the scroll, so the keyboard shrinks the viewport instead of
                // covering the picker. `union` keeps the nav bar from being paid for twice.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(TimeboxShapes.chip)
                        .background(laneSurface)
                        .border(1.dp, laneBorder, TimeboxShapes.chip)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(laneColor),
                    )
                    Text(
                        text = when {
                            isDraft -> "NEW BLOCK"
                            lane == Lane.Planned -> "PLANNED"
                            else -> "ACTUAL"
                        },
                        style = TimeboxTheme.type.laneLabel.copy(
                            fontSize = 10.5.sp,
                            letterSpacing = 0.06.em,
                        ),
                        color = laneColor,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = duration(state.sheetEnd - state.sheetStart),
                    style = TimeboxTheme.type.mono,
                    color = colors.onVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surf)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "${hhmm(state.sheetStart)} – ${hhmm(state.sheetEnd)}",
                style = TimeboxTheme.type.display,
                color = colors.on,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isDraft) {
                    "Pick a task type to create this block. Edits save as you make them."
                } else {
                    "Drag the block on the timeline to move it, or pull its grooves to resize."
                },
                style = TimeboxTheme.type.bodySmall,
                color = colors.onVariant,
            )

            Spacer(Modifier.height(18.dp))
            SheetLabel("Task type")
            Spacer(Modifier.height(8.dp))
            TaskTypePicker(
                taskTypes = state.taskTypes,
                query = state.typeQuery,
                onQueryChange = onTypeQueryChange,
                selectedTypeId = state.selectedBlock?.taskTypeId,
                onChoose = onChooseType,
                onCreate = onCreateType,
                // Raising the keyboard is right for a draft, where naming the type is the
                // only thing left to do. On an existing block it would bury the actions.
                autoFocus = isDraft,
            )

            Spacer(Modifier.height(18.dp))
            SheetLabel("Note")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.noteInput,
                onValueChange = onNoteChange,
                placeholder = {
                    Text("Optional", style = TimeboxTheme.type.body, color = colors.outlineVariant)
                },
                textStyle = TimeboxTheme.type.body.copy(color = colors.on),
                minLines = 3,
                maxLines = 4,
                shape = TimeboxShapes.field,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.bg,
                    unfocusedContainerColor = colors.bg,
                    focusedIndicatorColor = colors.outline,
                    unfocusedIndicatorColor = colors.hairline,
                    cursorColor = colors.on,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.selectedBlock != null) {
                    RoundIconButton(
                        icon = Icons.Outlined.Delete,
                        contentDescription = "Delete block",
                        onClick = onDelete,
                        tint = colors.error,
                        diameter = 44.dp,
                    )
                }
                Spacer(Modifier.weight(1f))
                val selected = state.selectedBlock
                if (selected != null && selected.lane == Lane.Planned) {
                    PrimaryButton(
                        text = if (state.selectedAlreadyCompleted) "Completed" else "Mark complete",
                        onClick = onComplete,
                        enabled = !state.selectedAlreadyCompleted,
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colors.bg,
                                modifier = Modifier.size(19.dp),
                            )
                        },
                    )
                }
                if (isDraft) {
                    PrimaryButton(text = "Done", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = TimeboxTheme.type.label.copy(fontSize = 11.sp),
        color = TimeboxTheme.colors.onVariant,
    )
}
