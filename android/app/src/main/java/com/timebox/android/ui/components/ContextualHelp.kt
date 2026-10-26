package com.timebox.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme

private var activeHelp by mutableStateOf<Any?>(null)

/** Help owns only its visibility; the parent editor stays composed with its draft intact. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualHelp(title: String, text: String) {
    val token = remember { Any() }
    val focus = remember { FocusRequester() }
    val colors = TimeboxTheme.colors
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val width = minOf(320, configuration.screenWidthDp - 32).dp
    val measured = rememberTextMeasurer().measure(text, style = TimeboxTheme.type.body,
        constraints = Constraints(maxWidth = with(density) { (width - 32.dp).roundToPx() }))
    val compact = measured.lineCount <= 4 && with(density) { measured.size.height.toDp() } < (configuration.screenHeightDp / 3).dp
    val dismiss = { activeHelp = null; focus.requestFocus() }
    DisposableEffect(token) { onDispose { if (activeHelp === token) activeHelp = null } }
    @Composable fun Content() {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = TimeboxTheme.type.label, color = colors.on, modifier = Modifier.semantics { heading() })
            Text(text, style = TimeboxTheme.type.body, color = colors.onVariant)
            TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close help") }
        }
    }
    Box {
        IconButton(onClick = { activeHelp = token }, modifier = Modifier.size(48.dp).focusRequester(focus)) {
            Icon(Icons.Outlined.Info, contentDescription = "Help: $title", tint = colors.onVariant)
        }
        if (compact) {
            DropdownMenu(expanded = activeHelp === token, onDismissRequest = dismiss,
                modifier = Modifier.width(width).heightIn(max = (configuration.screenHeightDp - 48).dp).semantics { paneTitle = title },
                containerColor = colors.bg) { Content() }
        } else if (activeHelp === token) {
            ModalBottomSheet(onDismissRequest = dismiss,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.bg) {
                Column(Modifier.verticalScroll(rememberScrollState()).semantics { paneTitle = title }) { Content() }
            }
        }
    }
}

