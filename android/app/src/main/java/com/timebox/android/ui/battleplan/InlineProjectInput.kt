package com.timebox.android.ui.battleplan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InlineProjectInput(
    state: InlineProjectCreation,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focus.requestFocus()
            keyboard?.show()
            bringIntoView.bringIntoView()
            onFocusRequested()
        }
    }
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) bringIntoView.bringIntoView()
    }
    Column(Modifier.fillMaxWidth().padding(8.dp).bringIntoViewRequester(bringIntoView)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.weight(1f).focusRequester(focus),
                label = { Text("Project name") },
                singleLine = true,
                enabled = !state.saving,
                isError = state.error != null,
                textStyle = TimeboxTheme.type.body,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!state.saving && state.name.isNotBlank()) onSubmit() }),
            )
            IconButton(onClick = onSubmit, enabled = !state.saving && state.name.isNotBlank()) {
                if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Check, contentDescription = "Create project", tint = TimeboxTheme.colors.project)
            }
            IconButton(onClick = onCancel, enabled = !state.saving) {
                Icon(Icons.Outlined.Close, contentDescription = "Cancel project creation")
            }
        }
        if (state.saving) Text("Creating project…", style = TimeboxTheme.type.body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        state.error?.let {
            Text(it, color = TimeboxTheme.colors.error, style = TimeboxTheme.type.body,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
