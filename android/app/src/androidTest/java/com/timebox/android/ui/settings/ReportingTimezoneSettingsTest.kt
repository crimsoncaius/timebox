package com.timebox.android.ui.settings

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.timebox.android.ui.theme.TimeboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReportingTimezoneSettingsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun explicitSharedZoneChooseAndSave() {
        var state by mutableStateOf(SettingsUiState(timezone = "Asia/Singapore", reportingZoneInput = "Asia/Singapore"))
        var saved = ""
        compose.setContent {
            TimeboxTheme(darkTheme = false) {
                SettingsScreen(state, false, {}, {}, {}, {}, onBaseUrlChange = {}, onApiKeyChange = {}, onSaveConnection = {}, notificationsAllowed = false, onRequestNotificationPermission = {}, onOpenNotificationSettings = {}, onReportingZoneChange = { state = state.copy(reportingZoneInput = it) }, onSaveReportingZone = { saved = state.reportingZoneInput }, onRetry = {})
            }
        }
        compose.onNodeWithText("Asia/Singapore", substring = true).assertExists()
        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onNodeWithText("Save time zone").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("new york")
        compose.onNodeWithText("America/New York").performClick()
        compose.onNodeWithText("Save time zone").performClick()
        compose.runOnIdle { assertEquals("America/New_York", saved) }
        compose.onNodeWithText("Search time zones").assertDoesNotExist()
    }
}
