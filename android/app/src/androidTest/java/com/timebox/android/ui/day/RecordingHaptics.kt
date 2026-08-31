package com.timebox.android.ui.day

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

internal class RecordingHaptics : HapticFeedback {
    val events = mutableListOf<HapticFeedbackType>()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        events += hapticFeedbackType
    }
}
