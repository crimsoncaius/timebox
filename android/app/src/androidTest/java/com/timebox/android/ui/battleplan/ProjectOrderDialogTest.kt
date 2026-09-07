package com.timebox.android.ui.battleplan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import com.timebox.android.data.Project
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProjectOrderDialogTest {
    @get:Rule val compose = createComposeRule()
    private val projects = listOf("Alpha", "Beta", "Gamma").mapIndexed { index, name ->
        Project(index + 1, name, Instant.EPOCH, Instant.EPOCH)
    }

    @Test fun movesWithAccessibleButtonsAndDisablesBoundaries() {
        var requested = emptyList<Int>()
        compose.setContent {
            var rows by remember { mutableStateOf(projects) }
            TimeboxTheme(darkTheme = false) {
                ProjectOrderDialog(rows, false, null, { ids ->
                    requested = ids
                    rows = ids.map { id -> projects.first { it.id == id } }
                }, {})
            }
        }
        compose.onNodeWithContentDescription("Move Alpha up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Gamma down").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Alpha down").performClick()
        compose.runOnIdle { assertEquals(listOf(2, 1, 3), requested) }
        compose.onNodeWithContentDescription("Move Beta up").assertIsNotEnabled()
    }

    @Test fun longPressDragRequestsNewOrder() {
        var requested = emptyList<Int>()
        compose.setContent {
            TimeboxTheme(darkTheme = true) { ProjectOrderDialog(projects, false, null, { requested = it }, {}) }
        }
        val first = compose.onNodeWithContentDescription("Drag Alpha").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription("Drag Beta").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Drag Alpha").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, second.center.y - first.center.y), delayMillis = 100)
            up()
        }
        compose.runOnIdle { assertEquals(listOf(2, 1, 3), requested) }
    }
}
