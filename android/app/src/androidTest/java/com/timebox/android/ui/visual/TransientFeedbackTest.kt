package com.timebox.android.ui.visual

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.timebox.android.ui.components.TransientFeedback
import com.timebox.android.ui.theme.TimeboxTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransientFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightFeedbackActionsAndVisuals() = verify(false)
    @Test fun darkFeedbackActionsAndVisuals() = verify(true)

    private fun verify(dark: Boolean) {
        var actions = 0
        var dismisses = 0
        compose.setContent {
            TimeboxTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = TimeboxTheme.colors.bg) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TransientFeedback("Task completed", detail = "2 future Planned Blocks removed.", actionLabel = "Undo", onAction = { actions++ }, onDismiss = { dismisses++ })
                        TransientFeedback("Could not restore Prepare the quarterly review and confirm next steps", detail = "Restore unavailable. Try again.", actionLabel = "Retry", isError = true)
                        TransientFeedback("Restoring task", actionLabel = "Restoring…", actionsEnabled = false)
                        TransientFeedback("Task created", actionLabel = "Open")
                        TransientFeedback("Plan saved")
                    }
                }
            }
        }
        compose.onNodeWithText("Restoring…").assertIsNotEnabled()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.runOnIdle { assertEquals(1, actions); assertEquals(1, dismisses) }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "visual-regression")
        directory.mkdirs()
        File(directory, "feedback-${if (dark) "dark" else "light"}.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
