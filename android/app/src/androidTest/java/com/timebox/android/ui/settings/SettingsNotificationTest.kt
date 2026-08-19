package com.timebox.android.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsNotificationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun deniedPermissionExplainsServerStateAndOffersBothRecoveryPaths() {
        var requested = 0
        var openedSettings = 0
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                SettingsScreen(
                    state = SettingsUiState(loading = false),
                    isDark = false,
                    onToggleDark = {},
                    onStartHourDelta = {},
                    onEndHourDelta = {},
                    onToggleFullDay = {},
                    onBaseUrlChange = {},
                    onApiKeyChange = {},
                    onSaveConnection = {},
                    notificationsAllowed = false,
                    onRequestNotificationPermission = { requested += 1 },
                    onOpenNotificationSettings = { openedSettings += 1 },
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Reminders still save to the server", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("Enable notifications").performScrollTo().performClick()
        compose.onNodeWithText("Open notification settings").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, requested)
            assertEquals(1, openedSettings)
        }
    }
}
