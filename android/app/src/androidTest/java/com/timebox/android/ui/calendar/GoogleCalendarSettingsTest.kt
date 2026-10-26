package com.timebox.android.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GoogleCalendarSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun heldCalendarMovesOnDropAndCancelledDragKeepsOrder() {
        val calendars = Json.parseToJsonElement("""[{"id":"work","name":"Work"},{"id":"home","name":"Home"},{"id":"family","name":"Family"}]""").jsonArray.map { it.jsonObject }
        var order by mutableStateOf(listOf("work", "home"))
        compose.setContent {
            TimeboxTheme { CalendarSelectionList(calendars, order, true) { order = it } }
        }
        val work = compose.onNodeWithText("Work").fetchSemanticsNode().boundsInRoot
        val home = compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Home").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, work.center.y - home.center.y), delayMillis = 100)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("work", "home"), order) }
        compose.onNodeWithText("Home").performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf("home", "work"), order) }
        compose.onNodeWithText("Work").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, -work.height * 1.5f), delayMillis = 100)
            cancel()
        }
        compose.runOnIdle { assertEquals(listOf("home", "work"), order) }
        compose.onNodeWithText("Family").performClick()
        compose.runOnIdle { assertEquals(listOf("home", "work", "family"), order) }
    }

    @Test fun selectionSurvivesOfflineStateAndRequiresExplicitReview() {
        val snapshot = Json.parseToJsonElement("""{"configured":true,"connected":true,"account":"alex@example.com","selected":["work"],"calendars":[{"id":"work","name":"Work"},{"id":"home","name":"Home"}],"timezone":"UTC","last_success":null}""").jsonObject
        var offline by mutableStateOf(false)
        var reviews = 0
        compose.setContent {
            var selection by remember { mutableStateOf<List<String>?>(null) }
            TimeboxTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GoogleCalendarSettings(snapshot, null, if (offline) "Cannot reach Timebox." else null, null, false,
                        selection, { selection = it }, {}, {}, { offline = false }, { reviews++ }, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Disconnect").assertDoesNotExist()
        compose.onNodeWithContentDescription("Manage calendars").performClick()
        compose.onNodeWithText("Home").performScrollTo().performClick()
        compose.onNodeWithText("Home").assertIsOn()
        compose.runOnIdle { assertEquals(0, reviews); offline = true }
        compose.onNodeWithText("Home").performScrollTo().assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Review calendar changes").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.onNodeWithText("Home").performScrollTo().assertIsOn()
        compose.onNodeWithText("Review calendar changes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, reviews) }
    }

    @Test fun failedInitialLoadOffersRetryInsteadOfConnect() {
        var retries = 0
        compose.setContent {
            TimeboxTheme {
                GoogleCalendarSettings(null, null, "Cannot reach Timebox.", null, false, null, {}, {}, {}, { retries++ }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Connect Google Calendar").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
