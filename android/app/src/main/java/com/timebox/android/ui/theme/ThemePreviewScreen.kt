package com.timebox.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.components.EmptyStateCard
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.components.SectionCard
import com.timebox.android.ui.components.SectionHeader
import com.timebox.android.ui.components.TimeboxChip

/** A small, data-free component gallery used for theme changes and visual QA. */
@Composable
fun ThemePreviewScreen(onBack: () -> Unit) {
    val colors = TimeboxTheme.colors
    var selectedChip by remember { mutableStateOf("Selected") }
    var fieldValue by remember { mutableStateOf("Editorial calm") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(bottom = TimeboxDimens.bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back to Settings") }

        SectionCard {
            SectionHeader("Surface ladder", "Page → embedded → card → raised → selected")
            Column(
                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SurfaceSample("Page", colors.bg)
                SurfaceSample("Field", colors.field)
                SurfaceSample("Card", colors.card)
                SurfaceSample("Raised", colors.raised)
                SurfaceSample("Selected", colors.selected, colors.onSelected)
            }
        }

        SectionCard {
            SectionHeader("Typography", "Manrope headlines and Inter interface text")
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Editorial heading", style = TimeboxTheme.type.screenTitle, color = colors.on)
                Text("Section heading", style = TimeboxTheme.type.sectionTitle, color = colors.on)
                Text("Body copy stays compact and readable across themes.", style = TimeboxTheme.type.body, color = colors.onVariant)
                Text("08:30–09:00", style = TimeboxTheme.type.mono, color = colors.on)
            }
        }

        SectionCard {
            SectionHeader("Controls", "Primary, selected, disabled, and field treatments")
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Selected", "Quiet").forEach { label ->
                        TimeboxChip(label, selectedChip == label, { selectedChip = label })
                    }
                }
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    label = { Text("Sample field") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton("Primary action", {}, Modifier.fillMaxWidth())
                PrimaryButton("Disabled action", {}, Modifier.fillMaxWidth(), enabled = false)
            }
        }

        EmptyStateCard(
            title = "A compact empty state",
            description = "Guidance remains close to the control that resolves it.",
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SurfaceSample(label: String, color: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color = TimeboxTheme.colors.on) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .border(1.dp, TimeboxTheme.colors.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = TimeboxTheme.type.bodySmall, color = contentColor)
    }
}
