package com.timebox.android.ui.battleplan

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProjectNameSheet(
    state: ProjectNameDraft,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    val saving by rememberUpdatedState(state.saving)
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !saving },
    )
    val creating = state.projectId == null

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheet,
        sheetMaxWidth = 560.dp,
        shape = TimeboxShapes.sheet,
        containerColor = colors.sheet,
        contentColor = colors.on,
        scrimColor = colors.scrim,
        // Material 3's overlay-priority Back callback runs before the IME.
        // Use this dialog's normal dispatcher so Back can hide the keyboard first.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        contentWindowInsets = { WindowInsets.ime.union(WindowInsets.navigationBars) },
    ) {
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val imeVisible = WindowInsets.isImeVisible
        CompositionLocalProvider(
            LocalOnBackPressedDispatcherOwner provides checkNotNull(LocalView.current.findViewTreeOnBackPressedDispatcherOwner()),
        ) {
            BackHandler {
                if (!saving) {
                    if (imeVisible) keyboard?.hide() else onDismiss()
                }
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { sheet.currentValue }.first { it == SheetValue.Expanded }
            if (!saving) {
                focus.requestFocus()
                keyboard?.show()
            }
        }
        DisposableEffect(Unit) {
            onDispose { keyboard?.hide() }
        }

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .testTag("project-name-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (creating) "New project" else "Edit project", style = TimeboxTheme.type.sectionTitle)
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .semantics { contentDescription = "Project name" },
                label = { Text("Project name") },
                singleLine = true,
                enabled = !state.saving,
                isError = state.error != null,
                shape = TimeboxShapes.field,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.project,
                    focusedLabelColor = colors.project,
                    cursorColor = colors.project,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!saving && state.name.isNotBlank()) onSave() }),
            )
            state.error?.let {
                Text(it, color = colors.error, style = TimeboxTheme.type.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel, enabled = !state.saving) { Text("Cancel") }
                PrimaryButton(
                    if (state.saving) "Saving…" else if (creating) "Create project" else "Save changes",
                    onSave,
                    Modifier.weight(1f),
                    enabled = !state.saving && state.name.isNotBlank(),
                )
            }
        }
    }
}
