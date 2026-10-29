package com.timebox.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.timebox.android.ui.theme.TimeboxTheme
import kotlin.math.roundToInt

/** Keeps the page mounted at full width while the shelf moves it aside. */
@Composable
internal fun NavigationShelf(
    open: Boolean,
    onClose: () -> Unit,
    currentRoute: String,
    onSelect: (String) -> Unit,
    onEditShortcuts: () -> Unit,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val edgeColor = TimeboxTheme.colors.hairline
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val shelfWidth = (maxWidth - 56.dp).coerceAtMost(360.dp)
        val widthPx = with(LocalDensity.current) { shelfWidth.toPx() }
        var drag by remember { mutableFloatStateOf(0f) }
        val close by rememberUpdatedState(onClose)
        val offset by animateFloatAsState(
            if (open) widthPx + drag else 0f,
            animationSpec = tween(if (reducedMotion || drag != 0f) 0 else 240),
            label = "navigation-shelf",
        )
        val swipe = Modifier.draggable(
            state = rememberDraggableState { delta -> drag = (drag + delta).coerceIn(-widthPx, 0f) },
            orientation = Orientation.Horizontal,
            enabled = open,
            onDragStopped = { velocity ->
                if (drag < -widthPx * .2f || velocity < -400f) close()
                drag = 0f
            },
        )
        Box(Modifier.width(shelfWidth).fillMaxHeight()
            .offset { IntOffset((offset - widthPx).roundToInt(), 0) }
            .then(swipe).testTag("navigation-shelf")
            .then(if (!open) Modifier.clearAndSetSemantics {} else Modifier.semantics {
                dismiss { close(); true }
            })) {
            NavigationDirectory(currentRoute, onSelect, onEditShortcuts)
        }
        Box(Modifier.fillMaxSize().offset { IntOffset(offset.roundToInt(), 0) }
            .background(TimeboxTheme.colors.bg).drawWithContent {
                drawContent()
                if (offset > 0f) drawLine(edgeColor, Offset(.5f, 0f), Offset(.5f, size.height), strokeWidth = 1f)
            }) {
            Box(Modifier.fillMaxSize().then(if (open) Modifier.clearAndSetSemantics {} else Modifier)) {
                content()
            }
            if (open) Box(Modifier.fillMaxSize().then(swipe)
                .semantics { contentDescription = "Close navigation shelf" }
                .clickable(onClick = onClose))
        }
        BackHandler(enabled = open, onBack = onClose)
    }
}
