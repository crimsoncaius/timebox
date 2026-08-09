package com.timebox.android.ui.day

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebox.android.data.Lane
import com.timebox.android.data.TaskType
import com.timebox.android.ui.components.ErrorState
import com.timebox.android.ui.components.Kicker
import com.timebox.android.ui.components.LoadingState
import com.timebox.android.ui.components.RoundIconButton
import com.timebox.android.ui.formatDayStrip
import com.timebox.android.ui.theme.TimeboxDimens
import com.timebox.android.ui.theme.TimeboxTheme
import kotlin.math.abs

@Composable
fun DayScreen(
    state: DayUiState,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onRetry: () -> Unit,
    onTapSlot: (Lane, Int) -> Unit,
    onSelectBlock: (Int) -> Unit,
    onCommitMove: (Int, Int, Int) -> Unit,
    onDismissSheet: () -> Unit,
    onChooseType: (TaskType) -> Unit,
    onTypeQueryChange: (String) -> Unit,
    onCreateType: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onCompleteSelected: () -> Unit,
) {
    val colors = TimeboxTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        DayStrip(
            label = "${formatDayStrip(state.date)} · swipe to change day",
            onPrev = onPrevDay,
            onNext = onNextDay,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TimeboxDimens.screenPadding)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(TimeboxDimens.laneGap),
        ) {
            Spacer(Modifier.width(TimeboxDimens.gutterWidth))
            Kicker("Planned", colors.planned, Modifier.weight(1f))
            Kicker("Actual", colors.actual, Modifier.weight(1f))
        }

        val day = state.day
        when {
            state.loading && day == null -> LoadingState(Modifier.weight(1f))
            state.error != null && day == null -> ErrorState(
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
            day != null -> Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = TimeboxDimens.screenPadding)
                    .padding(bottom = TimeboxDimens.bottomInset),
            ) {
                DayTimeline(
                    day = day,
                    selectedBlockId = state.selectedBlockId,
                    draft = state.draft,
                    onTapSlot = onTapSlot,
                    onSelectBlock = onSelectBlock,
                    onCommitMove = onCommitMove,
                )
            }
        }
    }

    if (state.sheetOpen) {
        BlockSheet(
            state = state,
            onDismiss = onDismissSheet,
            onChooseType = onChooseType,
            onTypeQueryChange = onTypeQueryChange,
            onCreateType = onCreateType,
            onNoteChange = onNoteChange,
            onDelete = onDeleteSelected,
            onComplete = onCompleteSelected,
        )
    }
}

@Composable
private fun DayStrip(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = TimeboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TimeboxDimens.screenPadding)
            .padding(bottom = 10.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        // Same 55px threshold the prototype uses.
                        if (abs(total) > 55f) {
                            if (total < 0) onNext() else onPrev()
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        total += amount
                    },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundIconButton(
            icon = Icons.Outlined.ChevronLeft,
            contentDescription = "Previous day",
            onClick = onPrev,
            tint = colors.on,
            diameter = 36.dp,
            border = colors.hairline,
            iconSize = 19.dp,
        )
        Text(
            text = label,
            style = TimeboxTheme.type.bodySmall,
            color = colors.onVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton(
            icon = Icons.Outlined.ChevronRight,
            contentDescription = "Next day",
            onClick = onNext,
            tint = colors.on,
            diameter = 36.dp,
            border = colors.hairline,
            iconSize = 19.dp,
        )
    }
}
