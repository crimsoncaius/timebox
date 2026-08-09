package com.timebox.android.ui.review

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.Kicker
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.PrimaryButton
import com.timebox.android.ui.duration
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme

@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onBackToDay: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = TimeboxTheme.colors

    if (state.loading) {
        LoadingState(Modifier.fillMaxSize())
        return
    }
    if (state.error != null) {
        ErrorState(message = state.error, onRetry = onRetry, modifier = Modifier.fillMaxSize())
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(bottom = TimeboxDimens.bottomInset),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TotalCard(
                label = "Planned",
                total = duration(state.plannedMinutes),
                accent = colors.planned,
                surface = colors.plannedSurface,
                border = colors.plannedBorder,
                modifier = Modifier.weight(1f),
            )
            TotalCard(
                label = "Actual",
                total = duration(state.actualMinutes),
                accent = colors.actual,
                surface = colors.actualSurface,
                border = colors.actualBorder,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TimeboxShapes.group)
                .background(colors.low)
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Where the day went",
                style = TimeboxTheme.type.sectionTitle,
                color = colors.on,
            )
            Spacer(Modifier.height(14.dp))
            if (state.rows.isEmpty()) {
                Text(
                    text = "No blocks on this day.",
                    style = TimeboxTheme.type.bodySmall,
                    color = colors.onVariant,
                )
            } else {
                state.rows.forEach { row ->
                    ReviewRowItem(row = row, maxMinutes = state.maxMinutes)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TimeboxShapes.group)
                .background(colors.low)
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            Text("Drift", style = TimeboxTheme.type.sectionTitle, color = colors.on)
            Spacer(Modifier.height(8.dp))
            Text(
                text = driftCopy(state),
                style = TimeboxTheme.type.bodySmall.copy(lineHeight = 21.sp),
                color = colors.onVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Back to the day",
            onClick = onBackToDay,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TotalCard(
    label: String,
    total: String,
    accent: Color,
    surface: Color,
    border: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Kicker(label, accent)
        Spacer(Modifier.height(6.dp))
        Text(
            text = total,
            style = TimeboxTheme.type.display.copy(fontSize = 30.sp),
            color = TimeboxTheme.colors.on,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReviewRowItem(row: ReviewRow, maxMinutes: Int) {
    val colors = TimeboxTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = row.name,
                style = TimeboxTheme.type.label.copy(fontSize = 12.5.sp),
                color = colors.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${duration(row.plannedMinutes)} → ${duration(row.actualMinutes)}",
                style = TimeboxTheme.type.mono,
                color = colors.onVariant,
            )
        }
        Bar(fraction = row.plannedMinutes.toFloat() / maxMinutes, color = colors.planned)
        Spacer(Modifier.height(3.dp))
        Bar(fraction = row.actualMinutes.toFloat() / maxMinutes, color = colors.actual)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    val target = fraction.coerceIn(0f, 1f)
    val width by animateFloatAsState(targetValue = target, label = "reviewBar")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(TimeboxTheme.colors.surf),
    ) {
        if (width > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}
