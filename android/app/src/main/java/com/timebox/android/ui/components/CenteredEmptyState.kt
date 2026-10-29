package com.timebox.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme

/** Direction C: a quiet, centered explanation close to the screen's controls. */
@Composable
fun CenteredEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = TimeboxTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 70.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = colors.onVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = TimeboxTheme.type.sectionTitle, color = colors.on,
            textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(10.dp))
        Text(description, style = TimeboxTheme.type.bodySmall, color = colors.onVariant,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 335.dp))
        Spacer(Modifier.height(24.dp))
        actions()
    }
}
