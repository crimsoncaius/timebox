package com.timebox.android.ui.day

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.data.TaskType
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class LogTimeFormTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typedSuggestionAndOptionalDetailsAreUsedWhenLogging() {
        var savedType: Int? = null
        var savedName = ""
        var createdPath = ""
        compose.setContent {
            var query by remember { mutableStateOf("") }
            var type by remember { mutableStateOf<Int?>(1) }
            var name by remember { mutableStateOf("") }
            TimeboxTheme(darkTheme = false) {
                LogTimeForm(ActivityTimeValue("2026-09-12T16:15"), ActivityTimeValue("2026-09-12T16:45"),
                    ZoneId.of("Asia/Singapore"), listOf(TaskType(1, "unspecified", 0), TaskType(2, "gym", 1)),
                    type, query, name, "", false, null, {}, {}, { query = it },
                    { type = it.id; query = it.name }, { createdPath = it }, { name = it }, {},
                    { savedType = type; savedName = name }, {})
            }
        }
        compose.onNodeWithText("Block Name (optional)").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("gy")
        compose.onNodeWithText("gym", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Selected: gym").assertExists()
        compose.onNodeWithText("Block Name & note", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Block Name (optional)").performScrollTo().performTextInput("Training")
        compose.onNodeWithText("Log 30 min").performClick()
        assertEquals(2, savedType)
        assertEquals("Training", savedName)
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("coding/ai")
        compose.onAllNodes(hasSetTextAction())[0].performImeAction()
        assertEquals("coding/ai", createdPath)
    }

    @Test fun overnightDurationIsValidButReversedRangeCannotSave() {
        compose.setContent {
            var end by remember { mutableStateOf(ActivityTimeValue("2026-09-13T00:15")) }
            TimeboxTheme(darkTheme = false) {
                LogTimeForm(ActivityTimeValue("2026-09-12T23:45"), end, ZoneId.of("Asia/Singapore"),
                    emptyList(), null, "", "", "", false, null, {}, {}, {}, {}, {}, {}, {},
                    { end = ActivityTimeValue("2026-09-12T23:15") }, {})
            }
        }
        compose.onNodeWithText("Log 30 min").assertIsEnabled().performClick()
        compose.onNodeWithText("End must be after start.").assertExists()
        compose.onNode(hasText("Log time") and hasClickAction()).assertIsNotEnabled()
    }
}
