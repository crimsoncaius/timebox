package com.timebox.android.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Target the popup window directly; Espresso's default root is the parent editor. */
@OptIn(ExperimentalTestApi::class)
fun dismissContextualHelp(compose: ComposeContentTestRule) {
    if (compose.onAllNodes(isPopup()).fetchSemanticsNodes().isNotEmpty()) {
        compose.onNode(isPopup()).performKeyInput { pressKey(Key.Back) }
    } else {
        androidx.test.espresso.Espresso.pressBackUnconditionally()
    }
}
