package com.timebox.android.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsProperties
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsNotificationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun apiKeyIsMaskedUntilExplicitlyRevealed() {
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                SettingsScreen(state = SettingsUiState(apiKeyInput = "test-key"), isDark = false, onToggleDark = {},
                    onBaseUrlChange = {}, onApiKeyChange = {}, onSaveConnection = {}, notificationsAllowed = true,
                    onRequestNotificationPermission = {}, onOpenNotificationSettings = {})
            }
        }
        val key = compose.onNode(hasText("API key") and hasSetTextAction())
        key.performScrollTo().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Show").performClick()
        key.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Hide").performClick()
        key.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test
    fun deniedPermissionExplainsBothReminderStoresAndOffersBothRecoveryPaths() {
        var requested = 0
        var openedSettings = 0
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                SettingsScreen(
                    state = SettingsUiState(),
                    isDark = false,
                    onToggleDark = {},
                    onBaseUrlChange = {},
                    onApiKeyChange = {},
                    onSaveConnection = {},
                    notificationsAllowed = false,
                    onRequestNotificationPermission = { requested += 1 },
                    onOpenNotificationSettings = { openedSettings += 1 },
                )
            }
        }

        compose.onNodeWithText("Battle Plan reminders still save to the server", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("Daily Reminder preferences stay saved on this device", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("this device cannot display any reminders until notifications are enabled", substring = true)
            .fetchSemanticsNode()
        compose.onNodeWithText("Off on this device").assertExists()
        compose.onNodeWithContentDescription("Day planning reminder").assertIsToggleable().assertIsOff()
        compose.onNodeWithContentDescription("Day review reminder").assertIsToggleable().assertIsOff()
        compose.onNodeWithContentDescription("Remind me before Planned Blocks").assertIsToggleable().assertIsOff()
        compose.onNodeWithText("Enable").performScrollTo().performClick()
        compose.onNodeWithText("Open notification settings").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, requested)
            assertEquals(1, openedSettings)
        }
    }
}
