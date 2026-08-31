package com.timebox.android.ui.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.EmptyStateCard
import com.timebox.android.ui.components.Hairline
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.leafOf
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

@Composable
fun TypesScreen(
    state: TypesUiState,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (TaskType) -> Unit,
    onConfirmCascade: () -> Unit,
    onMigrateTarget: (Int?) -> Unit,
    onConfirmMigrate: () -> Unit,
    onDismissCascade: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = TimeboxTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TimeboxDimens.screenPadding)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                singleLine = true,
                placeholder = {
                    Text("coding/ai", style = TimeboxTheme.type.body, color = colors.outlineVariant)
                },
                textStyle = TimeboxTheme.type.body.copy(color = colors.on, fontSize = 14.sp),
                shape = TimeboxShapes.field,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.field,
                    unfocusedContainerColor = colors.field,
                    focusedIndicatorColor = colors.outline,
                    unfocusedIndicatorColor = colors.hairline,
                    cursorColor = colors.on,
                ),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(TimeboxShapes.field)
                    .background(colors.action)
                    .clickable(enabled = !state.saving, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add task type",
                    tint = colors.onAction,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        when {
            state.loading && state.groups.isEmpty() -> LoadingState(Modifier.weight(1f))
            state.error != null && state.groups.isEmpty() -> ErrorState(
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
            state.groups.isEmpty() -> EmptyStateCard(
                title = "No Task Types yet",
                description = "Add a path such as coding/ai to organize Blocks and Tasks.",
                modifier = Modifier.padding(horizontal = TimeboxDimens.screenPadding, vertical = 8.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = TimeboxDimens.screenPadding,
                    end = TimeboxDimens.screenPadding,
                    bottom = TimeboxDimens.bottomInset,
                ),
            ) {
                state.groups.forEach { group ->
                    item(key = "header-${group.root}") {
                        Text(
                            text = group.root.uppercase(),
                            style = TimeboxTheme.type.laneLabel.copy(
                                fontSize = 9.5.sp,
                                letterSpacing = 0.16.em,
                            ),
                            color = colors.onVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                    }
                    item(key = "group-${group.root}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(TimeboxShapes.card)
                                .background(colors.low)
                                .padding(bottom = 0.dp),
                        ) {
                            group.items.forEachIndexed { index, type ->
                                if (index > 0) Hairline()
                                TypeRow(type = type, onDelete = { onDelete(type) })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    val pending = state.pendingCascade
    if (pending != null) {
        val otherTypes = state.groups.flatMap { it.items }.filter { it.id != pending.id }
        var migrateMenu by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onDismissCascade,
            containerColor = colors.sheet,
            titleContentColor = colors.on,
            textContentColor = colors.onVariant,
            title = { Text("Delete ${pending.name}?", style = TimeboxTheme.type.sectionTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = buildString {
                            append("Used by ${pending.usageCount} block(s), ${pending.taskUsageCount} Battle Plan task(s), and ${pending.recurringTemplateUsageCount} recurring template(s). ")
                            if (pending.hasTaskReferences) append("Task and template references will be cleared. ")
                            if (pending.usageCount > 0) append("Choose whether to delete its blocks or migrate them.")
                        },
                        style = TimeboxTheme.type.bodySmall,
                    )
                    if (pending.usageCount > 0 && otherTypes.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { migrateMenu = true }) {
                                Text(otherTypes.firstOrNull { it.id == state.migrateBlocksTo }?.name ?: "Choose migration target")
                            }
                            DropdownMenu(migrateMenu, { migrateMenu = false }) {
                                otherTypes.forEach { target -> DropdownMenuItem({ Text(target.name) }, { migrateMenu = false; onMigrateTarget(target.id) }) }
                            }
                        }
                        TextButton(enabled = state.migrateBlocksTo != null, onClick = onConfirmMigrate) { Text("Migrate blocks and delete") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmCascade) {
                    Text(if (pending.usageCount > 0) "Delete blocks and type" else "Clear references and delete", color = colors.error, style = TimeboxTheme.type.label)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCascade) {
                    Text("Cancel", color = colors.on, style = TimeboxTheme.type.label)
                }
            },
        )
    }
}

@Composable
private fun TypeRow(type: TaskType, onDelete: () -> Unit) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Nesting depth reads as indentation, same as the design.
        Spacer(Modifier.width((type.depth * 14).dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = leafOf(type.name),
                style = TimeboxTheme.type.label,
                color = colors.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = type.name,
                style = TimeboxTheme.type.monoSmall,
                color = colors.onVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Text(
            text = if (type.totalUsageCount > 0) "${type.totalUsageCount}×" else "—",
            style = TimeboxTheme.type.mono,
            color = colors.onVariant,
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete ${type.name}",
                tint = colors.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
