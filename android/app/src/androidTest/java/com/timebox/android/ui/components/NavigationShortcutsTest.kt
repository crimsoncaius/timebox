package com.timebox.android.ui.components

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.roundToInt
import com.timebox.android.data.AppPreferences
import com.timebox.android.ui.AppRoutes
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class NavigationShortcutsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun islandFitsItsContentsStaysCenteredAndScrollsWhenFull() {
        val pins = mutableStateOf(listOf(AppRoutes.DayPattern))
        compose.setContent { TimeboxTheme {
            Box(Modifier.width(360.dp).testTag("dock-host")) {
                NavigationDock(pins.value, AppRoutes.DayPattern, {})
            }
        } }
        val strip = compose.onNodeWithTag("shortcut-strip")
        val host = compose.onNodeWithTag("dock-host").getUnclippedBoundsInRoot()
        val single = strip.getUnclippedBoundsInRoot()
        assertTrue("One shortcut should leave space on both sides", (single.right - single.left) < (host.right - host.left) * 0.65f)
        assertEquals(((host.left + host.right) / 2).value, ((single.left + single.right) / 2).value, 1f)
        assertEquals(0f, strip.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue(), 1f)
        compose.runOnIdle { pins.value = navigationDestinations.take(4).map { it.route } }
        compose.waitForIdle()
        val four = strip.getUnclippedBoundsInRoot()
        val fourth = compose.onNodeWithText("Trends").getUnclippedBoundsInRoot()
        assertTrue("Fourth shortcut must fit completely", fourth.right <= four.right)
        compose.onNodeWithText("Trends").assertIsDisplayed()
        compose.runOnIdle { pins.value = navigationDestinations.take(7).map { it.route } }
        compose.waitForIdle()
        val full = strip.getUnclippedBoundsInRoot()
        assertTrue((full.right - full.left) > (single.right - single.left))
        assertEquals(((host.left + host.right) / 2).value, ((full.left + full.right) / 2).value, 1f)
        assertTrue(strip.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0)
        compose.onNodeWithText(navigationDestinations[6].label).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add shortcut").assertDoesNotExist()
        compose.runOnIdle { pins.value = emptyList() }
        compose.waitForIdle()
        strip.assertDoesNotExist()
        compose.onNodeWithText("Add shortcut").assertDoesNotExist()
    }

    @Test fun swipesSettleOnShortcutBoundariesOrTheEnd() {
        compose.setContent { TimeboxTheme {
            NavigationDock(listOf(AppRoutes.DayPattern, AppRoutes.BattlePlan, AppRoutes.Habits,
                AppRoutes.Trends, AppRoutes.Settings), AppRoutes.DayPattern, {})
        } }
        val strip = compose.onNodeWithTag("shortcut-strip")
        val itemWidth = with(compose.density) {
            (compose.onNodeWithText("Battle Plan").getUnclippedBoundsInRoot().left -
                compose.onNodeWithText("Day").getUnclippedBoundsInRoot().left).toPx()
        }
        for ((distance, duration) in listOf(1.4f to 1000L, 2.4f to 200L, -1.4f to 1000L, 10f to 1000L)) {
            strip.performTouchInput {
                val start = if (distance > 0) width * 0.9f else width * 0.1f
                val end = (start - distance * itemWidth).coerceIn(1f, width - 1f)
                swipe(Offset(start, height / 2f), Offset(end, height / 2f), duration)
            }
            compose.waitForIdle()
            val range = strip.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
            val offset = range.value()
            if (distance > 0) assertTrue("Swipe did not scroll", offset > 0)
            assertTrue("Unsnapped offset: $offset", abs(offset - range.maxValue()) < 2f ||
                abs(offset - (offset / itemWidth).roundToInt() * itemWidth) < 2f)
        }
    }

    @Test fun settingsPreserveOrderAndExplicitEmptySelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = AppPreferences(context)
        val before = preferences.navigationShortcuts.first()
        try {
            val ordered = listOf(AppRoutes.Settings, AppRoutes.DayPattern, AppRoutes.Review, AppRoutes.Habits, AppRoutes.Trends)
            preferences.setNavigationShortcuts(ordered)
            assertEquals(ordered, AppPreferences(context).navigationShortcuts.first())
            preferences.setNavigationShortcuts(emptyList())
            assertEquals(emptyList<String>(), AppPreferences(context).navigationShortcuts.first())
            assertEquals(emptyList<String>(), navigationShortcutRoutes(AppPreferences(context).navigationShortcuts.first()))
            assertEquals(listOf(AppRoutes.DayPattern, AppRoutes.BattlePlan), navigationShortcutRoutes(null))
            assertEquals(listOf(AppRoutes.Habits), navigationShortcutRoutes(listOf("removed-route", AppRoutes.Habits, AppRoutes.Habits)))
        } finally { preferences.setNavigationShortcuts(navigationShortcutRoutes(before)) }
    }

    @Test fun editorAllowsMoreThanThreeRestoresDraftAndSavesOrder() {
        var saved: List<String>? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TimeboxTheme {
            NavigationShortcutEditor(navigationShortcutRoutes(null), {}, { saved = it })
        } }
        compose.onNode(hasText("Habits") and isToggleable()).performScrollTo().performClick()
        compose.onNode(hasText("Trends") and isToggleable()).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Move Habits earlier").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf(AppRoutes.DayPattern, AppRoutes.Habits, AppRoutes.BattlePlan, AppRoutes.Trends), saved) }
    }

    @Test fun cancelAndSystemBackNeverCommitEdits() {
        var cancelled = 0
        var saved = false
        compose.setContent { TimeboxTheme {
            NavigationShortcutEditor(navigationShortcutRoutes(null), { cancelled++ }, { saved = true })
        } }
        compose.onNodeWithContentDescription("Remove Day").performClick()
        compose.onNodeWithText("Cancel").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle { assertEquals(2, cancelled); assertFalse(saved) }
    }

    @Test fun saveFailureKeepsEmptyDraftAndAllowsRetry() {
        var attempts = 0
        var saved: List<String>? = null
        compose.setContent { TimeboxTheme {
            NavigationShortcutEditor(navigationShortcutRoutes(null), {}, {
                if (++attempts == 1) throw IOException("disk unavailable")
                saved = it
            })
        } }
        compose.onNodeWithContentDescription("Remove Day").performClick()
        compose.onNodeWithContentDescription("Remove Battle Plan").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Couldn't save shortcuts. Try again.").assertIsDisplayed()
        compose.onNodeWithText("0 selected").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), saved) }
    }

    @Test fun emptyDockIsHidden() {
        compose.setContent { TimeboxTheme {
            NavigationDock(emptyList(), AppRoutes.DayPattern, {})
        } }
        compose.onNodeWithTag("shortcut-strip").assertDoesNotExist()
        compose.onNodeWithText("Add shortcut").assertDoesNotExist()
        compose.onNodeWithText("Navigate").assertDoesNotExist()
    }

    @Test fun editorMovesShortcutLaterAndSavesOrder() {
        var saved: List<String>? = null
        compose.setContent { TimeboxTheme {
            NavigationShortcutEditor(navigationShortcutRoutes(null), {}, { saved = it })
        } }
        compose.onNodeWithContentDescription("Move Battle Plan later").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Day later").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf(AppRoutes.BattlePlan, AppRoutes.DayPattern), saved) }
    }

    @Test fun homeGroupsShortcutEditorWithPreferences() {
        var opened = false
        compose.setContent { TimeboxTheme {
            NavigationDirectory(AppRoutes.Home, {}, onEditShortcuts = { opened = true })
        } }
        compose.onNodeWithText("Edit shortcuts").performScrollTo()
        val preferences = compose.onNodeWithText("PREFERENCES").getUnclippedBoundsInRoot()
        val editor = compose.onNodeWithText("Edit shortcuts").getUnclippedBoundsInRoot()
        val settings = compose.onNodeWithText("Settings").getUnclippedBoundsInRoot()
        assertTrue(preferences.bottom <= editor.top && editor.bottom <= settings.top)
        compose.onNodeWithText("Edit shortcuts").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun stripScrollsAndResetsOnReturn() {
        val route = mutableStateOf(AppRoutes.DayPattern)
        val editing = mutableStateOf(false)
        compose.setContent { TimeboxTheme {
            NavigationDock(listOf(AppRoutes.DayPattern, AppRoutes.BattlePlan, AppRoutes.Habits, AppRoutes.Trends, AppRoutes.Settings),
                route.value, { route.value = it }, resetScroll = editing.value)
        } }
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Day").assertIsDisplayed()
        compose.onNodeWithText("Settings").performScrollTo()
        compose.runOnIdle { editing.value = true }
        compose.runOnIdle { editing.value = false }
        compose.onNodeWithText("Day").assertIsDisplayed()
        compose.onNodeWithText("Navigate").assertDoesNotExist()
    }

    @Test fun dockReselectDoesNothingAndOtherShortcutsNavigate() {
        var selected: String? = null
        compose.setContent { TimeboxTheme {
            NavigationDock(navigationShortcutRoutes(null), AppRoutes.DayPattern, { selected = it })
        } }
        compose.onNodeWithText("Day").assertIsSelected().performClick()
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithText("Battle Plan").performClick()
        compose.runOnIdle { assertEquals(AppRoutes.BattlePlan, selected) }
    }
}
