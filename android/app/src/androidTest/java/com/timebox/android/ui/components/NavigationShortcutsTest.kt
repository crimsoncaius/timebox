package com.timebox.android.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
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

    @Test fun settingsPreserveOrderAndExplicitEmptySelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = AppPreferences(context)
        val before = preferences.navigationShortcuts.first()
        try {
            val ordered = listOf(AppRoutes.Settings, AppRoutes.DayPattern, AppRoutes.Review)
            preferences.setNavigationShortcuts(ordered)
            assertEquals(ordered, AppPreferences(context).navigationShortcuts.first())
            preferences.setNavigationShortcuts(emptyList())
            assertEquals(emptyList<String>(), AppPreferences(context).navigationShortcuts.first())
            assertEquals(emptyList<String>(), navigationShortcutRoutes(AppPreferences(context).navigationShortcuts.first()))
            assertEquals(listOf(AppRoutes.DayPattern, AppRoutes.BattlePlan), navigationShortcutRoutes(null))
            assertEquals(listOf(AppRoutes.Habits), navigationShortcutRoutes(listOf("removed-route", AppRoutes.Habits, AppRoutes.Habits)))
        } finally { preferences.setNavigationShortcuts(navigationShortcutRoutes(before)) }
    }

    @Test fun editorEnforcesLimitRestoresDraftAndSavesOrder() {
        var saved: List<String>? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TimeboxTheme {
            NavigationShortcutEditor(navigationShortcutRoutes(null), {}, { saved = it })
        } }
        compose.onNode(hasText("Habits") and isToggleable()).performScrollTo().performClick()
        compose.onNode(hasText("Trends") and isToggleable()).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Habits earlier").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf(AppRoutes.DayPattern, AppRoutes.Habits, AppRoutes.BattlePlan), saved) }
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
        compose.onNodeWithText("0 of 3 selected").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), saved) }
    }

    @Test fun dockReselectDoesNothingAndOtherShortcutsNavigate() {
        var selected: String? = null
        compose.setContent { TimeboxTheme {
            NavigationDock(navigationShortcutRoutes(null), AppRoutes.DayPattern, { selected = it }, {})
        } }
        compose.onNodeWithText("Day").assertIsSelected().performClick()
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithText("Battle Plan").performClick()
        compose.runOnIdle { assertEquals(AppRoutes.BattlePlan, selected) }
    }
}
