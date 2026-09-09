package com.timebox.android.ui.battleplan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

internal val ProjectCreationRowHeight = 48.dp

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
    Row(
        Modifier.fillMaxWidth().height(ProjectCreationRowHeight)
            .padding(horizontal = 8.dp).bringIntoViewRequester(bringIntoView),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focus)
                .semantics {
                    contentDescription = "Project name"
                    state.error?.let { error(it) }
                },
            singleLine = true,
            enabled = !state.saving,
            textStyle = TimeboxTheme.type.body.copy(color = TimeboxTheme.colors.on, lineHeight = 20.sp),
            cursorBrush = SolidColor(TimeboxTheme.colors.project),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!state.saving && state.name.isNotBlank()) onSubmit() }),
            decorationBox = { input ->
                Column(
                    Modifier.fillMaxSize()
                        .border(1.dp, if (state.error != null) TimeboxTheme.colors.error else TimeboxTheme.colors.outlineVariant, TimeboxShapes.field)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Status uses the label's reserved line; it never adds height.
                    Text(
                        state.error ?: if (state.saving) "Creating project…" else "Project name",
                        style = TimeboxTheme.type.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                        color = if (state.error != null) TimeboxTheme.colors.error else TimeboxTheme.colors.onVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    input()
                }
            },
        )
        IconButton(onClick = onSubmit, enabled = !state.saving && state.name.isNotBlank()) {
            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Check, contentDescription = "Create project", tint = TimeboxTheme.colors.project)
        }
        IconButton(onClick = onCancel, enabled = !state.saving) {
            Icon(Icons.Outlined.Close, contentDescription = "Cancel project creation")
        }
    }
}
