package com.timebox.android.ui.assistant

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.timebox.android.ui.components.EmptyStateCard
import com.timebox.android.ui.theme.TimeboxDimens

/** Placeholder home for the Assistant until its conversations land (#9). */
@Composable
fun AssistantScreen() {
    Box(Modifier.fillMaxSize().padding(horizontal = TimeboxDimens.screenPadding)) {
        EmptyStateCard(
            title = "Assistant is on its way",
            description = "Conversations with the assistant will live here.",
        )
    }
}
