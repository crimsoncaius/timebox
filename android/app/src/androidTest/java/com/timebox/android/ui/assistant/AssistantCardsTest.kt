package com.timebox.android.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class AssistantCardsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun carouselTabsHideOtherPagesAndOpenExactRangeInDarkLargeText() {
        var opened = ""
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                TimeboxTheme(darkTheme = true) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        AssistantCards(cardFixtures(), { opened = it.toString() }, { start, end -> opened = "$start/$end" })
                    }
                }
            }
        }
        compose.onNodeWithText("Open Day").performScrollTo().performClick()
        assertEquals("2026-09-26", opened)
        compose.onNodeWithContentDescription("Task Types, 2026-09-20 – 2026-09-26, card 2 of 3").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Open Day").assertDoesNotExist()
        compose.onNodeWithText("Work", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Writing").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("not planned", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open Trends").performScrollTo().performClick()
        assertEquals("2026-09-20/2026-09-26", opened)
        compose.onNodeWithContentDescription("Task Types, 2026-09-19, card 3 of 3").performScrollTo().performClick()
        compose.onNodeWithText("No stored time for this selection.").performScrollTo().assertIsDisplayed()
    }

    @Test fun singleLaneKeepsMidnightAndRunningReadDetails() {
        compose.setContent { TimeboxTheme(darkTheme = false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                AssistantCards(listOf(cardFixtures().first().let { it.copy(lane = "actual", blocks = it.blocks.filter { b -> b.lane == "actual" }) }), {}, { _, _ -> })
            }
        } }
        compose.onNodeWithText("this day", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("running", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Read 26 Sep 2026, 10:45", substring = true).assertExists()
    }
}

private fun cardFixtures(): List<AssistantCard> {
    val base = AssistantCard("blocks", LocalDate.parse("2026-09-26"), LocalDate.parse("2026-09-26"),
        ZoneId.of("Asia/Singapore"), Instant.parse("2026-09-26T02:45:00Z"), "both", null, false,
        blocks = listOf(
            AssistantBlock("actual", Instant.parse("2026-09-25T15:00:00Z"), Instant.parse("2026-09-25T23:00:00Z"), false, 480, 420, "Sleep", "Rest/Sleep"),
            AssistantBlock("planned", Instant.parse("2026-09-26T01:00:00Z"), Instant.parse("2026-09-26T02:00:00Z"), false, 60, 60, "Write proposal", "Work/Writing"),
            AssistantBlock("actual", Instant.parse("2026-09-26T01:15:00Z"), Instant.parse("2026-09-26T02:45:00Z"), true, 90, 90, "Write proposal", "Work/Writing"),
        ))
    return listOf(base, base.copy(id = "types", date = LocalDate.parse("2026-09-20"), filter = "Work", blocks = emptyList(), types = listOf(
        AssistantType("Work", 7200.0, 9000.0), AssistantType("Work/Writing", 7200.0, 5400.0), AssistantType("Work/Review", 0.0, 3600.0))),
        base.copy(id = "empty", date = LocalDate.parse("2026-09-19"), endDate = LocalDate.parse("2026-09-19"), blocks = emptyList(), types = emptyList()))
}
